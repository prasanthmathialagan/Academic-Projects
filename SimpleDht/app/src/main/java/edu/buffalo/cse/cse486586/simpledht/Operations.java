package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by prasanth on 3/16/16.
 */
public final class Operations
{
    private Operations()
    {

    }

    public static final String INIT = "INIT";

    public static final String JOIN_REQUEST = "JOIN_REQUEST";
    public static final String JOIN_RESPONSE = "JOIN_RESPONSE";

    public static final String UPDATE_SUCCESSOR_REQUEST = "UPDATE_SUCCESSOR_REQUEST";
    public static final String UPDATE_SUCCESSOR_RESPONSE = "UPDATE_SUCCESSOR_RESPONSE";

    public static final String INSERT_KEY_REQUEST = "INSERT_KEY_REQUEST";
    public static final String INSERT_KEY_RESPONSE = "INSERT_KEY_RESPONSE";

    public static final String GET_ALL_LOCAL_REQUEST = "GET_ALL_LOCAL_REQUEST";
    public static final String GET_ALL_LOCAL_RESPONSE = "GET_ALL_LOCAL_RESPONSE";

    public static final String GET_ALL_DHT_REQUEST = "GET_ALL_DHT_REQUEST";
    public static final String GET_ALL_DHT_RESPONSE = "GET_ALL_DHT_RESPONSE";

    public static final String GET_SINGLE_KEY_REQUEST = "GET_SINGLE_KEY_REQUEST";
    public static final String GET_SINGLE_KEY_RESPONSE = "GET_SINGLE_KEY_RESPONSE";

    // Delete
    public static final String DEL_ALL_LOCAL_REQUEST = "DEL_ALL_LOCAL_REQUEST";
    public static final String DEL_ALL_LOCAL_RESPONSE = "DEL_ALL_LOCAL_RESPONSE";

    public static final String DEL_ALL_DHT_REQUEST = "DEL_ALL_DHT_REQUEST";
    public static final String DEL_ALL_DHT_RESPONSE = "DEL_ALL_DHT_RESPONSE";

    public static final String DELETE_SINGLE_KEY_REQUEST = "DELETE_SINGLE_KEY_REQUEST";
    public static final String DELETE_SINGLE_KEY_RESPONSE = "DELETE_SINGLE_KEY_RESPONSE";
}
