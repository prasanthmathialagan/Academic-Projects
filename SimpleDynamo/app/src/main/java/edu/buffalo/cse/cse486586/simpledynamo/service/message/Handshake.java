package edu.buffalo.cse.cse486586.simpledynamo.service.message;

import java.io.Serializable;

/**
 * Created by prasanth on 4/21/16.
 */
public final class Handshake implements Serializable
{
    public static final String SYNC = "SYNC";
    public static final String SYN_ACK = "SYN_ACK";
    public static final String DUP_CON = "DUP_CON";

    private final String sourceNodeId;
    private final String destinationNodeId;
    private final String message;

    public Handshake(String sourceNodeId, String destinationNodeId, String message)
    {
        this.sourceNodeId = sourceNodeId;
        this.destinationNodeId = destinationNodeId;
        this.message = message;
    }

    public String getSourceNodeId()
    {
        return sourceNodeId;
    }

    public String getDestinationNodeId()
    {
        return destinationNodeId;
    }

    public String getMessage()
    {
        return message;
    }
}
