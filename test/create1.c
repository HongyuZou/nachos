/*
 * create.c
 *
 * Write a string to stdout, one byte at a time.  Does not require any
 * of the other system calls to be implemented.
 *
 * Geoff Voelker
 * 11/9/15
 */

#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int
main (int argc, char *argv[])
{   
    int fd1 = creat("file1");
    printf("created file 1 with fd %d\n", fd1);

    int fd2 = creat("file2");
    printf("created file 2 with fd %d\n", fd2);

    int fd4 = open("file1");
    printf("open file1 with fd %d\n", fd4);

    int fd5 = open("123");
    printf("open 123 with fd %d\n", fd5);

    int fd6 = open("file1");
    int fd7 = open("file2");
    printf("open file1 with fd %d\n", fd6);
    printf("open file1 with fd %d\n", fd7);
    int fd8 = creat("file3");
    printf("open file1 with fd %d\n", fd8);

    for(int i = 0; i < 10; i ++) {
        int fd = open("file1");
        printf("open file1 with fd %d\n", fd);
    }

   
}