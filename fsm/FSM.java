// Copyright (C) 1999 Christopher J. Terman - All Rights Reserved.

package fsm;

import gui.GuiFrame;
import java.util.Vector;
import javax.swing.JTextArea;

public class FSM {
    int ninputs;
    int noutputs;

    // one line in truth table => one entry in each of the vectors
    Vector state;	// name of state (String)
    Vector pattern;	// input pattern (int[ninputs])
    Vector next;	// name of next state (String)
    Vector outputs;	// outputs (int[noutputs])
    Vector sol;		// start of line (Integer)
    Vector eol;		// end of line (Integer)

    public JTextArea source;
    GuiFrame message;
    int index;

    public FSM(JTextArea source,GuiFrame message,int ninputs,int noutputs) {
	this.source = source;
	this.message = message;
	this.ninputs = ninputs;
	this.noutputs = noutputs;

	state = new Vector();
	pattern = new Vector();
	next = new Vector();
	outputs = new Vector();
	sol = new Vector();
	eol = new Vector();
    }

    void Error(String msg) {
	source.setCaretPosition(index);
	if (message != null)
	    message.Message(msg+" (caret marks location of error)");
    }

    void SkipWhitespace(String spec,int end) {
	while (index < end && Character.isWhitespace(spec.charAt(index)))
	    index += 1;
    }

    String ReadState(String spec,int end) {
	SkipWhitespace(spec,end);
	int start = index;
	while (index < end && Character.isLetterOrDigit(spec.charAt(index)))
	    index += 1;
	if (start == index) {
	    Error("expected state name");
	    return null;
	}
	return spec.substring(start,index);
    }

    int ReadInput(String spec,int end) {
	SkipWhitespace(spec,end);
	int ch = index < end ? spec.charAt(index) : 0;
	if (ch != '0' && ch != '1' && ch != '-') {
	    Error("expected '0', '1' or '-'");
	    return -1;
	}
	index += 1;
	return ch;
    }

    int ReadOutput(String spec,int end) {
	SkipWhitespace(spec,end);
	int ch = index < end ? spec.charAt(index) : 0;
	if (ch != '0' && ch != '1') {
	    Error("expected '0' or '1'");
	    return -1;
	}
	index += 1;
	return ch;
    }

    boolean ReadLine(String spec,int end) {
	int start = index;

	// skip over leading whitespace; ignore comments and empty lines
	SkipWhitespace(spec,end);
	if (index == end || spec.charAt(index) == ';') return false;

	// read input state and input values
	String istate = ReadState(spec,end);
	if (istate == null) return true;
	int ins[] = new int[ninputs];
	for (int i = 0; i < ninputs; i += 1) {
	    int in = ReadInput(spec,end);
	    if (in == -1) return true;
	    ins[i] = in;
	}

	// check for '|' separator
	SkipWhitespace(spec,end);
	if (index >= end || spec.charAt(index) != '|') {
	    Error("expected '|'");
	    return true;
	} else index += 1;

	// read input state and input values
	String ostate = ReadState(spec,end);
	if (ostate == null) return true;
	int outs[] = new int[noutputs];
	for (int i = 0; i < noutputs; i += 1) {
	    int out = ReadOutput(spec,end);
	    if (out == -1) return true;
	    outs[i] = out;
	}

	SkipWhitespace(spec,end);
	if (index != end) {
	    Error("expected end of line");
	    return true;
	}

	state.addElement(istate);
	pattern.addElement(ins);
	next.addElement(ostate);
	outputs.addElement(outs);
	sol.addElement(new Integer(start));
	eol.addElement(new Integer(end));
	return false;
    }

    // load fsm tables from spec.  Each line should look like:
    //	  state in1 in2 ... inN : next out1 out2 outM
    public boolean Load() {
	String spec = source.getText();
	int len = spec.length();
	index = 0;

	state.setSize(0);
	pattern.setSize(0);
	next.setSize(0);
	outputs.setSize(0);
	sol.setSize(0);
	eol.setSize(0);

	while (index < len) {
	    int eol = spec.indexOf('\n',index);
	    if (eol == -1) eol = len;
	    if (ReadLine(spec,eol)) return false;
	    index = eol + 1;
	}
	return true;
    }

    public void DumpState(int i) {
	System.out.print("state="+(String)state.elementAt(i));
	System.out.print(" inputs=");
	int p[] = (int[])pattern.elementAt(i);
	for (int j = 0; j < p.length; j += 1)
	    System.out.print((char)p[j]);
	System.out.println("");
    }

    // find which entry in tables that matches inputs
    public int Match(String s,int inputs[]) {
	int nlines = state.size();
	int index = -1;

	for (int i = 0; i < nlines; i += 1) {
	    //DumpState(i);
	    if (s.equalsIgnoreCase((String)state.elementAt(i))) {
		int p[] = (int[])pattern.elementAt(i);
		boolean match = true;
		for (int n = 0; n < ninputs; n += 1)
		    if (p[n]!='-' && p[n]!=inputs[n]) {
			match = false;
			break;
		    }
		if (match) {
		    if (index != -1 && message != null) {
			String error = "more than one match in FSM: s="+s;
			for (int n = 0; n < ninputs; n += 1)
			    error += " "+((char)inputs[n]);
			message.Message(error);
		    } else index = i;
		}
	    }
	}

	if (index == -1 && message != null) {
	    String error = "no match in FSM: s="+s;
	    for (int n = 0; n < ninputs; n += 1)
		error += " "+((char)inputs[n]);
	    message.Message(error);
	}

	return index;
    }

    public String NextState(int index) {
	if (index >= 0 && index < next.size())
	    return (String)next.elementAt(index);
	else return null;
    }

    public int[] Outputs(int index) {
	if (index >= 0 && index < next.size()) 
	    return (int [])outputs.elementAt(index);
	return null;
    }
}

