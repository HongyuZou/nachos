#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int
main (int argc, char *argv[]) {
    for(int i = 1; i < argc; i ++) {
        int fd = creat(argv[0]);
        write(fd, argv[1], 5);
    }
}