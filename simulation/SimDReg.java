// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.ArrayList;

public class SimDReg extends SimDevice {
    static final int D = 0;	// node indicies
    static final int CLK = 1;
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

    public SimDReg(String id,ArrayList inout,double tcd,double tpdr,
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
	nodes[CLK] = (SimNode)inout.get(CLK);
	nodes[Q] = (SimNode)inout.get(Q);
	SetupNodes();
	nodes[D].network.AddDevice(this,dsize);

	nodes[CLK].setClock();  // special treatment during timing analysis
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

    // change D regs to propagate C & D events only when clock
    // makes transition to "1".

    // recompute values for outputs because inputs have changed
    public void EvaluateP() {
	SimNode clk = nodes[CLK];
	if (clk.v == Node.V0) master = nodes[D].v;
	else if (clk.Trigger()) {
	    if (clk.v == Node.V1) {
		// track minimum setup time we see
		double now = clk.network.time;
		double tsetup = now - nodes[D].lastEvent;
		if (now > 0 && tsetup < minSetup) {
		    minSetup = tsetup;
		    minSetupTime = now;
		}
		// report setup time violations?

		if (!lenient || master != nodes[Q].v)
		    // for lenient dreg's, q output is propagated only
		    // when new output value differs from current one
		    nodes[Q].ScheduleCEvent(tcd);
		    nodes[Q].SchedulePEvent((master == Node.V0) ? tpdf : tpdr,
					    master,
					    (master == Node.V0) ? tf : tr,
					    lenient);
	    } else {
		// X on clock won't contaminate value in master if we're
		// a lenient register and master == D
		if (!lenient || master != nodes[D].v)
		    master = Node.VX;
		// send along to Q if we're not lenient or if master != Q
		if (!lenient || master != nodes[Q].v)
		    nodes[Q].SchedulePEvent(Math.min(tpdf,tpdr),Node.VX,0,lenient);
	    }
	}
    }

    public TimingInfo getTimingInfo(SimNode output) throws Exception {
	TimingInfo result = super.getTimingInfo(output);

	// add delay info for this gate
	double t1 = tpdr + tr*output.capacitance;
	double t2 = tpdf + tf*output.capacitance;
	result.setSpecs(tcd,Math.max(t1,t2));

	// timing is with respect to CLK input
	result.setDelays(nodes[CLK].getTimingInfo());

	return result;
    }

    public TimingInfo getClockInfo(SimNode clk) throws Exception {
	if (nodes[CLK] != clk || nodes[D].isPowerSupply()) return null;

	TimingInfo result = new TimingInfo(clk,this);
	result.setSpecs(-th,ts);
	result.setDelays(nodes[D].getTimingInfo());

	return result;
    }
}
