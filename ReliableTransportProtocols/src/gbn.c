#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../include/simulator.h"

#define A_LOG_HEADER "A__"
#define B_LOG_HEADER "__B"

#define TIMER_DELAY 25.0f

#define BUFFER_SIZE 1000

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

typedef struct a_data
{
	int base; // head
	int next_seq_no; // tail
	
	struct pkt *buffer[BUFFER_SIZE];
} A;

typedef struct b_data
{
	int expect_seq_no;
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
	int buf_size = (BUFFER_SIZE - a->base + a->next_seq_no) % BUFFER_SIZE;
	return buf_size;
}

// Used the pseudo code from Computer Networking - A Top Down Approach 6th edition # Go-Back-N
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
		
		if(a->base == a->next_seq_no)
		{
			starttimer(0, TIMER_DELAY);
			printf("[%s] [%f] Timer started.\n", A_LOG_HEADER, get_sim_time());
		}
	}
	else
	{
		printf("[%s] [%f] Packet is buffered since the window has reached its full size.\n", A_LOG_HEADER, get_sim_time());
	}

	a->next_seq_no = (a->next_seq_no + 1) % BUFFER_SIZE;
}

void restart_timer()
{
	stoptimer(0);
	starttimer(0, TIMER_DELAY);
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
	
	int old_base = a->base;
	a->base = (packet.acknum + 1) % BUFFER_SIZE;
	
	if(a->base == a->next_seq_no)
	{
		printf("[%s] [%f] Base and next seq number aligned at %d. Hence stopping the timer\n", A_LOG_HEADER, get_sim_time(), a->base);
		stoptimer(0);
	}
	else
	{
		// If there is any buffered message, send it
		if(buffer_size() >= getwinsize())
		{
			int index = (old_base + getwinsize()) % BUFFER_SIZE; // Get the first message right after the old window
			struct pkt *send_pkt = a->buffer[index]; 
			printf("[%s] [%f] Message pending to be sent in the buffer at index = %d and data = ", A_LOG_HEADER, get_sim_time(), index);
			print_payload(send_pkt->payload);
			printf("\n");
			
			tolayer3(0, *send_pkt);
			printf("[%s] [%f] Packet sent to layer3.\n", A_LOG_HEADER, get_sim_time());
		}
		
		restart_timer();
		printf("[%s] [%f] Timer restarted.\n", A_LOG_HEADER, get_sim_time());
	}
}

/* called when A's timer goes off */
void A_timerinterrupt()
{
	printf("[%s] [%f] Timer expired!!\n", A_LOG_HEADER, get_sim_time());
	
	int end = buffer_size() >= getwinsize() ? ((a->base + getwinsize()) % BUFFER_SIZE) : a->next_seq_no;
	int count = (BUFFER_SIZE + end - a->base) % BUFFER_SIZE;
	printf("[%s] [%f] Retransmitting %d packets from %d to %d.\n", A_LOG_HEADER, get_sim_time(), count, a->base, 
			(BUFFER_SIZE + end - 1) % BUFFER_SIZE);
	
	int i = 0;
	while(i < count)
	{
		int index = (a->base + i) % BUFFER_SIZE;
		tolayer3(0, *(a->buffer[index]));
		i++;
	}
	
	starttimer(0, TIMER_DELAY);
	printf("[%s] [%f] Timer started.\n", A_LOG_HEADER, get_sim_time());
}  

/* the following routine will be called once (only) before any other */
/* entity A routines are called. You can use it to do any initialization */
void A_init()
{
	printf("[%s] [%f] Initializing ....\n", A_LOG_HEADER, get_sim_time());

	a = malloc(sizeof(A));
	a->base = 0;
	a->next_seq_no = 0;

	int i = 0;
	for (i = 0; i < BUFFER_SIZE; i++)
	{
		a->buffer[i] = NULL;
	}
	
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
	
	if (packet.seqnum == b->expect_seq_no)
	{
		tolayer5(1, packet.payload);
		printf("[%s] [%f] Message sent to layer5 = ", B_LOG_HEADER, get_sim_time());
		print_payload(packet.payload);
		printf("\n");
		b->expect_seq_no = (b->expect_seq_no + 1) % BUFFER_SIZE;
	}
	else
	{
		printf("[%s] [%f] Message does not have the expected seq number %d. So, sending duplicated ack.\n",
				B_LOG_HEADER, get_sim_time(), b->expect_seq_no);
	}
	
	struct pkt ack_pkt;
	ack_pkt.seqnum = -1;
	ack_pkt.acknum = (BUFFER_SIZE + b->expect_seq_no - 1) % BUFFER_SIZE;
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
	b->expect_seq_no = 0;

	printf("[%s] [%f] Initialized successfully.\n", B_LOG_HEADER, get_sim_time());
}
