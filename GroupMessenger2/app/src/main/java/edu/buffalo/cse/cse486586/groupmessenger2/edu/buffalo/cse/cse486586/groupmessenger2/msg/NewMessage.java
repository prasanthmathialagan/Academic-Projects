package edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg;

import edu.buffalo.cse.cse486586.groupmessenger2.MessageIDGenerator;
import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.Message;

/**
 * Created by prasanth on 3/5/16.
 */
public class NewMessage extends Message
{
    private final String message;

    public NewMessage(int senderId, String message)
    {
        super(MessageIDGenerator.getNextID(), MessageType.NEW, senderId, senderId);
        this.message = message;
    }

    public String getMessage()
    {
        return message;
    }

    @Override
    public String toString()
    {
        return "NewMessage{" +
                "message='" + message + '\'' +
                "} " + super.toString();
    }
}
