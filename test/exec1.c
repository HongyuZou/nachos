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
    char *prog = "exit1.coff";
    int pid;
    printf("12345\n");
    pid = exec (prog, 0, 0);
    printf("pid is: %d\n", pid);
    if (pid < 0) {
	exit (-1);
    }
    printf("678910\n");
    exit (0);
}