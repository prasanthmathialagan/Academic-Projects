/*
 * commands.c
 *
 *  Created on: Feb 5, 2016
 *      Author: prasanth
 */

#include <stdio.h>
#include <string.h>
#include <netdb.h>
#include <ifaddrs.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>
#include <stdlib.h>

#include "../include/global.h"
#include "../include/logger.h"
#include "../include/commands.h"
#include "../include/utils.h"
#include "../include/customlogger.h"

/**
 * Implementation of AUTHOR command. This command can be executed from both client and server. Client must be able to 
 * execute this command irrespective of whether it is logged-in or not.
 */
void _author()
{
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_AUTHOR);
	cse4589_print_and_log("I, %s, have read and understood the course academic integrity policy.\n", AUTHOR);
	cse4589_print_and_log("[%s:END]\n", CMD_AUTHOR);
}

/**
 * Returns 1 if the application is a client and it is offline.
 */
int is_client_and_offline(struct application_context* context, SERVER_INFO* ser_info)
{
	return !context->server && is_offline(ser_info);
}

/**
 * Implementation of IP command. This command can be executed from both client and server. But, in client,
 * if it is offline, execution of this command will not have any effect.
 */
void _ip(struct application_context* context, SERVER_INFO* ser_info)
{
	if (is_client_and_offline(context, ser_info))
		return;
	
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_IP);
	cse4589_print_and_log("IP:%s\n", context->ip_address);
	cse4589_print_and_log("[%s:END]\n", CMD_IP);
}

/**
 * Implementation of PORT command. This command can be executed from both client and server. But, in client,
 * if it is offline, execution of this command will not have any effect.
 */
void _port(struct application_context* context, SERVER_INFO* ser_info)
{
	if (is_client_and_offline(context, ser_info))
		return;
	
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_PORT);
	cse4589_print_and_log("PORT:%d\n", context->port);
	cse4589_print_and_log("[%s:END]\n", CMD_PORT);
}

void print_list(CLIENTS* hosts)
{
	int i = 1;
	CLIENTS* temp = hosts;
	while (temp)
	{
		cse4589_print_and_log("%-5d%-35s%-20s%-8d\n", i, temp->h.host_name,
				temp->h.ip_address, temp->h.port);
		i++;
		temp = temp->next;
	}
}

/**
 * Implementation of LIST command. This command can be executed from both client and server. But, in client,
 * if it is offline, execution of this command will not have any effect.
 */
void _list(CLIENTS *hosts, struct application_context* context, SERVER_INFO* ser_info)
{
	if (is_client_and_offline(context, ser_info))
		return;
	
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_LIST);
	print_list(hosts);
	cse4589_print_and_log("[%s:END]\n", CMD_LIST);
}

/**
 * Implementation of STATISTICS command. This command must be executed only from the server.
 */
void _statistics(struct stats_list *stats)
{
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_STATISTICS);
	int i = 1;
	struct stats_list *temp = stats;
	while (temp)
	{
		char* status = (temp->s.status == 0) ? "offline" : "online";
		cse4589_print_and_log("%-5d%-35s%-8d%-8d%-8s\n", i, temp->s.h.host_name, temp->s.msgs_sent, temp->s.msgs_received, status);
		i++;
		temp = temp->next;
	}
	cse4589_print_and_log("[%s:END]\n", CMD_STATISTICS);
}

/**
 * 	Implementation of LOGIN command. This method should be invoked only from the client. 
 * 	
 * 	This method returns with an error message if
 * 	 - Number of arguments is not enough
 * 	 - Invalid IP address(Also logs ERROR)
 * 	 - Invalid port number(Also logs ERROR)
 * 	 - Client is already connected to the server.
 * 	 - Could not connect to the server(Also logs ERROR)
 * 	 
 * 	Response for the LOGIN message(sent in this method) from the server will be handled in clientsidehandler.handle_login_response()
 */
void _login(char** args, int argc, SERVER_INFO* ser_info, struct application_context* context)
{
	if (argc < 3)
	{
		_error("Not enough arguments. Usage: LOGIN <ip> <port>\n");
		return;
	}
	
	// Checking if the IP address format is valid 
	char* server_ip = args[1];
	if (!validate_ip(server_ip))
	{
		cse4589_print_and_log("[%s:ERROR]\n", CMD_LOGIN);
		_error("Invalid IP address %s\n", server_ip);
		cse4589_print_and_log("[%s:END]\n", CMD_LOGIN);
		return ;
	}

	// Checking if the port is valid
	int port = atoi(args[2]);
	if (port <= 0 || port > 65535)
	{
		cse4589_print_and_log("[%s:ERROR]\n", CMD_LOGIN);
		_error("Invalid port %s\n", args[2]);
		cse4589_print_and_log("[%s:END]\n", CMD_LOGIN);
		return ;
	}
	
	// Client is already logged in
	if (is_online(ser_info))
	{
		_error("Client is already connected to the server.\n");
		return;
	}

	struct sockaddr_in server_address;
	
	// Create socket
	int server_sock_fd = socket(AF_INET, SOCK_STREAM, 0);
	if (server_sock_fd < 0)
	{
		cse4589_print_and_log("[%s:ERROR]\n", CMD_LOGIN);
		_error("Cannot create socket.\n");
		cse4589_print_and_log("[%s:END]\n", CMD_LOGIN);
		return;
	}

	bzero(&server_address, sizeof(server_address));
	server_address.sin_family = AF_INET;
	inet_pton(AF_INET, server_ip, &server_address.sin_addr);
	server_address.sin_port = htons(port);

	if (connect(server_sock_fd, (struct sockaddr *)&server_address, sizeof(server_address)) < 0)
	{
		close(server_sock_fd);
		cse4589_print_and_log("[%s:ERROR]\n", CMD_LOGIN);
		_error("Cannot connect to the socket.\n");
		cse4589_print_and_log("[%s:END]\n", CMD_LOGIN);
		return ;
	}

	// Add to the read fds
	FD_SET(server_sock_fd, &context->master);
	update_fdmax(server_sock_fd, context);
	
	strcpy(ser_info->h.ip_address, server_ip);
	ser_info->h.port = port;
	ser_info->socket_fd = server_sock_fd;

	char data[PACKET_DATA_LEN];
	sprintf(data, "{hostname:'%s'}", context->host_name);
	struct packet* packet = construct_packet(context->ip_address, server_ip, context->port, port, CMD_LOGIN, data);
	serialize_send(packet, server_sock_fd);
	free(packet);

	ser_info->online_with_server = 1;
}

/**
 * 	Implementation of REFRESH command. This method should be invoked only from the client. 
 * 	
 * 	This method returns with an error message if
 * 	 - Client is not connected to the server.
 */
void _refresh(SERVER_INFO* ser_info, struct application_context* context)
{
	if(is_offline(ser_info))
	{
		_error("Client is not connected to the server.\n");
		return;
	}
	
	char data[PACKET_DATA_LEN];
	sprintf(data, "{hostname:'%s'}", context->host_name);
	
	struct packet* packet = construct_packet(context->ip_address, ser_info->h.ip_address, context->port, ser_info->h.port, CMD_REFRESH, data);
	serialize_send(packet, ser_info->socket_fd);
	free(packet);
}

/**
 * 	Implementation of BLOCK and UNBLOCK. This method returns with an error message if
 * 	 - Client is not connected to the server
 * 	 - Not enough arguments
 * 	 - Invalid IP address(Also logs ERROR)
 * 	 - Client tries to block itself
 * 	 - Non-existent IP address(Also logs ERROR)
 */
void block_or_unblock(char** args, int argc, int block, struct application_context* context, CLIENTS* clients, SERVER_INFO* ser_info)
{
	if (is_offline(ser_info))
	{
		_error("Client is not connected to the server.\n");
		return;
	}
	
	// Check the number of args
	if (argc < 2)
	{
		_error("Not enough arguments. Usage: BLOCK/UNBLOCK <ip>\n");
		return;
	}
	
	char* command = block ? CMD_BLOCK : CMD_UNBLOCK;

	// Checking if the IP address format is valid 
	char* client_ip = args[1];
	if (!validate_ip(client_ip))
	{
		cse4589_print_and_log("[%s:ERROR]\n", command);
		_error("Invalid IP address.\n");
		cse4589_print_and_log("[%s:END]\n", command);
		return;
	}

	// Check if it is available in clients list
	if(!is_valid_client(client_ip, clients))
	{
		cse4589_print_and_log("[%s:ERROR]\n", command);
		_error("Non-existent IP address.\n");
		cse4589_print_and_log("[%s:END]\n", command);
		return;
	}

	char data[PACKET_DATA_LEN];
	sprintf(data, "{ip:'%s'}", client_ip);
	
	struct packet* packet = construct_packet(context->ip_address, ser_info->h.ip_address, context->port, ser_info->h.port, command, data);
	serialize_send(packet,ser_info->socket_fd);
	free(packet);
}

/**
 * 	Implementation of BLOCK command. This method should be invoked only from the client. See block_or_unblock() method
 * 	for more information.
 */
void _block(char** args, int argc, struct application_context* context, CLIENTS* clients, SERVER_INFO* server_info)
{
	block_or_unblock(args, argc, 1, context, clients, server_info);
}

/**
 * 	Implementation of UNBLOCK command. This method should be invoked only from the client. See block_or_unblock() method
 * 	for more information.
 */
void _unblock(char** args, int argc, struct application_context* context, CLIENTS* clients, SERVER_INFO* server_info)
{
	block_or_unblock(args, argc, 0, context, clients, server_info);
}

/**
 *  Implementation of BLOCKED command. This method must be invoked only from the server.
 *  This method returns with an error message if
 * 	 - Not enough arguments
 * 	 - Invalid IP address(Also logs ERROR)
 * 	 - Non-existent IP address(Also logs ERROR)
 */
void _blocked(char** args, int argc, struct blocked_clients *blokd_clients)
{
	if (argc < 2)
	{
		_error("Not enough arguments. Usage: BLOCKED <ip>\n");
		return;
	}

	char* ip = args[1];

	// Checking if the IP address format is valid 
	if (!validate_ip(ip))
	{
		cse4589_print_and_log("[%s:ERROR]\n", CMD_BLOCKED);
		_error("Invalid IP address.\n");
		cse4589_print_and_log("[%s:END]\n", CMD_BLOCKED);
		return;
	}
	
	struct blocked_clients* c = get_blocked_clients(blokd_clients, ip);
	if(!c)
	{
		cse4589_print_and_log("[%s:ERROR]\n", CMD_BLOCKED);
		_error("Non-existent IP address.\n");
		cse4589_print_and_log("[%s:END]\n", CMD_BLOCKED);
		return;
	}

	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_BLOCKED);
	print_list(c->clients);
	cse4589_print_and_log("[%s:END]\n", CMD_BLOCKED);
}

/**
 *  Sends msg to the server
 */
void send_msg(char* src_ip, char* dest_ip, char* command, char* msg, SERVER_INFO* ser_info, struct application_context* context)
{
	char data[PACKET_DATA_LEN];
	sprintf(data, "{%s}", msg);
	struct packet* packet = construct_packet(src_ip, dest_ip, context->port, -1, command, data);
	serialize_send(packet, ser_info->socket_fd);
	free(packet);
}

/**
 * Extracts the message from the input for the SEND/BROADCAST commands. Since the msg can have spaces, we cannot use the
 * args and argc to get the msg. Instead we can extract from the raw input.
 */
void extract_msg(char* input, int broadcast, char* msg)
{
	// Move the pointer to the msg position
	int i = 0;

	// Move to the first non empty character
	while (input[i] == ' ')
		i++;
	
	// Now the pointer will be pointing to SEND or BROADCAST. Move till the ' ' character
	while(input[i] != ' ')
		i++;

	// For SEND, move past the ip address
	if(!broadcast)
	{
		// Move to the non empty character
		while (input[i] == ' ')
			i++;
		
		// Move to the empty character
		while(input[i] != ' ')
			i++;
	}

	i++; // Move to the next character
	
	strcpy(msg, input + i);
}

/**
 * 	Implementation of SEND command. This method should be invoked only from the client.
 * 	This method returns with an error message if
 * 	 - Not enough arguments
 * 	 - Invalid IP address(Also logs ERROR)
 * 	 - Non-existent IP address(Also logs ERROR)
 * 	 - Client is not connected to the server
 */
void _send(char** args, int argc, struct application_context* context, SERVER_INFO* ser_info, CLIENTS* clients, char* input)
{
	if (is_offline(ser_info))
	{
		_error("Client is not connected to the server.\n");
		return;
	}
	
	if (argc < 3)
	{
		_error("Not enough arguments. Usage: SEND <ip> <msg>\n");
		return;
	}
	
	// Checking if the IP address format is valid 
	char* dest_ip = args[1]; 
	if (!validate_ip(dest_ip))
	{
		cse4589_print_and_log("[%s:ERROR]\n", CMD_SEND);
		_error("Invalid IP address.\n");
		cse4589_print_and_log("[%s:END]\n", CMD_SEND);
		return;
	}
	
	// If the destination IP is 127.0.0.1, send the msg to server and the server sends the msg again to the same client
	if(strcmp(_LOCALHOST, dest_ip))
	{
		// Check if the client is present in the logged-in clients list
		CLIENTS* c = get_client(clients, dest_ip, -1);
		if(!c)
		{
			cse4589_print_and_log("[%s:ERROR]\n", CMD_SEND);
			_error("Non-existent IP address.\n");
			cse4589_print_and_log("[%s:END]\n", CMD_SEND);
			return;
		}
	}

	char msg[MESSAGE_LENGTH];
	extract_msg(input, 0, msg);
	
	send_msg(context->ip_address, dest_ip, CMD_SEND, msg, ser_info, context);
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_SEND);
	cse4589_print_and_log("[%s:END]\n", CMD_SEND);
}

/**
 * 	Implementation of BROADCAST command. This method should be invoked only from the client. 
 * 	This method returns with an error message if
 * 	 - Not enough arguments
 * 	 - Client is not connected to the server
 */
void _broadcast(char** args, int argc, struct application_context* context, SERVER_INFO* ser_info, char* input)
{
	if (is_offline(ser_info))
	{
		_error("Client is not connected to the server.\n");
		return;
	}
	
	if (argc < 2)
	{
		_error("Not enough arguments. Usage: BROADCAST <msg>\n");
		return;
	}
	
	char msg[MESSAGE_LENGTH];
	extract_msg(input, 1, msg);
		
	send_msg(context->ip_address, "255.255.255.255", CMD_BROADCAST, msg, ser_info, context);
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_BROADCAST);
	cse4589_print_and_log("[%s:END]\n", CMD_BROADCAST);
}

/**
 *  Implementation of SENDFILE command. This method should be invoked only from the client.
 *  This method returns with an error message if
 * 	 - Not enough arguments
 * 	 - Client is not connected to the server
 * 	 - Sending file to yourself
 */
void _send_file(char** args, int argc, struct application_context* context, SERVER_INFO* ser_info, CLIENTS* clients)
{
	if (is_offline(ser_info))
	{
		_error("Client is not connected to the server.\n");
		return;
	}

	if (argc < 3)
	{
		_error("Not enough arguments. Usage: SENDFILE <ip> <file>\n");
		return;
	}
	
	char* ip = args[1];
	char* file = args[2];
	
	if(!strcmp(ip, context->ip_address))
	{
		_error("You cannot send the file to yourself.\n");
		return ; 
	}
	
	// http://www.linuxquestions.org/questions/programming-9/c-howto-read-binary-file-into-buffer-172985/
	FILE* ptr = fopen(file, "rb");
	if(!ptr)
	{
		_error("Error opening file %s.\n", file);
		return;
	}
	
	CLIENTS* c = get_client(clients, ip, -1);
	if(!c)
	{
		_error("Could not get client info for %s.\n", ip);
		return;
	}
	
	// if client fd is not available
	if(c->socket_fd == -1)
	{
		struct sockaddr_in server_address;
		
		// Create socket
		int client_sock_fd = socket(AF_INET, SOCK_STREAM, 0);
		if (client_sock_fd < 0)
		{
			_error("Cannot create socket.\n");
			return;
		}

		bzero(&server_address, sizeof(server_address));
		server_address.sin_family = AF_INET;
		inet_pton(AF_INET, ip, &server_address.sin_addr);
		server_address.sin_port = htons(c->h.port);

		if (connect(client_sock_fd, (struct sockaddr *)&server_address, sizeof(server_address)) < 0)
		{
			close(client_sock_fd);
			_error("Cannot connect to the socket.\n");
			return ;
		}

		// Add to the read fds
		FD_SET(client_sock_fd, &context->master);
		update_fdmax(client_sock_fd, context);
		
		c->socket_fd = client_sock_fd;
	}
	
	fseek(ptr, 0, SEEK_END);
	int fileLen = ftell(ptr);
	fseek(ptr, 0, SEEK_SET);
	
	char* buf = malloc(fileLen);
	
	//Read file contents into buffer
	fread(buf, fileLen, 1, ptr);
	fclose(ptr);

	// Send SENDFILE msg to the client
	char data[PACKET_DATA_LEN];
	sprintf(data, "{size:'%d',name='%s'}", fileLen, file);
	
	struct packet* packet = construct_packet(context->ip_address, c->h.ip_address, context->port, c->h.port, CMD_SEND_FILE, data);
	serialize_send(packet,c->socket_fd);
	free(packet);
	
	_info("Sending file. name = %s, size = %d\n", file, fileLen);
	
	// SEND the file over the socket
	int l = fileLen;
	sendall(c->socket_fd, buf, &l);
	_info("File sent successfully\n");
	
	free(buf);
	
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_SEND_FILE);
	cse4589_print_and_log("[%s:END]\n", CMD_SEND_FILE);
}

/**
 * 	Returns the command_id associated with the given command. Returns -1 if the command is invalid. 
 * 	Validity of the command depends on whether it is executed from server or client.
 * 	
 * 	@param check - boolean indicating whether server or client check is necessary 
 */
int get_command(char* command, int check, struct application_context* context)
{
	if (!strcmp(CMD_AUTHOR, command))
		return CMD_ID_AUTHOR;

	if (!strcmp(CMD_IP, command))
		return CMD_ID_IP;

	if (!strcmp(CMD_PORT, command))
		return CMD_ID_PORT;

	if (!strcmp(CMD_LIST, command))
		return CMD_ID_LIST;
	
	if(!check || context->server)
	{
		if (!strcmp(CMD_STATISTICS, command))
			return CMD_ID_STATISTICS;

		if (!strcmp(CMD_BLOCKED, command))
			return CMD_ID_BLOCKED;
	}

	if(!check || !context->server)
	{
		if (!strcmp(CMD_LOGIN, command))
			return CMD_ID_LOGIN;

		if (!strcmp(CMD_REFRESH, command))
			return CMD_ID_REFRESH;

		if (!strcmp(CMD_SEND, command))
			return CMD_ID_SEND;

		if (!strcmp(CMD_BROADCAST, command))
			return CMD_ID_BROADCAST;
		
		if (!strcmp(CMD_BLOCK, command))
			return CMD_ID_BLOCK;
		
		if (!strcmp(CMD_UNBLOCK, command))
			return CMD_ID_UNBLOCK;

		if (!strcmp(CMD_LOGOUT, command))
			return CMD_ID_LOGOUT;

		if (!strcmp(CMD_EXIT, command))
			return CMD_ID_EXIT;
		
		if(!strcmp(CMD_SEND_FILE, command))
			return CMD_ID_SEND_FILE;
	}
	
	return -1;
}
