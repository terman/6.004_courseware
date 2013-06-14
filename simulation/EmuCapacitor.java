// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class EmuCapacitor extends EmuDevice {
    EmuNode n1;
    EmuNode n2;
    double capacitance;

    public EmuCapacitor(EmuNode n1,EmuNode n2,double capacitance) {
	super();

	this.n1 = n1;
	this.n2 = n2;
	this.capacitance = capacitance;
	n1.AddDevice(this);
	n2.AddDevice(this);
    }

    public void RemoveDevice() {
	n1.RemoveDevice(this);
	n2.RemoveDevice(this);
    }

    public double Incremental(EmuNode n,double timestep) {
	if (timestep <= 0) return 0;
	else return (capacitance/timestep)*((n == n1) ? n2.deltaV : n1.deltaV);
    }

    public void AddToRegion(EmuRegion r,EmuNode n) {
	super.AddToRegion(r,(n == n1) ? n2 : n1);
    }
}
