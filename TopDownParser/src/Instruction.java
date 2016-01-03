
/**
 * 
 * @author Prasanth
 *
 */
public class Instruction
{
	private final String opcode;
	private final boolean hasOffset;
	private int branchOffset = -1;
	
	public Instruction(String opcode)
	{
		this(opcode, false);
	}

	public Instruction(String opcode, boolean hasOffset)
	{
		this.opcode = opcode;
		this.hasOffset = hasOffset;
	}

	public void setBranchOffset(int branchOffset)
	{
		if (hasOffset)
			this.branchOffset = branchOffset;
	}

	public void addBranchOffset(int value)
	{
		if (hasOffset)
			branchOffset = branchOffset + value;
	}

	@Override
	public String toString()
	{
		String s = opcode;
		if (hasOffset)
			s = s + " " + branchOffset;
		return s;
	}
}
