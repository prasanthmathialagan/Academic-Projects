/*
 * data_plane.c
 *
 *  Created on: Apr 13, 2016
 *      Author: prasanth
 */

#include <string.h>
#include <stdlib.h>
#include <stdio.h>

#include "../include/data_plane.h"
#include "../include/global.h"
#include "../include/utils.h"
#include "../include/customlogger.h"

void extract_data_pkt(char* buf, DATA_PKT* data_pkt)
{
	unpack_ip(buf, data_pkt->dest_ip);
	data_pkt->transfer_id = (uint8_t) buf[4];
	data_pkt->ttl = (uint8_t) buf[5];
	data_pkt->seq_num = unpack_u16(buf + 6);
	data_pkt->fin = ((uint8_t) buf[8]) >> 7;
	memcpy(data_pkt->payload, buf + DATA_HDR_SIZE, FILE_SEG_SIZE);
	
	convert_ip_to_string(data_pkt->dest_ip_str, data_pkt->dest_ip);
	_trace("Data packet: Destination IP = %s, Transfer ID = %d, TTL = %d, Sequence number = %d, FIN = %d.\n", 
			data_pkt->dest_ip_str, data_pkt->transfer_id, data_pkt->ttl, data_pkt->seq_num, data_pkt->fin);
}

void handle_data_pkt(DATA_PKT* data_pkt, CONTEXT* context, char* data_pkt_buffer)
{
	// Decrement the TTL
	uint8_t new_ttl = data_pkt->ttl - 1;
	
	// If TTL is 0, drop the packet
	if(new_ttl == 0)
	{
		_info("Dropping the packet[%d:%d] as the TTL is 0.\n", data_pkt->transfer_id, data_pkt->seq_num);
		return;
	}
	
	ROUTER* next_hop = get_next_hop(context, data_pkt->dest_ip_str);
	if(next_hop == NULL)
	{
		_info("Dropping the packet[%d:%d] as there is no next hop.\n", data_pkt->transfer_id, data_pkt->seq_num);
		return;
	}
	
	// Update the new TTL
	data_pkt_buffer[5] = (uint8_t) new_ttl;
	
	// Get the transfer related to the transfer id
	TRANSFER_NODE* transfer = get_transfer(data_pkt->transfer_id, context);
	if(transfer == NULL)
	{
		transfer = malloc(sizeof(TRANSFER_NODE));
		transfer->transfer_id = data_pkt->transfer_id;
		transfer->file_name = NULL;
		transfer->file_buffer = NULL;
		transfer->file_buf_offset = 0;
		transfer->ttl = new_ttl;
		transfer->initial_seq_num = -1;
		
		int i;
		for(i = 0; i < 4; i++)
			transfer->dest_ip[i] = data_pkt->dest_ip[i];

		strcpy(transfer->dest_ip_str, data_pkt->dest_ip_str);
		
		transfer->seq_num_list = NULL;
		transfer->seq_num_count = 0;
		transfer->last_seq_num = NULL;
		transfer->next = NULL;
		
		add_to_transfer_list(transfer, &context->transfer_list);
	}
	
	SEQ_NUM_NODE* seq_num = malloc(sizeof(SEQ_NUM_NODE));
	seq_num->next = NULL;
	seq_num->seq_num = data_pkt->seq_num;
	
	if(transfer->last_seq_num == NULL)
		transfer->seq_num_list = seq_num;
	else
		transfer->last_seq_num->next = seq_num;
	
	transfer->seq_num_count += 1;
	transfer->last_seq_num = seq_num;
	
	// If the next hop is you, save the packet
	if(!strcmp(context->ip_address, next_hop->ip_address))
	{
		if(transfer->file_buffer == NULL)
		{
			transfer->file_buffer = malloc(10*1024*1024); // 10 MB
			transfer->file_buf_offset = 0;
		}

		memcpy(transfer->file_buffer + transfer->file_buf_offset, data_pkt->payload, FILE_SEG_SIZE);
		transfer->file_buf_offset += FILE_SEG_SIZE;
		_debug("Saving the packet[%d:%d] to the buffer.\n", transfer->transfer_id, data_pkt->seq_num);
		
		if(data_pkt->fin == 1)
		{
			// Save the file
			char file_name[20];
			sprintf(file_name, "file-%d", transfer->transfer_id);

			_info("Saving the data in the buffer for the transfer %d to the file %s.\n", transfer->transfer_id, file_name);
			
			FILE *write_ptr;
			write_ptr = fopen(file_name, "wb");  // b for binary, w for writing
			fwrite(transfer->file_buffer, transfer->file_buf_offset, 1, write_ptr);
			fclose(write_ptr);
			
			free(transfer->file_buffer);
			transfer->file_buffer = NULL;
		}
	}
	else // Forward to the next hop
	{
		// Establish connection to the next hop router if not exists
		if(create_fd_if_not_exists(next_hop, context) < 0)
			return;
		
		int len = DATA_PKT_SIZE;
		sendall(next_hop->data_fd, data_pkt_buffer, &len);
	}
	
	save_last_and_penultimate_data_pkt(data_pkt_buffer, context);
}
