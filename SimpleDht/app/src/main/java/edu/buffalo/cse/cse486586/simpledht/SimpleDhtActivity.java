package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Map;

public class SimpleDhtActivity extends Activity {

    private static final String TAG = SimpleDhtActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_dht_main);
        
        TextView tv = (TextView) findViewById(R.id.editText1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button3).setOnClickListener(
                new OnTestClickListener(tv, getContentResolver()));

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final int myPort = Integer.parseInt(portStr) * 2;

        Button sendButton = (Button) findViewById(R.id.button4);
        sendButton.setOnClickListener(new View.OnClickListener()
        {
            int counter = 0;

            @Override
            public void onClick(View v)
            {
                EditText textField = (EditText) findViewById(R.id.editText1);
                String msg = textField.getText().toString();
                textField.setText("");

                if(msg.equals("DELETE_DHT"))
                {
                    int deletedCount = getContentResolver().delete(Utils.buildUri(), Keys._STAR, null);
                    Log.i(TAG, "[DELETE_DHT] Deleted count = " + deletedCount);
                }
                else if(msg.equals("DELETE_LOCAL"))
                {
                    int deletedCount = getContentResolver().delete(Utils.buildUri(), Keys._AT, null);
                    Log.i(TAG, "[DELETE_LOCAL] Deleted count = " + deletedCount);
                }
                else if(msg.startsWith("DELETE_KEY"))
                {
                    String key = msg.replaceAll("DELETE_KEY#", "");
                    int deletedCount = getContentResolver().delete(Utils.buildUri(), key, null);
                    Log.i(TAG, "[DELETE_KEY] [" + key + "] Deleted count = " + deletedCount);
                }
                else if(msg.startsWith("GET_KEY"))
                {
                    String key = msg.replaceAll("GET_KEY#", "");
                    Cursor cursor = getContentResolver().query(Utils.buildUri(), null, key , null, null);
                    if(cursor == null)
                    {
                        Log.e(TAG, "[GET_KEY] [ERROR]");
                    }
                    else
                    {
                        Map<String, String> keyValues = Utils.getKeyValues(cursor);
                        Log.i(TAG, "[GET_KEY] " + keyValues);
                    }
                    cursor.close();
                }
                else
                {
                    Log.i(TAG, "[NEW_MESSAGE] " + msg);

                    ContentValues values = new ContentValues();
                    values.put(KeyValueStorageHelper.COLUMN_KEY, myPort + "-" + counter++ + "-" + msg);
                    values.put(KeyValueStorageHelper.COLUMN_VALUE, msg);

                    getContentResolver().insert(Utils.buildUri(), values);
                }
            }
        });

        Button lDumpButton =  (Button) findViewById(R.id.button1);
        lDumpButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Cursor cursor = getContentResolver().query(Utils.buildUri(), null, Keys._AT, null, null);
                if (cursor == null) {
                    Log.e(TAG, "[GET_LOCAL] [ERROR]");
                } else {
                    Map<String, String> keyValues = Utils.getKeyValues(cursor);
                    Log.i(TAG, "[GET_LOCAL] " + keyValues);
                    cursor.close();
                }
            }
        });

        Button gDumpButton = (Button) findViewById(R.id.button2);
        gDumpButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Cursor cursor = getContentResolver().query(Utils.buildUri(), null, Keys._STAR, null, null);
                if(cursor == null)
                {
                    Log.e(TAG, "[GET_ALL] [ERROR]");
                }
                else
                {
                    Map<String, String> keyValues = Utils.getKeyValues(cursor);
                    Log.i(TAG, "[GET_ALL] " + keyValues);
                    cursor.close();
                }
            }
        });

        ContentValues values = new ContentValues();
        values.put(Keys.OPERATION_TYPE, Operations.INIT);
        values.put(Keys.PORT_STR, myPort + "");

        Uri response = getContentResolver().insert(Utils.buildUri(), values);
        String code = response.getLastPathSegment();
        Log.i(TAG, "[INIT_RESPONSE] " + code);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_simple_dht_main, menu);
        return true;
    }

}
