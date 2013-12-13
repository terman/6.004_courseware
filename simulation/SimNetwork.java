// Copyright (C) 1999-2008 Christopher J. Terman - All Rights Reserved.

package simulation;

import gui.ProgressTracker;
import gui.UI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import netlist.Parameter;

public class SimNetwork extends Network {
    // cons up a special marker to mark the end of device lists.  Use this
    // instead of null so we can can use null to indicate that device isn't
    // aready part of the list
    static final SimDevice DEOL = new SimDevice("DEOL",0,0);

    SimDevice devices;		// linked list of network's devices
    double size;		// sum of device sizes
    int ngates;			// gate count
    SimDevice clist;		// devices that need evaluating because of contamination events
    SimDevice plist;		// devices that need evaluating because of propagation events
    SimEvent eventQueue;	// list of pending events
    SimEvent freeEvents;	// unused event structures;
    boolean networkError;	// true if network is ill-formed
    boolean initialized;	// network has been initialized
    boolean finalized;		// Finalize has been called
    ArrayList gndNodes;		// all the ground nodes the user defined

    int debugLevel;		// control amount of debugging printout
    SimDevice minSetupDevice;	// device with min setup time
    double minSetupTime;
    double cintercept;		// used for interconnect capacitance calculation
    double cslope;

    public SimNetwork(HashMap options,String tempdir) {
	super(options,tempdir);
	size = 0;
	ngates = 0;
	devices = DEOL;
	debugLevel = (int)GetOption("debug",0);
	eventQueue = null;
	freeEvents = null;
	initialized = false;
	finalized = false;
	gndNodes = new ArrayList();
	networkError = false;

	cintercept = GetOption("cintercept",0);
	cslope = GetOption("cslope",0);
    }

    public String Size() {
	String result = ngates+" gates ("+size+" microns^2)";

	double minSetup = MinObservedSetup();
	if (!Double.isInfinite(minSetup))
	    result += "; min observed setup ="+UI.EngineeringNotation(minSetup,3)+"s @ time="+UI.EngineeringNotation(minSetupTime,3)+" ("+minSetupDevice.name+")";

	return result;
    }

    public double NetworkSize() {
	return size*1E-12;	/* square microns */
    }

    public void NetworkError(String message) {
	if (!networkError) {
	    problem = message;
	    //System.out.println(problem);
	    networkError = true;
	}
    }

    // add an event to the queue
    public SimEvent AddEvent(double time,int type,SimNode n,int v) {
	// allocate and initialize event structure
	SimEvent e = freeEvents;
	if (e == null) e = new SimEvent(type,n,v);
	else {
	    freeEvents = (SimEvent)e.left;
	    e.Initialize(type,n,v);
	}

	eventQueue = (SimEvent)e.AddToQueue(eventQueue,time);
	if (debugLevel > 2) System.out.println("add event "+e+" time="+time);
	return e;
    }

    // remove an event from the queue
    public void RemoveEvent(SimEvent e) {
	if (debugLevel > 2) System.out.println("remove event "+e);
	eventQueue = (SimEvent)e.RemoveFromQueue(eventQueue);

	// recycle event for later use
	e.left = freeEvents;
	freeEvents = e;
    }

    // add a new device to the network
    public void AddDevice(SimDevice d,double dsize) {
	if (d != null) {
	    // don't add device to list more than once!
	    if (d.link == null) {
		d.link = devices;
		devices = d;
		size += dsize;
	    }
	}
    }

    public Object FindDevice(String name) {
	for (SimDevice d = devices; d != null; d = d.link)
	    if (d.name.equals(name)) return d;
	return null;
    }

    public Object FindNode(String name,boolean create) {
	SimNode n = (SimNode)nodes.get(name);
	if (n == null && create) {
	    n = new SimNode(name,this);
	    nodes.put(name,n);
	} else if (n != null && n.merged != null) {
	    while (n.merged != null) n = n.merged;
	    nodes.put(name,n);
	}
	return n;
    }

    public Object MakeGndNode(String name) {
	SimNode n = (SimNode)FindNode(name,true);
	if (!gndNodes.contains(n)) {
	    gndNodes.add(n);
	    ArrayList nodes = new ArrayList();
	    nodes.add(n);
	    new SimLogicDevice("gnd_power_supply",nodes,0,0,0,0,0,0,0,0,true,false,SimLookupTable.LTable);
	}
	return n;
    }

    public double GetTime() {
	return (double)time;
    }

    public boolean isAnalogSimulation() { return false; }

    // finish setting up the network after all nodes and devices
    // have been added
    public boolean Finalize() {
	return Finalize(false);
    }

    public boolean Finalize(boolean allowUndrivenNodes) {
	if (invalidDevice || networkError) return false;

	if (!finalized) {
	    // merge nodes as requested by user
	    int nmerges = mergedNodes.size();
	    for (int i = 0; i < nmerges; i += 2) {
		SimNode n1 = (SimNode)mergedNodes.get(i);
		SimNode n2 = (SimNode)mergedNodes.get(i+1);
		while (n1.merged != null) n1 = n1.merged;
		while (n2.merged != null) n2 = n2.merged;
		if (n1 != n2) n1.Merge(n2);
	    }

	    networkError = false;
	    // finalize each node
	    Iterator iter = nodes.values().iterator();
	    while (iter.hasNext()) {
		SimNode n = (SimNode)iter.next();
		if (n.merged != null) {
		    String name = n.name;
		    while (n.merged != null) n = n.merged;
		    nodes.put(name,n);
		}
		n.Finalize(allowUndrivenNodes);
	    }
	    finalized = true;
	}
	return !networkError;
    }

    // return network to initial state
    public void Initialize() {
	if (!finalized) Finalize();
	initialized = false;

	// reset queues and lists
	time = 0;
	clist = DEOL;
	plist = DEOL;

	// initialized all nodes, remove events from queues
	Iterator iter = nodes.values().iterator();
	while (iter.hasNext()) {
	    SimNode n = (SimNode)iter.next();
	    n.Reset();
	}

	// initialize all devices
	for (SimDevice d = devices; d != DEOL; d = d.link) {
	    d.Reset();
	    d.clink = null;
	    d.plink = null;
	}

	initialized = true;
    }

    // report minimum setup time seen by devices
    public double MinObservedSetup() {
	double minSetup = Double.POSITIVE_INFINITY;
	minSetupDevice = null;
	minSetupTime = -1;
	for (SimDevice d = devices; d != DEOL; d = d.link) {
	    double tsetup = d.MinObservedSetup();
	    if (tsetup < minSetup) {
		minSetup = tsetup;
		minSetupDevice = d;
		minSetupTime = d.MinObservedSetupTime();
	    }
	}
	return minSetup;
    }

    // evaluate all devices whose inputs received contamination events
    public void EvaluateC() {
	SimDevice d = clist;
	clist = DEOL;
	while (d != DEOL) {
	    SimDevice dnext = d.clink;
	    d.clink = null;
	    if (debugLevel > 1)
		System.out.println("Evaluating (c) "+d.name+" @ "+time);
	    d.EvaluateC();
	    d = dnext;
	}
    }

    // evaluate all devices whose inputs received propagation events
    public void EvaluateP() {
	SimDevice d = plist;
	plist = DEOL;
	while (d != DEOL) {
	    SimDevice dnext = d.plink;
	    d.plink = null;
	    if (debugLevel > 1)
		System.out.println("Evaluating (p) "+d.name+" @ "+time);
	    d.EvaluateP();
	    d = dnext;
	}
    }

    void RecordNodeValues() {
	Iterator iter = nodes.values().iterator();
	while (iter.hasNext()) {
	    SimNode n = (SimNode)iter.next();
	    n.RecordLogicValue(this,time,n.v);
	}
    }

    public void Simulate(double stopTime,ProgressTracker jpanel) {
	boolean interrupt = false;

	while (eventQueue != null) {
	    if (Thread.interrupted()) {
		interrupt = true;
		break;
	    }
	    Thread.yield();	// make sure other threads work too...

	    // see if we should process next event in queue
	    double nextEventTime = eventQueue.etime;
	    if (nextEventTime >= stopTime) break;
	    time = nextEventTime;
	    if (jpanel != null) jpanel.ProgressReport(this,time/stopTime);

	    // process all the events at the current time
	    while (eventQueue != null) {
		SimEvent e = eventQueue;
		if (e.etime > time) break;
		eventQueue = (SimEvent)e.RemoveFromQueue(eventQueue);

		// set node to its new value
		if (e.node.SetValue(e)) {
		    // schedule all fanout devices for evaluation.
		    if (e.type == SimEvent.CONTAMINATION)
			clist = e.node.ScheduleFanouts(clist,true);
		    else
			plist = e.node.ScheduleFanouts(plist,false);
		}

		// done with this event, recycle it
		e.left = freeEvents;
		freeEvents = e;
	    }

	    // first have devices process contamination events and
	    // keep iterating here until all 0-delay contamination
	    // events have been dealt with
	    if (clist != DEOL) {
		EvaluateC();
		if (eventQueue != null && eventQueue.etime == time) continue;
	    }

	    // now process propagation events
	    EvaluateP();
	}

	// we've reached stop time, record final node values
	if (!interrupt) time = stopTime;
	RecordNodeValues();
    }

    public boolean TransientAnalysis(double stopTime,double maxTimestep,ProgressTracker jpanel) {
	mode = TRANSIENT_ANALYSIS;
	dcLabels.clear();
	if (jpanel != null) jpanel.ProgressStart(this);
	//System.out.println("Transient Analysis: tstop="+stopTime);

	// initalize and report initial values
	Initialize();

	// do the hard work...
	Simulate(stopTime,jpanel);

	long elapsedTime = 0;
	if (jpanel != null) elapsedTime = jpanel.ProgressStop(this);
	//System.out.println("done, elapsed time "+(elapsedTime/1000)+" seconds");

	problem = null;
	nsamples = hIndex;
	return true;
    }

    public boolean DCAnalysis(String sweep1,double start1,double stop1,double step1,
			      String sweep2,double start2,double stop2,double step2,
			      ProgressTracker jpanel) {
	problem = "DC Analysis not available in Gate-level Simulation!";
	return false;
    }

    public void TimingAnalysis(java.io.PrintWriter awriter) {
	int MAXPATHS = 10;   // limit number of reported paths for each timing constraint

	// compute timing info for each node in the network
	ArrayList clocks = new ArrayList();
	ArrayList timingInfo = new ArrayList();
	Iterator iter = nodes.values().iterator();
	while (iter.hasNext()) {
	    SimNode n = (SimNode)iter.next();
	    if (n.merged != null) continue;
	    if (n.isClock() && !clocks.contains(n)) clocks.add(n);
	    try {
		TimingInfo t = n.getTimingInfo();
		if (!timingInfo.contains(t)) timingInfo.add(t);
	    }
	    catch (Exception e) {
		awriter.format("Oops, timing analysis failed: %s\n",e.getMessage());
		return;
	    }
	}

	// process worst-case combinational paths from inputs to output
	ArrayList tPD = new ArrayList();
	for (int i = 0; i < timingInfo.size(); i += 1) {
	    TimingInfo t = (TimingInfo)timingInfo.get(i);
	    // node must an output at the top level of the circuit
	    if (!t.node.isOutput() || t.node.name.indexOf('.')!=-1) continue;
	    // we'll handle clock-based timing constraints below
	    if (t.getTPDSource().isClock()) continue;
	    tPD.add(t);
	}
	if (tPD.size() > 0) {
	    // sort by tPD and print worst-case paths
	    awriter.print("\n==================================================================\n\n");
	    awriter.println("Worst-case tPDs for top-level combinational paths");
	    java.util.Collections.sort(tPD,new TPDComparator());
	    for (int i = 0; i < MAXPATHS && i < tPD.size(); i += 1) {
		TimingInfo t = (TimingInfo)tPD.get(i);
		awriter.format("\n  tPD from %1$s to %2$s (%3$5.3fns):\n\n",t.getTPDSource().name,t.node.name,t.tPDsum*1e9);
		t.printTPD(awriter,4);
	    }
	}

	// report timing constraints for each clock
	for (int iclk = 0; iclk < clocks.size(); iclk += 1) {
	    SimNode clk = (SimNode)clocks.get(iclk);

	    // look for timing constraints from clk to outputs
	    ArrayList toutput = new ArrayList();
	    for (int i = 0; i < timingInfo.size(); i += 1) {
		TimingInfo t = (TimingInfo)timingInfo.get(i);
		// node must an output at the top level of the circuit
		if (!t.node.isOutput() || t.node.name.indexOf('.')!=-1) continue;
		if (t.getTPDSource() == clk) toutput.add(t);
	    }
	    if (toutput.size() > 0) {
		// sort by tPD and print worst-case paths
		awriter.print("\n==================================================================\n\n");
		awriter.format("Worst-case tPDs from %1$s to top-level outputs:\n\n",clk.name);
		java.util.Collections.sort(toutput,new TPDComparator());
		for (int i = 0; i < MAXPATHS && i < toutput.size(); i += 1) {
		    TimingInfo t = (TimingInfo)toutput.get(i);
		    awriter.format("\n  tPD from %1$s to %2$s (%3$5.3fns):\n\n",t.getTPDSource().name,t.node.name,t.tPDsum*1e9);
		    t.printTPD(awriter,4);
		}
	    }

	    // collect timing info for this clock
	    ArrayList tclock = new ArrayList();
	    ArrayList tviolations = new ArrayList();
	    for (int i = 0; i < clk.fanouts.size(); i += 1) {
		SimDevice d = (SimDevice)clk.fanouts.get(i);
		try {
		    TimingInfo t = d.getClockInfo(clk);
		    if (t != null) {
			tclock.add(t);
			SimNode s = t.getTCDSource();
			if (t.tCDsum < 0) tviolations.add(t);
		    }
		}
		catch (Exception e) {
		}
	    }

	    // report all hold time violations
	    if (tviolations.size() > 0) {
		awriter.print("\n==================================================================\n\n");
		awriter.format("Hold time violations for %1$s:\n",clk.name);
		for (int i = 0; i < tviolations.size(); i += 1) {	
		    TimingInfo t = (TimingInfo)tviolations.get(i);
		    awriter.format("\n  tCD from %1$s to %2$s violates hold time by %3$5.3fns:\n\n",t.getTCDSource().name,t.tCDlink.node.name,t.tCDsum*1e9);
		    t.printTCD(awriter,4);
		}
	    }

	    // look at clk->clk timing contraints on cycle time
	    ArrayList tcyc = new ArrayList();
	    for (int i = 0; i < tclock.size(); i += 1) {
		TimingInfo t = (TimingInfo)tclock.get(i);
		if (t.getTPDSource() == clk) tcyc.add(t);
	    }
	    if (tcyc.size() > 0) {
		java.util.Collections.sort(tcyc,new TPDComparator());
		TimingInfo twc = (TimingInfo)tcyc.get(0);
		awriter.print("\n==================================================================\n\n");
		awriter.format("Minimum cycle time for %1$s is %2$5.3fns:\n",clk.name,twc.tPDsum*1e9);
		for (int i = 0; i < MAXPATHS && i < tcyc.size(); i += 1) {
		    TimingInfo t = (TimingInfo)tcyc.get(i);
		    awriter.format("\n  tPD from %1$s to %2$s (%3$5.3fns):\n\n",t.getTPDSource().name,t.tPDlink.node.name,t.tPDsum*1e9);
		    t.printTPD(awriter,4);
		}
	    }
	}
    }

    public boolean MakeCapacitor(String id,Object n1,Object n2,double capacitance) {
	    ((SimNode)n1).capacitance += capacitance;
	    ((SimNode)n2).capacitance += capacitance;
	    return true;
    }

    public boolean MakeIndependentVoltageSource(String id,Object npos,Object nneg,
					     double dc,double acmag,double acphase,
					     int trantype,double params[]) {
	// "neg" terminal has to be attached to ground
	if (!gndNodes.contains((SimNode)nneg)) {
	    problem = "Can't simulate voltage source with NEG terminal not gnd "+id;
	    invalidDevice = true;
	    return false;
	}

	// DC voltage sources get turned into power supplies.  Other
	// sources force their positive terminal to be marked as an
	// input and a device is created which will set the node's
	// voltage appropriately as time goes on
	SimNode n = (SimNode)npos;
	ArrayList nodes = new ArrayList();
	nodes.add(n);
	if (trantype == 0) {
	    int v = VtoL(dc);
	    SimLookupTable tbl = (v == Node.V1) ? SimLookupTable.HTable :
				 (v == Node.V0) ? SimLookupTable.LTable :
				 SimLookupTable.XTable;
		new SimLogicDevice(id+"_power_supply",nodes,0,0,0,0,0,0,0,0,true,false,tbl);
	} else {
	    SpiceSource src = SpiceSource.Allocate(dc,acmag,acphase,trantype,params,Vil,Vih);
	    if (src.SupportsGateLevelSimulation()) new SimSource(id,nodes,src);
	    else {
		problem = "Source type "+src.SourceName()+" not available in gate-level simulation!";
		invalidDevice = true;
		return false;
	    }
	}
	return true;
    }

    // estimate interconnect capacitance given number of terminals
    double InterconnectCapacitance(int nterminals) {
	return Math.max(0,cintercept + cslope*nterminals);
    }

    static double GetParameter(Parameter params,String name,double vdefault) {
	if (params == null) return vdefault;
	return params.Value(name,vdefault);
    }

    static String GetStringParameter(Parameter params,String name,String vdefault) {
	if (params == null) return vdefault;
	return params.SValue(name,vdefault);
    }

    static double[] GetVectorParameter(Parameter params,String name) {
	if (params == null) return null;
	return params.VValue(name,null);
    }

    public boolean MakeGate(String id,String function,ArrayList nodes,Parameter params) {
	double tcd = GetParameter(params,"tcd",0);
	double tpd = GetParameter(params,"tpd",0);
	double tpdr = GetParameter(params,"tpdr",tpd);
	double tpdf = GetParameter(params,"tpdf",tpd);
	double tr = GetParameter(params,"tr",0);
	double tf = GetParameter(params,"tf",0);
	double cin = GetParameter(params,"cin",0);
	double cout = GetParameter(params,"cout",0);
	double dsize = GetParameter(params,"size",0);
	boolean lenient = GetParameter(params,"lenient",1) != 0;
	boolean tristate = GetParameter(params,"tristate",0) != 0;

	SimLookupTable tbl = null;
	if (function.equalsIgnoreCase("$nand"))
	    tbl = SimLookupTable.NandTable;
	else if (function.equalsIgnoreCase("$nor"))
	    tbl = SimLookupTable.NorTable;
	else if (function.equalsIgnoreCase("$and"))
	    tbl = SimLookupTable.AndTable;
	else if (function.equalsIgnoreCase("$or"))
	    tbl = SimLookupTable.OrTable;
	else if (function.equalsIgnoreCase("$xor"))
	    tbl = SimLookupTable.XorTable;
	else if (function.equalsIgnoreCase("$xnor"))
	    tbl = SimLookupTable.Xor1Table;
	else if (function.equalsIgnoreCase("$mux2")) {
	    if (nodes.size() != 4) {
		problem = "$mux2 requires exactly four nodes to be specified";
		invalidDevice = true;
		return false;
	    }
	    tbl = SimLookupTable.Mux2Table;
	} else if (function.equalsIgnoreCase("$tristate_buffer")) {
	    if (nodes.size() != 3) {
		problem = "$tristate_buffer requires exactly three nodes to be specified";
		invalidDevice = true;
		return false;
	    }
	    tbl = SimLookupTable.TristateBufferTable;
	} else if (function.equalsIgnoreCase("$dreg")) {
	    if (nodes.size() != 3) {
		problem = "$dreg requires exactly three nodes to be specified";
		invalidDevice = true;
		return false;
	    }

	    double ts = GetParameter(params,"ts",0);
	    double th = GetParameter(params,"th",0);
	    new SimDReg(id,nodes,tcd,tpdr,tr,tpdf,tf,ts,th,cin,cout,
			dsize,lenient);
	    ngates += 1;
	    return true;
	} else if (function.equalsIgnoreCase("$dlatch")) {
	    if (nodes.size() != 3) {
		problem = "$dlatch requires exactly three nodes to be specified";
		invalidDevice = true;
		return false;
	    }

	    double ts = GetParameter(params,"ts",0);
	    double th = GetParameter(params,"th",0);
	    new SimDLatch(id,nodes,tcd,tpdr,tr,tpdf,tf,ts,th,cin,cout,
			  dsize,lenient);
	    ngates += 1;
	    return true;
	} else if (function.equalsIgnoreCase("$memory")) {
	    double ts = GetParameter(params,"ts",0);
	    double th = GetParameter(params,"th",0);
	    int width = (int)GetParameter(params,"width",0);
	    int nlocations = (int)GetParameter(params,"nlocations",0);
	    String filename = GetStringParameter(params,"file",null);
	    double[] contents = GetVectorParameter(params,"contents");

	    if (width < 1 || width > 32) {
		problem = "memory must have between 1 and 32 bits";
		invalidDevice = true;
		return false;
	    }

	    int maxlocs = 1 << SimMemory.MAXADDR;
	    if (nlocations < 1 || nlocations > maxlocs) {
		problem = "memory must have between 1 and "+maxlocs+" locations";
		invalidDevice = true;
		return false;
	    }


	    int naddr;
	    for (naddr = 1; naddr < 32; naddr += 1)
		if ((1 << naddr) >= nlocations) break;

	    int nportbits = 3 + naddr + width;
	    if ((nodes.size() % nportbits) != 0) {
		problem = "wrong number of terminals, expected multiple of "+nportbits+" (3 control, "+naddr+" address, "+width+" data)";
		invalidDevice = true;
		return false;
	    }

	    new SimMemory(id,nodes,tcd,tpdr,tr,tpdf,tf,ts,th,
			  cin,cout,width,naddr,(int)nlocations,
			  filename,contents);
	    ngates += 1;
	    return true;
	}

	if (tbl == null) {
	    problem = "Unrecognized built-in gate "+function;
	    invalidDevice = true;
	    return false;
	} else {
	    new SimLogicDevice(id,nodes,tcd,tpdr,tr,tpdf,tf,cin,cout,
			       dsize,lenient,tristate,tbl);
	    ngates += 1;
	    return true;
	}
    }

    public String SimulationType() { return "gate-level simulation"; }
}
