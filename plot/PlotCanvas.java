// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package plot;

import gui.UI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import javax.swing.JComponent;

public class PlotCanvas extends JComponent implements MouseListener, MouseMotionListener, KeyListener {
    static final int grid = 25;
    static final int ncursors = 2;
    static final int csize = 10;

    static final Color Colors[] = {
	Color.green,
	Color.red,
	Color.magenta,
	Color.cyan,
	Color.yellow
    };

    Plot parent;		// where to find vzoom
    ArrayList v;		// where to find data
    ArrayList vtype;		// how to display data (if not analog)

    public Image buffer;	// off-screen buffer for plot
    int mouseX,mouseY;		// current mouse coords

    double vdiv;		// units/grid vertically
    int xoff,yoff;		// coords of top left corner grid point
    double maxy,miny,maxx,minx;	// bounds of the plot coordinates
    double scalex,scaley;	// scale factors for each axis
    double xorigin,yorigin;	// coordinate origins (set by scroll bars)
    String vaxis,haxis;
  
    Font tfont;			// text font for annotations
    Rectangle cursor[];		// bounding box for cursor
    double curX[],curY[];	// actual coords for cursor
    String location[];		// string describing location of cursor
    int lx[],ly[];		// where to draw location string
    Rectangle locnBBox[];	// bounding box of cursor location
    boolean needsPlot;

    public PlotCanvas(Plot p) {
	parent = p;
	v = new ArrayList();
	vtype = new ArrayList();
	needsPlot = false;

	setBackground(UI.PBGColor);
	buffer = null;

	cursor = new Rectangle[ncursors];
	curX = new double[ncursors];
	curY = new double[ncursors];
	location = new String[ncursors];
	lx = new int[ncursors];
	ly = new int[ncursors];
	locnBBox = new Rectangle[ncursors];
	for (int i = 0; i < ncursors; i += 1) {
	    cursor[i] = new Rectangle();
	    locnBBox[i] = new Rectangle();
	}
	tfont = new Font("Helvetica",Font.PLAIN,10);

	addMouseListener(this);
	addMouseMotionListener(this);
	addKeyListener(this);

	ClearPlotData();
    }

    public int Grid() {
	Dimension d = getSize();
	if (d.width <= 0 || d.height <= 0) return grid;
	else if (d.height < (3*grid)/2) return (2*d.height)/3;
	else return grid;
    }

    public void ClearPlotData() {
	v.clear();
	vtype.clear();
	maxx = Double.NEGATIVE_INFINITY;
	minx = Double.POSITIVE_INFINITY;
	maxy = Double.NEGATIVE_INFINITY;
	miny = Double.POSITIVE_INFINITY;
	yorigin = 0.0;
	haxis = "";
	vaxis = "";
	needsPlot = true;
    }

    public void AddPlotData(ArrayList dvector,Object type) {
	int nplots = dvector.size();
	for (int i = 0; i < nplots; i += 1) {
	    PlotData d = (PlotData)dvector.get(i);
	    if (d.xmin < minx) minx = d.xmin;
	    if (d.xmax > maxx) maxx = d.xmax;
	    if (d.ymin < miny) miny = d.ymin;
	    if (d.ymax > maxy) maxy = d.ymax;

	    if (vaxis.length() == 0 && d.vaxis != null) vaxis = d.vaxis;
	    if (haxis.length() == 0 && d.haxis != null) haxis = d.haxis;

	    v.add(d);
	    vtype.add(type);
	}
	needsPlot = true;
    }

    // make sure local buffer is all set
    private boolean EnsureBuffer(boolean plot) {
	Dimension actual = getSize();

	// see if we actually have some real estate to work with...
	// if not, the update mechanism will do the right thing.
	if (actual.width <= 0 || actual.height <= 0) return false;

	if (buffer == null || buffer.getHeight(null) != actual.height ||
	    buffer.getWidth(null) != actual.width) {
	    try {
		buffer = createImage(actual.width,actual.height);
		if (buffer == null) return false;
	    }
	    catch (Exception e) {
		// sometimes createImage fails throwing an exception
		// if the windows aren't quite right yet...
		return false;
	    }

	    // compute location of cursor's location string
	    Graphics g = buffer.getGraphics();
	    FontMetrics fm = g.getFontMetrics(tfont);
	    int h = fm.getHeight();
	    int w = 40 * fm.charWidth('m');
	    for (int i = 0; i < ncursors; i += 1) {
		locnBBox[i].width = w;
		lx[i] = 3;
		ly[i] = (i+1)*h;
		locnBBox[i].x = lx[i];
		locnBBox[i].y = ly[i] - fm.getMaxAscent() - i*h;
		locnBBox[i].height = ly[i] - locnBBox[i].y + fm.getMaxDescent();
	    }

	    if (plot) Plot();
	}
	return true;
    }

    public void paintComponent(Graphics g) {
	// don't bother plotting if we don't have a size yet
	if (!EnsureBuffer(true)) return;

	// copy pre-draw waveform display onto screen
	if (needsPlot) Plot();
	g.drawImage(buffer,0,0,this);

	// overlay cursors
	for (int i = 0; i < ncursors; i += 1)
	    if (!cursor[i].isEmpty()) {
		g.setColor((i == 0) ? UI.PScaleColor : UI.PScale2Color);
		int temp = cursor[i].x + cursor[i].width/2;
		g.drawLine(temp,cursor[i].y,temp,cursor[i].y + cursor[i].height - 1);
		temp = cursor[i].y + cursor[i].height/2;
		g.drawLine(cursor[i].x,temp,cursor[i].x + cursor[i].width - 1,temp);
		if (location[i] != null) {
		    g.setFont(tfont);
		    g.drawString(location[i],lx[i],ly[i]);
		}
	    }
    }

    public void update(Graphics g) {
	paint(g);		// avoid unnecessary repaint of background
    }

    // find nearest multiple of 1, 2, or 5 that is larger than
    // the specified grid -- just like scales on an oscilloscope
    public static double ChooseScale(double grid,int zoom) {
	double log10 = Math.log(grid)/Math.log(10);
	double exp = Math.floor(log10);
	double mantissa = Math.pow(10,log10 - exp);
	int scale;

	// find nearest 1,2,5 setting that is larger than grid
	// avoiding noise introduced by exp/log
	if (mantissa < 1.000001) scale = 1;
	else if (mantissa < 2.000001) scale = 2;
	else if (mantissa < 5.000001) scale = 5;
	else { scale = 1; exp += 1; }

	// now adjust setting by zoom factor
	while (zoom != 0) {
	    if (zoom < 0) {
		if (scale == 1) { scale = 5; exp -= 1; }
		else if (scale == 2) scale = 1;
		else scale = 2;
		zoom += 1;
	    } else {
		if (scale == 1) scale = 2;
		else if (scale == 2) scale = 5;
		else { scale = 1; exp += 1; }
		zoom -= 1;
	    } 
	}

	// finally return the result
	return ((double)scale)*Math.pow(10,exp);
    }
  
    void PlotAnalogData(Graphics g,ArrayList coords,double sx,double sy,int xoff,int yoff,int width) {
	boolean first = true;
	int lastx=0, lasty=0;
	int ncoords = coords.size();

	for (int i = 0; i < ncoords; i += 1) {
	    AnalogPlotCoordinate c = (AnalogPlotCoordinate)coords.get(i);
	    int x = (int)((c.x - xorigin)*sx);
	    int y = (int)((maxy - c.y - yorigin)*sy);
	    if (!first) {
		if (c.x > xorigin)
		    g.drawLine(xoff+lastx,yoff+lasty,xoff+x,yoff+y);
	    } else first = false;
	    lastx = x;
	    lasty = y;
	    if (lastx + xoff > width) break;
	}
    }

    void PlotDigitalData(Graphics g,ArrayList coords,double sx,int xoff,int yoff,int width,int height,int bsize,Object type) {
	FontMetrics fm = g.getFontMetrics(tfont);
	boolean xvalue = true;
	int lastx = xoff, lasty = 0;
	int ncoords = coords.size();

	// useful Y coords
	int y0 = yoff + height;
	int y1 = yoff;
	int yz = (y0 + y1) >> 1;
	int ytext = yz + (fm.getAscent() >> 1);

	// sort out type
	ArrayList vtype = null;
	String stype = null;
	if (type != null && type instanceof ArrayList)
	    vtype = (ArrayList)type;
	else if (type != null && type instanceof String)
	    stype = (String)type;

	g.setFont(tfont);
	for (int i = 0; i < ncoords; i += 1) {
	    DigitalPlotCoordinate c = (DigitalPlotCoordinate)coords.get(i);
	    int y = 0;
	    boolean xv = false;
	    if (c.v2 != 0) {
		if (c.v1 == c.vmask) y = yz;	// Z
		else xv = true;			// X
	    } else if (c.v1 == 0) y = y0;	// 0
	    else y = y1;			// 1

	    if (c.x > xorigin) {
		// call to Math.min keeps x from getting really large when we've
		// zoomed way in on a plot.  Since we're not drawing sloped
		// lines, truncating x doesn't hurt us...
		int x = (int)Math.min(width+1,(c.x - xorigin)*sx) + xoff;
		if (xv) g.fillRect(lastx,y1,x-lastx+1,y0-y1+1);
		else {
		    if (bsize > 1 || type != null) {
			g.drawLine(lastx,y0,lastx,y1);
			if (y == yz) g.drawLine(lastx,yz,x,yz);
			else {
			    g.drawLine(lastx,y0,x,y0);
			    g.drawLine(lastx,y1,x,y1);
			    String representation;

			    if (vtype != null) {
				int index = (int)c.v1;
				if (index < 0 || index >= vtype.size())
				    representation = "0x" + c.toHexString();
				else
				    representation = (String)vtype.get(index);
			    } else if (stype != null) {
				if (stype.equalsIgnoreCase("o")) {

				    representation = "0" + c.toOctalString();
				} else if (stype.equalsIgnoreCase("b")) {
				    representation = "0b" + c.toBinaryString();
				} else if (stype.equalsIgnoreCase("d")) {
				    representation = Long.toString(c.v1);
				} else if (stype.equalsIgnoreCase("sd")) {
				    long num = c.v1;
				    long msb = 1L << (bsize-1);
				    if ((num & msb) != 0) num -= msb + msb;
				    representation = Long.toString(num);
				} else {
				    representation = "0x" + c.toHexString();
				}
			    } else
				representation = "0x" + c.toHexString();


			    // show representation centered in rectangular
			    // region but only if it fits
			    int w = fm.stringWidth(representation);
			    int lx = (lastx < xoff) ? xoff : lastx;
			    int dx = x - lx;
			    if (w < dx)
				g.drawString(representation,lx+((dx - w)>>1),ytext);
			}
		    } else {
			if (!xvalue) g.drawLine(lastx,lasty,lastx,y);
			g.drawLine(lastx,y,x,y);
		    }
		}
		lastx = x;
		if (lastx > width + xoff) break;
	    }
	    lasty = y;
	    xvalue = xv;
	}
    }

    // draw waveforms onto off-screen image
    synchronized public void Plot() {
	needsPlot = true;
	// don't bother plotting if we don't have a size yet
	if (!EnsureBuffer(false)) return;

	Graphics g = buffer.getGraphics();
	Dimension d = getSize();
	xoff = parent.xoff;
	yoff = parent.yoff;

	// center waveform vertically in the window, but make minimum y
	// value align with a grid line
	double range = maxy - miny;
	if (range == 0) range = 1;
	vdiv = ChooseScale(range/parent.nygrids,0);
	int gridMargin = (int)Math.floor((vdiv*parent.nygrids - range)/(2*vdiv));
	yorigin = -(gridMargin * vdiv);

	double gridRange = (parent.nygrids - 2*gridMargin)*vdiv;
	double delta = gridRange - range;
	if (delta < vdiv) yorigin -= delta;
	else yorigin += vdiv - delta;

	// fill in background and grid
	g.setColor(UI.PBGColor);
	g.fillRect(0,0,d.width,d.height);
  	g.setColor(UI.PGridColor);
	for (int x = xoff; x < d.width; x += Grid())
	    g.drawLine(x,0,x,d.height);
	for (int y = yoff; y < d.height; y += Grid())
	    g.drawLine(0,y,d.width,y);

	if (!v.isEmpty()) {
	    // scale plot to fit in available space
	    xorigin = parent.xorigin;
	    double hdiv = parent.hdiv;

	    scalex = Grid()/hdiv;
	    scaley = Grid()/vdiv;

	    // now plot each curve
	    int nvars = v.size();
	    for (int i = 0; i < nvars; i += 1) {
		PlotData data = (PlotData)v.get(i);
		Object type = vtype.get(i);
		g.setColor(Colors[i % Colors.length]);

		ArrayList coords = data.coords;
		if (coords.isEmpty()) continue;
		if (coords.get(0) instanceof AnalogPlotCoordinate)
		    PlotAnalogData(g,coords,scalex,scaley,xoff,yoff,d.width);
		else if (coords.get(0) instanceof DigitalPlotCoordinate)
		    PlotDigitalData(g,coords,scalex,xoff,yoff,
				    d.width,parent.nygrids*Grid(),data.width,type);
	    }

	    // output scale info
	    g.setFont(tfont);
	    g.setColor(UI.PScaleColor);
	    if (Double.isNaN(vdiv))
		g.drawString("H: "+UI.EngineeringNotation(hdiv,0)+haxis+"/div",3,d.height-3);
	    else
		g.drawString("V: "+UI.EngineeringNotation(vdiv,0)+vaxis+"/div, H: "+
			     UI.EngineeringNotation(hdiv,0)+haxis+"/div",3,d.height-3);
	}
	g.dispose();
	needsPlot = false;
	if (cursor[0].width != 0) TrackPlotCursor(0,mouseX,mouseY);
	repaint();
    }

    public void RemovePlotCursor(int n) {
	if (!cursor[n].isEmpty()) {
	    int w = cursor[n].width;
	    cursor[n].width = 0;
	    repaint(cursor[n].x,cursor[n].y,w,cursor[n].height);
	    location[n] = "";
	    repaint(locnBBox[n].x,locnBBox[n].y,locnBBox[n].width,locnBBox[n].height);
	}
    }

    public void ShowCrossCursor(int n,int x,int y,String locn) {
	location[n] = locn;
	repaint(locnBBox[n].x,locnBBox[n].y,locnBBox[n].width,locnBBox[n].height);

	cursor[n].x = x - csize;
	cursor[n].width = 2*csize;
	cursor[n].y = y - csize;
	cursor[n].height = 2*csize;
	repaint(cursor[n].x,cursor[n].y,cursor[n].width,cursor[n].height);

	parent.ShowLineCursor(n,x,this);
    }

    public void ShowLineCursor(int n,int x,String locn) {
	location[n] = locn;
	repaint(locnBBox[n].x,locnBBox[n].y,locnBBox[n].width,locnBBox[n].height);

	ShowLineCursor(n,x);
	parent.ShowLineCursor(n,x,this);
    }

    public void ShowLineCursor(int n,int x) {
	cursor[n].x = x;
	cursor[n].width = 1;
	cursor[n].y = 0;
	cursor[n].height = getSize().height;
	repaint(cursor[n].x,cursor[n].y,cursor[n].width,cursor[n].height);
    }

    public double ScreenToX(int x) {
	return ((double)(x - xoff))/scalex + xorigin;
    }

    public double ScreenToY(int y) {
	return maxy - ((double)(y - yoff))/scaley - yorigin;
    }

    public void TrackPlotCursor(int n,int screenx,int screeny) {
	if (v.isEmpty()) return;

	double cx = ScreenToX(screenx);
	double cy = ScreenToY(screeny);
	double nearestY = Double.POSITIVE_INFINITY;
	int nindex = 0;

	parent.RemovePlotCursor(n);

	// for each curve find its Y value at X = cx.  Remember the Y that's
	// closest to the mouse's Y position, but not below it
	if (cx >= parent.xmin) {
	    int nvars = v.size();
	    for (int i = 0; i < nvars; i += 1) {
		ArrayList coords = ((PlotData)v.get(i)).coords;
		int csize = coords.size();
		if (csize == 0) continue;

		PlotCoordinate c;
		double yy;		// value of this curve at X=cx
		int min = 0;
		int max = csize - 1;
		int index;

		// do a binary search for coordinate with greatest X < cx
		double x;
		while (true) {
		    index = (min+max)/2;
		    c = (PlotCoordinate)coords.get(index);
		    x = c.GetX();
		    if (x == cx) break;
		    else if (min == max) {
			if (x > cx) {
			    index -= 1;
			    if (index < 0) index = 0;
			    c = (PlotCoordinate)coords.get(index);
			    x = c.GetX();
			}
			break;
		    } else if (x < cx) min = (min == index) ? max :index;
		    else max = (max == index) ? min : index;
		}

		// now compute Y that corresponds to cx
		if (x == cx) yy = c.GetY();
		else if (index == csize - 1) continue;
		else {
		    PlotCoordinate cnext = (PlotCoordinate)coords.get(index+1);
		    double y = c.GetY();
		    yy = y + (cnext.GetY() - y)*(cx - x)/(cnext.GetX() - x);
		}

		// if the coord we computed is above the mouse and nearer than
		// whatever else we've found, remember it
		if (yy >= cy && yy < nearestY) {
		    nearestY = yy;
		    nindex = i;
		}
	    }
	}

	PlotData data = (PlotData)v.get(nindex);
	// make sure we display enough digits to show a different value for each
	// possible mouse position.  Always show at least 3 digits past decimal point.
	int hdigits = (int)Math.max(3,Math.ceil(Math.log(Math.abs(cx)/(parent.hdiv/Grid()))/Math.log(10)));
	if (nearestY < Double.POSITIVE_INFINITY) {
	    int vdigits = (int)Math.max(3,Math.ceil(Math.log(Math.abs(nearestY)/(vdiv/Grid()))/Math.log(10)));
	    String locn = data.name+": "+
		UI.EngineeringNotation(cx,hdigits)+data.haxis+", "+
		UI.EngineeringNotation(nearestY,vdigits)+data.vaxis;
	    // compute delta from previous cursor
	    if (n > 0 && !cursor[n-1].isEmpty()) {
		double dx = cx - curX[n-1];
		if (!Double.isNaN(curY[n-1])) {
		    double dy = nearestY - curY[n-1];
		    locn += " ["+UI.EngineeringNotation(dx,3)+data.haxis+","+
			UI.EngineeringNotation(dy,3)+data.vaxis+"]";
		} else {
		    locn += " ["+UI.EngineeringNotation(dx,3)+data.haxis+"]";
		}
	    }
	    curX[n] = cx;
	    curY[n] = nearestY;
	    ShowCrossCursor(n,screenx,((int)((maxy - nearestY - yorigin)*scaley)) + yoff,locn);
	} else {
	    String locn = UI.EngineeringNotation(cx,hdigits)+data.haxis;
	    // compute delta from previous cursor
	    if (n > 0 && !cursor[n-1].isEmpty()) {
		double dx = cx - curX[n-1];
		locn += " ["+UI.EngineeringNotation(dx,3)+data.haxis+"]";
	    }
	    curX[n] = cx;
	    curY[n] = Double.NaN;
	    ShowLineCursor(n,screenx,locn);
	}
    }

    public void mouseEntered(MouseEvent event) {
	requestFocus();
    }
    public void mouseExited(MouseEvent event) {
	for (int i = 0; i < ncursors; i += 1) parent.RemovePlotCursor(i);
    }
    public void mouseClicked(MouseEvent event) { }
    public void mousePressed(MouseEvent event) { }
    public void mouseReleased(MouseEvent event) {
	mouseMoved(event);
    }
    public void mouseMoved(MouseEvent event) {
	mouseX = event.getX();
	mouseY = event.getY();
	parent.RemovePlotCursor(1);
	TrackPlotCursor(0,mouseX,mouseY);
    }
    public void mouseDragged(MouseEvent event) {
	TrackPlotCursor(1,event.getX(),event.getY());
    }

    public void keyPressed(KeyEvent event) {
	int key = event.getKeyCode();
	double cx = ScreenToX(mouseX);
	if (key == KeyEvent.VK_UP) parent.Zoom(mouseX,cx,-1);
	else if (key == KeyEvent.VK_DOWN) parent.Zoom(mouseX,cx,1);
	else if (key == KeyEvent.VK_LEFT)
	    parent.incrementOrigin(-parent.nxgrids);
	else if (key == KeyEvent.VK_RIGHT)
	    parent.incrementOrigin(parent.nxgrids);
    }
    public void keyReleased(KeyEvent event) { }
    public void keyTyped(KeyEvent event) {
	char key = event.getKeyChar();
	double cx = ScreenToX(mouseX);
	if (key == 'X') parent.Zoom(mouseX,cx,-1);
	else if (key == 'x') parent.Zoom(mouseX,cx,1);
	else if (key == 'c') parent.Recenter(cx);
    }
  
    public void Print(Graphics g,int pgrid,int xorg,int yorg,int w,int h) {
	// setup font info
	Font f = Font.decode("SansSerif-plain-10");
	FontMetrics fm = g.getFontMetrics(f);
	int ascent = fm.getAscent();
	int letting = ascent + fm.getDescent();
	g.setFont(f);

	// make sure we don't over step our bounds
	g.setClip(xorg-1,yorg-1,w+1,h+1);

	// draw grid
	g.setColor(new Color(240,240,240)/*Color.lightGray*/);
	for (int x = xorg+xoff; x < xorg+w; x += pgrid)
	    g.drawLine(x,yorg,x,yorg+h);
	for (int y = yorg+yoff; y < yorg+h; y += pgrid)
	    g.drawLine(xorg,y,xorg+w,y);
      
	// scale plot to fit in available space
	double hdiv = parent.hdiv;
	double sx = pgrid/hdiv;
	double sy = pgrid/vdiv;

	if (!v.isEmpty()) {
	    // now plot each curve
	    int nvars = v.size();
	    for (int i = 0; i < nvars; i += 1) {
		PlotData data = (PlotData)v.get(i);
		Object type = vtype.get(i);
		g.setColor(i == 0 ? Color.black : Colors[(i-1)%Colors.length]);
     	
		g.drawString(data.name,xorg+xoff+2,yorg+ascent+i*letting);

		ArrayList coords = data.coords;
		if (coords.isEmpty()) continue;
		if (coords.get(0) instanceof AnalogPlotCoordinate)
		    PlotAnalogData(g,coords,sx,sy,xorg+xoff,yorg+yoff,w);
		else if (coords.get(0) instanceof DigitalPlotCoordinate)
		    PlotDigitalData(g,coords,sx,xorg+xoff,yorg+yoff,
				    w,parent.nygrids*pgrid,data.width,type);
	    }
	}
      
	// finally draw frame
	g.setColor(Color.black);
	int lx = xorg + 2;
	int ly = yorg + h - 2 - fm.getMaxDescent();
	if (Double.isNaN(vdiv))
	    g.drawString("H: "+UI.EngineeringNotation(hdiv,0)+haxis+"/div",lx,ly);
	else
	    g.drawString("V: "+UI.EngineeringNotation(vdiv,0)+vaxis+"/div, H: "+
			     UI.EngineeringNotation(hdiv,0)+haxis+"/div",lx,ly);
	g.drawRect(xorg-1,yorg-1,w,h);
    }
}

