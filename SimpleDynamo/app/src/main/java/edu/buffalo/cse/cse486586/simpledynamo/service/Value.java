package edu.buffalo.cse.cse486586.simpledynamo.service;

import java.io.Serializable;

/**
 * Created by prasanth on 4/23/16.
 */
public class Value implements Serializable
{
    private final String value;
    private final int version;

    public Value(String value, int version)
    {
        this.value = value;
        this.version = version;
    }

    public String getValue()
    {
        return value;
    }

    public int getVersion()
    {
        return version;
    }

    @Override
    public String toString()
    {
        return "Value{" +
                "value='" + value + '\'' +
                ", version=" + version +
                '}';
    }
}
