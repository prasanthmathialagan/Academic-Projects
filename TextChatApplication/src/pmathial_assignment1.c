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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <netdb.h>
#include <ifaddrs.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>

#include "../include/global.h"
#include "../include/logger.h"
#include "../include/utils.h"
#include "../include/context.h"
#include "../include/mockdata.h"
#include "../include/commands.h"
#include "../include/serversidehandler.h"
#include "../include/clientsidehandler.h"
#include "../include/customlogger.h"

struct application_context *context = NULL;

fd_set read_fds;  // temp file descriptor list for select()

// Sorted list
// List in client will not have socket fd.
CLIENTS *clients = NULL;

// Sorted list. Only applicable for server
struct stats_list *stats = NULL;

// List as Map. Only applicable for server
struct blocked_clients *blokd_clients = NULL;
struct messages_map *msgs_map = NULL;

// Only applicable for client
SERVER_INFO ser_info;

int online()
{
	return is_online(&ser_info);
}

int offline()
{
	return !online();
}

// This method should be called on graceful shutdown and on forceful logout of server
void clean()
{
	if(offline())
		return;
	
	// Set client as offline
	ser_info.online_with_server = 0;

	// Free clients
	free_clients(clients);
	clients = NULL;
}

// TODO : Move this method to commands.c
void _logout()
{
	if(offline())
	{
		_error("Client is not connected to the server.\n");
		return ;
	}
	
	struct packet* packet = construct_packet(context->ip_address, ser_info.h.ip_address,context->port, ser_info.h.port, CMD_LOGOUT, "{}");
	serialize_send(packet, ser_info.socket_fd);
	free(packet);
	
	clean();
	
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_LOGOUT);
	cse4589_print_and_log("[%s:END]\n", CMD_LOGOUT);
}

// TODO : Move this method to commands.c
void _exit_()
{
	struct packet* packet = construct_packet(context->ip_address, ser_info.h.ip_address,context->port, ser_info.h.port, CMD_EXIT, "{}");
	serialize_send(packet, ser_info.socket_fd);
	free(packet);

	clean();
	
	// Reset server fd
	ser_info.socket_fd = -1;
	
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_EXIT);
	cse4589_print_and_log("[%s:END]\n", CMD_EXIT);
	
	exit(0);
}

void read_from_stdin()
{
	char input[INPUT_LENGTH];
	fgets(input, INPUT_LENGTH, stdin);
	if (input[0] == '\n')
		return;

	if (input[strlen(input) - 1] == '\n')
		input[strlen(input) - 1] = '\0';

	int argc = get_word_count(input);
	char** args = split(input);

	char* command = args[0];
	int command_id = get_command(command, 1, context);
	
	SERVER_INFO* server_info = &ser_info;
	
	switch (command_id)
	{
		case CMD_ID_AUTHOR:
			_author();
			break;
		case CMD_ID_IP:
			_ip(context, server_info);
			break;
		case CMD_ID_PORT:
			_port(context, server_info);
			break;
		case CMD_ID_LIST:
			_list(clients, context, server_info);
			break;
		case CMD_ID_STATISTICS:
			_statistics(stats);
			break;
		case CMD_ID_REFRESH:
			_refresh(server_info, context);
			break;
		case CMD_ID_BLOCKED:
			_blocked(args, argc, blokd_clients);
			break;
		case CMD_ID_LOGIN:
			_login(args, argc, server_info, context);
			break;
		case CMD_ID_LOGOUT:
			_logout();
			break;
		case CMD_ID_EXIT:
			_exit_();
			break;
		case CMD_ID_BLOCK:
			_block(args, argc, context, clients, server_info);
			break;
		case CMD_ID_UNBLOCK:
			_unblock(args, argc, context, clients, server_info);
			break;
		case CMD_ID_SEND:
			_send(args, argc, context, server_info, clients, input);
			break;
		case CMD_ID_BROADCAST:
			_broadcast(args, argc, context, server_info, input);
			break;
		case CMD_ID_SEND_FILE:
			_send_file(args, argc, context, server_info, clients);
			break;
		default:
			break;
	}

	// Deallocate memory
	int i = 0;
	for(;i < argc; i++)
		free(args[i]);
	free(args);
}

// Adapted from http://beej.us/guide/bgnet/output/html/multipage/advanced.html - 7.2. select()—Synchronous I/O Multiplexing
void _accept_connections()
{
    // clear the master and temp sets
	FD_ZERO(&context->master);
	FD_ZERO(&read_fds);
	
	// add stdin and the listening socket to the master fds
	FD_SET(STDIN, &context->master);
	FD_SET(context->socket_fd, &context->master);
	
	// keep track of the biggest file descriptor
	context->fdmax = context->socket_fd; // so far, it's this one
	
	while(1)
	{
		printf("$");
		fflush(stdout);

		read_fds = context->master;
		if (select(context->fdmax + 1, &read_fds, NULL, NULL, NULL) < 0)
		{
			_fatal("Unable to select a socket");
			exit(4);
		}
		
		// run through the existing connections looking for data to read
		int i;
		for (i = 0; i <= context->fdmax; i++)
		{
			if (!FD_ISSET(i, &read_fds))
				continue;
			
			if (i == STDIN)
			{
				read_from_stdin();
			}
			// Got a connection request
			else if (i == context->socket_fd)
			{
				// http://beej.us/guide/bgnet/output/html/multipage/syscalls.html#accept
				struct sockaddr_storage remoteaddr; // client address
				socklen_t addrlen = sizeof remoteaddr;
				int client_fd = accept(context->socket_fd, (struct sockaddr *) &remoteaddr, &addrlen);
				if (client_fd == -1)
				{
					_error("Unable to accept the new connection");
					continue;
				}
				
				FD_SET(client_fd, &context->master); // add to master set
				update_fdmax(client_fd, context); // keep track of the max
			}
			else
			{
				// Reconstruct the packet
				struct packet packet;
				
				int closed = 0;
				
				construct_packet_from_stream(i, &closed, &packet);
				
				if(closed)
				{
					// For client, if the server closed the connection
					if(!context->server && i == ser_info.socket_fd)
					{
						clean();
					}
					
					// If the client forcefully closed the connection
					if(context->server)
					{
						CLIENTS* c = get_client_info(clients, i);
						if(c)
						{
							char ip_address[IP_ADDRESS_LEN];
							strcpy(ip_address, c->h.ip_address); // Since the memory allocated to the ptr will be freed.
							handle_exit_request(ip_address, c->h.port, i, &clients, &stats, &blokd_clients, context, msgs_map);
						}
					}
					_close_socket(i, context);
				}
				else
				{
					int command_id = get_command(packet.command, 0, context);
					if(context->server)
					{
						switch (command_id)
						{
							case CMD_ID_LOGIN:
								handle_login_request(&packet, i, &clients, &stats, &blokd_clients, &msgs_map, context);
								break;
							case CMD_ID_LOGOUT:
								handle_logout_request(packet.src_ip, packet.src_port, i, &clients, stats, context);
								break;
							case CMD_ID_EXIT:
								handle_exit_request(packet.src_ip, packet.src_port, i, &clients, &stats, &blokd_clients, context, msgs_map);
								break;
							case CMD_ID_REFRESH:
								send_clients_list_and_msgs(i, &packet, CMD_REFRESH, context, clients, NULL, stats);
								break;
							case CMD_ID_BLOCK:
								handle_block_request(&packet, stats, blokd_clients, i);
								break;
							case CMD_ID_UNBLOCK:
								handle_unblock_request(&packet, blokd_clients, i);
								break;
							case CMD_ID_SEND:
								handle_send_request(&packet, blokd_clients, clients, msgs_map, stats);
								break;
							case CMD_ID_BROADCAST:
								handle_broadcast_request(&packet, blokd_clients, clients, stats, msgs_map);
								break;
							default:
								break;
						}
					}
					else
					{
						switch (command_id)
						{
							case CMD_ID_LOGIN:
								handle_login_response(&packet, &clients, i);
								break;
							case CMD_ID_REFRESH:
								handle_refresh_response(&packet, &clients);
								break;
							case CMD_ID_BLOCK:
								handle_block_response(&packet);
								break;
							case CMD_ID_UNBLOCK:
								handle_unblock_response(&packet);
								break;
							case CMD_ID_SEND:
							case CMD_ID_BROADCAST:
								handle_received_msg(&packet);
								break;
							case CMD_ID_SEND_FILE:
								handle_file_recv(&packet, i);
								break;
							default:
								break;
						}
					}
				}
			}
		}
	}
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
	/*Init. Logger*/
	cse4589_init_log(argv[2]);

	/*Clear LOGFILE*/
	fclose(fopen(LOGFILE, "w"));

	/*Start Here*/
	context = malloc(sizeof(struct application_context));
	
	// initialize context
	init(context, argv, argc);
	
	// Bind port
	if(bind_port(context) < 0)
	{
		exit(1);
	}
	
	ser_info.online_with_server = 0;
	ser_info.socket_fd = -1;
	
	// Test
//	clients = mock_clients();
//	stats = mock_stats();
//	blokd_clients = mock_blocked();
	
	// Start accepting connections
	_accept_connections();

	free(context);
	
	return 0;
}

