package edu.buffalo.cse.cse486586.simpledynamo.service.message;

import java.util.Map;

import edu.buffalo.cse.cse486586.simpledynamo.service.Value;
import edu.buffalo.cse.cse486586.simpledynamo.service.message.Message;

/**
 * Created by prasanth on 4/23/16.
 */
public class QueryNodeResponse extends Message
{
    private final Map<String, Value> keyValues;

    public QueryNodeResponse(Map<String, Value> keyValues)
    {
        this.keyValues = keyValues;
    }

    public Map<String, Value> getKeyValues()
    {
        return keyValues;
    }
}
