/*
 * customlogger.c
 *
 *  Created on: Feb 25, 2016
 *      Author: prasanth
 */

#include <stdarg.h>
#include <stdio.h>

#include "../include/customlogger.h"

int log_level = INFO;
void set_log_level(int level)
{
	if(level < TRACE || level > FATAL)
	{
		fprintf(stderr, "Unknown log level %d. Hence setting the level to default", level);
		log_level = INFO;
	}
	else
	{
		log_level = level;
	}
}

int get_log_level()
{
	return log_level;
}

void _trace(const char* format, ...)
{
	if(TRACE < log_level)
		return;
	
	printf("[TRACE]: ");
	
	va_list args;
	va_start(args, format);
	vprintf(format, args);
	va_end(args);
}

void _debug(const char* format, ...)
{
	if(DEBUG < log_level)
		return;
	
	printf("[DEBUG]: ");
		
	va_list args;
	va_start(args, format);
	vprintf(format, args);
	va_end(args);
}

void _info(const char* format, ...)
{
	if(INFO < log_level)
		return;
	
	printf("[INFO]: ");
	
	va_list args;
	va_start(args, format);
	vprintf(format, args);
	va_end(args);
}

void _warn(const char* format, ...)
{
	if(WARN < log_level)
		return;
	
	printf("[WARN]: ");
	
	va_list args;
	va_start(args, format);
	vprintf(format, args);
	va_end(args);
}

void _error(const char* format, ...)
{
	if (ERROR < log_level)
		return;

	printf("[ERROR]: ");
	
	va_list args;
	va_start(args, format);
	vprintf(format, args);
	va_end(args);
}

void _fatal(const char* format, ...)
{
	if (FATAL < log_level)
		return;

	printf("[FATAL]: ");
	
	va_list args;
	va_start(args, format);
	vprintf(format, args);
	va_end(args);
}
