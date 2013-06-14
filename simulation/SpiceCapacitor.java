// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceCapacitor extends SpiceStateDevice {
    int i,j;			// two terminal nodes

    // capacitor is modelled as a current source in parallel with an resistor
    SpiceCell sourceI,sourceJ;	// elements from source vector
    SpiceCell ii,ij,ji,jj;	// elements from admittance matrix

    public SpiceCapacitor(SpiceNetwork net,int n1,int n2,double c) {
	super(net,c,true);
	i = n1;
	j = n2;

	sourceI = network.FindSourceElement(n1);
	sourceJ = network.FindSourceElement(n2);
	ii = network.FindMatrixElement(n1,n1);
	ij = network.FindMatrixElement(n1,n2);
	ji = network.FindMatrixElement(n2,n1);
	jj = network.FindMatrixElement(n2,n2);
    }

    public boolean EachIteration(int mode,double time,double timestep) {
	double vcap = ((i < 0) ? 0 : network.solution[i]) -
	    ((j < 0) ? 0 : network.solution[j]);

	x = value * vcap;		// set charge

	if (mode == SpiceNetwork.TRANSIENT_ANALYSIS) {
	    Integrate();

	    sourceI.luExp -= xprimeEQ;
	    sourceJ.luExp += xprimeEQ;
	    ii.luExp += geq;
	    ij.luExp -= geq;
	    ji.luExp -= geq;
	    jj.luExp += geq;
	}

	return true;
    }
}
