package edu.buffalo.cse.cse486586.simpledynamo.service;

/**
 *  This class is not thread safe
 *
 * Created by prasanth on 4/22/16.
 */
public final class SimpleDynamoNode
{
    private final String nodeId;
    private boolean alive;
    private long lastConnectTimestamp = -1;

    private volatile SocketHandler socketHandler;

    public SimpleDynamoNode(String nodeId)
    {
        this.nodeId = nodeId;
    }

    public String getNodeId()
    {
        return nodeId;
    }

    public boolean isAlive()
    {
        return alive;
    }

    public void setAlive(boolean alive)
    {
        this.alive = alive;
    }

    public long getLastConnectTimestamp()
    {
        return lastConnectTimestamp;
    }

    public void setLastConnectTimestamp(long lastConnectTimestamp)
    {
        this.lastConnectTimestamp = lastConnectTimestamp;
    }

    public SocketHandler getSocketHandler()
    {
        return socketHandler;
    }

    public void setSocketHandler(SocketHandler socketHandler)
    {
        this.socketHandler = socketHandler;
    }
}
