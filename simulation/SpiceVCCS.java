// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceVCCS extends SpiceDependentSource {
    public SpiceVCCS(SpiceNetwork network,int pos,int neg,int cpos,int cneg,double g) {
	super(pos,neg,cpos,cneg,g);

	// vccs can be handled completely by updating constants used
	// used to initialize admittance matrix on each iteration
	network.FindMatrixElement(k1,j1).gExp += g;
	network.FindMatrixElement(k1,j2).gExp -= g;
	network.FindMatrixElement(k2,j1).gExp += g;
	network.FindMatrixElement(k2,j2).gExp -= g;
    }
}
