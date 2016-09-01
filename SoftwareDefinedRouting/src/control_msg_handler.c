/*
 * control_msg_handler.c
 *
 *  Created on: Apr 8, 2016
 *      Author: prasanth
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>

#include "../include/global.h"
#include "../include/utils.h"
#include "../include/control_msg_handler.h"
#include "../include/customlogger.h"

CONTROL_MSG* extract_control_msg(int socket_fd, int* closed)
{
	char header[8];
	receive_all(socket_fd, header, 8, closed);
	if (*closed)
		return NULL;
	
	CONTROL_MSG* msg = malloc(sizeof(CONTROL_MSG));
	unpack_ip(header, msg->router_ip);
	msg->control_code = (uint8_t) header[4];
	msg->response_time = (uint8_t) header[5];
	msg->payload_size = unpack_u16(header + 6);
	
	_info("Control Message: Code = %d, Response time = %d, Payload size = %d\n", msg->control_code, msg->response_time, msg->payload_size);

	if (msg->payload_size > 0)
	{
		char* payload = malloc(msg->payload_size);
		receive_all(socket_fd, payload, msg->payload_size, closed);
		if (*closed)
		{
			free(payload);
			free(msg);
			return NULL;
		}
		
		msg->payload = payload;
	}
	else
	{
		msg->payload = NULL;
	}
	
	return msg;
}

void free_payload(CONTROL_MSG* msg)
{
	free(msg->payload);
	msg->payload = NULL;
}

/**
 * Sends the response to the controller. payload_size can be 0.
 */
void send_response(uint8_t control_code, uint8_t response_code, uint16_t payload_size, char* new_payload, int socket_fd)
{
	char* buffer = malloc(CTRLMSG_HDRSIZE + payload_size);
	
	int offset = 0;
	
	// --------------------------------------------------------------------
	// --------------------------------HEADER------------------------------
	// --------------------------------------------------------------------
	
	// Controller IP
	uint8_t controller_ip[4];
	get_peer_ip(socket_fd, controller_ip);
	pack_ip(buffer + offset, controller_ip);
	offset += 4;
	
	// Control code
	buffer[offset++] = control_code;
	
	// Response code
	buffer[offset++] = response_code;
	
	// Payload length
	packi16(buffer + offset, payload_size);
	offset += 2;
	
	// --------------------------------------------------------------------
	// ------------------------------PAYLOAD-------------------------------
	// --------------------------------------------------------------------
	
	if(payload_size > 0)
	{
		memcpy(buffer + offset, new_payload, payload_size);
		offset += payload_size;
	}
	
	_trace("Control response message packet size = %d\n", offset);
	sendall(socket_fd, buffer, &offset);
	_trace("Bytes sent to controller = %d\n", offset);
	
	free(buffer);
}

void handle_INIT(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	char* payload = msg->payload;
		
	int offset = 0;
	
	TOPOLOGY* topology = malloc(sizeof(TOPOLOGY)); 

	uint16_t routers_count = unpack_u16(payload + offset);
	topology->routers_count = routers_count;
	offset += 2;

	topology->refresh_interval = unpack_u16(payload + offset);
	offset += 2;
	
	_info("Routers = %d, Refresh interval = %d\n", topology->routers_count, topology->refresh_interval);
	
	topology->routers = malloc(routers_count * sizeof(ROUTER*));
	
	// Initialize distance vectors
	context->distance_vectors = malloc(routers_count * sizeof(uint16_t*));
	int j;
	for (j = 0; j < routers_count; ++j) 
	{
		context->distance_vectors[j] = malloc(routers_count * sizeof(uint16_t));
		int k;
		for (k = 0; k < routers_count; ++k)
		{
			context->distance_vectors[j][k] = INF;
		}
	}
	
	// Initialize forwarding table
	context->forwarding_table = malloc(routers_count * sizeof(uint16_t));
	for (j = 0; j < routers_count; ++j) 
	{
		context->forwarding_table[j] = INF;
	}
	
	for (j = 0; j < routers_count; ++j) 
	{
		ROUTER* router = malloc(sizeof(ROUTER));
		
		router->id = unpack_u16(payload + offset);
		offset += 2;
		
		router->router_port = unpack_u16(payload + offset);
		offset += 2;
		
		router->data_port = unpack_u16(payload + offset);
		offset += 2;
		
		router->link_cost = unpack_u16(payload + offset);
		router->neighbor = (router->link_cost != INF && router->link_cost != 0) ? TRUE : FALSE;
		router->timer_fd = -1;
		offset += 2;

		unpack_ip(payload + offset, router->ip);
		sprintf(router->ip_address, "%d.%d.%d.%d", router->ip[0], router->ip[1], router->ip[2], router->ip[3]);
		offset += 4;

		_info("ID = %d, Router port = %d, Data port = %d, Link cost = %d, IP = %s, isNeighbor = %d\n",router->id, router->router_port, 
				router->data_port, router->link_cost, router->ip_address, router->neighbor);
		
		router->data_fd = -1;
		
		topology->routers[j] = router;
	}
	
	// Sort the routers based on the ID
	int a, b;
	ROUTER* temp = NULL;
	for (a = 0; a < routers_count; a++)
	{
		for (b = a + 1; b < routers_count; b++)
		{
			if (topology->routers[a]->id > topology->routers[b]->id)
			{
				temp = topology->routers[a];
				topology->routers[a] = topology->routers[b];
				topology->routers[b] = temp;
			}
		}
	}
	
	context->topology = topology;
	
	free_payload(msg);
	
	// Get the details of the current router
	ROUTER* current_router = NULL;
	for (j = 0; j < routers_count; ++j) 
	{
		ROUTER* r = context->topology->routers[j];
		if(!strcmp(r->ip_address, context->ip_address))
		{
			current_router = r;
			break;
		}
	}
	
	// Set the router id and router index for the current router
	context->router_id = current_router->id;
	context->router_idx = j;
	_info("Current router: ID = %d, Index = %d\n", context->router_id, context->router_idx);

	// Start listening on router and data ports
	context->router_port = current_router->router_port;
	context->router_fd = bind_port(context->router_port, FALSE);
	add_to_select(context->router_fd, context);
	_info("Accepting connections on router port %d.\n", context->router_port);
	
	context->data_port = current_router->data_port;
	context->data_fd = bind_port(context->data_port, TRUE);
	add_to_select(context->data_fd, context);
	_info("Accepting connections on data port %d.\n", context->data_port);
	
	// Set initial values for the current router's distance vector and forwarding table
	for(j = 0; j < routers_count; j++)
	{
		ROUTER* router = topology->routers[j];
		context->distance_vectors[context->router_idx][j] = router->link_cost; // Initialize with the cost provided by the Controller
		context->forwarding_table[j] = router->link_cost == INF ? INF : j; // If the router is not a neighbor or itself, next hop will be INF
	}
	
	// Initialize Transfers list to NULL
	context->transfer_list = NULL;
	context->last_data_packet = NULL;
	context->penultimate_data_packet = NULL;
	
	print_DV_matrix(context);
	print_forwarding_table(context);
	
	// Send the distance vector to the neighbors
	send_routing_updates(context);
	
	// Create your timer for sending routing updates
	int timer_fd = create_and_start_timer(context->topology->refresh_interval);
	_trace("Timer FD = %d\n", timer_fd);
	context->timer_fd = timer_fd;
	add_to_select(timer_fd, context);
	
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, 0, NULL, socket_fd);
}

void handle_AUTHOR(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	char* payload = "I, pmathial, have read and understood the course academic integrity policy.";
	
	_info("%s\n", payload);
	
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, strlen(payload), payload, socket_fd);
}

void handle_ROUTING_TABLE(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	print_DV_matrix(context);
	print_forwarding_table(context);
	
	uint16_t routers_count = context->topology->routers_count;
	ROUTER** routers = context->topology->routers;
	uint16_t* forwarding_table = context->forwarding_table;
	uint16_t** distance_vectors = context->distance_vectors;
	
	char* payload = malloc(routers_count * 8); // For each router, we need 8 bytes
	int offset = 0;
	
	int i;
	for (i = 0; i < routers_count; ++i) 
	{
		ROUTER* router = routers[i];
		
		uint16_t router_id = router->id;
		packi16(payload + offset, router_id);
		offset += 2;
		
		// Padding
		packi16(payload + offset, PADDING);
		offset += 2;
		
		uint16_t next_hop_id = forwarding_table[i] == INF ? INF : routers[forwarding_table[i]]->id;
		packi16(payload + offset, next_hop_id);
		offset += 2;
		
		uint16_t cost = distance_vectors[context->router_idx][i];
		packi16(payload + offset, cost);
		offset += 2;
	}
	
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, offset, payload, socket_fd);
	
	free_payload(msg);
}

void handle_UPDATE(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	char* payload = msg->payload;
	
	uint16_t router_id = unpack_u16(payload);
	uint16_t new_cost = unpack_u16(payload + 2);
	
	_info("New link cost to router %d = %d\n", router_id, new_cost);
	
	// Update the new link cost
	ROUTER* router = get_router_for_id(router_id, context);
	router->link_cost = new_cost;
	
	// Re-compute distance vectors and update forwarding table. No need to send updates to the neighbors now
	compute_distant_vectors(context);
	
	free_payload(msg);
	
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, 0, NULL, socket_fd);
}

void handle_CRASH(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, 0, NULL, socket_fd);
	
	_info("Shutting down the router.\n");
	
	exit(1);
}

void send_data_pkt(char* buffer, uint16_t seq_num, char* file_buf, int file_offset, int fd)
{
	// Set the sequence number
	packi16(buffer + 6, seq_num);

	// Copy the contents from file buffer to send buffer
	memcpy(buffer + DATA_HDR_SIZE, file_buf + file_offset, FILE_SEG_SIZE);

	int len = DATA_PKT_SIZE;
	// Send the packet
	sendall(fd, buffer, &len);
}

// Send a file to the destination router
void handle_SENDFILE(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	char* payload = msg->payload;
	
	int offset = 0;
	
	TRANSFER_NODE* transfer = malloc(sizeof(TRANSFER_NODE));
	transfer->next = NULL;
	
	transfer->seq_num_list = NULL;
	transfer->seq_num_count = 0;
	transfer->last_seq_num = NULL;
	transfer->file_buffer = NULL;
	transfer->file_buf_offset = 0;

	unpack_ip(payload + offset, transfer->dest_ip); // Destination router IP address
	convert_ip_to_string(transfer->dest_ip_str, transfer->dest_ip);
	offset += 4;
	
	transfer->ttl = (uint8_t) payload[offset++]; // Initial TTL
	transfer->transfer_id = (uint8_t) payload[offset++]; // Transfer ID
	transfer->initial_seq_num = unpack_u16(payload + offset); // Sequence number
	offset += 2;
	
	int i = 0;
	char* file_name = malloc((msg->payload_size - offset) + 1); // +1 for '\0'
	while(offset < msg->payload_size)
	{
		file_name[i] = payload[offset];
		i++;
		offset++;
	}
	file_name[i] = '\0';
	transfer->file_name = file_name;
	
	_info("[FILE TRANSFER] Destination IP = %s, Initial TTL = %d, Transfer ID = %d, Initial Sequence number = %d, File = %s\n",
			transfer->dest_ip_str, transfer->ttl, transfer->transfer_id, transfer->initial_seq_num, transfer->file_name);

	ROUTER* next_hop_router = get_next_hop(context, transfer->dest_ip_str);
	_info("Next hop: IP = %s, Router ID = %d\n", next_hop_router->ip_address, next_hop_router->id);
	
	FILE* ptr = fopen(file_name, "rb");
	if (!ptr)
	{
		_error("Error opening file %s.\n", file_name);
		return;
	}

	fseek(ptr, 0, SEEK_END);
	int file_size = ftell(ptr);
	fseek(ptr, 0, SEEK_SET);

	int packets = file_size / FILE_SEG_SIZE;
	_info("File size = %d. Number of packets = %d.\n", file_size, packets);
	
	// Read file contents into buffer
	char* file_buf = malloc(file_size);
	fread(file_buf, file_size, 1, ptr);
	fclose(ptr);

	_trace("Next hop router FD = %d\n", next_hop_router->data_fd);
	
	// Establish connection to the next hop router if not exists
	if(create_fd_if_not_exists(next_hop_router, context) < 0)
		return;
	
	char buffer[DATA_PKT_SIZE];
	memset(buffer, 0, DATA_PKT_SIZE);
	
	// Copy the destination IP address
	pack_ip(buffer, transfer->dest_ip);
	
	// Copy the transfer ID
	buffer[4] = (uint8_t) transfer->transfer_id;
	
	// Copy the TTL
	buffer[5] = (uint8_t) transfer->ttl;
	
	// buffer[6] and buffer[7] correspond to the seq number
	// fin and padding are set to 0 in memset itself
	
	// Transfer the file
	SEQ_NUM_NODE* curr = NULL;
	int k = 1, file_offset = 0;
	uint16_t j;
	for (j = transfer->initial_seq_num; k <= packets - 1; j++, k++)
	{
		_trace("Sending packet(%d) with sequence number(%d).\n", k, j);
		send_data_pkt(buffer, j, file_buf, file_offset, next_hop_router->data_fd);
		
		SEQ_NUM_NODE* seq_num = malloc(sizeof(SEQ_NUM_NODE));
		seq_num->next = NULL;
		seq_num->seq_num = j;
		
		if(curr == NULL)
			transfer->seq_num_list = seq_num;
		else
			curr->next = seq_num;

		curr = seq_num;
		transfer->seq_num_count += 1;
		
		// Update the last packet and pen-ultimate packet. This must be the penultimate packet.
		if(k == packets - 1)
			save_last_and_penultimate_data_pkt(buffer, context);
		
		file_offset += FILE_SEG_SIZE;
	}
	
	// Send the last packet with fin set to 1
	buffer[8] = (uint8_t) 128;
			
	_trace("Sending the last packet(%d) with sequence number(%d).\n", k, j);
	send_data_pkt(buffer, j, file_buf, file_offset, next_hop_router->data_fd);
	
	// FIXME: Refactor this snippet
	SEQ_NUM_NODE* seq_num = malloc(sizeof(SEQ_NUM_NODE));
	seq_num->next = NULL;
	seq_num->seq_num = j;

	if (curr == NULL)
		transfer->seq_num_list = seq_num;
	else
		curr->next = seq_num;

	curr = seq_num;
	transfer->seq_num_count += 1;
	
	// Update the last packet and pen-ultimate packet
	save_last_and_penultimate_data_pkt(buffer, context);
	
	// add to transfers list
	add_to_transfer_list(transfer, &context->transfer_list);
	
	free(file_buf);
	free_payload(msg);
	
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, 0, NULL, socket_fd);
}

void handle_SENDFILE_STATS(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	char* payload = msg->payload;

	uint8_t transfer_id = (uint8_t) payload[0];
	TRANSFER_NODE* transfer = get_transfer(transfer_id, context);
	_info("[SENDFILE_STATS] Transfer ID = %d, TTL = %d, Total packets = %d.\n", transfer_id, transfer->ttl, transfer->seq_num_count);
	
	char* buffer = malloc(4 + transfer->seq_num_count * 2);
	int offset = 0;
	
	// Copy the transfer id
	buffer[offset++] = transfer_id;
	
	// Copy the TTL
	buffer[offset++] = transfer->ttl;
	
	// Padding
	packi16(buffer + offset, 0);
	offset += 2;
	
	// Copy the sequence number
	SEQ_NUM_NODE* curr = transfer->seq_num_list;
	while(curr)
	{
		packi16(buffer + offset, curr->seq_num);
		offset += 2;
		
		curr = curr->next;
	}
	
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, offset, buffer, socket_fd);
	
	free(buffer);
	free_payload(msg);
}

void handle_LAST_DATA_PKT(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, DATA_PKT_SIZE, context->last_data_packet, socket_fd);
}

void handle_PENULTIMATE_DATA_PKT(CONTROL_MSG* msg, CONTEXT* context, int socket_fd)
{
	// Respond to the controller
	send_response(msg->control_code, SUCCESS, DATA_PKT_SIZE, context->penultimate_data_packet, socket_fd);
}
