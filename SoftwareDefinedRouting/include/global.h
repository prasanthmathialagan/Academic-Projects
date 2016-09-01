/*
 * global.h
 *
 *  Created on: Apr 7, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_GLOBAL_H_
#define INCLUDE_GLOBAL_H_

#include <sys/socket.h>
#include <stdint.h>

#define BACKLOG 20
#define CTRLMSG_HDRSIZE 8
#define IPADDR_LEN 16
#define PADDING 0
#define ROUTING_UPDATE_HDRSIZE 8
#define FILE_SEG_SIZE 1024
#define DATA_HDR_SIZE 12
#define DATA_PKT_SIZE 1036

#define INF UINT16_MAX
#define SUCCESS 0

#define CMD_AUTHOR 0
#define CMD_INIT 1
#define CMD_ROUTING_TABLE 2
#define CMD_UPDATE 3
#define CMD_CRASH 4
#define CMD_SEND_FILE 5
#define CMD_SEND_FILE_STATS 6
#define CMD_LAST_DATA_PKT 7
#define CMD_PENULTIMATE_DATA_PKT 8

typedef enum bool
{
	FALSE, TRUE
} boolean;

typedef struct control_message
{
	uint8_t router_ip[4];
	uint8_t control_code;
	uint8_t response_time;
	uint16_t payload_size;
	
	char* payload;
} CONTROL_MSG;

typedef struct router_details
{
	uint16_t id;
	uint16_t router_port;
	uint16_t data_port;
	
	// Neighbor related details
	uint16_t link_cost;
	boolean neighbor; // if neighbor is TRUE and link_cost is INF, then the neighbor is down
	int timer_fd; // Timer fd corresponding to the neighbor

	uint8_t ip[4];
	char ip_address[IPADDR_LEN]; // string representation of the IP address
	
	int data_fd;
	
} ROUTER;

typedef struct topology
{
	uint16_t routers_count; // set in handle_INIT()
	uint16_t refresh_interval; // set in handle_INIT()
	
	ROUTER** routers; // Sorted array with size equal to the routers count
} TOPOLOGY;

typedef struct seq_num_list
{
	uint16_t seq_num;
	struct seq_num_list* next;
} SEQ_NUM_NODE, SEQ_NUM_LIST;

typedef struct transfers_list
{
	uint8_t transfer_id;
	char* file_name; // Applicable only at the source router
	uint8_t ttl; // Value after decrementing TTL from previous hop in case of intermediate and destination routers
	uint16_t initial_seq_num; // Applicable only at the source router

	uint8_t dest_ip[4];
	char dest_ip_str[IPADDR_LEN]; // string representation of the IP address
	
	SEQ_NUM_LIST* seq_num_list;
	int seq_num_count;
	SEQ_NUM_NODE* last_seq_num; // To avoid traversing the list again. Applicable only at the intermediate and destination routers.
	
	struct transfers_list* next;
	
	char* file_buffer; // Applicable only at the destination router. Free the memory once the file is written
	int file_buf_offset; // Applicable only at the destination router.
	
} TRANSFER_NODE, TRANSFERS_LIST;

typedef struct controller_conn_list
{
	int socket_fd;
	struct controller_conn_list* next;
} CONTROL_CONN_NODE, CONTROL_CONN_LIST;

typedef struct application_context
{
	uint16_t control_port; // set in main()
	int control_fd; // set in main()
	
	uint16_t router_id; // set in handle_INIT()
	uint16_t router_idx; // set in handle_INIT() --> Index of the router in the distance vector
	
	uint16_t router_port; // set in handle_INIT()
	int router_fd; // set in handle_INIT()
	
	uint16_t data_port; // set in handle_INIT()
	int data_fd; // set in handle_INIT()
	
	uint8_t router_ip[4]; // set in populate_ip_address()
	char ip_address[IPADDR_LEN]; // set in populate_ip_address()
	
	CONTROL_CONN_LIST* control_conn_list; // TODO:
	
	fd_set master; // master file descriptor list
	int fdmax; // set in add_to_select()
	
	TOPOLOGY* topology; // set in handle_INIT()
	
	uint16_t** distance_vectors; // Matrix of dimension routers_count x routers_count
		
	uint16_t* forwarding_table; // Array with size equal to the routers count
	
	int timer_fd; // My timer fd for sending periodic updates
	
	// Data transfer
	TRANSFERS_LIST* transfer_list; // For easy access to the latest TRANSFER, maintain the list in a reverse manner.
	char* last_data_packet;
	char* penultimate_data_packet;
} CONTEXT;

typedef struct data_packet
{
	uint8_t dest_ip[4];
	char dest_ip_str[IPADDR_LEN];
	
	uint8_t transfer_id;
	uint8_t ttl;
	uint16_t seq_num;
	uint8_t fin; // boolean indicating last packet
	
	char payload[FILE_SEG_SIZE];
} DATA_PKT;

#endif /* INCLUDE_GLOBAL_H_ */
