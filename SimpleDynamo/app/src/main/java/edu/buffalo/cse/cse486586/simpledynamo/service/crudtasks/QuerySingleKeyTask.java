package edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks;

import android.database.Cursor;
import android.util.Log;

import java.util.concurrent.Callable;

import edu.buffalo.cse.cse486586.simpledynamo.KeyValueStorageHelper;
import edu.buffalo.cse.cse486586.simpledynamo.service.CRUDHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoNode;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoService;
import edu.buffalo.cse.cse486586.simpledynamo.service.SocketHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.Value;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.QuerySingleMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.QuerySingleResponse;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.QuerySingleKeyTask.ValueWrapper;

/**
 * Created by prasanth on 4/24/16.
 */
public class QuerySingleKeyTask implements Callable<ValueWrapper>
{
    private static final String TAG = QuerySingleKeyTask.class.getSimpleName();

    private final String key;
    private final String nodeId;
    private final String destNodeId;
    private final boolean localRead;
    private final KeyValueStorageHelper storageHelper;
    private final SimpleDynamoService dynamoService;

    public QuerySingleKeyTask(String key, String nodeId, String destNodeId, boolean localRead, KeyValueStorageHelper storageHelper, SimpleDynamoService dynamoService)
    {
        this.key = key;
        this.nodeId = nodeId;
        this.destNodeId = destNodeId;
        this.localRead = localRead;
        this.storageHelper = storageHelper;
        this.dynamoService = dynamoService;
    }

    // To avoid the NPE in ExecutorCompletionService
    public class ValueWrapper
    {
        private final Value value;
        private final boolean error;

        private ValueWrapper(String value, int version, boolean error)
        {
            this(new Value(value, version), error);
        }

        private ValueWrapper(Value value, boolean error)
        {
            this.value = value;
            this.error = error;
        }

        public Value getValue()
        {
            return value;
        }

        public boolean isError()
        {
            return error;
        }
    }

    @Override
    public ValueWrapper call() throws Exception
    {
        Log.i(TAG, "[" + key + "] nodeId = " + nodeId + ", destination node = " + destNodeId);

        // If it is a local read
        if(localRead)
        {
            Log.i(TAG, "[" + key + "] Querying the key locally.");
            Cursor cursor = storageHelper.getDataForKey(key);
            try
            {
                cursor.moveToFirst();
                if(cursor.getCount() > 0)
                {
                    String value = cursor.getString(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VALUE));
                    int version = cursor.getInt(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VERSION));
                    return new ValueWrapper(value, version, false);
                }
                else
                {
                    return new ValueWrapper(null, false); // key is not present in the database.
                }
            }
            finally
            {
                cursor.close();
            }
        }
        else
        {
            Value value = null;
            boolean error = false;

            Log.i(TAG, "[" + key + "] Sending query key message to the node " + destNodeId);
            QuerySingleMessage querySingleMessage = new QuerySingleMessage(key, nodeId);
            SimpleDynamoNode node = dynamoService.getNode(destNodeId);
            synchronized (node)
            {
                SocketHandler socketHandler = node.getSocketHandler();
                if(socketHandler == null)
                {
                    error = true;
                    Log.w(TAG, "[" + key + "] Socket handler is not active for " + destNodeId + ". Hence dropping the message");
                }
                else
                {
                    if(!socketHandler.sendMessage(querySingleMessage))
                    {
                        error = true;
                        Log.w(TAG, "[" + key + "] Could not send the message to the node " + destNodeId);
                    }
                    else
                    {
                        QuerySingleResponse message = (QuerySingleResponse) socketHandler.readMessage();
                        if(message == null)
                        {
                            Log.w(TAG, "[" + key + "] No response from the destination node " + destNodeId);
                            error = true;
                        }
                        else
                        {
                            value = message.getValue();
                            Log.w(TAG, "[" + key + "] Response from the destination node " + destNodeId + ", " + value );
                        }
                    }
                }

            }

            return new ValueWrapper(value, error);
        }
    }
}
