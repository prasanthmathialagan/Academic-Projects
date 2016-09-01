package edu.buffalo.cse.cse486586.simpledynamo.service.message;

import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;

/**
 * Created by prasanth on 4/24/16.
 */
public class DeleteKeyMessage extends Message
{
    private final String key;
    private final String nodeId;

    public DeleteKeyMessage(String key, String nodeId)
    {
        this.key = key;
        this.nodeId = nodeId;
    }

    public String getKey()
    {
        return key;
    }

    public String getNodeId()
    {
        return nodeId;
    }
}
