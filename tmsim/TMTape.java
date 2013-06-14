// Copyright (C) 2003 Christopher J. Terman - All Rights Reserved.

package tmsim;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.util.LinkedList;
import javax.swing.JPanel;

public class TMTape extends JPanel {
    public static String EMPTY_CELL = "-";
    public static Font FONT = Font.decode("SansSerif-BOLD-14");
    public static int XMARGIN = 5;
    public static int YMARGIN = 5;
    public static int ARROWLEN = 30;
    public static Color STATE_COLOR = Color.yellow;
    public static Color TAPE_COLOR = Color.white;
    public static Color HEAD_COLOR = new Color(0xFF,0xFF,0xC0);


    FontMetrics fm;		// metrics for display font
    int cellHeight;		// height of one cell on the tape
    int baselineOffset;		// where to draw label inside the cell
    int stateWidth;		// width of widest state name
    int tapeWidth;		// width of widest symbol name

    LinkedList initialContents;	// initial contents of tape (after reset)
    int initialHeadPosition;	// initial position of head
    String initialState;	// initial state for FSM
    LinkedList tape;		// current contents of tape
    int headPosition;		// current position of head [0..tape.size()]
    String currentState;	// used for display
    boolean solved;

    public TMTape() {
	super();
	setOpaque(true);

	// initialize tape info
	initialContents = new LinkedList();
	initialHeadPosition = 0;
	tape = new LinkedList();
	headPosition = 0;

	InitializeMetrics();	// set up display metrics (will be redone)
	FinalizeMetrics();
	currentState = "unknown";
    }

    public void CanonicalizeTape() {
	while (((String)tape.getFirst()).equals(EMPTY_CELL)) {
	    tape.removeFirst();
	    headPosition -= 1;
	}
	while (((String)tape.getLast()).equals(EMPTY_CELL)) tape.removeLast();
    }

    // compare current tape contents and head position with expected values
    public String CheckResults(LinkedList expected) {
	CanonicalizeTape();
	solved = false;
	int epos = ((Integer)expected.removeFirst()).intValue();

	// negative head position means that only the current tape
	// cell should be checked
	if (epos < 0) {
	    String have = Read(); //(String)tape.get(headPosition);
	    String want = (String)expected.get(0);
	    if (!have.equalsIgnoreCase(want))
		return "Expected symbol '"+want+"' in current cell, found symbol '"+have+"'.";
	    else {
		solved = true;
		return "TM tape has expected contents!";
	    }
	}

	if (tape.size() != expected.size())
	    return "TM tape has "+tape.size()+" cells, expected "+expected.size()+" cells.";

	for (int i = 0; i < tape.size(); i += 1) {
	    String have = (String)tape.get(i);
	    String want = (String)expected.get(i);
	    if (!have.equalsIgnoreCase(want))
		return "Expected symbol '"+want+"' in cell "+(i+1)+", found symbol '"+have+"'.";
	}

	if (epos != headPosition)
	    return "Expected head to be reading cell "+(epos+1)+" but it's reading cell "+(headPosition+1)+" instead.";

	solved = true;
	return "TM tape has expected contents!";
    }

    public void InitializeMetrics() {
	fm = Toolkit.getDefaultToolkit().getFontMetrics(FONT);
	baselineOffset = YMARGIN + fm.getAscent();
	cellHeight = baselineOffset + fm.getDescent() + YMARGIN;
	stateWidth = 0;
	tapeWidth = fm.stringWidth(EMPTY_CELL);
    }

    // keep track of widest symbol
    public void PossibleSymbol(String s) {
	int w = fm.stringWidth(s);
	if (w > tapeWidth) tapeWidth = w;
    }

    // keep track of widest state
    public void PossibleState(String s) {
	int w = fm.stringWidth(s);
	if (w > stateWidth) stateWidth = w;
    }

    public void FinalizeMetrics() {
	stateWidth += XMARGIN + XMARGIN;
	if (stateWidth < 100) stateWidth = 100;
	tapeWidth += XMARGIN + XMARGIN;
	//if (tapeWidth < 100) tapeWidth = 100;

	//setMinimumSize(new Dimension(stateWidth + tapeWidth + ARROWLEN,100));
	setMinimumSize(new Dimension(stateWidth,2*(YMARGIN+cellHeight) + ARROWLEN));
	setPreferredSize(getMinimumSize());
	invalidate();
    }


    // set up initial contents and head position
    public void Initialize(LinkedList init,String start) {
	initialHeadPosition = ((Integer)init.removeFirst()).intValue();
	initialContents = init;
	initialState = start;
	Reset();
    }

    // restore tape to its original condition
    public void Reset() {
	headPosition = initialHeadPosition;
	tape = (LinkedList)initialContents.clone();
	currentState = initialState;
	Repaint();
    }

    // return tape symbol at specified offset from current head position
    public String Read(int offset) {
	String result;
	int pos = headPosition + offset;
	if (pos < 0 || pos >= tape.size()) result = EMPTY_CELL;
	else result = (String)tape.get(pos);

	return result;
    }


    // return tape symbol at current head position
    public String Read() {
	return Read(0);
    }

    // write the tape, move and update state name
    public void Write(String symbol,int direction,String state,boolean redraw) {
	if (tape.size() == 0) tape.add(symbol);
	else tape.set(headPosition,symbol);
	if (direction > 0) {
	    headPosition += 1;
	    if (headPosition == tape.size()) tape.addLast(EMPTY_CELL);
	} else if (direction < 0) {
	    headPosition -= 1;
	    if (headPosition < 0) {
		tape.addFirst(EMPTY_CELL);
		headPosition = 0;
	    }
	}
	currentState = state;
	if (redraw) Repaint();
    }

    public void Repaint() {
	// batch repaints into 0.1 sec groups
	repaint(100);
    }

    public void paintComponent(Graphics g) {
	super.paintComponent(g);	// paint background
	Dimension d = getSize();
	HDrawTape(g,d.width,d.height);
    }

    // draw a white rectangle w/ black edge w/ string centered inside
    public void DrawBox(Graphics g,int x,int y,int w,int h,Color c,String s) {
	g.setColor(c);
	g.fillRect(x,y,w,h);
	g.setColor(Color.black);
	g.drawRect(x,y,w,h);
	if (isEnabled() && s != null) {
	    int xoffset = (w - fm.stringWidth(s)) >> 1;
	    g.setFont(FONT);
	    g.drawString(s,x+xoffset,y+baselineOffset);
	}
    }

    public void VDrawTape(Graphics g,int w,int h) {
	int ncells = h / cellHeight;
	int hcell = ncells >> 1;
	int yoffset = (h - ncells*cellHeight) >> 1;
	int xoffset = (w - stateWidth - tapeWidth - ARROWLEN) >> 1;

	int ystate = yoffset+hcell*cellHeight;
	DrawBox(g,xoffset,ystate,stateWidth,cellHeight,STATE_COLOR,currentState);

	ystate += cellHeight/2;
	xoffset += stateWidth + ARROWLEN;
	g.drawLine(xoffset-ARROWLEN,ystate,xoffset,ystate);
	g.drawLine(xoffset-10,ystate-5,xoffset,ystate);
	g.drawLine(xoffset-10,ystate+5,xoffset,ystate);

	for (int y = 0; y < ncells; y += 1) {
	    String label = "...";
	    if (y > 0 && y < ncells-1) label = Read(y - hcell);
	    Color c = TAPE_COLOR;
	    if (y == hcell) c = HEAD_COLOR;
	    DrawBox(g,xoffset,y*cellHeight+yoffset,tapeWidth,cellHeight,c,label);
	}
    }

    public void HDrawTape(Graphics g,int w,int h) {
	int ncells = w / tapeWidth;
	int hcell = ncells >> 1;
	int yoffset = (h - 2*cellHeight - ARROWLEN) >> 1;
	int xoffset = (w - ncells*tapeWidth) >> 1;

	// tape contents
	for (int x = 0; x < ncells; x += 1) {
	    String label = "...";
	    if (x > 0 && x < ncells-1) label = Read(x - hcell);
	    Color c = TAPE_COLOR;
	    if (x == hcell) c = HEAD_COLOR;
	    DrawBox(g,x*tapeWidth + xoffset,yoffset,tapeWidth,cellHeight,c,label);
	}


	// current state
	int xstate = xoffset + hcell*tapeWidth + (tapeWidth>>1);
	int ystate = yoffset + cellHeight + ARROWLEN;
	DrawBox(g,xstate-(stateWidth>>1),ystate,stateWidth,cellHeight,STATE_COLOR,currentState);


	// decorative arrow
	ystate -= ARROWLEN;
	g.drawLine(xstate,ystate+ARROWLEN,xstate,ystate);
	g.drawLine(xstate-5,ystate+10,xstate,ystate);
	g.drawLine(xstate+5,ystate+10,xstate,ystate);
    }
}
