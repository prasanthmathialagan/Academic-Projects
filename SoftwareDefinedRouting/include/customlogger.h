/*
 * customlogger.h
 *
 *  Created on: Feb 25, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_CUSTOMLOGGER_H_
#define INCLUDE_CUSTOMLOGGER_H_

#define TRACE 1
#define DEBUG 2
#define INFO 3
#define WARN 4
#define ERROR 5
#define FATAL 6

void set_log_level(int);
int get_log_level();
void _trace(const char*, ...);
void _debug(const char*, ...);
void _info(const char*, ...);
void _warn(const char*, ...);
void _error(const char*, ...);
void _fatal(const char*, ...);

#endif /* INCLUDE_CUSTOMLOGGER_H_ */
