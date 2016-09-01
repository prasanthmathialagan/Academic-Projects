package edu.buffalo.cse.cse486586.simpledht;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.net.Socket;

import static edu.buffalo.cse.cse486586.simpledht.Utils.readJson;
import static edu.buffalo.cse.cse486586.simpledht.Utils.sendJson;

/**
 * Created by prasanth on 3/18/16.
 */
public class DeleteAllKeyTask extends AsyncTask<Void, Void, Integer>
{
    private static final String TAG = DeleteAllKeyTask.class.getName();

    private final String myId;
    private final String initiator;
    private final KeyValueStorageHelper storageHelper;

    public DeleteAllKeyTask(String myId, String initiator, KeyValueStorageHelper storageHelper)
    {
        this.myId = myId;
        this.initiator = initiator;
        this.storageHelper = storageHelper;
    }

    @Override
    protected Integer doInBackground(Void... params)
    {
        Log.i(TAG, "[DELETE_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Deleting all the local keys");

        // Delete all the messages present in the local partition
        int deletedCount = storageHelper.deleteAll();
        Log.i(TAG, "[DELETE_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Deleted local keys = " + deletedCount);

        if(initiator.equals(Neighbors.getInstance().getSuccessorPort()))
        {
            Log.i(TAG, "[DELETE_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Successor is the initiator. Hence not querying further.");
            return deletedCount;
        }

        Log.i(TAG, "[DELETE_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Querying the successor " + Neighbors.getInstance().getSuccessorPort());

        try
        {
            JSONObject delAllKeysRequest = Utils.formDelAllKeysRequest(initiator, false);
            Socket successorSocket = Neighbors.getInstance().getSuccessorSocket();
            sendJson(successorSocket, delAllKeysRequest);

            JSONObject delAllKeysResponse = readJson(successorSocket);
            successorSocket.close();

            if(!Keys._OK.equals(delAllKeysResponse.getString(Keys.RESPONSE)))
            {
                Log.e(TAG, "[DELETE_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Could not delete keys in the successor.");
                return -1;
            }

            int succDeletedCount = delAllKeysResponse.getInt(Keys.DELETED_KEYS_COUNT);
            Log.i(TAG, "[DELETE_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Successor deleted keys count = " + succDeletedCount);

            deletedCount += succDeletedCount;
            Log.i(TAG, "[DELETE_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Final deleted keys count = " + deletedCount);

            return deletedCount;
        }
        catch (Exception e)
        {
            Log.e(TAG, "[DELETE_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Error occurred while deleting keys in the successor.", e);
            return -1;
        }
    }
}
