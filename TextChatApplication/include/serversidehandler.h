/*
 * serversidehandler.h
 *
 *  Created on: Feb 18, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_SERVERSIDEHANDLER_H_
#define INCLUDE_SERVERSIDEHANDLER_H_

void send_clients_list_and_msgs(int, struct packet*, char*, struct application_context*, CLIENTS*, struct messages*, struct stats_list*);
void handle_login_request(struct packet*, int, CLIENTS**, struct stats_list**, struct blocked_clients**, 
		struct messages_map**, struct application_context*);
void handle_block_request(struct packet*, struct stats_list*, struct blocked_clients*, int);
void handle_unblock_request(struct packet*, struct blocked_clients*, int);
void handle_logout_request(char*, int, int, CLIENTS** , struct stats_list*, struct application_context*);
void handle_send_request(struct packet*, struct blocked_clients*, CLIENTS*, struct messages_map*, struct stats_list*);
void handle_broadcast_request(struct packet*, struct blocked_clients*, CLIENTS*, struct stats_list*, struct messages_map*);
void handle_exit_request(char*, int, int, CLIENTS**, struct stats_list**, struct blocked_clients**, struct application_context*, struct messages_map*);

#endif /* INCLUDE_SERVERSIDEHANDLER_H_ */
