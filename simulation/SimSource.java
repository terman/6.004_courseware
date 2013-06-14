// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.ArrayList;

class SimSource extends SimDevice {
    SpiceSource source;

    public SimSource(String name,ArrayList outputs,SpiceSource source) {
	super(name,1,1);
	this.source = source;

	// make output and input too, so each event triggers the
	// scheduling of the next
	nodes[0] = (SimNode)outputs.get(0);
	nodes[1] = nodes[0];
	SetupNodes();

	// add device to network
	nodes[0].network.AddDevice(this,0.0);
    }

    public void Reset() {
	EvaluateC();
	int v = nodes[0].network.VtoL(source.TransientValue(0));
	if (v != Node.VX) nodes[1].SchedulePEvent(0,v,0,false);
	else EvaluateP();
    }

    public void EvaluateC() {
	SimNetwork network = nodes[1].network;
	double next = source.NextContaminationTime(network.time);
	//System.out.println("cnext="+next+" @ "+network.time);
	if (next >= 0) nodes[1].ScheduleCEvent(next - network.time);
    }

    public void EvaluateP() {
	SimNetwork network = nodes[1].network;
	double next = source.NextPropagationTime(network.time);
	//System.out.println(name+": pnext="+next+" v="+source.TransientValue(next)+" @ "+network.time);
	int v = network.VtoL(source.TransientValue(next));
	if (next >= 0) nodes[1].SchedulePEvent(next - network.time,v,0,false);
    }

    // for timing analysis

    public boolean isSource() {	return true; }
}
