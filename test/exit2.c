/* 
 * exit2.c
 *
 * It does not get simpler than this...
 */
   
#include "syscall.h"

int
main (int argc, char *argv[])
{
    int pid = exec("halt2.coff", 0, 0);
    exit(1);
}