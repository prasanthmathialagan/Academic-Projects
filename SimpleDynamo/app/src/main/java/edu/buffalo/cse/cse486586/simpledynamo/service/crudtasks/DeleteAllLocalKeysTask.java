package edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks;

import android.util.Log;

import java.util.concurrent.Callable;

import edu.buffalo.cse.cse486586.simpledynamo.KeyValueStorageHelper;
import edu.buffalo.cse.cse486586.simpledynamo.service.CRUDHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoNode;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoService;
import edu.buffalo.cse.cse486586.simpledynamo.service.SocketHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.DeleteNodeDataMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.DeleteNodeDataResponse;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.DeleteAllLocalKeysTask.DeletionWrapper;

/**
 * Created by prasanth on 4/24/16.
 */
public class DeleteAllLocalKeysTask implements Callable<DeletionWrapper>
{
    private static final String TAG = DeleteAllLocalKeysTask.class.getSimpleName();

    private final String nodeId;
    private final String destNodeId;
    private final boolean localDelete;
    private final KeyValueStorageHelper storageHelper;
    private final SimpleDynamoService dynamoService;

    public DeleteAllLocalKeysTask(String nodeId, String destNodeId, boolean localDelete, KeyValueStorageHelper storageHelper, SimpleDynamoService dynamoService)
    {
        this.nodeId = nodeId;
        this.destNodeId = destNodeId;
        this.localDelete = localDelete;
        this.storageHelper = storageHelper;
        this.dynamoService = dynamoService;
    }

    public class DeletionWrapper
    {
        private final int deletionCount;
        private final boolean error;

        private DeletionWrapper(int deletionCount, boolean error)
        {
            this.deletionCount = deletionCount;
            this.error = error;
        }

        public int getDeletionCount()
        {
            return deletionCount;
        }

        public boolean isError()
        {
            return error;
        }
    }

    @Override
    public DeletionWrapper call() throws Exception
    {
        Log.i(TAG, "[DELETE_NODE] [" + nodeId + "] destination node = " + destNodeId);

        // If it is a local delete
        if(localDelete)
        {
            Log.i(TAG, "[DELETE_NODE] [" + nodeId + "] Deleting keys locally.");
            return new DeletionWrapper(storageHelper.deleteAllDataForNode(nodeId), false);
        }
        else
        {
            int deletionCount = 0;
            boolean error = false;

            Log.i(TAG, "[DELETE_NODE] [" + nodeId + "] Sending query key message to the node " + destNodeId);

            DeleteNodeDataMessage deleteNodeDataMessage = new DeleteNodeDataMessage(nodeId);
            SimpleDynamoNode node = dynamoService.getNode(destNodeId);
            synchronized (node)
            {
                SocketHandler socketHandler = node.getSocketHandler();
                if(socketHandler == null)
                {
                    error = true;
                    Log.w(TAG, "[DELETE_NODE] [" + nodeId + "] Socket handler is not active. Hence dropping the message");
                }
                else
                {
                    if(!socketHandler.sendMessage(deleteNodeDataMessage))
                    {
                        error = true;
                        Log.w(TAG, "[DELETE_NODE] [" + nodeId + "] Could not send the message to the node " + destNodeId);
                    }
                    else
                    {
                        DeleteNodeDataResponse response = (DeleteNodeDataResponse) socketHandler.readMessage();
                        if(response == null)
                        {
                            Log.w(TAG, "[DELETE_NODE] [" + nodeId + "] No response from the destination node " + destNodeId);
                            error = true;
                        }
                        else
                        {
                            deletionCount = response.getDeletionCount();
                            Log.w(TAG, "[DELETE_NODE] [" + nodeId + "] Response from the destination node " + destNodeId + ", deletion count = " + deletionCount);
                        }
                    }
                }
            }

            return new DeletionWrapper(deletionCount, error);
        }
    }
}
