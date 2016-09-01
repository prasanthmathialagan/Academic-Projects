package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by prasanth on 3/5/16.
 */
public class MessageIDGenerator
{
    private static final AtomicInteger id = new AtomicInteger(0);

    public static int getNextID()
    {
        return id.getAndIncrement();
    }
}
