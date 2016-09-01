package edu.buffalo.cse.cse486586.simpledht;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  Helper class to access data from SQLite database
 *
 * Created by prasanth on 1/30/16.
 *
 * Adapted from http://developer.android.com/guide/topics/data/data-storage.html#db
 */
public class KeyValueStorageHelper extends SQLiteOpenHelper
{
    private static final String TAG = KeyValueStorageHelper.class.getName();

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "DS_SIMPLE_DHT";

    private static final String TABLE_NAME = "MESSAGES";

    // Columns
    private static final String COLUMN_ID = "ID";
    public static final String COLUMN_KEY = "key";
    public static final String COLUMN_VALUE = "value";

    private static final String TABLE_CREATE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_KEY + " VARCHAR NOT NULL UNIQUE, " + COLUMN_VALUE + " VARCHAR);";

    private static final String STMT_INSERT = "INSERT INTO " + TABLE_NAME + "(" + COLUMN_KEY + ", " + COLUMN_VALUE
            + ") VALUES (?, ?)";

    private static final String STMT_GET_ALL = "SELECT "+ COLUMN_KEY +", " +COLUMN_VALUE +" FROM "+ TABLE_NAME;

    private static final String STMT_GET_FOR_KEY = STMT_GET_ALL + " WHERE " + COLUMN_KEY + " = ?";

    private static final String STMT_UPDATE_FOR_KEY = "UPDATE " + TABLE_NAME + " SET " + COLUMN_VALUE
            + " = ? WHERE " + COLUMN_KEY + " = ?";

    public KeyValueStorageHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION, null);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL(TABLE_CREATE);
        Log.v(TAG, "Table created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        // Not needed
    }

    // If the key is null, it generates key using the ID generator
    public synchronized boolean insertOrUpdate(String key, String value)
    {
        try
        {
            boolean update = isKeyPresent(key);
            Log.v(TAG, "Inserting key = " + key + ", value = " + value);

            String sql = update ? STMT_UPDATE_FOR_KEY : STMT_INSERT;
            Object[] args = update ? new Object[]{value, key} : new Object[]{key, value};
            getDatabase().execSQL(sql, args);
            return true;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error occurred while inserting key = " + key + ", value = " + value, e);
            return false;
        }
    }

    private boolean isKeyPresent(String key)
    {
        Cursor cursor = getDatabase().rawQuery(STMT_GET_FOR_KEY, new String[]{key});
        try
        {
            cursor.moveToFirst();
            return cursor.getCount() > 0;
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

    public synchronized Cursor getDataForKey(String key)
    {
        return getDatabase().rawQuery(STMT_GET_FOR_KEY, new String[]{key});
    }

    public synchronized Cursor getAllData()
    {
        return getDatabase().rawQuery(STMT_GET_ALL, null);
    }

    public synchronized int deleteAll()
    {
        return getDatabase().delete(TABLE_NAME, null, null);
    }

    public synchronized int delete(String key)
    {
        return getDatabase().delete(TABLE_NAME, COLUMN_KEY + " = ?", new String[]{key});
    }

    // FIXME: 3/18/16 - Make it batch
    public synchronized int deleteKeys(Set<String> keys)
    {
        int deletedCount = 0;
        for (String key: keys)
        {
            deletedCount += delete(key);
        }
        return deletedCount;
    }
}
