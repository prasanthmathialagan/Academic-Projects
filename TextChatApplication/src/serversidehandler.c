/*
 * serversidehandler.c
 *
 *  Created on: Feb 18, 2016
 *      Author: prasanth
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../include/global.h"
#include "../include/utils.h"
#include "../include/serversidehandler.h"
#include "../include/logger.h"
#include "../include/customlogger.h"

/**
 * 	Inserts the given client at the correct position in the clients list
 */
void update_clients(CLIENTS* client, CLIENTS** clients)
{
	// Insert at the correct position. Sorted by port number
	if (!(*clients))
	{
		*clients = client;
	}
	else if ((*clients)->h.port > client->h.port) // If the port is less than the first element
	{
		client->next = *clients;
		*clients = client;
	}
	else        // Insert at the other positions
	{
		CLIENTS* prev = NULL;
		CLIENTS* curr = *clients;
		while (curr)
		{
			if (curr->h.port > client->h.port)
				break;

			prev = curr;
			curr = curr->next;
		}
		prev->next = client;
		client->next = curr;
	}
}

/**
 * Updates statistics for the given client
 */
void update_stats(char* host_name, char* ip_address, int port, struct stats_list** stats)
{
	// Insert at the correct position. Sorted by port number
	if (!(*stats))
	{
		*stats = new_stats(host_name, ip_address, port);
	}
	else // if stats is not null, check for the presence of stats for the given 
	{
		struct stats_list* temp = *stats;
		while (temp)
		{
			if (!strcmp(host_name, temp->s.h.host_name) && port == temp->s.h.port)
				break;

			temp = temp->next;
		}
		// Already present. Set the client to online
		if (temp)
		{
			temp->s.status = 1;
		}
		else
		{
			struct stats_list* new_s = new_stats(host_name, ip_address, port);
			
			if ((*stats)->s.h.port > new_s->s.h.port) // If the port is less than the first element
			{
				new_s->next = *stats;
				*stats = new_s;
			}
			else    // Insert at the other positions
			{
				struct stats_list* prev = NULL;
				struct stats_list* curr = *stats;
				while (curr)
				{
					if (curr->s.h.port > new_s->s.h.port)
						break;

					prev = curr;
					curr = curr->next;
				}
				prev->next = new_s;
				new_s->next = curr;
			}
		}
	}
}

void form_clients_data(char* hosts, int list_size, CLIENTS* clients)
{
	hosts[0] = '[';
	int k = 1; // ptr to the hosts

	int count = 1;
	CLIENTS* temp = clients;
	for (; count <= list_size; count++)
	{
		char host[200];
		int c = sprintf(host, "{name:'%s',ip:'%s',port:'%d'}",
				temp->h.host_name, temp->h.ip_address, temp->h.port);
		strncpy(hosts + k, host, c);
		k = k + c;
		if (count < list_size)
		{
			hosts[k++] = ',';
		}
		temp = temp->next;
	}
	
	hosts[k++] = ']';
	hosts[k] = '\0';
}

void log_msgs(struct messages* msgs, char* dest_ip)
{
	if(!msgs->broadcast)
	{
		cse4589_print_and_log("[%s:SUCCESS]\n", "RELAYED");
		cse4589_print_and_log("msg from:%s, to:%s\n[msg]:%s\n", msgs->src_ip, dest_ip, msgs->message);
		cse4589_print_and_log("[%s:END]\n", "RELAYED");
	}

	if (msgs->next)
		log_msgs(msgs->next, dest_ip);
}

void free_msgs(struct messages* msgs)
{
	if (msgs->next)
		free_msgs(msgs->next);
	free(msgs);
}

/**
 *  Sends the updated list to the server
 */
void send_clients_list_and_msgs(int client_fd, struct packet* packet, char* command, struct application_context* context, 
		CLIENTS* clients, struct messages* messages, struct stats_list* stats)
{
	char hosts[800];
	int clients_c = get_clients_size(clients);
	form_clients_data(hosts, clients_c, clients);

	char data[PACKET_DATA_LEN];
	sprintf(data, "{size='%d',hosts:%s}", clients_c, hosts);
	
	struct packet* loc_packet = construct_packet(context->ip_address, packet->src_ip, context->port, packet->src_port, command, data);
	serialize_send(loc_packet, client_fd);
	free(loc_packet);
	
	if(strcmp(command, CMD_LOGIN))
		return;
	
	// Send the pending messages. Also free the memory allocated to messages. Update the statistics
	int msgs_c = 0;
	if (messages)
	{
		struct messages* t = messages;
		while (t)
		{
			msgs_c++;
			t = t->next;
		}
	}

	_info("Total msgs = %d\n", msgs_c);
	send_size(msgs_c, client_fd);
	if(!msgs_c)
		return;
	
	struct messages* msg_temp = messages;
	int msg_i;
	for (msg_i = 1; msg_i <= msgs_c; msg_i++)
	{
		char _msg[MESSAGE_LENGTH + IP_ADDRESS_LEN + 3]; // +3 for the delimiters
		sprintf(_msg, "%s$%s", msg_temp->src_ip, msg_temp->message);
		int len = strlen(_msg) + 1; // +1 for '\0'
		send_size(len, client_fd); // Send size
		sendall(client_fd, _msg, &len); // Send message
		msg_temp = msg_temp->next;
	}
	
	log_msgs(messages, packet->src_ip);
	
	// Free memory
	free_msgs(messages);

	// Update messages received
	increment_msgs_recvd(packet->src_ip, packet->src_port, stats, msgs_c);
}

/**
 * Handles the LOGIN request from the clients.
 */
void handle_login_request(struct packet* packet, int client_fd, CLIENTS** clients, struct stats_list** stats, 
		struct blocked_clients** blokd_clients, struct messages_map** msgs_map, struct application_context* context)
{
	CLIENTS* client = malloc(sizeof(CLIENTS));
	
	// Getting host name from data
	// Format of the data : {hostname:'Prasanth-Inspiron'}
	int size = strlen(packet->data);
	int start = 11;
	int end = size - 2;
	
	int i;
	int j = 0;
	for(i = start; i < end; i++, j++)
	{
		client->h.host_name[j] = packet->data[i]; 
	}
	client->h.host_name[j] = '\0';

	strcpy(client->h.ip_address, packet->src_ip);
	client->h.port = packet->src_port;
	client->socket_fd = client_fd;
	client->next = NULL;

	// Update clients
	update_clients(client, clients);
	
	// Add or update statistics
	update_stats(client->h.host_name, client->h.ip_address, client->h.port, stats);
	
	// Add or update blocked clients
	// Insert at the correct position. Sorted by port number
	if (!(*blokd_clients))
	{
		*blokd_clients = malloc(sizeof(struct blocked_clients));
		strcpy((*blokd_clients)->host_ip, packet->src_ip);
		(*blokd_clients)->clients = NULL;
		(*blokd_clients)->next = NULL;
	}
	else 
	{
		struct blocked_clients* temp = *blokd_clients;
		while (temp)
		{
			if (!strcmp(temp->host_ip, packet->src_ip))
				break;

			temp = temp->next;
		}
		
		// if there is no entry for the client
		if (!temp)
		{
			struct blocked_clients* c = malloc(sizeof(struct blocked_clients));
			strcpy(c->host_ip, packet->src_ip);
			c->clients = NULL;
			c->next = NULL;
			
			struct blocked_clients* parent = *blokd_clients;
			while(parent->next)
			{
				parent = parent->next;
			}
			parent->next = c;
		}
	}
	
	// Get the pending messages
	struct messages* msgs_to_send = NULL;
	if(!*msgs_map) // If there are no messages
	{
		*msgs_map = malloc(sizeof(struct messages_map));
		strcpy((*msgs_map)->ip, packet->src_ip);
		(*msgs_map)->msgs = NULL;
		(*msgs_map)->next = NULL;
	}
	else
	{
		struct messages_map* temp = *msgs_map;
		while (temp)
		{
			if (!strcmp(temp->ip, packet->src_ip))
				break;

			temp = temp->next;
		}
		
		// if there is no entry for the client
		if (!temp)
		{
			struct messages_map* m = malloc(sizeof(struct messages_map));
			strcpy(m->ip, packet->src_ip);
			m->msgs = NULL;
			m->next = NULL;
			
			struct messages_map* parent = *msgs_map;
			while (parent->next)
			{
				parent = parent->next;
			}
			parent->next = m;
		}
		else
		{
			// Check if there are any buffered messages
			msgs_to_send = temp->msgs;
			
			// Remove the reference as the messages will be delivered to the client.
			temp->msgs = NULL;
		}
	}
	
	// Send the client list and pending messages, Also free the memory allocated to the messages
	send_clients_list_and_msgs(client_fd, packet, CMD_LOGIN, context, *clients, msgs_to_send, *stats);
}

void respond_with_msg(struct packet* old_packet, char* msg, int client_fd)
{
	struct packet* new_pack = construct_packet(old_packet->dest_ip, old_packet->src_ip, old_packet->dest_port, 
			old_packet->src_port, old_packet->command, msg);
	serialize_send(new_pack, client_fd);
	free(new_pack);
}

/**
 *  Handles the BLOCK request from the clients. Acknowledges the client with OK or ERROR response.
 */
void handle_block_request(struct packet* packet, struct stats_list* stats, struct blocked_clients* blokd_clients, int client_fd)
{
	char* src_ip = packet->src_ip;
	char ip_to_block[IP_ADDRESS_LEN];
	
	// {ip:'128.205.36.46'}
	// Get the IP to block
	int size = strlen(packet->data);
	int start = 5;
	int end = size - 2;

	int i;
	int j = 0;
	for (i = start; i < end; i++, j++)
	{
		ip_to_block[j] = packet->data[i];
	}
	ip_to_block[j] = '\0';
	
	// We are getting the client information from stats since the client may not be online.
	struct stats_list* blkd_cl_info = get_stats(ip_to_block, -1, stats);
	
	// Find the node corresponding to the given client
	struct blocked_clients *c = blokd_clients;
	while (c)
	{
		if (!strcmp(c->host_ip, src_ip))
			break;

		c = c->next;
	}

	// Add to the blocked clients list
	
	// If the list is empty
	if(!c->clients)
	{
		CLIENTS* new_c = new_client(ip_to_block, blkd_cl_info->s.h.host_name, blkd_cl_info->s.h.port);
		c->clients = new_c;
	}
	else 
	{
		CLIENTS* temp = c->clients;
		while (temp)
		{
			if (!strcmp(ip_to_block, temp->h.ip_address))
			{
				// Already blocked.
				_warn("Client %s is already blocked by %s\n", ip_to_block, src_ip);
				
				// Send ERROR message to the client
				respond_with_msg(packet, "{ERROR}", client_fd);

				return ; 
			}

			temp = temp->next;
		}
		
		// If create a new client and add to list
		CLIENTS* new_c = new_client(ip_to_block, blkd_cl_info->s.h.host_name, blkd_cl_info->s.h.port);
		if (c->clients->h.port > new_c->h.port) // If the port is less than the first element
		{
			new_c->next = c->clients;
			c->clients = new_c;
		}
		else    // Insert at the other positions
		{
			CLIENTS* prev = NULL;
			CLIENTS* curr = c->clients;
			while (curr)
			{
				if (curr->h.port > new_c->h.port)
					break;

				prev = curr;
				curr = curr->next;
			}
			prev->next = new_c;
			new_c->next = curr;
		}
	}
	
	// Send OK message to the client
	respond_with_msg(packet, "{OK}", client_fd);
}

/**
 *  Handles UNBLOCK requests from the clients. Acknowledges the client with OK or ERROR response.
 */
void handle_unblock_request(struct packet* packet, struct blocked_clients* blokd_clients, int client_fd)
{
	char* src_ip = packet->src_ip;
	char ip_to_unblock[IP_ADDRESS_LEN];

	// {ip:'128.205.36.46'}
	// Get the IP to block
	int size = strlen(packet->data);
	int start = 5;
	int end = size - 2;

	int i;
	int j = 0;
	for (i = start; i < end; i++, j++)
	{
		ip_to_unblock[j] = packet->data[i];
	}
	ip_to_unblock[j] = '\0';

	// Find the node corresponding to the given client
	struct blocked_clients *c = blokd_clients;
	while (c)
	{
		if (!strcmp(c->host_ip, src_ip))
			break;

		c = c->next;
	}
	
	// Remove from client list
	CLIENTS *prev = NULL;
	CLIENTS *curr = c->clients;
	while (curr)
	{
		if (!strcmp(curr->h.ip_address, ip_to_unblock))
			break;
		
		prev = curr;
		curr = curr->next;
	}
	
	// If the list is empty or if the client is not found
	if(!curr)
	{
		_warn("Client %s is not blocked by %s\n", ip_to_unblock, src_ip);
		
		// Send ERROR message to the client
		respond_with_msg(packet, "{ERROR}", client_fd);
		return ;
	}

	// It is the first element in the list
	if (!prev)
	{
		// Only one element
		if (!curr->next)
		{
			c->clients = NULL;
		}
		else // More than one element. Move the pointer to the next element
		{
			c->clients = c->clients->next;
		}
	}
	else // If it is at other locations
	{
		prev->next = curr->next;
		curr->next = NULL;
	}
	
	// Free the memory
	free(curr);
	
	// Send OK message to the client
	respond_with_msg(packet, "{OK}", client_fd);
}

/**
 *  Handles LOGOUT request from the clients.
 */
void handle_logout_request(char* ip_address, int port, int client_fd, CLIENTS** clients, struct stats_list* stats, 
		struct application_context* context)
{
	// Remove from client list
	CLIENTS *prev = NULL;
	CLIENTS *curr = *clients;
	while (curr)
	{
		if(!strcmp(curr->h.ip_address, ip_address) && curr->h.port == port)
			break;
		prev = curr;
		curr = curr->next;
	}
	
	// If the client has already logged out
	if(!curr)
	{
		_debug("Client has already logged out\n");
		return;
	}

	// It is the first element in the list
	if(!prev)
	{
		// Only one element
		if(!curr->next)
		{
			*clients = NULL;
		}
		else // More than one element. Move the pointer to the next element
		{
			*clients = (*clients)->next;
		}
	}
	else // If it is at other locations
	{
		prev->next = curr->next;
		curr->next = NULL;
	}
	
	// Update statistics
	struct stats_list *temp = stats;
	while (temp)
	{
		if(!strcmp(temp->s.h.host_name, curr->h.host_name) && temp->s.h.port == curr->h.port)
		{
			temp->s.status = 0; // Setting it to offline
			break;
		}
		temp = temp->next;
	}
	
	// Free the memory
	free(curr);
}

void get_msg(char* msg, struct packet* packet)
{
	int i = 1;
	int j = 0;
	for (; i < strlen(packet->data) - 1; i++, j++)
	{
		msg[j] = packet->data[i];
	}
	msg[j] = '\0';
}

void handle_send0(char* src_ip, char* dest_ip, int dest_port, struct packet* packet, struct blocked_clients* blokd_clients, CLIENTS* clients, 
		struct messages_map* msgs_map, struct stats_list* stats, int broadcast)
{
	char msg[MESSAGE_LENGTH];
	get_msg(msg, packet);
	
	// If the dest ip is 127.0.0.1, then the server should send the msg to the src ip again in case of not a broadcast
	if(!broadcast && !strcmp(_LOCALHOST, dest_ip))
	{
		_info("Sending the msg to the same client since the dest ip is 127.0.0.1\n");
		
		// If the own IP address is blocked
		if (is_blocked(src_ip, src_ip, blokd_clients))
		{
			_info("%s is blocked by %s. Hence skipping the message.\n", src_ip, src_ip);
			return;
		}
		
		CLIENTS* cl = get_client(clients, src_ip, dest_port);
		serialize_send(packet, cl->socket_fd);
		increment_msgs_recvd(src_ip, packet->src_port, stats, 1);
		cse4589_print_and_log("[%s:SUCCESS]\n", "RELAYED");
		cse4589_print_and_log("msg from:%s, to:%s\n[msg]:%s\n", src_ip, dest_ip, msg);
		cse4589_print_and_log("[%s:END]\n", "RELAYED");
		return;
	}
	
	// Check if the client is blocked
	if (is_blocked(src_ip, dest_ip, blokd_clients))
	{
		_info("%s is blocked by %s. Hence skipping the message.\n", src_ip, dest_ip);
		return;
	}

	// Check if the client is online
	CLIENTS* c = get_client(clients, dest_ip, dest_port);
	if (c)
	{
		// If online, send message to the client
		serialize_send(packet, c->socket_fd);
		increment_msgs_recvd(dest_ip, dest_port, stats, 1);
		if(!broadcast)
		{
			cse4589_print_and_log("[%s:SUCCESS]\n", "RELAYED");
			cse4589_print_and_log("msg from:%s, to:%s\n[msg]:%s\n", src_ip, dest_ip, msg);
			cse4589_print_and_log("[%s:END]\n", "RELAYED");
		}
		return;
	}

	// add to messages list
	_info("Adding msg %s to the buffer for the client %s.\n", msg, dest_ip);
	add_msg(msgs_map, src_ip, dest_ip, msg, broadcast);
}

/**
 *  Handles the SEND request from the clients.
 */
void handle_send_request(struct packet* packet, struct blocked_clients* blokd_clients, CLIENTS* clients, struct messages_map* msgs_map,
		struct stats_list* stats)
{
	char* src_ip = packet->src_ip;
	increment_msgs_sent(src_ip, packet->src_port, stats, 1);
	char* dest_ip = packet->dest_ip;
	handle_send0(src_ip, dest_ip, packet->dest_port, packet, blokd_clients, clients, msgs_map, stats, 0);
}

/**
 *  Handles the BROADCAST request from the clients.
 */
void handle_broadcast_request(struct packet* packet, struct blocked_clients* blokd_clients, CLIENTS* clients, struct stats_list* stats, 
		struct messages_map* msgs_map)
{
	char* src_ip = packet->src_ip;
	increment_msgs_sent(src_ip, packet->src_port, stats, 1);

	// Stats map is used for getting all the clients since the clients list will contain only logged-in clients
	struct stats_list* temp = stats;
	while(temp)
	{
		// Send msg to all the clients other than the one that broadcasted the message
		if(strcmp(temp->s.h.ip_address, src_ip) || temp->s.h.port != packet->src_port)
		{
			handle_send0(src_ip, temp->s.h.ip_address, temp->s.h.port, packet, blokd_clients, clients, msgs_map, stats, 1);
		}

		temp = temp->next;
	}

	char msg[MESSAGE_LENGTH];
	get_msg(msg, packet);
	
	cse4589_print_and_log("[%s:SUCCESS]\n", "RELAYED");
	cse4589_print_and_log("msg from:%s, to:%s\n[msg]:%s\n", src_ip, "255.255.255.255", msg);
	cse4589_print_and_log("[%s:END]\n", "RELAYED");
}

void remove_from_stats(char* ip_address, int port, struct stats_list** stats)
{
	struct stats_list* prev = NULL;
	struct stats_list* curr = *stats;
	while (curr)
	{
		if (!strcmp(curr->s.h.ip_address, ip_address) && curr->s.h.port == port)
			break;

		prev = curr;
		curr = curr->next;
	}
	
	// It is the first element in the list
	if (!prev)
	{
		// Only one element
		if (!curr->next)
		{
			*stats = NULL;
		}
		else // More than one element. Move the pointer to the next element
		{
			*stats = (*stats)->next;
		}
	}
	else // If it is at other locations
	{
		prev->next = curr->next;
		curr->next = NULL;
	}
	
	free(curr);
}

void remove_from_blocked_clients(struct blocked_clients** blokd_clients, char* ip_address)
{
	struct blocked_clients* prev = NULL;
	struct blocked_clients* curr = *blokd_clients;
	while (curr)
	{
		if (!strcmp(curr->host_ip, ip_address))
			break;

		prev = curr;
		curr = curr->next;
	}
	
	if(!curr)
	{
		_warn("Something is fishy!! There is no entry for the client %s in blocked clients list.\n", ip_address);
		return;
	}
	
	// It is the first element in the list
	if (!prev)
	{
		// Only one element
		if (!curr->next)
		{
			*blokd_clients = NULL;
		}
		else // More than one element. Move the pointer to the next element
		{
			*blokd_clients = (*blokd_clients)->next;
		}
	}
	else // If it is at other locations
	{
		prev->next = curr->next;
		curr->next = NULL;
	}
	
	free(curr);
}

/**
 * Handles EXIT request from the client
 */
void handle_exit_request(char* ip_address, int port, int client_fd, CLIENTS** clients, struct stats_list** stats, 
		struct blocked_clients** blokd_clients, struct application_context* context, struct messages_map* msgs_map)
{
	handle_logout_request(ip_address, port, client_fd, clients, *stats, context);
	
	// Remove statistics
	remove_from_stats(ip_address, port, stats);
	
	// Remove blocked clients
	remove_from_blocked_clients(blokd_clients, ip_address);
	
	// Remove from the client list of any of the clients in blocked_clients
	struct blocked_clients* temp = *blokd_clients;
	while(temp)
	{
		// Remove from client list
		CLIENTS *prev = NULL;
		CLIENTS *curr = temp->clients;
		while (curr)
		{
			if (!strcmp(curr->h.ip_address, ip_address))
			{
				// It is the first element in the list
				if (!prev)
				{
					// Only one element
					if (!curr->next)
					{
						temp->clients = NULL;
					}
					else // More than one element. Move the pointer to the next element
					{
						temp->clients = temp->clients->next;
					}
				}
				else // If it is at other locations
				{
					prev->next = curr->next;
					curr->next = NULL;
				}

				// Free the memory
				free(curr);
				
				break;
			}
			
			prev = curr;
			curr = curr->next;
		}
		
		temp = temp->next;
	}
	
	// Remove pending messages
	if (msgs_map)
	{
		struct messages_map* temp = msgs_map;
		while (temp)
		{
			if (!strcmp(temp->ip, ip_address))
				break;

			temp = temp->next;
		}

		if(temp && temp->msgs)
		{
			free_msgs(temp->msgs);
			temp->msgs = NULL;
		}
	}
	
	_close_socket(client_fd, context);
}
