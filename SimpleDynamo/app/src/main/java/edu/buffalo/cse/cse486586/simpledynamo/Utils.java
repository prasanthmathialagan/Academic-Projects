package edu.buffalo.cse.cse486586.simpledynamo;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import edu.buffalo.cse.cse486586.simpledynamo.service.Value;

/**
 * Created by prasanth on 4/22/16.
 */
public class Utils
{
    /**
     *
     * @return
     */
    public static Uri buildUri()
    {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.simpledynamo.provider");
        uriBuilder.scheme("content");
        return uriBuilder.build();
    }

    /**
     *
     * @param time
     */
    public static void sleep(long time)
    {
        try
        {
            Thread.sleep(time);
        }
        catch (InterruptedException e1)
        {

        }
    }

    /**
     *
     * @param soc
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object receiveObject(Socket soc) throws IOException, ClassNotFoundException
    {
        ObjectInputStream ois = new ObjectInputStream(soc.getInputStream());
        return ois.readUnshared();
    }

    /**
     *
     * @param o
     * @param socket
     * @throws IOException
     */
    public static void sendObject(Object o, Socket socket) throws IOException
    {
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        oos.writeUnshared(o);
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
     * @param cursor
     * @return
     */
    public static Map<String, Value> getKeyValues(Cursor cursor)
    {
        Map<String, Value> keyValues = new HashMap<String, Value>();
        fillFromCursor(cursor, keyValues);
        return keyValues;
    }

    /**
     * https://examples.javacodegeeks.com/android/core/database/android-cursor-example/
     *
     * @param cursor
     * @param keyValues
     */
    public static void fillFromCursor(Cursor cursor, Map<String, Value> keyValues)
    {
        if(cursor.moveToFirst())
        {
            do
            {
                String key = cursor.getString(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_KEY));
                String value = cursor.getString(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VALUE));
                int version = cursor.getInt(cursor.getColumnIndex(KeyValueStorageHelper.COLUMN_VERSION));
                keyValues.put(key, new Value(value, version));
            } while (cursor.moveToNext());
        }
    }

    /**
     *
     * @param latch
     * @param timeout in milliseconds
     */
    public static void await(CountDownLatch latch, long timeout) throws InterruptedException
    {
        if(timeout <= 0)
            latch.await();
        else
            latch.await(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     *
     * @param ecs
     * @param timeout in milliseconds
     * @param <T>
     * @return
     */
    public static<T> Future<T> poll(ExecutorCompletionService<T> ecs, long timeout) throws InterruptedException
    {
        if(timeout <= 0)
            return ecs.take();
        else
            return ecs.poll(timeout, TimeUnit.MILLISECONDS);
    }
}
