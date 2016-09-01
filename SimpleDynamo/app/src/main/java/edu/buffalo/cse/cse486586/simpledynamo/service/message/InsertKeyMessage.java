package edu.buffalo.cse.cse486586.simpledynamo.service.message;

/**
 * Created by prasanth on 4/22/16.
 */
public class InsertKeyMessage extends Message
{
    private final String key;
    private final String value;
    private final String nodeId;

    public InsertKeyMessage(String key, String value, String nodeId)
    {
        this.key = key;
        this.value = value;
        this.nodeId = nodeId;
    }

    public String getKey()
    {
        return key;
    }

    public String getValue()
    {
        return value;
    }

    public String getNodeId()
    {
        return nodeId;
    }
}
