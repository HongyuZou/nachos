/*
 * exec1.c
 *
 * Simple program for testing exec.  It does not pass any arguments to
 * the child.
 */

#include "syscall.h"
#include "stdio.h"

int
main (int argc, char *argv[])
{
    char *prog = "write1.coff";
    char *prog2 = "write2.coff";
    int pid;
    
    for(int i = 0; i < 6; i ++) {
        if (i % 2 == 0) {
            pid = exec (prog, 0, 0);
        } else {
            pid = exec (prog2, 0, 0);
        }
        
        printf("pid is: %d\n", pid);
        if (pid < 0) {
	        exit (-1);
        }
    }
    
    exit (0);
}