/*
 * commands.h
 *
 *  Created on: Feb 5, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_COMMANDS_H_
#define INCLUDE_COMMANDS_H_

int get_command(char*, int, struct application_context*);
void _author();
void _ip(struct application_context *, SERVER_INFO*);
void _port(struct application_context *, SERVER_INFO*);
void _list(CLIENTS*, struct application_context*, SERVER_INFO*);
void _statistics(struct stats_list *);
void _login(char**, int, SERVER_INFO*, struct application_context*);
void _refresh(SERVER_INFO*, struct application_context*);
void _block(char**, int, struct application_context*, CLIENTS*, SERVER_INFO*);
void _unblock(char**, int, struct application_context*, CLIENTS*, SERVER_INFO*);
void _blocked(char**, int, struct blocked_clients*);
void _send(char**, int, struct application_context*, SERVER_INFO*, CLIENTS*, char*);
void _broadcast(char**, int, struct application_context*, SERVER_INFO*, char*);
void _send_file(char**, int, struct application_context*, SERVER_INFO*, CLIENTS*);

#endif /* INCLUDE_COMMANDS_H_ */
