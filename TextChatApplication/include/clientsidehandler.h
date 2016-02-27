/*
 * clientsidehandler.h
 *
 *  Created on: Feb 18, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_CLIENTSIDEHANDLER_H_
#define INCLUDE_CLIENTSIDEHANDLER_H_

void handle_login_response(struct packet*, CLIENTS**, int);
void handle_refresh_response(struct packet*, CLIENTS**);
void handle_block_response(struct packet*);
void handle_unblock_response(struct packet*);
void handle_received_msg(struct packet*);
void handle_file_recv(struct packet*, int);

#endif /* INCLUDE_CLIENTSIDEHANDLER_H_ */
