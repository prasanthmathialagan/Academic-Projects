package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.util.Log;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.AgreedMessage;
import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.Message;
import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.NewMessage;
import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.PingMessage;
import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.ProposedMessage;

/**
 * Created by prasanth on 3/5/16.
 */
public class MessageHandler
{
    private static final String TAG = MessageHandler.class.getName();

    private final LinkedBlockingQueue<Message> msgQueue;
    private final int myId;

    private volatile int proposedSeqNo = -1;
    private volatile int agreedSeqNo = -1;

    private final PriorityQueue<ShortMsg> priorityQueue = new PriorityQueue<ShortMsg>();
    private final ContentResolver contentResolver;
    private final Uri uri;
    private final Set<Integer> clients;
    private volatile boolean pingInited = false;

    private final AtomicInteger globalSeqNum = new AtomicInteger(0);

    // For holding PROPOSED messages
    private final ConcurrentHashMap<Integer, Set<ProposedMessage>> msgsMap
            = new ConcurrentHashMap<Integer, Set<ProposedMessage>>();

    public MessageHandler(LinkedBlockingQueue<Message> msgQueue, ContentResolver contentResolver, Set<Integer> clients, int myId)
    {
        this.msgQueue = msgQueue;
        this.myId = myId;
        this.contentResolver = contentResolver;
        this.clients = clients;

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        uri = uriBuilder.build();
    }

    private int getNextProposedSeqNo()
    {
        proposedSeqNo = Math.max(proposedSeqNo, agreedSeqNo) + 1;
        return proposedSeqNo;
    }

    public synchronized Set<Integer> getActiveClients()
    {
        return new HashSet<Integer>(clients);
    }

    private int getActiveClientsCount()
    {
        return clients.size();
    }

    public synchronized void handleReceivedMsg(Message msg)
    {
        initPing();

        switch (msg.getType())
        {
            case NEW:
                handleNEW(msg);
                break;
            case PROPOSED:
                handlePROPOSED(msg);
                break;
            case AGREED:
                handleAGREED(msg);
                break;
            case PING:
                handlePING(msg);
                break;
            default:
                break;
        }
    }

    /**
     *
     * @param msg
     */
    private void handleNEW(Message msg)
    {
        NewMessage newMsg = (NewMessage) msg;
        Log.v(TAG, "[MSG-" + newMsg.getMsgOwnerId() + "-" + newMsg.getId() + "] [NEW-MESSAGE] " + newMsg.getMessage());

        if(!clients.contains(newMsg.getMsgOwnerId()))
        {
            Log.v(TAG, "[MSG-" + newMsg.getMsgOwnerId() + "-" + newMsg.getId() + "] [NEW-DROP] " + newMsg.getMsgOwnerId());
            return;
        }

        int proposedSeqNo = getNextProposedSeqNo();
        addNEW(newMsg, proposedSeqNo, myId);
        ProposedMessage propMsg = new ProposedMessage(newMsg, myId, proposedSeqNo);
        msgQueue.offer(propMsg);
    }

    private void addNEW(NewMessage msg, int proposedSeqNo, int proposerId)
    {
        ShortMsg sm = new ShortMsg(msg.getId(), msg.getMsgOwnerId(), proposedSeqNo, proposerId, msg.getMessage());
        priorityQueue.add(sm);
    }

    /**
     *
     * @param msg
     */
    private void handlePROPOSED(Message msg)
    {
        ProposedMessage propMsg = (ProposedMessage) msg;

        Log.v(TAG, "[MSG-" + propMsg.getMsgOwnerId() + "-" + propMsg.getId() + "] [PROP-MESSAGE] " + propMsg.getProposerId());

        if(!clients.contains(propMsg.getProposerId()))
        {
            Log.v(TAG, "[MSG-" + propMsg.getMsgOwnerId() + "-" + propMsg.getId() + "] [PROP-DROP] " + propMsg.getProposerId());
            return;
        }

        int msgId = propMsg.getId();
        Set<ProposedMessage> messages = msgsMap.get(msgId);
        if(messages == null)
        {
            messages = new HashSet<ProposedMessage>();
            msgsMap.put(msgId, messages);
        }

        messages.add(propMsg);

        if(constructAGREED(msgId, messages))
            msgsMap.remove(msgId);
    }

    private boolean constructAGREED(int msgId, Set<ProposedMessage> messages)
    {
        Set<Integer> aclients = getActiveClients();
        for (ProposedMessage propMsg : messages)
        {
            aclients.remove(propMsg.getProposerId());
        }

        Log.v(TAG, "[MSG-" + myId + "-" + msgId + "] [PROP-PENDING] " + aclients);

        int size = messages.size();
        if(size == getActiveClientsCount())
        {
            ProposedMessage pm = getMsgWithMaxProposedID(messages);
            Log.v(TAG, "[MSG-" + myId + "-" + msgId + "] [PROP-ACCEPT] " + pm.getProposerId());
            AgreedMessage agreedMessage = new AgreedMessage(pm);
            msgQueue.offer(agreedMessage);
            return true;
        }

        return false;
    }

    private ProposedMessage getMsgWithMaxProposedID(Set<ProposedMessage> messages)
    {
        int seqNo = Integer.MIN_VALUE;
        int proposerId = Integer.MIN_VALUE;

        for(ProposedMessage msg: messages)
        {
            if(seqNo < msg.getProposedSeqNo())
            {
                seqNo = msg.getProposedSeqNo();
                proposerId = msg.getProposerId();
            }
            else if (seqNo == msg.getProposedSeqNo())
            {
                proposerId = Math.max(proposerId, msg.getProposerId());
            }
        }

        for(ProposedMessage msg: messages)
        {
            if(seqNo == msg.getProposedSeqNo() && msg.getProposerId() == proposerId)
                return msg;
        }

        return null;
    }


    /**
     *
     * @param msg
     */
    private void handleAGREED(Message msg)
    {
        AgreedMessage agrMsg = (AgreedMessage) msg;
        updateAgreedSeqAndDeliverIfPossible(agrMsg);
    }

    private void updateAgreedSeqAndDeliverIfPossible(AgreedMessage msg)
    {
        Log.v(TAG, "[MSG-" + msg.getMsgOwnerId() + "-" + msg.getId() + "] [AGREE-MESSAGE] " + msg.getAgreedSeqNo());

        // Update agreed sequence
        agreedSeqNo = Math.max(msg.getAgreedSeqNo(), agreedSeqNo);

        int senderId = msg.getMsgOwnerId();
        int msgId = msg.getId();

        ShortMsg reqMsg = null;
        for (ShortMsg sm : priorityQueue)
        {
            if(sm.senderId == senderId && sm.id == msgId)
            {
                reqMsg = sm;
                break;
            }
        }

        if(reqMsg == null)
        {
            Log.v(TAG, "[MSG-" + msg.getMsgOwnerId() + "-" + msg.getId() + "] [AGREE-ERROR] Not in priority queue");
            return;
        }

        priorityQueue.remove(reqMsg);

        reqMsg.proposerId = msg.getProposerId();
        reqMsg.seqNo = msg.getAgreedSeqNo();
        reqMsg.deliverable = true;
        Log.v(TAG, "[MSG-" + msg.getMsgOwnerId() + "-" + msg.getId() + "] [AGREE-DELIVERABLE]");

        priorityQueue.offer(reqMsg);

        // Deliver the messages if deliverable
        deliverIfPossible();
    }

    private void deliverIfPossible()
    {
        while(!priorityQueue.isEmpty())
        {
            ShortMsg s = priorityQueue.peek();
            if(!s.deliverable)
            {
                Log.v(TAG, "[PRIORITY-QUEUE] [MSG-" + s.senderId + "-" + s.id + "] Not deliverable");
                break;
            }

            priorityQueue.poll();

            Log.v(TAG, "[MSG-" + s.senderId + "-" + s.id + "] [AGREE-DELIVERED]");

            ContentValues values = new ContentValues();
            values.put(KeyValueStorageHelper.COLUMN_KEY, globalSeqNum.getAndIncrement());
            values.put(KeyValueStorageHelper.COLUMN_VALUE, s.msg);
            contentResolver.insert(uri, values);
        }
    }

    public synchronized boolean removeMessages(int clientId)
    {
        Log.v(TAG, "[FAILURE] " + clientId);

        clients.remove(clientId);

        for(Iterator<Map.Entry<Integer, Set<ProposedMessage>>> iter = msgsMap.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry<Integer, Set<ProposedMessage>> entry = iter.next();
            Set<ProposedMessage> messages = entry.getValue();

            Log.v(TAG, "[FAILURE] [MSG-" + myId + "-" + entry.getKey() + "] proposed messages = " + messages.size());

            for(Iterator<ProposedMessage> iter2 = messages.iterator(); iter2.hasNext();)
            {
                ProposedMessage pmsg = iter2.next();
                if(pmsg.getProposerId() == clientId)
                {
                    iter2.remove();
                    break;
                }
            }

            if(constructAGREED(entry.getKey(), messages))
                iter.remove();
        }

        Iterator<ShortMsg> iter = priorityQueue.iterator();
        while(iter.hasNext())
        {
            ShortMsg msg = iter.next();
            if(msg.senderId == clientId)
            {
                Log.v(TAG, "[FAILURE][PRIORITY-QUEUE] Remove " + msg);
                iter.remove();
            }
        }

        return true;
    }

    /**
     *
     * @param msg
     */
    private void handlePING(Message msg)
    {
//        Log.v(TAG, "Ping received " + msg);
    }

    /**
     *
     */
    public synchronized void initPing()
    {
        if(pingInited)
            return;

        new PingGenerator().start();
        pingInited = true;
    }

    private class PingGenerator extends Thread
    {
        private final String TAG = PingGenerator.class.getName();

        private volatile int count = 0;

        @Override
        public void run()
        {
            Log.i(TAG, "Starting ping generator");

            while(true)
            {
                PingMessage msg = new PingMessage(myId);
                msgQueue.offer(msg);

                // Sleep for 2000 ms
                try
                {
                    Thread.sleep(5000);
                }
                catch (InterruptedException e)
                {

                }

                count++;

                if(count == 10)
                {
                    Log.v(TAG, "[LOGGER] Priority Queue = " + priorityQueue.size());
                    count = 0;
                }
            }
        }
    }
}
