package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	int[] coffTable;
	int coffPageCnt = 0;
	Lock bigLock2 = VMKernel.bigLock2;

	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		pageTable = new TranslationEntry[numPages];
		this.coffTable = new int[numPages];
		
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages) VMProcess");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				coffTable[vpn] = s;
				boolean readOnly = section.isReadOnly();
				pageTable[vpn] = new TranslationEntry(-1, -1, false, readOnly, false, false);
				coffPageCnt ++;
			}
		}

		// load stack/arg stack + arg = 9 pages, rest are coff
		for(int i = numPages - stackPages - 1; i < numPages; i ++) {
			pageTable[i] = new TranslationEntry(-1, -1, false, false, false, false);
		}
		return true;
	}

	// handle swapped out page (coff, stack)
	private void handleSwappedPage(TranslationEntry PTE) {
		PTE.dirty = false;
		VMKernel.readFromSwap(PTE.ppn, PTE.vpn);
	}

	// handle readOnly/clean coff (code section)
	private void handleCleanCoff(int secNum, int vpn, int ppn) {
		VMKernel.pin(ppn);
		CoffSection coffSec = coff.getSection(secNum);
		int secppn = vpn - coffSec.getFirstVPN();
		coffSec.loadPage(secppn, ppn);
		VMKernel.unpin(ppn);
	}

	// handle new stack page -> zero init
	private void handleNewStackPage(byte[] memory, int ppn) {
		VMKernel.pin(ppn);
		byte[] zeroMemory = new byte[pageSize];
		System.arraycopy(zeroMemory, 0, memory, pageSize * ppn, pageSize);
		VMKernel.unpin(ppn);
	}

	protected void requestPage(int vpn) {
		Lib.debug(dbgProcess, "request page with vpn: " + vpn + " phys memory size: " + VMKernel.physMemory.size());

		// get physical page: evict or use free memory
		int ppn = -1;
		if(VMKernel.physMemory.size() == 0) {
			ppn = VMKernel.getNextPageClock();
		} else {
			ppn = VMKernel.physMemory.poll();
		}
		Lib.assertTrue(ppn >= 0, "requested physical page number should >= 0");
		
		VMKernel.invertedPageTable[ppn].owner = this;
		VMKernel.invertedPageTable[ppn].PTE = pageTable[vpn];
		pageTable[vpn].valid = true;
		pageTable[vpn].ppn = ppn;
		pageTable[vpn].used = true;

		// check whether coff or stack/args, if coff, load it, if stack/args, 0 init (unswapped out pages)
		int swapPageNum = pageTable[vpn].vpn;
		if(swapPageNum >= 0) {
			handleSwappedPage(pageTable[vpn]);
		} else if((coffPageCnt > vpn && swapPageNum < 0) || pageTable[vpn].readOnly) {
			handleCleanCoff(coffTable[vpn], vpn, ppn);
		} else {
			handleNewStackPage(Machine.processor().getMemory(), ppn);
		}

		return;
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		bigLock2.acquire();
		int res = readVirtualMemoryRecursive(vaddr, data, offset, length);
		bigLock2.release();
		return res;
	}

	public int readVirtualMemoryRecursive(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int virtualMemorySize = pageTable.length * pageSize;
		if (vaddr < 0 || vaddr >= virtualMemorySize) {
			return 0;
		}
		
		int vpn = Processor.pageFromAddress(vaddr);

		// handle page fault
		if(!pageTable[vpn].valid) {
			requestPage(vpn);
		}
		
		int ppn = pageTable[vpn].ppn;
		VMKernel.pin(ppn);
		pageTable[vpn].used = true;
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
		VMKernel.unpin(ppn);

		// read next page
		if(length > remainings) {
			return amount + readVirtualMemoryRecursive(vpn * pageSize + pageSize, data, offset + amount, length - amount);
		}

		return amount;
	}


	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		bigLock2.acquire();
		int res = writeVirtualMemoryRecursive(vaddr, data, offset, length);
		bigLock2.release();
		return res;
	}

	public int writeVirtualMemoryRecursive(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int virtualMemorySize = pageTable.length * pageSize;
		if (vaddr < 0 || vaddr >= virtualMemorySize) {
			return 0;
		}
		
		int vpn = Processor.pageFromAddress(vaddr);

		if(!pageTable[vpn].valid) {
			requestPage(vpn);
		}

		int ppn = pageTable[vpn].ppn;
		VMKernel.pin(ppn);
		pageTable[vpn].used = true;
		pageTable[vpn].dirty = true;
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
		VMKernel.unpin(ppn);

		// read next page
		if(length > remainings) {
			return amount + writeVirtualMemoryRecursive(vpn * pageSize + pageSize, data, offset + amount, length - amount);
		}

		return amount;
	}

	protected int handleExit(int status) {
		bigLock2.acquire();
		Lib.debug('f', "I exited.");
		for(int i = 0; i < pageTable.length; i ++) {
			if(pageTable[i].vpn != -1) {
				VMKernel.swapList.add(pageTable[i].vpn);
			}
		}
		bigLock2.release();
		return super.handleExit(status);
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		switch (cause) {
		case Processor.exceptionPageFault:
			bigLock2.acquire();
			int virtualAddr = Machine.processor().readRegister(Processor.regBadVAddr);
			int vpn = Processor.pageFromAddress(virtualAddr);
			requestPage(vpn);
			bigLock2.release();
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
