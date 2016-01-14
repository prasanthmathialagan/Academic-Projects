/*
 * main.cpp
 *
 *  Created on: Jan 14, 2016
 *      Author: prasanth
 */

#include "help.h"
#include <iostream>
#include <netdb.h>
#include <stdio.h>
#include <unistd.h>
#include <errno.h>
#include <sys/wait.h>
#include <string.h>
#include <stdlib.h>

using namespace std;

#define STDIN 0

void creator(void);
int bind_port(char*);

int sockfd;
bool server;
fd_set master;    // master file descriptor list
fd_set read_fds;  // temp file descriptor list for select()
int fdmax;        // maximum file descriptor number

int main(int argc, char **argv)
{
	int res = bind_port(argv[2]);
	if(res)
		exit(1);
	
	// Setting if the process is a server
	server = argv[1][0] == 's';
	
	char command[200];
	while(true)
	{
		cout << "$";
		cin >> command;
		
		int com_id = get_command_id(command);
		switch(com_id)
		{
			case _HELP:
				display_commands();
				break;
			case _CREATOR:
				creator();
				break;
		}
	}
}

void creator()
{
	cout << "Prasanth Mathialagan\npmathial\npmathial@buffalo.edu\n";
}

void sigchld_handler(int s)
{
	// waitpid() might overwrite errno, so we save and restore it:
	int saved_errno = errno;
	while (waitpid(-1, NULL, WNOHANG) > 0);
	errno = saved_errno;
}

int bind_port(char* port)
{
	struct addrinfo hints, *servinfo, *p;
	struct sigaction sa;
	int yes = 1;
	
	FD_ZERO(&master);    // clear the master and temp sets
	FD_ZERO(&read_fds);
	
	memset(&hints, 0, sizeof hints);
	hints.ai_family = AF_UNSPEC;
	hints.ai_socktype = SOCK_STREAM;
	hints.ai_flags = AI_PASSIVE; // use my IP
	
	int status;
	if ((status = getaddrinfo(NULL, port, &hints, &servinfo)) != 0)
	{
		fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(status));
		return 1;
	}
	
	// loop through all the results and bind to the first we can
	for (p = servinfo; p != NULL; p = p->ai_next)
	{
		if ((sockfd = socket(p->ai_family, p->ai_socktype, p->ai_protocol))	== -1)
			continue;

		if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(int)) == -1)
			return 1;

		if (bind(sockfd, p->ai_addr, p->ai_addrlen) == -1)
		{
			close (sockfd);
			perror("server: bind");
			continue;
		}

		break;
	}
	
	freeaddrinfo(servinfo); // all done with this structure
	
	if (p == NULL)
	{
		fprintf(stderr, "server: failed to bind\n");
		return 1;
	}

	if (listen(sockfd, 20) == -1)
	{
		perror("listen");
		return 1;
	}
	
	sa.sa_handler = sigchld_handler; // reap all dead processes
	sigemptyset(&sa.sa_mask);
	sa.sa_flags = SA_RESTART;
	if (sigaction(SIGCHLD, &sa, NULL) == -1)
	{
		perror("sigaction");
		return 1;
	}
	
	// add stdin and the listening socket to the fds
	FD_SET(STDIN, &master);
	FD_SET(sockfd, &master);
	
	// keep track of the biggest file descriptor
	fdmax = sockfd; // so far, it's this one
	
	return 0;
}


