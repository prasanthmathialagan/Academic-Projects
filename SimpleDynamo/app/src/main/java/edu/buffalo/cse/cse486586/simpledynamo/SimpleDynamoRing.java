package edu.buffalo.cse.cse486586.simpledynamo;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

/**
 *  Completely thread-safe class.
 *
 * Created by prasanth on 4/21/16.
 */
public final class SimpleDynamoRing
{
    private static final String TAG = SimpleDynamoRing.class.getSimpleName();

    public static final int REPLICA_SIZE = 3;

    public static final String[] NODES = {"5554", "5556", "5558", "5560", "5562"};

    private static final Map<String, String> nodeIdToHash;
    private static final Map<String, String> nodeHashToId;

    private static final Map<String, String[]> nodeToReplicas;
    private static final Map<String, String[]> nodeToReverseReplicas; // For reconciliation
    private static final Map<String, String> nodeToPredecessor;

    static
    {
        nodeIdToHash = new HashMap<String, String>(NODES.length);
        nodeHashToId = new HashMap<String, String>(NODES.length);
        for (String nodeId : NODES)
        {
            String hash = genHash(nodeId);
            nodeIdToHash.put(nodeId, hash);
            nodeHashToId.put(hash, nodeId);
        }

        nodeToReplicas = new HashMap<String, String[]>(NODES.length);
        nodeToReverseReplicas = new HashMap<String, String[]>(NODES.length);
        nodeToPredecessor = new HashMap<String, String>(NODES.length);

        ArrayList<String> hashValues = new ArrayList<String>(nodeHashToId.keySet());
        Collections.sort(hashValues);
        for (int i = 0; i < NODES.length; i++)
        {
            String hash = hashValues.get(i);
            String nodeId = nodeHashToId.get(hash);

            String[] replicas = new String[REPLICA_SIZE - 1];
            for (int j = 1; j <= REPLICA_SIZE - 1; j++)
            {
                int index = (i + j) % NODES.length;
                replicas[j - 1] = nodeHashToId.get(hashValues.get(index));
            }
            nodeToReplicas.put(nodeId, replicas);

            String[] reverseReplicas = new String[REPLICA_SIZE - 1];
            for (int j = 1; j <= REPLICA_SIZE - 1; j++)
            {
                int index = (NODES.length + i - j) % NODES.length;
                reverseReplicas[j - 1] = nodeHashToId.get(hashValues.get(index));
            }
            nodeToReverseReplicas.put(nodeId, reverseReplicas);

            int predIndex = (NODES.length + i - 1) % NODES.length;
            nodeToPredecessor.put(nodeId, nodeHashToId.get(hashValues.get(predIndex)));
        }

        // Print the replica and predecessor details
        StringBuilder sb = new StringBuilder("Predecessor and Replica details\n");
        for (String nodeId : NODES)
        {
            sb.append(nodeId).append("\n");

            sb.append(" Predecessor : ").append(nodeToPredecessor.get(nodeId)).append("\n");
            sb.append(" Replicas : ").append(Arrays.toString(nodeToReplicas.get(nodeId))).append("\n");
            sb.append(" Reverse replicas : ").append(Arrays.toString(nodeToReverseReplicas.get(nodeId))).append("\n");
        }

//        Log.i(TAG, sb.toString());
    }

    /**
     *      Generates SHA-1 hash for the given input string.
     *
     * @param input
     * @return null if the hash cannot be computed.
     */
    public static final String genHash(String input)
    {
        try
        {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] sha1Hash = sha1.digest(input.getBytes());
            Formatter formatter = new Formatter();
            for (byte b : sha1Hash)
            {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     *    Returns true if hash1 is lexicographically greater than hash2.
     *
     * @param hash1
     * @param hash2
     * @return
     */
    public static boolean isGreater(String hash1, String hash2)
    {
        return hash1.compareTo(hash2) > 0;
    }

    /**
     *    Returns true if hash1 is lexicographically less than or equal to hash2.
     *
     * @param hash1
     * @param hash2
     * @return
     */
    public static boolean isLessThanOrEqualTo(String hash1, String hash2)
    {
        return !isGreater(hash1, hash2);
    }

    /**
     *
     * @param predecessorId
     * @param myId
     * @param desiredKeyHash
     * @return
     */
    public static final boolean isWithinBounds(String predecessorId, String myId, String desiredKeyHash)
    {
        String predecessorHash = nodeIdToHash.get(predecessorId);
        String myHash = nodeIdToHash.get(myId);

        boolean gt = isGreater(desiredKeyHash, predecessorHash);
        boolean lte = isLessThanOrEqualTo(desiredKeyHash, myHash);

        // If it is an increasing sequence
        if(isGreater(myHash, predecessorHash))
        {
            // key's hash should be greater than the predecessor's hash and less than or equal to mine
            return gt && lte;
        }
        else
        {
            // key's hash should either be greater than the predecessor's hash or less than or equal to mine
            return gt || lte;
        }
    }

    /**
     *
     * @param nodeId
     * @return
     */
    public static final String[] getReplicas(String nodeId)
    {
        return nodeToReplicas.get(nodeId);
    }

    /**
     *
     * @param nodeId
     * @return
     */
    public static final String getPredecessor(String nodeId)
    {
        return nodeToPredecessor.get(nodeId);
    }

    /**
     *      Returns the node responsible for the key.
     *
     * @param key
     * @return
     */
    public static final String getNodeResponsible(String key)
    {
        String keyHash = genHash(key);
        for (Map.Entry<String, String> entry: nodeToPredecessor.entrySet())
        {
            String nodeId = entry.getKey();
            String predecessorId = entry.getValue();
            if(isWithinBounds(predecessorId, nodeId, keyHash))
                return nodeId;
        }

        Log.w(TAG, "Could not find the node responsible for the key " + key + ", hash = " + keyHash);

        return null; // This should not happen
    }

    /**
     *
     * @param nodeId
     * @return
     */
    public static final String[] getReverseReplicas(String nodeId)
    {
        return nodeToReverseReplicas.get(nodeId);
    }
}
