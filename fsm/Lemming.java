// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

package fsm;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Rectangle;
import java.net.URL;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.JComponent;

import gui.GuiFrame;

public class Lemming extends Thread {
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final int STEP = 2;
    public static final int QUANTUM = 50;

    public static Sequencer midi;

    public static final int INVISIBLE = 0;

    public static final int STANDING = 1;
    static final String STANDING_IMAGES[] = {
	"/fsm/Stander01.gif",
	"/fsm/Stander02.gif",
	"/fsm/Stander03.gif",
	"/fsm/Stander04.gif",
	"/fsm/Stander05.gif",
	"/fsm/Stander06.gif",
	"/fsm/Stander07.gif",
	"/fsm/Stander08.gif",
	"/fsm/Stander09.gif",
	"/fsm/Stander10.gif",
	"/fsm/Stander11.gif",
	"/fsm/Stander12.gif",
	"/fsm/Stander13.gif",
	"/fsm/Stander14.gif"
    };
    static Image standingImages[];

    public static final int WALKING = 2;
    public static final String WALKING_IMAGES[] = {
	"/fsm/Walker01.gif",
	"/fsm/Walker02.gif",
	"/fsm/Walker03.gif",
	"/fsm/Walker04.gif",
	"/fsm/Walker05.gif",
	"/fsm/Walker06.gif",
	"/fsm/Walker07.gif",
	"/fsm/Walker08.gif",
	"/fsm/Walker09.gif",
	"/fsm/Walker10.gif",
	"/fsm/Walker11.gif",
	"/fsm/Walker12.gif",
	"/fsm/Walker13.gif",
	"/fsm/Walker14.gif",
    };
    static Image walkingImages[];
    static int w,h;	// image size

    public int x,y;		// location
    public String facing;	// which way we're facing
    public Rectangle bbox;	// area we're headed into
    int pause;
    int quantum;

    public int nextState;	// what we should be doing
    ElevatorDiagram parent;
    int currentState;		// what we are doing
    int currentSubstate;	// animation substate
    Image currentImages[];

    public Lemming(GuiFrame gui,ElevatorDiagram parent,int pause) {
	this.parent = parent;
	this.pause = pause;
	bbox = new Rectangle();

	if (midi == null) {
	    try {
		midi = MidiSystem.getSequencer();
		midi.open();
		URL url = parent.getClass().getResource("/fsm/Lemmings.mid");
		midi.setSequence(MidiSystem.getSequence(url));
		midi.start();
	    }
	    catch (Exception e) {
		System.out.println(e);
	    }
	}

	// one-time initialization: read in images, wait till ready
	if (standingImages == null) {
	    MediaTracker t = new MediaTracker(parent);
	    int tindex = 0;
	    int n = STANDING_IMAGES.length;

	    standingImages = new Image[n];
	    for (int i = 0; i < n; i += 1) {
		standingImages[i] = gui.GetImageResource(STANDING_IMAGES[i]);
		t.addImage(standingImages[i],tindex++);
	    }

	    n = WALKING_IMAGES.length;
	    walkingImages = new Image[n];
	    for (int i = 0; i < n; i += 1) {
		walkingImages[i] = gui.GetImageResource(WALKING_IMAGES[i]);
		t.addImage(walkingImages[i],tindex++);
	    }

	    try {
		t.waitForAll();
		w = walkingImages[0].getWidth(null);
		h = walkingImages[0].getHeight(null);
	    }
	    catch (InterruptedException e) { }
	}
    }

    public void run() {
	while (!interrupted()) {
	    try {
		sleep(quantum);
		quantum = QUANTUM;
	    }
	    catch (InterruptedException e) { }
	    Step();
	    Repaint();
	}
    }

    synchronized void Step() {
	if (currentSubstate == currentImages.length-1) {
	    // use mutex to ensure two lemmings don't head for
	    // the same open space
	    synchronized (parent) {
		// see if we can start walking...
		int dx = STEP * (walkingImages.length - 1);
		if (facing == LEFT) bbox.setBounds(x-dx,y,dx+w,h);
		else bbox.setBounds(x,y,dx+w,h);
		if (parent.ClearAhead(this)) currentState = WALKING;
		else {
		    bbox.setBounds(x,y,w,h);
		    facing = (facing == LEFT) ? RIGHT : LEFT;
		    currentState = STANDING;
		}
	    }
	    SetupImages();
	} else {
	    currentSubstate += 1;
	    if (currentState == WALKING)
		x += (facing == LEFT) ? -STEP : STEP;
	}
    }

    synchronized public void Reset(int x,int y) {
	quantum = pause;
	currentState = STANDING;
	facing = LEFT;
	this.x = x;
	this.y = y;
	bbox.setBounds(x,y,w,h);
	SetupImages();
    }

    public void SetupImages() {
	currentSubstate = 0;
	switch (currentState) {
	case STANDING:
	    currentImages = standingImages;
	    break;
	case WALKING:
	    currentImages = walkingImages;
	    break;
	}
    }

    public void Repaint() {
	parent.repaint(x-STEP,y-STEP,w+2*STEP,h+2*STEP);
    }

    synchronized public void Draw(Graphics g,Color bg) {
	if (currentState != INVISIBLE) {
	    Image i = currentImages[currentSubstate];
	    if (facing == LEFT)
		g.drawImage(i,x,y,x+w,y+h,0,0,w,h,bg,null);
	    else
		g.drawImage(i,x,y,x+w,y+h,w,0,0,h,bg,null);
	}
    }
}
