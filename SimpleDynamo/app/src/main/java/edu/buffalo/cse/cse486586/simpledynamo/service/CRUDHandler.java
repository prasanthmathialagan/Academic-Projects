package edu.buffalo.cse.cse486586.simpledynamo.service;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import edu.buffalo.cse.cse486586.simpledynamo.KeyValueStorageHelper;
import edu.buffalo.cse.cse486586.simpledynamo.SimpleDynamoRing;
import edu.buffalo.cse.cse486586.simpledynamo.Utils;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.DeleteAllLocalKeysTask;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.DeleteAllLocalKeysTask.DeletionWrapper;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.DeleteKeyTask;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.InsertKeyTask;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.QueryAllLocalKeysTask;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.QueryAllLocalKeysTask.KeyValuesWrapper;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.QuerySingleKeyTask;
import edu.buffalo.cse.cse486586.simpledynamo.service.crudtasks.QuerySingleKeyTask.ValueWrapper;

/**
 * Created by prasanth on 4/22/16.
 */
public final class CRUDHandler
{
    private static final String TAG = CRUDHandler.class.getSimpleName();

    private final SimpleDynamoService dynamoService;
    private final KeyValueStorageHelper storageHelper;

    private static final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);

    public static final String STAR_QUERY1 = "*";
    public static final String STAR_QUERY2 = "\"*\"";

    public static final String AT_QUERY1 = "@";
    public static final String AT_QUERY2 = "\"@\"";


    public CRUDHandler(SimpleDynamoService dynamoService, KeyValueStorageHelper storageHelper)
    {
        this.dynamoService = dynamoService;
        this.storageHelper = storageHelper;
    }

    private boolean isLocalOperation(String nodeId)
    {
        return dynamoService.getCurrentNodeId().equals(nodeId);
    }

    /**
     *
     * @param key
     * @param value
     * @param nodeId
     * @param replicas
     * @return
     */
    public Uri insert(String key, String value, String nodeId, String[] replicas)
    {
        CountDownLatch latch = new CountDownLatch(SimpleDynamoRing.REPLICA_SIZE - 1);

        executor.execute(new InsertKeyTask(nodeId, key, value, latch, isLocalOperation(nodeId), nodeId, storageHelper, dynamoService));

        for (String replica : replicas)
        {
            executor.execute(new InsertKeyTask(nodeId, key, value, latch, isLocalOperation(replica), replica, storageHelper, dynamoService));
        }

        // Wait for twice the socket timeout
        try
        {
            Utils.await(latch, SimpleDynamoService.SO_TIMEOUT * 2);
        }
        catch (InterruptedException e)
        {
            Log.e(TAG, "[INSERT_KEY] [" + key + "] Timeout occurred while waiting for insertion!!!");
            return null;
        }

        return Utils.buildUri();
    }

    /**
     *
     * @param key
     * @param nodeId
     * @param replicas
     * @return
     */
    public Cursor querySingle(String key, String nodeId, String[] replicas)
    {
        ExecutorCompletionService<ValueWrapper> ecs = new ExecutorCompletionService<ValueWrapper>(executor);

        ecs.submit(new QuerySingleKeyTask(key, nodeId, nodeId, isLocalOperation(nodeId), storageHelper, dynamoService));

        for (String replica : replicas)
        {
            ecs.submit(new QuerySingleKeyTask(key, nodeId, replica, isLocalOperation(replica), storageHelper, dynamoService));
        }

        List<Value> results = new ArrayList<Value>(SimpleDynamoRing.REPLICA_SIZE - 1);
        int successCount = 0;
        for(int i = 0; i < SimpleDynamoRing.REPLICA_SIZE; i++)
        {
            try
            {
                Future<ValueWrapper> future = Utils.poll(ecs, SimpleDynamoService.SO_TIMEOUT);
                ValueWrapper valueWrapper = future.get();
                if(valueWrapper.isError())
                    continue;;

                if(valueWrapper.getValue() != null)
                    results.add(valueWrapper.getValue());

                successCount++;
            }
            catch (InterruptedException e)
            {
                Log.e(TAG, "[QUERY_SINGLE] [" + key + "] Timeout occurred while waiting for query response!!!");
            }
            catch (Exception e)
            {
                Log.e(TAG, "[QUERY_SINGLE] [" + key + "] Unexpected error occurred!!!");
            }

            if(successCount == SimpleDynamoRing.REPLICA_SIZE - 1)
                break;
        }

        if(successCount < SimpleDynamoRing.REPLICA_SIZE - 1)
        {
            Log.wtf(TAG, "[QUERY_SINGLE] [" + key + "] Not enough results[" + successCount + "] from the nodes");
           // return null; FIXME: This should not be commented out.
        }

        String value = null;
        int version = -1;
        for (Value v : results)
        {
            if(v.getVersion() > version)
            {
                if(version != -1)
                {
                    Log.w(TAG, "[QUERY_SINGLE] [" + key + "][VERSION_MISMATCH][OLD_VALUE]: value = " + value + ", version = " + version
                        + ", [NEW_VALUE]: value = " + v.getValue() + ", version = " + v.getVersion());
                }

                value = v.getValue();
                version = v.getVersion();
            }
        }

        Log.i(TAG, "[QUERY_SINGLE] [" + key + "] value = " + value + ", version = " + version);

        MatrixCursor cursor = Utils.getMatrixCursor();

        if(value != null)
            cursor.addRow(new Object[]{key, value});

        return cursor;
    }

    /**
     *
     * @param nodeId
     * @param replicas
     * @return
     */
    public Cursor queryNode(String nodeId, String[] replicas)
    {
        Log.i(TAG, "[QUERY_NODE] [" + nodeId + "]");

        Map<String, String> keyValues = _queryNode(nodeId, replicas);
        if(keyValues == null)
        {
            Log.i(TAG, "[QUERY_NODE] [" + nodeId + "] Could not retrieve the key values");
            return null;
        }

        Log.i(TAG, "[QUERY_NODE] [" + nodeId + "] Total keys = " + keyValues.size());

        return Utils.formCursor(keyValues);
    }

    private Map<String, String> _queryNode(String nodeId, String[] replicas)
    {
        ExecutorCompletionService<KeyValuesWrapper> ecs = new ExecutorCompletionService<KeyValuesWrapper>(executor);

        ecs.submit(new QueryAllLocalKeysTask(nodeId, nodeId, isLocalOperation(nodeId), storageHelper, dynamoService));

        for (String replica : replicas)
        {
            ecs.submit(new QueryAllLocalKeysTask(nodeId, replica, isLocalOperation(replica), storageHelper, dynamoService));
        }

        List<Map<String, Value>> results = new ArrayList<Map<String, Value>>(SimpleDynamoRing.REPLICA_SIZE - 1);
        int successCount = 0;
        for(int i = 0; i < SimpleDynamoRing.REPLICA_SIZE; i++)
        {
            try
            {
                Future<KeyValuesWrapper> future = Utils.poll(ecs, SimpleDynamoService.SO_TIMEOUT);
                KeyValuesWrapper wrapper = future.get();
                if(wrapper.isError())
                    continue;;

                if(wrapper.getKeyValues() != null)
                    results.add(wrapper.getKeyValues());

                successCount++;
            }
            catch (InterruptedException e)
            {
                Log.e(TAG, "[QUERY_NODE] [" + nodeId + "] Timeout occurred while waiting for query response!!!", e);
            }
            catch (Exception e)
            {
                Log.e(TAG, "[QUERY_NODE] [" + nodeId + "] Unexpected error occurred!!!", e);
            }

            if(successCount == SimpleDynamoRing.REPLICA_SIZE - 1)
                break;
        }

        if(successCount < SimpleDynamoRing.REPLICA_SIZE - 1)
        {
            Log.wtf(TAG, "[QUERY_NODE] [" + nodeId + "] Not enough results[" + successCount + "] from the nodes");
            // return null; FIXME: This should not be commented out.
        }

        Map<String, String> keyValuesMap = new HashMap<String, String>();

        Set<String> keys = new HashSet<String>();
        for (Map<String, Value> v : results)
        {
            keys.addAll(v.keySet());
        }

        for (String key : keys)
        {
            int version = -1;
            String value = null;
            for (Map<String, Value> kvs : results)
            {
                Value v = kvs.get(key);
                if(v == null)
                {
                    Log.w(TAG, "[QUERY_NODE] [" + nodeId + "] No value associated with the key = " + key);
                    continue;
                }

                if(v.getVersion() > version)
                {
                    if(version != -1)
                    {
                        Log.w(TAG, "[QUERY_NODE] [" + nodeId + "][VERSION_MISMATCH] key = " + key + " [OLD_VALUE]: value = "
                                + value + ", version = " + version + ", [NEW_VALUE]: value = " + v.getValue() + ", version = " + v.getVersion());
                    }

                    value = v.getValue();
                    version = v.getVersion();
                }
            }

            if(value != null)
            {
                keyValuesMap.put(key, value);
            }
            else
            {
                Log.w(TAG, "[QUERY_NODE] [" + nodeId + "] Key = " + key + " might have been deleted in one replica.");
            }
        }

        Log.i(TAG, "[QUERY_NODE] [" + nodeId + "] keys = " + keyValuesMap.size());

        return keyValuesMap;
    }

    /**
     *
     * @return
     */
    public Cursor queryDynamo()
    {
        Log.i(TAG, "[QUERY_DYNAMO]");

        Map<String, String> keyValues = new HashMap<String, String>();

        for (String nodeId : SimpleDynamoRing.NODES)
        {
            Log.i(TAG, "[QUERY_DYNAMO] Querying the node " + nodeId);
            String[] replicas = SimpleDynamoRing.getReplicas(nodeId);
            Map<String, String> kvs = _queryNode(nodeId, replicas);
            if(kvs == null)
            {
                Log.i(TAG, "[QUERY_DYNAMO] [" + nodeId + "] Could not retrieve the key values");
                return null;
            }

            Log.i(TAG, "[QUERY_DYNAMO] [" + nodeId + "] Number of keys = " + kvs.size());

            keyValues.putAll(kvs);
        }

        Log.i(TAG, "[QUERY_DYNAMO] Total keys = " + keyValues.size());

        return Utils.formCursor(keyValues);
    }

    /**
     *
     * @param key
     * @param nodeId
     * @param replicas
     * @return
     */
    public int deleteSingle(String key, String nodeId, String[] replicas)
    {
        CountDownLatch latch = new CountDownLatch(SimpleDynamoRing.REPLICA_SIZE - 1);

        executor.execute(new DeleteKeyTask(key, nodeId, nodeId, isLocalOperation(nodeId), latch, storageHelper, dynamoService));

        for (String replica : replicas)
        {
            executor.execute(new DeleteKeyTask(key, nodeId, replica, isLocalOperation(replica), latch, storageHelper, dynamoService));
        }

        // Wait for twice the socket timeout
        try
        {
            Utils.await(latch, SimpleDynamoService.SO_TIMEOUT * 2);
        }
        catch (InterruptedException e)
        {
            Log.e(TAG, "[DELETE_KEY] [" + key + "] Timeout occurred while waiting for deletion!!!");
            return 0;
        }

        return 1;
    }

    /**
     *
     * @param nodeId
     * @param replicas
     * @return
     */
    public int deleteNodeData(String nodeId, String[] replicas)
    {
        Log.i(TAG, "[DELETE_NODE] [" + nodeId + "]");

        ExecutorCompletionService<DeletionWrapper> ecs = new ExecutorCompletionService<DeletionWrapper>(executor);

        ecs.submit(new DeleteAllLocalKeysTask(nodeId, nodeId, isLocalOperation(nodeId), storageHelper, dynamoService));

        for (String replica : replicas)
        {
            ecs.submit(new DeleteAllLocalKeysTask(nodeId, replica, isLocalOperation(replica), storageHelper, dynamoService));
        }

        int deletionCount = 0;
        int successCount = 0;
        for(int i = 0; i < SimpleDynamoRing.REPLICA_SIZE; i++)
        {
            try
            {
                Future<DeletionWrapper> future = ecs.poll(SimpleDynamoService.SO_TIMEOUT / 1000, TimeUnit.SECONDS);
                DeletionWrapper wrapper = future.get();
                if(wrapper.isError())
                    continue;;

                deletionCount = Math.max(deletionCount, wrapper.getDeletionCount());
                successCount++;
            }
            catch (InterruptedException e)
            {
                Log.e(TAG, "[DELETE_NODE] [" + nodeId + "] Timeout occurred while waiting for query response!!!");
            }
            catch (ExecutionException e)
            {
                Log.e(TAG, "[DELETE_NODE] [" + nodeId + "] Unexpected error occurred!!!");
            }

            if(successCount == SimpleDynamoRing.REPLICA_SIZE - 1)
                break;
        }

        Log.i(TAG, "[DELETE_NODE] [" + nodeId + "] keys = " + deletionCount);

        return deletionCount;
    }

    /**
     *
     * @return
     */
    public int deleteEntireDynamo()
    {
        Log.i(TAG, "[DELETE_DYNAMO]");

        int deletionCount = 0;

        for (String nodeId : SimpleDynamoRing.NODES)
        {
            Log.i(TAG, "[DELETE_DYNAMO] Querying the node " + nodeId);

            String[] replicas = SimpleDynamoRing.getReplicas(nodeId);
            int c = deleteNodeData(nodeId, replicas);

            Log.i(TAG, "[DELETE_DYNAMO] [" + nodeId + "] Number of keys = " + c);

            deletionCount += c;
        }

        Log.i(TAG, "[QUERY_DYNAMO] Total keys = " + deletionCount);

        return deletionCount;
    }
}
