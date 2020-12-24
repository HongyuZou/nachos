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
    char* args1[] = {"file1", "11111", "22222", "333333", "44444"};
    char* args2[] = {"file2", "AAAAA", "22222", "333333", "44444"};
    char* args3[] = {"file3", "VVVVV", "22222", "333333", "44444"};
    char* args4[] = {"file4", "CCCCC", "22222", "333333", "44444"};

    int pid = exec ("write5.coff", 4, args1);
        printf("1 pid is: %d\n", pid);
    if (pid < 0) {
        exit (-1);
    }

    pid = exec ("write5.coff", 4, args2);
        printf("2 pid is: %d\n", pid);
    if (pid < 0) {
        exit (-1);
    }


    pid = exec ("write5.coff", 4, args3);
        printf("3 pid is: %d\n", pid);
    if (pid < 0) {
        exit (-1);
    }


    pid = exec ("write5.coff", 4, args4);
        printf("4 pid is: %d\n", pid);
    if (pid < 0) {
        exit (-1);
    }

    exit (0);
}