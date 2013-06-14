// Copyright (C) 1999 Christopher J. Terman - All Rights Reserved.

package fsm;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JPanel;

public class Maze extends JPanel {
    static final double STEP = 0.1;	// ant step in grids
    static final double AINC = .2 /*.175*/;	// rotation increment in radians
    static final double ARIGHT = .785;	// 45 degrees
    static final double ALEFT = -.785;
    static final double ANTENNA = 0.34;	// attenna length in grids
    static final int MARK = 4;		// marks/grid dimension
    static final double QUANTA = 0.02;

    int nrows;
    int ncols;
    int map[];
    boolean marks[];
    int csize;
    int xoffset;
    int yoffset;

    double antX;	// ants coords in grids
    double antY;
    double antAngle;	// rotation, degress from north

    public boolean antR;	// right antenna touching a wall
    public boolean antL;	// left antenna touching a wall
    public boolean antS;	// mark under sensor
    public boolean antDone;	// entered finish cell

    double antennaX;
    double antennaY;

    public Maze() {
	super();
	setBackground(Color.blue);
	setOpaque(true);
    }

    public void SetMaze(String m) {
	int index;

	// count rows and columns
	nrows = 0;
	ncols = 0;
	int last = 0;
	int len = m.length();
	while (last < len) {
	    nrows += 1;
	    index = m.indexOf('\n',last);
	    if (index == -1) index = len;
	    ncols = Math.max(ncols,index - last);
	    last = index + 1;
	}
	//System.out.println("rows="+nrows+" cols="+ncols);

	// allocate and fill in map
	map = new int[nrows * ncols];
	marks = new boolean[MARK * MARK * map.length];
	index = 0;
	for (int r = 0; r < nrows; r += 1) {
	    int c = 0;
	    while (true) {
		if (index >= len) break;
		int ch = m.charAt(index++);
		if (ch == '\n') break;
		map[r * ncols + c++] = ch;
	    }
	}

	// reset and repaint
	Reset();
    }

    // restore maze to its original condition
    public void Reset() {
	// place at center of starting cell, facing north
	antX = antY = -1;
	antAngle = -Math.PI/2;
	for (int index = 0; index < map.length; index += 1)
	    if (map[index] == 's') {
		antX = (index % ncols) + 0.5;
		antY = (index / ncols) + 0.5;
		break;
	    }

	// clear up any marks
	for (int index = 0; index < marks.length; index += 1)
	    marks[index] = false;

	CheckAntenna();
	Repaint();
    }


    // ant machinery
    public void Update() {
	CheckAntenna();
	RepaintAnt();
    }

    public void CheckAntenna() {
	antR = AntennaPosition(antAngle + ARIGHT);
	antL = AntennaPosition(antAngle + ALEFT);
	antDone = map[MapIndex(antX,antY)] == 'f';

	// smell using 3x3 grid around current point.  Ignores
	// what happens at edge, so don't let ant too close
	int index = MarkIndex(antX,antY);
	int prev = index - (ncols * MARK);
	int next = index + (ncols * MARK);
	antS = marks[prev-1] | marks[prev] | marks[prev+1];
	antS |= marks[index-1] | marks[index] | marks[index+1];
	antS |= marks[next-1] | marks[next] | marks[next+1];
    }

    public void TurnLeft() {
	antAngle -= AINC;
	if (antAngle < 0) antAngle += 2*Math.PI;
    }

    public void TurnRight() {
	antAngle += AINC;
	if (antAngle >= 2*Math.PI) antAngle -= 2*Math.PI;
    }

    public boolean Forward() {
	if (antL || antR) return false;
	// quantize ant position
	antX += QUANTA*Math.floor(STEP * Math.cos(antAngle) / QUANTA);
	antY += QUANTA*Math.floor(STEP * Math.sin(antAngle) / QUANTA);
	return true;
    }

    public void SetMark(boolean which) {
	int index = MarkIndex(antX,antY);
	int prev = index - (ncols * MARK);
	int next = index + (ncols * MARK);

	// mark 3x3 grid centered around current location.  Ignores
	// what happens at edge, so don't let ant get too close!
	marks[prev-1] = which;
	marks[prev] = which;
	marks[prev+1] = which;
	marks[index-1] = which;
	marks[index] = which;
	marks[index+1] = which;
	marks[next-1] = which;
	marks[next] = which;
	marks[next+1] = which;

	// erase a 5 x 5 grid
	if (!which) {
	    marks[prev-2] = which;
	    marks[prev+2] = which;
	    marks[index-2] = which;
	    marks[index+2] = which;
	    marks[next-2] = which;
	    marks[next+2] = which;
	    prev -= ncols * MARK;
	    marks[prev-2] = which;
	    marks[prev-1] = which;
	    marks[prev] = which;
	    marks[prev+1] = which;
	    marks[prev+2] = which;
	    next += ncols * MARK;
	    marks[next-2] = which;
	    marks[next-1] = which;
	    marks[next] = which;
	    marks[next+1] = which;
	    marks[next+2] = which;
	}

	//System.out.println("mark @ "+MarkIndex(antX,antY));
    }

    // internals...

    int MapIndex(double x,double y) {
	return ((int)Math.floor(y)) * ncols + (int)Math.floor(x);
    }

    int MarkIndex(double x,double y) {
	return ((int)Math.floor(MARK*y))* MARK * ncols + (int)Math.floor(MARK*x);
    }

    boolean AntennaPosition(double angle) {
	antennaX = antX + ANTENNA * Math.cos(angle);
	antennaY = antY + ANTENNA * Math.sin(angle);
	return map[MapIndex(antennaX,antennaY)] == 'X';
    }

    // tell system we need to redraw the ant
    void RepaintAnt() {
	Dimension d = getSize();
	int x = xoffset + ((d.width - csize*ncols) >> 1) + (int)(antX * csize);
	int y = yoffset + ((d.height - csize*nrows) >> 1) + (int)(antY * csize);
	int h = csize;
	Repaint(x-h,y-h,h+h,h+h);
    }

    public void Repaint() {
	Dimension d = getSize();
	Repaint(0,0,d.width,d.height);
    }

    public void DrawAntenna(Graphics g,int x,int y,double angle,boolean touching) {
	AntennaPosition(angle);
	g.setColor(Color.green);
	int ax = x + (int)((antennaX - antX) * csize);
	int ay = y + (int)((antennaY - antY) * csize);
	int ah = csize/12;
	g.drawLine(x,y,ax,ay);
	if (touching) g.setColor(Color.red);
	g.fillOval(ax-ah,ay-ah,ah+ah,ah+ah);
    }

    public void DrawMark(Graphics g,int r,int c,int xoffset,int yoffset,int roffset,int coffset) {
	int index = (MARK*r + roffset)*MARK*ncols + MARK*c + coffset;
	if (marks[index]) {
	    int h2 = csize/MARK;
	    int mx = xoffset + c*csize + coffset*h2;
	    int my = yoffset + r*csize + roffset*h2;
	    g.setColor(Color.lightGray);
	    g.fillRect(mx,my,h2,h2);
	}
    }

    public void DrawMaze(Graphics g,int width,int height,int xoffset,int yoffset) {
	// start with all wall
	//g.setColor(getBackground());
	//g.fillRect(xoffset,yoffset,width,height);

	// choose a building block that's a multiple of MARK
	csize = Math.min(width/ncols,height/nrows);
	csize -= csize % MARK;
	xoffset += (width - csize*ncols) >> 1;
	yoffset += (height - csize*nrows) >> 1;

	// clear out passageways
	int index = 0;
	for (int r = 0; r < nrows; r += 1)
	    for (int c = 0; c < ncols; c += 1) {
		int x = xoffset + c*csize;
		int y = yoffset + r*csize;
		int mch = map[index++];
		if (mch == 'X') continue;	// wall

		g.setColor(Color.white);
		g.fillRect(x,y,csize,csize);

		if (mch == 'f') {
		    g.setColor(Color.magenta);
		    int w = csize >> 2;
		    g.fillOval(x+w,y+w,csize-w-w,csize-w-w);
		}

		// draw marks
		for (int i = 0; i < MARK; i += 1)
		    for (int j = 0; j < MARK; j += 1) 
			DrawMark(g,r,c,xoffset,yoffset,i,j);
	    }

	// paint ant
	if (antX > 0 && antY > 0) {
	    int x = xoffset + (int)(antX * csize);
	    int y = yoffset + (int)(antY * csize);
	    int h = csize/6;
	    g.setColor(Color.green);
	    g.fillOval(x-h,y-h,h+h,h+h);

	    DrawAntenna(g,x,y,antAngle + ARIGHT,antR);
	    DrawAntenna(g,x,y,antAngle + ALEFT,antL);
	}

	/*
	// draw grid
	g.setColor(Color.black);
	for (int r = 0; r <= nrows; r += 1)
	    g.drawLine(xoffset,yoffset + r*csize,
		       xoffset + ncols*csize,yoffset + r*csize);
	for (int c = 0; c <= ncols; c += 1)
	    g.drawLine(xoffset + c*csize,yoffset,
		       xoffset + c*csize,yoffset + nrows*csize);
	*/
    }

    public void Repaint(int cx,int cy,int cw,int ch) {
	// batch repaints into 0.1 sec groups
	repaint(100,cx,cy,cw,ch);
	//repaint(100);
    }

    /*
    public void update(Graphics g) {
	paint(g);
    }

    public void paint(Graphics g) {
	Dimension d = getSize();
	DrawMaze(g,d.width,d.height,0,0);
    }
    */
    public void paintComponent(Graphics g) {
	super.paintComponent(g);	// paint background
	Dimension d = getSize();
	DrawMaze(g,d.width,d.height,0,0);
    }
}
