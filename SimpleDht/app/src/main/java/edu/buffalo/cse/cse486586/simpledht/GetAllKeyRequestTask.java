package edu.buffalo.cse.cse486586.simpledht;

import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static edu.buffalo.cse.cse486586.simpledht.Utils.readJson;
import static edu.buffalo.cse.cse486586.simpledht.Utils.sendJson;

/**
 * Created by prasanth on 3/17/16.
 */
public class GetAllKeyRequestTask extends AsyncTask<Void, Void, Map<String, String>>
{
    private static final String TAG = GetAllKeyRequestTask.class.getName();

    private final String myId;
    private final String initiator;
    private final KeyValueStorageHelper storageHelper;

    public GetAllKeyRequestTask(String myId, String initiator, KeyValueStorageHelper storageHelper)
    {
        this.myId = myId;
        this.initiator = initiator;
        this.storageHelper = storageHelper;
    }

    @Override
    protected Map<String, String> doInBackground(Void... params)
    {
        Log.i(TAG, "[GET_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Fetching all the local keys");

        Map<String, String> keyValues = new HashMap<String, String>();

        // Get all the messages present in the local partition
        Cursor cursor = storageHelper.getAllData();
        Utils.fillFromCursor(cursor, keyValues);
        cursor.close();

        Log.i(TAG, "[GET_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Local keys count = " + keyValues.size());

        if(initiator.equals(Neighbors.getInstance().getSuccessorPort()))
        {
            Log.i(TAG, "[GET_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Successor is the initiator. Hence not querying further.");
        }
        else
        {
            Log.i(TAG, "[GET_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Querying the successor " + Neighbors.getInstance().getSuccessorPort());

            try
            {
                JSONObject getAllKeysRequest = Utils.formGetAllKeysRequest(initiator, false);
                Socket successorSocket = Neighbors.getInstance().getSuccessorSocket();
                sendJson(successorSocket, getAllKeysRequest);

                JSONObject getAllKeysResponse = readJson(successorSocket);
                successorSocket.close();

                if(!Keys._OK.equals(getAllKeysResponse.getString(Keys.RESPONSE)))
                {
                    Log.e(TAG, "[GET_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Could not retrieve keys from the successor.");
                    return null;
                }

                Map<String, String> successorKeyValues = Utils.extractKeyValues(getAllKeysResponse);
                Log.i(TAG, "[GET_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Successor keys count = " + successorKeyValues.size());

                keyValues.putAll(successorKeyValues);
            }
            catch (Exception e)
            {
                Log.e(TAG, "[GET_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Error occurred while retrieving keys from the successor.", e);
                return null;
            }
        }

        Log.i(TAG, "[GET_ALL_DHT_KEYS] [" + initiator + "] [" + myId + "] Final keys count = " + keyValues.size());

        return keyValues;
    }
}
