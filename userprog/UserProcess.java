package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.LinkedList;
import java.util.*;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	// static vars
	static final int FD_TABLE_SIZE = 16;
	static int globalPid = 0;
	static int numProc = 0;
	public static Lock bigLock = new Lock();
	
	// instance field
	int pid;
	OpenFile[] fileTable = new OpenFile[FD_TABLE_SIZE];
	Queue<UserProcess> childrenList = new LinkedList<>();
	Map<Integer, Integer> childrenStatus = new HashMap<>();
	UserProcess parent;
	boolean hasException = false;

	// pipe
	static Map<String, List<Byte>> pipeTable = new HashMap<>();
	static Map<String, Condition> pipeCVMap = new HashMap<>();
	String[] pipeFdTable = new String[16];
	static Lock pipeTableLock = new Lock();
	static Lock rwLock = new Lock();
	Condition pipeCV = new Condition(rwLock);
	
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		// one more process
		bigLock.acquire();
		numProc += 1;
		
		this.pid = globalPid;
		globalPid += 1;
		
		// init fileTable
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();
		bigLock.release();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int virtualMemorySize = pageTable.length * pageSize;
		if (vaddr < 0 || vaddr >= virtualMemorySize) {
			return 0;
		}
		
		int vpn = Processor.pageFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int pageOffset = Processor.offsetFromAddress(vaddr);
		int physAddr = ppn * Processor.pageSize + pageOffset;

		if (physAddr < 0 || physAddr >= memory.length) {
			return 0;
		}

		// check remaining space
		int physAddrLen = physAddr + 1;
        int pageCnt = physAddrLen / pageSize;
        if (physAddrLen % pageSize != 0) {
            pageCnt ++;
        }
        int remainings = pageCnt * pageSize - physAddr;
		int amount = Math.min(length, remainings);
		System.arraycopy(memory, physAddr, data, offset, amount);

		// read next page
		if(length > remainings) {
			return amount + readVirtualMemory(vpn * pageSize + pageSize, data, offset + amount, length - amount);
		}

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int virtualMemorySize = pageTable.length * pageSize;
		if (vaddr < 0 || vaddr >= virtualMemorySize) {
			return 0;
		}
		
		int vpn = Processor.pageFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int pageOffset = Processor.offsetFromAddress(vaddr);
		int physAddr = ppn * Processor.pageSize + pageOffset;

		if (physAddr < 0 || physAddr >= memory.length || pageTable[vpn].readOnly) {
			return 0;
		}

		// check remaining space
		int physAddrLen = physAddr + 1;
        int pageCnt = physAddrLen / pageSize;
        if (physAddrLen % pageSize != 0) {
            pageCnt ++;
        }
        int remainings = pageCnt * pageSize - physAddr;
		int amount = Math.min(length, remainings);
		System.arraycopy(data, offset, memory, physAddr, amount);

		// read next page
		if(length > remainings) {
			return amount + writeVirtualMemory(vpn * pageSize + pageSize, data, offset + amount, length - amount);
		}

		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		bigLock.acquire();
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			bigLock.release();
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				
				// not enough memory
				if(UserKernel.physMemory.size() == 0) {
					coff.close();
					Lib.debug(dbgProcess, "\tinsufficient physical memory");
					bigLock.release();
					return false;
				}

				Integer ppn = UserKernel.physMemory.poll();
				boolean readOnly = section.isReadOnly();
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, readOnly, false, false);
				section.loadPage(i, ppn);
			}
		}

		// load stack/arg stack + arg = 9 pages, rest are coff
		for(int i = numPages - stackPages - 1; i < numPages; i ++) {
			// not enough memory
			if(UserKernel.physMemory.size() == 0) {
				coff.close();
				Lib.debug(dbgProcess, "\tinsufficient physical memory");
				bigLock.release();
				return false;
			}

			Integer ppn = UserKernel.physMemory.poll();
			pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);
		}
		bigLock.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// free memory
		bigLock.acquire();
		for(int i = 0; i < pageTable.length; i ++) {
			TranslationEntry entry = pageTable[i];
			if(entry != null && entry.valid) {
				UserKernel.physMemory.add(entry.ppn);
				pageTable[i] = null;
			}
		}
		bigLock.release();
		coff.close();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if(pid != 0) {
			return -1;
		}
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	protected int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");

		// clean up fileTable
		for(int i = 0; i < this.fileTable.length; i ++) {
			if(this.fileTable[i] != null) {
				this.fileTable[i].close();
			}
			this.fileTable[i] = null;
		}

		// check child
		while(this.childrenList.size() != 0) {
			UserProcess child = childrenList.poll();
			child.parent = null;
		}

		// clean memory
		unloadSections();
		
		// update parent 
		Lib.debug(dbgProcess, "has exception: " + hasException);
		if(this.parent != null && !hasException) {
			this.parent.childrenStatus.put(this.pid, status);
		}
		
		bigLock.acquire();
		numProc --;
		Lib.debug(dbgProcess, "numProc is: " + numProc);
		if(numProc > 0) {
			bigLock.release();
			Lib.debug(dbgProcess, "Process exit pid: " + pid);
			KThread.finish();
			return 0;
		}
		
		Lib.debug(dbgProcess, "Process exit pid: " + pid);
		bigLock.release();
		Kernel.kernel.terminate();
		return 0;
	}

	/**
	 * Helper function to check valid pointer
	 */
	private boolean isPtrValid(int ptr) {
		int maxMemoryAddr = this.numPages * Processor.pageSize;
		if(ptr <= 0 || ptr >= maxMemoryAddr) {
			return false;
		}
		return true;
	}
 
	/**
	 * Helper function to check file table
	 */
	private boolean isFileTableFull() {
		for(OpenFile file : this.fileTable) {
			if (file == null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Helper function to check file name
	 */
	private boolean isFileNameValid(String name) {
		if(name == null || name.length() == 0) {
			return false;
		}
		return true;
	}

	/**
	 * Helper function to check valid fd
	 */
	private boolean isFDValid(int fd) {
		if(fd < 0 || fd >= this.fileTable.length) {
			return false;
		}

		if(fileTable[fd] == null && pipeFdTable[fd] == null) {
			return false;
		}
		return true;
	}

	private boolean isPipe(String name) {
		if(name.startsWith("/pipe/") && name.length() > 6) {
			return true;
		}
		return false;
	}

	/**
	 * Handle the crate() system call.
	 */
	private int handleCreate(int namePtr) {
		// check null or wrong addr
		if(!isPtrValid(namePtr)) {
			return -1;
		}

		// check file descriptor table size
		if(isFileTableFull()) {
			return -1;
		}

		// check name validity
		String name = readVirtualMemoryString(namePtr, 256);
		if(!isFileNameValid(name)) {
			return -1;
		}

		// check pipe
		if(isPipe(name)) {
			if(pipeTable.size() >= 16) {
				return -1;
			}

			if(pipeTable.get(name) != null) {
				return -1;
			}

			pipeTableLock.acquire();
			pipeTable.put(name, new ArrayList(Processor.pageSize));
			int pipeFD = -1;
			for(int i = 0; i < fileTable.length; i ++) {
				if(fileTable[i] == null) {
					pipeFD = i;
					pipeFdTable[pipeFD] = name;
					pipeCVMap.put(name, pipeCV);
					break;
				}
			}
			pipeTableLock.release();
			Lib.debug(dbgProcess, "fd1: " + pipeFD);
			return pipeFD;
		}

		// if file exists, truncate
		OpenFile file = ThreadedKernel.fileSystem.open(name, true);
		if (file == null) {
			return -1;
		}

		for(int i = 0; i < FD_TABLE_SIZE; i ++) {
			if(this.fileTable[i] == null && pipeFdTable[i] == null) {
				this.fileTable[i] = file;
				return i;
			}
		}
		return -1;
	}

	/**
	 * Handle the open() system call.
	 */
	private int handleOpen(int namePtr) {
		// check null or wrong addr
		if(!isPtrValid(namePtr)) {
			return -1;
		}

		// check file descriptor table size
		if(isFileTableFull()) {
			return -1;
		}

		// check name validity
		String name = readVirtualMemoryString(namePtr, 256);
		if(!isFileNameValid(name)) {
			return -1;
		}

		// check pipe
		if(isPipe(name)) {
			if(pipeTable.get(name) == null) {
				return -1;
			}

			int pipeFD = -1;
			for(int i = 0; i < fileTable.length; i ++) {
				if(fileTable[i] == null) {
					pipeFdTable[i] = name; 
					pipeFD = i;
					pipeCV = pipeCVMap.get(name);
					break;
				}
			}
			return pipeFD;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(name, false);
		if (file == null) {
			return -1;
		}

		for(int i = 0; i < FD_TABLE_SIZE; i ++) {
			if(this.fileTable[i] == null && pipeFdTable[i] == null) {
				this.fileTable[i] = file;
				return i;
			}
		}
		return -1;
	}

	/**
	 * Handle the close() system call.
	 */
	private int handleClose(int fd) {
		if(!isFDValid(fd)) {
			return -1;
		}

		// pipe
		if(pipeFdTable[fd] != null) {
			pipeTable.remove(pipeFdTable[fd]);
			pipeCVMap.remove(pipeFdTable[fd]);
			pipeFdTable[fd] = null;
		}
		
		OpenFile file = fileTable[fd];
		file.close();
		fileTable[fd] = null;
		return 0;
	}

	/**
	 * Handle the unlink() system call.
	 */
	private int handleUnlink(int namePtr) {
		// check null or wrong addr
		if(!isPtrValid(namePtr)) {
			return -1;
		}

		// check name validity
		String name = readVirtualMemoryString(namePtr, 256);
		if(!isFileNameValid(name)) {
			return -1;
		}

		// check is actually removed
		boolean removed = ThreadedKernel.fileSystem.remove(name);
		if(!removed) {
			return -1;
		}

		return 0;
	}

	private int handleRead(int fd, int bufferPtr, int size) {
		// check fd
		if(!isFDValid(fd)) {
			Lib.debug(dbgProcess, "fd negative");
			return -1;
		}

		// check buffer ptr
		if(!isPtrValid(bufferPtr)) {
			return -1;
		}

		// check size
		if(size < 0) {
			return -1;
		}

		// check pipe
		if(pipeFdTable[fd] != null) {
			return handlePipeRead(fd, bufferPtr, size);
		}
		
		// read data from file
		OpenFile file = this.fileTable[fd];
		int remaining = size;
		int totalWriteCnt = 0;
		while(remaining > 0) {
			int pageSize = Processor.pageSize;
			byte[] content = new byte[pageSize];
			int readCnt = 0;

			// try to read from file
			readCnt = remaining > pageSize ? 
				file.read(content, 0, Processor.pageSize) : 
				file.read(content, 0, remaining);
			if(readCnt < 0) { 
				return -1; 
			}
			
			// write to the buffer
			int writeCnt = UserKernel
				.currentProcess()
				.writeVirtualMemory(bufferPtr, content, 0, readCnt);
			
			if (writeCnt <= 0) { 
				return totalWriteCnt; 
			}

			totalWriteCnt += writeCnt;
			if (writeCnt < readCnt) {
				return totalWriteCnt;
			}

			// finished writing, move bufferPtr to next write position
			remaining -= readCnt;
			bufferPtr += writeCnt;
		}
		return totalWriteCnt;
	}

	private int handlePipeRead(int fd, int bufferPtr, int size) {
		// read data from file
		String name = pipeFdTable[fd];
		List<Byte> pipeArray = pipeTable.get(name);
		int remaining = size;
		int totalWriteCnt = 0;
		
		// lock !!!
		rwLock.acquire();
		while(remaining > 0) {
			int pageSize = Processor.pageSize;
			int readCnt = 0;
			byte[] content = new byte[pageSize];

			while(pipeArray.size() == 0) {
				pipeCV.wakeAll();
				pipeCV.sleep();
			}
	
			// try to read from pointer
			int index = 0;
			while(pipeArray.size() > 0 && remaining > 0) {
				content[index] = pipeArray.remove(0);
				readCnt ++;
				index ++;
				remaining --;
			}

			// write to the buffer
			int writeCnt = UserKernel
				.currentProcess()
				.writeVirtualMemory(bufferPtr, content, 0, readCnt);

			if(readCnt != writeCnt) { 
				rwLock.release();
				return -1; 
			}
		
			// finished writing, move bufferPtr to next write position
			bufferPtr += writeCnt;
			totalWriteCnt += writeCnt;
		}
		rwLock.release();
		return totalWriteCnt;
	}

	private int handleWrite(int fd, int bufferPtr, int size) {
		if(size < 0) {
			return -1;
		}

		// check fd
		if(!isFDValid(fd)) {
			return -1;
		}

		// check buffer ptr
		if(!isPtrValid(bufferPtr)) {
			return -1;
		}

		// check pipe
		if(pipeFdTable[fd] != null) {
			return handlePipeWrite(fd, bufferPtr, size);
		}
	
		// read data from file
		OpenFile file = this.fileTable[fd];
		int remaining = size;
		int totalWriteCnt = 0;
		
		while(remaining > 0) {
			int pageSize = Processor.pageSize;
			byte[] content = new byte[pageSize];
			int readCnt = 0;
			
			// try to read from pointer
			readCnt = remaining > pageSize ? 
				UserKernel
				.currentProcess()
				.readVirtualMemory(bufferPtr, content, 0, pageSize) :
				UserKernel
				.currentProcess()
				.readVirtualMemory(bufferPtr, content, 0, remaining);

			if(readCnt <= 0) { return -1; }
			remaining -= readCnt;

			// write to the buffer
			int writeCnt = file.write(content, 0, readCnt);

			if (writeCnt <= 0) { 
				return totalWriteCnt; 
			}

			totalWriteCnt += writeCnt;
			if (writeCnt < readCnt) {
				return totalWriteCnt;
			}

			// finished writing, move bufferPtr to next write position
			bufferPtr += writeCnt;
		}

		return totalWriteCnt;
	}

	private int handlePipeWrite(int fd, int bufferPtr, int size) {
		// read data from file
		String name = pipeFdTable[fd];
		List<Byte> pipeArray = pipeTable.get(name);
		int remaining = size;
		int totalWriteCnt = 0;
		
		// lock !!!
		rwLock.acquire();
		while(remaining > 0) {
			int pageSize = Processor.pageSize;
			byte[] content = new byte[pageSize];
			int readCnt = 0;

			// try to read from pointer
			readCnt = remaining > pageSize ? 
				UserKernel
				.currentProcess()
				.readVirtualMemory(bufferPtr, content, 0, pageSize) :
				UserKernel
				.currentProcess()
				.readVirtualMemory(bufferPtr, content, 0, remaining);

				Lib.debug(dbgProcess, "readCnt " + readCnt);
			if(readCnt <= 0) { 
				rwLock.release();
				return -1; 
			}

			// write to the buffer
			int index = 0;
			int writeCnt = 0;
			while(pipeArray.size() < pageSize) {
				if(remaining == 0) {
					rwLock.release();
					Lib.debug(dbgProcess, "totalWriteCnt " + totalWriteCnt);
					return totalWriteCnt;
				}
				pipeArray.add(content[index]);
				index ++;
				writeCnt ++;
				totalWriteCnt ++;
				remaining --;
				pipeCV.wakeAll();
			}

			pipeCV.sleep();
		
			// finished writing, move bufferPtr to next write position
			bufferPtr += writeCnt;
		}
		rwLock.release();
		Lib.debug(dbgProcess, "totalWriteCnt " + totalWriteCnt);
		return totalWriteCnt;
	}

	private int handleExec(int filePtr, int argc, int argvPtr) {
		// check pointer and argument pointer
		if(!isPtrValid(filePtr)) {
			return -1;
		}

		if(argc < 0 || (argc > 0 && argvPtr == 0)) {
			return -1;
		}

		String fileName = readVirtualMemoryString(filePtr, 256);
		if(!isFileNameValid(fileName) || !fileName.endsWith(".coff")) {
			return -1;
		}
		
		// read argument names from argvPtr
		int offset = 0;
		String[] argv = new String[argc];
		for(int i = 0; i < argc; i ++) {
			byte[] charPtr = new byte[4];
			int nextArgAddr = argvPtr + offset;
			int readCnt = readVirtualMemory(nextArgAddr, charPtr);
			if(readCnt != 4) {return -1;}
			
			// read the argument out
			int charPtrInt = Lib.bytesToInt(charPtr, 0);
			String argument = readVirtualMemoryString(charPtrInt, 256);
			
			if (!isFileNameValid(argument)) {
				return -1;
			}

			argv[i] = argument;
			offset += 4;
		}

		// create new process
		UserProcess newProc = newUserProcess();
		newProc.parent = this;
		this.childrenList.add(newProc);

		// execute
		Lib.debug(dbgProcess, "current process pid: "+ this.pid);
		Lib.debug(dbgProcess, "new process name: " + fileName);
		Lib.debug(dbgProcess, "new process pid: "+ newProc.pid);
		Lib.debug(dbgProcess, "new process parent: "+ newProc.parent.pid);
		boolean success = newProc.execute(fileName, argv);
		if(!success) {
			bigLock.acquire();
			numProc --;
			bigLock.release();
			return -1;
		}
		return newProc.pid;
	}

	// lock?
	private int handleJoin(int pid, int statusPtr) {
		if(!isPtrValid(statusPtr) || pid < 0) {
			return -1;
		}

		UserProcess child = null;		
		for(int i = 0; i < childrenList.size(); i ++) {
			if(((List<UserProcess>)childrenList).get(i).pid == pid) {
				child = ((List<UserProcess>)childrenList).get(i);
				((List<UserProcess>)childrenList).remove(i);
			}
		}
		
		if(child == null) {
			Lib.debug('a', KThread.currentThread().getName() + " child is null");
			return -1;
		}

		// join child, suspend
		child.thread.join();

		// resume
		Integer childStatus = this.childrenStatus.get(pid);
		Lib.debug('a', KThread.currentThread().getName() + "child status: " + childStatus);
		// unhandled exception
		if(childStatus == null) {
			Lib.debug('a', KThread.currentThread().getName() + " child has exception");
			return 0;
		}

		byte[] statusByte = Lib.bytesFromInt(childStatus);
		int writeCnt = writeVirtualMemory(statusPtr, statusByte);
		if(writeCnt != 4) {
			return -1;
		}		
		
		// exit 
		return 1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ pid);
			hasException = true;
			handleExit(-1);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
}
