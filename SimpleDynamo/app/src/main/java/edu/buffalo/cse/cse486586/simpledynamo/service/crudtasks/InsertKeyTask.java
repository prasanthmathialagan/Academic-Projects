package edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks;

import android.util.Log;

import java.util.concurrent.CountDownLatch;

import edu.buffalo.cse.cse486586.simpledynamo.KeyValueStorageHelper;
import edu.buffalo.cse.cse486586.simpledynamo.service.CRUDHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoNode;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoService;
import edu.buffalo.cse.cse486586.simpledynamo.service.SocketHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.InsertKeyMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;

/**
 * Created by prasanth on 4/24/16.
 */
public class InsertKeyTask implements Runnable
{
    private static final String TAG = InsertKeyTask.class.getSimpleName();

    private final String nodeId;
    private final String key;
    private final String value;
    private final CountDownLatch latch;
    private final boolean localInsert;
    private final String destNodeId;
    private final KeyValueStorageHelper storageHelper;
    private final SimpleDynamoService dynamoService;

    public InsertKeyTask(String nodeId, String key, String value, CountDownLatch latch, boolean localInsert, String destNodeId, KeyValueStorageHelper storageHelper, SimpleDynamoService dynamoService)
    {
        this.nodeId = nodeId;
        this.key = key;
        this.value = value;
        this.latch = latch;
        this.localInsert = localInsert;
        this.destNodeId = destNodeId;
        this.storageHelper = storageHelper;
        this.dynamoService = dynamoService;
    }

    @Override
    public void run()
    {
        Log.i(TAG, "[" + key + "] value = " + value);

        // If it is a local insert
        if(localInsert)
        {
            Log.i(TAG, "[" + key + "] Inserting the key locally.");
            storageHelper.insertOrUpdate(key, value, nodeId, 1);
            latch.countDown();
        }
        else
        {
            Log.i(TAG, "[" + key + "] Sending insert key message to the node " + destNodeId);

            InsertKeyMessage insertKeyMessage = new InsertKeyMessage(key, value, nodeId);

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
                    if(!socketHandler.sendMessage(insertKeyMessage))
                    {
                        Log.w(TAG, "[" + key + "] Could not send the message to the node " + destNodeId);
                    }
                    else
                    {
                        Message message = socketHandler.readMessage();
                        if(message == null)
                        {
                            Log.w(TAG, "[" + key + "] Key could not be inserted at the destination node " + destNodeId);
                        }
                        else
                        {
                            Log.i(TAG, "[" + key + "] Key inserted successfully at the destination node " + destNodeId);
                            latch.countDown();
                        }
                    }

                }
            }
        }
    }


}
