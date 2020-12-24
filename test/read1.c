#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int
main (int argc, char *argv[])
{   
    int fd = open("file3");
    char buffer[50];
    int readNum = read(fd, buffer, 50);
    printf("%s\n", buffer);
    printf("%d\n", readNum);

    int fd2 = open("file3");
    char buffer2 [10];
    char buffer3 [7];
    int readNum2 = read(fd2, buffer2, 10);
    int readNum3 = read(fd2, buffer3, 7);

    printf("%s\n", buffer2);
    printf("%d\n", readNum2);

    printf("%s\n", buffer3);
    printf("%d\n", readNum3);

    char buffer4[3];
    int fd3 = open("file3");
    
    for(int i = 0; i < 5; i ++) {
        int readNum = read(fd3, buffer4, 3);
        printf("%s\n", buffer4);
        printf("%d\n", readNum);
    }
}