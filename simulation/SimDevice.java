// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

public class SimDevice {
    String name;		// name of this device
    SimDevice link;		// link in network's list of all devices
    SimDevice clink;		// link in list of devices with incoming C events
    SimDevice plink;		// link in list of devices with incoming P events
    int ninputs;		// number of inputs for this device
    int noutputs;		// number of outputs for this device
    SimNode nodes[];		// all the fanin and fanout nodes;

    public SimDevice(String name) {
	this.name = name;
	this.ninputs = 0;
	this.noutputs = 0;
	nodes = null;
    }

    public SimDevice(String name,int ninputs,int noutputs) {
	this.name = name;
	this.ninputs = ninputs;
	this.noutputs = noutputs;
	nodes = new SimNode[ninputs+noutputs];
    }

    public String toString() {
	String result = name+"[";
	for (int i = 0; i < ninputs; i += 1) {
	    if (i != 0) result += ",";
	    result += nodes[i].name;
	}
	result += ";";
	for (int i = 0; i < noutputs; i += 1) {
	    if (i != 0) result += ",";
	    result += nodes[ninputs+i].name;
	}
	return result + "]";
    }

    // change all internal references to node n1 to node n2
    public void ChangeNode(SimNode n1,SimNode n2) {
	for (int i = ninputs + noutputs - 1; i >=0; i -= 1)
	    if (nodes[i] == n1) nodes[i] = n2;
    }

    // change first instance of node n1 in output list to node n2
    public void ChangeOutputNode(SimNode n1,SimNode n2) {
	for (int i = 0; i < noutputs; i += 1)
	    if (nodes[i + ninputs] == n1) {
		nodes[i + ninputs] = n2;
		return;
	    }
    }

    // helper function which adds this device as a fanout of the
    // input nodes and a driver of the output nodes
    void SetupNodes() {
	for (int i = 0; i < ninputs; i += 1)
	    nodes[i].AddFanout(this);
	for (int i = 0; i < noutputs; i += 1)
	    nodes[i+ninputs].AddDriver(this);
    }

    // is specified node always driven to 0 by this device?
    public boolean isAlwaysZero(SimNode n) { return false; }

    // initialize state and constant nodes
    public void Reset() { }

    // report minimum observed setup time
    public double MinObservedSetup() { return Double.POSITIVE_INFINITY; }
    public double MinObservedSetupTime() { return -1; }

    // recompute values for outputs because inputs have changed
    public void EvaluateC() { }
    public void EvaluateP() { }

    // determine whether specified output can be tristated
    public boolean Tristate(SimNode n) { return false; }

    // capacitance of terminal(s) connected to this node
    public double Capacitance(SimNode n) { return 0; }

    // for timing analysis
    public boolean isSource() {	return false; }
    public boolean isPowerSupply() { return false; }
    public TimingInfo getTimingInfo(SimNode node) throws Exception {
	return new TimingInfo(node,this);
    }
    public TimingInfo getClockInfo(SimNode node) throws Exception {
	return null;
    }
}
