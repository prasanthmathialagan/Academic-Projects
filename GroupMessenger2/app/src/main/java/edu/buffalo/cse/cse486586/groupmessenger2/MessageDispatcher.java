package edu.buffalo.cse.cse486586.groupmessenger2;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.Message;

/**
 * Created by prasanth on 3/5/16.
 */
public class MessageDispatcher extends AsyncTask<Void, Void, Void>
{
    private static final String TAG = MessageDispatcher.class.getName();

    private final LinkedBlockingQueue<Message> msgQueue;
    private final MessageHandler messageHandler;

    public MessageDispatcher(LinkedBlockingQueue<Message> msgQueue, MessageHandler messageHandler)
    {
        this.msgQueue = msgQueue;
        this.messageHandler = messageHandler;
    }

    private boolean send(Message msg, int port)
    {
        try
        {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), port);
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject(msg);
            oos.close();
            socket.close();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Error occurred when sending " + msg + " over port " + port, e);
            messageHandler.removeMessages(port);
            return false;
        }

        return true;
    }

    @Override
    protected Void doInBackground(Void... params)
    {
        while(!isCancelled())
        {
            try
            {
                Message msg = msgQueue.take();
                messageHandler.initPing();
                switch (msg.getType())
                {
                    case NEW:
                    case AGREED:
                    case PING:
                        multicast(msg);
                        break;
                    case PROPOSED:
                        Log.v(TAG, "[UNICAST] client = " + msg.getMsgOwnerId() + ", " + msg);
                        send(msg, msg.getMsgOwnerId());
                        break;
                    default:
                        break;
                }
            }
            catch (InterruptedException e)
            {
                Log.w(TAG, e);
            }
        }

        return null;
    }

    private void multicast(Message msg)
    {
        synchronized (messageHandler)
        {
            Set<Integer> clients = messageHandler.getActiveClients();
            Log.v(TAG, "[MULTICAST] Active clients = " + clients.size() + ", " + msg);
            for (Integer port : clients)
            {
                send(msg, port);
            }
        }
    }
}
