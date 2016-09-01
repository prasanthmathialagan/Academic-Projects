package edu.buffalo.cse.cse486586.groupmessenger2;

import android.os.AsyncTask;
import android.util.Log;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.Message;

/**
 * Created by prasanth on 3/5/16.
 */
public class ServerTask extends AsyncTask<ServerSocket, String, Void>
{
    private static final String TAG = ServerTask.class.getName();
    private final MessageHandler msgHandler;

    public ServerTask(MessageHandler msgHandler)
    {
        this.msgHandler = msgHandler;
    }

    @Override
    protected Void doInBackground(ServerSocket... sockets)
    {
        ServerSocket serverSocket = sockets[0];

        while (!isCancelled())
        {
            try
            {
                Socket soc = serverSocket.accept();
                ObjectInputStream ois = new ObjectInputStream(soc.getInputStream());
                Message msg = (Message) ois.readObject();
                msgHandler.handleReceivedMsg(msg);
                ois.close();
                soc.close();
            }
            catch (Exception e)
            {
                Log.e(TAG, "", e);
            }
        }

        return null;
    }
}
