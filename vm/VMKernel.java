package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	// global memory accounting -> inverted page table
	// process <- page
	class pageMeta {
		VMProcess owner;
		boolean isPinned;
		TranslationEntry PTE;
	}

	public static pageMeta[] invertedPageTable = new pageMeta[Machine.processor().getNumPhysPages()]; 
	public static Lock bigLock2;
	public static Condition pinnedCV;
	public static OpenFile swapFile;
	public static Queue<Integer> swapList = new LinkedList<>(); // how many pages can be written to swap file
	public static int swapSize = 30;
	public static int clockptr = 0;

	private static boolean ifAllPinned() {
		boolean allPinned = true;
		for(int i = 0; i < invertedPageTable.length; i ++) {
			if(!invertedPageTable[i].isPinned) {
				allPinned = false;
				break;
			}
		}
		return allPinned;
	}

	// if need to expand, expand, else do nothing
	private static void checkExpandSwap() {
		// Lib.debug('f', "swapList's size is " + swapList.size());
		if(swapList.size() == 0) {
			int originalSize = swapSize;
			swapSize += swapSize;
			for(int i = originalSize; i < swapSize; i ++) {
				swapList.add(i);
			}
			Lib.debug('f', "Doubling swapSize from " + originalSize + " to " + swapSize);
		}
		return;
	}

	// use clock algorithm to get next page
	public static int getNextPageClock() {
		// all pinned, sleep, will be waken when someone call unpin()
		while(ifAllPinned()) {
			pinnedCV.sleep();
		}

		// clock
		while(invertedPageTable[clockptr].isPinned || invertedPageTable[clockptr].PTE.used) {
			invertedPageTable[clockptr].PTE.used = false;
			clockptr = (++clockptr) % invertedPageTable.length;
			Lib.debug('a', "inverted page table: " + invertedPageTable[clockptr].PTE);
		}
		
		pageMeta evictPage = invertedPageTable[clockptr];
		clockptr = (++clockptr) % invertedPageTable.length;

		// dirty, write to swap
		if(evictPage.PTE.dirty) {
			writeToSwap(evictPage.PTE.ppn, evictPage.PTE);
		}
		
		evictPage.PTE.valid = false;
		return evictPage.PTE.ppn;
	}

	// don't forget to pin/unpin
	public static void writeToSwap(int ppn, TranslationEntry PTE) {
		// pin
		pin(ppn);
		checkExpandSwap();
		// poll page from swap list, process, vpn -> spn
		int pageSize = Processor.pageSize;
		int swapPageNum = swapList.poll();
		
		PTE.vpn = swapPageNum;
		byte[] memory = Machine.processor().getMemory();
		int res = swapFile.write(swapPageNum * pageSize, memory, ppn * pageSize, pageSize);
		
		if(res != pageSize) {
			Lib.assertNotReached("swap out faliure: size not equal");
		}

		//unpin
		unpin(ppn);
	}

	// if stack/coff is written and is swapped to swap
	// everytime you read <- swap read
	public static void readFromSwap(int ppn, int swapPageNum) {
		// pin
		pin(ppn);
		int pageSize = Processor.pageSize;
		byte[] memory = Machine.processor().getMemory();
		int res = swapFile.read(swapPageNum * pageSize, memory, ppn * pageSize, pageSize);
		if(res != pageSize) {
			Lib.assertNotReached("swap in faliure: size not equal");
		}
		// unpin
		unpin(ppn);
	}

	// pin must hold the lock 
	public static void pin(int ppn) {
		Lib.assertTrue(bigLock2.isHeldByCurrentThread());
		invertedPageTable[ppn].isPinned = true;
	}

	// pin must hold the lock 
	public static void unpin(int ppn) {
		Lib.assertTrue(bigLock2.isHeldByCurrentThread());
		invertedPageTable[ppn].isPinned = false;
		pinnedCV.wakeAll();
	}

	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		
		for(int i = 0; i < invertedPageTable.length; i ++) {
			invertedPageTable[i] = new pageMeta();
		}

		// if file exists, truncate
		swapFile = ThreadedKernel.fileSystem.open("swap", true);
		Lib.assertTrue(swapFile != null, "cannot create swap");

		// init swap list, default 30 pages
		for(int i = 0; i < 30; i++) {
			swapList.add(i);
		}

		bigLock2 = new Lock();
		pinnedCV = new Condition(bigLock2);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapFile.close();
		boolean removed = ThreadedKernel.fileSystem.remove("swap");
		Lib.assertTrue(removed);
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
}
