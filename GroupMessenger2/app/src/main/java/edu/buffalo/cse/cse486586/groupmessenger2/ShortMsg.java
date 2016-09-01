package edu.buffalo.cse.cse486586.groupmessenger2;

/**
 * Created by prasanth on 3/6/16.
 */
public class ShortMsg implements Comparable<ShortMsg>
{
    int id;
    int senderId;
    int seqNo;
    int proposerId;
    String msg;
    boolean deliverable;

    public ShortMsg(int id, int senderId, int seqNo, int proposerId, String msg)
    {
        this.id = id;
        this.senderId = senderId;
        this.seqNo = seqNo;
        this.proposerId = proposerId;
        this.msg = msg;
    }

    @Override
    public int compareTo(ShortMsg s)
    {
        int c = Integer.compare(seqNo, s.seqNo);
        return c == 0 ? Integer.compare(proposerId, s.proposerId) : c;
    }

    @Override
    public String toString()
    {
        return "ShortMsg{" +
                "id=" + id +
                ", senderId=" + senderId +
                ", msg='" + msg + '\'' +
                ", deliverable=" + deliverable +
                '}';
    }
}
