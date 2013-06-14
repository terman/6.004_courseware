// Copyright (C) 1999-2008 Christopher J. Terman - All Rights Reserved.

package simulation;

import gui.ProgressTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import netlist.NetlistConsumer;

public class EmuNetwork extends Network {
    HashMap devices;		// maps name => EmuDevice
    int nfets;			// number of fets
    HashMap models;		// names to models
    EmuRegion regions;
    EmuMeter meters;
    ArrayList emuModels;	// models we've constructed

    EmuRegion eventQueue;	// "leftist tree" of pending events

    double temperature;		// simulation temperature
    double cAdjust;		// capacitance scale factor
    double scale;
    double defas;
    double defps;
    double defad;
    double defpd;
    double Vih;			// input/output voltage references
    double Vil;			// input/output voltage references
    double Voh;			// input/output voltage references
    double Vol;			// input/output voltage references
    double relTol;
    double absTol;
    double eventThreshold;
    double minTimestep;
    double dvLimit;
    double minCapacitance;
    int maxIterations;
    double psmax;		// largest power supply voltage we've seen
    int debugLevel;		// how much debugging printout to generate

    public EmuNetwork(HashMap options,String tempdir) {
	super(options,tempdir);

	devices = new HashMap();
	nfets = 0;
	models = new HashMap();
	eventQueue = null;
	meters = null;
	regions = null;
	psmax = 0;
	emuModels = new ArrayList();

	// set up parameters
	temperature = GetOption(".temp",25);
	cAdjust = GetOption("cadjust",1.0);
	scale = GetOption("scale",1.0);
	defas = GetOption("defas",0.0);
	defps = GetOption("defps",0.0);
	defad = GetOption("defad",0.0);
	defpd = GetOption("defpd",0.0);

	Vil = GetOption("vil",1.5);
	Vih = GetOption("vih",3.5);
	Voh = GetOption("voh",5.0);
	Vol = GetOption("vol",0.0);
	relTol = GetOption("reltol",0.05);
	absTol = GetOption("abstol",0.001);
	eventThreshold = GetOption("eventthreshold",0.25);
	minTimestep = GetOption("mintimestep",1e-16);
	maxIterations = (int)GetOption("maxiterations",50);
	minCapacitance = GetOption("mincapacitance",1e-15);
	dvLimit = GetOption("dvlimit",1);
	debugLevel = (int)GetOption("debug",0);
    }

    public String Size() {
	return nfets+" mosfets";
    }

    public double NetworkSize() {
	return nfets;
    }

    void ResetTime() {
	double oldTime = time;

	ResetHistory();
	time = 0;
	for (EmuRegion r = regions; r != null; r = r.netLink)
	    r.ResetTime(oldTime);
	for (EmuMeter m = meters; m != null; m = m.netLink)
	    m.Reset();
    }

    void ResetState() {
	time = 0;
	eventQueue = null;
	for (EmuRegion r = regions; r != null; r = r.netLink)
	    r.ResetState();
	for (EmuMeter m = meters; m != null; m = m.netLink)
	    m.ResetState();
    }

    // bring all the regions up to the specified time.  The
    // time appears as two parameters since we play a trick
    // during initialization of reseting the time to 0 after
    // each update.
    void UpdateRegions(double t1,double t2) {
	for (EmuRegion r = regions; r != null; r = r.netLink)
	    if (r.link == null)
		r.ComputeUpdate(t1,EmuRegion.LAST_REGION);
	for (EmuRegion r = regions; r != null; r = r.netLink)
	    r.ForcedUpdate(t2);
    }

    void Initialize() {
	if (debugLevel > 0) System.out.println("Initializing...");

	ResetState();

	// see if we can relax our way to an operating point
	for (int iter = 50; iter > 0; iter -= 1)
	    UpdateRegions(-1,0);

	// now see when first "real" event should be scheduled
	for (EmuRegion r = regions; r != null; r = r.netLink) {
	    r.ScheduleNextUpdate(0);
	    r.Snapshot();		// record initial values
	}

	if (debugLevel > 0) System.out.println("Initialization complete");
    }

    void ProcessEvent(EmuRegion r) {
	EmuRegion visitedRegions;

	// compute what next voltage will be for all the nodes in
	// the region.  If the new voltage would trigger an event
	// for a node, update all its fanout regions, and so on.
	// We'll get back a linked list of the regions that were
	// updated.  Note that none of the voltages will be changed
	// until all affected regions have been updated.
	visitedRegions = r.ComputeUpdate(time,EmuRegion.LAST_REGION);

	// actually update the voltages in each region
	for (r = visitedRegions; r != EmuRegion.LAST_REGION; r = r.link)
	    r.PerformUpdate();

	// using the new voltages, figure out when each region should be
	// visited again
	for (r = visitedRegions; r != EmuRegion.LAST_REGION; r = r.link)
	    r.ScheduleNextUpdate(time);

	// finally, clean up the linked list (an empty link is used to
	// indicate that the region still needs processing, so we need
	// to leave a clean slate for next time).
	EmuRegion next;
	for (r = visitedRegions; r != EmuRegion.LAST_REGION; r = next) {
	    next = r.link;
	    r.link = null;
	}
    }

    void DeleteEvent(EmuRegion e) {
	eventQueue = (EmuRegion)e.RemoveFromQueue(eventQueue);
    }

    void AddEvent(EmuRegion e,double t) {
	eventQueue = (EmuRegion)e.AddToQueue(eventQueue,t);
    }

    void SimStep(double delta,ProgressTracker jpanel) {
	double stopTime = time + delta;
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

	    EmuRegion sourceRegion = eventQueue;
	    DeleteEvent(sourceRegion);
	    EmuMeter.UpdateMeters(meters,nextEventTime,nextEventTime - time);

	    // process the event
	    time = nextEventTime;
	    if (debugLevel > 0)
		System.out.println("processing event for "+sourceRegion);
	    ProcessEvent(sourceRegion);

	    if (jpanel != null) jpanel.ProgressReport(this,time/stopTime);
	}

	// see if there's still a bit time before we reach stopTime
	// if so, update meters appropriately
	if (!interrupt && time < stopTime) {
	    EmuMeter.UpdateMeters(meters,stopTime,stopTime - time);
	    time = stopTime;
	}

	// update all regions to the current time
	for (EmuRegion r = regions; r != null; r = r.netLink)
	    if (r.lastUpdateTime != time) ProcessEvent(r);
    }

    public EmuMOSModel MakeEmuMOSModel(SpiceMOSModel m,double w,double l) {
	int nmodels = emuModels.size();
	for (int i = 0; i < nmodels; i += 1) {
	    EmuMOSModel model = (EmuMOSModel)emuModels.get(i);
	    if (model.Match(m,w,l)) return model;
	}

	// have to make a new one!
	EmuMOSModel model = new EmuMOSModel(m,w,l,psmax);
	//System.out.println("built model for w="+w+" l="+l+" psmax="+psmax);
	emuModels.add(model);
	return model;
    }

    public double GetTime() {
	return time;
    }

    // Simulator interface

    public boolean Finalize() {
	if (invalidDevice) return false;

	if (mergedNodes.size() != 0) {
	    problem = "Can't use .connect in fast transient analysis";
	    return false;
	}

	// assign a region to each node
	// here's how it works:
	//    EmuRegion(net,node) calls
	//    EmuRegion.AddNode(node) which calls
	//    EmuNode.AddToRegion(region) which calls 
	//    EmuDevice.AddToRegion(region,node) for each device, which calls
	//    EmuRegion.AddDevice(device,xnode) for other connected nodes, which calls
	//    EmuRegion.AddNode(xnode), etc.
	// stopping the recursion at nodes/devices which have already
	// been assigned a region
	Iterator iter = nodes.values().iterator();
	while (iter.hasNext()) {
	    EmuNode n = (EmuNode)iter.next();
	    if (n.region == null) {
		EmuRegion r = new EmuRegion(this,n);
		r.netLink = regions;
		regions = r;
	    }
	}

	// finalize each region
	// which in turn finalizes each device
	// which in turn adds any fanout links
	for (EmuRegion r = regions; r != null; r = r.netLink)
	    r.Finalize();

	return true;
    }

    public boolean TransientAnalysis(double stopTime,double maxTimestep,ProgressTracker jpanel) {
	mode = TRANSIENT_ANALYSIS;
	dcLabels.clear();
	if (jpanel != null) jpanel.ProgressStart(this);
	//System.out.println("Transient Analysis: tstop="+stopTime);

	// initalize and report initial value
	Initialize();

	// do the hard work...
	SimStep(stopTime,jpanel);

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
	mode = DC_ANALYSIS;
	problem = "DC Analysis not available in Fast Transient Analysis Simulation!";
	return false;
    }

    // NetlistConsumer interface

    public Object MakeModel(String name,int mtype,HashMap options) {
	SpiceModel m = (SpiceModel)models.get(name);

	if (m == null) {
	    switch (mtype) {
	    case NetlistConsumer.NMOS:
	    case NetlistConsumer.PMOS:
		Double Level = (Double)options.get("level");
		int level = (Level == null) ? 1 : Level.intValue();
		if (level == 3)
		    m = new SpiceMOSModel_L3(name,mtype,options,temperature);
		else
		    m = new SpiceMOSModel_L1(name,mtype,options,temperature);
		break;
	    default:
		m = new SpiceModel(name,options);
		break;
	    }
	    models.put(name,m);
	} else System.out.println("Duplicate MakeModel for "+name);
	return m;
    }

    public Object FindNode(String name,boolean create) {
	EmuNode n = (EmuNode)nodes.get(name);

	if (n == null && create) {
	    n = new EmuNode(name);
	    nodes.put(name,n);
	}
	return n;
    }

    public Object MakeGndNode(String name) {
	EmuNode n = (EmuNode)FindNode(name,true);
	n.PowerSupply(0);
	return n;
    }

    public Object FindDevice(String name) {
	return devices.get(name);
    }

    public boolean MakeResistor(String id,Object n1,Object n2,double resistance) {
	EmuDevice d = new EmuResistor((EmuNode)n1,(EmuNode)n2,1/resistance);
	devices.put(id,d);
	return true;
    }

    public boolean MakeCapacitor(String id,Object n1,Object n2,double capacitance) {
	EmuNode node1 = (EmuNode)n1;
	EmuNode node2 = (EmuNode)n2;

	node1.capacitance += capacitance;
	node2.capacitance += capacitance;
	if (!node1.powerSupply && !node2.powerSupply) {
	    EmuDevice d = new EmuCapacitor(node1,node2,capacitance);
	    devices.put(id,d);
	}
	return true;
    }

    public boolean MakeMosfet(String id,Object d,Object g,Object s,Object b,
			      Object model,double l,double w,double sl,double sw,
			      double ad,double pd,double nrd,double rdc,
			      double as,double ps,double nrs,double rsc) {
	// compute effective channel size
	if (l == 0) l = sl * scale;
	if (w == 0) w = sw * scale;

	// default missing geometric/electrical parameters
	if (as == 0) as = defas;
	if (ps == 0) ps = defps;
	if (ad == 0) ad = defad;
	if (pd == 0) pd = defpd;

	EmuDevice fet = new EmuMosfet((EmuNode)d,(EmuNode)g,(EmuNode)s,(EmuNode)b,
				      (SpiceMOSModel)model,l,w,ad,pd,as,ps);
	devices.put(id,fet);
	nfets += 1;
	return false;
    }

    public boolean MakeIndependentVoltageSource(String id,Object npos,Object nneg,
						double dc,double acmag,double acphase,
						int trantype,double params[]) {
	// "neg" terminal has to be attached to ground
	EmuNode reference = (EmuNode)nneg;
	if (!reference.powerSupply || reference.voltage != 0) {
	    problem = "Can't simulate voltage source with NEG terminal not gnd "+id;
	    invalidDevice = true;
	    return false;
	}

	// DC voltage sources get turned into power supplies.  Other
	// sources force their positive terminal to be marked as an
	// input and a device is created which will set the node's
	// voltage appropriately as time goes on
	EmuNode n = (EmuNode)npos;
	if (n.source != null) {
	    // only allow one voltage source per node
	    problem = "Can't simulate multiple voltage sources per node "+id;
	    invalidDevice = true;
	    return false;
	} else if (trantype == 0) {
	    n.PowerSupply(dc);
	    psmax = Math.max(psmax,dc);
	} else n.VoltageSource(SpiceSource.Allocate(dc,acmag,acphase,trantype,params,Vil,Vih));
	return true;
    }

    public String SimulationType() { return "fast transient analysis"; }
}
