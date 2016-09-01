package edu.buffalo.cse.cse486586.simpledynamo.service.message;

import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;

/**
 * Created by prasanth on 4/24/16.
 */
public class DeleteNodeDataMessage extends Message
{
    private final String nodeId;

    public DeleteNodeDataMessage(String nodeId)
    {
        this.nodeId = nodeId;
    }

    public String getNodeId()
    {
        return nodeId;
    }
}
