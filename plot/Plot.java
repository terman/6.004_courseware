// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package plot;

import gui.GuiFrame;
import gui.UI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.PrintJob;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import netlist.Analysis;
import netlist.Netlist;
import netlist.PlotRequest;
import simulation.Network;

public class Plot extends JPanel implements AdjustmentListener, ActionListener, ComponentListener, Observer, DocumentListener {
    static final int MAXPANELS = 16;
    static final int SLIDERHEIGHT = 10;

    GuiFrame cparent;	// where controls reside
    GuiFrame gparent;	// where we are in graphics hierarchy
    Netlist netlist;
    HashMap acache;	// cache for analog plot data
    HashMap dcache;	// cache for digital plot data
    PlotCanvas panels[];// PlotPanels we're managing
    JTextArea names[];	// names of values we're plotting
    JScrollPane wrappers[];

    int npanels;	// number of panels to display
    JScrollBar hbar;	// horizontal scrolling for all plots
    JButton b1,b2,b3;

    int hzoom;		// current horizontal zoom factor
    double hdiv;	// size of each horizontal grid
    double xorigin;	// current x origin for plotting (for scrolling)
    double yorigin;	// current y origin for plotting
    int nxgrids,nygrids;
    boolean needsDoPlot;
    int xoff,yoff;
    double xmin,xmax;

    public Plot(GuiFrame cparent,GuiFrame gparent,Netlist netlist) {
	super();
	needsDoPlot = false;

	this.cparent = cparent;
	this.gparent = gparent;
	this.netlist = netlist;
	if (netlist != null) netlist.addObserver(this);
	acache = new HashMap();
	dcache = new HashMap();

	panels = new PlotCanvas[MAXPANELS];
	names =  new JTextArea[MAXPANELS];
	wrappers = new JScrollPane[MAXPANELS];
	Font font = new Font("Courier",Font.PLAIN,12);
	for (int i = 0; i < MAXPANELS; i += 1) {
	    panels[i] = new PlotCanvas(this);
	    add(panels[i]);
	    names[i] = new JTextArea(1,20);
	    wrappers[i] = new JScrollPane(names[i]);
	    add(wrappers[i]);
	    names[i].setFont(font);
	    names[i].getDocument().addDocumentListener(this);
	}

	hzoom = 0;		// no zoom to start with
	xorigin = Double.NEGATIVE_INFINITY;
	addComponentListener(this);
    
	// here are the horizontal zoom/pan controls
	hbar = new JScrollBar(JScrollBar.HORIZONTAL);
	add(hbar);
	hbar.addAdjustmentListener(this);
	hbar.setBackground(UI.BGCOLOR);
	b1 = cparent.ImageButton("/icons/zoomin.gif");
	b1.setToolTipText("Zoom in");
	b1.setPreferredSize(new Dimension(20,20));
	b1.setActionCommand("+");
	add(b1);
	b1.addActionListener(this);
	b2 = cparent.ImageButton("/icons/zoomout.gif");
	b2.setToolTipText("Zoom out");
	b2.setPreferredSize(new Dimension(20,20));
	b2.setActionCommand("-");
	add(b2);
	b2.addActionListener(this);
	b3 = cparent.ImageButton("/icons/zoom.gif");
	b3.setToolTipText("Surround");
	b3.setPreferredSize(new Dimension(20,20));
	b3.setActionCommand("@");
	add(b3);
	b3.addActionListener(this);

	SetPanels(1);
    }

    public void SetPanels(int npanels) {
	if (this.npanels != npanels) {
	    this.npanels = npanels;
	    invalidate();
	    validate();
	    DoPlot();
	}
	cparent.SetChannels(npanels);
    }

    public void doLayout() {
	Dimension d = getSize();
	Dimension ndim = names[0].getPreferredSize();
	Dimension bdim = b1.getPreferredSize();

	// locate controls along bottom
	int x = ndim.width + 3;
	int y = d.height - bdim.height;
	b1.setBounds(x,y,bdim.width,bdim.height);
	x += bdim.width;
	b2.setBounds(x,y,bdim.width,bdim.height);
	x += bdim.width;
	b3.setBounds(x,y,bdim.width,bdim.height);
	x += bdim.width;
	hbar.setBounds(x,y,d.width - x,bdim.height);

	// locate panels in remaining space
	int margin = 2;
	int pheight = (y / npanels) - margin - margin;
	int pextra = y % npanels;
	x = ndim.width + 3;
	y = 0;
	for (int i = 0; i < MAXPANELS; i += 1) {
	    if (i < npanels) {
		wrappers[i].setVisible(true);
		wrappers[i].setBounds(0,y+margin,x,pheight);
		panels[i].setVisible(true);
		panels[i].setBounds(x,y+margin,d.width - x,pheight);
		y += pheight + margin + margin;
		if (pextra-- > 0) y += 1;
	    } else {
		wrappers[i].setVisible(false);
		panels[i].setVisible(false);
	    }
	}
    }

    public void NamesChanged(int index) {
	panels[index].ClearPlotData();
	gparent.ClearMessage(null);
	Network network = netlist.currentNetwork;
	String n = names[index].getText().toLowerCase();
	int len = n.length();
	int last = 0;
	while (last < len) {
	    int end = n.indexOf('\n',last);
	    if (end == -1) end = len;
	    if (last != end) {
		String name = n.substring(last,end);
		String type = null;
		ArrayList dvector;

		int paren = name.indexOf('(');
		if (paren == -1 || !name.endsWith(")") || name.startsWith("i(")) {
		    // analog data
		    dvector = (ArrayList)acache.get(name);
		    if (dvector == null) {
			dvector = network.RetrieveAnalogPlotData(name);
			if (dvector != null && !dvector.isEmpty())
			    acache.put(name,dvector);
		    }
		} else {
		    // digital data
		    type = name.substring(0,paren);
		    name = name.substring(paren+1,name.length()-1);
		    dvector = (ArrayList)dcache.get(name);
		    if (dvector == null) {
			dvector = network.RetrieveDigitalPlotData(name);
			if (dvector != null && !dvector.isEmpty())
			    dcache.put(name,dvector);
		    }
		}

		if (dvector == null) {
		    gparent.Message("Can't get simulation data for "+network.Problem());
		} else {
		    Object def = (type == null) ? null : netlist.plotdefs.get(type);
		    panels[index].AddPlotData(dvector,def == null ? type : def);
		}
	    }
	    last = end + 1;
	}

	// recompute range
	xmin = Double.POSITIVE_INFINITY;
	xmax = Double.NEGATIVE_INFINITY;
	for (int i = 0; i < npanels; i += 1) {
	    if (panels[i].minx < xmin) xmin = panels[i].minx;
	    if (panels[i].maxx > xmax) xmax = panels[i].maxx;
	}
	if (Double.isInfinite(xmin) || Double.isInfinite(xmax)) {
	    xmin = 0;
	    xmax = network.GetTime();
	}
	if (xmin == xmax) {
	    xmin = 0;
	    if (xmin == xmax) xmax = 1;
	}
    }

    public void changedUpdate(DocumentEvent e) {
	textValueChanged(e);
    }
    public void insertUpdate(DocumentEvent e) {
	textValueChanged(e);
    }
    public void removeUpdate(DocumentEvent e) {
	textValueChanged(e);
    }

    public void textValueChanged(DocumentEvent e) {
	setBusy(true);

	Document src = e.getDocument();
	for (int i = 0; i < npanels; i += 1)
	    if (src == names[i].getDocument()) {
		NamesChanged(i);
		break;
	    }
	DoPlot();

	setBusy(false);
    }

    // called when network has been resimulated
    public void update(Observable observed,Object arg) {
	if (arg == netlist) {
	    setBusy(true);

	    acache.clear();
	    dcache.clear();
	    Analysis a = (Analysis)netlist.analyses.get(0);
	    int nplots = a.plots.size();

	    if (nplots > 8) SetPanels(16);
	    else if (nplots > 4) SetPanels(8);
	    else if (nplots > 2) SetPanels(4);
	    else if (nplots > 1) SetPanels(2);
	    else SetPanels(1);

	    StringBuffer vars = new StringBuffer();
	    for (int i = 0; i < MAXPANELS; i += 1) {
		String n = "";
		if (i < nplots) {
		    ArrayList requests = (ArrayList)a.plots.get(i);
		    vars.setLength(0);
		    
		    Iterator iter = requests.iterator();
		    while (iter.hasNext()) {
			PlotRequest pr = (PlotRequest)iter.next();
			if (vars.length() != 0) vars.append('\n');
			if (pr.Property().equalsIgnoreCase("v"))
			    vars.append(pr.Element());
			else {
			    vars.append(pr.Property());
			    vars.append('(');
			    vars.append(pr.Element());
			    vars.append(')');
			}
		    }
		    n = vars.toString();
		}
		names[i].setText(n);
		NamesChanged(i);
	    }
	    hzoom = 0;
	    xorigin = Double.NEGATIVE_INFINITY;
	    DoPlot();
	    cparent.SetPlot(this);

	    setBusy(false);
	} else {
	    for (int i = 0; i < npanels; i += 1)
		panels[i].ClearPlotData();
	    acache.clear();
	    dcache.clear();
	    DoPlot();
	    gparent.ClearMessage(null);
	}
    }

    public void setBusy(boolean which) {
	if (which) {
	    Cursor c = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
	    setCursor(c);
	    for (int i = 0; i < npanels; i += 1) names[i].setCursor(c);
	} else {
	    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	    Cursor c = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
	    for (int i = 0; i < npanels; i += 1) names[i].setCursor(c);
	}
    }

    public void DoPlot() {
	needsDoPlot = true;

	// compute grid dimensions for all the panels
	Dimension d = panels[0].getSize();
	if (d.width <= 0 || d.height <= 0) return;
	int grid = panels[0].Grid();
	nxgrids = d.width/grid;
	if (nxgrids < 1) nxgrids = 1;
	xoff = (d.width - nxgrids*grid)/2;

	nygrids = d.height/PlotCanvas.grid;
	// keep waveform from getting crowded vertically
	if (d.height - (nygrids * grid) < grid)
	    nygrids -= 1;
	if (nygrids < 1) nygrids = 1;
	yoff = (d.height - nygrids*grid)/2;

	// compute size of each horizontal division
	double range = xmax - xmin;
	hdiv = PlotCanvas.ChooseScale(range/nxgrids,hzoom);

	// see if we should set xorigin
	if (xorigin == Double.NEGATIVE_INFINITY)
	    xorigin = Quantize(xmin,hdiv);

	// set up scrollbar
	int max = 10000;
	double sbunit = ((double)max)/range;
	int val = (int)(sbunit*(xorigin - xmin));
	int linc = (int)(sbunit*hdiv);
	int vis = (int)(sbunit*hdiv*nxgrids);
	hbar.removeAdjustmentListener(this);
	hbar.setValues(val,vis,0,max);
	hbar.setUnitIncrement(linc < 1 ? 1 : linc);
	hbar.setBlockIncrement(vis < 1 ? 1 : vis);
	hbar.addAdjustmentListener(this);

	for (int i = 0; i < npanels; i += 1) panels[i].Plot();
	needsDoPlot = false;
    }

    public double Quantize(double x,double quanta) {
	return Math.floor(x/quanta)*quanta;
    }

    public void Zoom(int x,double cx,int dhzoom) {
	hzoom += dhzoom;
	double nhdiv = PlotCanvas.ChooseScale((xmax - xmin)/nxgrids,hzoom);
	double start = Quantize(cx - x*nhdiv/panels[0].Grid(),nhdiv);
	xorigin = Math.max(Quantize(xmin,nhdiv),start);
	DoPlot();
    }

    public void Recenter(double cx) {
	double nhdiv = PlotCanvas.ChooseScale((xmax - xmin)/nxgrids,hzoom);
	double start = Quantize(cx - nhdiv*nxgrids*0.5,nhdiv);
	xorigin = Math.max(Quantize(xmin,nhdiv),start);
	DoPlot();
    }

    public void incrementOrigin(int inc) {
	adjustOrigin(xorigin + inc*hdiv);
    }

    public void adjustOrigin(double norigin) {
	xorigin = norigin;
	if (xorigin < xmin) xorigin = Quantize(xmin,hdiv);
	else if (xorigin > xmax) xorigin = Quantize(xmax,hdiv);
	DoPlot();
    }

    // for handling horizontal scrollbar events
    public void adjustmentValueChanged(AdjustmentEvent event) {
	int v = event.getValue();
	double range = xmax - xmin;
	double norigin = range*((double)v)/((double)hbar.getMaximum()) + xmin;
	norigin = Quantize(norigin,hdiv);
	if (Math.abs(norigin - xorigin) < hdiv/2) norigin += hdiv;
	adjustOrigin(norigin);
    }
  
    public void componentHidden(ComponentEvent e) { }
    public void componentMoved(ComponentEvent e) { }
    public void componentShown(ComponentEvent e) { }
    public void componentResized(ComponentEvent e) {
	DoPlot();
    }
	
    public void actionPerformed(ActionEvent event) {
	String a = event.getActionCommand();

	if (a.equals(UI.PRINT)) DoPrint();
	else if (a.equals(UI.CHAN1)) SetPanels(1);
	else if (a.equals(UI.CHAN2)) SetPanels(2);
	else if (a.equals(UI.CHAN4)) SetPanels(4);
	else if (a.equals(UI.CHAN8)) SetPanels(8);
	else if (a.equals(UI.CHAN16)) SetPanels(16);
	else if (a.equals("+")) {
	    hzoom -= 1;
	    DoPlot();
	}
	else if (a.equals("-")) {
	    hzoom += 1;
	    DoPlot();
	}
	else if (a.equals("@")) {
	    hzoom = 0;
	    xorigin = xmin;
	    DoPlot();
	}
    }
  
    public void RemovePlotCursor(int n) {
	for (int i = 0; i < npanels; i += 1)
	    panels[i].RemovePlotCursor(n);
    }

    public void ShowLineCursor(int n,int x,PlotCanvas source) {
	for (int i = 0; i < npanels; i += 1)
	    if (panels[i] != source)
		panels[i].ShowLineCursor(n,x);
    }

    public void DoPrint() {
	Dimension d = new Dimension();
	Dimension offset = new Dimension();
	PrintJob pj = gparent.StartPrinting(d,offset);
	if (pj != null) {
	    OutputPage(pj,d,offset);
	    pj.end();
	}
    }
	
    public void OutputPage(PrintJob pj,Dimension d,Dimension offset) {
	Graphics g = pj.getGraphics();
	Font f = Font.decode("SansSerif-plain-10");
	FontMetrics fm = g.getFontMetrics(f);
	int letting = fm.getAscent() + fm.getDescent();

	String src = netlist.Source();
	String user = System.getProperty("user.name","???");
	gparent.PrintBanner(g,d,offset,src+" for "+user);
	int margin = (npanels == 1) ? 0 : 5;
	int pheight = (d.height - letting)/npanels;
			
	// see how many pixels per grid
	int xgrid = (d.width - 2 - 2*xoff)/nxgrids;
	int ygrid = (pheight - 2 - margin - 2*yoff)/nygrids;
	int pgrid = Math.min(xgrid,ygrid);
	int w = nxgrids*pgrid + 2*xoff;
	int h = nygrids*pgrid + 2*yoff;
	int xorg = (d.width - w)/2 + offset.width;
	int yorg = (pheight - h)/2 + offset.height + letting;
			
	g.setFont(f);
	g.setColor(Color.black);
	int hdigits = (int)Math.max(3,Math.ceil(Math.log(Math.abs(xorigin)/(hdiv/PlotCanvas.grid))/Math.log(10)));
	g.drawString(UI.EngineeringNotation(xorigin,hdigits)+"s",xorg,yorg-fm.getDescent());

	for (int i = 0; i < npanels; i += 1)
	    panels[i].Print(g,pgrid,xorg,i*pheight + yorg,w,h);
			
	// send page to printer
	g.dispose();
    }

    public void update(Graphics g) {
	paint(g);
    }

    public void paint(Graphics g) {
	if (needsDoPlot) DoPlot();

	// if we're still in need of a replot, try again later
	if (needsDoPlot) repaint();

	super.paint(g);
    }
}
