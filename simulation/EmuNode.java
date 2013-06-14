// Copyright (C) 1999-2001 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.ArrayList;
import netlist.PlotRequest;

class EmuNode extends Node {
    EmuRegion region;		// region we belong to
    EmuNode regionLink;		// next node in region's node list
    ArrayList fanoutRegions;	// regions we affect
    double lastFanoutVoltage;	// last "event" voltage
    ArrayList devices;		// devices we connect to
    double capacitance;
    double voltage;
    double deltaV;
    double lastDeltaV;
    double totalCurrent;
    double totalTransconductance;
    boolean powerSupply;
    SpiceSource source;
    PlotRequest plotRequest;

    public EmuNode(String name) {
	super(name);
	region = null;
	devices = new ArrayList();
	fanoutRegions = new ArrayList();
	voltage = 0.0;
	capacitance = 0.0;
	powerSupply = false;
	source = null;
	plotRequest = null;
    }

    // turn node into a power supply
    public void PowerSupply(double voltage) {
	powerSupply = true;
	this.voltage = voltage;
	deltaV = 0;
	lastFanoutVoltage = voltage;
	region = EmuRegion.CONSTANT_REGION;

	// remove any capacitors connected to this node from
	// the network -- they now behave as if they were
	// capacitors to gnd, ie, we account for the effect
	// in the nodal capacitance for each terminal.
	for (int i = devices.size() - 1; i >= 0; i -= 1) {
	    EmuDevice d = (EmuDevice)devices.get(i);
	    if (d instanceof EmuCapacitor)
		((EmuCapacitor)d).RemoveDevice();
	}
    }

    // turn node into an input
    public void VoltageSource(SpiceSource source) {
	this.source = source;
    }

    public double GetValue(Network network) {
	return voltage;
    }

    public void AddDevice(EmuDevice d) {
	if (!powerSupply)
	    devices.add(d);
    }

    public void RemoveDevice(EmuDevice d) {
	devices.remove(d);
    }

    public void AddFanoutRegion(EmuRegion r) {
	if (!fanoutRegions.contains(r))
	    fanoutRegions.add(r);
    }

    public void AddToRegion(EmuRegion r) {
	EmuNetwork network = r.network;
	if (capacitance < network.minCapacitance)
	    capacitance = network.minCapacitance;
	capacitance *= network.cAdjust;
	region = r;
	int ndevices = devices.size();
	for (int i = 0; i < ndevices; i += 1) {
	    EmuDevice d = (EmuDevice)devices.get(i);
	    if (d.region == null) d.AddToRegion(r,this);
	}
    }

    public void Reset() {
	if (!powerSupply) {
	    double v = (source == null) ? 0 : source.TransientValue(0);
	    voltage = v;
	    deltaV = 0;
	    lastFanoutVoltage = v;
	    lastDeltaV = 0;
	}
	totalCurrent = 0;
	totalTransconductance = 0;
   }

    // compute how much the voltage will change over specified timestep
    public boolean ComputeUpdate(double timestep) {
	boolean converged = true;

	// input nodes get special treatment
	if (source != null) {
	    double v = source.TransientValue(region.network.time);
	    deltaV = v - voltage;
 	} else {
	    // update current flowing into this node
	    double i = totalCurrent;
	    int ndevices = devices.size();
	    for (int j = 0; j < ndevices; j += 1) {
		EmuDevice d = (EmuDevice)devices.get(j);
		i += d.Incremental(this,timestep);
	    }

	    // no current => no voltage change
	    if (i == 0) {
		deltaV = 0;
		lastDeltaV = 0;
		return true;
	    }

	    // timestep <= 0 indicates open capacitors
	    if (timestep <= 0)
		deltaV = i/totalTransconductance;
	    else
		deltaV = i/((capacitance/timestep) + totalTransconductance);

	    // limit change in voltage at each iteration
	    double dvLimit = region.network.dvLimit;
	    double dv = deltaV - lastDeltaV;
	    if (dv  > dvLimit) {
		deltaV = lastDeltaV + dvLimit;
		converged = false;
	    } else if (dv < -dvLimit) {
		deltaV = lastDeltaV - dvLimit;
		converged = false;
	    }
	}

	// check for convergence
	if (Math.abs(deltaV) > region.network.absTol &&
		   Math.abs((deltaV - lastDeltaV)/deltaV) > region.network.relTol)
	    converged = false;
	lastDeltaV = deltaV;

	return converged;
    }

    public void Snapshot() {
	EmuNetwork network = region.network;
	RecordValue(network,network.time,voltage);
    }

    // update voltage according to dictates of ComputeUpdate
    public void PerformUpdate() {
	voltage += deltaV;

	// gak!
	if (voltage > 6) {
	    voltage = 6;
	    //System.out.println(name+": cap="+capacitance+"deltaV="+deltaV+" totalCurrent="+totalCurrent+" totalG="+totalTransconductance);
	} else if (voltage < -1) voltage = -1;

	deltaV = 0;
	totalTransconductance = 0;

	EmuNetwork network = region.network;
	if (network.time > 0) RecordValue(network,network.time,voltage);
    }

    // predict when next event will happen based on node's capacitance,
    // how much delta V we're looking for and the total current flowing
    // in/out of the node
    public double PredictTimestep() {
	if (source != null) {
	    double time = region.network.time;
	    double bp = source.NextBreakpoint(time,region.network.eventThreshold);
	    if (bp == -1) return EmuRegion.NO_EVENT;
	    else return Math.max(region.network.minTimestep,bp - time);
	} else if (totalCurrent == 0)
	    return EmuRegion.NO_EVENT;
	else {
	    double t = (capacitance * region.network.eventThreshold) /
			    Math.abs(totalCurrent);
	    return Math.max(region.network.minTimestep,t);
	}
    }

    // if our new voltage has changed enough since the last time we
    // processed the fanout regions, we have to bring them up to date
    public EmuRegion CheckFanouts(double time,EmuRegion link) {
	if (fanoutRegions.size() > 0) {
	    double newV = voltage + deltaV;
	    if (Math.abs(newV - lastFanoutVoltage) >= region.network.eventThreshold) {
		lastFanoutVoltage = newV;
		int nfanouts = fanoutRegions.size();
		for (int i = 0; i < nfanouts; i += 1) {
		    EmuRegion r = (EmuRegion)fanoutRegions.get(i);
		    // update region if it hasn't been processed yet
		    if (r.link == null) link = r.ComputeUpdate(time,link);
		}
	    }
	}

	return link;
    }
}
