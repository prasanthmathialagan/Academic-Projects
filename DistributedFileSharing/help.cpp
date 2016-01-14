/*
 * help.cpp
 *
 *  Created on: Jan 14, 2016
 *      Author: prasanth
 */

#include "help.h"
#include <iostream>
#include <string.h>

using namespace std;

void display_commands()
{
	cout << _HELP << ". " << _STR_HELP << '\n'
		 << _CREATOR << ". " << _STR_CREATOR << '\n'
		 << _DISPLAY << ". " << _STR_DISPLAY << '\n'
		 << _REGISTER << ". " << _STR_REGISTER << '\n'
		 << _CONNECT << ". " << _STR_CONNECT << '\n'
		 << _LIST << ". " << _STR_LIST << '\n'
		 << _TERMINATE << ". " << _STR_TERMINATE << '\n'
		 << _QUIT << ". " << _STR_QUIT << '\n'
		 << _GET << ". " << _STR_GET << '\n'
		 << _PUT << ". " << _STR_PUT << '\n'
		 << _SYNC << ". " << _STR_SYNC << '\n';
}

bool is_command_of_type(char* command, char* type)
{
	int res = strcmp(command, type);
	return res == 0;
}

int get_command_id(char* command)
{
	if(is_command_of_type(command, _STR_HELP))
		return _HELP;
	
	if(is_command_of_type(command, _STR_CREATOR))
		return _CREATOR;
		
	if(is_command_of_type(command, _STR_DISPLAY))
		return _DISPLAY;
		
	if(is_command_of_type(command, _STR_REGISTER))
		return _REGISTER;
		
	if(is_command_of_type(command, _STR_CONNECT))
		return _CONNECT;
	
	if(is_command_of_type(command, _STR_LIST))
		return _LIST;
	
	if(is_command_of_type(command, _STR_TERMINATE))
		return _TERMINATE;
	
	if(is_command_of_type(command, _STR_QUIT))
		return _QUIT;
		
	if(is_command_of_type(command, _STR_GET))
		return _GET;
		
	if(is_command_of_type(command, _STR_PUT))
		return _PUT;
	
	if(is_command_of_type(command, _STR_SYNC))
		return _SYNC;
	
	return -1;
}

