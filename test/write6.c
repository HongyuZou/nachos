#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int
main (int argc, char *argv[]) {
    int fd3 = creat("hozhozhoz");
    char bigWrite[5000];
    for(int i = 0; i < 5000; i ++) {
        bigWrite[i] = '3';
    }
    printf("Write big %d\n", write(fd3, bigWrite, 5000));
    exit(0);
}