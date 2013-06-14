// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class EmuResistor extends EmuDevice {
    EmuNode n1;
    EmuNode n2;
    double conductance;
    double previousI;

    public EmuResistor(EmuNode n1,EmuNode n2,double conductance) {
	super();
	this.n1 = n1;
	this.n2 = n2;
	this.conductance = conductance;
	n1.AddDevice(this);
	n2.AddDevice(this);
    }

    public void Reset() {
	previousI = 0;
    }

    public double Incremental(EmuNode n,double timestep) {
	return conductance*((n == n1) ? n2.deltaV : n1.deltaV);
    }

    public double Update(double time) {
	double i = conductance*(n1.voltage - n2.voltage);
	double deltaI = i - previousI;
	previousI = i;
	n1.totalTransconductance += conductance;
	n2.totalTransconductance += conductance;
	n1.totalCurrent -= deltaI;
	n2.totalCurrent += deltaI;

	return EmuRegion.NO_EVENT;
    }

    public void AddToRegion(EmuRegion r,EmuNode n) {
	super.AddToRegion(r,(n == n1) ? n2 : n1);
    }
}
