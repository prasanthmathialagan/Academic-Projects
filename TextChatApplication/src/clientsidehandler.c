/*
 * clientsidehandler.c
 *
 *  Created on: Feb 18, 2016
 *      Author: prasanth
 */

#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <string.h>

#include "../include/utils.h"
#include "../include/logger.h"
#include "../include/customlogger.h"

// refresh is a boolean
void handle_response_for_login_or_refresh(struct packet* packet, int refresh, CLIENTS** clients, int server_fd)
{
	// {size='3',hosts:[{name:'stones.cse.buffalo.edu',ip:'128.205.36.46',port:'4545'},
	// {name:'embankment.cse.buffalo.edu',ip:'128.205.36.35',port:'5000'},{name:'Prasanth-Inspiron',ip:'192.168.100.156',port:'5622'}]}

	if(refresh)
	{
		free_clients(*clients);
	}
	
	int i = 7;
	
	// Parse the size of the list
	/*
	 * 3',hosts:[{name:'stones.cse.buffalo.edu',ip:'128.205.36.46',port:'4545'},
	 * {name:'embankment.cse.buffalo.edu',ip:'128.205.36.35',port:'5000'},{name:'Prasanth-Inspiron',ip:'192.168.100.156',port:'5622'}]}
	 */ 
	char s[6];
	int j = 0;
	while(packet->data[i] != '\'')
	{
		s[j++] = packet->data[i++];
	}
	s[j] = '\0';
	int list_size = atoi(s);
	if(list_size <= 0)
	{
		_error("List size is invalid.\n");
		return ;
	}
	
	i = i + 9;
	int count = 1;
	/*
	 * {name:'stones.cse.buffalo.edu',ip:'128.205.36.46',port:'4545'},
	 * {name:'embankment.cse.buffalo.edu',ip:'128.205.36.35',port:'5000'},{name:'Prasanth-Inspiron',ip:'192.168.100.156',port:'5622'}]}
	 */
	CLIENTS* head = NULL;
	CLIENTS* tail = NULL;
	while(count <= list_size)
	{
		CLIENTS* h = (CLIENTS*) malloc(sizeof(CLIENTS));
		
		i = i + 7; // Move to the first character of name
		
		// Copy the host name
		j = 0;
		while(packet->data[i] != '\'')
		{
			h->h.host_name[j++] = packet->data[i++];
		}
		h->h.host_name[j] = '\0';
		
		i = i + 6; // Move to the first character of ip
		
		// Copy the ip
		j = 0;
		while (packet->data[i] != '\'')
		{
			h->h.ip_address[j++] = packet->data[i++];
		}
		h->h.ip_address[j] = '\0';
		
		i = i + 8; // Move to the first character of port
		
		// Copy the port
		j = 0;
		while (packet->data[i] != '\'')
		{
			s[j++] = packet->data[i++];
		}
		s[j] = '\0';
		h->h.port = atoi(s);
		h->socket_fd = -1;
		h->next = NULL;
		
		// Set the pointers properly
		if(!head)
		{
			head = h;
		}
		else
		{
			tail->next = h;
		}
		tail = h;
		
		i = i + 3; // Move to the next '{'
		count ++;
	}
	
	*clients = head;
	
	if(!refresh)
	{
		int* closed = 0; // Dummy variable
		int msgs_c = receive_size(server_fd, closed);
		if (msgs_c == 0)
		{
			_info("No buffered messages.\n");
			return;
		}
		
		int msg_i = 1;
		while(msg_i <= msgs_c)
		{
			char buf[MESSAGE_LENGTH + IP_ADDRESS_LEN + 3]; // +3 for the delimiters
			char ip[IP_ADDRESS_LEN];
			char msg[MESSAGE_LENGTH];

			int msg_len = receive_size(server_fd, closed); // Message size
			receive_all(server_fd, buf, msg_len, closed); // Get the msg
			
			char *start = buf;
			char *e;
			int index;

			// Copy the IP address
			e = strchr(start, '$');
			index = (int) (e - start);
			strncpy(ip, start, index);
			ip[index] = '\0';
			start = e + 1;

			// Copy the message
			strcpy(msg, start);

			msg_i++;
			
			cse4589_print_and_log("[%s:SUCCESS]\n", "RECEIVED");
			cse4589_print_and_log("msg from:%s\n[msg]:%s\n", ip, msg);
			cse4589_print_and_log("[%s:END]\n", "RECEIVED");
		}
	}
}

/**
 * Handles the response for LOGIN obtained from the server.
 */
void handle_login_response(struct packet* packet, CLIENTS** clients, int server_fd)
{
	handle_response_for_login_or_refresh(packet, 0, clients, server_fd);
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_LOGIN);
	cse4589_print_and_log("[%s:END]\n", CMD_LOGIN);
}

/**
 * Handles the response for REFRESH obtained from the server.
 */
void handle_refresh_response(struct packet* packet, CLIENTS** clients)
{
	handle_response_for_login_or_refresh(packet, 1, clients, -1);
	cse4589_print_and_log("[%s:SUCCESS]\n", CMD_REFRESH);
	cse4589_print_and_log("[%s:END]\n", CMD_REFRESH);
}

void get_response(char* response, struct packet* packet)
{
	int i = 0;
	int j = 1;
	for (; packet->data[j] != '}'; i++, j++)
	{
		response[i] = packet->data[j];
	}
	response[i] = '\0';
}

/**
 * Handles the response for BLOCK and UNBLOCK command.
 */
void handle_block_or_unblock_response(struct packet* packet, int block)
{
	char response_code[10];
	get_response(response_code, packet);
	char* command = block ? CMD_BLOCK : CMD_UNBLOCK;
	if (!strcmp(response_code, RESPONSE_OK))
	{
		cse4589_print_and_log("[%s:SUCCESS]\n", command);
	}
	else
	{
		cse4589_print_and_log("[%s:ERROR]\n", command);
	}
	cse4589_print_and_log("[%s:END]\n", command);
}

/**
 * Handles the response for BLOCK obtained from the server.
 */
void handle_block_response(struct packet* packet)
{
	handle_block_or_unblock_response(packet, 1);
}

/**
 * Handles the response for UNBLOCK obtained from the server.
 */
void handle_unblock_response(struct packet* packet)
{
	handle_block_or_unblock_response(packet, 0);
}

void _get_msg(char* msg, struct packet* packet)
{
	int i = 1;
	int j = 0;
	for (; i < strlen(packet->data) - 1; i++, j++)
	{
		msg[j] = packet->data[i];
	}
	msg[j] = '\0';
}

/**
 * Handles the message relayed by the server.
 */
void handle_received_msg(struct packet* packet)
{
	char msg[MESSAGE_LENGTH];
	_get_msg(msg,packet);
	cse4589_print_and_log("[%s:SUCCESS]\n", "RECEIVED");
	cse4589_print_and_log("msg from:%s\n[msg]:%s\n", packet->src_ip, msg);
	cse4589_print_and_log("[%s:END]\n", "RECEIVED");
}

void handle_file_recv(struct packet* packet, int fd)
{
	// {size:'%d',name='%s'}
	int i = 7;
	int j = 0;
	
	char size[10];
	for (; packet->data[i] != '\''; i++, j++)
	{
		size[j] = packet->data[i];
	}
	size[j] = '\0';
	
	int file_size = atoi(size);
	
	i = i + 8;
	char file_name[PATH_LEN];
	
	j = 0;
	for (; i < strlen(packet->data) - 2; i++, j++)
	{
		file_name[j] = packet->data[i];
	}
	file_name[j] = '\0';
	
	_info("File download: name = %s, size = %d\n", file_name, file_size);

	int closed = 0;
	char* buf = malloc(file_size);
	receive_all(fd, buf, file_size, &closed);
	
	FILE *write_ptr;
	write_ptr = fopen(file_name,"wb");  // w for write, b for binary
	fwrite(buf, file_size, 1, write_ptr);
	fclose(write_ptr);
	free(buf);
	
	_info("File written succesfully!!\n");
}

