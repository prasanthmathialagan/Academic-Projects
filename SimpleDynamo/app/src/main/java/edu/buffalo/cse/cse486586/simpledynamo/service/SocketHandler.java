package edu.buffalo.cse.cse486586.simpledynamo.service;

import android.database.Cursor;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.buffalo.cse.cse486586.simpledynamo.KeyValueStorageHelper;
import edu.buffalo.cse.cse486586.simpledynamo.Utils;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.ConnectionCheck;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.DeleteKeyMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.DeleteNodeDataMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.DeleteNodeDataResponse;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.InsertKeyMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.QueryNodeMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.QueryNodeResponse;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.QuerySingleMessage;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.QuerySingleResponse;

/**
 * Created by prasanth on 4/22/16.
 */
public class SocketHandler implements Runnable
{
    private static final String TAG = SocketHandler.class.getSimpleName();

    private final SimpleDynamoNode node;
    private final Socket socket;
    private volatile boolean run = true;
    private final LinkedBlockingQueue<Message> queue = new LinkedBlockingQueue<Message>(1);
    private final KeyValueStorageHelper storageHelper;

    public SocketHandler(SimpleDynamoNode node, Socket socket, KeyValueStorageHelper storageHelper)
    {
        this.node = node;
        this.socket = socket;
        this.storageHelper = storageHelper;
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Starting socket handler for the node " + node.getNodeId());

        while(run)
        {
            try
            {
                Message m = (Message) Utils.receiveObject(socket);

                if (m instanceof InsertKeyMessage)
                {
                    InsertKeyMessage insertKeyMessage = (InsertKeyMessage) m;

                    String nodeId = insertKeyMessage.getNodeId();
                    String key = insertKeyMessage.getKey();
                    String value = insertKeyMessage.getValue();

                    Log.i(TAG, "[INSERT] key = " + key + ", value = " + value + ", nodeId = " + nodeId);

                    storageHelper.insertOrUpdate(key, value, nodeId, 1);

                    Message response = new Message();
                    sendMessage(response);
                }
                else if(m instanceof QuerySingleMessage)
                {
                    QuerySingleMessage querySingleMessage = (QuerySingleMessage) m;

                    String key = querySingleMessage.getKey();
                    String nodeId = querySingleMessage.getNodeId();

                    Log.i(TAG, "[QUERY_SINGLE] key = " + key + ", nodeId = " + nodeId);

                    Cursor cursor = storageHelper.getDataForKey(key);
                    try
                    {
                        Value value;

                        cursor.moveToFirst();
                        if(cursor.getCount() > 0)
                        {
                            String v = cursor.getString(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VALUE));
                            int version = cursor.getInt(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VERSION));
                            value = new Value(v, version);
                        }
                        else
                        {
                            // key is not present in the database.
                            value = null;
                        }

                        Log.i(TAG, "[QUERY_SINGLE] key = " + key + ", value = " + value);

                        QuerySingleResponse response = new QuerySingleResponse(key, value);
                        sendMessage(response);
                    }
                    finally
                    {
                        cursor.close();
                    }
                }
                else if(m instanceof QueryNodeMessage)
                {
                    QueryNodeMessage queryNodeMessage = (QueryNodeMessage) m;
                    String nodeId = queryNodeMessage.getNodeId();

                    Log.i(TAG, "[QUERY_NODE] nodeId = " + nodeId);

                    Cursor cursor = storageHelper.getAllDataForNode(nodeId);
                    try
                    {
                        Map<String, Value> keyValues = Utils.getKeyValues(cursor);

                        Log.i(TAG, "[QUERY_NODE] nodeId = " + nodeId + ", keys = " + keyValues.size());

                        QueryNodeResponse response = new QueryNodeResponse(keyValues);
                        sendMessage(response);
                    }
                    finally
                    {
                        cursor.close();
                    }
                }
                else if(m instanceof DeleteKeyMessage)
                {
                    DeleteKeyMessage deleteKeyMessage = (DeleteKeyMessage) m;

                    String nodeId = deleteKeyMessage.getNodeId();
                    String key = deleteKeyMessage.getKey();

                    Log.i(TAG, "[DELETE_SINGLE] key = " + key + ", nodeId = " + nodeId);

                    storageHelper.delete(key, nodeId);

                    Message response = new Message();
                    sendMessage(response);
                }
                else if(m instanceof DeleteNodeDataMessage)
                {
                    DeleteNodeDataMessage deleteNodeDataMessage = (DeleteNodeDataMessage) m;

                    String nodeId = deleteNodeDataMessage.getNodeId();
                    int deletionCount = storageHelper.deleteAllDataForNode(nodeId);

                    Log.i(TAG, "[DELETE_NODE] nodeId = " + nodeId + ", deletion count = " + deletionCount);

                    DeleteNodeDataResponse response = new DeleteNodeDataResponse(deletionCount);
                    sendMessage(response);
                }
                else if(m instanceof ConnectionCheck)
                {
                    Log.i(TAG, "[CONNECTION_CHECK] Success");
                }
                else
                {
                    Log.v(TAG, "Queueing the message");
                    queue.put(m);
                }
            }
            catch (SocketTimeoutException te)
            {
                Log.e(TAG, "Timeout occurred for the node " + node.getNodeId(), te);
                close();
            }
            catch (IOException e)
            {
                Log.e(TAG, "Error occurred while reading data from node " + node.getNodeId(), e);
                close();
            }
            catch (Exception e)
            {
                Log.wtf(TAG, "Unexpected error occurred for the node " + node.getNodeId(), e);
                close();
            }
        }

        Log.i(TAG, "Stopping socket handler for the node " + node.getNodeId());
    }

    private void close()
    {
        run = false;
        node.setSocketHandler(null);
        node.setAlive(false);

        try
        {
            // Assuming that there will be only one thread waiting on this
            if(queue.offer(new PoisonPill()))
            {
                Log.i(TAG, "Poison pill put into the queue for " + node.getNodeId());
            }
            else
            {
                Log.w(TAG, "Unable to put the poison pill into the queue for " + node.getNodeId()
                        + ". This may be due to another poison pill inside the queue.");
            }
            socket.close();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error occurred while closing socket handler for " + node.getNodeId(), e);
        }
    }

    /**
     *     Returns false if the message cannot be sent. In case of IOException, socket handler will be closed.
     *
     * @param msg
     * @return
     */
    public synchronized boolean sendMessage(Message msg)
    {
        try
        {
            if(socket.isClosed())
            {
                Log.w(TAG, "Socket handler is closed for " + node.getNodeId());
                return false;
            }

            Utils.sendObject(msg, socket);
            return true;
        }
        catch (IOException e)
        {
            Log.e(TAG, "IOException occurred while sending data to node " + node.getNodeId(), e);
            close();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Unexpected exception occurred while sending data to node " + node.getNodeId(), e);
        }

        return false;
    }

    /**
     * @return
     */
    public Message readMessage()
    {
        try
        {
            Message message;
            if(SimpleDynamoService.SO_TIMEOUT <= 0)
                message = queue.take();
            else
                message = queue.poll(SimpleDynamoService.SO_TIMEOUT, TimeUnit.MILLISECONDS);

            if(message instanceof PoisonPill)
            {
                Log.i(TAG, "Poison pill received.");
                message = null;
            }

            return message;
        }
        catch (Exception e)
        {
            Log.wtf(TAG, "Unexpected error occurred!!!", e);
            return null;
        }
    }

    private class PoisonPill extends Message
    {

    }
}
