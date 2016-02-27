/*
 * context.c
 *
 *  Created on: Feb 5, 2016
 *      Author: prasanth
 */

/**
 * @pmathial_assignment1
 * @author  Prasanth Mathialagan <pmathial@buffalo.edu>
 * @version 1.0
 *
 * @section LICENSE
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details at
 * http://www.gnu.org/copyleft/gpl.html
 *
 * @section DESCRIPTION
 *
 * This contains the main function. Add further description here....
 */

#include <arpa/inet.h>
#include <stdio.h>
#include <string.h>
#include <netdb.h>
#include <stdlib.h>
#include <unistd.h>

#include "../include/context.h"
#include "../include/global.h"

// http://beej.us/guide/bgnet/output/html/multipage/syscalls.html#getaddrinfo
void populate_ip_address(struct application_context* context)
{
	struct addrinfo hints, *res;

	memset(&hints, 0, sizeof hints);
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;

	getaddrinfo(context->host_name, NULL, &hints, &res);

	struct sockaddr_in *ipv4 = (struct sockaddr_in *) res->ai_addr;
	inet_ntop(res->ai_family, &(ipv4->sin_addr), context->ip_address, IP_ADDRESS_LEN);

	freeaddrinfo(res);
}

void init(struct application_context* context, char** argv, int argc)
{
	// Server or client
	context->server = (argv[1][0] == 's') ? 1 : 0;
	
	// Setting the port
	context->port = atoi(argv[2]);
	
	// Setting the host name
	gethostname(context->host_name, sizeof(context->host_name));
	
	// Populate IP address
	populate_ip_address(context);
}

