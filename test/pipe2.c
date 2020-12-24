#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int
main (int argc, char *argv[]) {
    // for test1: writer create first, reader reads
    // int fd = open("/pipe/pipe1");
    // char smallWrite[9];
    // smallWrite[9] = '\0';
    // int readCnt = read(fd, smallWrite, 8);
    // printf("readCnt %d\n", readCnt);
    // printf("read from buffer %s\n", smallWrite);

    // for test2: reader create first, writer reads
    int fd = creat("/pipe/pipe1");
    char smallWrite[9];
    smallWrite[9] = '\0';
    int readCnt = read(fd, smallWrite, 6);
    printf("readCnt %d\n", readCnt);
    printf("read from buffer %s\n", smallWrite);
}
    