// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

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

public class ElevatorPanel extends EditBuffer implements ChangeListener, Runnable {
    public static final int MAXDELAY = 500;
    public static final int INITDELAY = 50;
    public static int NFLOORS = 3;

    FSM fsm;
    int mindex;
    ElevatorDiagram diagram;
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
    boolean solved;

    public ElevatorPanel(GuiFrame parent,File source) {
	super(parent,source);
	delay = INITDELAY;
	fsm = new FSM(text,parent,NFLOORS,4);
	inputs = new int[NFLOORS];

	// remember if we've solved it
	solved = false;
	Changed();	// initialize solved array

	// set up state highlighter
	highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255,255,128));
	highlighter = text.getHighlighter();

	// set up two view ports: one for text, one for display
	JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
					  scroll,
					  DiagramPanel());
	split.setOneTouchExpandable(true);
	split.setDividerLocation(300);
	add(split,BorderLayout.CENTER);
    }

    JPanel DiagramPanel() {
	JPanel p = new JPanel();

	// build contents
	GridBagLayout glayout = new GridBagLayout();
	p.setLayout(glayout);
	GridBagConstraints c = new GridBagConstraints();
	c.anchor = GridBagConstraints.CENTER;

	// display
	diagram = new ElevatorDiagram(parent,NFLOORS);
	c.fill = GridBagConstraints.BOTH;
	c.weightx = 1;
	c.weighty = 1;
	c.gridwidth = GridBagConstraints.REMAINDER;
	glayout.setConstraints(diagram,c);
	p.add(diagram);

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

	return p;
    }

    public int Solved() {
	return solved ? 1 : 0;
    }

    protected void Changed() {
	reloadFSM = true;
	solved = false;
    }

    public void setText(String t) {
	super.setText(t);
	Changed();
	Reset();
    }

    void Reset() {
	stop = true;
	diagram.Reset();
	state = "start";
	counter = 0;

	if (ReloadFSM()) DisplayState();

	breset.setEnabled(true);
	bstep.setEnabled(!diagram.done);
	brun.setEnabled(!diagram.done);
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
	for (int i = 0; i < NFLOORS; i += 1)
	    inputs[i] = diagram.button[i] ? '1' : '0';
	return fsm.Match(state,inputs);
    }

    // run one step of the state machine
    void Step() {
	if (!diagram.done) {
	    counter += 1;
	    int index = FSMIndex();
	    if (index >= 0) {
		int outputs[] = fsm.Outputs(index);
		if (outputs != null) {
		    // then move
		    if (outputs[0] != '0') diagram.Up();
		    if (outputs[1] != '0') diagram.Down();
		    if (outputs[2] != '0') diagram.OpenDoor();
		    if (outputs[3] != '0') diagram.CloseDoor();
		    // see what we've found
		    diagram.Update();
		    if (diagram.done) stop = true;
		}
		state = fsm.NextState(index);
		if (state == null) state = "start";
	    } else {
		// issue complaint here
		stop = true;
	    }
	}
    }

    // display current state to user
    public void DisplayState() {
	if (diagram.done) {
	    Message("Done! ("+counter+" steps)");
	    solved = true;
	} else {
	    String msg = "state: \""+state+"\", inputs:";
	    for (int i = 0; i < NFLOORS; i += 1)
		msg = msg + " F" + (i+2) + "=" + (diagram.button[i] ? "1":"0");
	    Message(msg+"; step "+counter);
	}

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

    // run the simulation in the background
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
	bstep.setEnabled(!diagram.done);
	brun.setEnabled(!diagram.done);
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
	}
    }
}
