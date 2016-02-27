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

#include <stdlib.h>
#include <stdio.h>
#include <arpa/inet.h>
#include <string.h>
#include <netdb.h>
#include <unistd.h>

#include "../include/utils.h"
#include "../include/global.h"
#include "../include/customlogger.h"

int get_word_count(char* s)
{
	int count = 0;
	int i = 0;
	int j = 0;

	// Move to the first non empty character
	while (s[i] == ' ')
		i++;

	while (s[i] != '\0')
	{
		if (s[i] == ' ')
		{
			count++;
			j = 0;
			while (s[i] == ' ')
				i++;
		}
		else
		{
			j++;
			i++;
		}
	}

	if (j != 0)
		count++;

	return count;
}

char** split(char* s)
{
	int count = get_word_count(s);

	// Allocate memory
	char** arr = (char **) malloc(sizeof(char *) * count);

	int i = 0;
	int j = 0;
	int k = 0;

	// Move to the first empty character
	while (s[i] == ' ')
		i++;

	while (s[i] != '\0')
	{
		if (s[i] != ' ')
		{
			i++;
			j++;
			continue;
		}

		arr[k] = (char *) malloc(sizeof(char) * (j + 1));
		int a = 0;
		for (; a < j; a++)
		{
			arr[k][j - a - 1] = s[i - a - 1];
		}

		arr[k][j] = '\0';
		k++;
		j = 0;

		while (s[i] == ' ')
			i++;
	}

	if (j != 0)
	{
		arr[k] = (char *) malloc(sizeof(char) * (j + 1));
		int a = 0;
		for (;a < j; a++)
		{
			arr[k][j - a - 1] = s[i - a - 1];
		}

		arr[k][j] = '\0';
	}

	return arr;
}

// http://stackoverflow.com/questions/791982/determine-if-a-string-is-a-valid-ip-address-in-c
int validate_ip(char* ip_address)
{
	struct sockaddr_in sa;
	int result = inet_pton(AF_INET, ip_address, &(sa.sin_addr));
	return result;
}

// Adapted from http://beej.us/guide/bgnet/output/html/multipage/syscalls.html
int bind_port(struct application_context* context)
{
	struct sockaddr_in server_address;

	// Create socket
	context->socket_fd = socket(AF_INET, SOCK_STREAM, 0);
	if (context->socket_fd < 0)
	{
		_error("Cannot create socket.\n");
		return -1;
	}
	
	bzero(&server_address, sizeof(server_address));

	server_address.sin_family = AF_INET;
	server_address.sin_addr.s_addr = htonl(INADDR_ANY);
	server_address.sin_port = htons(context->port);

	// To avoid "address already in use error"
	int yes = 1;
	if (setsockopt(context->socket_fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(int)) == -1)
	{
		_error("setsockopt error.\n");
		close(context->socket_fd);
		return -1;
	}
	
	// Bind
	if (bind(context->socket_fd, (struct sockaddr *)&server_address, sizeof(server_address)) < 0)
	{
		_error("Bind failed.\n");
		close(context->socket_fd);
		return -1;
	}
	
	// Listen on the port
	if (listen(context->socket_fd, BACKLOG) < 0)
	{
		_error("Unable to listen on port %d", context->port);
		close(context->socket_fd);
		return -1;
	}
	
	return 0;
}

// SRCIP $ DESTIP $ SRCPORT $ DESTPORT $ COMMAND $ DATASIZE $ DATA
void serialize(struct packet* packet, char* buf)
{
	sprintf(buf, "%s$%s$%d$%d$%s$%d$%s", packet->src_ip, packet->dest_ip, packet->src_port, packet->dest_port, packet->command, 
			(int)strlen(packet->data), packet->data);
}

// Returns the client info associated with the client fd
CLIENTS* get_client_info(CLIENTS* clients, int client_fd)
{
	CLIENTS* temp = clients;
	while(temp)
	{
		if(temp->socket_fd == client_fd)
			return temp;
		temp = temp->next;
	}
		
	return NULL;
}

// Returns the client info associated with the IP. If port is -1, first client matching the IP address will be returned
CLIENTS* get_client(CLIENTS* clients, char* ip, int port)
{
	CLIENTS* temp = clients;
	while(temp)
	{
		if(!strcmp(temp->h.ip_address, ip))
		{
			if(port == -1 || temp->h.port == port)
				return temp;
		}
		temp = temp->next;
	}
		
	return NULL;
}

int get_clients_size(CLIENTS* clients)
{
	int size = 0;
	CLIENTS* temp = clients;
	while(temp)
	{
		size++;
		temp = temp->next;
	}
	return size;
}

// returns a boolean
int is_valid_client(char* ip_address, CLIENTS* clients)
{
	CLIENTS* temp = clients;
	while(temp)
	{
		if(!strcmp(temp->h.ip_address, ip_address))
			return 1;
		
		temp = temp->next;
	}
	return 0;
}

// returns NULL if no such ip address found. If port is -1, first entry matching the IP address will be returned.
struct stats_list* get_stats(char* ip_address, int port, struct stats_list* stats)
{
	struct stats_list* s = stats;
	while(s)
	{
		if(!strcmp(s->s.h.ip_address, ip_address))
		{
			if(port == -1 || port == s->s.h.port)
				return s;
		}
		s = s->next;
	}
	return s;
}

CLIENTS* new_client(char* ipaddress, char* host_name, int port)
{
	CLIENTS* client = malloc(sizeof(CLIENTS));
	strcpy(client->h.ip_address, ipaddress);
	strcpy(client->h.host_name, host_name);
	client->h.port = port;
	client->next = NULL;
	
	return client;
}

// http://beej.us/guide/bgnet/output/html/multipage/advanced.html#sendall
int sendall(int s, char *buf, int *len)
{
	int total = 0;        // how many bytes we've sent
	int bytesleft = *len; // how many we have left to send
	int n;

	while (total < *len)
	{
		n = send(s, buf + total, bytesleft, 0);
		if (n == -1)
		{
			break;
		}
		total += n;
		bytesleft -= n;
	}

	*len = total; // return number actually sent here

	return n == -1 ? -1 : 0; // return -1 on failure, 0 on success
}

struct packet* construct_packet(char* src_ip, char* dest_ip, int src_port,
		int dest_port, char* command, char* data)
{
	struct packet* packet = malloc(sizeof(struct packet));
	
	strcpy(packet->src_ip, src_ip);
	strcpy(packet->dest_ip, dest_ip);
	packet->src_port = src_port;
	packet->dest_port = dest_port;
	strcpy(packet->command, command);
	strcpy(packet->data, data);
	
	return packet;
}

void send_size(int size, int sock_fd)
{
	char size_str[SEND_RECV_SIZE_LEN];
	sprintf(size_str, "%d", size);
	int s = strlen(size_str) + 1;
	sendall(sock_fd, size_str, &s);
}

void serialize_send(struct packet* packet, int socket)
{
	char buf[SERIAL_BUFFER_SIZE];
	serialize(packet, buf);
	int packet_size = strlen(buf) + 1; // +1 for '\0'
	send_size(packet_size, socket);
	sendall(socket, buf, &packet_size);
}

struct stats_list* new_stats(char* hostname, char* ipaddress, int port)
{
	struct stats_list* sts = malloc(sizeof(struct stats_list));
	strcpy(sts->s.h.host_name, hostname);
	strcpy(sts->s.h.ip_address, ipaddress);
	sts->s.msgs_sent = 0;
	sts->s.msgs_received = 0;
	sts->s.status = 1;
	sts->s.h.port = port;
	sts->next = NULL;
	
	return sts;
}

void receive_all(int socket, char* buf, int packet_size, int* closed)
{
	int remaining = packet_size;
	int received = 0;
	int nbytes = 0;
	while(remaining > 0)
	{
		nbytes = recv(socket, buf + received, remaining, 0);
		if(nbytes <= 0)
		{
			*closed = 1;
			return;
		}
		received += nbytes;
		remaining -= nbytes;
	}
}

// closed is boolean indicating the socket has been closed
int receive_size(int socket, int* closed)
{
	char size_str[SEND_RECV_SIZE_LEN];
	int i = -1;
	int nbytes = 0;
	do
	{
		i++;
		nbytes = recv(socket, size_str + i, 1, 0);
		if (nbytes <= 0)
		{
			*closed = 1;
			return -1;
		}
	} while (size_str[i] != '\0');
	
	int size = atoi(size_str);
	return size;
}

// closed is boolean indicating the socket has been closed
void construct_packet_from_stream(int socket, int* closed, struct packet* packet)
{
	int packet_size = receive_size(socket, closed);
	if (*closed)
		return;

	_debug("Packet size --> %d\n", packet_size);
	
	char buf[SERIAL_BUFFER_SIZE];
	receive_all(socket, buf, packet_size, closed);
	
	_trace("Message received --> %s\n", buf);
	
	// http://stackoverflow.com/questions/3217629/in-c-how-do-i-find-the-index-of-a-character-within-a-string
	char *start = buf;
	char *e;
	int index;

	// Src ip
	e = strchr(start, '$');
	index = (int) (e - start);
	strncpy(packet->src_ip, start, index);
	packet->src_ip[index] = '\0';
	start = e + 1;

	// Dest ip
	e = strchr(start, '$');
	index = (int) (e - start);
	strncpy(packet->dest_ip, start, index);
	packet->dest_ip[index] = '\0';
	start = e + 1;

	// Src port
	e = strchr(start, '$');
	index = (int) (e - start);
	char p[6];
	strncpy(p, start, index);
	p[index] = '\0';
	packet->src_port = atoi(p);
	start = e + 1;

	// Dest port
	e = strchr(start, '$');
	index = (int) (e - start);
	strncpy(p, start, index);
	p[index] = '\0';
	packet->dest_port = atoi(p);
	start = e + 1;

	// Command
	e = strchr(start, '$');
	index = (int) (e - start);
	strncpy(packet->command, start, index);
	packet->command[index] = '\0';
	start = e + 1;

	// Data size
	e = strchr(start, '$');
	index = (int) (e - start);
	char size[4];
	strncpy(size, start, index);
	size[index] = '\0';
	int data_size = atoi(size);
	start = e + 1;

	strncpy(packet->data, start, data_size);
	packet->data[data_size] = '\0';

	_trace("Src IP = %s, Dest IP = %s, Src port = %d, Dest port = %d, Command = %s, Data size = %d, Data = %s\n", 
			packet->src_ip, packet->dest_ip, packet->src_port, packet->dest_port, packet->command, data_size, packet->data);
}

void add_msg(struct messages_map* map, char* src_ip, char* dest_ip, char* msg, int broadcast)
{
	struct messages* m = malloc(sizeof(struct messages));
	strcpy(m->src_ip, src_ip);
	strcpy(m->message, msg);
	m->broadcast = broadcast;
	m->next = NULL;
	
	struct messages_map* temp = map;
	while(temp)
	{
		if(!strcmp(temp->ip, dest_ip))
			break;
		
		temp = temp->next;
	}
	
	if(!temp->msgs)
	{
		temp->msgs = m;
	}
	else
	{
		struct messages* parent = temp->msgs;
		while(parent->next)
		{
			parent = parent->next;
		}
		parent->next = m;
	}
}

void update_fdmax(int sock_fd, struct application_context* context)
{
	if (context->fdmax < sock_fd)
	{
		context->fdmax = sock_fd;
	}
}

void free_clients(CLIENTS* c)
{
	CLIENTS* next = c->next;
	if(next)
		free_clients(next);
	free(c);
}

struct blocked_clients* get_blocked_clients(struct blocked_clients* blokd_clients, char* ip)
{
	struct blocked_clients* c = blokd_clients;
	while (c)
	{
		if (!strcmp(c->host_ip, ip))
			break;

		c = c->next;
	}
	return c;
}

// src_ip - ip sending the msg
// dest_ip - ip receving the msg
// Check if the dest_ip has blocked the src_ip
int is_blocked(char* src_ip, char* dest_ip, struct blocked_clients* blokd_clients)
{
	struct blocked_clients* c = get_blocked_clients(blokd_clients, dest_ip);
	if(!c)
	{
		return 0; // Not blocked
	}
	
	CLIENTS *temp = c->clients;
	while(temp)
	{
		if(!strcmp(temp->h.ip_address, src_ip)) // Blocked
			return 1;
		
		temp = temp->next;
	}
	
	return 0;
}

// TODO : Update fdmax
void _close_socket(int fd, struct application_context* context)
{
	// close the socket and remove from read fds 
	close(fd);
	FD_CLR(fd, &context->master);
}

void increment_msgs_sent(char* ip, int port, struct stats_list* stats, int count)
{
	struct stats_list* client_stats = get_stats(ip, port, stats);
	client_stats->s.msgs_sent += count;
}

void increment_msgs_recvd(char* ip, int port, struct stats_list* stats, int count)
{
	struct stats_list* client_stats = get_stats(ip, port, stats);
	client_stats->s.msgs_received += count;
}

int is_online(SERVER_INFO* ser_info)
{
	return ser_info->online_with_server;
}

int is_offline(SERVER_INFO* ser_info)
{
	return !is_online(ser_info);
}
