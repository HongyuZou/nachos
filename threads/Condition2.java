package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		this.sleepQueue = new LinkedList<>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// prevent context switch
		boolean intStatus = Machine.interrupt().disable();
		conditionLock.release();
		
		this.sleepQueue.add(new WaitThread(KThread.currentThread(), -1));
		KThread.sleep();

		// execute after waken by other thread
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
		return;
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
		if(this.sleepQueue.peek() != null) {
			WaitThread waitThread = sleepQueue.poll();
			boolean isCancelled = ThreadedKernel.alarm.cancel(waitThread.thread);

			// haven't added to the alarm queue and is not found, is not handled by alarm cancel()
			if(!isCancelled && (waitThread.wakeTime == -1)) {
				waitThread.thread.ready();
			} 
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		while(this.sleepQueue.peek() != null) {
			WaitThread waitThread = sleepQueue.poll();
			boolean isCancelled = ThreadedKernel.alarm.cancel(waitThread.thread);

			// wakeTime > machine time, will be waken
			// wakeTime < machine time -> already wake in cancel()
			// haven't added to the alarm queue and is not found, is not handled by alarm cancel()
			if(!isCancelled && (waitThread.wakeTime == -1)) {
				waitThread.thread.ready();
			} 
		}
		Machine.interrupt().restore(intStatus);
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
    public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		this.conditionLock.release();
		long wakeTime = Machine.timer().getTime() + timeout;
		WaitThread waitThread = new WaitThread(KThread.currentThread(), wakeTime);
		this.sleepQueue.add(waitThread);
		ThreadedKernel.alarm.waitUntil(timeout);

		// wake before timedout -> handled in wake/wakeAll, removed from queue
		// wake after timedout -> not handled, need to manually removed from queue
		long currentTime = Machine.timer().getTime();
		if(wakeTime <= currentTime) {
			this.sleepQueue.remove(waitThread);
		}
		
		// wake 
		this.conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

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

	private Lock conditionLock;
	public Queue<WaitThread> sleepQueue;

	private static class InterlockTest {
        private static Lock lock;
        private static Condition2 cv;

        private static class Interlocker implements Runnable {
            public void run () {
                lock.acquire();
                for (int i = 0; i < 10; i++) {
                    System.out.println(KThread.currentThread().getName());
                    cv.wake();   // signal
                    cv.sleep();  // wait
                }
                lock.release();
            }
        }

        public InterlockTest () {
            lock = new Lock();
            cv = new Condition2(lock);

            KThread ping = new KThread(new Interlocker());
            ping.setName("ping");
            KThread pong = new KThread(new Interlocker());
            pong.setName("pong");

            ping.fork();
            pong.fork();

            // We need to wait for ping to finish, and the proper way
            // to do so is to join on ping.  (Note that, when ping is
            // done, pong is sleeping on the condition variable; if we
            // were also to join on pong, we would block forever.)
            // For this to work, join must be implemented.  If you
            // have not implemented join yet, then comment out the
            // call to join and instead uncomment the loop with
            // yields; the loop has the same effect, but is a kludgy
            // way to do it.
            ping.join();
            // for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
        }
	}

	public static void selfTest() {
        new InterlockTest();
	}
		
	private static void sleepForTest1 () {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
	
		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
					" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}

	public static void sleepForTest2 () {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread child1 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println("thread 1 runnning");
				cv.sleepFor(1000000);
				long t1 = Machine.timer().getTime();
				System.out.println("thread 1 runnning again time: " + (t1 - t0));
				lock.release();
			}
		});
	
		child1.setName("child 1").fork();
		for(int i = 0; i < 2; i ++) {
			KThread.yield();
		}

		KThread.yield();
		System.out.println("parent wake child");
		lock.acquire();
		cv.wake();
		lock.release();
		child1.join();
	}

	public static void sleepForTest3 () {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread child1 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println("thread 1 runnning");
				cv.sleepFor(1000);
				long t1 = Machine.timer().getTime();
				System.out.println("thread 1 runnning again time: " + (t1 - t0));
				lock.release();
			}
		});

		KThread child2 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println("thread 2 runnning");
				cv.sleepFor(1000000);
				long t1 = Machine.timer().getTime();
				System.out.println("thread 2 runnning again time: " + (t1 - t0));
				lock.release();
			}
		});

		KThread child3 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println("thread 3 runnning");
				cv.sleepFor(1500000);
				long t1 = Machine.timer().getTime();
				System.out.println("thread 3 runnning again time: " + (t1 - t0));
				lock.release();
			}
		});
	
		child1.setName("child 1").fork();
		child2.setName("child 2").fork();
		child3.setName("child 3").fork();
		for(int i = 0; i < 3; i ++) {
			KThread.yield();
		}

		KThread.yield();
		System.out.println("parent wake child");
		lock.acquire();
		cv.wakeAll();
		lock.release();
		child1.join();
		child2.join();
		child3.join();
	}
	
	public static void selfTest2() {
		sleepForTest1();
	}


	public static void cvTest1() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread child1 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				System.out.println("thread 1 runnning");
				cv.sleep();
				System.out.println("thread 1 runnning again");
				lock.release();
			}
		});

		KThread child2 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				System.out.println("thread 2 runnning");
				cv.sleep();
				System.out.println("thread 2 runnning again");
				lock.release();
			}
		});

		child1.setName("child1").fork();
		child2.setName("child2").fork();
		
		for(int i = 0; i < 10; i ++) {
			System.out.println("parent yieding ... ");
			KThread.yield();
		}
		
		System.out.println("wake 1st one");
		lock.acquire();
		cv.wake();
		lock.release();
		System.out.println("wake 2nd one");
		lock.acquire();
		cv.wake();
		lock.release();
		child1.join();
		child2.join();
	}

	public static void cvTest2() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread child1 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				System.out.println("thread 1 runnning");
				cv.sleep();
				System.out.println("thread 1 runnning again");
				lock.release();
			}
		});

		KThread child2 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				System.out.println("thread 2 runnning");
				cv.sleep();
				System.out.println("thread 2 runnning again");
				lock.release();
			}
		});

		child1.setName("child1").fork();
		child2.setName("child2").fork();
		
		for(int i = 0; i < 10; i ++) {
			System.out.println("parent yieding ... ");
			KThread.yield();
		}
		
		System.out.println("wake all");
		lock.acquire();
		cv.wakeAll();
		lock.release();
		child1.join();
		child2.join();
	}

	public static void cvTest3() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread child1 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				System.out.println("thread 1 runnning");
				cv.sleep();
				System.out.println("thread 1 runnning again");
				lock.release();
			}
		});

		KThread child2 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				System.out.println("thread 2 runnning");
				cv.sleep();
				System.out.println("thread 2 runnning again");
				lock.release();
			}
		});

		child1.setName("child1").fork();
		child2.setName("child2").fork();
		
		for(int i = 0; i < 10; i ++) {
			System.out.println("parent yieding ... ");
			KThread.yield();
		}
		
		System.out.println("wake all");
		cv.wakeAll();
		lock.release();
		child1.join();
		child2.join();
	}

	public static void cvTest4() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread child1 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				System.out.println("thread 1 runnning");
				cv.sleep();
				System.out.println("thread 1 runnning again");
				lock.release();
			}
		});

		KThread child2 = new KThread( new Runnable () {
			public void run() {
				//KThread.yield();
				lock.acquire();
				System.out.println("thread 2 runnning");
				cv.sleep();
				System.out.println("thread 2 runnning again");
				lock.release();
			}
		});

		lock.acquire();
		cv.wakeAll();
		lock.release();

		child1.setName("child1").fork();
		child2.setName("child2").fork();
		
		for(int i = 0; i < 10; i ++) {
			System.out.println("parent yieding ... ");
			KThread.yield();
		}
	}

	public static void cvTest5() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}


}
