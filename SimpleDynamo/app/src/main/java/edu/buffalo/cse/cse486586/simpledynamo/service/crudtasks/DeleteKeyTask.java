package edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks;

import android.util.Log;

import java.util.concurrent.CountDownLatch;

import edu.buffalo.cse.cse486586.simpledynamo.KeyValueStorageHelper;
import edu.buffalo.cse.cse486586.simpledynamo.service.CRUDHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoNode;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoService;
import edu.buffalo.cse.cse486586.simpledynamo.service.SocketHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.DeleteKeyMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;

/**
 * Created by prasanth on 4/24/16.
 */
public class DeleteKeyTask implements Runnable
{
    private static final String TAG = DeleteKeyTask.class.getSimpleName();

    private final String key;
    private final String nodeId;
    private final String destNodeId;
    private final boolean localDelete;
    private final CountDownLatch latch;
    private final KeyValueStorageHelper storageHelper;
    private final SimpleDynamoService dynamoService;

    public DeleteKeyTask(String key, String nodeId, String destNodeId, boolean localDelete, CountDownLatch latch, KeyValueStorageHelper storageHelper, SimpleDynamoService dynamoService)
    {
        this.key = key;
        this.nodeId = nodeId;
        this.destNodeId = destNodeId;
        this.localDelete = localDelete;
        this.latch = latch;
        this.storageHelper = storageHelper;
        this.dynamoService = dynamoService;
    }

    @Override
    public void run()
    {

        Log.i(TAG, "[" + key + "] nodeId = " + nodeId + ", destination node = " + destNodeId);

        // If it is a local delete
        if(localDelete)
        {
            Log.i(TAG, "[" + key + "] Deleting the key locally.");
            storageHelper.delete(key, nodeId);
            latch.countDown();
        }
        else
        {
            Log.i(TAG, "[" + key + "] Sending delete key message to the node " + destNodeId);

            DeleteKeyMessage deleteKeyMessage = new DeleteKeyMessage(key, nodeId);

            SimpleDynamoNode node = dynamoService.getNode(destNodeId);
            synchronized (node)
            {
                SocketHandler socketHandler = node.getSocketHandler();
                if(socketHandler == null)
                {
                    Log.w(TAG, "[" + key + "] Socket handler is not active for " + destNodeId + ". Hence dropping the message");
                }
                else
                {
                    if(!socketHandler.sendMessage(deleteKeyMessage))
                    {
                        Log.w(TAG, "[" + key + "] Could not send the message to the node " + destNodeId);
                    }
                    else
                    {
                        Message message = socketHandler.readMessage();
                        if(message == null)
                        {
                            Log.w(TAG, "[" + key + "] Key could not be deleted at the destination node " + destNodeId);
                        }
                        else
                        {
                            Log.i(TAG, "[" + key + "] Key deleted successfully at the destination node " + destNodeId);
                            latch.countDown();
                        }
                    }

                }
            }
        }
    }
}
