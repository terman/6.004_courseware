// Copyright (C) 2003 Christopher J. Terman - All Rights Reserved.

package tmsim;

import gui.EditBuffer;
import gui.GuiFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

public class TMPanel extends EditBuffer implements ChangeListener, Runnable {
    public static final int MAXDELAY = 500;
    public static final int INITDELAY = 50;
    public static final String HALT = "*halt*";
    public static final String ERROR = "*error*";

    HashMap states;		// states.get(<current state>).get(<current symbol>) => TMAction
    HashMap symbols;		// used to remember declared symbols (used to catch typos)
    String startingState;	// initial state
    ArrayList lineStarts;	// buffer position where each line starts

    public String checkoffServer;	// checkoff info
    public String checkoffAssignment;
    public int checkoffChecksum;
    public int tapeChecksum;		// checksum from "tape" and "result" statements

    // user can specify several test tapes
    HashMap tapes;		// name -> tape contents
    HashMap results;		// name -> tape contents
    ArrayList tapeNames;	// ordered list of tape names

    JSplitPane split;
    JPanel choose;
    
    int currentTape;
    TMTape tape;
    JSlider speed;
    JButton breset;
    JButton bstep;
    JButton brun;
    JButton bstop;
    JButton balltests;
    HashMap tButtons;
    Highlighter.HighlightPainter highlightPainter;
    Highlighter highlighter;

    int delay;
    boolean reloadFSM;
    boolean capture;
    boolean stop;
    String state;
    int counter;
    boolean solved[];
    boolean allTests;

    public TMPanel(GuiFrame parent,File source) {
	super(parent,source);
	delay = INITDELAY;

	Changed();	// initialize solved array

	// set up state highlighter
	highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255,255,128));
	highlighter = text.getHighlighter();

	JPanel xxx = new JPanel();
	GridBagLayout glayout = new GridBagLayout();
	xxx.setLayout(glayout);
	GridBagConstraints c = new GridBagConstraints();
	c.fill = GridBagConstraints.BOTH;
	c.weightx = 1;
	c.gridwidth = GridBagConstraints.REMAINDER;

	JPanel tpanel = TapePanel();
	c.weighty = 0;
	glayout.setConstraints(tpanel,c);
	xxx.add(tpanel);

	c.weighty = 1;
	glayout.setConstraints(scroll,c);
	xxx.add(scroll);

	add(xxx,BorderLayout.CENTER);

	reloadFSM = true;
	capture = true;
	Reset();
    }

    JPanel TapePanel() {
	JPanel p = new JPanel();

	// build contents
	GridBagLayout glayout = new GridBagLayout();
	p.setLayout(glayout);
	GridBagConstraints c = new GridBagConstraints();
	c.anchor = GridBagConstraints.CENTER;

	// maze selection
	choose = new JPanel(new FlowLayout(FlowLayout.CENTER));
	c.fill = GridBagConstraints.HORIZONTAL;
	c.weightx = 1;
	c.gridwidth = GridBagConstraints.REMAINDER;
	glayout.setConstraints(choose,c);
	p.add(choose);

	// tape display
	tape = new TMTape();
	c.fill = GridBagConstraints.BOTH;
	c.weightx = 1;
	c.weighty = 1;
	c.gridwidth = GridBagConstraints.REMAINDER;
	glayout.setConstraints(tape,c);
	p.add(tape);
	choose.add(new JRadioButton("",false));   // so subpanel isn't empty during layout

	// speed control
	JLabel l = new JLabel("Slow",JLabel.RIGHT);
	c.fill = GridBagConstraints.BOTH;
	c.insets = new Insets(2,2,2,2);
	c.gridwidth = 1;
	c.weightx = 0.25;
	c.weighty = 0;
	glayout.setConstraints(l,c);
	p.add(l);

	speed = new JSlider(0,MAXDELAY,MAXDELAY - INITDELAY);
	speed.addChangeListener(this);
	c.gridwidth = 2;
	glayout.setConstraints(speed,c);
	p.add(speed);

	l = new JLabel("Fast",JLabel.LEFT);
	c.gridwidth = GridBagConstraints.REMAINDER;
	glayout.setConstraints(l,c);
	p.add(l);

	// "Reset" button
	breset = new JButton("Reset");
	breset.addActionListener(this);
	c.weightx = 1;
	c.gridwidth = 1;
	glayout.setConstraints(breset,c);
	p.add(breset);

	// "Step" button
	bstep = new JButton("Step");
	bstep.addActionListener(this);
	glayout.setConstraints(bstep,c);
	p.add(bstep);

	// "Run" button
	brun = new JButton("Run");
	brun.addActionListener(this);
	glayout.setConstraints(brun,c);
	p.add(brun);

	// "Stop" button
	bstop = new JButton("Stop");
	bstop.addActionListener(this);
	glayout.setConstraints(bstop,c);
	p.add(bstop);

	// "Stop" button
	balltests = new JButton("All tests");
	balltests.addActionListener(this);
	glayout.setConstraints(balltests,c);
	p.add(balltests);

	return p;
    }

    public LinkedList ReadTape(StreamTokenizer in,int lineno) throws IOException {
	// get name of tape
	in.nextToken();
	if (in.ttype != StreamTokenizer.TT_WORD) {
	    ParseError(lineno,"Expected name of tape");
	    return null;
	}

	String name = in.sval;
	LinkedList initialTape = new LinkedList();
	int initialPosition = 0;

	// process list of symbols used to initialize tape
	while (true) {
	    in.nextToken();
	    if (in.ttype != StreamTokenizer.TT_WORD) break;
	    String sym = in.sval;
	    if (sym.startsWith("[") && sym.endsWith("]")) {
		initialPosition = initialTape.size();
		sym = sym.substring(1,sym.length()-1);
	    }
	    if (!symbols.containsKey(sym)) {
		ParseError(lineno,"Use of undeclared symbol "+sym);
		return null;
	    } else initialTape.add(sym);
	}

	initialTape.addFirst(new Integer(initialPosition));
	initialTape.addFirst(name);
	return initialTape;
    }

    public void ParseError(int lineno,String msg) {
	Message("On line "+(lineno+1)+": "+msg);
	HighlightLine(lineno);
    }

    // parse fsm...
    public boolean Load() {
	tape.InitializeMetrics();
	tapeChecksum = 36038;
	states = new HashMap();
	states.put(HALT,new HashMap());
	states.put(ERROR,new HashMap());
	startingState = ERROR;
	symbols = new HashMap();
	symbols.put(TMTape.EMPTY_CELL,Boolean.TRUE);

	choose.removeAll();			// reset tape chooser
	choose.invalidate();
	ButtonGroup mchoice = new ButtonGroup();
	tButtons = new HashMap();

	tapes = new HashMap();			// name => LinkedList of symbols
	results = new HashMap();		// name => LinkedList of symbols
	tapeNames = new ArrayList();		// tape names, in decl order

	// compute line starts in the buffer
	String tm = text.getText();
	lineStarts = new ArrayList();
	int s = 0;
	while (true) {
	    lineStarts.add(new Integer(s));	// remember where current line starts
	    s = tm.indexOf('\n',s);	// find where next line starts
	    if (s == -1) break;
	    s += 1;			// skip newline
	}
	lineStarts.add(new Integer(tm.length()));  // one final entry marks end of last line

	// set up tokenizer stream
	StreamTokenizer in = new StreamTokenizer(new StringReader(tm));
	in.resetSyntax();
	in.whitespaceChars('\u0000','\u0020');	// space and below are whitespace
	in.wordChars('\u0021','\u00FF');	// everything else is part of a token
	in.ordinaryChar('/');			// make comments work
	in.quoteChar('"');
	in.eolIsSignificant(true);		// mark newlines with a token
	in.slashStarComments(true);		// C style comments
	in.slashSlashComments(true);		// C++ style comments
	
	// parse the description
	boolean error = false;
	ArrayList args = new ArrayList();
	try {
	    while (!error) {
		in.nextToken();
		if (in.ttype == StreamTokenizer.TT_EOF) break;
		else if (in.ttype != StreamTokenizer.TT_WORD) continue;

		int lineno = in.lineno() - 1;
		//System.out.println(lineno); System.out.flush();
		if (in.sval.equalsIgnoreCase("states")) {
		    // process list of state names
		    while (true) {
			in.nextToken();
			if (in.ttype != StreamTokenizer.TT_WORD && in.ttype != '"') break;
			if (!states.containsKey(in.sval)) {
			    if (startingState.equals(ERROR))
				startingState = in.sval;
			    states.put(in.sval,new HashMap());
			    tape.PossibleState(in.sval);
			}
		    }
		} else if (in.sval.equalsIgnoreCase("symbols")) {
		    // process list of legal symbols
		    while (true) {
			in.nextToken();
			if (in.ttype != StreamTokenizer.TT_WORD && in.ttype != '"') break;
			if (!symbols.containsKey(in.sval)) {
			    symbols.put(in.sval,Boolean.TRUE);
			    tape.PossibleSymbol(in.sval);
			}
		    }
		} else if (in.sval.equalsIgnoreCase("tape")) {
		    LinkedList t = ReadTape(in,lineno);
		    if (t != null) {
			String name = (String)t.removeFirst();

			// add appropriate tape selection button
			JRadioButton b = new JRadioButton(name,tapeNames.isEmpty());
			choose.add(b);
			mchoice.add(b);
			b.addActionListener(this);
			tButtons.put(name,b);
			
			tapes.put(name,t);
			tapeNames.add(name);
			tapeChecksum += t.hashCode();	// used to verify test aren't edited
		    }
		} else if (in.sval.equalsIgnoreCase("result")) {
		    LinkedList t = ReadTape(in,lineno);
		    if (t != null) {
			String name = (String)t.removeFirst();
			results.put(name,t);
			tapeChecksum += 31*t.hashCode(); // used to verify test aren't edited
		    }
		} else if (in.sval.equalsIgnoreCase("result1")) {
		    LinkedList t = ReadTape(in,lineno);
		    if (t != null) {
			String name = (String)t.removeFirst();
			t.removeFirst();	// ignore head position
			if (t.size() != 1) {
			    ParseError(lineno,"Expected a single symbol in \"result1\" statement");
			    error = true;
			    break;
			}
			t.addFirst(new Integer(-1));	// indicate that only one cell should be checked
			results.put(name,t);
			tapeChecksum += 31*t.hashCode(); // used to verify test aren
		    }
		} else if (in.sval.equalsIgnoreCase("action")) {
		    // "action state symbol state' write move"
		    // read in all the tokens on the remainder of the line
		    args.clear();
		    while (true) {
			in.nextToken();
			if (in.ttype != StreamTokenizer.TT_WORD && in.ttype != '"') break;
			args.add(in.sval);
		    }
		    if (args.size() != 5) {
			ParseError(lineno,"Expected 5 arguments in an \"action\" statement");
			error = true;
			break;
		    }

		    String state = (String)args.get(0);
		    if (!states.containsKey(state)) {
			ParseError(lineno,"First argument ("+state+") must be one of the declared states");
			error = true;
			break;
		    }
		    String symbol = (String)args.get(1);
		    if (!symbols.containsKey(symbol)) {
			ParseError(lineno,"Second argument ("+symbol+") must be one of the declared symbols");
			error = true;
			break;
		    }
		    String nextState = (String)args.get(2);
		    if (!states.containsKey(nextState)) {
			ParseError(lineno,"Third argument ("+nextState+") must be one of the declared states");
			error = true;
			break;
		    }
		    String write = (String)args.get(3);
		    if (!symbols.containsKey(write)) {
			ParseError(lineno,"Fourth argument ("+write+") must be one of the declared symbols");
			error = true;
			break;
		    }
		    String d = (String)args.get(4);
		    int dir;
		    if (d.equalsIgnoreCase("r")) dir = -1;
		    else if (d.equalsIgnoreCase("l")) dir = 1;
		    else if (d.equalsIgnoreCase("-")) dir = 0;
		    else {
			ParseError(lineno,"Fifth argument ("+d+") must be one of \"l\", \"r\", or \"-\"");
			error = true;
			break;
		    }
		    HashMap actions = (HashMap)states.get(state);
		    if (actions.containsKey(symbol)) {
			ParseError(lineno,"Duplicate action for state \""+state+"\" and symbol \""+symbol+"\"");
			error = true;
			break;
		    }
		    actions.put(symbol,new TMAction(nextState,write,dir,lineno));
		} else if (in.sval.equalsIgnoreCase("checkoff")) {
		    // "checkoff server assignment checksum"
		    // read in all the tokens on the remainder of the line
		    args.clear();
		    while (true) {
			in.nextToken();
			if (in.ttype != StreamTokenizer.TT_WORD && in.ttype != '"') break;
			args.add(in.sval);
		    }
		    if (args.size() != 3) {
			ParseError(lineno,"Expected 3 arguments in a \"checkoff\" statement, found "+args.size());
			error = true;
			break;
		    }

		    checkoffServer = (String)args.get(0);
		    checkoffAssignment = (String)args.get(1);
		    try {
			checkoffChecksum = Integer.parseInt((String)args.get(2));
		    }
		    catch (NumberFormatException e) {
			ParseError(lineno,"Checksum not a number: "+((String)args.get(2)));
			error = true;
			checkoffChecksum = 0;
		    }
		} else {
		    ParseError(lineno,"Unrecognized keyword at beginning of statement: "+in.sval);
		    error = true;
		}
	    }
	}
	catch (IOException e) {
	    System.out.println("IOException parsing TM description: "+e);
	}

	// all done with parse, send initializing info to tape
	tape.FinalizeMetrics();
	validate();
	int ntapes = tapeNames.size();
	if (ntapes > 0) {
	    SelectTape((String)tapeNames.get(0));
	    solved = new boolean[ntapes];
	} else currentTape = -1;
	return error;
    }

    public int getNStates() {
	// don't report ERROR and HALT states as part of count
	return (states == null) ? 0 : states.size() - 2;
    }

    public void Reload() {
	super.Reload();
	Reset();
    }

    public void SelectTape(String tname) {
	currentTape = tapeNames.indexOf(tname);
	if (currentTape != -1) {
	    LinkedList t = (LinkedList)tapes.get(tname);
	    tape.Initialize((LinkedList)t.clone(),startingState);
	}
    }

    public boolean Solved() {
	if (solved != null) {
	    for (int i = 0; i < solved.length; i += 1)
		if (!solved[i]) {
		    System.out.println("tape "+tapeNames.get(i)+" not solved.");
		    return false;
		}
	    return true;
	} else return false;
    }

    protected void Changed() {
	reloadFSM = true;
	capture = true;
	if (solved != null)
	    for (int i = 0; i < solved.length; i += 1) solved[i] = false;
	state = ERROR;
	startingState = state;

	if (breset != null) {
	    breset.setEnabled(true);
	    bstep.setEnabled(false);
	    brun.setEnabled(false);
	    bstop.setEnabled(false);
	    balltests.setEnabled(false);
	    tape.setEnabled(false);
	}
    }

    protected void Enable() {
	breset.setEnabled(true);
	bstep.setEnabled(!reloadFSM && !Halted());
	brun.setEnabled(!reloadFSM && !Halted());
	bstop.setEnabled(false);
	balltests.setEnabled(true);
	tape.setEnabled(!reloadFSM);
    }

    protected void Reset() {
	stop = true;
	tape.Reset();
	counter = 0;
	if (currentTape != -1 && solved != null) solved[currentTape] = false;

	// if necessary, recompile description
	if (reloadFSM) reloadFSM = Load();

	// if there's an error, give focus to edit window
	if (reloadFSM) text.requestFocus();
	else {
	    if (capture && checkoffAssignment != null) {
		parent.DoDataGathering(checkoffAssignment);
		capture = false;
	    }
	    state = startingState;
	    DisplayState();
	}

	// enable/disable appropriate control buttons
	Enable();
    }

    protected boolean Halted() {
	return state.equals(HALT) || state.equals(ERROR);
    }

    protected TMAction Lookup() {
	//System.out.println("Lookup state="+state+" symbol="+tape.Read());
	HashMap actions = (HashMap)states.get(state);	// get actions defined for this state
	return (TMAction)actions.get(tape.Read());	// use current symbol as key
    }

    // run one step of the state machine
    void Step(boolean redraw) {
	if (!Halted()) {
	    counter += 1;
	    TMAction a  = Lookup();
	    if (a != null) {
		state = a.nextState;
		tape.Write(a.writeSymbol,a.direction,state,redraw);
		if (Halted()) stop = true;
	    } else {
		Message("Oops! No action specified for state \""+state+"\" and symbol \""+tape.Read()+"\"");
		state = ERROR;
		stop = true;
	    }
	}
    }

    public void HighlightLine(int lineNumber) {
	int start = ((Integer)lineStarts.get(lineNumber)).intValue();
	int end = ((Integer)lineStarts.get(lineNumber + 1)).intValue() - 1;
	highlighter.removeAllHighlights();
	try {
	    highlighter.addHighlight(start,end,highlightPainter);
	    Rectangle lrect = text.modelToView(start);
	    if (lrect != null) {
		//System.out.println("lrect.y="+lrect.y);
		scroll.getViewport().scrollRectToVisible(lrect);
	    }
	}
	catch (BadLocationException e) { }
    }

    // display current state to user
    public void DisplayState() {
	if (Halted()) {
	    if (!state.equals(ERROR)) {
		String msg = "";

		if (currentTape != -1) {
		    LinkedList t = (LinkedList)results.get(tapeNames.get(currentTape));
		    if (t == null) msg = "oops! no result specified....";
		    else {
			msg = tape.CheckResults((LinkedList)t.clone());
			if (tape.solved) solved[currentTape] = true;
		    }
		}
		Message("Done! ("+counter+" steps) "+msg);
	    }
	} else Message("Cycle "+counter);

	tape.Repaint();
	TMAction a = Lookup();
	if (a != null) HighlightLine(a.lineNumber);
    }

    // run TM in the background
    public void run() {
	if (allTests) {
	    for (int i = 0; i < tapeNames.size(); i += 1) {
		String n = (String)tapeNames.get(i);
		((JRadioButton)tButtons.get(n)).doClick();  // select the last tape
		stop = false;
		while (!stop) Step(false);
		DisplayState();
		if (!solved[i]) break;
	    }
	    Enable();
	    return;
	}

	// just run test user has selected
	int counter = 10000;
	while (!stop) {
	    Step(false);
	    if (delay > 0 || counter-- == 0) {
		counter = 10000;
		DisplayState();
		Toolkit.getDefaultToolkit().sync();
		try { Thread.sleep(delay); }
		catch (InterruptedException e) { break; }
	    }
	}

	DisplayState();
	Enable();
    }

    // invoked by speed slider
    public void stateChanged(ChangeEvent e) {
	if (e.getSource() == speed) delay = MAXDELAY - speed.getValue();
    }

    // invoked by buttons
    public void actionPerformed(ActionEvent event) {
	Object source = event.getSource();

	if (source == breset) {
	    Reset();
	} else if (source == bstop) {
	    stop = true;
	} else if (source == bstep) {
	    Step(true);
	    DisplayState();
	    Enable();
	} else if (source == brun) {
	    breset.setEnabled(false);
	    bstep.setEnabled(false);
	    brun.setEnabled(false);
	    bstop.setEnabled(true);

	    stop = false;
	    text.select(0,0);
	    Message("running...");
	    allTests = false;
	    (new Thread(this)).start();
	} else if (source == balltests) {
	    Reset();			// start from scratch
	    if (reloadFSM) return;		// if there was a parse error, quit now

	    // run each test tape in turn
	    breset.setEnabled(false);
	    bstep.setEnabled(false);
	    brun.setEnabled(false);
	    bstop.setEnabled(true);
	    text.select(0,0);
	    allTests = true;
	    (new Thread(this)).start();
	} else if (source instanceof JRadioButton) {
	    JRadioButton b = (JRadioButton)source;
	    SelectTape(b.getLabel());
	    Reset();
	}
    }
}
