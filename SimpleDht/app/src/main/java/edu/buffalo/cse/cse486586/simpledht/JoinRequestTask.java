package edu.buffalo.cse.cse486586.simpledht;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.net.Socket;
import java.util.Map;

import static edu.buffalo.cse.cse486586.simpledht.Utils.sendJson;

/**
 * Created by prasanth on 3/17/16.
 */
public class JoinRequestTask extends AsyncTask<Void, Void, Boolean>
{
    private static final String TAG = JoinRequestTask.class.getName();

    private final String myPort;
    private final KeyValueStorageHelper storageHelper;

    public JoinRequestTask(String myPort, KeyValueStorageHelper storageHelper)
    {
        this.myPort = myPort;
        this.storageHelper = storageHelper;
    }

    @Override
    protected Boolean doInBackground(Void... params)
    {
        try
        {
            // Send join request to the leader
            Log.i(TAG, "[INIT] Making JOIN request to the leader " + SimpleDhtProvider.LEADER_NODE);
            JSONObject joinRequest = Utils.formJoinRequest(myPort);
            Socket leaderSocket = Utils.newSocket(SimpleDhtProvider.LEADER_NODE);
            sendJson(leaderSocket, joinRequest);

            JSONObject joinResponse = Utils.readJson(leaderSocket);
            leaderSocket.close();
            Log.i(TAG, "[INIT] JOIN response from the leader " + joinResponse);

            if(!Keys._OK.equals(joinResponse.get(Keys.RESPONSE)))
            {
                Log.e(TAG, "[INIT] Could not join the DHT.");
                return false;
            }

            Neighbors.getInstance().setPredecessorPort(joinResponse.getString(Keys.PREDECESSOR_PORT));
            Neighbors.getInstance().setSuccessorPort(joinResponse.getString(Keys.SUCCESSOR_PORT));

            // Transferring the messages pending
            Map<String, String> keyValues = Utils.extractKeyValues(joinResponse);
            Log.i(TAG, "[INIT] Keys to be inserted = " + keyValues.size());

            if(!keyValues.isEmpty())
            {
                synchronized (storageHelper)
                {
                    for (Map.Entry<String, String> entry: keyValues.entrySet())
                    {
                        String key = entry.getKey();
                        String value = entry.getValue();

                        storageHelper.insertOrUpdate(key,value);
                    }
                }
            }

        }
        catch (Exception e)
        {
            Log.e(TAG, "[INIT] Can't make JOIN request.", e);
            return false;
        }

        return true;
    }
}
