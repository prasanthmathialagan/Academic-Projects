#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../include/simulator.h"

#define A_LOG_HEADER "A__"
#define B_LOG_HEADER "__B"

#define TIMER_DELAY 11.0f

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

/********* STUDENTS WRITE THE NEXT SIX ROUTINES *********/

typedef enum bool
{
	TRUE, FALSE
} boolean;

typedef struct a_data
{
	int next_seq_no;
	boolean waitforack;
	int expected_ack_no;
	struct pkt *pkt;
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
	int i;
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

/* called from layer 5, passed the data to be sent to other side */
void A_output(message)
  struct msg message;
{
	/*
	 * On receiving a message from the Layer5,
	 * 	- Check if waitforack flag is set to true. If true, drop the message and return
	 * 	- seq number should be either 0 or 1
	 * 	- ack number can be set to -1
	 * 	- compute checksum for seq num, ack num and payload
	 * 	- construct the packet and set it as global variable (this is for retransmission on timeout)
	 * 	- set waitforack = true and expected ack number 
	 * 	- call toLayer3()
	 * 	- start the timer
	 * 
	 * Special cases:
	 *  - 
	 */
		
	printf("[%s] [%f] Message to send = ", A_LOG_HEADER, get_sim_time());
	print_payload(message.data);
	printf("\n");

	if (a->waitforack == TRUE)
	{
		printf("[%s] [%f] Dropping the message because a message is already in transit.\n", A_LOG_HEADER, get_sim_time());
		return;
	}

	a->pkt = malloc(sizeof(struct pkt));
	a->pkt->seqnum = a->next_seq_no;
	a->pkt->acknum = -1;
	
	int i;
	for(i = 0; i < 20; i++)
	{
		a->pkt->payload[i] = message.data[i];
	}

	a->pkt->checksum = compute_checksum(a->pkt);

	a->waitforack = TRUE;
	a->expected_ack_no = a->pkt->seqnum;

	print_packet(A_LOG_HEADER, a->pkt);
	
	a->next_seq_no = a->next_seq_no ^ 1; // Alternate the bit
	
	struct pkt send_pkt = *(a->pkt);
	tolayer3(0, send_pkt);
	printf("[%s] [%f] Packet sent to layer3.\n", A_LOG_HEADER, get_sim_time());
	
	starttimer(0, TIMER_DELAY);
	printf("[%s] [%f] Timer started.\n", A_LOG_HEADER, get_sim_time());
}

/* called from layer 3, when a packet arrives for layer 4 */
void A_input(packet)
  struct pkt packet;
{
	/*
	 * On receiving ack message packet from Layer3
	 * 	- if not waiting for the ack, drop the packet and return
	 *  - compute checksum for seq num, ack num and payload
	 *  - check if the checksum matches with that in the packet. if not, drop the packet (retransmit on timeout) and return
	 *  - check if the ack corresponds to the expected ack number(0 or 1). If not, drop the packet and return (duplicate ack)
	 *  - stop the timer 
	 *  - set waitforack = false
	 *  - free memory for the packet stored for retransmission
	 */

	printf("[%s] [%f] Received ack = %d\n", A_LOG_HEADER, get_sim_time(), packet.acknum);
	print_packet(A_LOG_HEADER, &packet);

	if(a->waitforack == FALSE)
	{
		printf("[%s] [%f] Dropping the packet %d because there are no acks pending.\n", A_LOG_HEADER, get_sim_time(), packet.acknum);
		return;
	}
	
	int chk_sum = compute_checksum(&packet);
	printf("[%s] [%f] Computed checksum = %d\n", A_LOG_HEADER, get_sim_time(), chk_sum);
	
	if(chk_sum != packet.checksum)
	{
		printf("[%s] [%f] Dropping the packet %d because it is corrupted.\n", A_LOG_HEADER, get_sim_time(), packet.acknum);
		return;
	}
	
	if(a->expected_ack_no != packet.acknum)
	{
		printf("[%s] [%f] Dropping the packet %d because ack number is not as expected %d.\n", A_LOG_HEADER, get_sim_time(), packet.acknum, 
				a->expected_ack_no);
		return;
	}
	
	stoptimer(0);
	printf("[%s] [%f] Timer stopped\n", A_LOG_HEADER, get_sim_time());
	
	a->waitforack = FALSE;
	free(a->pkt);
	a->pkt = NULL;
	a->expected_ack_no = -1;
}

/* called when A's timer goes off */
void A_timerinterrupt()
{
	/*
	 * 	- call toLayer3()
	 * 	- start the timer
	 */
	
	printf("[%s] [%f] Timer expired!!\n", A_LOG_HEADER, get_sim_time());
	
	struct pkt send_pkt = *(a->pkt);
	printf("[%s] [%f] Retransmitting message ", A_LOG_HEADER, get_sim_time());
	print_payload(send_pkt.payload);
	printf("\n");
	
	tolayer3(0, send_pkt);

	starttimer(0, TIMER_DELAY);
	printf("[%s] [%f] Timer restarted.\n", A_LOG_HEADER, get_sim_time());
}  

/* the following routine will be called once (only) before any other */
/* entity A routines are called. You can use it to do any initialization */
void A_init()
{
	printf("[%s] [%f] Initializing ....\n", A_LOG_HEADER, get_sim_time());
	
	a = malloc(sizeof(A));
	a->next_seq_no = 0;
	a->waitforack = FALSE;
	a->expected_ack_no = -1;
	a->pkt = NULL;
	
	printf("[%s] [%f] Initialized successfully.\n", A_LOG_HEADER, get_sim_time());
}

/* Note that with simplex transfer from a-to-B, there is no B_output() */

/* called from layer 3, when a packet arrives for layer 4 at B*/
void B_input(packet)
  struct pkt packet;
{
	/*
	 * On receiving new message packet from Layer3
	 *  - compute checksum for seq num, ack num and payload
	 *  - check if the checksum matches with that in the packet. if not, drop the packet and return
	 *  - check if the seq number corresponds to the expected seq number(0 or 1).
	 *  - If so, extract data and call toLayer5() and alternate the expected seq num 
	 *  - send ack for the seq number present in the packet
	 */
		
	printf("[%s] [%f] Received packet = %d\n", B_LOG_HEADER, get_sim_time(), packet.seqnum);

	print_packet(B_LOG_HEADER, &packet);

	int chk_sum = compute_checksum(&packet);
	printf("[%s] [%f] Computed checksum = %d\n", B_LOG_HEADER, get_sim_time(), chk_sum);

	if (chk_sum != packet.checksum)
	{
		printf("[%s] [%f] Dropping the packet %d because it is corrupted.\n",
				B_LOG_HEADER, get_sim_time(), packet.seqnum);
		return;
	}

	if(packet.seqnum == b->expect_seq_no)
	{
		tolayer5(1, packet.payload);
		printf("[%s] [%f] Message sent to layer5 = ", B_LOG_HEADER, get_sim_time());
		print_payload(packet.payload);
		printf("\n");
		b->expect_seq_no = b->expect_seq_no ^ 1;
	}
	else
	{
		printf("[%s] [%f] Message does not have the expected seq number %d. So, sending duplicated ack.\n", B_LOG_HEADER, get_sim_time(), 
				b->expect_seq_no);
	}
	
	struct pkt ack_pkt;
	ack_pkt.seqnum = -1;
	ack_pkt.acknum = packet.seqnum;
	bzero(ack_pkt.payload, 20);
	ack_pkt.checksum = compute_checksum(&ack_pkt);

	print_packet(B_LOG_HEADER, &ack_pkt);
	
	tolayer3(1, ack_pkt);
	
	printf("[%s] [%f] Ack message sent to layer3.\n", B_LOG_HEADER, get_sim_time());
}

/* the following routine will be called once (only) before any other */
/* entity B routines are called. You can use it to do any initialization */
void B_init()
{
	printf("[%s] [%f] Initializing ....\n", B_LOG_HEADER, get_sim_time());
	
	b = malloc(sizeof(B));
	b->expect_seq_no = 0;

	printf("[%s] [%f] Initialized successfully.\n", B_LOG_HEADER, get_sim_time());
}
