package edu.buffalo.cse.cse486586.simpledht;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by prasanth on 3/16/16.
 */
public class Utils
{
    private static final String TAG = Utils.class.getName();

    private Utils()
    {

    }

    public static String genHash(String input) throws NoSuchAlgorithmException
    {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        return formatToHexString(sha1Hash);
    }

    private static String formatToHexString(byte[] bytes)
    {
        Formatter formatter = new Formatter();
        for (byte b : bytes)
        {
            formatter.format("%02x", b);
        }

        try
        {
            return formatter.toString();
        }
        finally
        {
            formatter.close();
        }
    }

    /**
     *
     * @param port
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String getIdFromPort(String port) throws NoSuchAlgorithmException
    {
        int p = Integer.parseInt(port);
        int p_2 = p/2;
        return genHash(p_2 + "");
    }

    /**
     *    Returns true if hash1 is lexicographically greater than hash2.
     *
     * @param hash1
     * @param hash2
     * @return
     */
    public static boolean isGreater(String hash1, String hash2)
    {
        return hash1.compareTo(hash2) > 0;
    }

    /**
     *    Returns true if hash1 is lexicographically less than or equal to hash2.
     *
     * @param hash1
     * @param hash2
     * @return
     */
    public static boolean isLessThanOrEqualTo(String hash1, String hash2)
    {
        return !isGreater(hash1, hash2);
    }

    /**
     *
     * @param soc
     * @param response
     * @throws IOException
     */
    public static void sendJson(Socket soc, JSONObject response) throws IOException
    {
        BufferedWriter wrtr = new BufferedWriter(new OutputStreamWriter(soc.getOutputStream()));
        wrtr.write(response.toString());
        wrtr.newLine();
        wrtr.flush();
    }

    /**
     *
     * @param soc
     * @return
     * @throws IOException
     * @throws JSONException
     */
    public static JSONObject readJson(Socket soc) throws IOException, JSONException
    {
        BufferedReader rdr = new BufferedReader(new InputStreamReader(soc.getInputStream()));
        String recvdMsg = rdr.readLine();
        return new JSONObject(recvdMsg);
    }

    /**
     *
     * @param requester
     * @return
     * @throws JSONException
     */
    public static JSONObject formJoinRequest(String requester) throws JSONException
    {
        JSONObject request = new JSONObject();
        request.put(Keys.OPERATION_TYPE, Operations.JOIN_REQUEST);
        request.put(Keys.PORT_STR, requester);
        return request;
    }

    /**
     *
     * @param requester
     * @param predecessorPort
     * @param successorPort
     * @param ok
     * @param keys
     * @return
     * @throws JSONException
     */
    public static JSONObject formJoinResponse(String requester, String predecessorPort, String successorPort,
                                              boolean ok, Map<String, String> keys) throws JSONException
    {
        JSONObject response = new JSONObject();
        response.put(Keys.OPERATION_TYPE, Operations.JOIN_RESPONSE);
        response.put(Keys.PORT_STR, requester);
        response.put(Keys.PREDECESSOR_PORT, predecessorPort);
        response.put(Keys.SUCCESSOR_PORT, successorPort);
        response.put(Keys.RESPONSE, ok ? Keys._OK : Keys._ERROR);

        // Keys to be inserted at the new node
        if(ok)
        {
            JSONArray jsonArray = getJsonArray(keys);
            response.put(Keys.ALL_KEY_VALUES, jsonArray);
        }

        return response;
    }

    /**
     *
     * @param port
     * @return
     * @throws UnknownHostException
     */
    public static Socket newSocket(String port) throws IOException
    {
        return new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(port));
    }

    /**
     *
     * @return
     */
    public static Uri buildUri()
    {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.simpledht.provider");
        uriBuilder.scheme("content");
        return uriBuilder.build();
    }

    /**
     *
     * @param successor
     * @return
     * @throws JSONException
     */
    public static JSONObject formUpdateSuccessorReq(String successor) throws JSONException
    {
        JSONObject request = new JSONObject();
        request.put(Keys.OPERATION_TYPE, Operations.UPDATE_SUCCESSOR_REQUEST);
        request.put(Keys.SUCCESSOR_PORT, successor);
        return request;
    }

    /**
     *
     * @param ok
     * @return
     * @throws JSONException
     */
    public static JSONObject formUpdateSuccessorResp(boolean ok) throws JSONException
    {
        JSONObject response = new JSONObject();
        response.put(Keys.OPERATION_TYPE, Operations.UPDATE_SUCCESSOR_RESPONSE);
        response.put(Keys.RESPONSE, ok ? Keys._OK : Keys._ERROR);
        return response;
    }

    /**
     *
     * @param key
     * @param value
     * @return
     * @throws JSONException
     */
    public static JSONObject formInsertKeyRequest(String key, String value) throws JSONException
    {
        JSONObject request = new JSONObject();
        request.put(Keys.OPERATION_TYPE, Operations.INSERT_KEY_REQUEST);
        request.put(KeyValueStorageHelper.COLUMN_KEY, key);
        request.put(KeyValueStorageHelper.COLUMN_VALUE, value);
        return request;
    }

    /**
     *
     * @param key
     * @param ok
     * @return
     * @throws JSONException
     */
    public static JSONObject formInsertKeyResponse(String key, boolean ok) throws JSONException
    {
        JSONObject response = new JSONObject();
        response.put(Keys.OPERATION_TYPE, Operations.INSERT_KEY_RESPONSE);
        response.put(KeyValueStorageHelper.COLUMN_KEY, key);
        response.put(Keys.RESPONSE, ok ? Keys._OK : Keys._ERROR);
        return response;
    }

    /**
     *
     * @param initiator
     * @return
     * @throws JSONException
     */
    public static JSONObject formGetAllKeysRequest(String initiator, boolean local) throws JSONException
    {
        JSONObject request = new JSONObject();
        request.put(Keys.OPERATION_TYPE, local ? Operations.GET_ALL_LOCAL_REQUEST : Operations.GET_ALL_DHT_REQUEST);
        request.put(Keys.INITIATOR, initiator);
        return request;
    }

    /**
     *
     * @param keys
     * @param ok
     * @param local
     * @return
     * @throws JSONException
     */
    public static JSONObject formGetAllKeysResponse(Map<String, String> keys, boolean ok, boolean local) throws JSONException
    {
        JSONObject response = new JSONObject();

        response.put(Keys.OPERATION_TYPE, local ? Operations.GET_ALL_LOCAL_RESPONSE : Operations.GET_ALL_DHT_RESPONSE);
        response.put(Keys.RESPONSE, ok ? Keys._OK : Keys._ERROR);

        if(ok)
        {
            JSONArray keysArray = getJsonArray(keys);
            response.put(Keys.ALL_KEY_VALUES, keysArray);
        }

        return response;
    }

    private static JSONArray getJsonArray(Map<String, String> keys) throws JSONException
    {
        JSONArray keysArray = new JSONArray();
        for (Map.Entry<String, String> entry: keys.entrySet())
        {
            keysArray.put(getObject(entry.getKey(), entry.getValue()));
        }
        return keysArray;
    }

    private static JSONObject getObject(String key, String value) throws JSONException
    {
        JSONObject js = new JSONObject();
        js.put(KeyValueStorageHelper.COLUMN_KEY, key);
        js.put(KeyValueStorageHelper.COLUMN_VALUE, value);
        return js;
    }

    /**
     *
     * @return
     */
    public static Map<String, String> extractKeyValues(JSONObject json) throws JSONException
    {
        Map<String, String> keyValues = new HashMap<String, String>();

        JSONArray array = json.getJSONArray(Keys.ALL_KEY_VALUES);
        for (int i = 0; i < array.length(); i++)
        {
            JSONObject o = array.getJSONObject(i);
            keyValues.put(o.getString(KeyValueStorageHelper.COLUMN_KEY), o.getString(KeyValueStorageHelper.COLUMN_VALUE));
        }

        return keyValues;
    }

    /**
     *
     * @param keyValues
     * @return
     */
    public static Cursor formCursor(Map<String, String> keyValues)
    {
        MatrixCursor cursor = getMatrixCursor();
        for (Map.Entry<String, String> entry: keyValues.entrySet())
        {
            cursor.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
        return cursor;
    }

    /**
     *
     * @return
     */
    public static MatrixCursor getMatrixCursor()
    {
        return new MatrixCursor(new String[]{KeyValueStorageHelper.COLUMN_KEY, KeyValueStorageHelper.COLUMN_VALUE});
    }

    /**
     *
     * @param cursor
     * @return
     */
    public static Map<String, String> getKeyValues(Cursor cursor)
    {
        Map<String, String> keyValues = new HashMap<String, String>();
        fillFromCursor(cursor, keyValues);
        return keyValues;
    }

    /**
     * https://examples.javacodegeeks.com/android/core/database/android-cursor-example/
     *
     * @param cursor
     * @param keyValues
     */
    public static void fillFromCursor(Cursor cursor, Map<String, String> keyValues)
    {
        if(cursor.moveToFirst())
        {
            do
            {
                String key = cursor.getString(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_KEY));
                String value = cursor.getString(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VALUE));
                keyValues.put(key, value);
            } while (cursor.moveToNext());
        }
    }

    /**
     *
     * @param key
     * @return
     * @throws JSONException
     */
    public static JSONObject formGetKeyRequest(String key) throws JSONException
    {
        JSONObject request = new JSONObject();
        request.put(Keys.OPERATION_TYPE, Operations.GET_SINGLE_KEY_REQUEST);
        request.put(KeyValueStorageHelper.COLUMN_KEY, key);
        return request;
    }

    /**
     *
     * @param key
     * @param value
     * @return
     * @throws JSONException
     */
    public static JSONObject formGetKeyResponse(String key, String value) throws JSONException
    {
        JSONObject response = new JSONObject();
        response.put(Keys.OPERATION_TYPE, Operations.GET_SINGLE_KEY_RESPONSE);
        response.put(KeyValueStorageHelper.COLUMN_KEY, key);
        response.putOpt(KeyValueStorageHelper.COLUMN_VALUE, value); // This can be null
        return response;
    }

    /**
     *
     * @param initiator
     * @return
     * @throws JSONException
     */
    public static JSONObject formDelAllKeysRequest(String initiator, boolean local) throws JSONException
    {
        JSONObject request = new JSONObject();
        request.put(Keys.OPERATION_TYPE, local ? Operations.DEL_ALL_LOCAL_REQUEST : Operations.DEL_ALL_DHT_REQUEST);
        request.put(Keys.INITIATOR, initiator);
        return request;
    }

    /**
     *
     * @param deletedKeysCount
     * @param ok
     * @param local
     * @return
     * @throws JSONException
     */
    public static JSONObject formDelAllKeysResponse(int deletedKeysCount, boolean ok, boolean local) throws JSONException
    {
        JSONObject response = new JSONObject();

        response.put(Keys.OPERATION_TYPE, local ? Operations.DEL_ALL_LOCAL_RESPONSE : Operations.DEL_ALL_DHT_RESPONSE);
        response.put(Keys.RESPONSE, ok ? Keys._OK : Keys._ERROR);

        if(ok)
        {
            response.put(Keys.DELETED_KEYS_COUNT, deletedKeysCount);
        }

        return response;
    }

    /**
     *
     * @param key
     * @return
     * @throws JSONException
     */
    public static JSONObject formDelKeyRequest(String key) throws JSONException
    {
        JSONObject request = new JSONObject();
        request.put(Keys.OPERATION_TYPE, Operations.DELETE_SINGLE_KEY_REQUEST);
        request.put(KeyValueStorageHelper.COLUMN_KEY, key);
        return request;
    }

    /**
     *
     * @param key
     * @param deletedCount
     * @param ok
     * @return
     * @throws JSONException
     */
    public static JSONObject formDelKeyResponse(String key, int deletedCount, boolean ok) throws JSONException
    {
        JSONObject response = new JSONObject();
        response.put(Keys.OPERATION_TYPE, Operations.DELETE_SINGLE_KEY_RESPONSE);
        response.put(KeyValueStorageHelper.COLUMN_KEY, key);
        response.put(Keys.RESPONSE, ok ? Keys._OK : Keys._ERROR);

        if(ok)
        {
            response.put(Keys.DELETED_KEYS_COUNT, deletedCount);
        }

        return response;
    }

    /**
     *
     * @param id the hashed value
     * @param myId
     * @return
     */
    public static boolean isWithinMyBound(String id, String myId)
    {
        boolean gt = Utils.isGreater(id, Neighbors.getInstance().getPredecessorId());
        boolean lte = Utils.isLessThanOrEqualTo(id, myId);
        Log.v(TAG, "[BOUND_CHECK] [" + id + "] Greater than predecessor = " + gt + ", Less than or equal to my id = " + lte);

        // If it is an increasing sequence
        if(Utils.isGreater(myId, Neighbors.getInstance().getPredecessorId()))
        {
            Log.v(TAG, "[BOUND_CHECK] [" + id + "] Numbers are in increasing sequence. Hence doing usual check.");

            // id should be greater than the predecessor and less than or equal to my id
            return gt && lte;
        }
        else
        {
            Log.v(TAG, "[BOUND_CHECK] [" + id + "] Numbers wrap over. Hence doing special check.");

            // id should be greater than the predecessor or less than or equal to my id
            return gt || lte;
        }
    }
}
