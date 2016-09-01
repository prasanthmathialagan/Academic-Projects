package edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg;

import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.Message;
import edu.buffalo.cse.cse486586.groupmessenger2.edu.buffalo.cse.cse486586.groupmessenger2.msg.NewMessage;

/**
 * Created by prasanth on 3/5/16.
 */
public class ProposedMessage extends Message
{
    private final int proposerId;
    private final int proposedSeqNo;

    public ProposedMessage(NewMessage msg, int proposerId, int proposedSeqNo)
    {
        super(msg.getId(), MessageType.PROPOSED, msg.getMsgOwnerId(), proposerId);
        this.proposerId = proposerId;
        this.proposedSeqNo = proposedSeqNo;
    }

    public int getProposerId()
    {
        return proposerId;
    }

    public int getProposedSeqNo()
    {
        return proposedSeqNo;
    }

    @Override
    public String toString()
    {
        return "ProposedMessage{" +
                "proposerId=" + proposerId +
                ", proposedSeqNo=" + proposedSeqNo +
                ", parent=" + super.toString() +
                '}';
    }
}
