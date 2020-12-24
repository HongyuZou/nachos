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
    char *prog2 = "write3.coff";
    int pid;

    for(int i = 0; i < 2; i ++) {
        pid = exec ("dungen-gen.coff", 0, 0);
        printf("pid is: %d\n", pid);
        if (pid < 0) {
	        exit (-1);
        }
    }

    exit (0);
}