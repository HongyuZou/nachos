package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {
    
    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
    abilityExpert = 3;
    
    public int numPlayersInMatch;

    // match number
    public int matchNum = 0;
    public Lock matchNumLock;

    // condition for rank and play number in each rank
    public Condition[] condArr;
    public int[] playerNumArr = {0,0,0};
    public Lock[] playerNumLock = {new Lock(), new Lock(), new Lock()};

    // map for match number
    public ArrayList<Integer>[] matchNumMap = new ArrayList[3];
    public Lock[] matchNumMapLock = {new Lock(), new Lock(), new Lock()};
    

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {
        this.matchNumLock = new Lock();
        this.numPlayersInMatch = numPlayersInMatch;
        this.condArr = new Condition[3];
        for(int i = 0; i < 3; i ++) {
            condArr[i] = new Condition(playerNumLock[i]);
            this.matchNumMap[i] = new ArrayList<Integer>();
        }
    }

    /**
     * Wait for the required number of player threads of the same
     * ability to form a game match, and only return when a game match
     * is formed.  Many matches may be formed over time, but any one
     * player thread can be assigned to only one match.
     *
     * Returns the match number of the formed match.  The first match
     * returned has match number 1, and every subsequent match
     * increments the match number by one, independent of ability.  No
     * two matches should have the same match number, match numbers
     * should be strictly monotonically increasing, and there should
     * be no gaps between match numbers.
     * 
     * @param ability should be one of abilityBeginner, abilityIntermediate,
     * or abilityExpert; return -1 otherwise.
     */
    public int play (int ability) {
        // if not correc ability, return
        if (ability != 1 && ability != 2 && ability != 3){
            return -1;
        }

        // acquire num lock, if not have N players, sleep
        int idx = ability - 1;
        this.playerNumLock[idx].acquire();
        this.playerNumArr[idx] += 1;
        
        // Early ppl
        this.matchNumMapLock[idx].acquire();
        int matchNumIdx = this.matchNumMap[idx].size();
        int matchNumLocal = -1;
        this.matchNumMapLock[idx].release();
        if(playerNumArr[idx] < this.numPlayersInMatch) {
            condArr[idx].sleep();

            // other threads wake up for match, get match num from map
            this.playerNumLock[idx].release();
            
            // store to match num map for other threads to acquire
            this.matchNumMapLock[idx].acquire();
            matchNumLocal = this.matchNumMap[idx].get(matchNumIdx);
            this.matchNumMapLock[idx].release();
            return matchNumLocal;
        // Last one
        } else {
            condArr[idx].wakeAll();
            this.playerNumArr[idx] = 0;
            this.playerNumLock[idx].release();

            // increase match num and return
            this.matchNumLock.acquire();
            this.matchNum += 1;

            // store to match num map for other threads to acquire
            this.matchNumMapLock[idx].acquire();
            this.matchNumMap[idx].add(this.matchNum);
            matchNumLocal = this.matchNum;
            this.matchNumMapLock[idx].release();
            
            this.matchNumLock.release();
            return matchNumLocal;
        }
    }

    public static void matchTest4 () {
        final GameMatch match = new GameMatch(2);
    
        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
            });
        beg1.setName("B1");
    
        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
            });
        beg2.setName("B2");
    
        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                Lib.assertNotReached("int1 should not have matched!");
            }
            });
        int1.setName("I1");
    
        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                Lib.assertNotReached("exp1 should not have matched!");
            }
            });
        exp1.setName("E1");
    
        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();
    
        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 10; i++) {
            KThread.currentThread().yield();
        }
        beg1.join();
        beg2.join();
    }

    public static void matchTest1 () {
        final GameMatch match = new GameMatch(2);
    
        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
            });
        beg1.setName("B1");
    
        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
            });
        beg2.setName("B2");
    
        KThread beg3 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg3 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 2, "expected match number of 1");
            }
            });
        beg3.setName("B3");
    
        KThread beg4 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg4 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 2, "expected match number of 1");
            }
            });
        beg4.setName("B4");
    
        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        beg2.fork();
       
        // Assume join is not implemented, use yield to allow other
        // threads to run
        beg1.join();
        beg2.join();

        beg3.fork();
        beg4.fork();

        beg3.join();
        beg4.join();
    }

    public static void matchTest2 () {
        final GameMatch match = new GameMatch(2);
        KThread[] arr = new KThread[10];

        // Instantiate the threads
        for(int i = 0; i < 10; i ++) {
            KThread beg1 = new KThread( new Runnable () {
                public void run() {
                    int r = match.play(GameMatch.abilityBeginner);
                    System.out.println ("matched " + "matchNum: " + r);
                    // beginners should match with a match number of 1
                }
                });
            beg1.setName("B" + i);
            arr[i] = beg1;
        }

        for (KThread ele : arr) {
            ele.fork();
        } 

        for (KThread ele : arr) {
            ele.join();
        } 
    }

    public static void matchTest3 () {
        final GameMatch match = new GameMatch(2);
        KThread[] arr = new KThread[12];

        // Instantiate the threads
        for(int i = 0; i < 4; i ++) {
            KThread beg1 = new KThread( new Runnable () {
                public void run() {
                    int r = match.play(GameMatch.abilityBeginner);
                    System.out.println ("matched " + "matchNum: " + r);
                    // beginners should match with a match number of 1
                }
                });
            beg1.setName("A" + i);
            arr[i] = beg1;
        }

        // Instantiate the threads
        for(int i = 4; i < 8; i ++) {
            KThread beg1 = new KThread( new Runnable () {
                public void run() {
                    int r = match.play(GameMatch.abilityExpert);
                    System.out.println ("matched " + "matchNum: " + r);
                    // beginners should match with a match number of 1
                }
                });
            beg1.setName("B" + i);
            arr[i] = beg1;
        }

        // Instantiate the threads
        for(int i = 8; i < 12; i ++) {
            KThread beg1 = new KThread( new Runnable () {
                public void run() {
                    int r = match.play(GameMatch.abilityIntermediate);
                    System.out.println ("matched " + "matchNum: " + r);
                    // beginners should match with a match number of 1
                }
                });
            beg1.setName("C" + i);
            arr[i] = beg1;
        }

        for (KThread ele : arr) {
            ele.fork();
        } 

        for (KThread ele : arr) {
            ele.join();
        } 
    }

    public static void matchTest5 () {
        final GameMatch match = new GameMatch(3);
        KThread[] arr = new KThread[18];

        // Instantiate the threads
        for(int i = 0; i < 6; i ++) {
            KThread beg1 = new KThread( new Runnable () {
                public void run() {
                    int r = match.play(GameMatch.abilityBeginner);
                    System.out.println (GameMatch.abilityBeginner + " matched " + "matchNum: " + r);
                    // beginners should match with a match number of 1
                }
                });
            beg1.setName("A" + i);
            arr[i] = beg1;
        }

        // Instantiate the threads
        for(int i = 6; i < 9; i ++) {
            KThread beg1 = new KThread( new Runnable () {
                public void run() {
                    int r = match.play(GameMatch.abilityExpert);
                    System.out.println (GameMatch.abilityExpert + " matched " + "matchNum: " + r);
                    // beginners should match with a match number of 1
                }
                });
            beg1.setName("B" + i);
            arr[i] = beg1;
        }

        // Instantiate the threads
        for(int i = 9; i < 18; i ++) {
            KThread beg1 = new KThread( new Runnable () {
                public void run() {
                    int r = match.play(GameMatch.abilityIntermediate);
                    System.out.println (GameMatch.abilityIntermediate + " matched " + "matchNum: " + r);
                    // beginners should match with a match number of 1
                }
                });
            beg1.setName("C" + i);
            arr[i] = beg1;
        }

        for (KThread ele : arr) {
            ele.fork();
        } 

        for (KThread ele : arr) {
            ele.join();
        } 
    }
}
