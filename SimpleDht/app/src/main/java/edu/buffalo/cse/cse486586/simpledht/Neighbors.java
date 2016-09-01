package edu.buffalo.cse.cse486586.simpledht;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

/**
 * Created by prasanth on 3/16/16.
 */
public class Neighbors
{
    private static final String TAG = Neighbors.class.getName();

    private static final Neighbors INSTANCE = new Neighbors();

    private String predecessorPort;
    private String predecessorId; // HashedValue

    private String successorPort;
    private String successorId; // HashedValue

    private Neighbors()
    {

    }

    public static Neighbors getInstance()
    {
        return INSTANCE;
    }

    public String getPredecessorPort()
    {
        return predecessorPort;
    }

    public void setPredecessorPort(String predecessorPort) throws NoSuchAlgorithmException
    {
        this.predecessorPort = predecessorPort;
        this.predecessorId = Utils.getIdFromPort(predecessorPort);
    }

    public String getPredecessorId()
    {
        return predecessorId;
    }

    public String getSuccessorPort()
    {
        return successorPort;
    }

    public void setSuccessorPort(String successorPort) throws NoSuchAlgorithmException
    {
        this.successorPort = successorPort;
        this.successorId = Utils.getIdFromPort(successorPort);
    }

    public String getSuccessorId()
    {
        return successorId;
    }

    public Socket getPredecessorSocket() throws IOException
    {
        return Utils.newSocket(predecessorPort);
    }

    public Socket getSuccessorSocket() throws IOException
    {
        return Utils.newSocket(successorPort);
    }
}
