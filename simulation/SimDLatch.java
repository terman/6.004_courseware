// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.ArrayList;

public class SimDLatch extends SimDevice {
    static final int D = 0;	// node indicies
    static final int G = 1;
    static final int Q = 2;

    boolean lenient;	// input change doesn't force contamination
    double tcd;		// contamination delay from any input to output
    double tpdr;	// rising propagation delay from any input to output
    double tr;		// load dependent L->H delay (s/f)
    double tpdf;	// falling propagation delay from any input to output
    double tf;		// load dependent H->L delay (s/f)
    double ts;		// setup time
    double th;		// hold time
    double cin;		// capacitance of inputs
    double cout;	// capacitance of output

    int master;		// value we're saving
    double minSetup;	// minimum setup time we've seen
    double minSetupTime;	// time we saw minSetup

    public SimDLatch(String id,ArrayList inout,double tcd,double tpdr,
		     double tr,double tpdf,double tf,double ts,double th,
		     double cin,double cout,double dsize,boolean lenient) {
	super(id,2,1);

	this.tcd = tcd;
	this.tpdr = tpdr;
	this.tr = tr;
	this.tpdf = tpdf;
	this.tf = tf;
	this.ts = ts;
	this.th = th;
	this.cin = cin;
	this.cout = cout;
	this.lenient = lenient;

	// store input and output nodes, fix up fanout/driver lists
	nodes[D] = (SimNode)inout.get(D);
	nodes[G] = (SimNode)inout.get(G);
	nodes[Q] = (SimNode)inout.get(Q);
	SetupNodes();
	nodes[D].network.AddDevice(this,dsize);
    }

    // initialize state
    public void Reset() {
	minSetup = Double.POSITIVE_INFINITY;
	minSetupTime = -1;
	master = Node.VX;
    }

    // report minimum observed setup time
    public double MinObservedSetup() { return minSetup; }
    public double MinObservedSetupTime() { return minSetupTime; }

    // override this method if table lookup doesn't do the job
    public int ComputeOutputValue() {
	int g = nodes[G].v;
	int d = nodes[D].v;
	if (g == Node.V0) return master;
	else if (g == Node.V1 || master == d) return d;
	else return Node.VX;
    }

    // some input node has just processed a contamination event
    public void EvaluateC() {
	SimNode onode = nodes[Q];
	int v = ComputeOutputValue();

	// master follows D when G = 1
	if (nodes[G].v == Node.V1) master = v;

	// a lenient gate won't contaminate the output under the
	// right circumstances
	if (lenient) {
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

	// master follows D when G = 1
	if (nodes[G].v == Node.V1) master = v;

	if (!lenient || v != onode.v || onode.cdEvent != null || onode.pdEvent != null) {
	    double drive,tpd;
	    if (v == Node.V1) { tpd = tpdr; drive = tr; }
	    else if (v == Node.V0) { tpd = tpdf; drive = tf; }
	    else { tpd = Math.min(tpdr,tpdf); drive = 0; }
	    onode.SchedulePEvent(tpd,v,drive,lenient);
	}
    }

    public TimingInfo getTimingInfo(SimNode output) throws Exception {
	TimingInfo result = super.getTimingInfo(output);

	// add delay info for this gate
	double t1 = tpdr + tr*output.capacitance;
	double t2 = tpdf + tf*output.capacitance;
	result.setSpecs(tcd,Math.max(t1,t2));

	// loop through inputs looking for min/max paths.  Treat latch
	// like an ordinary gate for timing purposes.
	for (int i = 0; i < 2; i += 1) {
	    if (nodes[i].isPowerSupply()) continue;
	    TimingInfo t = nodes[i].getTimingInfo();
	    result.setDelays(t);
	}

	return result;
    }
}
