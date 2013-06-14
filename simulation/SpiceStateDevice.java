// Copyright (C) 2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceStateDevice extends SpiceDevice {
    SpiceNetwork network;	// we'll need some node voltages
    double value;		// device value
    double x;		// state variable (charge, flux)
    double xprime;	// dstate/dt (current, voltage)
    double previousX;	// x on last iteration
    double previousXprime;	// xprime on last iteration
    double xprimeEQ;	// equivalents for current timestep
    double geq;

    public SpiceStateDevice(SpiceNetwork net,double v,boolean eachIteration) {
	super();
	network = net;
	value = v;
	x = 0;
	xprime = 0;
	previousX = 0;
	previousXprime = 0;

	// we need to set up source vector and admittance matrix each iteration
	if (eachIteration) {
	    iterationLink = network.eachIteration;
	    network.eachIteration = this;
	}

	// update history at end of timestep
	timestepLink = network.endOfTimestep;
	network.endOfTimestep = this;
    }

    // for backward Euler: coeff0 = 1/timestep, coeff1 = 0
    // for trapezoidal: coeff0 = 2/timestep, coeff1 = 1
    void Integrate() {
	xprime = network.coeff0*(x - previousX) - network.coeff1*previousXprime;
	xprimeEQ = xprime - network.coeff0*x;
	geq = network.coeff0*value;
    }

    public void EndOfTimestep(boolean breakpoint,double timestep) {
	previousX = x;
	previousXprime = xprime;
    }
}
