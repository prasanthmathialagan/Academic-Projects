package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentUris;
import android.net.Uri;
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
public class InsertKeyRequestTask extends AsyncTask<Void, Void, Uri>
{
    private static final String TAG = InsertKeyRequestTask.class.getName();

    private final KeyValueStorageHelper storageHelper;
    private final Uri uri;
    private final String myId;
    private final String key;
    private final String value;

    public InsertKeyRequestTask(KeyValueStorageHelper storageHelper, Uri uri, String myId, String key, String value)
    {
        this.storageHelper = storageHelper;
        this.uri = uri;
        this.myId = myId;
        this.key = key;
        this.value = value;
    }

    @Override
    protected Uri doInBackground(Void... params)
    {
        try
        {
            String hash = genHash(key);
            Log.i(TAG, "[INSERT_KEY] [" + key + "] hash = " + hash + ", value = " + value);

            if(Utils.isWithinMyBound(hash, myId))
            {
                Log.i(TAG, "[INSERT_KEY] [" + key + "] I am responsible for storing this key.");

                if(!storageHelper.insertOrUpdate(key,value))
                {
                    Log.e(TAG, "[INSERT_KEY] [" + key + "] Can't insert the key.");
                    return ContentUris.withAppendedId(uri, Codes.CODE_INSERT_FAILED);
                }
                else
                {
                    Log.i(TAG, "[INSERT_KEY] [" + key + "] Insertion successful.");
                }
            }
            else // Forward the request to the successor
            {
                Log.i(TAG, "[INSERT_KEY] [" + key + "] Querying the successor " + Neighbors.getInstance().getSuccessorPort());

                JSONObject insertReqObj = Utils.formInsertKeyRequest(key, value);
                Socket successorSocket = Neighbors.getInstance().getSuccessorSocket();
                sendJson(successorSocket, insertReqObj);

                JSONObject insertResp = readJson(successorSocket);
                successorSocket.close();

                Log.i(TAG, "[INSERT_KEY] [" + key + "] Response from the successor " + Neighbors.getInstance().getSuccessorPort()
                        + " = " + insertResp);

                if(!Keys._OK.equals(insertResp.getString(Keys.RESPONSE)))
                    return ContentUris.withAppendedId(uri, Codes.CODE_INSERT_FAILED);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "[INSERT_KEY] [" + key + "] Can't insert the key.", e);
            return ContentUris.withAppendedId(uri, Codes.CODE_INSERT_FAILED);
        }

        return ContentUris.withAppendedId(uri, Codes.CODE_OK);
    }
}
