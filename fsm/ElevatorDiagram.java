// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

package fsm;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.Random;
import javax.swing.JPanel;

import gui.GuiFrame;

public class ElevatorDiagram extends JPanel {
    public boolean button[];	// request from floor N
    public boolean done;	// all customers serviced?

    Lemming lemmings[];
    GuiFrame parent;

    public ElevatorDiagram(GuiFrame parent,int nfloors) {
	super();
	setBackground(Color.white);
	setOpaque(true);
	this.parent = parent;

	button = new boolean[nfloors];

	lemmings = new Lemming[nfloors*3];
	Reset();
    }

    public boolean ClearAhead(Lemming l) {
	Rectangle r = l.bbox;
	if (r.x <= 40 || r.x+r.width >= 450) return false;
	for (int i = 0; i < lemmings.length; i += 1) {
	    if (l == lemmings[i]) continue;
	    if (r.intersects(lemmings[i].bbox)) return false;
	}
	return true;
    }

    // restore maze to its original condition
    public void Reset() {
	for (int i = 0; i < button.length; i += 1)
	    button[i] = false;

	// kill off last batch
	for (int i = 0; i < lemmings.length; i += 1)
	    if (lemmings[i] != null)
		lemmings[i].interrupt();

	// create new lemmings
	Random pause = new Random();
	for (int i = 0; i < lemmings.length; i += 1) {
	    int wait = pause.nextInt(Lemming.QUANTUM*300);
	    lemmings[i] = new Lemming(parent,this,wait);
	    lemmings[i].Reset(408 - 34*(i%3),60*(i/3)+60);
	}

	// let 'em rip
	for (int i = 0; i < lemmings.length; i += 1)
	    lemmings[i].start();

	repaint();
    }


    // simulate one step
    public void Update() {
    }

    // up one floor
    public void Up() {
    }

    // down one floor
    public void Down() {
    }

    // open door
    public void OpenDoor() {
    }

    // close door
    public void CloseDoor() {
    }

    public void paintComponent(Graphics g) {
	super.paintComponent(g);	// paint background
	for (int i = 0; i < lemmings.length; i += 1)
	    lemmings[i].Draw(g,Color.white);
    }
}
