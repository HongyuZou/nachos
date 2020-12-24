package nachos.threads;

import java.util.*;
import java.util.function.IntSupplier;
import nachos.machine.*;

/**
 * A <i>Future</i> is a convenient mechanism for using asynchonous
 * operations.
 */
public class Future {
    KThread thread;
    Lock lock;
    boolean joined; 
    int result;

    /**
     * Instantiate a new <i>Future</i>.  The <i>Future</i> will invoke
     * the supplied <i>function</i> asynchronously in a KThread.  In
     * particular, the constructor should not block as a consequence
     * of invoking <i>function</i>.
     */
    Future (IntSupplier function) {
        // fork thread, -> function()
        // currentThread ->         Future()
        // run().get()             
        // block                   
        // get()                    
        // value                    finished
        joined = false;
        lock = new Lock();
        this.thread = new KThread( new Runnable () {
			public void run() {
                result = function.getAsInt();
                // Simulate long function
                // ThreadedKernel.alarm.waitUntil(1000000);
			}
        });
        this.thread.setName("future").fork();
    }

    /**
     * Return the result of invoking the <i>function</i> passed in to
     * the <i>Future</i> when it was created.  If the function has not
     * completed when <i>get</i> is invoked, then the caller is
     * blocked.  If the function has completed, then <i>get</i>
     * returns the result of the function.  Note that <i>get</i> may
     * be called any number of times (potentially by multiple
     * threads), and it should always return the same value.
     */
    public int get () {
        // Monitor Style
        lock.acquire();
        // Only first one join
        if (!joined) {
            joined = true; // prevent future join
            thread.join(); // wait until thread done
        }
        // Set value
        int value = this.result;
        // Monitor Style
        lock.release();
	    return value;
    }

    public static void futureTest1() {
        // Create a IntSupplier instance 
        IntSupplier sup = () -> (int)(Math.random() * 10);
        long t0 = Machine.timer().getTime();
        Future future = new Future(sup);
        
        // for(int i = 0; i < 10; i ++) {
        //     KThread.yield();
        // }

        System.out.println(future.get());
        long t1 = Machine.timer().getTime();
        System.out.println("elapsed time: " + (t1 - t0));
    }

    public static void futureTest2() {
        // Create a IntSupplier instance 
        IntSupplier sup = () -> (int)(100);
        Future future = new Future(sup);
        
        // for(int i = 0; i < 1; i ++) {
        //     KThread.yield();
        // }
        
        long t0 = Machine.timer().getTime();
        System.out.println(future.get());
        long t1 = Machine.timer().getTime();
        System.out.println("elapsed time: " + (t1 - t0));
        System.out.println("again " + future.get());
        long t2 = Machine.timer().getTime();
        System.out.println("again elapsed time: " + (t2 - t1));
    }

    public static void futureTest3() {
        // Create a IntSupplier instance 
        IntSupplier sup = () -> (int)(Math.random() * 100);
        Future future = new Future(sup);
        KThread child2 = new KThread( new Runnable () {
			public void run() {
                long t0 = Machine.timer().getTime();
                System.out.println("child2 " + future.get());
                long t1 = Machine.timer().getTime();
                System.out.println("child 2 elapsed time: " + (t1 - t0));
                long t2 = Machine.timer().getTime();
                System.out.println("child 2 get again : " + future.get() + " " + (t2 - t1));
			}
        });
        child2.setName("child2").fork();
        
        // for(int i = 0; i < 1; i ++) {
        //     KThread.yield();
        // }

        child2.join();
        long t0 = Machine.timer().getTime();
        System.out.println("main: " + future.get());
        long t1 = Machine.timer().getTime();
        System.out.println("main: elapsed time: " + (t1 - t0));
        long t2 = Machine.timer().getTime();
        System.out.println("main: get again : " + future.get() + " " + (t2 - t1));
    }
}
