// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

package gui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.Rectangle;
import javax.swing.JPanel;

public class EditCanvas extends JPanel implements ComponentListener {
    public EditFrame parent;
    public Transform viewTransform;
    public Rectangle tempRect;
    public int grid;
    int cursor;				// cursor we should be using
	
    public EditCanvas(EditFrame e) {
	super();
	setBackground(UI.EDITBGCOLOR);
	setOpaque(true);
	parent = e;
		
	tempRect = new Rectangle();
	viewTransform = new Transform(0,0,Transform.NORTH,1.0);
	grid = 1;
	cursor = Cursor.DEFAULT_CURSOR;
		
	addMouseListener(e);
	addMouseMotionListener(e);
	addKeyListener(e);
	addComponentListener(this);
    }
	
    public void Repaint() {
	repaint();
    }
	
    public void Repaint(Rectangle r) {
	viewTransform.TransformRectangle(r,tempRect);
	tempRect.width += 1;	// avoid fractional pixel problems on repaint
	tempRect.height += 1;
	repaint(tempRect);
    }
	
    public void Repaint(int x,int y,int w,int h) {
	tempRect.setBounds(x,y,w,h);
	Repaint(tempRect);
    }

    public void Activate(boolean which) {
	setEnabled(which);
	if (which) {
	    requestFocus();
	    setCursor(Cursor.getPredefinedCursor(cursor));
	    Repaint();
	} else setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
	
    public void SetCursor(int newcursor) {
	if (cursor != newcursor) {
	    cursor = newcursor;
	    if (isEnabled()) setCursor(Cursor.getPredefinedCursor(cursor));
	}
    }

    public void DrawGrid(Graphics g, Rectangle clip) {
	// don't bother with grid when it gets small
	if ((grid * viewTransform.scale) >= 4) {
	    int minx = OnGrid(clip.x);
	    int maxx = OnGrid(clip.x + clip.width);
	    int miny = OnGrid(clip.y);
	    int maxy = OnGrid(clip.y + clip.height);
	    g.setColor(UI.GRIDCOLOR);
	    for (int i = minx; i <= maxx; i += grid)
		for (int j = miny; j <= maxy; j += grid) {
		    boolean mod8 = grid == 1 && (i % 8)==0 && (j % 8)==0;
		    viewTransform.DrawGridPoint(g,i,j,grid,mod8);
		}
					
	    // draw origin
	    viewTransform.DrawLine(g,-1,0,1,0);
	    viewTransform.DrawLine(g,0,-1,0,1);
	}
    }
	
    public void paintComponent(Graphics g) {
	super.paintComponent(g);

	Rectangle repaintRect = g.getClipBounds();
	tempRect.setBounds(X(repaintRect.x),Y(repaintRect.y),
			   X(repaintRect.width+repaintRect.x)-X(repaintRect.x),
			   Y(repaintRect.height+repaintRect.y)-Y(repaintRect.y));
	DrawGrid(g,tempRect);
	parent.DrawContents(g,viewTransform,tempRect);
    }

    public void componentHidden(ComponentEvent e) { }
    public void componentMoved(ComponentEvent e) { }
    public void componentShown(ComponentEvent e) { }
    public void componentResized(ComponentEvent e) {
	parent.SetupScrollbars();
	parent.Surround();
    }
	
    // convert canvas coordinate to on-grid schematic coordinate
    public int X(int x) {
	if (viewTransform == null) return 0;
	return (int)Math.round((x - viewTransform.orgx)/viewTransform.scale);
    }

    // convert canvas coordinate to on-grid schematic coordinate
    public int Y(int y) {
	if (viewTransform == null) return 0;
	return (int)Math.round((y - viewTransform.orgy)/viewTransform.scale);
    }
	
    // determine nearest grid point
    public int OnGrid(int v) {
	if (v < 0) return ((-v+(grid>>1))/grid)*-grid;
	else return ((v+(grid>>1))/grid)*grid;
    }

    // set viewTransform so that entire plot is visible
    public void Surround(int x,int y,int width,int height) {
	Dimension d = getSize();

	// choose scale so entire plot will fill about 90% of the view
	double scalex = (width==0) ? 4.0 : ((double)d.width)/width;
	double scaley = (height==0) ? 4.0 : ((double)d.height)/height;
	double scale = .9 * ((scalex < scaley) ? scalex : scaley);

	Recenter(x + (width >> 1),y + (height >> 1),scale);
    }

    // zoom display about (x,y) with specified scale
    public void Zoom(int mx,int my,int x,int y,double newscale) {
	viewTransform.orgx = mx - (int)(x * newscale);
	viewTransform.orgy = my - (int)(y * newscale);
	SetScale(newscale);
    }

    // recenter display about (x,y) with specified scale
    public void Recenter(int x,int y,double newscale) {
	Dimension d = getSize();

	viewTransform.orgx = d.width/2 - (int)(x * newscale);
	viewTransform.orgy = d.height/2 - (int)(y * newscale);
	SetScale(newscale);
    }

    public void SetScale(double scale) {
	viewTransform.scale = scale;
	parent.SetupScrollbars();
	Repaint();
    }
	
    public void SetGrid(int newgrid) {
	if (newgrid > 0 && grid != newgrid)  {
	    grid = newgrid;
	    Repaint();
	}
    }
	
    public double GetScale() {
	return viewTransform.scale;
    }
	
    public int GetOriginX() {
	return (int)viewTransform.orgx;
    }

    public int GetOriginY() {
	return (int)viewTransform.orgy;
    }

    public void SetOriginX(int x) {
	viewTransform.orgx = x;
	Repaint();
    }
	
    public void SetOriginY(int y) {
	viewTransform.orgy = y;
	Repaint();
    }
	
    protected void RepaintRectangle(Rectangle rect) {
	viewTransform.TransformRectangle(rect,tempRect);
	Repaint(tempRect.x-2,tempRect.y-2,
		tempRect.width+4,tempRect.height+4);
    }
}
