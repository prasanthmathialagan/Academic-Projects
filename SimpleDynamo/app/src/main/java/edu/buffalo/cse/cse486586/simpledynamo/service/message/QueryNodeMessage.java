package edu.buffalo.cse.cse486586.simpledynamo.service.message;

import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;

/**
 * Created by prasanth on 4/23/16.
 */
public class QueryNodeMessage extends Message
{
    private final String nodeId;

    public QueryNodeMessage(String nodeId)
    {
        this.nodeId = nodeId;
    }

    public String getNodeId()
    {
        return nodeId;
    }
}
