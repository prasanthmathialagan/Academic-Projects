package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static edu.buffalo.cse.cse486586.simpledht.Utils.formDelAllKeysResponse;
import static edu.buffalo.cse.cse486586.simpledht.Utils.formDelKeyResponse;
import static edu.buffalo.cse.cse486586.simpledht.Utils.formGetAllKeysResponse;
import static edu.buffalo.cse.cse486586.simpledht.Utils.formGetKeyResponse;
import static edu.buffalo.cse.cse486586.simpledht.Utils.formInsertKeyResponse;
import static edu.buffalo.cse.cse486586.simpledht.Utils.formJoinResponse;
import static edu.buffalo.cse.cse486586.simpledht.Utils.formUpdateSuccessorReq;
import static edu.buffalo.cse.cse486586.simpledht.Utils.formUpdateSuccessorResp;
import static edu.buffalo.cse.cse486586.simpledht.Utils.genHash;
import static edu.buffalo.cse.cse486586.simpledht.Utils.readJson;
import static edu.buffalo.cse.cse486586.simpledht.Utils.sendJson;

public class SimpleDhtProvider extends ContentProvider {

    private static final String TAG = SimpleDhtProvider.class.getName();

    private static final int SERVER_PORT = 10000;

    public static final String LEADER_NODE = "11108";

    private KeyValueStorageHelper storageHelper;

    private String myPort;
    private String myId;

    private boolean standalone = true;

    private boolean isLeader()
    {
        return LEADER_NODE.equals(myPort);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs)
    {
        Log.i(TAG, "[LOCAL_DELETE] selection = " + selection);

        String queryType;
        if(Keys._STAR.equals(selection))
        {
            queryType = Operations.DEL_ALL_DHT_REQUEST;
        }
        else if(Keys._AT.equals(selection))
        {
            queryType = Operations.DEL_ALL_LOCAL_REQUEST;
        }
        else
        {
            queryType = Operations.DELETE_SINGLE_KEY_REQUEST;
        }

        return _delete(uri, selection, selectionArgs, myPort, queryType);
    }

    /**
     *      This method contains the logic for deletion
     *
     * @param uri
     * @param selection
     * @param selectionArgs
     * @param initiator
     * @param queryType
     * @return
     */
    private int _delete(Uri uri, String selection, String[] selectionArgs, String initiator, String queryType)
    {
        Log.i(TAG, "[DELETE] type = " + queryType + ", initiator = " + initiator + ", selection = " + selection);

        // In case of standalone
        if(standalone)
        {
            Log.i(TAG, "[DELETE] [STANDALONE] selection = " + selection);
            if(Operations.DELETE_SINGLE_KEY_REQUEST.equals(queryType))
            {
                return storageHelper.delete(selection);
            }
            else // for "*" and "@"
            {
                return storageHelper.deleteAll();
            }
        }

        if(Operations.DEL_ALL_LOCAL_REQUEST.equals(queryType))
        {
            return storageHelper.deleteAll();
        }
        else if(Operations.DEL_ALL_DHT_REQUEST.equals(queryType))
        {
            AsyncTask<Void, Void, Integer> result = new DeleteAllKeyTask(myId, initiator, storageHelper).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            try
            {
                return result.get();
            }
            catch (Exception e)
            {
                Log.e(TAG, "[DELETE] [DEL_ALL_DHT_REQUEST] [ERROR]", e);
                return -1;
            }
        }
        else
        {
            AsyncTask<Void, Void, Integer> result = new DeleteSingleKeyTask(storageHelper, myId, selection).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            try
            {
                return result.get();
            }
            catch (Exception e)
            {
                Log.e(TAG, "[DELETE] [DEL_KEY] [ERROR]", e);
                return -1;
            }
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        String opType = values.getAsString(Keys.OPERATION_TYPE);

        if(Operations.INIT.equals(opType))
        {
            Log.i(TAG, "[INIT] Initializing the Simple DHT content provider");

            storageHelper = new KeyValueStorageHelper(getContext());
            myPort = values.getAsString(Keys.PORT_STR);

            Log.i(TAG, "[INIT] My port is " + myPort);

            try
            {
                myId = Utils.getIdFromPort(myPort);
            }
            catch (NoSuchAlgorithmException e)
            {
                Log.e(TAG, "[INIT] Can't generate hash", e);
                return ContentUris.withAppendedId(uri, Codes.CODE_INIT_FAILED);
            }

            Log.i(TAG, "[INIT] My ID is " + myId);

            try
            {
                ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
                new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            }
            catch (IOException e)
            {
                Log.e(TAG, "[INIT] Can't create a ServerSocket", e);
                return ContentUris.withAppendedId(uri, Codes.CODE_INIT_FAILED);
            }

            if(isLeader())
            {
                Log.i(TAG, "[INIT] I am the leader. So, not making JOIN request.");
                try
                {
                    Neighbors.getInstance().setPredecessorPort(myPort);
                    Neighbors.getInstance().setSuccessorPort(myPort);
                }
                catch (NoSuchAlgorithmException e)
                {
                    Log.e(TAG, "[INIT] Can't generate hash", e);
                    return ContentUris.withAppendedId(uri, Codes.CODE_INIT_FAILED);
                }
            }
            else
            {
                AsyncTask<Void, Void, Boolean> result = new JoinRequestTask(myPort, storageHelper).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);

                try
                {
                    if(!result.get())
                    {
                        Log.w(TAG, "[INIT] Could not join the DHT. So, running the application standalone.");
                        return ContentUris.withAppendedId(uri, Codes.STANDALONE_INIT);
                    }
                }
                catch (Exception e)
                {
                    Log.e(TAG, "[INIT] Can't make JOIN request.", e);
                    return ContentUris.withAppendedId(uri, Codes.CODE_INIT_FAILED);
                }
            }

            standalone = false;

            Log.i(TAG, "[INIT] Simple DHT content provider initialized.");
        }
        else // Insert a key
        {
            String key = values.getAsString(KeyValueStorageHelper.COLUMN_KEY);
            String value = values.getAsString(KeyValueStorageHelper.COLUMN_VALUE);

            if(standalone)
            {
                Log.i(TAG, "[INSERT_KEY] [" + key + "] [STANDALONE] value = " + value);
                if(!storageHelper.insertOrUpdate(key, value))
                {
                    Log.w(TAG, "[INSERT_KEY] [" + key + "] [STANDALONE] Can't insert the key.");
                    return ContentUris.withAppendedId(uri, Codes.CODE_INSERT_FAILED);
                }
            }
            else
            {
                AsyncTask<Void, Void, Uri> result = new InsertKeyRequestTask(storageHelper, uri, myId, key, value).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);

                try
                {
                    return result.get();
                }
                catch (Exception e)
                {
                    Log.e(TAG, "[INSERT_KEY] [" + key + "] Can't insert the key.", e);
                    return ContentUris.withAppendedId(uri, Codes.CODE_INSERT_FAILED);
                }
            }
        }

        return ContentUris.withAppendedId(uri, Codes.CODE_OK);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
    {
        Log.i(TAG, "[LOCAL_QUERY] selection = " + selection);

        String queryType;
        if(Keys._STAR.equals(selection))
        {
            queryType = Operations.GET_ALL_DHT_REQUEST;
        }
        else if(Keys._AT.equals(selection))
        {
            queryType = Operations.GET_ALL_LOCAL_REQUEST;
        }
        else
        {
            queryType = Operations.GET_SINGLE_KEY_REQUEST;
        }

        return _query(uri, projection, selection, selectionArgs, sortOrder, myPort, queryType);
    }

    /**
     *  This method contains the logic for query
     *
     * @param uri
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @param initiator
     * @param queryType
     * @return
     */
    private Cursor _query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder,
                          String initiator, String queryType)
    {
        Log.i(TAG, "[QUERY] type = " + queryType + ", initiator = " + initiator + ", selection = " + selection);

        // In case of standalone
        if(standalone)
        {
            Log.i(TAG, "[QUERY] [STANDALONE] selection = " + selection);
            if(Operations.GET_SINGLE_KEY_REQUEST.equals(queryType))
            {
                return storageHelper.getDataForKey(selection);
            }
            else // for "*" and "@"
            {
                return storageHelper.getAllData();
            }
        }

        if(Operations.GET_ALL_LOCAL_REQUEST.equals(queryType))
        {
            return storageHelper.getAllData();
        }
        else if(Operations.GET_ALL_DHT_REQUEST.equals(queryType))
        {
            AsyncTask<Void, Void, Map<String, String>> result = new GetAllKeyRequestTask(myId, initiator, storageHelper).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            try
            {
                Map<String, String> keyValues = result.get();
                if(keyValues == null)
                    return null;
                return Utils.formCursor(keyValues);
            }
            catch (Exception e)
            {
                Log.e(TAG, "[QUERY] [GET_ALL_DHT_REQUEST] [ERROR]", e);
                return null;
            }
        }
        else
        {
            AsyncTask<Void, Void, String> result = new GetKeyRequestTask(storageHelper, myId, selection).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            try
            {
                String value = result.get();
                if(value == null)
                {
                    Log.i(TAG, "[QUERY] [GET_KEY] [" + selection + "] Creating an empty cursor since the value is null");
                    return Utils.getMatrixCursor();
                }

                return Utils.formCursor(Collections.singletonMap(selection, value));
            }
            catch (Exception e)
            {
                Log.e(TAG, "[QUERY] [GET_KEY] [ERROR] " + selection, e);
                return null;
            }
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private Map<String, String> getKeysToUpdateForJoin() throws NoSuchAlgorithmException
    {
        String header = "[JOIN_REQUEST]";

        // Transfer the keys for which the new node must be responsible
        Cursor cursor = storageHelper.getAllData();
        Map<String, String> keysToUpdate = Utils.getKeyValues(cursor);
        cursor.close();

        Log.i(TAG, header + " Total keys = " + keysToUpdate.size());
        if(!keysToUpdate.isEmpty())
        {
            Set<String> keysToDelete = new HashSet<String>();
            for (Iterator<Map.Entry<String, String>> iter = keysToUpdate.entrySet().iterator(); iter.hasNext();)
            {
                Map.Entry<String, String> entry = iter.next();

                String key = entry.getKey();
                String hash = genHash(key);

                if(!Utils.isWithinMyBound(hash, myId))
                {
                    Log.i(TAG, header + " Key " + key + ", hash = " + hash + " is not within my bound anymore.");
                    iter.remove();
                    keysToDelete.add(key);
                }
            }

            Log.i(TAG, header + " Keys to delete = " + keysToDelete.size());

            int deletedCount = storageHelper.deleteKeys(keysToDelete);
            Log.i(TAG, header + " Deleted keys count = " + deletedCount);

            Log.i(TAG, header + " Keys to be sent to the new node = " + keysToUpdate.size());
        }

        return keysToUpdate;
    }

    public class ServerTask extends AsyncTask<ServerSocket, Socket, Void>
    {
        private final String TAG = ServerTask.class.getName();

        @Override
        protected Void doInBackground(ServerSocket... sockets)
        {
            ServerSocket serverSocket = sockets[0];

            while (!isCancelled())
            {
                try
                {
                    Socket soc = serverSocket.accept();

                    // Client should identify themselves
                    JSONObject json = readJson(soc);

                    String opType = json.getString(Keys.OPERATION_TYPE);
                    Log.i(TAG, "[NEW_CONNECTION] operationType = " + opType);

                    if(Operations.JOIN_REQUEST.equals(opType))
                    {
                        String portStr = json.getString(Keys.PORT_STR);
                        Log.i(TAG, "[JOIN_REQUEST] clientPort = " + portStr);

                        // If you are the leader, then compute the hashed value
                        if(isLeader())
                        {
                            String portId = Utils.getIdFromPort(portStr);
                            Log.i(TAG, "[JOIN_REQUEST] generated hash = " + portId);
                            json.put(Keys.PORT_ID, portId);
                        }

                        JSONObject response;

                        // If the leader is the only node in the DHT
                        if (isLeader() && myPort.equals(Neighbors.getInstance().getPredecessorPort())
                                && myPort.equals(Neighbors.getInstance().getSuccessorPort()))
                        {
                            Log.i(TAG, "[JOIN_REQUEST] Leader is the only node in DHT. So, sending its details.");

                            Neighbors.getInstance().setPredecessorPort(portStr);
                            Neighbors.getInstance().setSuccessorPort(portStr);

                            // Return your id
                            response = formJoinResponse(portStr, myPort, myPort, true, getKeysToUpdateForJoin());
                        }
                        else if(Utils.isWithinMyBound(json.getString(Keys.PORT_ID), myId))
                        {
                            Log.i(TAG, "[JOIN_REQUEST] I am the one responsible for the id. So, sending my details.");

                            String oldPredecessor = Neighbors.getInstance().getPredecessorPort();

                            // In case of leader, then inform the predecessor of the new successor
                            if(isLeader())
                            {
                                Log.i(TAG, "[JOIN_REQUEST] I am the leader. So, informing my old predecessor about its new successor.");

                                JSONObject updateSuccessorReq = formUpdateSuccessorReq(portStr);
                                Socket predecessorSocket = Neighbors.getInstance().getPredecessorSocket();
                                Utils.sendJson(predecessorSocket, updateSuccessorReq);

                                JSONObject updateSuccessorResp = Utils.readJson(predecessorSocket);
                                predecessorSocket.close();

                                if(Keys._OK.equals(updateSuccessorResp.getString(Keys.RESPONSE)))
                                {
                                    Log.i(TAG, "[JOIN_REQUEST] Old predecessor updated its new successor.");
                                }
                                else
                                {
                                    Log.w(TAG, "[JOIN_REQUEST] Old predecessor could not update its new successor.");
                                }
                            }

                            Neighbors.getInstance().setPredecessorPort(portStr);

                            // Return your id
                            response = formJoinResponse(portStr, oldPredecessor, myPort, true, getKeysToUpdateForJoin());
                        }
                        else // Query the successor
                        {
                            Log.i(TAG, "[JOIN_REQUEST] Querying the successor " + Neighbors.getInstance().getSuccessorPort());

                            Socket successorSocket = Neighbors.getInstance().getSuccessorSocket();
                            sendJson(successorSocket, json);

                            response = readJson(successorSocket);
                            successorSocket.close();

                            Log.i(TAG, "[JOIN_REQUEST] Response from the successor " + Neighbors.getInstance().getSuccessorPort() + " = " + response);

                            if(Keys._OK.equals(response.getString(Keys.RESPONSE)))
                            {
                                String predecInResponse = response.getString(Keys.PREDECESSOR_PORT);
                                if(myPort.equals(predecInResponse))
                                {
                                    Log.i(TAG, "[JOIN_REQUEST] I am the new predecessor for " + portStr +". So updating my successor.");
                                    Neighbors.getInstance().setSuccessorPort(portStr);
                                }
                            }
                        }

                        // Send the node to which the client should contact
                        sendJson(soc, response);
                    }
                    else if(Operations.UPDATE_SUCCESSOR_REQUEST.equals(opType))
                    {
                        String successor = json.getString(Keys.SUCCESSOR_PORT);

                        Neighbors.getInstance().setSuccessorPort(successor);
                        Log.i(TAG, "[UPDATE_SUCCESSOR_REQUEST] I am the new predecessor for " + successor + ", So updating my successor.");

                        sendJson(soc, formUpdateSuccessorResp(true));
                    }
                    else if(Operations.INSERT_KEY_REQUEST.equals(opType))
                    {
                        String key = json.getString(KeyValueStorageHelper.COLUMN_KEY);
                        Log.i(TAG, "[INSERT_KEY_REQUEST] [" + key + "] [START]");

                        ContentValues cv = new ContentValues();
                        cv.put(KeyValueStorageHelper.COLUMN_KEY, key);
                        cv.put(KeyValueStorageHelper.COLUMN_VALUE, json.getString(KeyValueStorageHelper.COLUMN_VALUE));
                        Uri resUri = insert(Utils.buildUri(), cv);

                        String code = resUri.getLastPathSegment();
                        Log.i(TAG, "[INSERT_KEY_RESPONSE] [" + key + "]" + code);

                        sendJson(soc, formInsertKeyResponse(json.getString(KeyValueStorageHelper.COLUMN_KEY), Integer.parseInt(code) == Codes.CODE_OK));

                        Log.i(TAG, "[INSERT_KEY_REQUEST] [" + key + "] [END]");
                    }
                    else if(Operations.GET_ALL_DHT_REQUEST.equals(opType) || Operations.GET_ALL_LOCAL_REQUEST.equals(opType))
                    {
                        String initiator = json.getString(Keys.INITIATOR);
                        boolean local = Operations.GET_ALL_LOCAL_REQUEST.equals(opType);
                        String header = local ? ("[GET_LOCAL_DHT_REQUEST] [" + initiator + "]") : ("[GET_ALL_DHT_REQUEST] [" + initiator + "]");
                        Log.i(TAG, header + " [START]");

                        Cursor cursor = _query(Utils.buildUri(), null, local ? Keys._AT : Keys._STAR, null, null, initiator, opType);
                        Map<String, String> keyValues = cursor == null ? null : Utils.getKeyValues(cursor);
                        if(cursor != null)
                            cursor.close();

                        sendJson(soc, formGetAllKeysResponse(keyValues, keyValues != null, local));
                        Log.i(TAG, header + " [END]");
                    }
                    else if(Operations.GET_SINGLE_KEY_REQUEST.equals(opType))
                    {
                        String key = json.getString(KeyValueStorageHelper.COLUMN_KEY);
                        Log.i(TAG, "[GET_KEY_REQUEST] [" + key + "] [START]");

                        String value = null;

                        Cursor cursor = _query(Utils.buildUri(), null, key, null, null, null, Operations.GET_SINGLE_KEY_REQUEST);
                        if(cursor.moveToFirst())
                           value = cursor.getString(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VALUE));
                        cursor.close();

                        Log.i(TAG, "[GET_KEY_REQUEST] [" + key + "] Value = " + value);

                        sendJson(soc, formGetKeyResponse(key, value));
                        Log.i(TAG, "[GET_KEY_REQUEST] [" + key + "] [END]");
                    }
                    else if(Operations.DEL_ALL_DHT_REQUEST.equals(opType) || Operations.DEL_ALL_LOCAL_REQUEST.equals(opType))
                    {
                        String initiator = json.getString(Keys.INITIATOR);
                        boolean local = Operations.DEL_ALL_LOCAL_REQUEST.equals(opType);
                        String header = local ? ("[DELETE_LOCAL_DHT_REQUEST] [" + initiator + "]") : ("[DELETE_ALL_DHT_REQUEST] [" + initiator + "]");
                        Log.i(TAG, header + " [START]");

                        int delCount = _delete(Utils.buildUri(), local ? Keys._AT : Keys._STAR, null, initiator, opType);
                        boolean success = delCount >= 0;

                        sendJson(soc, formDelAllKeysResponse(success ? delCount : 0, success, local));
                        Log.i(TAG, header + " [END]");
                    }
                    else if(Operations.DELETE_SINGLE_KEY_REQUEST.equals(opType))
                    {
                        String key = json.getString(KeyValueStorageHelper.COLUMN_KEY);
                        Log.i(TAG, "[DELETE_KEY_REQUEST] [" + key + "] [START]");

                        int deletedCount = _delete(Utils.buildUri(), key, null, null, Operations.DELETE_SINGLE_KEY_REQUEST);
                        boolean success = deletedCount >= 0;

                        sendJson(soc, formDelKeyResponse(key, success ? deletedCount : 0, success));
                        Log.i(TAG, "[DELETE_KEY_REQUEST] [" + key + "] [END]");
                    }

                    soc.close();
                }
                catch (Exception e)
                {
                    Log.e(TAG, "Error occurred!!", e);
                }
            }

            return null;
        }
    }
}
