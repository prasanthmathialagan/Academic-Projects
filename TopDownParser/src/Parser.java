
/* 		OBJECT-ORIENTED RECOGNIZER FOR SIMPLE EXPRESSIONS
expr    -> term   (+ | -) expr | term
term    -> factor (* | /) term | factor
factor  -> int_lit | '(' expr ')'     
*/

public class Parser
{
	private static final boolean debug = true;

	public static void main(String[] args)
	{
		System.out.println("Enter an expression that ends with 'end'!\n");
		Lexer.lex();
		new Program();
		Code.printOutput();
	}

	public static void print(Object o)
	{
		if (debug)
			System.out.println(o);
	}
}

// decls stmts end
class Program
{
	Decls d;
	Stmts stmts;

	public Program()
	{
		d = new Decls();
		stmts = new Stmts();
		Code.genCodeForReturn();
	}
}

// int idlist ';'
class Decls
{
	Idlist idlist;

	public Decls()
	{
		if (Lexer.nextToken == Token.KEY_INT || Lexer.nextToken == Token.KEY_BOOLEAN)
		{
			Lexer.lex();
			idlist = new Idlist();
		}

		if (Lexer.nextToken == Token.SEMICOLON)
			Lexer.lex();
	}
}

// idlist -> id [',' idlist ]
class Idlist
{
	char id;
	Idlist idlist;

	public Idlist()
	{
		id = Lexer.ident;
		Code.addId(id);
		
		Lexer.lex();

		switch (Lexer.nextToken)
		{
			case Token.COMMA:
				Lexer.lex();
				idlist = new Idlist();
				break;
			default:
				break;
		}
	}
}

// stmts -> stmt [ stmts ]
class Stmts
{
	Stmt stmt;
	Stmts stmts;

	public Stmts()
	{
		stmt = new Stmt();

		// FIXME : Is it right?
		if (Lexer.nextToken != Token.KEY_END && Lexer.nextToken != Token.RIGHT_BRACE)
			stmts = new Stmts();
	}
}

// stmt -> assign ';'| cmpd | cond | loop
class Stmt
{
	Assign assign;
	Cmpd cmpd;
	Cond cond;
	Loop loop;

	public Stmt()
	{
		switch (Lexer.nextToken)
		{
		case Token.ID:
			assign = new Assign();
			if (Lexer.nextToken == Token.SEMICOLON)
				Lexer.lex();
			break;
		case Token.LEFT_BRACE:
			cmpd = new Cmpd();
			break;
		case Token.KEY_IF:
			cond = new Cond();
			break;
		case Token.KEY_FOR:
			loop = new Loop();
			break;
		default:
			break;
		}
	}
}

// assign -> id '=' expr
class Assign
{
	char id;
	Expr expr;

	public Assign()
	{
		id = Lexer.ident;
		Lexer.lex();
		if (Token.ASSIGN_OP == Lexer.nextToken)
		{
			Lexer.lex();
			expr = new Expr();
			Code.genCodeForStore(id);
		}
	}
}

// cmpd -> '{' stmts '}'
class Cmpd
{
	Stmts stmts;

	public Cmpd()
	{
		Lexer.lex();
		stmts = new Stmts();
		if (Token.RIGHT_BRACE == Lexer.nextToken)
			Lexer.lex();
	}
}

// cond -> if '(' rexp ')' stmt [ else stmt ]
class Cond
{
	Rexp rexp;
	Stmt ifStmt;
	Stmt elseStmt;

	public Cond()
	{
		Lexer.lex();
		if (Token.LEFT_PAREN == Lexer.nextToken)
		{
			Lexer.lex();
			rexp = new Rexp();

			if (Token.RIGHT_PAREN == Lexer.nextToken)
			{
				Lexer.lex();
				ifStmt = new Stmt();

				if (Token.KEY_ELSE == Lexer.nextToken)
				{
					Code.genCodeForIfGoto();
					
					Lexer.lex();
					elseStmt = new Stmt();
				}
				
				Code.unmarkTopmostPositionFromStack();
			}
		}
	}
}

// loop -> for '(' [assign] ';' [rexp] ';' [assign] ')' stmt
class Loop
{
	Assign initAssign;
	Rexp rexp;
	Assign incAssign;
	Stmt stmt;

	public Loop()
	{
		Lexer.lex();
		if (Lexer.nextToken == Token.LEFT_PAREN)
		{
			Lexer.lex();
			if (Lexer.nextToken != Token.SEMICOLON)
				initAssign = new Assign();

			Lexer.lex(); // ;
			Code.markCurrentPositionInStack();
			if (Lexer.nextToken != Token.SEMICOLON)
				rexp = new Rexp();
			else
				Code.markPlaceboPositionInStack();
			
			Lexer.lex(); // ;
			if (Lexer.nextToken != Token.RIGHT_PAREN)
			{
				Code.markCurrentPositionInStack();
				incAssign = new Assign();
			}
			else
			{
				Code.markPlaceboPositionInStack();
			}

			Lexer.lex(); // )
			Code.markCurrentPositionInStack();
			stmt = new Stmt();
			Code.markCurrentPositionInStack();
			
			Code.relocate();
			
			Code.genCodeForForGoto();
		}
	}
}

// rexp -> expr ('<' | '>' | '==' | '!= ') expr
class Rexp
{
	Expr lExp;
	Expr rExp;

	public Rexp()
	{
		lExp = new Expr();
		int relopToken = Lexer.nextToken;
		Lexer.lex();
		rExp = new Expr();
		Code.genCodeForRelop(relopToken);
	}
}

// expr -> term [ ('+' | '-') expr ]
class Expr
{ // expr -> term (+ | -) expr | term
	Term t;
	Expr e;
	char op;

	public Expr()
	{
		t = new Term();
		if (Lexer.nextToken == Token.ADD_OP || Lexer.nextToken == Token.SUB_OP)
		{
			op = Lexer.nextChar;
			Lexer.lex();
			e = new Expr();
			Code.gen(new Instruction(Code.opcode(op), false), 1);
		}
	}
}

// factor [ ('*' | '/') term ]
class Term
{ // term -> factor (* | /) term | factor
	Factor f;
	Term t;
	char op;

	public Term()
	{
		f = new Factor();
		if (Lexer.nextToken == Token.MULT_OP || Lexer.nextToken == Token.DIV_OP)
		{
			op = Lexer.nextChar;
			Lexer.lex();
			t = new Term();
			Code.gen(new Instruction(Code.opcode(op), false), 1);
		}
	}
}

// FIXME
// int_lit | id | '(' expr ')'
class Factor
{ // factor -> number | '(' expr ')'
	Expr e;
	int i;
	char id;
	boolean b;

	public Factor()
	{
		switch (Lexer.nextToken)
		{
			case Token.BOOL_LIT:
				b = Lexer.boolValue;
				Code.genCodeForBoolLit(b);
				Lexer.lex();
				break;
			case Token.INT_LIT: // number
				i = Lexer.intValue;
				Code.genCodeForIntLit(i);
				Lexer.lex();
				break;
			case Token.ID:
				id = Lexer.ident;
				Lexer.lex();
				Code.genCodeForLoad(id);
				break;
			case Token.LEFT_PAREN: // '('
				Lexer.lex();
				e = new Expr();
				Lexer.lex(); // skip over ')'
				break;
			default:
				break;
		}
	}
}
