/*
 * data_plane.h
 *
 *  Created on: Apr 13, 2016
 *      Author: prasanth
 */

#ifndef INCLUDE_DATA_PLANE_H_
#define INCLUDE_DATA_PLANE_H_

#include "global.h"

void extract_data_pkt(char*, DATA_PKT*);
void handle_data_pkt(DATA_PKT*, CONTEXT*, char*);

#endif /* INCLUDE_DATA_PLANE_H_ */
