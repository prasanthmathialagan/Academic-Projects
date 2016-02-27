/*
 * mockdata.c
 *
 *  Created on: Feb 5, 2016
 *      Author: prasanth
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "../include/global.h"
#include "../include/mockdata.h"

CLIENTS* mock_clients()
{
	CLIENTS* clients = malloc(sizeof(CLIENTS));
	strcpy(clients->h.host_name, "stones.cse.buffalo.edu");
	strcpy(clients->h.ip_address, "128.205.36.46");
	clients->h.port = 4545;
	
	CLIENTS* next = malloc(sizeof(CLIENTS));
	strcpy(next->h.host_name, "embankment.cse.buffalo.edu");
	strcpy(next->h.ip_address, "128.205.36.35");
	next->h.port = 5000;
	next->next = NULL;
	
	clients->next = next;
	
	return clients;
}

struct stats_list* mock_stats()
{
	struct stats_list* stats = malloc(sizeof(struct stats_list));
	strcpy(stats->s.h.host_name, "stones.cse.buffalo.edu");
	strcpy(stats->s.h.ip_address, "128.205.36.46");
	stats->s.msgs_sent = 4;
	stats->s.msgs_received = 0;
	stats->s.h.port = 4545;
	stats->s.status = 0;
	
	struct stats_list* next = malloc(sizeof(struct stats_list));
	strcpy(next->s.h.host_name, "embankment.cse.buffalo.edu");
	strcpy(next->s.h.ip_address, "128.205.36.35");
	next->s.msgs_sent = 3;
	next->s.msgs_received = 67;
	next->s.h.port = 5000;
	next->s.status = 0;
	
	stats->next = next;
	next->next = NULL;
	
	return stats;
}

struct blocked_clients* mock_blocked()
{
	struct blocked_clients* blokd_clients = malloc(sizeof(struct blocked_clients));
	strcpy(blokd_clients->host_ip, "128.205.36.46");
	
	CLIENTS* c = malloc(sizeof(CLIENTS));
	strcpy(c->h.host_name, "embankment.cse.buffalo.edu");
	strcpy(c->h.ip_address, "128.205.36.35");
	c->h.port = 5000;
	CLIENTS* next = malloc(sizeof(CLIENTS));
	strcpy(next->h.host_name, "highgate.cse.buffalo.edu");
	strcpy(next->h.ip_address, "128.205.36.33");
	next->h.port = 5499;
	
	blokd_clients->clients = c;
	c->next = next;
	next->next = NULL;
	
	struct blocked_clients *blokd_clients_next = malloc(sizeof(struct blocked_clients));
	strcpy(blokd_clients_next->host_ip, "128.205.36.34");
	
	CLIENTS* d = malloc(sizeof(CLIENTS));
	strcpy(d->h.host_name, "embankment.cse.buffalo.edu");
	strcpy(d->h.ip_address, "128.205.36.35");
	d->h.port = 5000;
	CLIENTS* nextd = malloc(sizeof(CLIENTS));
	strcpy(nextd->h.host_name, "highgate.cse.buffalo.edu");
	strcpy(nextd->h.ip_address, "128.205.36.33");
	nextd->h.port = 5499;
	
	blokd_clients_next->clients = d;
	d->next = nextd;
	nextd->next = NULL;
	
	blokd_clients->next = blokd_clients_next;
	blokd_clients_next->next = NULL;
	
	return blokd_clients;
}

