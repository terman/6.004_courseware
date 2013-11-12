// Copyright (C) 2000-2001 Christopher J. Terman - All Rights Reserved.

package bsim;

import gui.EditBuffer;
import gui.GuiFrame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class Program extends EditBuffer implements ActionListener {
    // bits found in character info array cinfo[]
    public final static int SPC = 0x1;	// space
    public final static int EOL = 0x2;	// end of line
    public final static int D = 0x4;	// digit
    public final static int S = 0x8;	// can start symbol
    public final static int T = 0x10;	// can be part of symbol

    // small info table for each input character
    public final static int[] cinfo = {
	EOL,	0,	0,	0,	0,	0,	0,	0,
	0,	SPC,	EOL,	SPC,	SPC,	SPC,	0,	0,
	0,	0,	0,	0,	0,	0,	0,	0,
	0,	0,	0,	0,	0,	0,	0,	0,
	SPC,	0,	0,	0,	S+T,	0,	0,	0,
	0,	0,	0,	0,	0,	0,	S+T,	0,
	D+T,	D+T,	D+T,	D+T,	D+T,	D+T,	D+T,	D+T,
	D+T,	D+T,	0,	0,	0,	0,	0,	0,
	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,
	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,
	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,
	S+T,	S+T,	S+T,	0,	0,	0,	0,	S+T,
	0,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,
	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,
	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,	S+T,
	S+T,	S+T,	S+T,	0,	EOL,	0,	0,	0
    };

    Beta beta;			// where to send binary
    Hashtable symbols;		// symbol table used by assembler
    Symbol dot;			// current assembler location in memory 
    int maxDot;			// maximum value for "."
    boolean usesDot;		// true if expression uses "."
    int pass;			// which assembly pass we're on
    String sText;		// current input
    int sLength;		// length of current input
    int sOffset;		// where we are in current input
    int sStart;			// where line started
    EditBuffer sBuffer;		// which buffer is being scanned
    String sPrefix;		// macro pathname
    boolean errors;		// true if errors have been detected
    boolean writeable;		// true if assembled bytes are writeable

    public String checkoffServer;
    public String assignment;
    public Integer checkoffChecksum;
    public String checkoffType;
    boolean outputChecksum;
    boolean dumpMemory,mverify;
    public Vector verifications;

    public Program(GuiFrame parent,File source) {
	super(parent,source);
    }

    public void actionPerformed(ActionEvent event) {
	String a = event.getActionCommand();

	if (a.equals(BSim.ASSEMBLE)) {
	    Beta b = ((BSim)parent).GetBeta(this);
	    b.StopReset();
	    Assemble(b);
	    if (!errors) {
		Message("assembly complete, "+maxDot+" bytes output");
		((BSim)parent).SetPlot(b);
	    }
	} else if (a.equals(BSim.A2FILE)) {
	    Beta b = ((BSim)parent).GetBeta(this);
	    if (dot == null) Assemble(b);
	    if (!errors) {
		String fname = source.getName();
		if (fname.endsWith(".uasm"))
		    fname = fname.substring(0,fname.length()-5);
		try {
		    File bin = new File(source.getParentFile(),fname+".bin");
		    FileOutputStream fbin = new FileOutputStream(bin);
		    for (int i = 0; i < maxDot; i += 1)
			fbin.write(b.ReadMemoryByte(i));
		    fbin.close();

		    File contents = new File(source.getParentFile(),fname+".contents");
		    PrintWriter s = new PrintWriter(new FileOutputStream(contents));
		    for (int i = 0; i < maxDot; i += 4) {
			if (i % 32 == 0) s.print("\n+");
			s.print(" 0x"+Integer.toHexString(b.ReadMemory(i)));
		    }
		    s.print("\n");
		    s.close();

		    File coe = new File(source.getParentFile(),fname+".coe");
		    PrintWriter c = new PrintWriter(new FileOutputStream(coe));
		    c.print("memory_initialization_radix=16;\n");
		    c.print("memory_initialization_vector=\n");
		    for (int i = 0; i < maxDot; i += 4) {
			String n = Integer.toHexString(b.ReadMemory(i));
			if (n.length() < 8)
			    n = "00000000".substring(0,8-n.length()) + n;
			c.print(n);
			c.print(",\n");
		    }
		    c.print("0;\n");
		    c.close();

		    Message(maxDot+" bytes output to "+fname+".bin and "+fname+".contents and "+fname+".coe");
		}
		catch (Exception e) {
		    Message("exception while writing .bin or .contents file: "+e);
		}
	    }
	}
    }

    /*
    private void InitializeSymbols() {
	symbols = new Hashtable();
	dot = new Symbol(symbols,".",0);

	// symbols from beta.uasm
	new Symbol(symbols,"r0",0); new Symbol(symbols,"R0",0);
	new Symbol(symbols,"r1",1); new Symbol(symbols,"R1",1);
	new Symbol(symbols,"r2",2); new Symbol(symbols,"R2",2);
	new Symbol(symbols,"r3",3); new Symbol(symbols,"R3",3);
	new Symbol(symbols,"r4",4); new Symbol(symbols,"R4",4);
	new Symbol(symbols,"r5",5); new Symbol(symbols,"R5",5);
	new Symbol(symbols,"r6",6); new Symbol(symbols,"R6",6);
	new Symbol(symbols,"r7",7); new Symbol(symbols,"R7",7);
	new Symbol(symbols,"r8",8); new Symbol(symbols,"R8",8);
	new Symbol(symbols,"r9",9); new Symbol(symbols,"R9",9);
	new Symbol(symbols,"r10",10); new Symbol(symbols,"R10",10);
	new Symbol(symbols,"r11",11); new Symbol(symbols,"R11",11);
	new Symbol(symbols,"r12",12); new Symbol(symbols,"R12",12);
	new Symbol(symbols,"r13",13); new Symbol(symbols,"R13",13);
	new Symbol(symbols,"r14",14); new Symbol(symbols,"R14",14);
	new Symbol(symbols,"r15",15); new Symbol(symbols,"R15",15);
	new Symbol(symbols,"r16",16); new Symbol(symbols,"R16",16);
	new Symbol(symbols,"r17",17); new Symbol(symbols,"R17",17);
	new Symbol(symbols,"r18",18); new Symbol(symbols,"R18",18);
	new Symbol(symbols,"r19",19); new Symbol(symbols,"R19",19);
	new Symbol(symbols,"r20",20); new Symbol(symbols,"R20",20);
	new Symbol(symbols,"r21",21); new Symbol(symbols,"R21",21);
	new Symbol(symbols,"r22",22); new Symbol(symbols,"R22",22);
	new Symbol(symbols,"r23",23); new Symbol(symbols,"R23",23);
	new Symbol(symbols,"r24",24); new Symbol(symbols,"R24",24);
	new Symbol(symbols,"r25",25); new Symbol(symbols,"R25",25);
	new Symbol(symbols,"r26",26); new Symbol(symbols,"R26",26);
	new Symbol(symbols,"r27",27); new Symbol(symbols,"R27",27);
	new Symbol(symbols,"r28",28); new Symbol(symbols,"R28",28);
	new Symbol(symbols,"r29",29); new Symbol(symbols,"R29",29);
	new Symbol(symbols,"r30",30); new Symbol(symbols,"R30",30);
	new Symbol(symbols,"r31",31); new Symbol(symbols,"R31",31);
	new Symbol(symbols,"bp",27); new Symbol(symbols,"BP",27);
	new Symbol(symbols,"lp",28); new Symbol(symbols,"LP",28);
	new Symbol(symbols,"sp",29); new Symbol(symbols,"SP",29);
	new Symbol(symbols,"xp",30); new Symbol(symbols,"XP",30);

	new Symbol(symbols,"VEC_RESET",0);
	new Symbol(symbols,"VEC_II",4);
	new Symbol(symbols,"VEC_CLK",8);
	new Symbol(symbols,"VEC_KBD",12);
	new Symbol(symbols,"PC_SUPERVISOR",0x80000000);
	new Symbol(symbols,"PC_MASK",0x7fffffff);
    }
    */

    private void InitializeMacros() {
	for (Enumeration e = symbols.elements(); e.hasMoreElements();) {
	    Symbol s = (Symbol)e.nextElement();
	    s.ClearMacroDefinitions();
	}
	sPrefix = "";

	/*
	// macro definitions from beta.uasm
	new Macro(symbols,"WORD","x","x%256 (x>>8)%256");
	new Macro(symbols,"LONG","x","WORD(x) WORD(x>>16)");
	new Macro(symbols,"STORAGE","NWORDS",". = .+(4*NWORDS)");

	new Macro(symbols,"betaop","OP","RA","RB","RC",".align 4\nLONG((OP<<26)+((RC%32)<<21)+((RA%32)<<16)+((RB%32)<<11))");
	new Macro(symbols,"betaopc","OP","RA","CC","RC",".align 4\nLONG((OP<<26)+((RC%32)<<21)+((RA%32)<<16)+(CC % 0x10000))");
	new Macro(symbols,"betabr","OP","RA","RC","LABEL","betaopc(OP,RA,((LABEL-.)>>2)-1, RC)");

	new Macro(symbols,"ADD","RA","RB","RC","betaop(0x20,RA,RB,RC)");
	new Macro(symbols,"ADDC","RA","C","RC","betaopc(0x30,RA,C,RC)");
	new Macro(symbols,"AND","RA","RB","RC","betaop(0x28,RA,RB,RC)");
	new Macro(symbols,"ANDC","RA","C","RC","betaopc(0x38,RA,C,RC)");
	new Macro(symbols,"MUL","RA","RB","RC","betaop(0x22,RA,RB,RC)");
	new Macro(symbols,"MULC","RA","C","RC","betaopc(0x32,RA,C,RC)");
	new Macro(symbols,"OR","RA","RB","RC","betaop(0x29,RA,RB,RC)");
	new Macro(symbols,"ORC","RA","C","RC","betaopc(0x39,RA,C,RC)");
	new Macro(symbols,"SHL","RA","RB","RC","betaop(0x2C,RA,RB,RC)");
	new Macro(symbols,"SHLC","RA","C","RC","betaopc(0x3C,RA,C,RC)");
	new Macro(symbols,"SHR","RA","RB","RC","betaop(0x2D,RA,RB,RC)");
	new Macro(symbols,"SHRC","RA","C","RC","betaopc(0x3D,RA,C,RC)");
	new Macro(symbols,"SRA","RA","RB","RC","betaop(0x2E,RA,RB,RC)");
	new Macro(symbols,"SRAC","RA","C","RC","betaopc(0x3E,RA,C,RC)");
	new Macro(symbols,"SUB","RA","RB","RC","betaop(0x21,RA,RB,RC)");
	new Macro(symbols,"SUBC","RA","C","RC","betaopc(0x31,RA,C,RC)");
	new Macro(symbols,"XOR","RA","RB","RC","betaop(0x2A,RA,RB,RC)");
	new Macro(symbols,"XORC","RA","C","RC","betaopc(0x3A,RA,C,RC)");
	new Macro(symbols,"XNOR","RA","RB","RC","betaop(0x2B,RA,RB,RC)");
	new Macro(symbols,"XNORC","RA","C","RC","betaopc(0x3B,RA,C,RC)");
	new Macro(symbols,"CMPEQ","RA","RB","RC","betaop(0x24,RA,RB,RC)");
	new Macro(symbols,"CMPEQC","RA","C","RC","betaopc(0x34,RA,C,RC)");
	new Macro(symbols,"CMPLE","RA","RB","RC","betaop(0x26,RA,RB,RC)");
	new Macro(symbols,"CMPLEC","RA","C","RC","betaopc(0x36,RA,C,RC)");
	new Macro(symbols,"CMPLT","RA","RB","RC","betaop(0x25,RA,RB,RC)");
	new Macro(symbols,"CMPLTC","RA","C","RC","betaopc(0x35,RA,C,RC)");
	new Macro(symbols,"BEQ","RA","LABEL","RC","betabr(0x1C,RA,RC,LABEL)");
	new Macro(symbols,"BEQ","RA","LABEL","betabr(0x1C,RA,R31,LABEL)");
	new Macro(symbols,"BF","RA","LABEL","RC","BEQ(RA,LABEL,RC)");
	new Macro(symbols,"BF","RA","LABEL","BEQ(RA,LABEL)");
	new Macro(symbols,"BNE","RA","LABEL","RC","betabr(0x1D,RA,RC,LABEL)");
	new Macro(symbols,"BNE","RA","LABEL","betabr(0x1D,RA,R31,LABEL)");
	new Macro(symbols,"BT","RA","LABEL","RC","BNE(RA,LABEL,RC)");
	new Macro(symbols,"BT","RA","LABEL","BNE(RA,LABEL)");
	new Macro(symbols,"BR","LABEL","RC","BEQ(R31, LABEL,RC)");
	new Macro(symbols,"BR","LABEL","BR(LABEL,R31)");
	new Macro(symbols,"JMP","RA","RC","betaopc(0x1B,RA,0,RC)");
	new Macro(symbols,"JMP","RA","betaopc(0x1B,RA,0,R31)");
	new Macro(symbols,"LD","RA","CC","RC","betaopc(0x18,RA,CC,RC)");
	new Macro(symbols,"LD","CC","RC","betaopc(0x18,R31,CC,RC)");
	new Macro(symbols,"ST","RC","CC","RA","betaopc(0x19,RA,CC,RC)");
	new Macro(symbols,"ST","RC","CC","betaopc(0x19,R31,CC,RC)");
	new Macro(symbols,"LDR","CC","RC","betabr(0x1F,0,RC,CC)");

	new Macro(symbols,"MOVE","RA","RC","ADD(RA,R31,RC)");
	new Macro(symbols,"CMOVE","CC","RC","ADDC(R31,CC,RC)");
	new Macro(symbols,"PUSH","RA","ADDC(SP,4,SP) ST(RA,-4,SP)");
	new Macro(symbols,"POP","RA","LD(SP,-4,RA) ADDC(SP,-4,SP)");
	new Macro(symbols,"CALL","LABEL","BR(LABEL,LP)");
	new Macro(symbols,"RTN","JMP(LP)");
	new Macro(symbols,"XRTN","JMP(XP)");
	new Macro(symbols,"NOP","ADD(R31,R31,R31)");

	new Macro(symbols,"GETFRAME","OFFSET","REG","LD(BP,OFFSET,REG)");
	new Macro(symbols,"PUTFRAME","REG","OFFSET","ST(REG,OFFSET,BP)");
	new Macro(symbols,"CALL","S","N","BR(S,LP) SUBC(SP,4*N,SP)");
	new Macro(symbols,"ALLOCATE","N","ADDC(SP,N*4,SP)");
	new Macro(symbols,"DEALLOCATE","N","SUBC(SP,N*4,SP)");

	new Macro(symbols,"PRIV_OP","FNCODE","betaopc(0x00,0,FNCODE,0)");
	new Macro(symbols,"HALT","PRIV_OP(0)");
	new Macro(symbols,"RDCHAR","PRIV_OP(1)");
	new Macro(symbols,"WRCHAR","PRIV_OP(2)");
	new Macro(symbols,"RDSWT","PRIV_OP(3)");
	new Macro(symbols,"BPT","PRIV_OP(20)");
	new Macro(symbols,"SVC","CODE","betaopc(0x01,0,CODE,0)");
	*/
    }

    private void SkipBlanks() {
	while (sOffset < sLength && cinfo[sText.charAt(sOffset)] == SPC)
	    sOffset += 1;
    }

    private void SkipToken() {
	while (sOffset < sLength && (cinfo[sText.charAt(sOffset)] & T) != 0)
	    sOffset += 1;
    }

    private boolean CheckForChar(int ch) {
	SkipBlanks();
	if (sOffset < sLength && sText.charAt(sOffset) == ch) {
	    sOffset += 1;
	    return true;
	}
	return false;
    }

    private void AssembleByte(int v) {
	if (pass ==2 && beta != null)
	    beta.WriteMemoryByte(dot.value,v,writeable);
	dot.value += 1;
	if (dot.value > maxDot) maxDot = dot.value;
    }

    
    // read up to 3 octal digits and assemble into an int.
    // first digit is passed as the argument.
    private int AssembleOctalDigits(int ch) {
	int result = ch - '0';

	if (sOffset < sLength) {
	    // second octal digit?
	    ch = sText.charAt(sOffset);
	    if (ch >= '0' && ch <= '7') {
		sOffset += 1;
		result = result*8 + ch - '0';
		if (sOffset < sLength) {
		    // third octal digit?
		    ch = sText.charAt(sOffset);
		    if (ch >= '0' && ch <= '7') {
			sOffset += 1;
			result = result*8 + ch - '0';
		    }
		}
	    }
	}

	return result;
    }

    private void AssembleString() {
	if (CheckForChar('"')) {
	loop:
	    while (sOffset < sLength) {
		int ch = sText.charAt(sOffset++);
		switch (ch) {
		case '"':
		    return;
		case '\n':
		    break loop;
		case '\\':
		    if (sOffset < sLength) ch = sText.charAt(sOffset++);
		    switch (ch) {
		    case 'b':	ch = '\b'; break;
		    case 'f':	ch = '\f'; break;
		    case 'n':	ch = '\n'; break;
		    case 'r':	ch = '\r'; break;
		    case 't':	ch = '\t'; break;
		    case '\\':	ch = '\\'; break;
		    default:
			if (ch >= '0' && ch <= '7')
			    ch = AssembleOctalDigits(ch);
		    }
		    // fall through
		default:
		    AssembleByte(ch);
		    break;
		}		
	    }
	    ReportError("unterminated string constant");
	}
    }

    private void ReportError(String msg) {
	if (!errors) {
	    int eol = sText.indexOf('\n',sStart);
	    if (eol == -1) eol = sLength;
	    if (sPrefix.length() > 0) msg = "[" + sPrefix + "] " + msg;
	    Message(sBuffer,sStart,eol,msg);
	    errors = true;
	}
    }

    private String ReadString() {
	StringBuffer result = new StringBuffer();
	SkipBlanks();
	if (!CheckForChar('"'))
	    ReportError("expected double-quote as start of string");

	loop:
	    while (sOffset < sLength) {
		int ch = sText.charAt(sOffset++);
		switch (ch) {
		case '"':
		    return result.toString();
		case '\n':
		    break loop;
		case '\\':
		    if (sOffset < sLength) ch = sText.charAt(sOffset++);
		    switch (ch) {
		    case 'b':	ch = '\b'; break;
		    case 'f':	ch = '\f'; break;
		    case 'n':	ch = '\n'; break;
		    case 'r':	ch = '\r'; break;
		    case 't':	ch = '\t'; break;
		    }
		    // fall through
		default:
		    result.append((char)ch);
		    break;
		}		
	    }
	    ReportError("unterminated string constant");
	    return result.toString();
    }

    private Integer ReadTerm() {
	Integer result = null;
	SkipBlanks();

	if (sOffset >= sLength) return null;
	int ch = sText.charAt(sOffset);
	int info = cinfo[ch];

	if ((info & D) != 0) {		// number
	    int base = 10;
	    int start = sOffset;
	    SkipToken();
	    String number = sText.substring(start,sOffset);

	    if (number.startsWith("0x") || number.startsWith("0X")) {
		base = 16;
		number = number.substring(2);
	    } else if (number.startsWith("0b") || number.startsWith("0B")) {
		base = 2;
		number = number.substring(2);
	    } else if (number.startsWith("0")) {
		if (number.length() > 1) {
		    base = 8;
		    number = number.substring(1);
		}
	    }

	    try {
		// use longs to avoid exceptions on 0x80000000
		long lresult = Long.parseLong(number,base);
		result = new Integer((int)lresult);
	    }
	    catch (NumberFormatException e) {
		ReportError("bad number format: "+number);
	    }
	} else if ((info & S) != 0) {	// symbol
	    int start = sOffset;
	    SkipToken();
	    String sname = sText.substring(start,sOffset);
	    Symbol s = Symbol.Lookup(symbols,sname,true);
	    if (pass == 2 && s.type == Symbol.UNDEF)
		ReportError("undefined symbol "+sname);
	    else {
		result = new Integer(s.value);
		//System.out.println(sname+"="+Integer.toHexString(s.value));
	    }
	    if (s == dot) usesDot = true;
	} else if (ch == '\'') {	// char constant
	    sOffset += 3;
	    if (sOffset < sLength) {
		if (sText.charAt(sOffset - 2) == '\\') {
		    sOffset += 1;
		    ch = sText.charAt(sOffset - 2);
		    switch (ch) {
		    case 'b':	ch = '\b'; break;
		    case 'f':	ch = '\f'; break;
		    case 'n':	ch = '\n'; break;
		    case 'r':	ch = '\r'; break;
		    case 't':	ch = '\t'; break;
		    }
		} else ch = sText.charAt(sOffset - 2);
		if (sOffset < sLength & sText.charAt(sOffset - 1) == '\'')
		    return new Integer(ch);
	    }
	    // bad character constant
	    ReportError("bad character constant");
	} else if (ch == '-') {		// unary minus
	    sOffset += 1;
	    Integer v = ReadTerm();
	    if (v != null) result = new Integer(-v.intValue());
	} else if (ch == '~') {		// complement
	    sOffset += 1;
	    Integer v = ReadTerm();
	    if (v != null) result = new Integer(~v.intValue());
	} else if (ch == '(') {		// parenthesized expression
	    sOffset += 1;
	    Integer v = ReadExpression();
	    if (v != null) {
		SkipBlanks();
		if (sOffset >= sLength || sText.charAt(sOffset) != ')')
		    ReportError("unbalanced parenthesis in expression");
		else {
		    sOffset += 1;
		    result = v;
		}
	    }
	} else ReportError("Illegal term in expression: "+sText.substring(sOffset,Math.min(sLength,sOffset+10)));
	return result;
    }

    private Integer ReadExpression() {
	//System.out.println("ReadExpression: "+sText.substring(sOffset,Math.min(sLength,sOffset+10)));

	Integer v1 = ReadTerm();
	Integer v2;

    loop:
	while (v1 != null) {
	    SkipBlanks();
	    if (sOffset >= sLength) break;
	    //System.out.println("  next: "+sText.substring(sOffset,Math.min(sLength,sOffset+10)));

	    switch (sText.charAt(sOffset++)) {
	    case '+':
		v2 = ReadTerm();
		if (v2 == null) v1 = null;
		else v1 = new Integer(v1.intValue() + v2.intValue());
		continue;
	    case '-':
		v2 = ReadTerm();
		if (v2 == null) v1 = null;
		else v1 = new Integer(v1.intValue() - v2.intValue());
		continue;
	    case '*':
		v2 = ReadTerm();
		if (v2 == null) v1 = null;
		else v1 = new Integer(v1.intValue() * v2.intValue());
		continue;
	    case '/':
		v2 = ReadTerm();
		if (v2 == null) v1 = null;
		else v1 = new Integer(v1.intValue() / v2.intValue());
		continue;
	    case '%':
		v2 = ReadTerm();
		if (v2 == null) v1 = null;
		else {
		    int v = v1.intValue() % v2.intValue();
		    if (v < 0) v += v2.intValue();
		    v1 = new Integer(v);
		}
		continue;
	    case '>':
		if (CheckForChar('>')) {
		    v2 = ReadTerm();
		    if (v2 == null) v1 = null;
		    else v1 = new Integer(v1.intValue() >> v2.intValue());
		    continue;
		}
		sOffset -= 1;
		break loop;
	    case '<':
		if (CheckForChar('<')) {
		    v2 = ReadTerm();
		    if (v2 == null) v1 = null;
		    else v1 = new Integer(v1.intValue() << v2.intValue());
		    continue;
		}
		sOffset -= 1;
		break loop;
	    default:
		sOffset -= 1;
		break loop;
	    }
	}
	return v1;
    }

    private void ReadOperand() {
	SkipBlanks();

	if ((cinfo[sText.charAt(sOffset)] & S) != 0) {
	    int start = sOffset;
	    SkipToken();
	    int end = sOffset;
	    if (CheckForChar('(')) {			// macro call
		String mname = sText.substring(start,end);

		// evaluate arguments
		Vector args = new Vector();
		while (true) {
		    if (CheckForChar(')')) break;
		    CheckForChar(',');
		    Integer v = ReadExpression();
		    if (v != null) args.addElement(v);
		    else {
			ReportError("expression or close paren expected");
			return;
		    }
		}

		// lookup macro definition
		Symbol s = Symbol.Lookup(symbols,mname);
		int nargs = args.size();
		Macro m = s.LookupMacro(nargs);
		if (m == null) {
		    // undefined macro
		    ReportError("can't find macro definition for "+mname+" with "+args.size()+" arguments");
		    return;
		} else if (m.called) {
		    // recursive call to macro
		    ReportError("recursive call to macro "+mname);
		    return;
		}

		// process macro call
		m.called = true;
		int[] savedValues = new int[nargs];
		int[] savedTypes = new int[nargs];
		for (int i = 0; i < nargs; i += 1) {
		    Symbol p = (Symbol)m.params.elementAt(i);
		    savedValues[i] = p.value;
		    savedTypes[i] = p.type;
		    p.value = ((Integer)args.elementAt(i)).intValue();
		    p.type = Symbol.ASSIGN;
		}
		String savedPrefix = sPrefix;
		if (sPrefix.length() > 0) sPrefix += ","+mname;
		else sPrefix = mname;
		Scan(null,m.body);
		sPrefix = savedPrefix;
		m.called = false;
		for (int i = 0; i < nargs; i += 1) {
		    Symbol p = (Symbol)m.params.elementAt(i);
		    p.value = savedValues[i];
		    p.type = savedTypes[i];
		}

		return;
	    }
	    sOffset = start;
	}

	// ordinary expression
	Integer v = ReadExpression();
	if (v != null) AssembleByte(v.intValue());
	else ReportError("illegal operand");
    }

    private void ReadMacro() {
	// gobble down name of macro
	int start = sOffset;
	SkipToken();
	if (sOffset == start) {
	    ReportError("expected name following .macro");
	    return;
	}
	Symbol mname = Symbol.Lookup(symbols,sText.substring(start,sOffset),true);

	// see if parenthesized list follows.  If it does, each entry
	// should be a symbol
	Vector params = new Vector();
	if (CheckForChar('(')) {
	    while (true) {
		if (CheckForChar(')')) break;
		if (sOffset >= sLength) {
		    ReportError("expected ')' in macro definition");
		    return;
		}
		int ch = sText.charAt(sOffset);
		if ((cinfo[sText.charAt(sOffset)] & S) == 0) {
		    ReportError("symbol expected in macro parameter list");
		    return;
		}
		start = sOffset;
		SkipToken();
		Symbol s = Symbol.Lookup(symbols,sText.substring(start,sOffset),true);
		params.addElement(s);
		SkipBlanks();
		if (sOffset < sLength && sText.charAt(sOffset) == ',')
		    sOffset += 1;
	    }
	}

	// read body of macro
	int end;
	if (CheckForChar('{')) end = sText.indexOf('}',sOffset);
	else end = sText.indexOf('\n',sOffset);
	if (end == -1) end = sLength;
	String body = sText.substring(sOffset,end);
	sOffset = end + 1;

	//System.out.println("macro "+mname.name+" params="+params.size()+" body="+body);

	// complete definition
	if (mname.LookupMacro(params.size()) != null)
	    ReportError("redefinition of macro "+mname.name);
	else new Macro(mname,body,params);
    }

    private void Scan(EditBuffer buffer,String s) {
	EditBuffer savedBuffer = sBuffer;
	String savedText = sText;
	int savedLength = sLength;
	int savedOffset = sOffset;
	boolean updateStart = false;

	// System.out.println("Scan "+s);

	// set up new input
	sText = s;
	sLength = s.length();
	sOffset = 0;

	if (buffer != null) {
	    sBuffer = buffer;
	    sStart = 0;
	    updateStart = true;
	}

	while (sOffset < sLength & !errors) {
	    SkipBlanks();

	    // if we're not at the end of a line there's more to process
	    if (sOffset < sLength && (cinfo[sText.charAt(sOffset)] & EOL) == 0) {
		SkipBlanks();
		int start = sOffset;
		SkipToken();
		String token = sText.substring(start,sOffset);
		SkipBlanks();
		int ch;

		if (sOffset < sLength) ch = sText.charAt(sOffset);
		else ch = 0;

		// look for special forms
		if (token.equals(".macro")) {
		    ReadMacro();
		    continue;
		} else if (token.equals(".include")) {
		    int istart = sOffset;
		    while (sOffset < sLength) {
			int ich = sText.charAt(sOffset);
			if (ich != '.' &&
			    (cinfo[ich] == SPC || cinfo[ich] == EOL)) break;
			sOffset += 1;
		    }
		    String iname = sText.substring(istart,sOffset);
		    if (iname.length() == 0)
			ReportError("expected filename following .include");
		    else {
			if (source != null)
			    iname = parent.MergePathnames(source,iname);
			File ifile = new File(iname);
			try {
			    if (ifile.exists()) {
				EditBuffer ibuffer = parent.FindBuffer(ifile);
				Scan(ibuffer,ibuffer.getText());
			    } else
				ReportError("Can't read included file: "+ifile.getCanonicalPath()+" not found");
			}
			catch (Exception e) {
			    ReportError("Can't read included file: "+e);
			}
		    }
		    continue;
		} else if (token.equals(".align")) {
		    int align = 4;
		    if (cinfo[ch] != EOL) {
			Integer v = ReadExpression();
			if (v != null) align = v.intValue();
		    }
		    while ((dot.value % align) != 0) AssembleByte(0);
		    continue;
		} else if (token.equals(".text")) {
		    AssembleString();
		    AssembleByte(0);
		    while ((dot.value % 4) != 0) AssembleByte(0);
		    continue;
		} else if (token.equals(".ascii")) {
		    AssembleString();
		    continue;
		} else if (token.equals(".breakpoint")) {
		    if (pass ==2 && beta != null) beta.SetBreakpoint(dot.value);
		    continue;
		} else if (token.equals(".protect")) {
		    writeable = false;
		    continue;
		} else if (token.equals(".unprotect")) {
		    writeable = true;
		    continue;
		} else if (token.equals(".options")) {
		    while (true) {
			SkipBlanks();
			if ((cinfo[sText.charAt(sOffset)] & EOL) != 0) break;
			start = sOffset;
			SkipToken();
			String option = sText.substring(start,sOffset);
			if (option.equals("plugh2536038"))
			    outputChecksum = true;
			else if (option.equals("dumpmemory"))
			    dumpMemory = true;
			else if (option.equals("mverify"))
			    mverify = true;
			else if (pass == 2 && beta != null)
			    beta.Option(option);
		    }
		    continue;
		} else if (token.equals(".pcheckoff") || token.equals(".tcheckoff")) {
		    //.pcheckoff url projectname checksum
		    //.tcheckoff url projectname checksum
		    checkoffServer = ReadString();
		    assignment = ReadString();
		    checkoffChecksum = ReadTerm();
		    checkoffType = token;
		    continue;
		} else if (token.equals(".verify")) {
		    //.verify maddr nwords v0 v1...
		    Vector vdata = new Vector();
		    if (verifications != null) verifications.add(vdata);
		    vdata.add(ReadTerm());
		    Integer nwords = ReadTerm();
		    for (int i = nwords.intValue(); i > 0; i -= 1)
			vdata.add(ReadTerm());
		    continue;
		}

		// check for label and symbol definitions
		if (ch == ':') {
		    sOffset += 1;
		    Symbol label = Symbol.Lookup(symbols,token,true);
		    if (pass == 1) {
			if (label.type != Symbol.UNDEF)
			    ReportError("multiply defined symbol: "+token);
			else {
			    label.type = Symbol.LABEL;
			    label.value = dot.value;
			}
		    } else {
			if (label.value != dot.value)
			    ReportError("phase error in symbol definition: "+token);
			beta.DefineLabel(dot.value,label.name);
		    }
		    continue;
		} else if (ch == '=') {
		    sOffset += 1;
		    Symbol sym = Symbol.Lookup(symbols,token,true);
		    usesDot = false;
		    Integer v = ReadExpression();
		    if (v != null) {
			if (sym.type == Symbol.LABEL)
			    ReportError("illegal redefinition of symbol: "+token);
			else {
			    sym.type = Symbol.ASSIGN;
			    sym.value = v.intValue();
			    //System.out.println("define "+sym.name+" = "+sym.value);
			    if (sym == dot && dot.value > maxDot) maxDot = dot.value;
			}
		    }
		    continue;
		}

		// not a special form, so read operands and place their value
		// into memory
		sOffset = start;
		while (!errors && sOffset < sLength && (cinfo[sText.charAt(sOffset)] & EOL) == 0) {
		    ReadOperand();
		    SkipBlanks();
		    if (sOffset < sLength && sText.charAt(sOffset) == ',')
			sOffset += 1;
		}
	    }

	    // now at EOL marker (which might be the comment char)
	    // so skip until we find the real end of line
	    while (sOffset < sLength && sText.charAt(sOffset) != '\n')
		sOffset += 1;
	    sOffset += 1;	// skip newline
	    if (updateStart) sStart = sOffset;
	}

	// restore previous input
	sBuffer = savedBuffer;
	sText = savedText;
	sLength = savedLength;
	sOffset = savedOffset;
    }

    public void Assemble(Beta beta) {
	this.beta = beta;
	symbols = new Hashtable();
	dot = new Symbol(symbols,".",0);
	errors = false;
	checkoffServer = null;
	outputChecksum = false;
	dumpMemory = false;
        mverify = false;

	// first pass
	pass = 1;
	InitializeMacros();
	dot.value = 0;
	maxDot = 0;
	verifications = null;
	Message("starting pass 1");
	Scan(this,getText());

	if (!errors) {
	    if (beta != null) {
		beta.SetMemorySize(Math.max(128,(dot.value + 3) >> 2));
		beta.AssemblyStart();
	    }
	    // second pass
	    pass = 2;
	    InitializeMacros();
	    dot.value = 0;
	    maxDot = 0;
	    writeable = true;
	    verifications = new Vector();
	    Message("starting pass 2");
	    Scan(this,getText());
	    if (beta != null) beta.AssemblyFinish();
	}
    }

    public int Size() {
	return dot != null ? dot.value : 0;
    }

    // produce 8-character hex string
    public String hexify(int i) {
        String n = Integer.toHexString(i);
        if (n.length() < 8)
            n = "00000000".substring(0,8-n.length()) + n;
        return n;
    }

    public String Checkoff() {
	if (beta == null || beta.cycles == 0) {
	    return ("<font size=5>Oops...</font><p>Can't find any simulation results to verify... did you run assemble and run the program?");
	}

	if (mverify || dumpMemory) {
	    try {
		PrintWriter out = new PrintWriter(new FileOutputStream("checkoff_data"));
		int maddr = 0;
		int nwords = Size() >> 2;
                if (mverify) {
                    out.print(".mverify xmem 0\n");
                    while (nwords > 0) {
                        int addr = maddr;
                        out.print("+ ");
                        for (int j = 1; j <= 4; j += 1) {
                            int have = beta.ReadMemory(maddr);
                            maddr += 4;
                            out.print(" 0x"+hexify(have));
                            nwords -= 1;
                            if (nwords == 0) break;
                        }
                        out.print("   // Beta addr 0x"+
                                  Integer.toHexString(addr)+
                                  ", Mem addr 0x"+
                                  Integer.toHexString(addr/4)+
                                  "\n");
                    }
                } else {
                    while (nwords > 0) {
                        out.print(".verify 0x"+Integer.toHexString(maddr)+" 8");
                        for (int j = 1; j <= 8; j += 1) {
                            int have = beta.ReadMemory(maddr);
                            maddr += 4;
                            out.print(" 0x"+hexify(have));
                            nwords -= 1;
                            if (nwords == 0) break;
                        }
                        out.print("\n");
                    }
                }
		out.close();
	    }
	    catch (Exception e) {
	    }
	    return "Memory data written to file checkoff_data";
	}

	if (mverify || dumpMemory) {
	    try {
		PrintWriter out = new PrintWriter(new FileOutputStream("checkoff_data"));
		int maddr = 0;
		int nwords = Size() >> 2;
                if (mverify) {
                    out.print(".mverify xmem 0\n");
                    while (nwords > 0) {
                        int addr = maddr;
                        out.print("+ ");
                        for (int j = 1; j <= 4; j += 1, maddr += 4) {
                            int have = beta.ReadMemory(maddr);
                            out.print(" 0x"+hexify(have));
                            nwords -= 1;
                            if (nwords == 0) break;
                        }
                        out.print("   // Beta addr 0x"+
                                  Integer.toHexString(addr)+
                                  ", Mem addr 0x"+
                                  Integer.toHexString(addr/4)+
                                  "\n");
                    }
                } else {
                    while (nwords > 0) {
                        out.print(".verify 0x"+Integer.toHexString(maddr)+" 8");
                        for (int j = 1; j <= 8; j += 1, maddr += 4) {
                            int have = beta.ReadMemory(maddr);
                            out.print(" 0x"+hexify(have));
                            nwords -= 1;
                            if (nwords == 0) break;
                        }
                        out.print("\n");
                    }
                }
		out.close();
	    }
	    catch (Exception e) {
	    }
	    return "Memory data written to file checkoff_data";
	}

	if (checkoffServer == null) {
	    return ("<font size=5>Oops...</font><p>Can't find checkoff information... did you include the appropriate \"XXXcheckoff.uasm\" file which supplies the information needed to complete the checkoff?");
	}

	int chksum = checkoffChecksum.intValue();
	if (checkoffType.equals(".pcheckoff")) {
	    int vChecksum = 36038;
	    // verify specified memory locations
	    for (int i = verifications.size()-1; i >=0; i -= 1) {
		Vector vdata = (Vector)verifications.elementAt(i);
		int nwords = vdata.size();
		int maddr = ((Integer)vdata.elementAt(0)).intValue();
		for (int j = 1; j < nwords; j += 1, maddr += 4) {
		    int want = ((Integer)vdata.elementAt(j)).intValue();
		    int have = beta.ReadMemory(maddr);
		    if (want != have) {
			return
			    "<font size=5>Verification error...</font><tt><ul>"
			    +"<li>memory location: 0x"+Integer.toHexString(maddr)
			    +"<li>expected: 0x"+Integer.toHexString(want)
			    +"<li>actual: 0x"+Integer.toHexString(have)
			    +"</tt></ul>";
		    }
		    vChecksum += j*(maddr + have);
		}
	    }

	    if (outputChecksum) return("vChecksum="+vChecksum+"\n");

	    if (chksum != 0 && vChecksum != chksum) {
		return ("<font size=5>Verification error...</font><p>It appears that the checkoff information has been modified in some way.  Please verify that you are using the official checkoff file; contact 6004-labs@6004.lcs.mit.edu if you can't resolve the problem.");
	    }
	} else if (checkoffType.equals(".tcheckoff")) {
	    int vChecksum = beta.ttyChecksum();
	    if (outputChecksum) return("vChecksum="+vChecksum+"\n");
	    if (chksum != 0 && vChecksum != chksum) {
		return ("<font size=5>Verification error...</font><p>The test program did not type out the expected results.  Please check the lab writeup to see what type out is expected.");	    }
	}

	return null;	// checkoff okay to proceed
    }

    public void DumpSymbols() {
	if (symbols != null) 
	    for (Enumeration e = symbols.elements(); e.hasMoreElements();) {
		Symbol s = (Symbol)e.nextElement();
		System.out.println(s);
		for (Enumeration ee = s.mdefs.elements(); ee.hasMoreElements();) {
		    Macro m = (Macro)ee.nextElement();
		    System.out.println(m);
		}
	    }
    }
}
