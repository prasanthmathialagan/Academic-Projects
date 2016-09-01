/**
 * @pmathial_assignment3
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

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <unistd.h>
#include <netdb.h>
#include <string.h>
#include <arpa/inet.h>
#include <time.h>
#include <sys/timerfd.h>

#include "../include/customlogger.h"
#include "../include/global.h"
#include "../include/utils.h"
#include "../include/control_msg_handler.h"
#include "../include/data_plane.h"

CONTEXT* context = NULL;

fd_set read_fds;  // temp file descriptor list for select()

// get sockaddr, IPv4 or IPv6:
void *get_in_addr(struct sockaddr *sa)
{
    if (sa->sa_family == AF_INET) {
        return &(((struct sockaddr_in*)sa)->sin_addr);
    }

    return &(((struct sockaddr_in6*)sa)->sin6_addr);
}

boolean is_data_port_fd(int fd, ROUTER** router)
{
	int j;
	for (j = 0; j < context->topology->routers_count; ++j)
	{
		ROUTER* r = context->topology->routers[j];
		if(fd == r->data_fd)
		{
			*router = r;
			return TRUE;
		}
	}
	
	*router = NULL;
	return FALSE;
}

void _accept_connections()
{
	while(1)
	{
		printf("\n");
		
		read_fds = context->master;
		if (select(context->fdmax + 1, &read_fds, NULL, NULL, NULL) < 0)
		{
			_fatal("Unable to select a socket\n");
			exit(4);
		}
		
		// run through the existing connections looking for data to read
		int i;
		for (i = 0; i <= context->fdmax; i++)
		{
			if (!FD_ISSET(i, &read_fds))
				continue;
			
			// New connection on control port
			if(i == context->control_fd)
			{
				_debug("Got a new connection on the control port.\n");
				
				int controller_fd = _accept(context->control_fd, context);
				if(controller_fd < 0)
				{
					_error("Couldn't accept a connection from the controller.\n");
					continue;
				}
				
				add_to_control_conn_list(controller_fd, context);
			}
			// Data from the controller
			else if(is_control_connection(i, context))
			{
				int closed = 0;
				CONTROL_MSG* msg = extract_control_msg(i, &closed);
				if(closed)
				{
					_debug("Controller closed the connection.\n");
					_close_fd(i, context);
					remove_from_control_conn_list(i, context);
					continue;
				}
				
				switch(msg->control_code)
				{
					case CMD_AUTHOR:
						printf("--------------------------AUTHOR-----------------------------\n");
						handle_AUTHOR(msg, context, i);
						break;
					case CMD_INIT:
						printf("--------------------------INIT-------------------------------\n");
						handle_INIT(msg, context, i);
						break;
					case CMD_ROUTING_TABLE:
						handle_ROUTING_TABLE(msg, context, i);
						break;
					case CMD_UPDATE:
						printf("-------------------------UPDATE------------------------------\n");
						handle_UPDATE(msg, context, i);
						break;
					case CMD_CRASH:
						printf("--------------------------CRASH------------------------------\n");
						handle_CRASH(msg, context, i);
						break;
					case CMD_SEND_FILE:
						printf("------------------------SEND FILE----------------------------\n");
						handle_SENDFILE(msg, context, i);
						break;
					case CMD_SEND_FILE_STATS:
						printf("---------------------SEND FILE STATS-------------------------\n");
						handle_SENDFILE_STATS(msg, context, i);
						break;
					case CMD_LAST_DATA_PKT:
						printf("--------------------LAST DATA PACKET-------------------------\n");
						handle_LAST_DATA_PKT(msg, context, i);
						break;
					case CMD_PENULTIMATE_DATA_PKT:
						printf("-----------------PENULTIMATE DATA PACKET----------------------\n");
						handle_PENULTIMATE_DATA_PKT(msg, context, i);
						break;
					default:
						break;
				}
				
				free(msg);
			}
			// Data over UDP
			else if(i == context->router_fd)
			{
				_trace("Got data on the router port.\n");
				
				struct sockaddr_in router_address;
				
				int data_size = ROUTING_UPDATE_HDRSIZE + (12 * context->topology->routers_count); // For each router, we need 12 bytes for the payload
				char* buffer = malloc(data_size);
				
				socklen_t addr_len = sizeof router_address;
				int bytes = recvfrom(i, buffer, data_size, 0, (struct sockaddr *) &router_address, &addr_len);
				_trace("%d bytes received.\n", bytes);

				char ip[IPADDR_LEN];
				_info("Received DV from %s.\n", inet_ntop(router_address.sin_family, get_in_addr((struct sockaddr *)&router_address), ip, IPADDR_LEN));
				_trace("Packet is %d bytes long\n", bytes);
				
				int offset = 0;
				
				offset += 2; // Number of update fields
				offset += 2; // Source router port
				offset += 4; // Source IP address
				
				ROUTER* r = get_router_for_ip(context, ip);
				
				// Update the distance vectors
				int idx = get_idx(context, r);
				boolean changed = FALSE;
				_trace("Index for the router = %d\n", idx);
				uint16_t* dist_vector = context->distance_vectors[idx];
				
				int j;
				for(j = 0; j < context->topology->routers_count; j++)
				{
					offset += 4; // Router IP address
					offset += 2; // Router port
					offset += 2; // Padding
					offset += 2; // Router ID
					
					uint16_t new_cost = unpack_u16(buffer + offset);
					offset += 2;
					if(dist_vector[j] != new_cost)
					{
						dist_vector[j] = new_cost;
						changed = TRUE;	
					}
				}
				
				// Re-compute if the distance vector is changed
				if(changed == TRUE)
				{
					_info("DV for the router %d has changed. Hence recomputing the DVs.\n", r->id);
					compute_distant_vectors(context);
				}
				else
				{
					_info("Distance vector for the router %d has not changed.\n", r->id);
				}
				
				// If it is the first routing update from the neighbor, start the timer
				if(r->timer_fd == -1)
				{
					int timer_fd = create_and_start_timer(context->topology->refresh_interval * 3);
					_debug("Timer FD for the neighbor %d = %d\n", r->id, timer_fd);
					r->timer_fd = timer_fd;
					add_to_select(timer_fd, context);
				}
				else // Reset the timer
				{
					_info("Resetting the timer for the neighbor %d.\n", r->id);
					struct itimerspec time_value;
					timerfd_gettime(r->timer_fd, &time_value);
					
					// Setting the timer to trigger at next interval
					time_value.it_value.tv_sec = context->topology->refresh_interval * 3;
					timerfd_settime(r->timer_fd, 0, &time_value, NULL);
				}
				
				free(buffer);
			}
			// Data port
			else if(i == context->data_fd)
			{
				int fd = _accept(context->data_fd, context);
				if (fd < 0)
				{
					_error("Couldn't accept the connection on the data port.\n");
					continue;
				}

				uint8_t peer_ip[4];
				get_peer_ip(fd, peer_ip);

				char peer_ip_str[IPADDR_LEN];
				convert_ip_to_string(peer_ip_str, peer_ip);
				_info("Got a new connection on the data port from %s.\n", peer_ip_str);

				ROUTER* r = get_router_for_ip(context, peer_ip_str);
				r->data_fd = fd;
			}
			// My timer FD
			else if(i == context->timer_fd)
			{
				size_t s = 0;
				int bytes = read(i, &s, sizeof(s));
				if (bytes != -1)
				{
					_info("It is time to send routing updates to the neighbors.\n");
					send_routing_updates(context);
				}
			}
			else 
			{
				ROUTER* r;
				if(is_data_port_fd(i, &r) == TRUE)
				{
					_trace("Receiving data from %s\n", r->ip_address);
					
					char buff[DATA_PKT_SIZE];
					
					int closed = 0;
					receive_all(i, buff, DATA_PKT_SIZE, &closed);
					if(closed)
					{
						_info("Closing the data connection to %s\n", r->ip_address);
						_close_fd(i, context);
						r->data_fd = -1;
					}
					else
					{
						DATA_PKT data_pkt;
						extract_data_pkt(buff, &data_pkt);
						handle_data_pkt(&data_pkt, context, buff);
					}
				}
				else
				{
					// Other routers' Timer FD
					size_t s = 0;
					int bytes = read(i, &s, sizeof(s));
					if (bytes != -1)
					{
						_debug("Other timer %d expired.\n", i);
					
						int j;
						ROUTER* router = NULL;
						for (j = 0; j < context->topology->routers_count; j++)
						{
							router = context->topology->routers[j];
							if(router->timer_fd == i)
							{
								_info("Router %d crashed.\n", router->id);
								router->timer_fd = -1;
								break;
							}
						}
						
						// Set the link cost to INF, set neighbor = FALSE and recompute the distance vectors
						router->link_cost = INF;
						router->neighbor = FALSE;
						compute_distant_vectors(context);
					}
					
					_close_fd(i, context);
				}
			}
		}
	}
}

// http://beej.us/guide/bgnet/output/html/multipage/syscalls.html#getaddrinfo
void populate_ip_address()
{
	struct addrinfo hints, *res;

	memset(&hints, 0, sizeof hints);
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;

	char host_name[200];
	gethostname(host_name, sizeof(host_name));
	
	getaddrinfo(host_name, NULL, &hints, &res);

	struct sockaddr_in *ipv4 = (struct sockaddr_in *) res->ai_addr;
	inet_ntop(res->ai_family, &(ipv4->sin_addr), context->ip_address, IPADDR_LEN);

	get_ip_from_string(context->ip_address, context->router_ip);
	_info("Router IP address = %s\n", context->ip_address);
	
	freeaddrinfo(res);
}

/**
 * main function
 *
 * @param  argc Number of arguments
 * @param  argv The argument list
 * @return 0 EXIT_SUCCESS
 */
int main(int argc, char **argv)
{
	/*Start Here*/
	set_log_level(INFO);
	
	_info("Starting the router....\n");

	int control_port = atoi(argv[1]);
	int control_fd = bind_port(control_port, TRUE);
	if(control_fd < 0)
	{
		_error("Could not listen on the control port %d. Hence quitting.\n", control_port);
		exit(1);
	}

	context = malloc(sizeof(CONTEXT));
	context->fdmax = -1;
	context->data_fd = -1;
	context->router_fd = -1; 
	context->timer_fd = -1;
	
	context->control_port = (uint16_t) control_port;
	context->control_fd = control_fd;
	populate_ip_address();
	
	// clear the master and temp sets
	FD_ZERO(&context->master);
	FD_ZERO(&read_fds);
	
	add_to_select(context->control_fd, context);
	_info("Accepting connections on control port %d.\n", control_port);
	
	_accept_connections();
	
	return 0;
}
