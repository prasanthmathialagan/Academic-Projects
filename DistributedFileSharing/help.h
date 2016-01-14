/*
 * help.h
 *
 *  Created on: Jan 14, 2016
 *      Author: prasanth
 */

#ifndef HELP_H_
#define HELP_H_

#define _HELP 1
#define _CREATOR 2
#define _DISPLAY 3
#define _REGISTER 4
#define _CONNECT 5
#define _LIST 6
#define _TERMINATE 7
#define _QUIT 8
#define _GET 9
#define _PUT 10
#define _SYNC 11

#define _STR_HELP "HELP"
#define _STR_CREATOR "CREATOR"
#define _STR_DISPLAY "DISPLAY"
#define _STR_REGISTER "REGISTER"
#define _STR_CONNECT "CONNECT"
#define _STR_LIST "LIST"
#define _STR_TERMINATE "TERMINATE"
#define _STR_QUIT "QUIT"
#define _STR_GET "GET"
#define _STR_PUT "PUT"
#define _STR_SYNC "SYNC"

void display_commands();
int get_command_id(char*);

#endif /* HELP_H_ */
