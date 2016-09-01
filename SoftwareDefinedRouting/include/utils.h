/*
 * utils.h
 *
 *  Created on: Apr 7, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_UTILS_H_
#define INCLUDE_UTILS_H_

#include "global.h"

int bind_port(int, boolean);
void add_to_select(int, CONTEXT*);
int _accept(int, CONTEXT*);
void receive_all(int, char*, int, int*);
void _close_fd(int, CONTEXT*);
uint16_t unpack_u16(char*);
void unpack_ip(char*, uint8_t[4]);
void get_ip_from_string(char*, uint8_t[4]);
void get_peer_ip(int, uint8_t[4]);
void print_ip(uint8_t[4]);
void packi16(char*, uint16_t);
void pack_ip(char*, uint8_t[4]);
int sendall(int, char*, int*);
void send_over_UDP(char*, int, char*, int);
void send_routing_updates(CONTEXT*);
int create_and_start_timer(uint16_t);
ROUTER* get_router_for_id(uint16_t, CONTEXT*);
ROUTER* get_router_for_ip(CONTEXT*, char*);
int get_idx(CONTEXT*, ROUTER*);
boolean compute_distant_vectors(CONTEXT*);
void print_forwarding_table(CONTEXT*);
void print_DV_matrix(CONTEXT*);
void convert_ip_to_string(char*, uint8_t[4]);
ROUTER* get_next_hop(CONTEXT*, char*);
int create_fd_if_not_exists(ROUTER*, CONTEXT*);
void add_to_transfer_list(TRANSFER_NODE*, TRANSFERS_LIST**);
TRANSFER_NODE* get_transfer(uint8_t, CONTEXT*);
void save_last_and_penultimate_data_pkt(char*, CONTEXT*);
void add_to_control_conn_list(int, CONTEXT*);
boolean is_control_connection(int, CONTEXT*);
void remove_from_control_conn_list(int, CONTEXT*);

#endif /* INCLUDE_UTILS_H_ */
