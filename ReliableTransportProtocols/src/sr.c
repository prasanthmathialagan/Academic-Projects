#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../include/simulator.h"

#define A_LOG_HEADER "A__"
#define B_LOG_HEADER "__B"

#define TIMER_DELAY 25.0f

#define BUFFER_SIZE 1010

/* ******************************************************************
 ALTERNATING BIT AND GO-BACK-N NETWORK EMULATOR: VERSION 1.1  J.F.Kurose

   This code should be used for PA2, unidirectional data transfer 
   protocols (from A to B). Network properties:
   - one way network delay averages five time units (longer if there
     are other messages in the channel for GBN), but can be larger
   - packets can be corrupted (either the header or the data portion)
     or lost, according to user-defined probabilities
   - packets will be delivered in the order in which they were sent
     (although some can be lost).
**********************************************************************/

/********* STUDENTS WRITE THE NEXT SEVEN ROUTINES *********/

typedef struct timer
{
	int pkt_id;
	float start_time;
	float end_time;
} TIMER;

typedef struct timer_node
{
	TIMER timer;
	struct timer_node* next;
} TIMER_LIST, TIMER_NODE;

typedef struct a_data
{
	int send_base; // head
	int next_seq_no; // tail
	
	struct pkt *buffer[BUFFER_SIZE];
	
	TIMER_LIST *timers;
	TIMER current_timer;
} A;

typedef struct b_data
{
	int rcv_base; // head
	struct pkt *buffer[BUFFER_SIZE];
} B;

A *a = NULL;
B *b = NULL;

int compute_checksum(struct pkt *packet)
{
	int chk_sum = 0;
	
	chk_sum += packet->seqnum;
	chk_sum += packet->acknum;
	
	int i;
	for (i = 0; i < 20; i++) 
	{
		chk_sum += (int) packet->payload[i];
	}
	
	return chk_sum;
}

void print_payload(char* payload)
{
	int i = 0;
	for (i = 0; i < 20; i++)
		printf("%c", payload[i]);
}

void print_packet(char* header, struct pkt *packet)
{
	printf("[%s] [%f] Packet: Seq=%d, Ack=%d, Checksum=%d, Payload=", header, get_sim_time(), packet->seqnum, packet->acknum,
			packet->checksum);
	print_payload(packet->payload);
	printf("\n");
}

int buffer_size()
{
	int buf_size = (BUFFER_SIZE - a->send_base + a->next_seq_no) % BUFFER_SIZE;
	return buf_size;
}

void _schedule(TIMER_NODE* timer_node)
{
	// if there are no timers running
	if (a->timers == NULL)
	{
		a->current_timer.start_time = timer_node->timer.start_time;
		a->current_timer.end_time = timer_node->timer.end_time;
		a->current_timer.pkt_id = timer_node->timer.pkt_id;
		
		printf("[%s] [%f] There are no timers running. So starting a new timer.\n", A_LOG_HEADER, get_sim_time());
		
		a->timers = timer_node;
		
		starttimer(0, TIMER_DELAY);
	}
	else // timers are running
	{
		printf("[%s] [%f] Adding the schedule to the end of the timers list.\n", A_LOG_HEADER, get_sim_time());
		
		// add to the end of the list because all the timers are of fixed duration TIMER_DELAY
		TIMER_NODE* curr = a->timers;
		while (curr->next)
			curr = curr->next;
		curr->next = timer_node;
	}
}

void _schedule_timer(struct pkt *pkt)
{
	TIMER_NODE *timer_node = malloc(sizeof(TIMER_NODE));
	timer_node->timer.start_time = get_sim_time();
	timer_node->timer.end_time = timer_node->timer.start_time + TIMER_DELAY;
	timer_node->timer.pkt_id = pkt->seqnum;
	timer_node->next = NULL;
	
	printf("[%s] [%f] Scheduling a timer for the packet %d from %f to %f.\n", A_LOG_HEADER, get_sim_time(), timer_node->timer.pkt_id, 
			timer_node->timer.start_time, timer_node->timer.end_time);
	
	_schedule(timer_node);
}

void _reschedule_next()
{
	TIMER_NODE* next = a->timers;
	if (next)
	{
		float curr_time = get_sim_time();
		float delay = next->timer.end_time - curr_time;
		
		a->current_timer.start_time = curr_time;
		a->current_timer.end_time = next->timer.end_time;
		a->current_timer.pkt_id = next->timer.pkt_id;
		
		printf("[%s] [%f] Re-scheduling the timer for the packet %d from %f to %f with new delay %f.\n",
				A_LOG_HEADER, get_sim_time(), next->timer.pkt_id, curr_time, next->timer.end_time, delay);
		
		starttimer(0, delay);
	}
	else
	{
		printf("[%s] [%f] There are no more events to schedule.\n", A_LOG_HEADER, get_sim_time());
	}
}

void _stop_and_reschedule(struct pkt *ack_pkt)
{
	printf("[%s] [%f] Removing the schedule for the packet %d.\n", A_LOG_HEADER, get_sim_time(), ack_pkt->acknum);

	printf("[%s] [%f] Timer currently running for the packet %d.\n", A_LOG_HEADER, get_sim_time(), a->current_timer.pkt_id);

	// If it is the currently running timer, stop it
	if(a->current_timer.pkt_id == ack_pkt->acknum)
	{
		printf("[%s] [%f] Stopping the current timer since it is for the same packet.\n", A_LOG_HEADER, get_sim_time());
		
		// Stop the timer
		stoptimer(0);
		
		// Remove it from the timers list
		TIMER_NODE *curr = a->timers;
		a->timers = a->timers->next;
		free(curr);
		
		// Reschedule the next timer
		_reschedule_next();
	}
	else
	{
		// Remove the schedule for the packet
		TIMER_NODE *prev = NULL;
		TIMER_NODE *curr = a->timers;
		while(curr)
		{
			if(curr->timer.pkt_id == ack_pkt->acknum)
				break;
		
			prev = curr;
			curr = curr->next;
		}
		
		prev->next = curr->next;
		
		curr->next = NULL;
		free(curr);

		printf("[%s] [%f] Removed the non-active schedule for the packet %d.\n", A_LOG_HEADER, get_sim_time(), ack_pkt->acknum);
	}
}

/* called from layer 5, passed the data to be sent to other side */
void A_output(message)
  struct msg message;
{
	printf("[%s] [%f] Message to send = ", A_LOG_HEADER, get_sim_time());
	print_payload(message.data);
	printf("\n");
	
	int buf_size = buffer_size();
	
	// If the buffer is full
	if(buf_size == BUFFER_SIZE - 1) // In order to ensure that head does not become equal to tail, we don't allow N elements. Only N-1 elements.
	{
		printf("[%s] [%f] Buffer has reached its full capacity %d. Hence quitting the application.", A_LOG_HEADER, get_sim_time(), BUFFER_SIZE);
		exit(1);
	}
	
	printf("[%s] [%f] Current buffer size = %d.\n", A_LOG_HEADER, get_sim_time(), buf_size);
	
	struct pkt *send_pkt = malloc(sizeof(struct pkt));
	send_pkt->seqnum = a->next_seq_no;
	send_pkt->acknum = -1;
		
	int i;
	for(i = 0; i < 20; i++)
	{
		send_pkt->payload[i] = message.data[i];
	}

	send_pkt->checksum = compute_checksum(send_pkt);
	
	print_packet(A_LOG_HEADER, send_pkt);
	a->buffer[a->next_seq_no] = send_pkt;

	// If the message falls within the window
	if(buf_size < getwinsize())
	{
		tolayer3(0, *send_pkt);
		printf("[%s] [%f] Packet sent to layer3.\n", A_LOG_HEADER, get_sim_time());
		_schedule_timer(send_pkt);
	}
	else
	{
		printf("[%s] [%f] Packet is buffered since the window has reached its full size.\n", A_LOG_HEADER, get_sim_time());
	}

	a->next_seq_no = (a->next_seq_no + 1) % BUFFER_SIZE;
}

/* called from layer 3, when a packet arrives for layer 4 */
void A_input(packet)
  struct pkt packet;
{
	printf("[%s] [%f] Received ack = %d\n", A_LOG_HEADER, get_sim_time(), packet.acknum);
	print_packet(A_LOG_HEADER, &packet);
	
	int chk_sum = compute_checksum(&packet);
	printf("[%s] [%f] Computed checksum = %d\n", A_LOG_HEADER, get_sim_time(), chk_sum);

	if (chk_sum != packet.checksum)
	{
		printf("[%s] [%f] Dropping the packet %d because it is corrupted.\n", A_LOG_HEADER, get_sim_time(), packet.acknum);
		return;
	}
	
	if(a->buffer[packet.acknum] == NULL)
	{
		printf("[%s] [%f] Dropping the duplicate ack %d.\n", A_LOG_HEADER, get_sim_time(), packet.acknum);
		return;
	}
	
	free(a->buffer[packet.acknum]);
	a->buffer[packet.acknum] = NULL;
	
	// Stop the timer for the packet and reschedule the timer
	_stop_and_reschedule(&packet);

	// If the ack corresponds to the packet at the send_base, move the window, send new packets(if available) and reschedule the timer
	if (a->send_base == packet.acknum)
	{
		// Move the send_base to the next unacknowledged sequence
		do
		{
			int old_base = a->send_base;
			a->send_base = (a->send_base + 1) % BUFFER_SIZE;
			
			// If there is any buffered message, send it
			if (buffer_size() >= getwinsize())
			{
				int index = (old_base + getwinsize()) % BUFFER_SIZE; // Get the first message right after the old window
				struct pkt *send_pkt = a->buffer[index];
				printf("[%s] [%f] Message pending to be sent in the buffer at index = %d and data = ", A_LOG_HEADER, get_sim_time(), index);
				
				print_payload(send_pkt->payload);
				printf("\n");
	
				tolayer3(0, *send_pkt);
				printf("[%s] [%f] Packet sent to layer3.\n", A_LOG_HEADER, get_sim_time());
				_schedule_timer(send_pkt);
			}
		} while(a->send_base != a->next_seq_no && !a->buffer[a->send_base]);
	}
}

/* called when A's timer goes off */
void A_timerinterrupt()
{
	int pkt_id = a->current_timer.pkt_id;
	
	printf("[%s] [%f] Timer expired for the packet %d!!\n", A_LOG_HEADER, get_sim_time(), pkt_id);

	TIMER_NODE *curr = a->timers;
	a->timers = a->timers->next;
	curr->next = NULL;
	
	// Reschedule the next timer
	_reschedule_next();
	
	printf("[%s] [%f] Retransmitting the packet %d.\n", A_LOG_HEADER, get_sim_time(), pkt_id);
	tolayer3(0, *(a->buffer[pkt_id]));
	printf("[%s] [%f] Packet sent to layer3.\n", A_LOG_HEADER, get_sim_time());
	
	// Schedule the timer for expired packet again
	curr->timer.start_time = get_sim_time();
	curr->timer.end_time = curr->timer.start_time + TIMER_DELAY;
	_schedule(curr);
}  

/* the following routine will be called once (only) before any other */
/* entity A routines are called. You can use it to do any initialization */
void A_init()
{
	printf("[%s] [%f] Initializing ....\n", A_LOG_HEADER, get_sim_time());

	a = malloc(sizeof(A));
	a->send_base = 0;
	a->next_seq_no = 0;

	int i = 0;
	for (i = 0; i < BUFFER_SIZE; i++)
	{
		a->buffer[i] = NULL;
	}
	
	a->timers = NULL;
	
	a->current_timer.pkt_id = -1;
	a->current_timer.start_time = -1;
	a->current_timer.end_time = -1;
	
	printf("[%s] [%f] Initialized successfully.\n", A_LOG_HEADER, get_sim_time());
}

/* Note that with simplex transfer from a-to-B, there is no B_output() */

/* called from layer 3, when a packet arrives for layer 4 at B*/
void B_input(packet)
  struct pkt packet;
{
	printf("[%s] [%f] Received packet = %d\n", B_LOG_HEADER, get_sim_time(), packet.seqnum);	
		
	print_packet(B_LOG_HEADER, &packet);
	
	int chk_sum = compute_checksum(&packet);
	printf("[%s] [%f] Computed checksum = %d\n", B_LOG_HEADER, get_sim_time(), chk_sum);

	if (chk_sum != packet.checksum)
	{
		printf("[%s] [%f] Dropping the packet %d because it is corrupted.\n", B_LOG_HEADER, get_sim_time(), packet.seqnum);
		return;
	}

	if (b->rcv_base == packet.seqnum)
	{
		tolayer5(1, packet.payload);
		printf("[%s] [%f] Message sent to layer5 = ", B_LOG_HEADER, get_sim_time());
		print_payload(packet.payload);
		printf("\n");
		
		// Check for buffered messages
		int i = 1;
		do
		{
			b->rcv_base = (b->rcv_base + 1) % BUFFER_SIZE;
			struct pkt *buf_pkt = b->buffer[b->rcv_base];
			if(!buf_pkt)
				break;
			
			tolayer5(1, buf_pkt->payload);
			printf("[%s] [%f] Buffered packet %d sent to layer5.\n", B_LOG_HEADER, get_sim_time(), b->rcv_base);
			
			free(buf_pkt);
			b->buffer[b->rcv_base] = NULL;

			i++;
		}while(i < getwinsize());
	}
	// If the message comes out of order, but within the window
	else if(((BUFFER_SIZE + packet.seqnum - b->rcv_base) % BUFFER_SIZE) < getwinsize())
	{
		if(b->buffer[packet.seqnum])
		{
			printf("[%s] [%f] Already the packet %d is received out of order %d and buffered it.\n", B_LOG_HEADER, get_sim_time(), 
					packet.seqnum, b->rcv_base);
		}
		else
		{
			printf("[%s] [%f] Packet %d received out of order %d. Hence buffering it.\n", B_LOG_HEADER, get_sim_time(), packet.seqnum, b->rcv_base);
					
			struct pkt *rcvd_pkt = malloc(sizeof(struct pkt));
			rcvd_pkt->seqnum = packet.seqnum;
			rcvd_pkt->acknum = packet.acknum;
			rcvd_pkt->checksum = packet.checksum;
			
			int i;
			for(i = 0; i < 20; i++)
			{
				rcvd_pkt->payload[i] = packet.payload[i];
			}
			
			b->buffer[packet.seqnum] = rcvd_pkt;
		}
	}
	// If the message comes out of order and to the left of the recv base and within the window
	else if(((BUFFER_SIZE + (b->rcv_base - 1) - (packet.seqnum)) % BUFFER_SIZE) <= getwinsize())
	{
		printf("[%s] [%f] Packet %d received out of order %d and to the left of recv base (within the window). Hence sending duplicate ack.\n", 
				B_LOG_HEADER, get_sim_time(), packet.seqnum, b->rcv_base);
	}
	else
	{
		printf("[%s] [%f] Packet %d received out of order %d and does not fit within any of the windows. Hence dropping it.\n", 
						B_LOG_HEADER, get_sim_time(), packet.seqnum, b->rcv_base);
		return;
	}
	
	// Send ack
	struct pkt ack_pkt;
	ack_pkt.seqnum = -1;
	ack_pkt.acknum = packet.seqnum;
	bzero(ack_pkt.payload, 20);
	ack_pkt.checksum = compute_checksum(&ack_pkt);

	print_packet(B_LOG_HEADER, &ack_pkt);
		
	tolayer3(1, ack_pkt);
	
	printf("[%s] [%f] Ack message sent to layer3.\n", B_LOG_HEADER, get_sim_time());
}

/* the following rouytine will be called once (only) before any other */
/* entity B routines are called. You can use it to do any initialization */
void B_init()
{
	printf("[%s] [%f] Initializing ....\n", B_LOG_HEADER, get_sim_time());

	b = malloc(sizeof(B));
	b->rcv_base = 0;
	int i = 0;
	for (i = 0; i < BUFFER_SIZE; i++)
	{
		b->buffer[i] = NULL;
	}
	
	printf("[%s] [%f] Initialized successfully.\n", B_LOG_HEADER, get_sim_time());
}
