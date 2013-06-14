// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.ArrayList;

public class SimNode extends Node {
    SimNetwork network;		// network we belong to
    ArrayList drivers;		// devices which want to control value of this node
    SimDevice driver;		// device which controls value of this node
    ArrayList fanouts;		// devices with this node as an input
    int v;			// logic value of this node
    SimEvent cdEvent;		// contamination delay event
    SimEvent pdEvent;		// propagation delay event
    double lastEvent;		// time of last event
    double capacitance;		// nodal capacitance
    SimNode merged;		// who we were merged with

    boolean clock;		// is node connected to clock input of state device
    TimingInfo timingInfo;	// min tCD, max tPD for this node
    boolean inProgress;		// flag to catch combinational cycles

    public SimNode(String name,SimNetwork network) {
	super(name);
	this.network = network;
	drivers = new ArrayList();
	fanouts = new ArrayList();
	capacitance = 0;
	v = Node.VX;
	merged = null;
	clock = false;
	timingInfo = null;
    }

    public String toString() {
	return name+"="+VALUES.charAt(v);
    }

    public double GetValue(Network network) {
	if (v == V0) return 0;
	else if (v == V1) return 1;
	else if (v == VZ) return Double.POSITIVE_INFINITY;
	else return Double.NaN;
    }

    public boolean SetValue(SimEvent e) {
	// update event pointers
	if (e == cdEvent) cdEvent = null;
	else if (e == pdEvent) pdEvent = null;

	if (v != e.v) {
	    // output trace if enabled
	    if (network.debugLevel > 0)
		System.out.println(name+": "+VALUES.charAt(v)+"->"+VALUES.charAt(e.v)+" @ "+network.time);

	    // keep track of event history
	    RecordLogicValue(network,network.time,v);

	    // set node to it's new value
	    v = e.v;
	    lastEvent = network.time;
	    return true;
	} else return false;
    }

    // add device to fanout list of this node
    public void AddFanout(SimDevice d) {
	// add device to fanout list
	if (!fanouts.contains(d)) fanouts.add(d);
    }

    // add device to drivers list of this node
    public void AddDriver(SimDevice d) {
	drivers.add(d);
    }

    // merge another node's info with this node
    public void Merge(SimNode n) {
	// merge driver list and update devices to point here
	int ndrivers = n.drivers.size();
	for (int i = 0; i < ndrivers; i += 1) {
	    SimDevice d = (SimDevice)n.drivers.get(i);
	    AddDriver(d);
	    d.ChangeNode(n,this);
	}
	n.drivers = null;

	// merge fanout lists and update devices to point here
	int nfanouts = n.fanouts.size();
	for (int i = 0; i < nfanouts; i += 1) {
	    SimDevice d = (SimDevice)n.fanouts.get(i);
	    AddFanout(d);
	    d.ChangeNode(n,this);
	}
	n.fanouts = null;

	n.merged = this;
    }

    public boolean isAlwaysZero() {
	if (driver != null) return driver.isAlwaysZero(this);
	else if (drivers != null && drivers.size() == 1)
	    return ((SimDevice)drivers.get(0)).isAlwaysZero(this);
	else return false;
    }

    // finalize node after all devices have been added
    public void Finalize(boolean allowUndrivenNodes) {
	if (drivers == null || driver != null) return;	// already finalized

	// if no explicit capacitance has been supplied, estimate
	// interconnect capacitance
	int ndrivers = drivers.size();
	if (ndrivers == 0) {
	    if (!allowUndrivenNodes) network.NetworkError("Node "+name+" is not connected to any output");
	    return;
	}

	int nfanouts = fanouts.size();
	if (capacitance == 0)
	    capacitance = network.InterconnectCapacitance(ndrivers+nfanouts);

	// calculate additional capacitance from fanin and fanout
	for (int i = 0; i < nfanouts; i +=1)
	    capacitance += ((SimDevice)fanouts.get(i)).Capacitance(this);

	// if there is only 1 driver and it's not a tristate output
	// then that device is the driver for this node
	if (ndrivers == 1) {
	    SimDevice d = (SimDevice)drivers.get(0);
	    if (!d.Tristate(this)) {
		driver = d;
		drivers = null;
		return;
	    }
	}
	for (int i = 0; i < ndrivers; i +=1)
	    capacitance += ((SimDevice)drivers.get(i)).Capacitance(this);

	//System.out.println("multiple drivers detected for node "+name);

	// if node has more than 1 driver or is attached to a tristate output
	// of a device, insert a BUS device into the network.  Create new
	// nodes which serve as inputs to the BUS device and for each
	// driving device convert references to this node into references
	// to the new nodes.  Then create BUS device with new nodes as inputs
	// and this node as output.
	ArrayList nlist = new ArrayList();
	for (int i = 0; i < ndrivers; i += 1) {
	    SimDevice d = (SimDevice)drivers.get(i);
	    // complain if non-tristate device outputs are shorted together
	    if (!d.Tristate(this)) {
		SimDevice d1 = (SimDevice)drivers.get(0);
		if (d1 == d) d1 = (SimDevice)drivers.get(1);
		network.NetworkError("Node "+name+" connects to more than one non-tristate output: see devices "+d.name+" and "+d1.name);
	    }
	    // new node doesn't appear in network's hashtable
	    SimNode n = new SimNode(name+"%"+nlist.size(),network);
	    n.capacitance = capacitance;
	    nlist.add(n);
	    d.ChangeOutputNode(this,n);
	    n.driver = d;
	}
	//System.out.println("Adding bus driver for node "+name);
	nlist.add(this);	// output node
	driver = new SimLogicDevice(name+"%driver",nlist,0,0,0,0,0,0,0,0,true,false,SimLookupTable.BusTable);
	drivers = null;		// don't get finalized twice!
    }

    // return to initial state
    public void Reset() {
	v = (driver == null) ? VZ : VX;
	lastEvent = -1;
	if (cdEvent != null) {
	    network.RemoveEvent(cdEvent);
	    cdEvent = null;
	}
	if (pdEvent != null) {
	    network.RemoveEvent(pdEvent);
	    pdEvent = null;
	}
    }

    // used by devices to check if this was the node that caused
    // them to be evaluated...
    public boolean Trigger() {
	return lastEvent == network.time;
    }

    // add all our fanout devices to network's device list
    public SimDevice ScheduleFanouts(SimDevice dlist,boolean clink) {
	int fsize = fanouts.size();
	for (int i = 0; i < fsize; i +=1) {
	    SimDevice d = (SimDevice)fanouts.get(i);
	    if (clink) {
		if (d.clink == null) { d.clink = dlist; dlist = d; }
	    } else {
		if (d.plink == null) { d.plink = dlist; dlist = d; }
	    }
	}
	return dlist;
    }
	

    // schedule contamination event
    public void ScheduleCEvent(double tcd) {
	double cdtime = network.time + tcd;

	if (network.debugLevel > 2)
	    System.out.println("schedule C event "+name+" @ "+cdtime);

	// remove any pending propagation event that happens after tcd
	if (pdEvent != null && pdEvent.etime >= cdtime) {
	    //System.out.println(name+": remove event "+pdEvent);
	    network.RemoveEvent(pdEvent);
	    pdEvent = null;
	}

	// if we've already scheduled a contamination event for an earlier
	// time, make the conservative assumption that node will become
	// contaminated at the earlier possible time, i.e., keep the
	// earlier of the two contamination events
	if (cdEvent != null) {
	    if (cdEvent.etime <= cdtime) return;
	    //System.out.println(name+": remove event "+cdEvent);
	    network.RemoveEvent(cdEvent);
	    cdEvent = null;
	}

	cdEvent = network.AddEvent(cdtime,SimEvent.CONTAMINATION,this,VX);
    }

    // schedule propagation event
    public void SchedulePEvent(double tpd,int v,double drive,boolean lenient) {
	double pdtime = network.time + tpd + drive*capacitance;

	//System.out.println(name+": tpd="+tpd+", drive="+drive+", c="+capacitance+", pd="+pdtime);

	if (network.debugLevel > 2)
	    System.out.println("schedule P event "+name+"->"+VALUES.charAt(v)+" @ "+pdtime);

	if (pdEvent != null) {
	    if (lenient && pdEvent.v == v && pdtime >= pdEvent.etime) return;
	    network.RemoveEvent(pdEvent);
	    pdEvent = null;
	}

	pdEvent = network.AddEvent(pdtime,SimEvent.PROPAGATION,this,v);
    }

    // for timing analysis

    public boolean isInput() {
	return driver == null || driver.isSource();
    }

    public boolean isPowerSupply() {
	return driver != null && driver.isPowerSupply();
    }

    public boolean isOutput() {
	return fanouts.size() == 0 && driver != null && !driver.isPowerSupply();
    }

    public void setClock() {
	clock = true;
    }

    public boolean isClock() {
	return clock;
    }

    // return min tCD, max tPD for this node
    public TimingInfo getTimingInfo() throws Exception {
	if (timingInfo == null) {
	    if (driver == null) timingInfo = new TimingInfo(this,null);
	    else {
		if (inProgress)
		    throw new Exception("combinational cycle detected at node "+name);
		else inProgress = true;
		timingInfo = driver.getTimingInfo(this);
		inProgress = false;
	    }
	}
	return timingInfo;
    }
}
