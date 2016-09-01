package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentUris;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.net.Socket;

import static edu.buffalo.cse.cse486586.simpledht.Utils.genHash;
import static edu.buffalo.cse.cse486586.simpledht.Utils.readJson;
import static edu.buffalo.cse.cse486586.simpledht.Utils.sendJson;

/**
 * Created by prasanth on 3/18/16.
 */
public class DeleteSingleKeyTask extends AsyncTask<Void, Void, Integer>
{
    private static final String TAG = DeleteSingleKeyTask.class.getName();

    private final KeyValueStorageHelper storageHelper;
    private final String myId;
    private final String key;

    public DeleteSingleKeyTask(KeyValueStorageHelper storageHelper, String myId, String key)
    {
        this.storageHelper = storageHelper;
        this.myId = myId;
        this.key = key;
    }

    @Override
    protected Integer doInBackground(Void... params)
    {
        try
        {
            String hash = genHash(key);
            Log.i(TAG, "[DELETE_KEY] [" + key + "] hash = " + hash);

            if(Utils.isWithinMyBound(hash, myId))
            {
                Log.i(TAG, "[DELETE_KEY] [" + key + "] I am responsible for deleting this key.");

                int deletedCount = storageHelper.delete(key);
                if(deletedCount > 0)
                    Log.i(TAG, "[DELETE_KEY] [" + key + "] Successfully deleted.");
                else
                    Log.i(TAG, "[DELETE_KEY] [" + key + "] Key might not be present in the storage.");

                return deletedCount;
            }
            else // Forward the request to the successor
            {
                Log.i(TAG, "[DELETE_KEY] [" + key + "] Querying the successor " + Neighbors.getInstance().getSuccessorPort());

                JSONObject deleteReqObj = Utils.formDelKeyRequest(key);
                Socket successorSocket = Neighbors.getInstance().getSuccessorSocket();
                sendJson(successorSocket, deleteReqObj);

                JSONObject deleteResp = readJson(successorSocket);
                successorSocket.close();

                Log.i(TAG, "[DELETE_KEY] [" + key + "] Response from the successor " + Neighbors.getInstance().getSuccessorPort()
                        + " = " + deleteResp);

                if(!Keys._OK.equals(deleteResp.getString(Keys.RESPONSE)))
                    return -1;

                return deleteResp.getInt(Keys.DELETED_KEYS_COUNT);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "[DELETE_KEY] [" + key + "] Can't delete the key.", e);
            return -1;
        }
    }
}
