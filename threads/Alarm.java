package nachos.threads;

import java.util.PriorityQueue;
import java.util.*;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	Queue<WaitThread> waitQueue = new PriorityQueue<>();
	class WaitThread implements Comparable<WaitThread> {
		KThread thread;
		long wakeTime;

		/**
		 * Constructor
		 * @param thread waiting thread
		 * @param wakeTime wake time of the waiting thread
		 */
		WaitThread(KThread thread, long wakeTime) {
			this.thread = thread;
			this.wakeTime = wakeTime;
		}

		/**
		 * implement the compareTo interface of comparable
		 */
		@Override
		public int compareTo(WaitThread thread) {
			if(this.wakeTime > thread.wakeTime) {
				return 1;
			} else {
				return -1;
			}
		}

		@Override
		public boolean equals(Object obj) {
			return this.thread == ((WaitThread)obj).thread;
		}
	} 

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		while (this.waitQueue.peek() != null && 
			   this.waitQueue.peek().wakeTime <= Machine.timer().getTime()) {
			// check if thread should be ready
			WaitThread waitThread = this.waitQueue.poll();
			KThread thread = waitThread.thread;
			Lib.debug('t', "wake thread status: " + thread.status);
			thread.ready();
		}
		KThread.yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		
		// while (wakeTime > Machine.timer().getTime())
		// 	KThread.yield();

		// disable interrupt to ensure atomicity
		boolean originalStatus = Machine.interrupt().disable();
		KThread curThread = KThread.currentThread();
		long wakeTime = Machine.timer().getTime() + x;

		// put curThread into wait queue and sleep
		WaitThread waitThread = new WaitThread(curThread, wakeTime);
		this.waitQueue.add(waitThread);
		Lib.debug('t', "sleep thread status: " + waitThread.thread.status);
		KThread.sleep();
		
		Machine.interrupt().restore(originalStatus);
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
    public boolean cancel(KThread thread) {
		boolean originalStatus = Machine.interrupt().disable();
		WaitThread waitThread = new WaitThread(thread, 0);
		
		// if contains thread, move it to ready set
		if(this.waitQueue.contains(waitThread)) {
			this.waitQueue.remove(waitThread);
			thread.ready();
			Machine.interrupt().restore(originalStatus);
			return true;
		}
		Machine.interrupt().restore(originalStatus);
		return false;
	}

	/*
	 * Test cases 
	 */
	public static void alarmTest1() {
		int durations[] = {1000, 0, 1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static void alarmTest2() {
		KThread thread1 = new KThread(new Runnable() {
			public void run() {
				System.out.println("thread 1 " +  Machine.timer().getTime());
				ThreadedKernel.alarm.waitUntil(2000);
				System.out.println("thread 1 waked" + Machine.timer().getTime());
			}
		});

		KThread thread2 = new KThread(new Runnable() {
			public void run() {
				System.out.println("thread 2"+  Machine.timer().getTime());
				ThreadedKernel.alarm.waitUntil(3000);
				System.out.println("thread 2 waked"+ Machine.timer().getTime());
			}
		});

		thread2.setName("thread 2").fork();
		thread1.setName("thread 1").fork();

		thread1.join();
		thread2.join();
	}
}
