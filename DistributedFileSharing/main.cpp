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
#include <ifaddrs.h>
#include <arpa/inet.h>

using namespace std;

#define STDIN 0

void creator(void);
void _display(void);
void _register(char*, char*);
void _connect(char*, char*);
void _list(void);
void _terminate(char*);
void _quit();
void _get(char*, char*);
void _put(char*, char*);
void _sync();

char hostname[200];
char ipaddress[20];
char port[6];

int sockfd;
bool server;
fd_set master;    // master file descriptor list
fd_set read_fds;  // temp file descriptor list for select()
int fdmax;        // maximum file descriptor number

void populate_host_details()
{
	gethostname(hostname, sizeof hostname);
	
	struct ifaddrs * ifAddrStruct;
	struct ifaddrs * ifa;
	void * tmpAddrPtr;

	getifaddrs(&ifAddrStruct);

	for (ifa = ifAddrStruct; ifa != NULL; ifa = ifa->ifa_next)
	{
		if (!ifa->ifa_addr)
		{
			continue;
		}

		// check it is IP4 and it is not local host
		if (ifa->ifa_addr->sa_family == AF_INET && strcasecmp("lo", ifa->ifa_name))
		{ 	
			tmpAddrPtr = &((struct sockaddr_in *) ifa->ifa_addr)->sin_addr;
			inet_ntop(AF_INET, tmpAddrPtr, ipaddress, INET_ADDRSTRLEN);
			break;
		}
	}
	
	if (ifAddrStruct != NULL)
		freeifaddrs(ifAddrStruct);
}

void sigchld_handler(int s)
{
	// waitpid() might overwrite errno, so we save and restore it:
	int saved_errno = errno;
	while (waitpid(-1, NULL, WNOHANG) > 0);
	errno = saved_errno;
}

int bind_port()
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

int main(int argc, char **argv)
{
	strcpy(port, argv[2]);
	populate_host_details();
	int res = bind_port();
	if(res)
		exit(1);
	
	// Setting if the process is a server
	server = argv[1][0] == 's';
	
	char command[30];
	char other_host[200];
	char file_name[200];
	char port[6];
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
			case _DISPLAY:
				_display();
				break;
			case _REGISTER:
				cin >> other_host >> port;
				_register(other_host, port);
				break;
			case _CONNECT:
				cin >> other_host >> port;
				_connect(other_host, port);
				break;
			case _LIST:
				_list();
				break;
			case _TERMINATE:
				cin >> other_host;
				_terminate(other_host);
				break;
			case _QUIT:
				_quit();
				break;
			case _GET:
				cin >> other_host >> file_name;
				_get(other_host, file_name);
				break;
			case _PUT:
				cin >> other_host >> file_name;
				_put(other_host, file_name);
				break;
			case _SYNC:
				_sync();
				break;
			default:
				cout << "Invalid command" << endl ;
				break;
		}
	}
}

void creator()
{
	cout << "Prasanth Mathialagan\npmathial\npmathial@buffalo.edu\n";
}

void _display()
{
	cout << "IP address : " << ipaddress << "\nPort : " << port << endl; 
}

void _register(char* server, char* port)
{
	cout << server << endl;
	cout << port << endl;
	// TODO :
}

void _connect(char *host, char* port)
{
	cout << host << endl;
	cout << port << endl;
	// TODO :
}

void _list()
{
	// TODO:
}

void _terminate(char* connection)
{
	// TODO:
}

void _quit()
{
	// TODO:
}

void _get(char* host, char* file_name)
{
	// TODO:
}

void _put(char* host, char* file_name)
{
	// TODO:
}

void _sync()
{
	// TODO:
}
