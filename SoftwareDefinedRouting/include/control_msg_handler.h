/*
 * control_msg_handler.h
 *
 *  Created on: Apr 8, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_CONTROL_MSG_HANDLER_H_
#define INCLUDE_CONTROL_MSG_HANDLER_H_

CONTROL_MSG* extract_control_msg(int, int*);
void handle_AUTHOR(CONTROL_MSG*, CONTEXT*, int);
void handle_INIT(CONTROL_MSG*, CONTEXT*, int);
void handle_ROUTING_TABLE(CONTROL_MSG*, CONTEXT*, int);
void handle_UPDATE(CONTROL_MSG*, CONTEXT*, int);
void handle_CRASH(CONTROL_MSG*, CONTEXT*, int);
void handle_SENDFILE(CONTROL_MSG*, CONTEXT*, int);
void handle_SENDFILE_STATS(CONTROL_MSG*, CONTEXT*, int);
void handle_LAST_DATA_PKT(CONTROL_MSG*, CONTEXT*, int);
void handle_PENULTIMATE_DATA_PKT(CONTROL_MSG*, CONTEXT*, int);

#endif /* INCLUDE_CONTROL_MSG_HANDLER_H_ */
