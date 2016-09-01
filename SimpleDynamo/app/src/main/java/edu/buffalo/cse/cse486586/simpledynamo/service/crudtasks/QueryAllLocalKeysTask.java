package edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks;

import android.database.Cursor;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.Callable;

import edu.buffalo.cse.cse486586.simpledynamo.KeyValueStorageHelper;
import edu.buffalo.cse.cse486586.simpledynamo.Utils;
import edu.buffalo.cse.cse486586.simpledynamo.service.CRUDHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoNode;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoService;
import edu.buffalo.cse.cse486586.simpledynamo.service.SocketHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.Value;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.QueryNodeMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.QueryNodeResponse;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.QueryAllLocalKeysTask.KeyValuesWrapper;

/**
 * Created by prasanth on 4/24/16.
 */
public class QueryAllLocalKeysTask implements Callable<KeyValuesWrapper>
{
    private static final String TAG = QueryAllLocalKeysTask.class.getSimpleName();

    private final String nodeId;
    private final String destNodeId;
    private final boolean localRead;
    private final KeyValueStorageHelper storageHelper;
    private final SimpleDynamoService dynamoService;

    public QueryAllLocalKeysTask(String nodeId, String destNodeId, boolean localRead, KeyValueStorageHelper storageHelper, SimpleDynamoService dynamoService)
    {
        this.nodeId = nodeId;
        this.destNodeId = destNodeId;
        this.localRead = localRead;
        this.storageHelper = storageHelper;
        this.dynamoService = dynamoService;
    }

    // To avoid the NPE in ExecutorCompletionService
    public class KeyValuesWrapper
    {
        private final Map<String, Value> keyValues;
        private final boolean error;

        private KeyValuesWrapper(Map<String, Value> keyValues, boolean error)
        {
            this.keyValues = keyValues;
            this.error = error;
        }

        public Map<String, Value> getKeyValues()
        {
            return keyValues;
        }

        public boolean isError()
        {
            return error;
        }
    }

    @Override
    public KeyValuesWrapper call() throws Exception
    {
        Log.i(TAG, "[" + nodeId + "] destination node = " + destNodeId);

        // If it is a local read
        if(localRead)
        {
            Log.i(TAG, "[" + nodeId + "] Querying for keys locally.");
            Cursor cursor = storageHelper.getAllDataForNode(nodeId);

            try
            {
                return new KeyValuesWrapper(Utils.getKeyValues(cursor), false);
            }
            finally
            {
                cursor.close();
            }
        }
        else
        {
            Map<String, Value> keyValues = null;
            boolean error = false;

            Log.i(TAG, "[" + nodeId + "] Sending query node message to the node " + destNodeId);

            QueryNodeMessage queryNodeMessage = new QueryNodeMessage(nodeId);
            SimpleDynamoNode node = dynamoService.getNode(destNodeId);
            synchronized (node)
            {
                SocketHandler socketHandler = node.getSocketHandler();
                if(socketHandler == null)
                {
                    error = true;
                    Log.w(TAG, "[" + nodeId + "] Socket handler is not active for " + destNodeId + ". Hence dropping the message");
                }
                else
                {
                    if(!socketHandler.sendMessage(queryNodeMessage))
                    {
                        error = true;
                        Log.w(TAG, "[" + nodeId + "] Could not send the message to the node " + destNodeId);
                    }
                    else
                    {
                        QueryNodeResponse message = (QueryNodeResponse) socketHandler.readMessage();
                        if(message == null)
                        {
                            Log.w(TAG, "[" + nodeId + "] No response from the destination node " + destNodeId);
                            error = true;
                        }
                        else
                        {
                            keyValues = message.getKeyValues();
                            Log.w(TAG, "[" + nodeId + "] Response from the destination node " + destNodeId + ", keys = " + keyValues.size());
                        }
                    }
                }
            }

            return new KeyValuesWrapper(keyValues, error);
        }
    }

}
