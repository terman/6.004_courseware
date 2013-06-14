// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceInductor extends SpiceStateDevice {
    int i,j;			// two terminal nodes
    SpiceCell sourceCell;	// our entry in the source vector
    int k;			// matrix row for branch current
    SpiceCell kk;		// where we'll put Req.

    public SpiceInductor(SpiceNetwork net,int n1,int n2,double l) {
	super(net,l,true);
	i = n1;
	j = n2;

	k = network.size++;
	network.FindMatrixElement(i,k).gExp = 1;
	network.FindMatrixElement(j,k).gExp = -1;
	network.FindMatrixElement(k,i).gExp = 1;
	network.FindMatrixElement(k,j).gExp = -1;
	sourceCell = network.FindSourceElement(k);
	kk = network.FindMatrixElement(k,k);
    }

    public boolean EachIteration(int mode,double time,double timestep) {
	x = value * network.solution[k];	// set flux

	if (mode == SpiceNetwork.TRANSIENT_ANALYSIS) {
	    Integrate();
	    kk.luExp = -geq;
	    sourceCell.luExp = xprimeEQ;
	}

	return true;
    }
}
