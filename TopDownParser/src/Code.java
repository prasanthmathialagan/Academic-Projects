import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * 
 * @author Prasanth
 *
 */
public class Code
{
	private static List<Instruction> instructions = new ArrayList<>();
	private static List<Character> idList = new ArrayList<>();
	private static Stack<Integer> branchStmtsStack = new Stack<>();
	
	public static void printOutput()
	{
		for (int i = 0; i < instructions.size(); i++)
		{
			Instruction instr = instructions.get(i);
			if(instr == null)
				continue;
			System.out.println(i +": " + instr);
		}
	}
	
	public static void genCodeForReturn()
	{
		gen(new Instruction("return"), 1);
	}
	
	public static int gen(Instruction instr, int incrPtrBy)
	{
		int currentPtr = instructions.size();
		instructions.add(instr);

		for (int i = 1; i < incrPtrBy ; i++)
			instructions.add(null);
		
		return currentPtr;
	}

	public static void addId(char id)
	{
		idList.add(id);
	}
	
	public static void genCodeForStore(char id)
	{
		genCodeWithId(id, "istore");
	}
	
	public static void genCodeForLoad(char id)
	{
		genCodeWithId(id, "iload");
	}
	
	private static void genCodeWithId(char id, String type)
	{
		String opcode;
		int offset;
		
		int position = idList.indexOf(id) + 1;
		if(position > 3)
		{
			opcode = type + " " + position;
			offset = 2;
		}
		else
		{
			opcode = type + "_" + position;
			offset = 1;
		}
		
		gen(new Instruction(opcode, false), offset);
	}
	
	public static void genCodeForIntLit(int i)
	{
		String opcode;
		int offset;
		
		if(i > 127)
		{
			opcode = "sipush " + i;
			offset = 3;
		}
		else if(i > 5)
		{
			opcode = "bipush " + i;
			offset = 2;
		}
		else
		{
			opcode = "iconst_" + i;
			offset = 1;
		}
		
		gen(new Instruction(opcode, false), offset);
	}
	
	public static void genCodeForBoolLit(boolean b)
	{
		int i = b ? 1 : 0;
		gen(new Instruction("iconst_" + i), 1);
	}
	
	public static void genCodeForRelop(int token)
	{
		markCurrentPositionInStack();

		switch (token)
		{
			case Token.GREATER_OP:
				gen(new Instruction("if_icmple", true), 3);
				break;
			case Token.LESSER_OP:
				gen(new Instruction("if_icmpge", true), 3);
				break;
			case Token.EQ_OP:
				gen(new Instruction("if_icmpne", true), 3);
				break;
			case Token.NOT_EQ:
				gen(new Instruction("if_icmpeq", true), 3);
				break;
			default:
				break;
		}
	}

	public static void markCurrentPositionInStack()
	{
		push(instructions.size());
	}

	private static void push(Integer value)
	{
		branchStmtsStack.push(value);
	}
	
	public static void markPlaceboPositionInStack()
	{
		push(null);
	}
	
	public static void genCodeForIfGoto()
	{
		Integer lastIndex = branchStmtsStack.pop();
		markCurrentPositionInStack();
		
		gen(new Instruction("goto", true), 3);

		Instruction inst = instructions.get(lastIndex);
		inst.setBranchOffset(instructions.size());
	}
	
	public static void genCodeForForGoto()
	{
		int gotoIndex = gen(new Instruction("goto", true), 3);

		Integer lastIndex = branchStmtsStack.pop();
		if(lastIndex != null)
		{
			Instruction inst = instructions.get(lastIndex);
			inst.setBranchOffset(instructions.size());
		}

		Integer gotoLoc = branchStmtsStack.pop();
		Instruction inst = instructions.get(gotoIndex);
		inst.setBranchOffset(gotoLoc);
	}
	
	public static void unmarkTopmostPositionFromStack()
	{
		Integer lastIndex = branchStmtsStack.pop();
		Instruction inst = instructions.get(lastIndex);
		inst.setBranchOffset(instructions.size());
	}
	
	public static void relocate()
	{
		int toIndex = branchStmtsStack.pop();
		int fromIndex = branchStmtsStack.pop();
		Integer insertIdx = branchStmtsStack.pop();
		int insertIndex = insertIdx == null? fromIndex : insertIdx;

		int branchOffset = insertIndex - fromIndex;
		
		for (int i = 0; i < toIndex - fromIndex; i++)
		{
			int remIndex = fromIndex + i;
			Instruction instr = instructions.remove(remIndex);
			if(instr != null)
				instr.addBranchOffset(branchOffset);
			
			int finInserIndex = insertIndex + i;
			instructions.add(finInserIndex, instr);
		}
	}
	
	public static String opcode(char op)
	{
		switch (op)
		{
			case '+':
				return "iadd";
			case '-':
				return "isub";
			case '*':
				return "imul";
			case '/':
				return "idiv";
			default:
				return "";
		}
	}
}
