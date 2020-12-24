#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int
main (int argc, char *argv[])
{   
    int fd = open("file3");
    int closeRes = close(fd);
    printf("%d\n", fd);

    int fd2 = open("file1");
    printf("%d\n", fd2);

    int closeRes2 = close(10);
    printf("%d\n", closeRes2);

    int fd3 = open("file1");
    char* buffer = "12345";
    write(fd3, buffer, 5);
    close(fd3);
    printf("write closed: %d\n", write(fd3, buffer, 5));

    unlink("file1");
}