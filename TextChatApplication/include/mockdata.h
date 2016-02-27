/*
 * mockdata.h
 *
 *  Created on: Feb 5, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_MOCKDATA_H_
#define INCLUDE_MOCKDATA_H_

#include "../include/global.h"

CLIENTS* mock_clients();
struct stats_list* mock_stats();
struct blocked_clients* mock_blocked();

#endif /* INCLUDE_MOCKDATA_H_ */
