// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceCCCS extends SpiceDependentSource {
    public SpiceCCCS(SpiceNetwork network,int pos,int neg,int cpos,int cneg,double g) {
	super(pos,neg,cpos,cneg,g);

	int branch = network.size++;

	// cccs can be handled completely by updating constants used
	// used to initialize admittance matrix on each iteration
	network.FindMatrixElement(j1,branch).gExp = 1;
	network.FindMatrixElement(j2,branch).gExp = -1;
	network.FindMatrixElement(branch,j1).gExp = 1;
	network.FindMatrixElement(branch,j2).gExp = -1;
	network.FindMatrixElement(k1,branch).gExp = g;
	network.FindMatrixElement(k2,branch).gExp = -g;
    }
}
