package edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg;

import edu.buffalo.cse.cse486586.groupmessenger2.MessageIDGenerator;

/**
 * Created by prasanth on 3/5/16.
 */
public class PingMessage extends Message
{
    public PingMessage(int senderId)
    {
        super(MessageIDGenerator.getNextID(), MessageType.PING, senderId, senderId);
    }
}
