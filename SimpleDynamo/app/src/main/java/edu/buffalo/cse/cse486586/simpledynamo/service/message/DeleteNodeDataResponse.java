package edu.buffalo.cse.cse486586.simpledynamo.service.message;

import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;

/**
 * Created by prasanth on 4/24/16.
 */
public class DeleteNodeDataResponse extends Message
{
    private final int deletionCount;

    public DeleteNodeDataResponse(int deletionCount)
    {
        this.deletionCount = deletionCount;
    }

    public int getDeletionCount()
    {
        return deletionCount;
    }
}
