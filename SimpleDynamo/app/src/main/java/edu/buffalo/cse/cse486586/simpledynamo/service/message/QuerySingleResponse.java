package edu.buffalo.cse.cse486586.simpledynamo.service.message;

import edu.buffalo.cse.cse486586.simpledynamo.service.Value;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;

/**
 * Created by prasanth on 4/23/16.
 */
public class QuerySingleResponse extends Message
{
    private final String key;
    private final Value value;

    public QuerySingleResponse(String key, Value value)
    {
        this.key = key;
        this.value = value;
    }

    public String getKey()
    {
        return key;
    }

    public Value getValue()
    {
        return value;
    }
}
