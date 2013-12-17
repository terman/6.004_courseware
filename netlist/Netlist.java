// Copyright (C) 1998-2007 Christopher J. Terman - All Rights Reserved.

package netlist;

import gui.EditBuffer;
import gui.GuiFrame;
import gui.UI;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import javax.swing.SwingUtilities;
import plot.PlotData;
import plot.DigitalPlotCoordinate;
import simulation.EmuNetwork;
import simulation.Network;
import simulation.SimNetwork;
import simulation.FlattenNetwork;
import simulation.SimMemory;
import simulation.SpiceNetwork;

public class Netlist extends EditBuffer implements ActionListener, Runnable {
    private StringBuffer line;		// current input line while parsing
    private int lineStart;		// offset into netlist at which line started
    private int lineLength;		// line.length()
    private int lineOffset;		// current offset into line
    boolean nestingAllowed;		// true if nested definitions allowed
    boolean localNames;			// add aliases for .subckt terminal names

    public HashMap options;		// options as specified .OPTIONS
    public HashMap plotdefs;		// collected from .PLOTDEF
    public HashMap globalNodes;		// hashtable for globally declared nodes
    public Subcircuit topLevel;		// top level circuitry
    public Subcircuit currentSubcircuit;// subcircuit currently being defined
    public ArrayList analyses;		// what analyses to do
    public Node gnd;			// global ground node
    public String prefix;		// pathname prefix used during netlisting

    public Network currentNetwork;	// network to simulate
    public Netlist currentNetlist;	// what netlist we're reading from

    boolean busy;			// if we're netlisting this buffer
    Thread simulation;			// do the hard work in the background
    Netlist target;
    Runnable ShowSimulationResults;

    public String checkoffCommand;	// type of checkoff
    public String checkoffServer;	// where to submit results
    public String assignment;		// name of this assignment
    public int checkoffChecksum;	// checkoff checksum
    public ArrayList verifications;		// values to verify
    ArrayList generateCheckoffs;	// values to print out
    ArrayList mverifications;		// memory contents to verify
    ArrayList writedata;		// values to write out to a file

    public int error_start;             // save error info
    public int error_end;
    public String error_message;

    public Netlist(GuiFrame parent,File source) {
	super(parent,source);

	// run this in event dispatch thread after simulation completes
	// in the background.  Swing doesn't like you to update visible
	// components except in the event dispatch thread.
	target = this;
	ShowSimulationResults = new Runnable() {
		public void run() {
		    observers.notifyObservers(target);
		    Message("");
		    String size = currentNetwork.Size();
		    if (size != null) {
                        String msg = "circuit size = "+size;
                        if (options.get("benmark") != null)
                            msg = msg + " benmark = "+UI.EngineeringNotation(currentNetwork.Benmark(),2);
                        Message(msg);
                    }
		}
	    };

	busy = false;
	simulation = null;
	nestingAllowed = parent.GetParameter("nesting") != null;
	localNames = parent.GetParameter("no-local-names") == null;

	// set up virgin network
	line = new StringBuffer();
    }

    // flatten our network into individual nodes and devices
    public boolean Netlist(NetlistConsumer nc) {
	prefix = "";		// top level has null pathname prefix
    
	// get netlister nodes for all the global nodes
	Iterator iter = globalNodes.values().iterator();
	while (iter.hasNext()) {
	    Node n = (Node)iter.next();
	    if (n == gnd) n.netlisterNode = nc.MakeGndNode(n.id.name);
	    else n.netlisterNode = nc.FindNode(n.id.name,true);
	}

	// netlist top level "sub"circuit
	boolean result = topLevel.Netlist(this,nc);
	return result;
    }

    public String MinObservedSetup() {
	if (currentNetwork == null) return null;
	double minSetup = currentNetwork.MinObservedSetup();
	if (!Double.isInfinite(minSetup)) return Double.toString(minSetup);
	else return null;
    }

    public double NetworkTime() {
	return currentNetwork == null ? 0 : currentNetwork.NetworkTime();
    }

    public double NetworkSize() {
	return currentNetwork == null ? 0 : currentNetwork.NetworkSize();
    }

    public boolean hasCheckoffServer() {
	return (checkoffServer != null) && checkoffServer.length() > 0;
    }

    public String WriteDataRequest(Network network,String filename,ArrayList params,String nodes) {
	System.out.println("WriteDataRequest("+filename+","+nodes+")\n");

	// get actual values from network
	ArrayList dvector = network.RetrieveDigitalPlotData(nodes);
	if (dvector == null) {
	    return "<font size=5>Internal error...</font><p>can't get simulation data for "+nodes;
	}
	if (dvector.size() != 1) {
	    return "<font size=5>Internal error...</font><p>expected one-element vector, got "+dvector.size();
	}
	PlotData d = (PlotData)dvector.get(0);

	// process time parameters: step and optional start, stop
	double step,time,stop;
	step = ((Number)params.get(0)).value;
	if (params.size() > 1) {
	    time = ((Number)params.get(1)).value;
	    if (params.size() > 2) stop = ((Number)params.get(2)).value;
	    else stop = Double.POSITIVE_INFINITY;
	} else {
	    time = 0.0;
	    stop = Double.POSITIVE_INFINITY;
	}

	// write out the values
	try {
	    PrintWriter out = new PrintWriter(new FileOutputStream(filename));
	    while (time < stop) {
		DigitalPlotCoordinate c = (DigitalPlotCoordinate)d.FindCoordinate(time);
		if (c != null) {
		    out.print(c.toBinaryString());
		    out.print('\n');
		    time += step;
		} else break;
	    }
	    out.close();
	}
	catch (Exception e) {
	    return "<font size=5>Internal error...</font><p>Exception while writing file: "+e;
	}
	return null;
    }

    public String Checkoff() {
	if (currentNetwork == null) {
	    return ("<font size=5>Oops...</font><p>Can't find any simulation results to verify... did you run the simulation?");
	}

	if (generateCheckoffs != null && generateCheckoffs.size() != 0) {
	    try {
		PrintWriter out = new PrintWriter(new FileOutputStream("checkoff_data"));
		int ncheckoffs = generateCheckoffs.size();
		for (int i = 0; i < ncheckoffs; i += 1) {
		    VerifyData v = (VerifyData)generateCheckoffs.get(i);
		    v.GenerateCheckoff(currentNetwork,out);
		}
		out.close();
	    }
	    catch (Exception e) {
	    }
	    return "Node data written to file checkoff_data";
	}

	if (checkoffServer == null) {
	    return ("<font size=5>Oops...</font><p>Can't find checkoff information... did you include the appropriate \"labXcheckoff.jsim\" file which supplies the information needed to complete the checkoff?");
	}

	//return("checkoffServer: "+checkoffServer+"<br>assignment: "+assignment+"<br>checksum: "+checkoffChecksum);

	// handle .writedata requests
	int nwritedata = writedata.size();
	for (int i = 0; i < nwritedata; i += 1) {
	    ArrayList request = (ArrayList)writedata.get(i);
	    String filename = (String)request.get(0);	// first entry is filename
	    ArrayList params = (ArrayList)request.get(1);  // second entry is timing parameters
	    String nodes = (String)request.get(2);	// third entry is node list
	    String error = WriteDataRequest(currentNetwork,filename,params,nodes);
	    if (error != null) return error;
	}

	// compare expected and actual values
	int vChecksum = 2536038;
	int nverifications = verifications.size();
	for (int i = 0; i < nverifications; i += 1) {
	    VerifyData v = (VerifyData)verifications.get(i);
	    String error = v.Verify(currentNetwork);
	    if (error != null) return error;
	    vChecksum += v.Checksum();
	}

	// check memory contents
	nverifications = mverifications.size();
	for (int i = 0; i < nverifications; i += 1) {
	    ArrayList data = (ArrayList)mverifications.get(i);
	    String deviceName = ((Identifier)data.get(0)).name;
	    int addr = (int)(((Number)data.get(1)).value);
	    int nvalues = data.size();
	    Object d = currentNetwork.FindDevice(deviceName);
	    if (d == null || !(d instanceof SimMemory)) {
		return ("<font size=5>Oops...</font><p>Can't find memory device specified by .mverify: "+deviceName);
	    }
	    SimMemory m = (SimMemory)d;
	    int width = m.width;
	    vChecksum += deviceName.hashCode();

	    for (int j = 2; j < nvalues; j += 1) {
		long v = (long)(((Number)data.get(j)).value);
		vChecksum += j*(addr + v);
		// see if location m[addr] == v, if not complain
		for (int bit = 0; bit < width; bit += 1) {
		    int actual = m.ReadBit(addr,bit);
		    if (actual != ((v >> bit) & 1)) {	// mismatch
			StringBuffer estring = new StringBuffer();
			StringBuffer astring = new StringBuffer();
			for (int k = width-1; k >= 0; k -= 1) {
			    int a = m.ReadBit(addr,k);
			    int e = (int)((v >> k) & 1);
			    estring.append(e == 0 ? '0' : '1');
			    if (a != e) astring.append("<font color=\"red\">");
			    astring.append(a == 0 ? '0' : (a == 1 ? '1' : 'X'));
			    if (a != e) astring.append("</font>");
			}
			return
			    "<font size=5>Memory contents verification error...</font><tt><ul>"
			    +"<li>memory:&nbsp;&nbsp;&nbsp;"+deviceName
			    +"<li>location:&nbsp;0x"+Integer.toHexString(addr)
			    +"<li>expected:&nbsp;0b"+estring.toString()
			    +"<li>actual:&nbsp;&nbsp;&nbsp;0b"+astring.toString()
			    +"</tt></ul>";
		    }
		}
		addr += 1;
	    }
	}

	// if no server specified, assume that we're just doing
	// a unofficial verification (eg, for debugging, but not
	// to be reported to the server).
	if (!hasCheckoffServer()) return null;

	// magic to find out correct checksum
	if (options.get("plugh2536038") != null) {
	    System.out.println("vChecksum="+vChecksum);
	    return null;
	}

	if (checkoffChecksum != 0 && vChecksum != checkoffChecksum) {
	    return ("<font size=5>Verification error...</font><p>It appears that the checkoff information has been modified in some way.  Please verify that you are using the official checkoff file; contact 6004-labs@lists.csail.mit.edu if you can't resolve the problem.<p>"+vChecksum);
	}

	return null;	// checkoff okay to proceed
    }

    public void actionPerformed(ActionEvent event) {
	String a = event.getActionCommand();

	if (a.equals(UI.STOP)) {
	    synchronized (this) {
		if (simulation != null) simulation.interrupt();
	    }
	} else if (a.equals(UI.SIMULATE)) DoSimulate();
	else if (a.equals(UI.FASTSIMULATE)) DoFastSimulate();
	else if (a.equals(UI.GATESIMULATE)) DoGateSimulate();
	else if (a.equals(UI.TIMINGANALYSIS)) DoTimingAnalysis();
	else if (a.equals(UI.FLATTEN)) DoFlatten();
    }

    // run a transient analysis
    public void DoTransientAnalysis(Network s,Analysis a) {
	double maxTimestep = 2e-10;
	double endTime = 1e-8;
	for (int i = 0; i < a.params.size(); i += 1) {
	    Object o = a.params.get(i);
	    if (!(o instanceof Number)) {
		Identifier id = (Identifier)o;
		Error(id,"Expected a number here");
		return;
	    }
	    if (i == 0) {
		endTime = ((Number)o).value;
		maxTimestep = endTime/50;
	    } else {
		maxTimestep = endTime;
		endTime = ((Number)o).value;
	    }
	}
	String msg = "Performing a "+UI.EngineeringNotation(endTime,3)+"s transient analysis using "+s.SimulationType();
	Message(msg);
	boolean okay = s.TransientAnalysis(endTime,maxTimestep,parent);
	if (!okay) Error(s.Problem());
	else Message("");
    }

    // run a DC analysis
    public void DoDCAnalysis(Network s,Analysis a) {
	String d1 = null,d2 = null;
	double start1 = 0,stop1 = 0,step1 = 1;
	double start2 = 0,stop2 = 0,step2 = 1;

	// process analysis parameters
	for (int i = 0; i < a.params.size(); i += 1) {
	    Object o = a.params.get(i);
	    if (i==0 || i==4) {
		if (!(o instanceof Identifier)) {
		    Number n = (Number)o;
		    Error(n,"Expected a source name here");
		    return;
		}
	    } else {
		if (!(o instanceof Number)) {
		    Identifier id = (Identifier)o;
		    Error(id,"Expected a number here");
		    return;
		}
	    }
	    switch (i) {
	    case 0: d1 = ((Identifier)o).name; break;
	    case 1: start1 = ((Number)o).value; break;
	    case 2: stop1 = ((Number)o).value; break;
	    case 3:
		step1 = ((Number)o).value;
		if (start1 != stop1 && step1 == 0) {
		    Number n = (Number)o;
		    Error(n,"Step size must be nonzero");
		    return;
		}
		break;
	    case 4: d2 = ((Identifier)o).name; break;
	    case 5: start2 = ((Number)o).value; break;
	    case 6: stop2 = ((Number)o).value; break;
	    case 7:
		step2 = ((Number)o).value;
		if (start2 != stop2 && step2 == 0) {
		    Number n = (Number)o;
		    Error(n,"Step size must be nonzero");
		    return;
		}
		break;
	    }
	}

	// a second sweep source means the first sweep gets repeated
	int nreps = 1;
	if (d2 != null) {
	    step2 = (stop2 >= start2) ? Math.abs(step2) : -Math.abs(step2);
	    nreps = (int)Math.floor(Math.abs(stop2-start2)/Math.abs(step2));
	    // see if we'll need an extra step at the end
	    if (start2 + nreps*step2 != stop2) nreps += 1;
	    // remember to count the first one!
	    nreps += 1;
	}

	if (!errors) {
	    String msg = "Performing DC sweep on "+d1;
	    if (d2 != null) msg += " and "+d2;
	    msg += " using "+s.SimulationType();
	    Message(msg);
	    boolean okay = s.DCAnalysis(d1,start1,stop1,step1,d2,start2,stop2,step2,parent);
	    if (!okay) Error(s.Problem());
	    else Message("");
	}
    }

    

    // perform each analysis in turn in the background
    public void run() {
	// give GUI first shot at the cpu
	Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

	try {
	    // perform first analysis
	    if (analyses.size() > 0) {
		Analysis a = (Analysis)analyses.get(0);

		// make sure we start with a clean slate.  On some systems
		// gc will starve since it runs in a different thread than
		// the simulation, so do it here (does this solve the problem???)
		System.gc();

		switch (a.type) {
		case Analysis.Transient:	
		    DoTransientAnalysis(currentNetwork,a);
		    break;
		case Analysis.DC:
		    DoDCAnalysis(currentNetwork,a);
		    break;
		}

		if (!errors) {
		    Message("Preparing plot data");
		    SwingUtilities.invokeLater(ShowSimulationResults);
		}
	    }
	} finally {
	    // do this even if simulation has caused an exception
	    synchronized (this) {
		simulation = null;
	    }
	}
    }

    public void CleanUp() {
	if (currentNetwork != null) {
	    currentNetwork.CleanUp();
	    currentNetwork = null;
	    // tell observers that we're punting old network
	    observers.notifyObservers(null);
	    System.gc();	// recover space network used
	}
    }

    void Simulate(Network s) {
	// see if there's actually anything for us to do...
	if (analyses.size() == 0) {
	    Error("No analyses (.OP, .DC, .TRAN) requested!");
	    return;
	}

	currentNetwork = s;

	// flatten our network
	Message("Creating network");
	if (Netlist((NetlistConsumer)currentNetwork)) {
	    Message("Finalizing network");
	    if (!currentNetwork.Finalize()) {
		Error(currentNetwork.Problem());
	    } else {
		// before running the simulation, capture the buffers
		this.parent.DoDataGathering(assignment);

		// tell observers that we're starting a new simulation
		observers.notifyObservers(null);

		// work on analyses in the background
		simulation = new Thread(this,"simulation");
		simulation.start();
	    }
	}
    }

    public void DoSimulate() {
	if (simulation != null) {
	    Message("Simulation already in progress...");
	    return;
	}
	Message("");		// clean up old message, if any
	ReadNetlist(this,false,Subcircuit.DSUBCKT);	// parse netlist
	if (!errors) {
	    CleanUp();
	    Simulate(new SpiceNetwork(options,tempdir));
	}
    }

    public void DoFastSimulate() {
	if (simulation != null) {
	    Message("Simulation already in progress...");
	    return;
	}
	Message("");		// clean up old message, if any
	ReadNetlist(this,false,Subcircuit.DSUBCKT);	// parse netlist
	if (!errors) {
	    CleanUp();
	    Simulate(new EmuNetwork(options,tempdir));
	}
    }

    public void DoGateSimulate() {
	if (simulation != null) {
	    Message("Simulation already in progress...");
	    return;
	}
	Message("");		// clean up old message, if any
	ReadNetlist(this,false,Subcircuit.GSUBCKT);	// parse netlist
	if (!errors) {
	    CleanUp();
	    SimNetwork network = new SimNetwork(options,tempdir);
	    Simulate(network);
	}
    }

    public void DoFlatten() {
	if (simulation != null) {
	    Message("Simulation in progress...");
	    return;
	}
	Message("");		// clean up old message, if any
	ReadNetlist(this,false,Subcircuit.GSUBCKT);	// parse netlist
	if (!errors) {
	    CleanUp();
	    currentNetwork = new FlattenNetwork(options,tempdir);

	    // flatten our network
	    Message("Creating network");
	    if (Netlist((NetlistConsumer)currentNetwork)) {
		Message("Flattening network");
		if (!currentNetwork.Finalize()) {
		    Error(currentNetwork.Problem());
		}
	    }
	}
    }

    public void DoTimingAnalysis() {
	Message("");		// clean up old message, if any
	ReadNetlist(this,false,Subcircuit.GSUBCKT);	// parse netlist
	if (!errors) {
	    // flatten our network
	    SimNetwork network = new SimNetwork(options,tempdir);
	    Message("Creating network");
	    if (Netlist((NetlistConsumer)network)) {
		Message("Finalizing network");
		if (!network.Finalize(true)) {
		    Error(network.Problem());
		} else {
		    Message("Performing timining analysis...");

		    // set up a new tab to hold analysis results
		    File afile = new File(Source()+".timing");
		    EditBuffer abuffer = parent.FindTab(afile);
		    StringWriter astring = new StringWriter();
		    PrintWriter awriter = new PrintWriter(astring);
		    awriter.format("Timing analysis for %1$s at %2$s\n",Source(),(new java.util.Date()).toString());

		    // do the timing analysis
		    network.TimingAnalysis(awriter);

		    // copy the analysis results into the new tab
		    abuffer.text.setText(astring.toString());
		    Message("Timing analysis complete");

		    // now capture the buffers
		    this.parent.DoDataGathering(assignment);
		}
	    }
	}
    }

    public Node FindGlobalNode(Identifier name) {
	Node n;
	if ((n = (Node)globalNodes.get(name.name)) != null) return(n);
	n = new Node(null,name);
	globalNodes.put(name.name,n);
	return(n);
    }
  
    public void Error(String msg) {
        error_start = (currentNetlist == null) ? 0 : lineStart+lineOffset;
        error_end = error_start;
        error_message = msg;

	Error(currentNetlist==null ? this : currentNetlist,error_start,msg);
    }

    public void Error(Token t,String msg) {
        error_start = t.start;
        error_end = t.end;
        error_message = msg;
	errors = true;

	Message(t.netlist,t.start,t.end,msg);
    }

    // see if we can read an identifier starting at line[lineOffset].
    // if so: return an Identifier structure, advance lineOffset
    // if not: return null, don't advance lineOffset
    private Identifier ReadIdentifier(boolean complain) {
	Identifier id = Identifier.Parse(line,currentNetlist,lineOffset);

	if (id == null) {
	    if (complain) Error("Identifier expected here");
	    return null;
	}

	lineOffset = id.end;
	id.start += lineStart;
	id.end += lineStart;
	return(id);
    }

    // see if we can read a node name starting at line[lineOffset].
    private Node ReadNode(boolean complain) {
	Identifier id = ReadIdentifier(false);

	if (id == null) {
	    if (complain) Error("Node name expected here");
	    return null;
	}

	// let user specify a[0]...
	ArrayList vid = id.Expand();
	if (vid.size() != 1) {
	    if (complain) Error("Just a single node name expected here");
	    return null;
	}

	return currentSubcircuit.FindNode(this,(Identifier)vid.get(0));
    }

    // see if we can read a number starting at line[lineOffset].
    // if so: return a Number structure, advance lineOffset
    // if not: return null, don't advance lineOffset
    private Number ReadNumber(boolean complain) {
	Number n = Number.Parse(line,currentNetlist,lineOffset);

	if (n == null) {
	    if (complain) Error("Numeric value expected here");
	    return null;
	}

	lineOffset = n.end;
	n.start += lineStart;
	n.end += lineStart;
	return(n);
    }

    // read quoted string 
    private String ReadString(boolean firstQuote) {
	if (!firstQuote && !ReadExpected('"')) {
	    Error("Expected '\"' (quote mark) here");
	    return null;
	}
	StringBuffer iname = new StringBuffer();
	int ch;
	while (lineOffset < lineLength && (ch = line.charAt(lineOffset)) != '"') {
	    iname.append((char)ch);
	    lineOffset += 1;
	}
	if (!ReadExpected('"')) {
	    Error("Expected '\"' (quote mark) here");
	    return null;
	}
	return iname.toString();
    }

    // read a token: quoted string or sequence of non-space chars
    private String ReadToken() {
	// skip over blanks
	while (lineOffset < lineLength && line.charAt(lineOffset) <= ' ')
	    lineOffset += 1;

	if (lineOffset == lineLength) return null;
	else if (line.charAt(lineOffset) == '"') 
	    return ReadString(false);

	StringBuffer token = new StringBuffer();
	int ch;
	while (lineOffset < lineLength && (ch = line.charAt(lineOffset))>' ') {
	    token.append((char)ch);
	    lineOffset += 1;
	}

	// see if we've found anything
	String result = token.toString();
	return result.length() == 0 ? null : result;
    }



    // make sure there's nothing else on the line
    private boolean ReadEndOfLine(boolean complain) {
	while (lineOffset < lineLength) {
	    // skip over blanks
	    if (line.charAt(lineOffset) <= ' ') {
		lineOffset += 1;
		continue;
	    }
	    if (complain) Error("End of line expected here");
	    return false;
	}
	return true;
    }

    // make sure next thing is character we expect
    private boolean ReadExpected(int expected) {
	int ch;

	while (lineOffset < lineLength) {
	    // skip over blanks
	    if ((ch = line.charAt(lineOffset)) <= ' ') {
		lineOffset += 1;
		continue;
	    } else if (ch != expected) break;
	    lineOffset += 1;
	    return true;
	}
	return false;
    }

    private ArrayList ReadNumberList(boolean openParen) {
	ArrayList params = new ArrayList();
	while (true) {
	    if (openParen && ReadExpected(')')) break;
	    if (ReadExpected(',')) continue;
	    Number n = ReadNumber(false);
	    if (n == null) {
		if (openParen) {
		    Error("Expected ')' (close paren) here");
		    return null;
		}
		break;
	    }
	    params.add(n);
	}
	return params;
    }

    private double[] ReadDoubleList(boolean openParen) {
	ArrayList params = ReadNumberList(openParen);
	if (params == null || params.size() == 0) return null;
	int nnumbers = params.size();
	double result[] = new double[nnumbers];
	for (int i = 0; i < nnumbers; i += 1)
	    result[i] = ((Number)params.get(i)).value;
	return result;
    }


    // determine values for each parameter on our list.  If complain is true,
    // complain if we run across a spec that's not on our list
    private Parameter ReadParameters(Parameter list,boolean complain) {
	Parameter p;

	// initialize each parameter to its default value
	for (p = list; p != null; p = p.next) p.Initialize();

	// now look for parameter definitions in remainder of input line
	// checking each spec to see if it's a parameter we care about...
	while (lineOffset < lineLength) {
	    Identifier id = ReadIdentifier(false);
	    if (id == null) break;

	    // locate parameter in the list
	    p = list.Find(id.name);
	    if (p == null && complain) {
		Error(id,"Unrecognized parameter name");
		return null;
	    }

	    // read in value
	    if (ReadExpected('=')) {
		if (p.type == Parameter.STRING) {
		    if (!ReadExpected('"')) {
			Error("Expected a quoted string here");
			return null;
		    }
		    String s = ReadString(true);
		    if (s == null) return null;
		    p.ovalue = s;
		} else if (p.type == Parameter.VECTOR) {
		    if (!ReadExpected('(')) {
			Error("Expected an open parenthesis here");
			return null;
		    }
		    double v[] = ReadDoubleList(true);
		    if (v == null) return null;
		    p.ovalue = v;
		} else {
		    Number n = ReadNumber(complain);
		    if (n == null) return null;
		    p.nvalue = n.value;
		}
		p.defined = true;
	    }
	}
	return list;
    }

    private boolean ReadSubckt(Identifier command,int stype) {
	if (!nestingAllowed && currentSubcircuit != topLevel) {
	    Error(command,"Nested .subckt definitions not enabled (are you missing a \".ends\" statement for "+currentSubcircuit.id.name+"?)");
	    return false;
	}

	// get subcircuit name and ensure it's unique
	Identifier cktname = ReadIdentifier(true);
	if (cktname == null) return false;

	if (cktname.name.charAt(0) == '$') {
	    Error(cktname,"Subcircuit names beginning with $ are reserved for built-in functions");
	    return false;
	}
	if (currentSubcircuit.GetSubcircuit(cktname.name,stype) != null) {
	    Error(cktname,"Duplicate name for "+Subcircuit.SNAMES[stype]);
	    return false;
	}

	// create the subcircuit and process external node list
	Subcircuit s = new Subcircuit(currentSubcircuit,cktname,stype);
	while (true) {
	    Identifier ext = ReadIdentifier(false);
	    if (ext == null) break;
	    ArrayList exts = ext.Expand();
	    int nexts = exts.size();
	    for (int i = 0; i < nexts; i += 1) {
		Identifier id = (Identifier)exts.get(i);
		Node extnode = s.FindNode(this,id);		// add node to local hashtable
		if (extnode.parent == null) {
		    Error(ext,"Global nodes can't be used in .subckt statement");
		    return false;
		}

		if (s.externals.contains(extnode)) {
		    Error(ext,"Duplicate node name in .subckt statement");
		    return false;
		}

		extnode.external = true;
		s.externals.add(extnode);
	    }
	}
	// if everything's okay, install new subcircuit as current one
	if (ReadEndOfLine(true)) currentSubcircuit = s;
	return false;
    }

    // return true if processing of netlist is to stop, false otherwise
    private boolean ReadControlCard() {
	Identifier command = ReadIdentifier(true);

	// subcircuit definitions
	if (command.equals(".subckt"))
	    return ReadSubckt(command,Subcircuit.SUBCKT);
	if (command.equals(".dsubckt"))
	    return ReadSubckt(command,Subcircuit.DSUBCKT);
	if (command.equals(".gsubckt"))
	    return ReadSubckt(command,Subcircuit.GSUBCKT);
    
	// end of subcircuit definition
	if (command.equals(".ends")) {
	    // get subcircuit name and check to see if it matches current .subckt
	    Identifier cktname = ReadIdentifier(false);
	    if (cktname!=null && !cktname.equals(currentSubcircuit.id)) {
		Error(cktname,"Name doesn't match that of most recent .subckt");
		return false;
	    }

	    if (currentSubcircuit == topLevel) {
		Error(command,".ends without matching .subckt");
		return false;
	    }

	    currentSubcircuit = currentSubcircuit.parent;
	    return false;
	}

	// connect directive
	if (command.equals(".connect")) {
	    ArrayList connects = new ArrayList();
	    Identifier id;
	    while ((id = ReadIdentifier(false)) != null) {
		ArrayList ids = id.Expand();
		int nids = ids.size();
		for (int i = 0; i < nids; i += 1)
		 connects.add(ids.get(i));
	    }
	    if (!ReadEndOfLine(false))
		Error("Expected identifier or end of line here");

	    // remember which connection pairs we want to make
	    int nconnects = connects.size();
	    if (nconnects > 1) {
		id = (Identifier)connects.get(0);
		for (int i = 1; i < nconnects; i += 1)
		    currentSubcircuit.ConnectNodes(this,id,(Identifier)connects.get(i));
	    }

	    return false;
	}

	// all the rest of the control cards must appear at top level
	if (currentSubcircuit != topLevel) {
	    Error(command,command.name+" cannot appear in a subcircuit definition.");
	    return false;
	}

	// include directive
	if (command.equals(".include")) {
	    String iname = ReadString(false);
	    if (iname == null || !ReadEndOfLine(true)) return false;

	    // load file into a buffer and parse it
	    String n;
	    if (source == null) n = iname.toString();
	    else n = parent.MergePathnames(source,iname.toString());
	    File ifile = new File(n);
	    try {
		if (ifile.exists()) 
		    ReadNetlist((Netlist)parent.FindBuffer(ifile),true,0);
		else
		    Error("Can't read included netlist: "+ifile.getCanonicalPath()+" not found");
	    }
	    catch (Exception e) {
		    Error("Can't read included netlist: "+e);
	    }
	    
	    return false;
	}

	// .checkoff server assignment checksum
	if (command.equals(".checkoff") || command.equals(".pcheckoff")) {
	    checkoffCommand = command.name.substring(1);
	    if ((checkoffServer = ReadToken()) == null) {
		Error("expected name of checkoff server here");
		return false;
	    }
	    if ((assignment = ReadToken()) == null) {
		Error("expected name of assignment here");
		return false;
	    }
	    Number n = ReadNumber(true);
	    if (n != null) checkoffChecksum = (int)n.value;
	    ReadEndOfLine(true);
	    return false;
	}

	// .verify nodes... spec(#,#,...) d0 d1...
	if (command.equals(".verify")) {
	    ReadVerify(verifications);
	    return false;
	}

	// .generatecheckoff nodes... spec(#,#,...) d0 d1...
	if (command.equals(".generatecheckoff")) {
	    ReadVerify(generateCheckoffs);
	    return false;
	}

	// .mverify device_name starting_addr v0 v1...
	if (command.equals(".mverify")) {
	    ReadMVerify();
	    return false;
	}

	// .writedata "filename" (step,[offset,[stop]) nodes...
	if (command.equals(".writedata")) {
	    ReadWriteData();
	    return false;
	}

	// temporary directory
	if (command.equals(".tempdir")) {
	    String tname = ReadString(false);
	    if (tname != null) tempdir = tname;
	    return false;
	}

	// model definition
	if (command.equals(".model")) {
	    // get model name and ensure it's unique
	    Identifier mname = ReadIdentifier(true);
	    if (currentSubcircuit.models.get(mname.name) != null) {
		Error(mname,"Duplicate .model name");
		return false;
	    }
	    // get model type
	    Identifier modeltype = ReadIdentifier(true);
	    if (modeltype == null) return false;
	    int mtype = Model.ModelType(modeltype.name);
	    if (mtype == 0) {
		Error(modeltype,"Unrecognized model type");
		return false;
	    }
	    // create the model and process its options
	    Model m = new Model(currentSubcircuit,mname,mtype);
	    Identifier id;
	    boolean paren = ReadExpected('(');
	    while ((id = ReadIdentifier(false)) != null) {
		Number n = null;
		if (ReadExpected('=')) {
		    if ((n = ReadNumber(true)) == null) return false;
		}
		m.options.put(id.name,new Double((n == null) ? 1.0 : n.value));
	    }
	    if (paren && !ReadExpected(')')) {
		Error("Expected ')' (close paren) here");
		return false;
	    }
	    ReadEndOfLine(true);
	    return false;
	}

	// analysis requests
	// .tran [plotstep] stoptime
	// .dc source1 start1 stop1 step1 [source2 start2 stop2 step2]
	// .ac ...
	// .op ...
	int atype = -1;
	if (command.equals(".tran")) atype = Analysis.Transient;
	else if (command.equals(".dc")) atype = Analysis.DC;
	else if (command.equals(".ac")) atype = Analysis.AC;
	else if (command.equals(".op")) atype = Analysis.OperatingPoint;
	if (atype != -1) {
	    Analysis a = new Analysis(command,atype);
	    // gobble down all the parameters
	    while (true) {
		Number n = ReadNumber(false);
		if (n != null) a.params.add(n);
		else {
		    Identifier id = ReadIdentifier(false);
		    if (id != null) a.params.add(id);
		    else break;
		}
	    }
	    // add to list of requested analyses
	    this.analyses.add(a);
	    return false;
	}

	// plot request
	// .plot atype spec...
	if (command.equals(".plot")) {
	    Identifier id = ReadIdentifier(false);
	    if (id == null) {
		Error("Expected name of an analysis or node here");
		return false;
	    }
	    Analysis a = Analysis.FindAnalysis(this,id.name);
	    if (a == null) {
		if (analyses.size() > 0) a = (Analysis)analyses.get(analyses.size()-1);
		else {
		    Error(id,"Can't find analysis of this type");
		    return false;
		}
	    } else id = null;

	    ArrayList plot = new ArrayList();
	    a.plots.add(plot);
	    while (true) {			// read each plot request
		Identifier pname = null;
		if (id == null) id = ReadIdentifier(false);
		if (id == null) break;
		if (ReadExpected('(')) {
		    pname = id;
		    StringBuffer names = new StringBuffer();
		    while (true) {
			id = ReadIdentifier(true);
			if (id == null) return false;
			if (names.length() > 0) names.append(',');
			names.append(id.name);
			if (!ReadExpected(',')) break;
		    }
		    if (!ReadExpected(')')) {
			Error("Expected ')' (close paren) here");
			return false;
		    }
		    id = new Identifier(names.toString(),pname.netlist,pname.end,id.end);
		} else {
		    pname = new Identifier("v",id.netlist,id.start,id.end);
		}
		plot.add(new PlotRequest(pname,id));
		id = null;
	    }
	    if (!ReadEndOfLine(false))
		Error("Expected plot specification here");
	    return false;
	}
    
	// .plotdef type id0 id1 ... idn
	// used to generate symbolic plots, e.g.,
	//  .plotdef color black dgray lgray white
	//  .plot color(pixel[1:0])
	// would display the value of pixel[1:0] as "lgray" instead of "10"
	if (command.equals(".plotdef")) {
	    String def = ReadToken();
	    if (def == null) {
		Error("Expected name of new plot type here");
		return false;
	    }
	    if (plotdefs.get(def) != null) {
		Error("duplicate .plotdef specification for "+def);
	    }

	    ArrayList names = new ArrayList();
	    String entry;
	    while ((entry = ReadToken()) != null) names.add(entry);

	    if (!ReadEndOfLine(false))
		Error("Expected identifier or end of line here");

	    plotdefs.put(def,names);
	    return false;
	}

	// specify various options
	// .options [id | id=n]...
	if (command.equals(".options")) {
	    Identifier id;
	    while ((id = ReadIdentifier(false)) != null) {
		Number n = null;
		if (ReadExpected('=')) {
		    if ((n = ReadNumber(true)) == null) return false;
		}
		//System.out.println("option "+id.name+"="+n);
		options.put(id.name,new Double((n == null) ? 1.0 : n.value));
	    }
	    if (!ReadEndOfLine(false))
		Error("Expected identifier or end of line here");
	    return false;
	}
    

	// specify simulation temperature in centigrade
	// .temp n
	if (command.equals(".temp")) {
	    Number n = ReadNumber(true);

	    if (!ReadEndOfLine(false))
		Error("Expected end of line here");
	    else if (n != null) {
		options.put(command.name,new Double(n.value));
	    }

	    return false;
	}

	// declare identifier(s) to be global node names
	// .global id...
	if (command.equals(".global")) {
	    Identifier id;
	    while ((id = ReadIdentifier(false)) != null)
		FindGlobalNode(id);
	    if (!ReadEndOfLine(false))
		Error("Expected identifier or end of line here");
	    return false;
	}

	// end of netlist
	// .end
	if (command.equals(".end")) {
	    ReadEndOfLine(true);
	    return true;
	}

	// for testing...
	if (command.equals(".bug")) {
	    String foo = null;
	    foo.length();
	    return false;
	}

	Error(command,"Unrecognized control card");
	return false;
    }

    private void ReadSubcircuitCall() {
	Identifier id,last = null;
	ArrayList args = new ArrayList();
	Parameter params = null;

	if ((id = ReadIdentifier(true)) == null) return;

	// read list of argument nodes.  Last identifier is actually name of
	// subcircuit so process each identifier only after we're sure it's
	// not the last!
	while (true) {
	    Identifier arg = ReadIdentifier(false);
	    if (arg == null) break;
	    if (ReadExpected('=')) {
		if (ReadExpected('"')) {
		    String s = ReadString(true);
		    if (s == null) return;
		    params = new Parameter(arg.name,s,params);
		} else if (ReadExpected('(')) {
		    double v[] = ReadDoubleList(true);
		    if (v == null) return;
		    params = new Parameter(arg.name,v,params);
		} else {
		    Number n = ReadNumber(true);
		    if (n == null) return;
		    params = new Parameter(arg.name,n.value,params);
		}
	    } else {
		if (last != null) {
		    ArrayList ids = last.Expand();
		    int nids = ids.size();
		    for (int i = 0; i < nids; i += 1) {
			Identifier argid = (Identifier)ids.get(i);
			Node n = currentSubcircuit.FindNode(this,argid);
			args.add(n);
		    }
		}
		last = arg;
	    }
	}

	if (last == null) {
	    Error("Expected name of user-defined device here");
	    return;
	}

	if (ReadEndOfLine(true)) 
	    currentSubcircuit.AddDevice(this,new SubcircuitCall(id,last,args,params));
    }

    private void ReadCapacitor() {
	Identifier id;
	Node n1,n2;
	Number capacitance;

	if ((id = ReadIdentifier(true)) == null ||
	    (n1 = ReadNode(true)) == null ||
	    (n2 = ReadNode(true)) == null ||
	    (capacitance = ReadNumber(true)) == null ||
	    !ReadEndOfLine(true))
	    return;

	//System.out.println("Capacitor "+id.name+" "+n1.id.name+" "+n2.id.name+
	//		       " "+capacitance.value);

	if (capacitance.value < 0) {
	    Error(capacitance,"Capacitances must be non-negative");
	    return;
	}

	currentSubcircuit.AddDevice(this,new CapacitorPrototype(id,n1,n2,capacitance));
    }

    private void ReadInductor() {
	Identifier id;
	Node n1,n2;
	Number inductance;

	if ((id = ReadIdentifier(true)) == null ||
	    (n1 = ReadNode(true)) == null ||
	    (n2 = ReadNode(true)) == null ||
	    (inductance = ReadNumber(true)) == null ||
	    !ReadEndOfLine(true))
	    return;

	//System.out.println("Inductor "+id.name+" "+n1.id.name+" "+n2.id.name+
	//		       " "+inductance.value);

	if (inductance.value < 0) {
	    Error(inductance,"Inductances must be non-negative");
	    return;
	}

	currentSubcircuit.AddDevice(this,new InductorPrototype(id,n1,n2,inductance));
    }

    private void ReadResistor() {
	Identifier id;
	Node n1,n2;
	Number resistance;

	if ((id = ReadIdentifier(true)) == null ||
	    (n1 = ReadNode(true)) == null ||
	    (n2 = ReadNode(true)) == null ||
	    (resistance = ReadNumber(true)) == null ||
	    !ReadEndOfLine(true))
	    return;

	if (resistance.value <= 0) {
	    Error(resistance,"Resistances must be positive");
	    return;
	}

	currentSubcircuit.AddDevice(this,new ResistorPrototype(id,n1,n2,resistance));
    }

    private void ReadMosfet() {
	Identifier id,mname;
	Node d,g,s,b;

	if ((id = ReadIdentifier(true)) == null ||
	    (d = ReadNode(true)) == null ||
	    (g = ReadNode(true)) == null ||
	    (s = ReadNode(true)) == null ||
	    (b = ReadNode(true)) == null ||
	    (mname = ReadIdentifier(true)) == null)
	    return;

	// process any parameters
	Parameter p = ReadParameters(MosfetPrototype.mparams,true);
	if (p == null) return;

	if (!ReadEndOfLine(false)) {
	    Error("End of line expected here.  Mosfet format is \"Mid ndrain ngate nsource nsubstrate model params...\"");
	   return;
	}

	if ((!p.Find("l").defined && !p.Find("sl").defined) ||
	    (!p.Find("w").defined && !p.Find("sw").defined)) {
	    Error(mname.netlist,mname.end,"Mosfet length and width must be specified");
	    return;
	}

	currentSubcircuit.AddDevice(this,
	 new MosfetPrototype(id,d,g,s,b,mname,
			     p.Value("l",0),p.Value("w",0),
			     p.Value("sl",0),p.Value("sw",0),
			     p.Value("ad",0),p.Value("pd",0),
			     p.Value("nrd",0),p.Value("rdc",0),
			     p.Value("as",0),p.Value("ps",0),
			     p.Value("nrs",0),p.Value("rsc",0)));
    }

    private void ReadIndependentSource() {
	Identifier id;
	Node npos,nneg;
	Number dc = null;
	Number acmag = null;
	Number acphase = null;
	int trantype = 0;
	ArrayList params = null;

	// read name of source and the pos/neg terminals
	if ((id = ReadIdentifier(true)) == null ||
	    (npos = ReadNode(true)) == null ||
	    (nneg = ReadNode(true)) == null) return;

	// now look for DC, AC or transient source specifications
	while (true) {
	    // if next thing is a number, then it's the DC value
	    Number xdc = ReadNumber(false);
	    if (xdc != null) {
		if (dc != null) {
		    Error(xdc,"Duplicate DC source specification");
		    return;
		}
		dc = xdc;
		continue;
	    }

	    // identifier will tell us type of source value is next
	    Identifier temp = ReadIdentifier(false);
	    if (temp == null) break;
	    else if (temp.equals("dc")) {		// DC value
		if (dc != null) {
		    Error(temp,"Duplicate DC source specification");
		    return;
		}
		ReadExpected('=');
		if ((dc = ReadNumber(true)) == null) return;
	    } else if (temp.equals("ac")) {		// AC mag and phase
		if (acmag != null) {
		    Error(temp,"Duplicate AC source specification");
		    return;
		}
		ReadExpected('=');
		if ((acmag = ReadNumber(true)) == null) return;
		if (ReadExpected(',')) {
		    if ((acphase = ReadNumber(true)) == null) return;
		}
	    } else {					// transient source
		if (trantype != 0) {
		    Error(temp,"Duplicate transient source specification");
		    return;
		}
		trantype = IndependentSourcePrototype.SourceFunctionType(temp.name);
		if (trantype == 0) {
		    Error(temp,"Unrecognized transient source function");
		    return;
		}
		// parameters might be delimited by '(' and ')'
		boolean openParen = ReadExpected('(');
		params = ReadNumberList(openParen);
		if (params == null) return;
		if (!IndependentSourcePrototype.CheckParams(this,trantype,params)) return;
	    }
	}

	if (dc==null && acmag==null && trantype==0) {
	    Error("Expected DC, AC or name of transient source here");
	    return;
	}
	if (!ReadEndOfLine(true)) return;

	currentSubcircuit.AddDevice(this,
				    new IndependentSourcePrototype(id,
								   Character.toLowerCase(id.name.charAt(0))=='v' ?
								   IndependentSourcePrototype.VoltageSource :
								   IndependentSourcePrototype.CurrentSource,
								   npos,nneg,dc,acmag,acphase,trantype,params));
    }

    // Wid nodes... nrz(vlow,vhigh,tp,td,tr,tf) d0 d1 ...
    private void ReadWaveformSource() {
	ArrayList nodes = new ArrayList();

	// read name of source
	Identifier id;
	if ((id = ReadIdentifier(true)) == null) return;

	// read in nodes until we find timing spec
	Identifier temp = null;
	while (true) {
	    temp = ReadIdentifier(true);
	    if (ReadExpected('(')) break;
	    if (temp == null) {
		Error("Expected waveform timing specification");
		return;
	    }
	    ArrayList ids = temp.Expand();
	    int nids = ids.size();
	    for (int i = 0; i < nids; i += 1) {
		Identifier nid = (Identifier)ids.get(i);
		Node n = currentSubcircuit.FindNode(this,nid);
		nodes.add(n);
	    }
	}

	if (temp.equals("nrz")) {
	    ArrayList params = new ArrayList();
	    while (true) {
		if (ReadExpected(')')) break;
		if (ReadExpected(',')) continue;
		Number n = ReadNumber(false);
		if (n == null) {
		    Error("Expected ')' (close paren) here");
		    return;
		}
		params.add(n);
	    }
	    ArrayList data = new ArrayList();
	    while (true) {
		Number n = ReadNumber(false);
		if (n == null) break;
		data.add(n);
	    }

	    // build PWL source(s)
	    double vlow = 0;
	    double vhigh = 5;
	    double tp = 1e-8;
	    double td = 0;
	    double tr = 1e-10;
	    double tf = 1e-10;

	    if (params.size() > 0) vlow = ((Number)params.get(0)).value;
	    if (params.size() > 1) vhigh = ((Number)params.get(1)).value;
	    if (params.size() > 2) tp = ((Number)params.get(2)).value;
	    if (params.size() > 3) td = ((Number)params.get(3)).value;
	    if (params.size() > 4) tf = tr = ((Number)params.get(4)).value;
	    if (params.size() > 5) tf = ((Number)params.get(5)).value;

	    int nnodes = nodes.size();
	    int ndata = data.size();
	    Node gnd = currentSubcircuit.FindNode(this,new Identifier("0",this,0,0));
	    Number zero = new Number(0,this,0,0);
	    for (int i = 0; i < nnodes; i += 1) {
		ArrayList tvpairs = new ArrayList();

		// start waveform at 0s, vlow
		tvpairs.add(new Number(0,this,0,0));
		tvpairs.add(new Number(vlow,this,0,0));

		// construct PWL waveform from data
		int last = 0;
		for (int j = 0; j < ndata; j += 1) {
		    long v = (long)((Number)data.get(j)).value;
		    int bit = (int)((v >> (nnodes - i - 1)) & 0x1);
		    if (bit != last) {
			double t = j * tp + td;
			tvpairs.add(new Number(t,this,0,0));
			tvpairs.add(new Number(last == 0 ? vlow : vhigh,this,0,0));
			tvpairs.add(new Number(t+(bit == 0 ? tf : tr),this,0,0));
			tvpairs.add(new Number(bit == 0 ? vlow : vhigh,this,0,0));
			last = bit;
		    }
		}

		// finally add PWL voltage to the network for this bit
		currentSubcircuit.AddDevice(this,
		  new IndependentSourcePrototype(
		       new Identifier(id.name+"#"+i,id.netlist,id.start,id.end),
		       IndependentSourcePrototype.VoltageSource,
		       (Node)nodes.get(i),gnd,
		       zero,zero,zero,NetlistConsumer.PWL,tvpairs));
		
	    }
	} else {
	    Error(temp,"Expected waveform type (eg, NRZ) here");
	    return;
	}

	if (!ReadEndOfLine(true)) return;
    }

    private void ReadDependentSource() {
	Identifier id;
	Node npos,nneg,ncpos,ncneg;
	Number gain = null;

	// read name of source, pos/neg terminals, pos/neg control terminals
	// and gain
	if ((id = ReadIdentifier(true)) == null ||
	    (npos = ReadNode(true)) == null ||
	    (nneg = ReadNode(true)) == null ||
	    (ncpos = ReadNode(true)) == null ||
	    (ncneg = ReadNode(true)) == null ||
	    (gain = ReadNumber(true)) == null ||
	    !(ReadEndOfLine(true))) return;

	currentSubcircuit.AddDevice(this,
				    new DependentSourcePrototype(id,npos,nneg,ncpos,ncneg,gain));
    }

    // .verify nodes... periodic(tfirst,tperiod) d0 d1 ...
    private void ReadVerify(ArrayList list) {
	String nodes = "";

	// read in nodes until we find sampling spec
	Identifier temp = null;
	while (true) {
	    temp = ReadIdentifier(true);
	    if (ReadExpected('(')) break;
	    if (temp == null) {
		Error("Expected verify sampling specification");
		return;
	    }
	    if (nodes.length() > 0) nodes += ",";
	    nodes += temp.name;
	}

	// read sampling parameters
	ArrayList params = new ArrayList();
	while (true) {
	    if (ReadExpected(')')) break;
	    if (ReadExpected(',')) continue;
	    Number n = ReadNumber(false);
	    if (n == null) {
		Error("Expected ')' (close paren) here");
		return;
	    }
	    params.add(n);
	}

	// read data values
	ArrayList data = new ArrayList();
	while (true) {
	    Number n = ReadNumber(false);
	    if (n == null) break;
	    data.add(n);
	}

	if (!ReadEndOfLine(true)) return;

	VerifyData v = new VerifyData(nodes,temp.name,params,data);
	if (v.ValidType()) list.add(v);
	else Error(temp,"unrecognized sample specification for .verify");
    }

    // .mverify device_name starting_addr v0 v1...
    private void ReadMVerify() {
	Identifier deviceName = ReadIdentifier(true);
	if (deviceName == null) return;
	Number startingAddr = ReadNumber(true);
	if (startingAddr == null) return;

	ArrayList data = new ArrayList();
	data.add(deviceName);	// first entry is device name
	data.add(startingAddr);	// second entry is starting address
	while (true) {		// rest are expected data values
	    Number n = ReadNumber(false);
	    if (n == null) break;
	    data.add(n);
	}
	if (!ReadEndOfLine(true)) return;
	mverifications.add(data);	// save for checkoff
    }

    // .writedata "filename" (step[,offset,[stop]]) nodes...
    private void ReadWriteData() {
	// capture filename
	String fname = ReadString(false);
	if (fname == null) return;

	// capture step and optional offset, stop times
	boolean openParen = ReadExpected('(');
	ArrayList params = ReadNumberList(openParen);
	if (params == null) return;

	ArrayList data = new ArrayList();
	data.add(fname);	// first entry is filename
	data.add(params);	// second entry is timing parameters

	// collect list of nodes whose values are to be written out
	StringBuffer nodes = new StringBuffer();
	while (true) {
	    Identifier id = ReadIdentifier(false);
	    if (id == null) break;
	    if (nodes.length() > 0) nodes.append(',');
	    nodes.append(id.name);
	}
	if (nodes.length() == 0) return;
	data.add(nodes.toString());	// third entry is list of nodes

	if (!ReadEndOfLine(true)) return;
	writedata.add(data);	// save for checkoff
    }

    public void ReadNetlist(Netlist buffer,boolean includeFile,int stype) {
	if (includeFile) {
	    if (buffer.busy) {
		Error("Recursive .include statement");
		return;
	    }
	} else Message("Parsing netlist");

	buffer.busy = true;
	Netlist save = currentNetlist;
	currentNetlist = buffer;

	String netlist = buffer.getText();
	int maxOffset = netlist.length();	// how many chars we'll process
	int offset = 0;			// current location in input stream
	int ch;				// character we're working on
	boolean titleLine = false;	// treat first line as title?
	int i;

	// initialize network data structures
	if (!includeFile) {
	    title = "";
	    errors = false;
	    options = new HashMap();
	    plotdefs = new HashMap();
	    tempdir = null;
	    globalNodes = new HashMap();
	    gnd = FindGlobalNode(new Identifier("0",this,-1,-1));
	    topLevel = new Subcircuit(null,new Identifier("***top level***",this,-1,-1),stype);
	    currentSubcircuit = topLevel;
	    analyses = new ArrayList();
	    checkoffServer = null;
	    assignment = null;
	    checkoffChecksum = 0;
	    verifications = new ArrayList();
	    generateCheckoffs = new ArrayList();
	    mverifications = new ArrayList();
	    writedata = new ArrayList();
	}

	// prime the pump
	if (offset < maxOffset) ch = netlist.charAt(offset++);
	else ch = -1;

	// process the netlist line-by-line
	while (ch >= 0 && !errors) {
	    lineStart = offset - 1;		// remember where line started
	    line.setLength(0);		// start with fresh buffer

	    // read a line into our buffer handling extension lines (+).
	    // To make accurate error reporting possible, blanks are
	    // added whenever characters are skipped to ensure that (lineStart +
	    // offset into buffer) corresponds to actual offset of character
	    // in the input stream.
	    boolean possibleEOL = false;
	    while (ch >= 0) {
		if (ch == '\n') {		// end of line (maybe)
		    line.append(' ');
		    possibleEOL = true;
		} else if (ch == '/') {		// comment (maybe)
		    // see what next character is
		    if (offset < maxOffset) ch = netlist.charAt(offset++);
		    else ch = -1;
		    if (ch == '/') {
			// add same number of chars to buffer
			line.append(' ');
			line.append(' ');
			// skip to newline
			while (true) {
			    if (offset < maxOffset) ch = netlist.charAt(offset++);
			    else { ch = -1; break; }
			    if (ch == '\n') break;
			    line.append(' ');
			}
			continue;
		    } else if (ch == '*') {
			int commentStart = offset - 2;
			// add same number of chars to buffer
			line.append(' ');
			line.append(' ');
			// skip to "*/"
			boolean asterisk = false;
			while (true) {
			    if (offset < maxOffset) ch = netlist.charAt(offset++);
			    else {
				ch = -1;
				Error(this,commentStart,"unterminated '/*' comment");
				break;
			    }
			    line.append(' ');
			    if (asterisk && ch == '/') break;
			    asterisk = (ch == '*');
			}
		    } else {
			line.append('/');
			continue;
		    }
		} else if (possibleEOL && ch == '+') {
		    // extension lines begin with +
		    line.append(' ');
		    possibleEOL = false;
		} else {
		    if (ch > ' ' && possibleEOL) break;
		    line.append((char)ch);
		}
		// gobble down next character
		if (offset < maxOffset) ch = netlist.charAt(offset++);
		else ch = -1;
	    }

	    if (titleLine) {			// first line is title
		title = line.toString();
		titleLine = false;
		continue;
	    }

	    // chop off any blanks at end of line, skip blank and comment lines
	    i = line.length() - 1;
	    while (i >= 0 && line.charAt(i) <= ' ') i -= 1;
	    if (i < 0 || line.charAt(0) == '*') continue;
	    lineLength = i + 1;
	    line.setLength(lineLength);

	    // process the line we just read
	    lineOffset = 0;
	    int lch;
	    while ((lch = line.charAt(lineOffset)) <= ' ') lineOffset += 1;
	    switch (Character.toLowerCase((char)lch)) {
	    case '.':	if (ReadControlCard()) ch = -1; break;
	    case 'c':	ReadCapacitor(); break;
	    case 'e':	ReadDependentSource(); break;
	    case 'f':	ReadDependentSource(); break;
	    case 'g':	ReadDependentSource(); break;
	    case 'h':	ReadDependentSource(); break;
	    case 'i':	ReadIndependentSource(); break;
	    case 'l':	ReadInductor(); break;
	    case 'm':	ReadMosfet(); break;
	    case 'r':	ReadResistor(); break;
	    case 'v':	ReadIndependentSource(); break;
	    case 'w':	ReadWaveformSource(); break;
	    case 'x':	ReadSubcircuitCall(); break;
	    default:	Error("Unrecognized device type");
	    break;
	    }
	}

	if (!errors && currentSubcircuit != topLevel) {
	    Error(currentSubcircuit.id,"Missing \".ends\" statement for this subcircuit");
	}
	currentSubcircuit = topLevel;

	buffer.busy = false;
	currentNetlist = save;
    }
}
