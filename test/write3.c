#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int
main (int argc, char *argv[]) {
    printf("123\n");
    for(int i = 0; i < argc; i ++) {
        int fd = creat(argv[i]);
        write(fd, argv[i], 10);
        close(fd);
    }
}