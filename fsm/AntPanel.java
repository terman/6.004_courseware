// Copyright (C) 1999 Christopher J. Terman - All Rights Reserved.

package fsm;

import gui.EditBuffer;
import gui.GuiFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
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

public class AntPanel extends EditBuffer implements ChangeListener, Runnable {
    public static final int MAXDELAY = 500;
    public static final int INITDELAY = 50;

    FSM fsm;
    int mindex;
    Maze maze;
    JSlider speed;
    JButton breset;
    JButton bstep;
    JButton brun;
    JButton bstop;
    Highlighter.HighlightPainter highlightPainter;
    Highlighter highlighter;

    int delay;
    int inputs[];
    boolean reloadFSM;
    boolean fsmOkay;
    boolean stop;
    String state;
    int counter;
    boolean solved[];

    public AntPanel(GuiFrame parent,File source) {
	super(parent,source);
	delay = INITDELAY;
	fsm = new FSM(text,parent,3,5);
	inputs = new int[3];

	// remember if we've solved all the mazes
	solved = new boolean[Ant.mazes.length];
	Changed();	// initialize solved array

	// set up state highlighter
	highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255,255,128));
	highlighter = text.getHighlighter();

	// set up two view ports: one for text, one for maze display
	JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
					  scroll,
					  MazePanel());
	split.setOneTouchExpandable(true);
	split.setDividerLocation(300);
	add(split,BorderLayout.CENTER);
    }

    JPanel MazePanel() {
	JPanel p = new JPanel();

	// build contents
	GridBagLayout glayout = new GridBagLayout();
	p.setLayout(glayout);
	GridBagConstraints c = new GridBagConstraints();
	c.anchor = GridBagConstraints.CENTER;

	// maze selection
	ButtonGroup mchoice = new ButtonGroup();
	JPanel choose = new JPanel(new FlowLayout(FlowLayout.CENTER));
	c.fill = GridBagConstraints.HORIZONTAL;
	c.weightx = 1;
	c.gridwidth = GridBagConstraints.REMAINDER;
	glayout.setConstraints(choose,c);
	p.add(choose);
	for (int i = 0; i < Ant.mazeNames.length; i += 1) {
	    JRadioButton b = new JRadioButton(Ant.mazeNames[i],i == 0); 
	    choose.add(b);
	    mchoice.add(b);
	    b.addActionListener(this);
	}

	// maze display
	maze = new Maze();
	c.fill = GridBagConstraints.BOTH;
	c.weighty = 1;
	glayout.setConstraints(maze,c);
	p.add(maze);

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

	// set initial view
	SetMaze(0);

	return p;
    }

    public void SetMaze(int index) {
	mindex = index;
	maze.SetMaze(Ant.mazes[index]);
	reloadFSM = true;
	Reset();
    }

    public int Solved() {
	int result = 0;
	for (int i = 0; i < solved.length; i += 1)
	    if (solved[i]) result |= 1 << i;
	return result;
    }

    protected void Changed() {
	reloadFSM = true;
	for (int i = 0; i < solved.length; i += 1) solved[i] = false;
    }

    void Reset() {
	stop = true;
	maze.Reset();
	state = "lost";
	counter = 0;

	if (ReloadFSM()) DisplayState();

	breset.setEnabled(true);
	bstep.setEnabled(!maze.antDone);
	brun.setEnabled(!maze.antDone);
	bstop.setEnabled(false);
    }

    boolean ReloadFSM() {
	if (reloadFSM) {
	    reloadFSM = false;
	    fsmOkay = fsm.Load();
	}

	// make sure caret is showing...
	if (!fsmOkay) text.requestFocus();
	return fsmOkay;
    }

    int FSMIndex() {
	inputs[0] = maze.antL ? '1' : '0';
	inputs[1] = maze.antR ? '1' : '0';
	inputs[2] = maze.antS ? '1' : '0';
	return fsm.Match(state,inputs);
    }

    // run one step of the state machine
    void Step() {
	if (!maze.antDone) {
	    counter += 1;
	    int index = FSMIndex();
	    if (index >= 0) {
		int outputs[] = fsm.Outputs(index);
		if (outputs != null) {
		    // do our business
		    if (outputs[3] != '0') maze.SetMark(true);
		    if (outputs[4] != '0') maze.SetMark(false);
		    // then move
		    if (outputs[0] != '0') maze.TurnLeft();
		    if (outputs[1] != '0') maze.TurnRight();
		    if (outputs[2] != '0') maze.Forward();
		    // see what we've found
		    maze.Update();
		    if (maze.antDone) stop = true;
		}
		state = fsm.NextState(index);
		if (state == null) state = "lost";
	    } else {
		// issue complaint here
		stop = true;
	    }
	}
    }

    // display current state to user
    public void DisplayState() {
	if (maze.antDone) {
	    Message("Done! ("+counter+" steps)");
	    solved[mindex] = true;
	}
	else Message("state: \""+state+"\", inputs: L="+(maze.antL?"1":"0")+" R="+(maze.antR?"1":"0")+" S="+(maze.antS?"1":"0")+"; step "+counter);

	int index = FSMIndex();
	if (index >= 0) {
	    int start = ((Integer)fsm.sol.elementAt(index)).intValue();
	    int end = ((Integer)fsm.eol.elementAt(index)).intValue();
	    // text.select(start,end);
	    highlighter.removeAllHighlights();
	    try {
		highlighter.addHighlight(start,end,highlightPainter);
	    }
	    catch (BadLocationException e) { }
	}
    }

    // run the ant in the background
    public void run() {
	while (!stop) {
	    Step();
	    if (delay > 0) {
		DisplayState();
		try { Thread.sleep(delay); }
		catch (InterruptedException e) { break; }
	    }
	    // on some platforms the GUI thread gets no time
	    // unless we explicitly yield
	    //Thread.yield();
	    Toolkit.getDefaultToolkit().sync();
	}

	DisplayState();
	breset.setEnabled(true);
	bstep.setEnabled(!maze.antDone);
	brun.setEnabled(!maze.antDone);
	bstop.setEnabled(false);
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
	    if (ReloadFSM()) {
		Step();
		DisplayState();
	    }
	} else if (source == brun) {
	    if (ReloadFSM()) {
		breset.setEnabled(false);
		bstep.setEnabled(false);
		brun.setEnabled(false);
		bstop.setEnabled(true);

		stop = false;
		text.select(0,0);
		Message("running...");
		(new Thread(this)).start();
	    }
	} else if (source instanceof JRadioButton) {
	    String label = ((JRadioButton)source).getText();
	    for (int i = 0; i < Ant.mazeNames.length; i += 1)
		if (label.equals(Ant.mazeNames[i])) {
		    SetMaze(i);
		    Reset();
		    break;
		}
	}
    }
}
