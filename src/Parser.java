import java.util.Stack;




/* 		OO PARSER AND BYTE-CODE GENERATOR FOR TINY PL

Grammar for TinyPL (using EBNF notation) is as follows:

 program ->  decls stmts end
 decls   ->  int idlist ;
 idlist  ->  id { , id } 
 stmts   ->  stmt [ stmts ]
 cmpdstmt->  '{' stmts '}'
 stmt    ->  assign | cond | loop
byteCount assign  ->  id = expr ;
 cond    ->  if '(' rexp ')' cmpdstmt [ else cmpdstmt ]
 loop    ->  while '(' rexp ')' cmpdstmt  
 rexp    ->  expr (< | > | =) expr
 expr    ->  term   [ (+ | -) expr ]
 term    ->  factor [ (* | /) term ]
 factor  ->  int_lit | id | '(' expr ')'

Lexical:   id is a single character; 
	      int_lit is an unsigned integer;
		 equality operator is =, not ==

Sample Program: Factorial

int n, i, f;
n = 4;
i = 1;
f = 1;
while (i < n) {
  i = i + 1;
  f= f * i;
}
end

   Sample Program:  GCD

int x, y;
x = 121;
y = 132;
while (x != y) {
  if (x > y) 
       { x = x - y; }
  else { y = y - x; }
}
end

 */

public class Parser {

	public static void main(String[] args)  {

		System.out.println("Enter program and terminate with 'end'!\n");
		Lexer.lex();
		Program prog = new Program();
		Code.output();
	}
}

class Program {
	Decls decls;
	Stmts stmts;
	public Program(){
		decls = new Decls();
		Lexer.lex();
		stmts = new Stmts(Token.KEY_END);
		Code.gen(1, "return");
	}
}

class Decls {
	Idlist idlist;
	public Decls(){
		idlist = new Idlist();
	}
}

class Idlist {
	static char[] id = new char[100];
	int i = 0;
	public Idlist() { 
		while( Lexer.lex() != 0 ) {
			if( Lexer.nextToken == Token.ID ) {
				id[i]=Lexer.ident;
				i = i + 1;
			}
		} 
	}
}

class Stmt {

	static char curr;
	static int indexCurr;
	Assign assign;
	Loop loop;
	Cond cond;

	public Stmt() {  
		switch (Lexer.nextToken) {
		case Token.ID:    
			curr = Lexer.ident;
			for(char ch : Idlist.id) {
				if(ch != Lexer.ident) {
					indexCurr = indexCurr + 1;
				} else {
					break;
				}
			}
			Lexer.lex();
		case Token.ASSIGN_OP: // =   
			assign = new Assign();
			curr = '\0'; indexCurr = 0;
			break;  
		case Token.KEY_WHILE: // while 
			int gotoptr = Code.bytes;
			loop = new Loop();    //Loop call
			Code.gen( 1, "goto  " + gotoptr );
			Code.bytes = Code.bytes + 2;
			if(!Rexpr.codePtr.isEmpty()){
				int whilePtr = Rexpr.codePtr.pop();
				Code.code[whilePtr] = Code.code[ whilePtr ] + Code.bytes;
			}
			break;   
		case Token.KEY_IF: // if
			cond = new Cond();
			break; 
		default:
			break;
		} 
	} 
} 

class Stmts {
	Stmt stmt;
	Stmts stmts;
	public Stmts(int cond) {
		stmt = new Stmt();
		if( Lexer.nextToken!=Token.KEY_END && Lexer.nextToken!=Token.ID) {
			Lexer.lex();
		}
		if ( Lexer.nextToken != cond && Lexer.nextToken!=Token.KEY_END ) {
			stmts = new Stmts( cond );
		}
	}
}

class Assign {
	Expr expr;
	public Assign(){
		Lexer.lex();
		expr = new Expr();
		Code.gen(1, "istore_" + Stmt.indexCurr);
	}
}

class Cond {
	Rexpr rexpr;
	Cmpdstmt cmpdstmt1;
	Cmpdstmt cmpdstmt2;
	//static int elsePtr;
	public Cond() {
		Lexer.lex();
		rexpr = new Rexpr();
		Lexer.lex();
		cmpdstmt1 = new Cmpdstmt();
		if( Lexer.nextToken != Token.KEY_END ) {
			Lexer.lex();
		}
		if(!Rexpr.codePtr.isEmpty()) {
			int elsePtr = Rexpr.codePtr.pop();
			int updateConn = Code.bytes + 3;
			Code.code[elsePtr] = Code.code[elsePtr] + updateConn;
		}
		if(Lexer.nextToken==Token.KEY_ELSE){
			//elsePtr = Code.codeptr;
			Code.gen(1, "goto ");
			Code.bytes = Code.bytes + 2;
			Lexer.lex();
			cmpdstmt2 = new Cmpdstmt();
			//Code.code[elsePtr] = Code.code[elsePtr] + Code.bytes;
		}
	}
}

class Loop {
	Rexpr rexpr;
	Cmpdstmt cmpdstmt;
	public Loop(){
		Lexer.lex();
		rexpr = new Rexpr();
		Lexer.lex();
		cmpdstmt = new Cmpdstmt();
	}
}

class Cmpdstmt {
	Stmts stmts;
	public Cmpdstmt(){	
		Lexer.lex();
		stmts = new Stmts(Token.RIGHT_BRACE);
	}
}

class Rexpr {
	Expr e1;
	Expr e2;
	static Stack<Integer> codePtr = new Stack<Integer>();
	static String codeGen;
	public Rexpr(){
		Lexer.lex();
		e1 = new Expr();

		switch (Lexer.nextToken) {
		case Token.ASSIGN_OP:     
			codeGen = "if_icmpne ";
			break;
		case Token.GREATER_OP:     
			codeGen = "if_icmple ";
			break;	  
		case Token.LESSER_OP:     
			codeGen = "if_icmpge ";
			break;  
		case Token.NOT_EQ:     
			codeGen = "if_icmpeq ";
			break; 
		} 
		Lexer.lex();
		e2 = new Expr();
		codePtr.push(Code.codeptr);
		Code.gen(1, codeGen);
		Code.bytes = Code.bytes + 2;
	}
}

class Expr {// expr -> term (+ | -) expr | term
	Term t;
	Expr e;
	char op;

	public Expr() {
		t = new Term();
		if (Lexer.nextToken == Token.ADD_OP || Lexer.nextToken == Token.SUB_OP) {
			op = Lexer.nextChar;
			Lexer.lex();
			e = new Expr();
			Code.gen(1,Code.opcode(op));
		}
	}
}

class Term { // term -> factor (* | /) term | factor
	Factor f;
	Term t;
	char op;

	public Term() {
		f = new Factor();
		if (Lexer.nextToken == Token.MULT_OP || Lexer.nextToken == Token.DIV_OP) {
			op = Lexer.nextChar;
			Lexer.lex();
			t = new Term();
			Code.gen(1,Code.opcode(op));
		}
	}
}

class Factor { // factor -> number | '(' expr ')'
	Expr e;
	int i;

	public Factor() {
		switch (Lexer.nextToken) {
		case Token.INT_LIT: // number
			i = Lexer.intValue;
			Lexer.lex();
			if( i <=5 ) {
				Code.gen(1,"iconst_" + i);
			} else if( i >= 6 && i<128 ){
				Code.gen(2,"bipush  " + i);
			} else {
				Code.gen(3,"sipush  " + i);
			}
			break;
		case Token.ID:
			int i = 0;

			for(char ch : Idlist.id) {
				if(ch == Lexer.ident) {
					Code.gen(1, "iload_" + i);
					break;
				} else {
					i = i + 1;
				}
			}

			Lexer.lex();
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

class Code {
	static String[] code = new String[100];
	static int codeptr = 0;
	static int bytes = 0;
	public static void gen(int byt, String s) {
		code[codeptr] = bytes + ": " + s;
		codeptr++;
		bytes = bytes + byt;
	}

	public static String opcode(char op) {
		switch(op) {
		case '+' : return "iadd";
		case '-':  return "isub";
		case '*':  return "imul";
		case '/':  return "idiv";
		default: return "";
		}
	}

	public static void output() {
		
		System.out.println("\nJava Byte Codes are :\n");
		
		for (int i=0; i<codeptr; i++)
			System.out.println(code[i]);
	}
}


