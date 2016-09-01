package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.Message;
import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.NewMessage;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity
{
    private static final String TAG = GroupMessengerActivity.class.getName();
    private static final int SERVER_PORT = 10000;

    private final LinkedBlockingQueue<Message> msgQueue = new LinkedBlockingQueue<Message>();
    private MessageHandler msgHandler;
    private int myPort;

    private void initialize()
    {
        Set<Integer> clients = new HashSet<Integer>();
        clients.add(11108);
        clients.add(11112);
        clients.add(11116);
        clients.add(11120);
        clients.add(11124);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = Integer.parseInt(portStr) * 2;

        msgHandler = new MessageHandler(msgQueue, getContentResolver(), clients, myPort);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        initialize();

        try
        {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask(msgHandler).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch (IOException e)
        {
            Log.e(TAG, "Can't create a ServerSocket", e);
            return;
        }

        new MessageDispatcher(msgQueue, msgHandler).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        /*
         * You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        Button sendButton = (Button) findViewById(R.id.button4);
        sendButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                EditText textField = (EditText) findViewById(R.id.editText1);
                String msg = textField.getText().toString();
                textField.setText("");

                NewMessage nmsg = new NewMessage(myPort, msg);
                msgQueue.offer(nmsg);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
