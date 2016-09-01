package edu.buffalo.cse.cse486586.simpledht;

import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.net.Socket;

import static edu.buffalo.cse.cse486586.simpledht.Utils.genHash;
import static edu.buffalo.cse.cse486586.simpledht.Utils.readJson;
import static edu.buffalo.cse.cse486586.simpledht.Utils.sendJson;

/**
 * Created by prasanth on 3/17/16.
 */
public class GetKeyRequestTask extends AsyncTask<Void, Void, String>
{
    private static final String TAG = GetKeyRequestTask.class.getName();

    private final KeyValueStorageHelper storageHelper;
    private final String myId;
    private final String key;

    public GetKeyRequestTask(KeyValueStorageHelper storageHelper, String myId, String key)
    {
        this.storageHelper = storageHelper;
        this.myId = myId;
        this.key = key;
    }

    @Override
    protected String doInBackground(Void... params)
    {
        try
        {
            String hash = genHash(key);
            Log.i(TAG, "[GET_KEY] [" + key + "] hash = " + hash);

            if(Utils.isWithinMyBound(hash, myId))
            {
                Log.i(TAG, "[GET_KEY] [" + key + "] I am responsible for this key.");

                Cursor cursor = storageHelper.getDataForKey(key);

                try
                {
                    if(!cursor.moveToFirst())
                    {
                        Log.i(TAG, "[GET_KEY] [" + key + "] Not present in the storage.");
                        return null;
                    }

                    return cursor.getString(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VALUE));
                }
                finally
                {
                    cursor.close();
                }
            }
            else // Forward the request to the successor
            {
                Log.i(TAG, "[GET_KEY] [" + key + "] Querying the successor " + Neighbors.getInstance().getSuccessorPort());

                JSONObject getKeyReqObj = Utils.formGetKeyRequest(key);
                Socket successorSocket = Neighbors.getInstance().getSuccessorSocket();
                sendJson(successorSocket, getKeyReqObj);

                JSONObject getKeyRespObj = readJson(successorSocket);
                successorSocket.close();

                Log.i(TAG, "[GET_KEY] [" + key + "] Response from the successor " + Neighbors.getInstance().getSuccessorPort()
                        + " = " + getKeyRespObj);

                return getKeyRespObj.optString(KeyValueStorageHelper.COLUMN_VALUE, null);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "[GET_KEY] [" + key + "] Can't get the value for the key.");
            return null;
        }
    }
}
