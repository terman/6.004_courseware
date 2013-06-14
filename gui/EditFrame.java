// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JToolBar;

public class EditFrame extends EditPanel implements ActionListener, AdjustmentListener, MouseListener, MouseMotionListener, KeyListener {
    public EditCanvas canvas;
    public JToolBar bbar;
    JScrollBar vbar,hbar;
    JButton b1,b2,b3;
    public int mx,my;			// mouse coords
	
    public static final double ZOOM_FACTOR = 1.5;
	
    public EditFrame(GuiFrame p) {
	super(p);
	mx = my = 0;

	// set up tool bar
	bbar = new JToolBar();
	bbar.putClientProperty("JToolBar.isRollover",Boolean.TRUE);
	add("North",bbar);

	// use subsidiary panel for other elements so that
	// toolbar can be repositioned about edge...
	JPanel panel = new JPanel();
	panel.setLayout(new BorderLayout());
	add("Center",panel);

	canvas = new EditCanvas(this);
	canvas.setBorder(BorderFactory.createLoweredBevelBorder());

	vbar = new JScrollBar(JScrollBar.VERTICAL);
	vbar.setBackground(UI.BGCOLOR);
	vbar.addAdjustmentListener(this);
	hbar = new JScrollBar(JScrollBar.HORIZONTAL);
	hbar.addAdjustmentListener(this);
	hbar.setBackground(UI.BGCOLOR);

	Dimension bdim = new Dimension(16,16);
	b1 = p.ImageButton("/icons/zoomin.gif");
	b1.setToolTipText("Zoom in");
	b1.addActionListener(this);
	b1.setPreferredSize(bdim);
	b2 = p.ImageButton("/icons/zoomout.gif");
	b2.setToolTipText("Zoom out");
	b2.addActionListener(this);
	b2.setPreferredSize(bdim);
	b3 = p.ImageButton("/icons/zoom.gif");
	b3.setToolTipText("Surround");
	b3.addActionListener(this);
	b3.setPreferredSize(bdim);

	panel.add("East",vbar);
	panel.add("Center",canvas);

	// use GridBag stuff to bottom row
	JPanel bottom = new JPanel();
	panel.add("South",bottom);
	GridBagLayout glayout = new GridBagLayout();
	bottom.setLayout(glayout);
	GridBagConstraints c = new GridBagConstraints();

	// zoom buttons line up horizontally along bottom edge
	c.fill = GridBagConstraints.NONE;
	c.weightx = 0.0;
	c.weighty = 0.0;
	c.gridwidth = 1;
	glayout.setConstraints(b1,c);
	bottom.add(b1);
	glayout.setConstraints(b2,c);
	bottom.add(b2);
	glayout.setConstraints(b3,c);
	bottom.add(b3);

	// the horizonatal scrollbar fills out the bottom edge
	c.fill = GridBagConstraints.HORIZONTAL;
	c.weightx = 1.0;
	c.gridwidth = GridBagConstraints.REMAINDER;
	glayout.setConstraints(hbar,c);
	bottom.add(hbar);
    }

    public void DrawContents(Graphics g,Transform t,Rectangle bbox) {
    }

    public void MarkModified() {
	observers.notifyObservers(this);
    }

    public void Activate(boolean which) {
	canvas.Activate(which);
	hbar.setEnabled(which);
	vbar.setEnabled(which);
	b1.setEnabled(which);
	b2.setEnabled(which);
	b3.setEnabled(which);
    }

    // override
    public void Surround() {
	canvas.Surround(0,0,0,0);
    }

    // override
    public void SetupScrollbars() {
	SetupScrollbars(0,0,0,0);
    }

    // set scrollbar params to canonical values
    public void SetupScrollbars(int x,int y,int width,int height) {
	Dimension d = canvas.getSize();
	Transform t = canvas.viewTransform;

	int extra = width >> 1;
	int vis = (int)(d.width/t.scale);
	int max = x + width + extra;
	hbar.setValues((int)(-t.orgx/t.scale),vis,x-extra,max);
	hbar.setBlockIncrement(vis);
	int temp = (canvas.grid < vis) ? canvas.grid : vis;
	hbar.setUnitIncrement(temp);

	extra = height >> 1;
	vis = (int)(d.height/t.scale);
	max = y +  height + extra;
	vbar.setValues((int)(-t.orgy/t.scale),vis,y-extra,max);
	vbar.setBlockIncrement(vis);
	vbar.setUnitIncrement(temp);
    }

    // handle scrollbar events
    public void adjustmentValueChanged(AdjustmentEvent event) {
	JScrollBar source = (JScrollBar)event.getSource();
	int max = source.getMaximum() - source.getVisibleAmount();
	int org = (int)(-canvas.GetScale()*Math.min(max,event.getValue()));
	if (source == hbar) canvas.SetOriginX(org);
	else canvas.SetOriginY(org);
	canvas.repaint();
    }
	
    // handle button/menu events
    public void actionPerformed(ActionEvent event) {
	Object s = event.getSource();
	String a = event.getActionCommand();

	if (b1 == s) canvas.SetScale(canvas.GetScale() * ZOOM_FACTOR);
	else if (b2 == s) canvas.SetScale(canvas.GetScale() / ZOOM_FACTOR);
	else if (b3 == s) Surround();
	else super.actionPerformed(event);
    }

    public void mouseEntered(MouseEvent event) {
	canvas.requestFocus();
    }
	
    public void mouseExited(MouseEvent event) { }
    public void mouseClicked(MouseEvent event) { }
    public void mouseMoved(MouseEvent event) {
	mx = event.getX();
	my = event.getY();
    }
    public void mousePressed(MouseEvent event) {}
    public void mouseReleased(MouseEvent event) {}
    public void mouseDragged(MouseEvent event) {}

    public void keyPressed(KeyEvent event) {
	int x = canvas.X(mx);
	int y = canvas.Y(my);

	switch (event.getKeyChar()) {
	case 'Z':
	    canvas.Zoom(mx,my,x,y,canvas.GetScale()*ZOOM_FACTOR);
	    break;
	case 'z':
	    canvas.Zoom(mx,my,x,y,canvas.GetScale()/ZOOM_FACTOR);
	    break;
	case 'c':
	    canvas.Recenter(x,y,canvas.GetScale());
	    break;
	}
    }
    public void keyReleased(KeyEvent event) { }
    public void keyTyped(KeyEvent event) { }
}
