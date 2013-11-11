// Copyright (C) 2000-2011 Christopher J. Terman - All Rights Reserved.

package bsim;

import gui.GuiFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class Beta extends JPanel implements ActionListener, Runnable, KeyListener, MouseListener, AdjustmentListener {
    public final static int MSIZE = 1024;	// initial size of memory, in words
    public final static String zeros = "00000000";

    public final static int N = 1;	// orientations
    public final static int NNE = 2;
    public final static int NE = 3;
    public final static int ENE = 4;
    public final static int E = 5;
    public final static int ESE = 6;
    public final static int SE = 7;
    public final static int SSE = 8;
    public final static int S = 9;
    public final static int SSW = 10;
    public final static int SW = 11;
    public final static int WSW = 12;
    public final static int W = 13;
    public final static int WNW = 14;
    public final static int NW = 15;
    public final static int NNW = 16;

    public final static int ILLOP = 0x00000004;	// illegal instruction trap
    public final static int INTERRUPT = 0x00000008;	// interrupt
    public final static int CLOCK_INTERRUPT = 0x00000001;
    public final static int KBD_INTERRUPT = 0x00000002;
    public final static int MOUSE_INTERRUPT = 0x00000004;
    public final static int CYCLES_PER_TICK = 10000;

    public final static Color HIDE = new Color(170,170,170);
    public final static Color SHOW = Color.black;

    public final static int DATAPATH = 0;
    public final static int PROGRAMMERS_PANEL = 1;
    public final static int NLOCS = 26;  // number of mem locations to display

    public Thread simulation;		// for background execution
    Runnable UpdateDisplay;
    GuiFrame message;
    int display;			// one of DATAPATH or PROGRAMMERS_PANEL

    JTextArea usertty;			// where tty input/output goes
    int mouseCoords;			// coords of last mouse click
    JScrollPane userscroll;
    StringBuffer ttyInput;		// buffer for incoming characters
    JScrollBar maddrScroll;		// scroll through main memory
    JScrollBar iaddrScroll;		// scroll through instructions

    public Memory memory;		// main memory (inst & data)
    public int[] initialContents;	// used to initialize memory
    public String[] instructions;	// disassembled instructions
    public boolean[] breakpoints;	// breakpoints, one per memory location
    public boolean[] writeable;		// r/w permission per location
    public String[] labels;		// location label
    public long cycles;			// cycle count
    public int interrupts;		// interrupt flags
    public Random random;		// support for RANDOM SVC
    public ArrayList serverInfo;	// values to be sent to checkoff server

    // options
    public boolean mul;		// MUL implemented?
    public boolean div;		// DIV implemented?
    public boolean clock;	// generate clock interrupts?
    public boolean tty;		// implement WRCHAR, RDCHAR?
    public boolean kalways;	// stay in kernel mode?
    public boolean annotate;	// label stack frames?
    public boolean trace;	// trace pc values?

    // filled in by Execute
    public int[] regs;		// 32 beta registers
    public int pc;		// PC[30:2]
    public int pcmsb;		// PC[31] = supervisor bit

    public int wdata;		// result to be written into regfile
    public int inst;		// current instruction
    public int ra;		// id[21:15]
    public int rb;		// id[15:11] or id[21:15]
    public int rc;		// regfile location to be written
    public int rd1;		// Regs[ra]
    public int rd2;		// Regs[rb]
    public int literal;		// sxt(id[15:0])
    public boolean werf;	// do we write regfile
    public int ma;		// memory address
    public int mdata;		// memory write data
    public boolean wr;		// do we write memory
    public int npc;		// next pc
    public int npcmsb;		// next pcmsb
    public boolean irq;		// interrupt taken this cycle

    public int iaddr;		// instruction address
    public int maddr;		// main memory address

    // filled in by Signals
    public int pcsel;		// control signals for PCSEL mux
    public int ra2sel;		// control signal for RA2SEL mux
    public int asel;		// control signal for ASEL mux
    public int bsel;		// control signal for BSEL mux
    public int wasel;		// control signal for WASEL mux
    public int z;		// rd1 == 0?
    public boolean needZ;	// Z used?
    public String alufn;	// ALU operation
    public int wdsel;		// control signals for WDSEL mux
    public int pcinc;		// output of pc incrementer
    public int offset;		// sxt(id[15:0]) << 2
    public int pcoffset;	// pcinc + offset
    public int alua;		// A input to ALU
    public int alub;		// B input to ALU
    public int alu;		// output of ALU
    public int mrd;		// read data from memory

    Font tFont;		// font used in drawing
    int tH,tW;
    int gridx;		// dimensions of grid used in drawing
    int gridy;
    int hx,hy;		// half grids...
    int baseline;	// offset for drawing characters in grid

    public Beta(GuiFrame parent,GuiFrame msg) {
	super();
	message = msg;
	addKeyListener(this);
	setLayout(new BorderLayout());

	// set up text area where WRCHAR data will appear
	usertty = new JTextArea(5,10);
	usertty.setEditable(false);
	usertty.setHighlighter(null);
	usertty.setBackground(Color.white);
	usertty.setFont(new Font("monospaced",Font.PLAIN,12));
	usertty.addKeyListener(this);
	usertty.addMouseListener(this);
	userscroll = new JScrollPane(usertty,
				     JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				     JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	userscroll.setBorder(BorderFactory.createLoweredBevelBorder());

	// scroll bar for main memory
	maddrScroll = new JScrollBar(JScrollBar.VERTICAL,0,7,0,MSIZE);
	maddrScroll.addAdjustmentListener(this);
	add(maddrScroll,BorderLayout.EAST);

	// scroll bar for instructions
	iaddrScroll = new JScrollBar(JScrollBar.VERTICAL,0,3,0,MSIZE);
	iaddrScroll.addAdjustmentListener(this);
	add(iaddrScroll,BorderLayout.WEST);

	// where data for RDCHAR comes from
	ttyInput = new StringBuffer();

	random = new Random();	// initialize random number generator
	serverInfo = new ArrayList();

	display = PROGRAMMERS_PANEL;
	tFont = null;
	tH = tW = -1;

	memory = new Memory(this);
	memory.setIconImage(parent.GetImageResource("/icons/bsim.gif"));
	memory.AddToolButton(parent.ImageButton("/icons/bstop.gif"),BSim.STOP,this);
	memory.AddToolButton(parent.ImageButton("/icons/breset.gif"),BSim.RESET,this);
	memory.AddToolButton(parent.ImageButton("/icons/brun.gif"),BSim.RUN,this);
        memory.AddToolButton(parent.ImageButton("/icons/bstep.gif"),BSim.STEP,this);

	regs = new int[32];

	simulation = null;
	UpdateDisplay = new Runnable() { public void run() { Update(); } };

	SetMemorySize(MSIZE);
	Reset();
    }

    public void doLayout() {
	super.doLayout();
	SetupScrollbars();
    }

    public void adjustmentValueChanged(AdjustmentEvent event) {
	int v = event.getValue();
	if (maddrScroll == event.getSource()) maddr = v << 2;
	else if (iaddrScroll == event.getSource()) iaddr = v << 2;
	repaint();
    }

    public void SetMemorySize(int nwords) {
	initialContents = null;
	nwords = memory.SetMemorySize(nwords);
	instructions = new String[nwords];
	breakpoints = new boolean[nwords];
	writeable = new boolean[nwords];
	labels = new String[nwords];
	maddrScroll.setMaximum(nwords + maddrScroll.getVisibleAmount() - 4);
	iaddrScroll.setMaximum(nwords + iaddrScroll.getVisibleAmount() - 2);
	Reset();
    }

    public void Update() {
	repaint();
	memory.Repaint();
    }

    public void keyPressed(KeyEvent event) { }
    public void keyReleased(KeyEvent event) { }
    public void keyTyped(KeyEvent event) {
	char key = event.getKeyChar();
	if (tty) {
	    synchronized (this) {
		ttyInput.append(key);
		interrupts |= KBD_INTERRUPT;
	    }
	}
    }

    public void mouseClicked(MouseEvent e) {
	if (tty) {
	    Point p = userscroll.getViewport().getViewPosition();
	    mouseCoords = ((e.getX() - p.x) << 16) + ((e.getY() - p.y) & 0xFFFF);
	    interrupts |= MOUSE_INTERRUPT;
	}
    }
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}

    public void AssemblyStart() {
	memory.Reset(null);

	mul = true;	// default options
	div = true;
	clock = false;
	tty = false;
	kalways = false;
	annotate = false;
	trace = false;

	int max = memory.Size();
	for (int i = 0; i < max; i += 1) {
	    breakpoints[i] = false;
	    writeable[i] = true;
	}
    }

    public void AssemblyFinish() {
	initialContents = memory.Copy();
	Reset();
    }

    public void Option(String option) {
	if (option.equals("mul")) mul = true;
	else if (option.equals("nomul")) mul = false;
	else if (option.equals("div")) div = true;
	else if (option.equals("nodiv")) div = false;
	else if (option.equals("clock")) clock = true;
	else if (option.equals("noclock")) clock = false;
	else if (option.equals("kalways")) kalways = true;
	else if (option.equals("nokalways")) kalways = false;
	else if (option.equals("annotate")) annotate = true;
	else if (option.equals("noannotate")) annotate = false;
	else if (option.equals("trace")) trace = true;
	else if (option.equals("notrace")) trace = false;
	else if (option.equals("tty")) {
	    tty = true;
	    add(userscroll,BorderLayout.SOUTH);
	    invalidate();
	} else if (option.equals("notty")) {
	    tty = false;
	    remove(userscroll);
	    invalidate();
	} else System.out.println("unrecognized option = "+option);
    }

    public int ttyChecksum() {
	return usertty.getText().hashCode() + 36038;
    }

    public void WriteMemory(int addr,int value,boolean writeable) {
	int offset = memory.WriteWord(addr,value);
	if (offset >= 0) {
	    instructions[offset] = null;	// clear disassembly cache
	    this.writeable[offset] = writeable;
	}
    }

    public void WriteMemoryByte(int addr,int value,boolean writeable) {
	int offset = memory.WriteByte(addr,value);
	if (offset >= 0) {
	    instructions[offset] = null;	// clear disassembly cache
	    this.writeable[offset] = writeable;
	}
    }

    public int ReadMemory(int addr) {
	return memory.ReadWord(addr);
    }

    public int ReadMemoryByte(int addr) {
	return memory.ReadByte(addr);
    }

    public int ReadRegister(int reg) {
	return regs[reg];
    }

    public void SetBreakpoint(int bpaddr) {
	breakpoints[bpaddr >> 2] = true;
    }

    public void DefineLabel(int addr,String label) {
	int index = addr >> 2;

	if (labels[index] == null) labels[index] = label;
    }

    public void actionPerformed(ActionEvent event) {
	String what = event.getActionCommand();

	if (what.equals(BSim.STEP)) SingleStep();
	else if (what.equals(BSim.RUN)) Run();
	else if (what.equals(BSim.STOP)) Stop();
	else if (what.equals(BSim.RESET)) Reset();
	else if (what.equals(BSim.CACHE)) memory.setVisible(true);
	else if (what.equals(BSim.DISPLAY)) {
	    display = (display == DATAPATH) ? PROGRAMMERS_PANEL : DATAPATH;
	    tFont = null;  // set up display again
	}
	Update();
    }

    public void Reset() {
	if (simulation == null) {
	    cycles = 0;
	    interrupts = 0;
	    for (int i = 0; i < 32; i += 1) regs[i] = 0;
	    memory.Reset(initialContents);
	    ttyInput.setLength(0);
	    mouseCoords = -1;
	    usertty.setText("");
	    message.Message("");
	    serverInfo.clear();

	    npc = 0;
	    npcmsb = 1;
	    werf = false;
	    wr = false;
	    ma = 0;

	    maddr = 0;
	    maddrScroll.setValue(maddr);
	    iaddr = 0;
	    iaddrScroll.setValue(iaddr);

	    SingleStep();
	}
    }

    public void SingleStep() {
	if (simulation == null) {
	    if (tty) requestFocus();
	    message.Message("");
	    memory.Message("");
	    Execute();
	}
    }

    public void StopReset() {
	Stop();
	while (simulation != null) Thread.yield();
	Reset();
    }

    synchronized public void Stop() {
	if (simulation != null) simulation.interrupt();
    }

    // perform each analysis in turn in the background
    public void run() {
	// give GUI first shot at the cpu
	Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

	maddrScroll.setVisible(false);
	iaddrScroll.setVisible(false);
	while (!Thread.interrupted()) {
	    if (Execute()) break;
	    int index = pc >> 2;
	    if (memory.ValidAddress(pc) && breakpoints[pc >> 2]) {
		message.Message("Stopped at breakpoint (cycle "+cycles+")");
		break;
	    }
	}
	maddrScroll.setVisible(true);
	iaddrScroll.setVisible(true);

	synchronized (this) {
	    simulation = null;
	    SwingUtilities.invokeLater(UpdateDisplay);
	}
    }

    public void Run() {
	if (simulation == null) {
	    if (tty) requestFocus();
	    message.Message("");
	    memory.Message("");
	    simulation = new Thread(this,"simulation");
	    simulation.start();
	}
    }

    private void Trap(int newpc) {
	rc = 30;
	wdata = (pcmsb << 31) | npc;
	npc = newpc;
	npcmsb = 1;
    }

    private boolean Execute() {
	cycles += 1;
	if (clock && cycles % CYCLES_PER_TICK == CYCLES_PER_TICK-1)
	    interrupts |= CLOCK_INTERRUPT;

	// update state
	pc = npc;
	pcmsb = npcmsb;
	if (werf) regs[rc] = wdata;
	regs[31] = 0;
	if (wr) {
	    int index = memory.CachedWriteWord(ma,mdata);
	    maddr = ma;
	    if (index >= 0) instructions[index] = null;
	}
	iaddr = pc;

	// start next instruction
	inst = memory.CachedReadWord(pc);
	rc = (inst >> 21) & 0x1F;
	werf = true;
	wr = false;
	ra = (inst >> 16) & 0x1F;
	rb = (inst >> 11) & 0x1F;
	rd1 = regs[ra];
	rd2 = regs[rb];
	literal = (inst << 16) >> 16;	// sign extended
	irq = false;

	/*
	  System.out.println("pc="+Integer.toHexString(pc)+
	  " op="+Integer.toHexString((inst >> 26) & 0x3F)+
	  " rd1="+Integer.toHexString(rd1)+
	  " rd2="+Integer.toHexString(rd2));
	*/

	npc = (pc + 4) & 0x7FFFFFFF;	// increment mod 2^31

	// check for interrupts when in user mode
	if (pcmsb == 0 && interrupts != 0) {
	  // just in case we're running in another thread
	  synchronized (this) {
	    int trap = INTERRUPT;
	    for (int i = 1; i != 0; i <<= 1, trap += 4) {
	      if ((interrupts & i) != 0) {
		irq = true;
		Trap(trap);
		interrupts &= ~i;
		break;
	      }
	    }
	  }
	} else {
	    // execute fetched instruction
	dispatch:
	    switch ((inst >> 26) & 0x3F) {
	    case 0x00:			// privileged instructions
		if (pcmsb == 1)	{	// only in Supervisor mode
		    switch (inst & 0xFFFF) {
		    case 0:		// halt
			rc = 31;
			npc = pc;
			wdata = (pcmsb << 31) | npc;
			werf = false;
			return true;
		    case 1:		// rdchar: return char in regs[0] or loop
			if (tty) {
			    if (ttyInput.length() > 0) {
				rc = 0;
				wdata = ttyInput.charAt(0);
				ttyInput.deleteCharAt(0);
				if (ttyInput.length() > 0)
				    interrupts |= KBD_INTERRUPT;
			    } else npc = pc;
			} else Trap(ILLOP);
			break dispatch;
		    case 2:		// wrchar: print char in regs[0]
			if (tty) {
			    usertty.append(String.valueOf((char)regs[0]));
			    try {
				usertty.setCaretPosition(usertty.getText().length() - 1);
			    }
			    catch (Exception e) { }
			    Thread.yield();
			} else Trap(ILLOP);
			break dispatch;
		    case 3:		// cycle: return current cycle count in regs[0]
			rc = 0;
			wdata = (int)cycles;
			break dispatch;
		    case 4:		// time: return time in regs[0]
			rc = 0;
			wdata = (int)System.currentTimeMillis();
			break dispatch;
		    case 5:		// click: return mouseCoords in regs[0]
			if (tty) {
			    rc = 0;
			    wdata = (int)mouseCoords;
			    mouseCoords = -1;
			} else Trap(ILLOP);
			break dispatch;
		    case 6:		// random: return random number in regs[0]
			rc = 0;
			wdata = (int)random.nextInt();
			break dispatch;
		    case 7:		// seed: set new seed from regs[0]
			random.setSeed((long)regs[0]);
			break dispatch;
		    case 8:		// server: save word in regs[0]
			serverInfo.add(new Integer(regs[0]));
			break dispatch;
		    default:
			Trap(ILLOP);
			break dispatch;
		    }
		} else Trap(ILLOP);
		break;
	    default:			// illegal instruction
		Trap(ILLOP);
		break;
	    case 0x18:			// LD
		ma = (rd1 + literal) & 0x7FFFFFFF;
		if (!memory.ValidAddress(ma)) {
		    wdata = 0;
		    message.Message("LD from invalid memory location");
		    return true;
		} else wdata = memory.CachedReadWord(ma);
		break;
	    case 0x19:			// ST
		mdata = regs[rc];
		ma = (rd1 + literal) & 0x7FFFFFFF;
		wr = true;
		werf = false;
		if (!memory.ValidAddress(ma)) {
		    wr = false;
		    message.Message("ST to invalid memory location");
		    return true;
		}
		if (!writeable[memory.Index(ma)]) {
		    wr = false;
		    message.Message("ST to read-only memory location");
		    return true;
		}
		break;
	    case 0x1B:			// JMP
		wdata = (pcmsb << 31) | npc;
		npc = rd1 & 0x7FFFFFFC;
		if (!kalways && (rd1 & 0x80000000) == 0) npcmsb = 0;
		break;
	    case 0x1C:			// BEQ (updated F11)
		wdata = (pcmsb << 31) | npc;
		if (rd1 == 0) npc = (npc + 4*literal) & 0x7FFFFFFC;
		break;
	    case 0x1D:			// BNE (updated F11)
		wdata = (pcmsb << 31) | npc;
		if (rd1 != 0) npc = (npc + 4*literal) & 0x7FFFFFFC;
		break;
	    case 0x1F:			// LDR
		ma = (npc + 4*literal) & 0x7FFFFFFF;
		wdata = memory.CachedReadWord(ma);
		break;
	    case 0x20:			// ADD
		wdata = rd1 + rd2;
		break;
	    case 0x21:			// SUB
		wdata = rd1 - rd2;
		break;
	    case 0x22:			// MUL
		if (mul) wdata = rd1 * rd2;
		else Trap(ILLOP);
		break;
	    case 0x23:			// DIV
		if (div) wdata = rd1 / rd2;
		else Trap(ILLOP);
		break;
	    case 0x24:			// CMPEQ
		wdata = (rd1 == rd2) ? 1 : 0;
		break;
	    case 0x25:			// CMPLT
		wdata = (rd1 < rd2) ? 1 : 0;
		break;
	    case 0x26:			// CMPLE
		wdata = (rd1 <= rd2) ? 1 : 0;
		break;
	    case 0x28:			// AND
		wdata = rd1 & rd2;
		break;
	    case 0x29:			// OR
		wdata = rd1 | rd2;
		break;
	    case 0x2A:			// XOR
		wdata = rd1 ^ rd2;
		break;
	    case 0x2B:			// XNOR (updated F11)
		wdata = ~(rd1 ^ rd2);
		break;
	    case 0x2C:			// SHL
		wdata = rd1 << rd2;
		break;
	    case 0x2D:			// SHR
		wdata = rd1 >>> rd2;
		break;
	    case 0x2E:			// SRA
		wdata = rd1 >> rd2;
		break;
	    case 0x30:			// ADDC
		wdata = rd1 + literal;
		break;
	    case 0x31:			// SUBC
		wdata = rd1 - literal;
		break;
	    case 0x32:			// MULC
		if (mul) wdata = rd1 * literal;
		else Trap(ILLOP);
		break;
	    case 0x33:			// DIVC
		if (div) wdata = rd1 / literal;
		else Trap(ILLOP);
		break;
	    case 0x34:			// CMPEQC
		wdata = (rd1 == literal) ? 1 : 0;
		break;
	    case 0x35:			// CMPLTC
		wdata = (rd1 < literal) ? 1 : 0;
		break;
	    case 0x36:			// CMPLEC
		wdata = (rd1 <= literal) ? 1 : 0;
		break;
	    case 0x38:			// ANDC
		wdata = rd1 & literal;
		break;
	    case 0x39:			// ORC
		wdata = rd1 | literal;
		break;
	    case 0x3A:			// XORC
		wdata = rd1 ^ literal;
		break;
	    case 0x3B:			// XNORC (updated F11)
		wdata = ~(rd1 ^ literal);
		break;
	    case 0x3C:			// SHLC
		wdata = rd1 << literal;
		break;
	    case 0x3D:			// SHRC
		wdata = rd1 >>> literal;
		break;
	    case 0x3E:			// SRAC
		wdata = rd1 >> literal;
		break;
	    }
	}
	return false;
    }

    private String Reg(int r) {
	if (r == 30) return "XP";
	else if (r == 29) return "SP";
	else if (r == 28) return "LP";
	else if (r == 27) return "BP";
	else return "R"+r;
    }

    private String Betaop(String op,int ra,int rb,int rc) {
	return op+"("+Reg(ra)+","+Reg(rb)+","+Reg(rc)+")";
    }

    private String Betaopc(String op,int ra,int literal,int rc) {
	return op+"("+Reg(ra)+","+literal+","+Reg(rc)+")";
    }

    private String Offset(int addr,int literal) {
	addr += 4 + 4*literal;
	if (addr >= 0 && (addr >> 2) < labels.length && labels[addr >> 2] != null)
	    return labels[addr >> 2];
	else return "0x"+Integer.toHexString(addr).toUpperCase();
    }

    private String Disassemble(int addr) {
	int offset = memory.Index(addr);
	String i = instructions[offset];

	if (i == null) {
	    if (labels[offset] != null) {
		i = labels[offset]+": ";
		while (i.length() < 8) i += " ";
	    } else i = "        ";

	    int inst = memory.ReadWord(addr);
	    int rc = (inst >> 21) & 0x1F;
	    int ra = (inst >> 16) & 0x1F;
	    int rb = (inst >> 11) & 0x1F;
	    int literal = (inst << 16) >> 16;	// sign extended
	    switch ((inst >> 26) & 0x3F) {
	    case 0x00:			// privileged instructions
		switch (inst & 0xFFFF) {
		case 0:		// halt
		    i += "HALT()";
		    break;
		case 1:		// rdchar
		  if (tty) i += "RDCHAR()";
		  else i += "illop";
		    break;
		case 2:		// wrchar
		  if (tty) i += "WRCHAR()";
		  else i += "illop";
		    break ;
		case 3:		// cycle
		    i += "CYCLE()";
		    break;
		case 4:		// time
		    i += "TIME()";
		    break;
		case 5:		// click
		    if (tty) i += "CLICK()";
		    else i += "illop";
		    break;
		case 6:		// random
		    i += "RANDOM()";
		    break;
		case 7:		// seed
		    i += "SEED()";
		    break;
		case 8:		// server
		    i += "SERVER()";
		    break;
		default:
		    i += "illop";
		    break;
		}
		break;
	    default:			// illegal instruction
		i += "illop";
		break;
	    case 0x18:			// LD
		i += Betaopc("LD",ra,literal,rc);
		break;
	    case 0x19:			// ST
		i += Betaopc("ST",rc,literal,ra);
		break;
	    case 0x1B:			// JMP
		if (rc == 31) i += "JMP("+Reg(ra)+")";
		else i += "JMP("+Reg(ra)+","+Reg(rc)+")";
		break;
	    case 0x1C:			// BEQ (updated F11)
		if (ra == 31) {
		    if (rc == 31) i += "BR("+Offset(addr,literal)+")";
		    else i += "BR("+Offset(addr,literal)+","+Reg(rc)+")";
		} else if (rc == 31)
		    i +="BEQ("+Reg(ra)+","+Offset(addr,literal)+")";
		else
		    i += "BEQ("+Reg(ra)+","+Offset(addr,literal)+","+Reg(rc)+")";
		break;
	    case 0x1D:			// BNE (updated F11)
		if (rc == 31)
		    i +="BNE("+Reg(ra)+","+Offset(addr,literal)+")";
		else
		    i += "BNE("+Reg(ra)+","+Offset(addr,literal)+","+Reg(rc)+")";
		break;
	    case 0x1F:			// LDR
		i += "LDR("+Offset(addr,literal)+","+Reg(rc)+")";
		break;
	    case 0x20:			// ADD
		i += Betaop("ADD",ra,rb,rc);
		break;
	    case 0x21:			// SUB
		i += Betaop("SUB",ra,rb,rc);
		break;
	    case 0x22:			// MUL
		i += Betaop("MUL",ra,rb,rc);
		break;
	    case 0x23:			// DIV
		i += Betaop("DIV",ra,rb,rc);
		break;
	    case 0x24:			// CMPEQ
		i += Betaop("CMPEQ",ra,rb,rc);
		break;
	    case 0x25:			// CMPLT
		i += Betaop("CMPLT",ra,rb,rc);
		break;
	    case 0x26:			// CMPLE
		i += Betaop("CMPLE",ra,rb,rc);
		break;
	    case 0x28:			// AND
		i += Betaop("AND",ra,rb,rc);
		break;
	    case 0x29:			// OR
		i += Betaop("OR",ra,rb,rc);
		break;
	    case 0x2A:			// XOR
		i += Betaop("XOR",ra,rb,rc);
		break;
	    case 0x2B:			// XNOR (updated F11)
		i += Betaop("XNOR",ra,rb,rc);
		break;
	    case 0x2C:			// SHL
		i += Betaop("SHL",ra,rb,rc);
		break;
	    case 0x2D:			// SHR
		i += Betaop("SHR",ra,rb,rc);
		break;
	    case 0x2E:			// SRA
		i += Betaop("SRA",ra,rb,rc);
		break;
	    case 0x30:			// ADDC
		i += Betaopc("ADDC",ra,literal,rc);
		break;
	    case 0x31:			// SUBC
		i += Betaopc("SUBC",ra,literal,rc);
		break;
	    case 0x32:			// MULC
		i += Betaopc("MULC",ra,literal,rc);
		break;
	    case 0x33:			// DIVC
		i += Betaopc("DIVC",ra,literal,rc);
		break;
	    case 0x34:			// CMPEQC
		i += Betaopc("CMPEQC",ra,literal,rc);
		break;
	    case 0x35:			// CMPLTC
		i += Betaopc("CMPLTC",ra,literal,rc);
		break;
	    case 0x36:			// CMPLEC
		i += Betaopc("CMPLEC",ra,literal,rc);
		break;
	    case 0x38:			// ANDC
		i += Betaopc("ANDC",ra,literal,rc);
		break;
	    case 0x39:			// ORC
		i += Betaopc("ORC",ra,literal,rc);
		break;
	    case 0x3A:			// XORC
		i += Betaopc("XORC",ra,literal,rc);
		break;
	    case 0x3B:			// XNORC (updated F11)
		i += Betaopc("XNORC",ra,literal,rc);
		break;
	    case 0x3C:			// SHLC
		i += Betaopc("SHLC",ra,literal,rc);
		break;
	    case 0x3D:			// SHRC
		i += Betaopc("SHRC",ra,literal,rc);
		break;
	    case 0x3E:			// SRAC
		i += Betaopc("SRAC",ra,literal,rc);
		break;
	    }
	    instructions[offset] = i;
	}

	return i;
    }

    private void Signals() {
	int op = (inst >> 26) & 0x3F;

	pcsel = 0;
	asel = (op == 0x1F) ? 1 : 0;
	bsel = ((op & 0x30) == 0x30 || op == 0x18 || op == 0x19) ? 1 : 0;
	ra2sel = (bsel == 1) ? -1 : 0;
	wasel = 0;
	needZ = false;
	z = rd1 == 0 ? 1 : 0;
	alufn = "+";
	wdsel = 1;
	pcinc = pc+4;
	offset = literal << 2;
	pcoffset = pcinc + offset;
	alua = (asel == 1) ? pcoffset : rd1;
	alub = (bsel == 1) ? literal : rd2;
	alu = wdata;

	if (irq) {
	    alu = alua + alub;
	    wasel = 1;
	    pcsel = 4;
	    wdsel = 0;
	    asel = -1;
	    bsel = -1;
	    ra2sel = -1;
	    alufn = null;
	} else switch (op) {
	default:			// illegal instruction
	    alu = alua + alub;
	    wasel = 1;
	    pcsel = 3;
	    wdsel = 0;
	    asel = -1;
	    bsel = -1;
	    ra2sel = -1;
	    alufn = null;
	    break;
	case 0x18:			// LD
	    alu = alua + alub;
	    wdsel = 2;
	    break;
	case 0x19:			// ST
	    alu = alua + alub;
	    ra2sel = 1;
	    rb = rc;
	    rd2 = mdata;
	    wasel = -1;
	    wdsel = -1;
	    break;
	case 0x1B:			// JMP
	    alu = alua + alub;
	    pcsel = 2;
	    wdsel = 0;
	    asel = -1;
	    bsel = -1;
	    ra2sel = -1;
	    alufn = null;
	    break;
	case 0x1D:			// BEQ
	    alu = alua + alub;
	    wdsel = 0;
	    asel = -1;
	    bsel = -1;
	    ra2sel = -1;
	    alufn = null;
	    pcsel = (z == 1) ? 1 : 0;
	    needZ = true;
	    break;
	case 0x1E:			// BNE
	    alu = alua + alub;
	    wdsel = 0;
	    asel = -1;
	    bsel = -1;
	    ra2sel = -1;
	    alufn = null;
	    pcsel = (z == 1) ? 0 : 1;
	    needZ = true;
	    break;
	case 0x1F:			// LDR
	    alu = alua;
	    alufn = "A";
	    bsel = -1;
	    ra2sel = -1;
	    wdsel = 2;
	    break;
	case 0x20:			// ADD
	case 0x30:			// ADDC
	    break;
	case 0x21:			// SUB
	case 0x31:			// SUBC
	    alufn = "-";
	    break;
	case 0x22:			// MUL
	case 0x32:			// MULC
	    alufn = "*";
	    break;
	case 0x23:			// DIV
	case 0x33:			// DIVC
	    alufn = "/";
	    break;
	case 0x24:			// CMPEQ
	case 0x34:			// CMPEQC
	    alufn = "==";
	    break;
	case 0x25:			// CMPLT
	case 0x35:			// CMPLTC
	    alufn = "<";
	    break;
	case 0x26:			// CMPLE
	case 0x36:			// CMPLEC
	    alufn = "<=";
	    break;
	case 0x28:			// AND
	case 0x38:			// ANDC
	    alufn = "&";
	    break;
	case 0x29:			// OR
	case 0x39:			// ORC
	    alufn = "|";
	    break;
	case 0x2A:			// XOR
	case 0x3A:			// XORC
	    alufn = "^";
	    break;
	case 0x2C:			// SHL
	case 0x3C:			// SHLC
	    alufn = "<<";
	    break;
	case 0x2D:			// SHR
	case 0x3D:			// SHRC
	    alufn = ">>";
	    break;
	case 0x2E:			// SRA
	case 0x3E:			// SRAC
	    alufn = "sxt(>>)";
	    break;
	}
	
	mrd = ReadMemory(alu);
    }

    private String Hexify(int v,int nchars) {
	String s = Integer.toHexString(v);
	if (s.length() < nchars) s = zeros.substring(0,nchars-s.length()) + s;
	return s.toUpperCase();
    }

    // draw an arrow
    private void DrawArrow(Graphics g,int cx,int cy,int length,int orientation,int boffset,int eoffset) {
	DrawArrow(g,cx,cy,length,orientation,boffset,eoffset,Color.black);
    }

    private void DrawArrow(Graphics g,int cx,int cy,int length,int orientation,int boffset,int eoffset,Color c) {
	int x = gridx * cx;
	int y = gridy * cy;
	
	g.setColor(c);
	switch (orientation) {
	case N:
	    g.drawLine(x + hx,y + eoffset,x + hx,y + length*gridy - boffset);
	    g.drawLine(x + hx,y + eoffset,x,y + gridx);
	    g.drawLine(x + hx,y + eoffset,x + hx + hx,y + gridx);
	    break;
	case E:
	    int xx = x + length*gridx - eoffset;
	    g.drawLine(x+boffset,y + hy,xx,y+hy);
	    g.drawLine(xx,y + hy,xx - gridx,y+hy+hx);
	    g.drawLine(xx,y + hy,xx - gridx,y+hy-hx);
	    break;
	case S:
	    int yy = y + length*gridy - eoffset;
	    g.drawLine(x + hx,y+boffset,x + hx,yy);
	    g.drawLine(x + hx,yy,x,yy - gridx);
	    g.drawLine(x + hx,yy,x + hx + hx,yy - gridx);
	    break;
	case W:
	    g.drawLine(x+eoffset,y + hy,x + length*gridx + boffset,y+hy);
	    g.drawLine(x+eoffset,y + hy,x + eoffset + gridx,y+hy+hx);
	    g.drawLine(x+eoffset,y + hy,x + eoffset + gridx,y+hy-hx);
	    break;
	}
    }

    // draw an inset frame around the specified grid rectangle
    private void DrawFrame(Graphics g,int cx,int cy,int cw,int ch) {
	int x = gridx * cx;
	int y = gridy * cy;
	int w = gridx * cw;
	int h = gridy * ch;

	g.setColor(Color.white);
	g.fillRect(x,y,w,h);

	g.setColor(Color.darkGray);
	g.drawLine(x-1,y-1,x-1,y+h);
	g.drawLine(x-1,y-1,x+w,y-1);
	g.drawLine(x-2,y-2,x-2,y+h+1);
	g.drawLine(x-2,y-2,x+w+1,y-2);

	g.setColor(new Color(220,220,220));
	g.drawLine(x+w,y+h,x-1,y+h);
	g.drawLine(x+w,y+h,x+w,y-1);
	g.drawLine(x+w+1,y+h+1,x-2,y+h+1);
	g.drawLine(x+w+1,y+h+1,x+w+1,y-2);
    }

    private void DrawLine(Graphics g,int x1,int y1,int dx,int dy) {
	DrawLine(g,x1,y1,dx,dy,Color.black);
    }

    private void DrawLine(Graphics g,int x1,int y1,int dx,int dy,Color c) {
	g.setColor(c);
	g.drawLine(x1,y1,x1+dx,y1+dy);
    }

    private void DrawString(Graphics g,String s,int cx,int cy) {
	DrawString(g,s,cx,cy,0,0,Color.black);
    }

    private void DrawString(Graphics g,String s,int cx,int cy,Color c) {
	DrawString(g,s,cx,cy,0,0,c);
    }

    private void DrawString(Graphics g,String s,int cx,int cy,int ox,int oy) {
	DrawString(g,s,cx,cy,ox,oy,Color.black);
    }

    private void DrawString(Graphics g,String s,int cx,int cy,int ox,int oy,Color c) {
	g.setColor(c);
	g.drawString(s,cx*gridx+ox,cy*gridy + baseline + oy);
    }

    // compute number of hex digits in address
    private int AddrSize() {
	int naddr = 1;
	int mlength = memory.Size();
	while ((1 << (4*naddr)) < 4*mlength) naddr += 1;
	return naddr;
    }

    private void DrawMemory(Graphics g,int cx,int cy,int addr,int nlocs,boolean inst) {
	int naddr = AddrSize();
	DrawFrame(g,cx,cy,naddr + 9 + (inst ? 32 : 0),nlocs);

	addr &= ~0x3;
	int xa = (addr - 4*(nlocs >> 1));

	for (int i = 0; i < nlocs; i += 1) {
	    if (memory.ValidAddress(xa)) {
		int index = memory.Index(xa);
		String a = Hexify(index<<2,naddr);
		String v = Hexify(memory.ReadWord(xa),8);
		DrawString(g,a,cx,cy,inst & breakpoints[index] ? Color.red : HIDE);
		DrawString(g,":",cx+naddr,cy,HIDE);
		DrawString(g,v,cx+naddr+1,cy,!inst ? SHOW : HIDE);
		if (inst)
		    DrawString(g,Disassemble(xa),cx+naddr+10,cy,xa == addr ? SHOW : HIDE);
	    }
	    xa += 4;
	    cy += 1;
	}
    }

    private void DrawRegFile(Graphics g,int cx,int cy) {
	int w = 13;
	int h = 8;
	DrawFrame(g,cx,cy,4*w,h);

	g.setFont(tFont);
	int xx = cx;
	int yy = cy;
	for (int i = 0; i < 32; i += 1) {
	    String v = Hexify(regs[i],8);
	    String rname;
	    if (i == 30) rname = " XP:";
	    else if (i == 29) rname = " SP:";
	    else if (i == 28) rname = " LP:";
	    else if (i == 27) rname = " BP:";
	    else if (i < 10) rname = " R"+i+":";
	    else rname = "R"+i+":";

	    DrawString(g,rname,xx,yy,Color.gray);
	    DrawString(g,v,xx + 4,yy);

	    yy += 1;
	    if ((i % 8) == 7) {
		xx += 13;
		yy = cy;
	    }
	}
    }

    private void DrawRegister(Graphics g,int cx,int cy,String name,int v) {
	DrawFrame(g,cx,cy,name.length()+9,1);
	DrawString(g,name+":",cx,cy,Color.gray);
	DrawString(g,Hexify(v,8),cx + 3,cy);
    }

    private void DrawHorizontalMux(Graphics g,int cx,int cy,int ninputs,String sel,int v,boolean leftToRight,boolean leftArrow,int offset) {
	DrawHorizontalMux(g,cx,cy,ninputs,sel,v,leftToRight,leftArrow,offset,Color.black);
    }

    private void DrawHorizontalMux(Graphics g,int cx,int cy,int ninputs,String sel,int v,boolean leftToRight,boolean leftArrow,int offset,Color c) {
	int spacing = 4;
	int cw = spacing*ninputs - (spacing - 1);
	int x = gridx * cx;
	int y = gridy * cy;
	int w = gridx * cw;

	g.setColor(c);
	g.drawLine(x-4,y,x+w+4,y);
	g.drawLine(x,y+gridy,x+w,y+gridy);
	g.drawLine(x-4,y,x,y+gridy);
	g.drawLine(x+w+4,y,x+w,y+gridy);

	for (int i = 0; i < ninputs; i += 1) {
	    int ax = leftToRight ? (cx + spacing*i) : (cx + cw - spacing*i - 1);
	    DrawString(g,Integer.toString(i),ax,cy,i == v ? c : HIDE);
	    DrawArrow(g,ax,cy-1,1,S,offset,0,i == v ? c : HIDE);
	}

	String label = sel+"="+(v >= 0 ? Integer.toString(v) : "-");
	if (leftArrow) {
	    DrawArrow(g,cx-2,cy,2,E,0,2,c);
	    DrawString(g,label,cx-label.length()-2,cy,-hx,0,Color.red);
	} else {
	    DrawArrow(g,cx+cw,cy,2,W,0,2,c);
	    DrawString(g,label,cx+cw+2,cy,hx,0,Color.red);
	}
    }

    private void DrawVerticalMux(Graphics g,int cx,int cy,int ninputs,String sel,int v,boolean topToBottom,boolean bottomArrow,int offset) {
	DrawVerticalMux(g,cx,cy,ninputs,sel,v,topToBottom,bottomArrow,offset,Color.black);
    }

    private void DrawVerticalMux(Graphics g,int cx,int cy,int ninputs,String sel,int v,boolean topToBottom,boolean bottomArrow,int offset,Color c) {
	int spacing = 2;
	int ch = spacing*ninputs - (spacing - 1);
	int x = gridx * cx;
	int y = gridy * cy;
	int h = gridy * ch;

	g.setColor(c);
	g.drawLine(x,y-4,x,y+h+4);
	g.drawLine(x+3*gridx,y+4,x+3*gridx,y+h-4);
	g.drawLine(x,y-4,x+3*gridx,y+4);
	g.drawLine(x,y+h+4,x+3*gridx,y+h-4);

	for (int i = 0; i < ninputs; i += 1) {
	    int ay = topToBottom ? (cy + spacing*i) : (cy + ch - spacing*i - 1);
	    DrawString(g,Integer.toString(i),cx,ay,hx,0,i == v ? c : HIDE);
	    DrawArrow(g,cx-2,ay,2,E,offset,0,i == v ? c : HIDE);
	}

	String label = sel+"="+(v >= 0 ? Integer.toString(v) : "-");
	if (bottomArrow) {
	    DrawArrow(g,cx+1,cy+ch,1,N,0,0,c);
	    DrawString(g,label,cx+1-(label.length()>>1),cy+ch+1,gridx*(1-label.length()%2),0,Color.red);
	} else {
	    DrawArrow(g,cx+1,cy-1,1,S,0,0,c);
	    DrawString(g,label,cx+1-(label.length()>>1),cy-2,gridx*(1-label.length()%2),0,Color.red);
	}
    }

    private void DrawVerticalRipper(Graphics g,int cx,int cy,String label,boolean left,Color c) {
	int x = gridx * cx;
	int y = gridy * cy;
	int w = label.length();

	g.setColor(c);
	g.drawLine(x-hx,y,x+hx,y+gridx);
	g.drawLine(x+hx,y+gridx,x+hx,y+gridy);

	if (left) DrawString(g,label,cx-w-1,cy,hx,0,c);
	else DrawString(g,label,cx+1,cy,hx,0,c);
    }

    private void DrawHorizontalRipper(Graphics g,int cx,int cy,String label,boolean above,Color c) {
	int x = gridx * cx;
	int y = gridy * cy;

	g.setColor(c);
	g.drawLine(x+hx,y+hy-gridx,x+hx+gridx,y+hy);
	g.drawLine(x+hx+gridx,y+hy,x+gridx+gridx,y+hy);

	if (above) DrawString(g,label,cx+1,cy,hx,-hy,c);
	else DrawString(g,label,cx+1,cy+1,hx,-hy,c);
    }

    private void DrawALU(Graphics g,int cx,int cy,String alufn,Color c) {
	int cw = 5;
	int ch = 2;
	int diag = 12;
	int x0 = gridx*cx;
	int y0 = gridy*cy;
	int y1 = gridy*(cy + ch);
	int dx = gridx*(cw + cw + 1);

	DrawLine(g,x0,y0,dx-hx,0,c);
	DrawLine(g,x0+dx-hx,y0,2*hx,2*hx,c);
	DrawLine(g,x0+dx+gridx+hx,y0,-2*hx,2*hx,c);
	DrawLine(g,x0+dx+gridx+hx,y0,dx-hx,0,c);
	DrawLine(g,x0+diag,y1,2*dx+gridx-2*diag,0,c);
	DrawLine(g,x0,y0,diag,ch*gridy,c);
	DrawLine(g,x0+2*dx+gridx,y0,-diag,ch*gridy,c);

	DrawArrow(g,cx+cw,cy-1,1,S,0,0,c);
	DrawString(g,"A",cx+cw,cy,c);
	DrawArrow(g,cx+cw+1+cw+1+cw,cy-1,1,S,0,0,c);
	DrawString(g,"B",cx+cw+1+cw+1+cw,cy,c);
	DrawString(g,"ALU",cx+cw+cw,cy+1,0,0,c);
	DrawArrow(g,cx-2,cy+1,2,E,0,-(3*diag >> 2),c);
	String fn = "ALUFN=";
	if (alufn == null) fn += "-";
	else fn += "\""+alufn+"\"";
	DrawString(g,fn,cx-2-fn.length(),cy+1,-hx,0,Color.red);
    }

    private void SelectFont(Graphics g) {
	int size = 10;
	int lastSize = -1;
	FontMetrics fm;
	int maxx,maxy;

	if (display == DATAPATH) {
	    maxx = 133;
	    maxy = 34;
	} else if (display == PROGRAMMERS_PANEL) {
	    maxx = 91;
	    maxy = NLOCS+3;
	} else {
	    maxx = 100;  // dummy values
	    maxy = 30;
	}

	while (size > 5 && size < 20) {
	    tFont = new Font("Monospaced",Font.PLAIN,size);
	    // use font metrics to establish drawing grid
	    fm = g.getFontMetrics(tFont);
	    gridx = fm.charWidth(' ');
	    gridy = fm.getHeight();

	    if (tW >= maxx*gridx && tH >= maxy*gridy) {
		lastSize = size;
		size += 1;
	    } else if (lastSize > 0) {
		size = lastSize;
		break;
	    } else size -= 1;
	}

	tFont = new Font("Monospaced",Font.PLAIN,size);
	// use font metrics to establish drawing grid
	fm = g.getFontMetrics(tFont);
	gridx = fm.charWidth(' ');
	gridy = fm.getHeight();
	hx = gridx >> 1;
	hy = gridy >> 1;
	baseline = fm.getAscent();
	//System.out.println("w="+tW+" h="+tH+" gridx="+gridx+" gridy="+gridy);
    }

    public void SetupScrollbars() {
	Dimension d = maddrScroll.getPreferredSize();

	if (display == DATAPATH) {
	    maddrScroll.setBounds(gridx*(120+AddrSize())+2,gridy*26-2,d.width,gridy*7+4);
	    maddrScroll.setVisible(true);
	    iaddrScroll.setBounds(gridx*(76+AddrSize())+2,gridy*5-2,d.width,gridy*3+4);
	    iaddrScroll.setVisible(true);
	} else if (display == PROGRAMMERS_PANEL) {
	    // fix me...
	    maddrScroll.setBounds(gridx*(87+AddrSize())+2,gridy*2-2,d.width,gridy*NLOCS+4);
	    maddrScroll.setVisible(true);
	    iaddrScroll.setBounds(gridx*(48+AddrSize())+2,gridy*12-2,d.width,gridy*(NLOCS-10)+4);
	    iaddrScroll.setVisible(true);
	} else {
	    maddrScroll.setVisible(false);
	    iaddrScroll.setVisible(false);
	}
    }

    public void paintComponent(Graphics g) {
	super.paintComponent(g);

	// establish drawing metrics based on window size
	int h = getHeight();
	if (tty) h -= userscroll.getHeight();
	if (tFont == null || tW != getWidth() || tH != h) {
	    tW = getWidth();
	    tH = h;
	    SelectFont(g);
	    SetupScrollbars();
	}
	iaddrScroll.setValue(iaddr >> 2);
	maddrScroll.setValue(maddr >> 2);

	if (simulation != null) {
	    g.setColor(Color.red);
	    g.setFont(new Font("Serif",Font.BOLD,32));
	    g.drawString("Running simulation... press stop to view state",20,50);
	    return;
	} else if (display == DATAPATH) DrawDatapath(g);
	else if (display == PROGRAMMERS_PANEL) DrawProgrammersPanel(g);
    }

    void DrawDatapath(Graphics g) {
	g.setFont(tFont);
	Signals();	// fill in interesting signal values

	Color c;
	int pcx = 10;		// location of pc mux
	int pcy = 3;
	int imemx = pcx + 25;
	int instx = imemx + 14;	// location of instruction bus
	int pcoffy = pcy+9;	// location of pc offset adder
	int regx = imemx+30;	// location of register file
	int regy = pcy+9;
	int amuxx = regx+9;	// location of asel/bsel muxes
	int bmuxx = regx+35;
	int muxy = regy+12;

	// pc select mux, PC, instruction memory
	DrawString(g,"8",pcx,pcy-2,pcsel == 4 ? SHOW : HIDE);
	DrawString(g,"4",pcx+4,pcy-2,pcsel == 3 ? SHOW : HIDE);
	DrawString(g,"JT",pcx+7,pcy-2,pcsel == 2 ? SHOW : HIDE);
	DrawHorizontalMux(g,pcx,pcy,5,"PCSEL",pcsel,false,true,0);
	DrawArrow(g,pcx+8,pcy+1,1,S,0,2);
	DrawString(g,Hexify((npcmsb << 31) | npc,8),pcx+9,pcy+1,hx,0,Color.blue);
	DrawRegister(g,pcx+3,pcy+2,"PC",(pcmsb << 31) | pc);
	DrawMemory(g,imemx,pcy+2,iaddr,3,true);
	DrawArrow(g,pcx+8,pcy+3,2,S,2,0);
	DrawArrow(g,pcx+9,pcy+3,imemx-(pcx+9),E,-hx,2);

	// pc incrementer
	if (wdsel == 0 || pcsel == 0 || pcsel == 1) {
	    c = SHOW;
	    DrawString(g,Hexify(pcinc,8),pcx+9,pcy+7,hx,0,Color.blue);
	} else c = HIDE;
	g.setColor(c);
	g.drawRect(gridx*(pcx+6),gridy*(pcy+5),5*gridx,2*gridy);
	DrawString(g,"+4",pcx+7,pcy+5,hx,hy,c);
	DrawLine(g,gridx*(pcx+8)+hx,gridy*(pcy+7),0,gridy,c);
	DrawLine(g,gridx*(pcx+8)+hx,gridy*(pcy+8),11*gridx,0,(pcsel == 0 || pcsel == 1) ? SHOW : HIDE);
	c = pcsel == 0 ? SHOW : HIDE;
	DrawLine(g,gridx*(pcx+16)+hx,gridy*(pcy-1),3*gridx,0,c);
	DrawLine(g,gridx*(pcx+19)+hx,gridy*(pcy-1),0,4*gridy+hy-3,c);
	DrawLine(g,gridx*(pcx+19)+hx,gridy*(pcy+8),0,-4*gridy-hy+3,c);

	// pc + offset
	c = (pcsel == 1) ? SHOW : HIDE;
	DrawLine(g,gridx*(pcx+12)+hx,gridy*(pcy-2),9*gridx,0,c);
	DrawLine(g,gridx*(pcx+12)+hx,gridy*(pcy-2),0,gridy,c);
	DrawLine(g,gridx*(pcx+21)+hx,gridy*(pcy-2),0,5*gridy+hy-3,c);
	DrawLine(g,gridx*(pcx+21)+hx,gridy*(pcy+8)-3,0,-4*gridy-hy+6,c);
	DrawLine(g,gridx*(pcx+21)+hx,gridy*(pcy+8)+3,0,2*gridy-3,c);
	c = (pcsel == 1 || asel == 1) ? SHOW : HIDE;
	DrawLine(g,gridx*(pcx+19)+hx,gridy*(pcy+8),12*gridx-hx,0,c);
	g.drawRect(gridx*(pcx+24),gridy*pcoffy,5*gridx,2*gridy);
	DrawString(g,"+",pcx+26,pcoffy,0,hy,c);
	DrawLine(g,gridx*(pcx+21)+hx,gridy*(pcoffy+1),3*gridx-hx,0,c);
	DrawArrow(g,pcx+29,pcoffy,2,W,0,0,c);
	DrawLine(g,gridx*(pcx+31),gridy*pcoffy+hy,0,-gridy*(pcoffy - (pcy+8))-hy,c);
	DrawArrow(g,pcx+29,pcoffy+1,instx-(pcx+29),W,-hx,0,c);
	DrawString(g,"[15:0]*4",pcx+30,pcoffy,hx,hy,c);
	DrawLine(g,gridx*instx-hx,gridy*(pcoffy+1)+hy,gridx,-gridx,c);
	if (pcsel == 1 || asel == 1) {
	    DrawString(g,Hexify(offset,8),pcx+30,pcoffy+1,hx,hy,Color.blue);
	    DrawString(g,Hexify(pcoffset,8),pcx+12,pcoffy,hx,hy,Color.blue);
	}


	// instruction bus
	DrawLine(g,gridx*instx+hx,gridy*(pcy+5),0,gridy*((regy+2)-(pcy+5)));
	DrawString(g,Hexify(inst,8),instx+1,pcy+5,0,0,Color.blue);

	// wasel
	if (wasel >= 0) {
	    c = SHOW;
	    DrawString(g,Hexify(rc,2),regx-4,regy+2,hx,hy,Color.blue);
	} else c = HIDE;
	DrawVerticalMux(g,regx-7,regy+2,2,"WASEL",wasel,true,true,0,c);
	DrawArrow(g,regx-4,regy+3,4,E,0,2,c);
	DrawString(g,"XP",regx-11,regy+4,wasel == 1 ? SHOW : HIDE);
	DrawHorizontalRipper(g,instx,regy+2,"[25:21]",true,wasel == 0 ? SHOW : HIDE);
	DrawLine(g,gridx*(instx+2),gridy*(regy+2)+hy,gridx*((regx-9)-(instx+2)),0,wasel == 0 ? SHOW : HIDE);

	// register file with inputs and outputs
	int ra2 = (ra2sel == 1 ? rc : rb);
	DrawLine(g,gridx*instx+hx,gridy*(regy-3),gridx*(regx+12-instx)+hx,0);
	DrawVerticalRipper(g,regx+13,regy-3,"RA:[20:16]",true,SHOW);
	DrawArrow(g,regx+13,regy-2,2,S,0,2,SHOW);
	DrawVerticalRipper(g,regx+37,regy-3,"RB:[15:11]",true,ra2sel == 0 ? SHOW : HIDE);
	DrawVerticalRipper(g,regx+41,regy-3,"RC:[25:21]",false,ra2sel == 1 ? SHOW : HIDE);
	DrawString(g,Hexify(ra,2),regx+14,regy-1,hx,0,Color.blue);
	if (ra2sel >= 0) {
	    c = SHOW;
	    DrawString(g,Hexify(rb,2),regx+40,regy-1,hx,0,Color.blue);
	} else c = HIDE;
	DrawLine(g,gridx*(regx+12)+hx,gridy*(regy-3),gridx*24,0,c);
	DrawLine(g,gridx*(regx+36)+hx,gridy*(regy-3),gridx*4,0,ra2sel == 1 ? SHOW : HIDE);
	DrawHorizontalMux(g,regx+37,regy-2,2,"RA2SEL",ra2sel,true,false,hy,c);
	DrawArrow(g,regx+39,regy-1,1,S,0,2,c);

	DrawRegFile(g,regx,regy);
	//DrawArrow(g,regx+13,regy+8,4,S,2,0);
	DrawLine(g,gridx*(regx+13)+hx,gridy*(regy+8)+2,0,gridy*2-hy-2,SHOW);
	DrawString(g,Hexify(rd1,8),regx+14,regy+8,hx,0,Color.blue);
	//DrawArrow(g,regx+39,regy+8,4,S,2,0);
	DrawLine(g,gridx*(regx+39)+hx,gridy*(regy+8)+2,0,gridy*2+hy-2,ra2sel >= 0 ? SHOW : HIDE);
	if (ra2sel >= 0)
	    DrawString(g,Hexify(rd2,8),regx+40,regy+8,hx,0,Color.blue);
	DrawArrow(g,regx+52,regy+4,2,W,0,2);
	DrawString(g,"WERF="+(werf ? "1" : "0"),regx+54,regy+4,hx,0,Color.red);
	DrawArrow(g,regx+52,regy+3,14,W,0,2,werf ? SHOW : HIDE);
	if (wdsel >= 0)
	    DrawString(g,Hexify(wdata,8),regx+54,regy+3,hx,-hy,Color.blue);

	// JT
	c = pcsel == 2 ? SHOW : HIDE;
	DrawArrow(g,regx+7,regy+8,6,W,hx,0,c);
	DrawString(g,"JT",regx+5,regy+8,c);

	// Z
	c = needZ ? SHOW : HIDE;
	DrawArrow(g,regx+11,regy+9,2,W,hx,2,c);
	g.setColor(c);
	g.drawRect(gridx*(regx+9)-2,gridy*(regy+9),2*gridx+4,gridy);
	DrawString(g,"0?",regx+9,regy+9,c);
	DrawArrow(g,regx+7,regy+9,2,W,-2,0,c);
	DrawString(g,"Z="+z,regx+4,regy+9,Color.red);

	// asel mux
	DrawHorizontalMux(g,amuxx,muxy,2,"ASEL",asel,false,false,hy,asel == -1 ? HIDE : SHOW);
	DrawLine(g,gridx*(pcx+21)+hx,gridy*(pcoffy+1),0,10*gridy+hy,asel == 1 ? SHOW : HIDE);
	DrawLine(g,gridx*(pcx+21)+hx,gridy*(pcy+20)+hy,gridx*(amuxx-(pcx+21)),0,asel == 1 ? SHOW : HIDE);
	DrawLine(g,gridx*(regx+13)+hx,gridy*(muxy-3)+hy,0,gridy*2+hy,asel == 0 ? SHOW : HIDE);

	// bsel mux
	DrawHorizontalMux(g,bmuxx,muxy,2,"BSEL",bsel,false,false,0,bsel == -1 ? HIDE : SHOW);
	if (bsel == 1) {
	    c = SHOW;
	    DrawString(g,Hexify(literal,8),bmuxx-8,muxy-2,0,-hy,Color.blue);
	} else c = HIDE;
	DrawHorizontalRipper(g,instx,muxy-2,"C:[15:0]",true,c);
	DrawLine(g,gridx*(instx+2),gridy*(muxy-2)+hy,gridx*((regx+13)-(instx+2)),0,c);
	DrawLine(g,gridx*(regx+14),gridy*(muxy-2)+hy,gridx*(bmuxx-(regx+14))+hx,0,c);
	DrawLine(g,gridx*bmuxx+hx,gridy*(muxy-2)+hy,0,gridy,c);
	DrawLine(g,gridx*instx+hx,gridy*(regy+2),0,gridy*((muxy-2)-(regy+2)),c);
	DrawLine(g,gridx*(regx+39)+hx,gridy*(muxy-1),0,-hy,bsel == 0 ? SHOW : HIDE);

	// alu
	DrawLine(g,gridx*(amuxx+2)+hx,gridy*(muxy+1),0,gridy,asel >= 0 ? SHOW : HIDE);
	DrawLine(g,gridx*(amuxx+2)+hx,gridy*(muxy+2),7*gridx,0,asel >= 0 ? SHOW : HIDE);
	DrawLine(g,gridx*(bmuxx+2)+hx,gridy*(muxy+1),0,gridy,bsel >= 0 ? SHOW : HIDE);
	DrawLine(g,gridx*(bmuxx+2)+hx,gridy*(muxy+2),-7*gridx,0,bsel >= 0 ? SHOW : HIDE);
	DrawALU(g,amuxx+4,muxy+3,alufn,alufn != null ? SHOW : HIDE);
	if (asel >= 0)
	    DrawString(g,Hexify(alua,8),amuxx+1,muxy+2,-hx,0,Color.blue);
	if (bsel >= 0)
	    DrawString(g,Hexify(alub,8),amuxx+22,muxy+2,hx,0,Color.blue);

	DrawLine(g,gridx*(amuxx+15)+hx,gridy*(muxy+5),0,hy,(wdsel >= 1 || wr) ? SHOW : HIDE );
	DrawLine(g,gridx*(amuxx+15)+hx,gridy*(muxy+5)+hy,0,2*gridy,wdsel == 1 ? SHOW : HIDE);
	if (wdsel > 0 || wr)
	    DrawString(g,Hexify(alu,8),amuxx+7,muxy+5,0,0,Color.blue);

	// data memory
	DrawMemory(g,amuxx+37,muxy+2,maddr,7,false);

	DrawLine(g,gridx*(amuxx+42)+hx,gridy*(muxy-1)-hy,-12*gridx,0,wr ? SHOW :HIDE);
	DrawArrow(g,amuxx+42,muxy-1,3,S,-hy,2,wr ? SHOW : HIDE);
	DrawString(g,"MWD",amuxx+39,muxy+1,-hx,0,wr ? SHOW : HIDE);

	DrawArrow(g,amuxx+47,muxy+1,1,S,0,2);
	DrawString(g,"WR="+(wr ? 1 : 0),amuxx+45,muxy,hx,0,Color.red);

	DrawArrow(g,amuxx+15,muxy+5,22,E,hx,2,(wdsel == 2 || wr) ? SHOW : HIDE);
	DrawString(g,"MA",amuxx+35,muxy+4,-hx,hy,(wdsel == 2 || wr) ? SHOW : HIDE);
	DrawLine(g,gridx*(amuxx+11+8)+hx,gridy*(muxy+7),gridx*17,0,wdsel == 2 ? SHOW : HIDE);
	if (wdsel == 2)
	    DrawString(g,Hexify(mrd,8),amuxx+11+9,muxy+6,0,0,Color.blue);
	DrawString(g,"MRD",amuxx+34,muxy+7,-hx,0,wdsel == 2 ? SHOW : HIDE);

	// wdsel mux
	c = wdsel == 0 ? SHOW : HIDE;
	DrawLine(g,gridx*(pcx+8)+hx,gridy*(pcy+8),0,20*gridy,c);
	DrawLine(g,gridx*(pcx+8)+hx,gridy*(muxy+7),gridx*((amuxx+11)-(pcx+8)),0,c);
	if (wdsel == 0)
	    DrawString(g,Hexify((pcmsb<<31)|pcinc,8),amuxx+11-8,muxy+6,0,0,Color.blue);
	c = wdsel >= 0 ? SHOW : HIDE;
	DrawHorizontalMux(g,amuxx+11,muxy+8,3,"WDSEL",wdsel,true,true,0,c);
	DrawArrow(g,amuxx+15,muxy+9,1,S,0,0,c);
	DrawLine(g,gridx*(amuxx+15)+hx,gridy*(muxy+10),gridx*((regx+66)-(amuxx+15))-hx,0,c);
	DrawLine(g,gridx*(regx+66),gridy*(regy+3)+hy,0,19*gridy-hy,c);

	// cycle number
	DrawString(g,"cycle = "+cycles,0,33,0,0,SHOW);
    }

    void AnnotateStack(Graphics g,int bp,int col,int row,int nrows,boolean topFrame) {
	int lastLoc = regs[29];
	int firstLoc = lastLoc - 4*nrows + 4;

	if (bp != 0 && bp >= firstLoc && bp <= lastLoc) {
	    // mark old BP location
	    int bpline = (bp - 4 - firstLoc) >> 2;

	    if (topFrame) {
		DrawString(g,"BP",col-6,row+bpline+1,Color.BLACK);
		DrawArrow(g,col-4,row+bpline+1,4,E,hx,2);
	    }

	    if (bpline < 0) return;
	    DrawString(g,"oldBP",col-5,row+bpline,-2,0,Color.WHITE);

	    // mark old LP location
	    bpline -= 1;
	    if (bpline < 0) return;
	    DrawString(g,"oldLP",col-5,row+bpline,-2,0,Color.WHITE);

	    // see how many arguments this function has
	    int returnInst = ReadMemory(ReadMemory(bp-8));
	    if ((returnInst & 0xFFFF0000) == 0xC7BD0000) {  // DEALLOCATE?
		int nargs = (returnInst & 0xFFFF) >> 2;
		int arg = 1;
		while (arg <= nargs) {
		    bpline -= 1;
		    if (bpline < 0) return;
		    DrawString(g,"arg"+Integer.toString(arg),col-5,row+bpline,-2,0,Color.WHITE);
		    arg += 1;
		}
	    }

	    // recurse to earlier stack frame
	    AnnotateStack(g,ReadMemory(bp - 4),col,row,nrows,false);
	}
    }

    void DrawProgrammersPanel(Graphics g) {
	g.setFont(tFont);

	// regfile
	DrawString(g,"REGISTERS",1,1,Color.BLACK);
	DrawRegFile(g,1,2);

	// instructions
	int ninsts = NLOCS - 10;
	int pcline = 12 + (ninsts>>1);
	DrawString(g,"INSTRUCTIONS",7,11,Color.BLACK);
	DrawMemory(g,7,12,iaddr,ninsts,true);
	DrawString(g,(pcmsb == 1) ? "(SUPERVISOR MODE)" : "(USER MODE)",20,11,Color.BLACK);
	DrawString(g,"PC",1,pcline,Color.BLACK);
	DrawArrow(g,3,pcline,4,E,hx,2);

	// stack
	DrawString(g,"STACK",61,1,Color.BLACK);
	DrawMemory(g,61,2,regs[29]-(4*(NLOCS>>1)-4),NLOCS,false);
	DrawString(g,"SP",55,NLOCS+1,Color.BLACK);
	DrawArrow(g,57,NLOCS+1,4,E,hx,2);

	if (annotate)
	    AnnotateStack(g,regs[27],61,2,NLOCS,true);

	// memory
	DrawString(g,"MEM[0x"+Integer.toHexString(maddr)+"]",78,1,Color.BLACK);
	DrawMemory(g,78,2,maddr,NLOCS,false);
    }
}
