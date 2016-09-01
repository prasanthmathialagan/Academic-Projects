package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by prasanth on 3/16/16.
 */
public final class Keys
{
    private Keys()
    {

    }

    public static final String OPERATION_TYPE = "OPERATION_TYPE";
    public static final String PORT_STR = "PORT_STR";
    public static final String PORT_ID = "PORT_ID"; // Hashed key

    public static final String SUCCESSOR_PORT = "SUCCESSOR_PORT";
    public static final String PREDECESSOR_PORT = "PREDECESSOR_PORT";

    public static final String RESPONSE = "RESPONSE";

    public static final String _OK = "OK";
    public static final String _ERROR = "ERROR";

    public static final String _STAR = "*";
    public static final String _AT = "@";

    public static final String INITIATOR = "INITIATOR";
    public static final String ALL_KEY_VALUES = "ALL_KEY_VALUES";

    public static final String DELETED_KEYS_COUNT = "DELETED_KEYS_COUNT";
}
