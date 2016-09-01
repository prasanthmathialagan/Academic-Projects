package edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg;

/**
 * Created by prasanth on 3/5/16.
 */
public class AgreedMessage extends Message
{
    private final int proposerId;
    private final int agreedSeqNo;

    public AgreedMessage(ProposedMessage msg)
    {
        super(msg.getId(), MessageType.AGREED, msg.getMsgOwnerId(), msg.getMsgOwnerId());
        this.proposerId = msg.getProposerId();
        this.agreedSeqNo = msg.getProposedSeqNo();
    }

    public int getProposerId()
    {
        return proposerId;
    }

    public int getAgreedSeqNo()
    {
        return agreedSeqNo;
    }

    @Override
    public String toString()
    {
        return "AgreedMessage{" +
                "proposerId=" + proposerId +
                ", agreedSeqNo=" + agreedSeqNo +
                "} " + super.toString();
    }
}
