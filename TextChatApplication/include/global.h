#ifndef GLOBAL_H_
#define GLOBAL_H_

#define AUTHOR "pmathial"

#define HOSTNAME_LEN 129
#define PATH_LEN 257
#define MESSAGE_LENGTH 257
#define IP_ADDRESS_LEN 16

#define BACKLOG 20
#define STDIN 0

#define COMMAND_LENGTH 30
#define INPUT_LENGTH 320
#define PACKET_DATA_LEN 1000
#define SERIAL_BUFFER_SIZE 1200

#define SEND_RECV_SIZE_LEN 20

#define CMD_AUTHOR "AUTHOR"
#define CMD_IP "IP"
#define CMD_PORT "PORT"
#define CMD_LIST "LIST"
#define CMD_STATISTICS "STATISTICS"
#define CMD_BLOCKED "BLOCKED"
#define CMD_LOGIN "LOGIN"
#define CMD_REFRESH "REFRESH"
#define CMD_SEND "SEND"
#define CMD_BROADCAST "BROADCAST"
#define CMD_BLOCK "BLOCK"
#define CMD_UNBLOCK "UNBLOCK"
#define CMD_LOGOUT "LOGOUT"
#define CMD_EXIT "EXIT"
#define CMD_SEND_FILE "SENDFILE"

#define CMD_ID_AUTHOR 100
#define CMD_ID_IP 101
#define CMD_ID_PORT 102
#define CMD_ID_LIST 103
#define CMD_ID_STATISTICS 104
#define CMD_ID_BLOCKED 105
#define CMD_ID_LOGIN 106
#define CMD_ID_REFRESH 107
#define CMD_ID_SEND 108
#define CMD_ID_BROADCAST 109
#define CMD_ID_BLOCK 110
#define CMD_ID_UNBLOCK 111
#define CMD_ID_LOGOUT 112
#define CMD_ID_EXIT 113
#define CMD_ID_SEND_FILE 114

#define RESPONSE_OK "OK"
#define RESPONSE_ERROR "ERROR"

#define _LOCALHOST "127.0.0.1"

struct host_info
{
	char host_name[HOSTNAME_LEN];
	char ip_address[IP_ADDRESS_LEN];
	int port;
};

struct stats
{
	struct host_info h;
	int msgs_sent;
	int msgs_received;
	int status;// 0 - offline, 1 - online
};

typedef struct host_list
{
	struct host_info h;
	int socket_fd;
	struct host_list *next;
} CLIENTS;

struct stats_list
{
	struct stats s;
	struct stats_list *next;
};

// List as Map
struct blocked_clients
{
	char host_ip[IP_ADDRESS_LEN];
	CLIENTS* clients;
	struct blocked_clients* next;
};

typedef struct server_info
{
	struct host_info h;
	int socket_fd;
	int online_with_server; // boolean indicating whether the client is connected to the server or not.
} SERVER_INFO;

struct application_context
{
	char host_name[HOSTNAME_LEN];
	char ip_address[IP_ADDRESS_LEN];
	int port;
	
	int server; // boolean. 1 for server. 0 for client
	
	int socket_fd;
	
	fd_set master; // master file descriptor list
	int fdmax;
};

struct packet
{
	char src_ip[IP_ADDRESS_LEN];
	char dest_ip[IP_ADDRESS_LEN];
	int src_port;
	int dest_port;
	char command[COMMAND_LENGTH];
	char data[PACKET_DATA_LEN];
};

struct messages
{
	char src_ip[IP_ADDRESS_LEN];
	char message[MESSAGE_LENGTH];
	int broadcast;
	struct messages* next;
};

struct messages_map
{
	char ip[IP_ADDRESS_LEN];
	struct messages* msgs;
	struct messages_map* next;
};

#endif
