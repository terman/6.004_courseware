// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.ArrayList;

public class SimLogicDevice extends SimDevice {
    boolean lenient;	// input change doesn't force contamination
    boolean tristate;	// output can be tristated
    double tcd;		// contamination delay from any input to output
    double tpdr;	// rising propagation delay from any input to output
    double tr;		// load dependent L->H delay (s/f)
    double tpdf;	// falling propagation delay from any input to output
    double tf;		// load dependent H->L delay (s/f)
    double cin;		// capacitance of inputs
    double cout;	// capacitance of output
    SimLookupTable tbl;
    

    public SimLogicDevice(String name,ArrayList inout,double tcd,double tpdr,double tr,double tpdf,double tf,double cin,double cout,double size,boolean lenient,boolean tristate,SimLookupTable tbl) {
	super(name,inout.size()-1,1);

	// store input and output nodes, fix up fanout/driver lists
	for (int i = 0; i <= ninputs; i += 1)
	    nodes[i] = (SimNode)inout.get(i);
	SetupNodes();

	nodes[0].network.AddDevice(this,size);
	this.tcd = tcd;
	this.tpdr = tpdr;
	this.tr = tr;
	this.tpdf = tpdf;
	this.tf = tf;
	this.cin = cin;
	this.cout = cout;
	this.lenient = lenient;
	this.tristate = tristate;
	this.tbl = tbl;
    }

    // override this method if table lookup doesn't do the job
    public int ComputeOutputValue() {
	SimLookupTable t = tbl;
	if (t == null) return Node.VX;

	for (int i = 0; i < ninputs; i += 1)
	    t = t.table[nodes[i].v];
	return t.value;
    }

    // check for constant generators
    public void Reset() {
	int v = ComputeOutputValue();
	if (v == Node.V0 || v == Node.V1) {
	    nodes[ninputs].ScheduleCEvent(tcd);
	    nodes[ninputs].SchedulePEvent((v == Node.V0) ? tpdf : tpdr,
					  v,
					  (v == Node.V0) ? tf : tr,
					  false);
	}
    }

    // is specified node always driven to 0 by this device?
    public boolean isAlwaysZero(SimNode n) {
	return ninputs == 0 && tbl.value==Node.V0;
    }

    // some input node has just processed a contamination event
    public void EvaluateC() {
	SimNode onode = nodes[ninputs];

	// a lenient gate won't contaminate the output under the
	// right circumstances
	if (lenient) {
	    int v = ComputeOutputValue();
	    if (onode.pdEvent == null) {
		// no events pending and current value is same as new value
		if (onode.cdEvent == null && v == onode.v) return;
	    } else {
		// node is destined to have the same value as new value
		if (v == onode.pdEvent.v) return;
	    }
	}

	// schedule contamination event with specified delay
	onode.ScheduleCEvent(tcd);
    }

    // some input node has just processed a propagation event
    public void EvaluateP() {
	SimNode onode = nodes[ninputs];
	int v = ComputeOutputValue();
	if (!lenient || v != onode.v || onode.cdEvent != null || onode.pdEvent != null) {
	    double drive,tpd;
	    if (v == Node.V1) { tpd = tpdr; drive = tr; }
	    else if (v == Node.V0) { tpd = tpdf; drive = tf; }
	    else { tpd = Math.min(tpdr,tpdf); drive = 0; }
	    onode.SchedulePEvent(tpd,v,drive,lenient);
	}
    }

    // determine whether specified output can be tristated
    public boolean Tristate(SimNode n) { return tristate; }

    // capacitance of terminal(s) connected to this node
    public double Capacitance(SimNode n) {
	double c = 0;
	for (int i = 0; i < ninputs; i += 1)
	    if (nodes[i] == n) c += cin;
	if (nodes[ninputs] == n) c += cout;
	return c;
    }

    // for timing analysis
    public boolean isPowerSupply() {
	return name.endsWith("_power_supply");
    }

    public TimingInfo getTimingInfo(SimNode output) throws Exception {
	TimingInfo result = super.getTimingInfo(output);

	// add delay info for this gate
	double t1 = tpdr + tr*output.capacitance;
	double t2 = tpdf + tf*output.capacitance;
	result.setSpecs(tcd,Math.max(t1,t2));

	// loop through inputs looking for min/max paths
	for (int i = 0; i < ninputs; i += 1) {
	    if (nodes[i].isPowerSupply()) continue;
	    TimingInfo t = nodes[i].getTimingInfo();
	    result.setDelays(t);
	}

	return result;
    }
}
