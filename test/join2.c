#include "syscall.h"
#include "stdio.h"

int
main (int argc, char *argv[])
{
    char *prog1 = "except1.coff";
    char *prog2 = "write1.coff";
    int pid, r, status = 0;

    printf ("execing %s...\n", prog1);
    pid = exec (prog1, 0, 0);
    
    printf ("joining %d...\n", pid);
    r = join (pid, &status);
    printf("join return value %d \n", r);
    if (r > 0) {
	printf ("...passed (status from child = %d)\n", status);
    } else if (r == 0) {
	printf ("...child exited with unhandled exception\n");
	exit (-1);
    } else {
	printf ("...failed (r = %d)\n", r);
	exit (-1);
    }

    // the return value from main is used as the status to exit
    return 0;
}