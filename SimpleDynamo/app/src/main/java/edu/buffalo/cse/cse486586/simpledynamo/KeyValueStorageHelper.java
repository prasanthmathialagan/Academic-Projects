package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.Map;

import edu.buffalo.cse.cse486586.simpledynamo.service.Value;

/**
 *  Helper class to access data from SQLite database
 *
 * Created by prasanth on 1/30/16.
 *
 * Adapted from http://developer.android.com/guide/topics/data/data-storage.html#db
 */
public class KeyValueStorageHelper extends SQLiteOpenHelper
{
    private static final String TAG = KeyValueStorageHelper.class.getSimpleName();

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "DS_SIMPLE_DYNAMO";

    private static final String TABLE_NAME = "MESSAGES";

    // Columns
    private static final String COLUMN_ID = "ID";
    public static final String COLUMN_KEY = "key";
    public static final String COLUMN_VALUE = "value";
    public static final String COLUMN_NODEID = "nodeId";
    public static final String COLUMN_VERSION = "version";

    private static final String TABLE_CREATE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_KEY + " VARCHAR NOT NULL UNIQUE, " + COLUMN_VALUE + " VARCHAR, "
                    + COLUMN_NODEID + " VARCHAR, " + COLUMN_VERSION + " INTEGER); "
                    + "CREATE INDEX IF NOT EXISTS " + TABLE_NAME + "_" + COLUMN_NODEID + " ON "
                    + TABLE_NAME + "(" + COLUMN_NODEID + ");" ;

    private static final String STMT_INSERT = "INSERT INTO " + TABLE_NAME + "(" + COLUMN_KEY + ", " + COLUMN_VALUE
            + ", " + COLUMN_NODEID + ", " + COLUMN_VERSION + ") VALUES (?, ?, ?, ?)";

    private static final String STMT_GET_ALL = "SELECT " + COLUMN_KEY + ", " + COLUMN_VALUE + ", " + COLUMN_VERSION + " FROM "
            + TABLE_NAME;

    private static final String STMT_GET_FOR_KEY = STMT_GET_ALL + " WHERE " + COLUMN_KEY + " = ? ";

    private static final String STMT_GET_FOR_NODE =  STMT_GET_ALL + " WHERE " + COLUMN_NODEID + " = ? ";

    private static final String STMT_UPDATE_FOR_KEY = "UPDATE " + TABLE_NAME + " SET " + COLUMN_VALUE + " = ?, " +
            COLUMN_VERSION + " = ? WHERE " + COLUMN_KEY + " = ? ";

    public KeyValueStorageHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION, null);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
//        Log.i(TAG, "Create table query: " + TABLE_CREATE);
        db.execSQL(TABLE_CREATE);
        Log.i(TAG, "Table created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        // Not needed
    }

    /**
     *      If the key is not present in the database, it will be inserted with the version provided.
     * If the key is already present in the database, value will be updated. In case of update, the version
     * present in the database is incremented by 1. If the version provided as an argument is greater than
     * the version present in the database, then the new version will be updated.
     *
     * @param key
     * @param value
     * @param nodeId
     * @param version
     * @return
     */
    public synchronized boolean insertOrUpdate(String key, String value, String nodeId, int version)
    {
        try
        {
            Log.v(TAG, "Inserting key = " + key + ", value = " + value);
            insertOrUpdate0(key, value, nodeId, version);
            return true;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error occurred while inserting key = " + key + ", value = " + value, e);
            return false;
        }
    }

    private void insertOrUpdate0(String key, String value, String nodeId, int version)
    {
        int currentVersion = getVersion(key);
        boolean update = currentVersion != -1;
        int versionToUpdate = update ? (version > currentVersion ? version : (currentVersion + 1)) : version;

        String sql = update ? STMT_UPDATE_FOR_KEY : STMT_INSERT;
        Object[] args = update ? new Object[]{value, versionToUpdate, key} : new Object[]{key, value, nodeId, versionToUpdate};
        getDatabase().execSQL(sql, args);
    }

    /**
     *      Returns -1 if the key is not present in the database.
     *
     * @param key
     * @return
     */
    private int getVersion(String key)
    {
        Cursor cursor = getDatabase().rawQuery(STMT_GET_FOR_KEY, new String[]{key});
        try
        {
            cursor.moveToFirst();
            if(cursor.getCount() > 0)
                return cursor.getInt(cursor.getColumnIndex(COLUMN_VERSION));
            else
                return -1;
        }
        finally
        {
           cursor.close();
        }
    }

    private SQLiteDatabase getDatabase()
    {
        return getWritableDatabase();
    }

    /**
     * @param key
     * @return
     */
    public synchronized Cursor getDataForKey(String key)
    {
        return getDatabase().rawQuery(STMT_GET_FOR_KEY, new String[]{key});
    }

    /**
     *
     * @param nodeId
     * @return
     */
    public synchronized Cursor getAllDataForNode(String nodeId)
    {
        return getDatabase().rawQuery(STMT_GET_FOR_NODE, new String[]{nodeId});
    }

    /**
     *
     * @param key
     * @param nodeId
     * @return
     */
    public synchronized int delete(String key, String nodeId)
    {
        insertOrUpdate0(key, null, nodeId, 1);
        return 1;
    }

    /**
     *
     * @param nodeId
     * @return
     */
    public synchronized int deleteAllDataForNode(String nodeId)
    {
        Cursor cursor = getAllDataForNode(nodeId);

        try
        {
            Map<String, Value> keyValues = Utils.getKeyValues(cursor);

            int deletedCount = 0;
            for(Map.Entry<String, Value> entry : keyValues.entrySet())
            {
                String key = entry.getKey();
                Value value = entry.getValue();

                // Key already deleted
                if(value == null)
                    continue;

                delete(key, nodeId);
                deletedCount++;
            }

            Log.v(TAG, "Deleted keys = " + deletedCount);

            return deletedCount;
        }
        finally
        {
            close();
        }
    }

    /**
     *
     * @return
     */
    public synchronized Cursor getAllLocalData()
    {
        String sql = "SELECT " + COLUMN_KEY + ", " + COLUMN_VALUE + " FROM " + TABLE_NAME
                + " WHERE " + COLUMN_VALUE + " IS NOT NULL";
        return getDatabase().rawQuery(sql, null);
    }
}
