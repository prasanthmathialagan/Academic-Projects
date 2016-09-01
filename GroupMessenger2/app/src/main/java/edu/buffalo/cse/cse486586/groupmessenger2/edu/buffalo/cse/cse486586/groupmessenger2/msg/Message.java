package edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg;

import java.io.Serializable;

/**
 * Created by prasanth on 3/5/16.
 */
public abstract class Message implements Serializable
{
    public enum MessageType
    {
        NEW,
        PROPOSED,
        AGREED,
        PING,
    }

    private final int id;
    private final MessageType type;
    private final int msgOwnerId;
    private final int senderId;

    protected Message(int id, MessageType type, int msgOwnerId, int senderId)
    {
        this.id = id;
        this.type = type;
        this.msgOwnerId = msgOwnerId;
        this.senderId = senderId;
    }

    public int getId()
    {
        return id;
    }

    public MessageType getType()
    {
        return type;
    }

    public int getMsgOwnerId()
    {
        return msgOwnerId;
    }

    public int getSenderId()
    {
        return senderId;
    }

    @Override
    public String toString()
    {
        return "M{" + msgOwnerId + "-" + id + ", " + type + ", " + senderId +'}';
    }
}
