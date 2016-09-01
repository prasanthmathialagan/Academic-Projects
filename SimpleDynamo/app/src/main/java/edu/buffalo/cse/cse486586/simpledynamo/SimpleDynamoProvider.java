package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;

import edu.buffalo.cse.cse486586.simpledynamo.service.CRUDHandler;
import edu.buffalo.cse.cse486586.simpledynamo.service.SimpleDynamoService;

/**
 *
 */
public class SimpleDynamoProvider extends ContentProvider
{
    private static final String TAG = SimpleDynamoProvider.class.getSimpleName();

    private SimpleDynamoService dynamoService;
    private KeyValueStorageHelper storageHelper;
    private CRUDHandler crudHandler;

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs)
    {
        Log.i(TAG, "[QUERY] selection = " + selection);

        if(CRUDHandler.STAR_QUERY1.equals(selection) || CRUDHandler.STAR_QUERY2.equals(selection))
        {
            return crudHandler.deleteEntireDynamo();
        }
        else if(CRUDHandler.AT_QUERY1.equals(selection) || CRUDHandler.AT_QUERY2.equals(selection))
        {
            String[] replicas = SimpleDynamoRing.getReplicas(dynamoService.getCurrentNodeId());
            return crudHandler.deleteNodeData(dynamoService.getCurrentNodeId(), replicas);
        }
        else
        {
            // Get the node responsible for the key
            String nodeId = SimpleDynamoRing.getNodeResponsible(selection);
            String[] replicas = SimpleDynamoRing.getReplicas(nodeId);
            return crudHandler.deleteSingle(selection, nodeId, replicas);
        }
    }

    @Override
    public String getType(Uri uri)
    {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values)
    {
        String key = values.getAsString(KeyValueStorageHelper.COLUMN_KEY);
        String value = values.getAsString(KeyValueStorageHelper.COLUMN_VALUE);

        Log.i(TAG, "[INSERT_START] key = " + key + ", value = " + value);

        // Get the node responsible for the key
        String nodeId = SimpleDynamoRing.getNodeResponsible(key);
        String[] replicas = SimpleDynamoRing.getReplicas(nodeId);

        try
        {
            return crudHandler.insert(key, value, nodeId, replicas);
        }
        finally
        {
            Log.i(TAG, "[INSERT_END] key = " + key + ", value = " + value);
        }
    }

    @Override
    public boolean onCreate()
    {
        // Find the application port and ID
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String id = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);

        storageHelper = new KeyValueStorageHelper(getContext());
        dynamoService = new SimpleDynamoService(id, storageHelper);
        crudHandler = new CRUDHandler(dynamoService, storageHelper);

        return dynamoService.start();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
    {
        Log.i(TAG, "[QUERY_START] selection = " + selection);

        try
        {
            if(CRUDHandler.STAR_QUERY1.equals(selection) || CRUDHandler.STAR_QUERY2.equals(selection))
            {
                return crudHandler.queryDynamo();
            }
            else if(CRUDHandler.AT_QUERY1.equals(selection) || CRUDHandler.AT_QUERY2.equals(selection))
            {
                return storageHelper.getAllLocalData();
            /*String[] replicas = SimpleDynamoRing.getReplicas(dynamoService.getCurrentNodeId());
            return crudHandler.queryNode(dynamoService.getCurrentNodeId(), replicas);*/
            }
            else
            {
                // Get the node responsible for the key
                String nodeId = SimpleDynamoRing.getNodeResponsible(selection);
                String[] replicas = SimpleDynamoRing.getReplicas(nodeId);
                return crudHandler.querySingle(selection, nodeId, replicas);
            }
        }
        finally
        {
            Log.i(TAG, "[QUERY_END] selection = " + selection);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
    {
        return 0;
    }
}
