/*
 * utils.h
 *
 *  Created on: Feb 4, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_UTILS_H_
#define INCLUDE_UTILS_H_

#include "../include/global.h"

int get_word_count(char*);
char** split(char*);

int bind_port(struct application_context*);
void serialize(struct packet*, char*);
CLIENTS* get_client_info(CLIENTS*, int);
int validate_ip(char*);
int get_clients_size(CLIENTS*);
int is_valid_client(char*, CLIENTS*);
struct stats_list* get_stats(char*, int, struct stats_list*);
CLIENTS* new_client(char*, char*, int);
struct packet* construct_packet(char*, char*, int, int, char*, char*);
void serialize_send(struct packet*, int);
struct stats_list* new_stats(char*, char*, int);
void construct_packet_from_stream(int, int*, struct packet*);
CLIENTS* get_client(CLIENTS*, char*, int);
void update_fdmax(int, struct application_context*);
void free_clients(CLIENTS*);
struct blocked_clients* get_blocked_clients(struct blocked_clients*, char*);
int is_blocked(char*, char*, struct blocked_clients*);
void _close_socket(int, struct application_context*);
void add_msg(struct messages_map*, char*, char*, char*, int);
void increment_msgs_sent(char*, int, struct stats_list*, int);
void increment_msgs_recvd(char*, int, struct stats_list*, int);
int is_online(SERVER_INFO *);
int is_offline(SERVER_INFO *);
int receive_size(int, int*);
void send_size(int, int);
int sendall(int, char*, int*);
void receive_all(int, char*, int, int*);

#endif /* INCLUDE_UTILS_H_ */
