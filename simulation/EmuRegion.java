// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

import netlist.PlotRequest;

class EmuRegion extends Event {
    static final EmuRegion CONSTANT_REGION = new EmuRegion(null);
    static final EmuRegion LAST_REGION = new EmuRegion(null);

    EmuNetwork network;		// network we belong to
    EmuRegion netLink;		// next region in network
    EmuNode nodes;		// DC-connected nodes
    EmuDevice devices;		// device belonging to the region
    double lastUpdateTime;
    EmuRegion link;		// next region in linked list

    public EmuRegion(EmuNetwork network) {
	super();
	this.network = network;
	nodes = null;
	devices = null;
	link = null;
    }

    public EmuRegion(EmuNetwork network,EmuNode n) {
	this(network);
	AddNode(n);
    }

    public String toString() {
	String result = "EmuRegion[";
	int i = 0;
	for (EmuNode n = nodes; n != null; n = n.regionLink) {
	    if (i != 0) result += ",";
	    result += n.name + "=" + n.voltage;
	    if (++i > 5) break;
	}
	return result+"]";
    }

    public void AddNode(EmuNode n) {
	if (n.region == null) {
	    n.regionLink = nodes;
	    nodes = n;
	    n.AddToRegion(this);
	}
    }

    public void AddDevice(EmuDevice d,EmuNode n) {
	d.regionLink = devices;
	devices = d;
	AddNode(n);
    }

    // finalize each device, set fanout links
    public void Finalize() {
	for (EmuDevice d = devices; d != null; d = d.regionLink)
	    d.Finalize();
    }

    public void ResetTime(double delta) {
	if (etime != 0) etime -= delta;
	lastUpdateTime = 0;
    }

    public void ResetState() {
	etime = NO_EVENT;
	lastUpdateTime = 0;
	for (EmuDevice d = devices; d != null; d = d.regionLink) d.Reset();
	for (EmuNode n = nodes; n != null; n = n.regionLink) n.Reset();
    }

    // compute what next voltage will be for all the nodes in
    // the region.  If the new voltage would trigger an event
    // for a node, update all its fanout regions, and so on.
    public EmuRegion ComputeUpdate(double time,EmuRegion link) {
	// add region to linked list of visited regions.  Filling in the
	// link marks this region as having already been processed...
	this.link = link;
	link = this;

	// if we're not up-to-date, iterate through each node in region
	// updating it's voltage and currents.  Iteration stops when
	// values converge or if we exceed alloted number of iterations.
	double timestep = time - lastUpdateTime;
	if (timestep != 0) {
	    if (network.debugLevel > 1)
		System.out.println("Update t="+time+", "+this);

	    int iterations = network.maxIterations;
	    boolean converged = false;
	    while (!converged && iterations > 0) {
		converged = true;
		// update each node in the region
		for (EmuNode n = nodes; n != null; n = n.regionLink)
		    if (!n.ComputeUpdate(timestep)) converged = false;
		iterations -= 1;
	    }
	    //if (!converged) System.out.println("No convergence at time "+time+" for "+this);
	}

	// remember that we did this update and check to see if any
	// fanout regions need updating too
	lastUpdateTime = time;
	for (EmuNode n = nodes; n != null; n = n.regionLink)
	    link = n.CheckFanouts(time,link);

	return link;
    }

    public void Snapshot() {
	for (EmuNode n = nodes; n != null; n = n.regionLink)
	    n.Snapshot();
    }

    // update node voltages
    public void PerformUpdate() {
	for (EmuNode n = nodes; n != null; n = n.regionLink)
	    n.PerformUpdate();
    }

    public void ForcedUpdate(double t) {
	for (EmuNode n = nodes; n != null; n = n.regionLink)
	    n.PerformUpdate();
	for (EmuDevice d = devices; d != null; d = d.regionLink)
	    d.Update(t);
	lastUpdateTime = t;
	link = null;
    }

    // determine when to schedule next event for this region
    public void ScheduleNextUpdate(double time) {
	double timestep = NO_EVENT;

	// update all devices and accumulate min timestep they require
	for (EmuDevice d = devices; d != null; d = d.regionLink) {
	    double t = d.Update(time);
	    if (t != NO_EVENT && (timestep == NO_EVENT || t < timestep))
		timestep = t;
	}

	// predict appropriate timestep for each node in region
	for (EmuNode n = nodes; n != null; n = n.regionLink) {
	    double t = n.PredictTimestep();
	    if (t != NO_EVENT && (timestep == NO_EVENT || t < timestep))
		timestep = t;
	}

	if (timestep != NO_EVENT) network.AddEvent(this,time + timestep);
	else if (etime != NO_EVENT) network.DeleteEvent(this);
    }
}
