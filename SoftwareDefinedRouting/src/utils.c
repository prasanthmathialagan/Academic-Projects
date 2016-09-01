/*
 * utils.c
 *
 *  Created on: Apr 7, 2016
 *      Author: prasanth
 */

#include <stdio.h>
#include <arpa/inet.h>
#include <string.h>
#include <netdb.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/timerfd.h>
#include <time.h>

#include "../include/utils.h"
#include "../include/global.h"
#include "../include/customlogger.h"

/**
 * 	Returns the socket fd associated with the binded port
 */
int bind_port(int port, boolean tcp)
{
	struct sockaddr_in server_address;

	// Create socket
	int socket_fd = socket(AF_INET, tcp == TRUE ? SOCK_STREAM : SOCK_DGRAM, 0);
	if (socket_fd < 0)
	{
		_error("Cannot create socket.\n");
		return -1;
	}
	
	bzero(&server_address, sizeof(server_address));

	server_address.sin_family = AF_INET;
	server_address.sin_addr.s_addr = htonl(INADDR_ANY);
	server_address.sin_port = htons(port);

	// To avoid "address already in use error"
	int yes = 1;
	if (setsockopt(socket_fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(int)) == -1)
	{
		_error("setsockopt error.\n");
		close(socket_fd);
		return -1;
	}
	
	// Bind
	if (bind(socket_fd, (struct sockaddr *)&server_address, sizeof(server_address)) < 0)
	{
		_error("Bind failed.\n");
		close(socket_fd);
		return -1;
	}
	
	if(tcp == TRUE)
	{
		// Listen on the port
		if (listen(socket_fd, BACKLOG) < 0)
		{
			_error("Unable to listen on port %d", port);
			close(socket_fd);
			return -1;
		}
	}
	
	return socket_fd;
}

/**
 * 	Adds the given fd to select
 */
void add_to_select(int fd, CONTEXT* context)
{
	FD_SET(fd, &context->master);
	
	// Keep track of the max fd
	if(context->fdmax < fd)
	{
		context->fdmax = fd;
	}
}

/**
 *  Converts the IP address string to an array of 8-bit unsigned ints
 */
void get_ip_from_string(char* ip_str, uint8_t ip[4])
{
	uint32_t _32_bit_ip = inet_addr(ip_str);

	ip[0] = (uint8_t) (_32_bit_ip & 0xFF);
	ip[1] = ((uint8_t) (_32_bit_ip >> 8)) & 0xFF;
	ip[2] = ((uint8_t) (_32_bit_ip >> 16)) & 0xFF;
	ip[3] = ((uint8_t) (_32_bit_ip >> 24)) & 0xFF;
}

//http://beej.us/guide/bgnet/output/html/multipage/syscalls.html#getpeername
void get_peer_ip_string(int socket_fd, char* ip_str)
{
	struct sockaddr_in addr;
	socklen_t addr_size = sizeof(struct sockaddr_in);
	getpeername(socket_fd, (struct sockaddr *) &addr, &addr_size);
	strcpy(ip_str, inet_ntoa(addr.sin_addr));
}

/**
 * 	Get the IP address of the host at the other end of the connection
 */
void get_peer_ip(int socket_fd, uint8_t ip[4]) 
{
    char ip_str[20];
    get_peer_ip_string(socket_fd, ip_str);
    get_ip_from_string(ip_str, ip);
}

/**
 *  Accepts a new connection on the given socket fd and adds to select
 */
int _accept(int socket_fd, CONTEXT* context)
{
	// http://beej.us/guide/bgnet/output/html/multipage/syscalls.html#accept
	struct sockaddr_storage remoteaddr; // client address
	socklen_t addrlen = sizeof remoteaddr;
	
	int client_fd = accept(socket_fd, (struct sockaddr *) &remoteaddr, &addrlen);
	if (client_fd == -1)
	{
		_error("Unable to accept the new connection on the socket fd %d.\n", socket_fd);
		return -1;
	}
	
	add_to_select(client_fd, context);
	
	return client_fd;
}

/**
 *  Closes the given fd and removes it from the master fds
 */
void _close_fd(int fd, CONTEXT* context)
{
	close(fd);
	FD_CLR(fd, &context->master);
}

/**
 *  Receives the specified amount of bytes from the socket and fills the buffer. Sets closed = 1 if the socket is closed.
 */
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

/**
 * Unpacks the 16-bit unsigned short from the buffer
 * 
 * http://beej.us/guide/bgnet/output/html/multipage/advanced.html#serialization
 */
uint16_t unpack_u16(char* buf)
{
	return ((uint16_t) ((uint8_t) buf[0]) << 8) | (uint8_t) buf[1];
}

/**
 * Unpacks the IP address from the buffer
 * 
 * http://beej.us/guide/bgnet/output/html/multipage/advanced.html#serialization
 */
void unpack_ip(char* buf, uint8_t ip[4])
{
	int i;
	for (i = 0; i < 4; i++) 
		ip[i] = (uint8_t) buf[i];
}

/**
 * 	Prints the given IP address
 */
void print_ip(uint8_t ip[4])
{
	printf("%d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3]);
}

/*
** packi16() -- store a 16-bit int into a char buffer (like htons())
** 
** http://beej.us/guide/bgnet/output/html/multipage/advanced.html#serialization
*/ 
void packi16(char *buf, uint16_t i)
{
    buf[0] = i>>8; 
    buf[1] = i;
}

/**
 * Unpacks the IP address from the buffer
 * 
 * http://beej.us/guide/bgnet/output/html/multipage/advanced.html#serialization
 */
void pack_ip(char* buf, uint8_t ip[4])
{
	int i;
	for (i = 0; i < 4; i++) 
		buf[i] = ip[i];
}

// http://beej.us/guide/bgnet/output/html/multipage/advanced.html#sendall
int sendall(int socket_fd, char *buf, int *len)
{
	int total = 0;        // how many bytes we've sent
	int bytesleft = *len; // how many we have left to send
	int n;

	while (total < *len)
	{
		n = send(socket_fd, buf + total, bytesleft, 0);
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

/**
 * 	Sends the data over UDP
 */
void send_over_UDP(char* ip_address, int port, char* buffer, int buf_size)
{
	struct sockaddr_in server_address;

	// Create socket
	int sock_fd = socket(AF_INET, SOCK_DGRAM, 0);
	if (sock_fd < 0)
	{
		_error("Cannot create socket.\n");
		return;
	}

	bzero(&server_address, sizeof(server_address));
	server_address.sin_family = AF_INET;
	inet_pton(AF_INET, ip_address, &server_address.sin_addr);
	server_address.sin_port = htons(port);

	int bytes = sendto(sock_fd, buffer, buf_size, 0, (struct sockaddr *) &server_address, sizeof(server_address));
	_trace("%d bytes sent.\n", bytes);
	
	close(sock_fd);
}

/**
 *  Send routing updates to the neighbors
 */
void send_routing_updates(CONTEXT* context)
{
	char* buffer = malloc(ROUTING_UPDATE_HDRSIZE + (12 * context->topology->routers_count)); // For each router, we need 12 bytes for the payload
	
	int offset = 0;
	
	// --------------------------------------------------------------------
	// --------------------------------HEADER------------------------------
	// --------------------------------------------------------------------

	// Number of update fields
	packi16(buffer + offset, context->topology->routers_count);
	offset += 2;
	
	// Source router port
	packi16(buffer + offset, context->router_port);
	offset += 2;
	
	// Source router IP address
	pack_ip(buffer + offset, context->router_ip);
	offset += 4;
	
	// --------------------------------------------------------------------
	// ------------------------------PAYLOAD-------------------------------
	// --------------------------------------------------------------------
	
	int j;
	for (j = 0; j < context->topology->routers_count; ++j) 
	{
		ROUTER* router = context->topology->routers[j];
		
		// Router IP address
		pack_ip(buffer + offset, router->ip);
		offset += 4;
		
		// Router port
		packi16(buffer + offset, router->router_port);
		offset += 2;
		
		// Padding
		packi16(buffer + offset, PADDING);
		offset += 2;
		
		// Router ID
		packi16(buffer + offset, router->id);
		offset += 2;
		
		// Cost
		uint16_t cost = context->distance_vectors[context->router_idx][j];
		packi16(buffer + offset, cost);
		offset += 2;
	}

	// Send to all the neighbors' router port over UDP
	for (j = 0; j < context->topology->routers_count; ++j) 
	{
		ROUTER* router = context->topology->routers[j];
		if(router->neighbor == TRUE && router->link_cost != INF)
		{
			send_over_UDP(router->ip_address, router->router_port, buffer, offset);
		}
	}
}

int create_and_start_timer(uint16_t interval)
{
	int timer_fd = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK);
	
	struct itimerspec new_timer;
	new_timer.it_value.tv_sec = interval;
	new_timer.it_value.tv_nsec = 0;
	new_timer.it_interval.tv_sec = interval;
	new_timer.it_interval.tv_nsec = 0;
	
	timerfd_settime(timer_fd, 0, &new_timer, NULL);

	return timer_fd;
}

ROUTER* get_router_for_id(uint16_t id, CONTEXT* context)
{
	int j;
	for (j = 0; j < context->topology->routers_count; ++j)
	{
		ROUTER* r = context->topology->routers[j];
		if (r->id == id)
		{
			return r;
		}
	}
	
	return NULL;
}

ROUTER* get_router_for_ip(CONTEXT* context, char* ip)
{
	int j;
	for (j = 0; j < context->topology->routers_count; ++j)
	{
		ROUTER* r = context->topology->routers[j];
		if(!strcmp(r->ip_address, ip))
		{
			return r;
		}
	}
	
	return NULL;
}

int get_idx(CONTEXT* context, ROUTER* router)
{
	int j = -1;
	for (j = 0; j < context->topology->routers_count; ++j)
	{
		ROUTER* r = context->topology->routers[j];
		if(r == router)
			return j;
	}
	
	return j;
}

uint16_t sum_and_align_for_wrap_over(uint16_t a, uint16_t b)
{
	uint16_t sum = a + b;
	
	// In case of wrap over, sum will be less than a and b
	if(sum < a && sum < b)
		return INF;
	
	return sum;
}

/**
 * 	Returns TRUE if there is a change in the distance vector
 */
boolean compute_distant_vectors(CONTEXT* context)
{
	boolean changed = FALSE;
	
	TOPOLOGY* topology = context->topology;
	uint16_t* distance_vector = context->distance_vectors[context->router_idx];
	uint16_t* forwarding_table = context->forwarding_table;
	
	// du(z) = min { c(u,v) + dv (z), c(u,x) + dx(z), c(u,w) + dw(z) }
	int v, neigh;
	for (v = 0; v < topology->routers_count; ++v) 
	{
		// No need to compute to yourself
		if(context->router_idx == v)
			continue;
		
		// Find the cost through all the neighbors
		uint16_t min_cost = INF;
		uint16_t next_hop = INF;

		for (neigh = 0; neigh < topology->routers_count; ++neigh)
		{
			ROUTER* router = topology->routers[neigh];
			if (router->neighbor == FALSE)
				continue;

			uint16_t thru_cost = context->distance_vectors[neigh][v];
			uint16_t computed_cost = sum_and_align_for_wrap_over(router->link_cost, thru_cost);
			_trace("Cost to neighbor %d = %d, From neighbor to %d = %d, New cost = %d\n", neigh, router->link_cost, v, thru_cost, computed_cost);
			if(computed_cost < min_cost)
			{
				min_cost = computed_cost;
				next_hop = neigh;
			}
		}
		
		_debug("Minimum cost to %d = %d, thru neighbor %d\n", v, min_cost, next_hop);
		
		// If the cost has changed
		if(distance_vector[v] != min_cost || forwarding_table[v] != next_hop)
		{
			changed = TRUE;
			distance_vector[v] = min_cost;
			forwarding_table[v] = next_hop;
		}
	}

	print_DV_matrix(context);
	print_forwarding_table(context);
	
	return changed;
}

void print_forwarding_table(CONTEXT* context)
{
	printf("-------------------------------------------------------------\n");
	printf("%35s\n","ROUTING TABLE");
	printf("-------------------------------------------------------------\n");
	
	TOPOLOGY* topology = context->topology;
	
	int j;
	for (j = 0; j < topology->routers_count; ++j) 
	{
		ROUTER* router = topology->routers[j];
		ROUTER* next_hop_router = context->forwarding_table[j] == INF ? NULL : topology->routers[context->forwarding_table[j]];
		uint16_t cost = context->distance_vectors[context->router_idx][j];
		printf("%-8d%-20s%-8d%-20s%-8d\n", router->id, router->ip_address, 
				next_hop_router == NULL ? INF : next_hop_router->id, 
				next_hop_router == NULL ? "-" : next_hop_router->ip_address, cost);
	}

	printf("-------------------------------------------------------------\n");
}

void print_DV_matrix(CONTEXT* context)
{
	printf("-------------------------------------------------------------\n");
	printf("%40s\n", "DISTANCE VECTOR MATRIX");
	printf("-------------------------------------------------------------\n");
	
	TOPOLOGY* topology = context->topology;
	
	printf("%-8s", "");
	int j;
	for (j = 0; j < topology->routers_count; ++j)
	{
		ROUTER* router = topology->routers[j];
		printf("%-8d", router->id);
	}
	printf("\n");
	
	int k;
	for (j = 0; j < topology->routers_count; ++j)
	{
		ROUTER* router = topology->routers[j];
		printf("%-8d", router->id);
		
		uint16_t* dv = context->distance_vectors[j];
		for (k = 0; k < topology->routers_count; ++k)
		{
			printf("%-8d", dv[k]);
		}
		printf("\n");
	}
	
	printf("-------------------------------------------------------------\n");
}

void convert_ip_to_string(char* ip_addr_str, uint8_t ip[4])
{
	sprintf(ip_addr_str, "%d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3]);
}

ROUTER* get_next_hop(CONTEXT* context, char* dest_ip)
{
	ROUTER* dest_router = get_router_for_ip(context, dest_ip);
	int next_hop_idx = context->forwarding_table[get_idx(context, dest_router)];
	if(next_hop_idx == INF)
		return NULL;
	return context->topology->routers[next_hop_idx];
}

int create_fd_if_not_exists(ROUTER* router, CONTEXT* context)
{
	if(router->data_fd != -1)
		return 0;
	
	struct sockaddr_in router_address;

	// Create socket
	int data_fd = socket(AF_INET, SOCK_STREAM, 0);
	if (data_fd < 0)
	{
		_error("Cannot create socket.\n");
		return -1;
	}

	bzero(&router_address, sizeof(router_address));
	router_address.sin_family = AF_INET;
	inet_pton(AF_INET, router->ip_address, &router_address.sin_addr);
	router_address.sin_port = htons(router->data_port);

	if (connect(data_fd, (struct sockaddr *) &router_address, sizeof(router_address)) < 0)
	{
		close(data_fd);
		_error("Cannot connect to the socket.\n");
		return -1;
	}

	add_to_select(data_fd, context);
	router->data_fd = data_fd;
	
	return 0;
}

void add_to_transfer_list(TRANSFER_NODE* transfer_node, TRANSFERS_LIST** transfer_list)
{
	if(!(*transfer_list))
	{
		_trace("Transfers list is empty. So creating a new list.\n");
		*transfer_list = transfer_node;
	}
	else
	{
		_trace("Appending to the head of the existing transfers list.\n");
		transfer_node->next = *transfer_list;
		*transfer_list = transfer_node;
	}
}

TRANSFER_NODE* get_transfer(uint8_t transfer_id, CONTEXT* context)
{
	TRANSFER_NODE* transfer = context->transfer_list;
	while(transfer)
	{
		if(transfer->transfer_id == transfer_id)
			break;
		
		transfer = transfer->next;
	}
	
	return transfer;
}

/**
 * Updates the last packet and pen-ultimate packet.
 */
void save_last_and_penultimate_data_pkt(char* data_pkt_buffer, CONTEXT* context)
{
	char* data_pkt_to_save = malloc(DATA_PKT_SIZE);
	memcpy(data_pkt_to_save, data_pkt_buffer, DATA_PKT_SIZE);
	
	if(context->penultimate_data_packet != NULL)
		free(context->penultimate_data_packet);

	context->penultimate_data_packet = context->last_data_packet;
	context->last_data_packet = data_pkt_to_save;
}

void add_to_control_conn_list(int socket_fd, CONTEXT* context)
{
	CONTROL_CONN_NODE* connection = malloc(sizeof(CONTROL_CONN_NODE));
	connection->socket_fd = socket_fd;
	 
	// Add to the head
	connection->next = context->control_conn_list;
	context->control_conn_list = connection;
}

boolean is_control_connection(int socket_fd, CONTEXT* context)
{
	CONTROL_CONN_NODE* connection = context->control_conn_list;
	while(connection)
	{
		if(connection->socket_fd == socket_fd)
			return TRUE;
		
		connection = connection->next;
	}
	
	return FALSE;
}

void remove_from_control_conn_list(int socket_fd, CONTEXT* context)
{
	CONTROL_CONN_NODE* prev = NULL;
	CONTROL_CONN_NODE* curr = context->control_conn_list;
	while(curr)
	{
		if(socket_fd != curr->socket_fd)
		{
			prev = curr;
			curr = curr->next;
			continue;
		}
		
		// It is the first element in the list
		if(prev == NULL)
		{
			// Only one element
			if(curr->next == NULL)
			{
				context->control_conn_list = NULL;
			}
			else // More than one element. Move the pointer to the next element
			{
				context->control_conn_list = context->control_conn_list->next;
			}
		}
		else // If it is at other locations
		{
			prev->next = curr->next;
			curr->next = NULL;
		}
		
		free(curr);
	}
}
