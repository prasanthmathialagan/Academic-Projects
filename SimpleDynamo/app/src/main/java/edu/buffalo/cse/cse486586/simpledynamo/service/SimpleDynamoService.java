package edu.buffalo.cse.cse486586.simpledynamo.service;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import edu.buffalo.cse.cse486586.simpledynamo.KeyValueStorageHelper;
import edu.buffalo.cse.cse486586.simpledynamo.SimpleDynamoRing;
import edu.buffalo.cse.cse486586.simpledynamo.Utils;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.QueryAllLocalKeysTask;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.QueryAllLocalKeysTask.KeyValuesWrapper;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.ConnectionCheck;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.Handshake;

/**
 * Created by prasanth on 4/21/16.
 */
public final class SimpleDynamoService
{
    private static final String TAG = SimpleDynamoService.class.getSimpleName();

    private static final int SERVER_PORT = 10000;

    private static final int HANDSHAKE_RETRIES = 10;
    private static final long RETRY_INTERVAL = 1000;
    public static final int SO_TIMEOUT = 0; // FIXME

    private static final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(SimpleDynamoRing.NODES.length - 1);

    private final Map<String, SimpleDynamoNode> idToNodeMap = new HashMap<String, SimpleDynamoNode>();

    private final String currentNodeId;
    private final String predecessorNodeId;

    private final KeyValueStorageHelper storageHelper;

    public SimpleDynamoService(String id, KeyValueStorageHelper storageHelper)
    {
        this.currentNodeId = id;
        this.predecessorNodeId = SimpleDynamoRing.getPredecessor(id);

        for (String nodeId : SimpleDynamoRing.NODES)
        {
            // Ignore the current node
            if(nodeId.equals(id))
                continue;

            idToNodeMap.put(nodeId, new SimpleDynamoNode(nodeId));
        }

        this.storageHelper = storageHelper;
    }

    public String getPredecessorNodeId()
    {
        return predecessorNodeId;
    }

    public String getCurrentNodeId()
    {
        return currentNodeId;
    }

    public SimpleDynamoNode getNode(String nodeId)
    {
        return idToNodeMap.get(nodeId);
    }

    public boolean start()
    {
        Log.i(TAG, "[DYNAMO_START] Starting the Simple Dynamo Service.....");

        long s1 = System.currentTimeMillis();

        // Start listening for incoming connections
        try
        {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch (IOException e)
        {
            Log.e(TAG, "[DYNAMO_START] Can't create a ServerSocket", e);
            return true; // FIXME: This should not be done.
        }

        long s2 = System.currentTimeMillis();

        ThreadPoolExecutor handshakeExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(SimpleDynamoRing.NODES.length);

        /**
         *  Make connections to all the nodes. If the response is ok, then monitor the socket. If the response says
         *  duplicate connection, then close it.
         */
        List<HandshakeTask> handshakeTasks = new ArrayList<HandshakeTask>();
        for (String nodeId : SimpleDynamoRing.NODES)
        {
            // Ignore the current node
            if(nodeId.equals(currentNodeId))
                continue;

            handshakeTasks.add(new HandshakeTask(nodeId));
        }

        try
        {
            handshakeExecutor.invokeAll(handshakeTasks);
        }
        catch (Exception e)
        {
            Log.e(TAG, "[DYNAMO_START] Unexpected error occurred during handshake", e);
            return false;
        }
        finally
        {
            handshakeExecutor.shutdown();
        }

        long e2 = System.currentTimeMillis();

        Log.i(TAG, "[DYNAMO_START] Time taken for handshake = " + (e2 - s2) + " ms");

        long s3 = System.currentTimeMillis();

        // Reconcile
        ThreadPoolExecutor reconExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);

        try
        {
            Log.i(TAG, "[DYNAMO_START] Reconciling my keys.");

            // My Keys reconciliation
            reconcile(reconExecutor, "[SELF_RECON]", currentNodeId, SimpleDynamoRing.getReplicas(currentNodeId));

            // Reverse replicas reconciliation
            String[] reverseReplicas = SimpleDynamoRing.getReverseReplicas(currentNodeId);
            for (String revRep : reverseReplicas)
            {
                Log.i(TAG, "[DYNAMO_START] Reconciling reverse replica " + revRep);
                String[] replicas = new String[reverseReplicas.length];
                replicas[0] = revRep;

                int i = 1;
                for (String rep : SimpleDynamoRing.getReplicas(revRep))
                {
                    // Ignore the current node
                    if(rep.equals(currentNodeId))
                        continue;

                    replicas[i++] = rep;
                }

                reconcile(reconExecutor, "[REV_REPLICA_RECON][" + revRep + "]", revRep, replicas);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "[DYNAMO_START] Unexpected error occurred during reconciliation", e);
            return false;
        }
        finally
        {
            reconExecutor.shutdown();
        }

        long e3 = System.currentTimeMillis();

        Log.i(TAG, "[DYNAMO_START] Time taken for reconciliation = " + (e3 - s3) + " ms");

        long e1 = System.currentTimeMillis();

        Log.i(TAG, "[DYNAMO_START] Simple Dynamo Service started in " + (e1 - s1) + " ms");

        return true;
    }

    private void reconcile(ThreadPoolExecutor reconExecutor, String header, String nodeId, String[] replicas) throws Exception
    {
        // Get the results from local
        KeyValuesWrapper myKeysWrapper = new QueryAllLocalKeysTask(nodeId, currentNodeId, true, storageHelper, this).call();
        Map<String, Value> myKVs = myKeysWrapper.getKeyValues();
        Log.i(TAG, header + " My keys = " + myKVs.size());

        ExecutorCompletionService<KeyValuesWrapper> ecs = new ExecutorCompletionService<KeyValuesWrapper>(reconExecutor);
        for (String replica : replicas)
        {
            ecs.submit(new QueryAllLocalKeysTask(nodeId, replica, false, storageHelper, this));
        }

        // Get the results from other partitions
        List<Map<String, Value>> otherResults = new ArrayList<Map<String, Value>>(SimpleDynamoRing.REPLICA_SIZE - 2);
        int successCount = 0;
        for(int i = 0; i < SimpleDynamoRing.REPLICA_SIZE - 1; i++)
        {
            try
            {
                Future<KeyValuesWrapper> future = Utils.poll(ecs, SimpleDynamoService.SO_TIMEOUT);
                KeyValuesWrapper wrapper = future.get();
                if(wrapper.isError())
                    continue;;

                if(wrapper.getKeyValues() != null)
                    otherResults.add(wrapper.getKeyValues());

                successCount++;
            }
            catch (InterruptedException e)
            {
                Log.e(TAG, header + " Timeout occurred while waiting for query response!!!", e);
            }
            catch (Exception e)
            {
                Log.e(TAG, header + " Unexpected error occurred!!!", e);
            }

            if(successCount == SimpleDynamoRing.REPLICA_SIZE - 2)
                break;
        }

        if(successCount < SimpleDynamoRing.REPLICA_SIZE - 2)
        {
            Log.wtf(TAG, header + " Not enough results[" + successCount + "] from the nodes");
            return;
        }

        Set<String> keys = new HashSet<String>();
        for (Map<String, Value> v : otherResults)
        {
            keys.addAll(v.keySet());
        }

        Log.i(TAG, header + " Other keys = " + keys.size());

        keys.addAll(myKVs.keySet());

        Log.i(TAG, header + " Total keys = " + keys.size());

        if(keys.isEmpty())
            return;

        Map<String, Value> keysToUpdate = new HashMap<String, Value>();

        for (String key : keys)
        {
            int myVersion = -1;
            int otherVersion = -1;
            String otherValue = null;
            String myValue = null;

            for (Map<String, Value> kvs : otherResults)
            {
                Value v = kvs.get(key);
                if (v == null)
                {
                    Log.w(TAG, header + " No value associated with the key = " + key);
                    continue;
                }

                if(v.getVersion() > otherVersion)
                {
                    otherValue = v.getValue();
                    otherVersion = v.getVersion();
                }
            }

            Value myV = myKVs.get(key);
            if(myV != null)
            {
                myValue = myV.getValue();
                myVersion = myV.getVersion();
            }

            if(myVersion == otherVersion)
                continue;

            Log.w(TAG, header + "[VERSION_MISMATCH] key = " + key + " [LOCAL_VALUE]: value = "
                    + myValue + ", version = " + myVersion + ", [OTHER_VALUE]: value = " + otherValue
                    + ", version = " + otherVersion);

            if(myVersion < otherVersion)
            {
                keysToUpdate.put(key, new Value(otherValue, otherVersion));
            }
            else
            {
                Log.wtf(TAG, header + " key = " + key + " WHAT KIND OF BEHAVIOUR IS THIS!!!!!");
            }
        }

        Log.i(TAG, header + " Total mismatch keys = " + keysToUpdate.size());

        insert(keysToUpdate, nodeId);
    }

    private void insert(Map<String, Value> keyValues, String nodeId)
    {
        if(!keyValues.isEmpty())
        {
            synchronized (storageHelper)
            {
                for (Map.Entry<String, Value> entry : keyValues.entrySet())
                {
                    String key = entry.getKey();
                    String value = entry.getValue().getValue();
                    int version = entry.getValue().getVersion();

                    storageHelper.insertOrUpdate(key, value, nodeId, version);
                }
            }
        }
    }

    private int getPort(String id)
    {
        return Integer.parseInt(id) * 2;
    }

    private void monitorSocket(Socket socket, SimpleDynamoNode node)
    {
        synchronized (node)
        {
            SocketHandler socketHandler = new SocketHandler(node, socket, storageHelper);
            node.setSocketHandler(socketHandler);
            node.setAlive(true);
            node.setLastConnectTimestamp(System.currentTimeMillis());

            executor.execute(socketHandler);
        }
    }

    public class ServerTask extends AsyncTask<ServerSocket, Socket, Void>
    {
        private final String TAG = SimpleDynamoService.class.getName();

        @Override
        protected Void doInBackground(ServerSocket... sockets)
        {
            ServerSocket serverSocket = sockets[0];

            String header = "[DYNAMO_SERVICE] ";

            Log.i(TAG, header + "Listening for inbound connections.....");

            while (!isCancelled())
            {
                try
                {
                    Socket soc = serverSocket.accept();
                    soc.setSoTimeout(SO_TIMEOUT);

                    Handshake handshakeMsg = (Handshake) Utils.receiveObject(soc);
                    String sourceNodeId = handshakeMsg.getSourceNodeId();
                    String destinationNodeId = handshakeMsg.getDestinationNodeId();
                    Log.i(TAG, header + "[INBOUND_CONN] [" + sourceNodeId + "]");

                    SimpleDynamoNode node = idToNodeMap.get(sourceNodeId);
                    synchronized (node)
                    {
                        // Dining philosopher check.
                        if(sourceNodeId.compareTo(destinationNodeId) < 0)
                        {
                            Log.i(TAG, header + "[INBOUND_CONN] [" + sourceNodeId + "] This is a valid connection by dining philosopher check.");
                            handshakeOK(soc, sourceNodeId, destinationNodeId, node);
                        }
                        else
                        {
                            if(node.isAlive())
                            {
                                ConnectionCheck connectionCheck = new ConnectionCheck();
                                if(node.getSocketHandler().sendMessage(connectionCheck)) // To remove obsolete connection
                                {
                                    Log.i(TAG, header + "[INBOUND_CONN] [" + sourceNodeId + "] Already there is a healthy connection. This is not a valid connection.");
                                    Utils.sendObject(new Handshake(destinationNodeId, sourceNodeId, Handshake.DUP_CON), soc);
                                    soc.close();
                                }
                                else
                                {
                                    Log.i(TAG, header + "[INBOUND_CONN] [" + sourceNodeId + "] The existing connection is obsolete. Hence allowing this connection");
                                    handshakeOK(soc, sourceNodeId, destinationNodeId, node);
                                }
                            }
                            else if(node.getLastConnectTimestamp() < 0)
                            {
                                // First time connection
                                Log.i(TAG, header + "[INBOUND_CONN] [" + sourceNodeId + "] This is not a valid connection by dining philosopher check.");
                                Utils.sendObject(new Handshake(destinationNodeId, sourceNodeId, Handshake.DUP_CON), soc);
                                soc.close();
                            }
                            else
                            {
                                // Connection after a failure
                                Log.i(TAG, header + "[INBOUND_CONN] [" + sourceNodeId + "] This is a new connection after recovering from failure.");
                                handshakeOK(soc, sourceNodeId, destinationNodeId, node);
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    Log.e(TAG, header + "Error occurred!!", e);
                }
            }

            return null;
        }

        private void handshakeOK(Socket soc, String sourceNodeId, String destinationNodeId, SimpleDynamoNode node) throws IOException
        {
            monitorSocket(soc, node);
            Utils.sendObject(new Handshake(destinationNodeId, sourceNodeId, Handshake.SYN_ACK), soc);
        }
    }

    private class HandshakeTask implements Callable<Boolean>
    {
        private final String nodeId;

        public HandshakeTask(String nodeId)
        {
            this.nodeId = nodeId;
        }

        @Override
        public Boolean call() throws Exception
        {
            Handshake handshakeMsg = new Handshake(currentNodeId, nodeId, Handshake.SYNC);
            int port = getPort(nodeId);
            SimpleDynamoNode node = idToNodeMap.get(nodeId);

            int retryCount = 1;

            // Establish connection retrying until all the other AVDs come online.
            while(retryCount <= HANDSHAKE_RETRIES)
            {
                Log.i(TAG, "[HANDSHAKE] NodeId = " + nodeId + ", Retry count = " + retryCount);

                Socket socket = null;
                try
                {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), port);
                    socket.setSoTimeout(SO_TIMEOUT);

                    // Send the message
                    Utils.sendObject(handshakeMsg, socket);

                    try
                    {
                        // Get the response
                        Handshake response = (Handshake) Utils.receiveObject(socket);
                        if(Handshake.DUP_CON.equals(response.getMessage()))
                        {
                            Log.i(TAG, "[HANDSHAKE][DUPLICATE_CONN] NodeId = " + nodeId);
                            socket.close();
                        }
                        else
                        {
                            monitorSocket(socket, node);
                            Log.i(TAG, "[HANDSHAKE][SUCCESS] NodeId = " + nodeId);
                        }

                        return true;
                    }
                    catch (ClassNotFoundException e)
                    {
                        Log.e(TAG, "[HANDSHAKE][ERROR] Unexpected error occurred.", e);
                        return false;
                    }
                }
                catch (IOException e)
                {
                    Log.w(TAG, "[HANDSHAKE][ERROR] Node " + nodeId + " is still offline");
                    retryCount++;

                    if(socket != null)
                    {
                        try
                        {
                            socket.close();
                        }
                        catch (IOException ex)
                        {

                        }
                    }

                    Utils.sleep(RETRY_INTERVAL);
                }
            }

            Log.i(TAG, "[HANDSHAKE][FAILURE] Unable to handshake with the node " + nodeId);
            synchronized (node)
            {
                node.setLastConnectTimestamp(System.currentTimeMillis()); // This is set to allow initiating connection from the other side.
            }

            return false;
        }
    }
}
