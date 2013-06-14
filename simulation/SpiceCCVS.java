// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceCCVS extends SpiceDependentSource {
    public SpiceCCVS(SpiceNetwork network,int pos,int neg,int cpos,int cneg,double g) {
	super(pos,neg,cpos,cneg,g);

	int b1 = network.size++;
	int b2 = network.size++;

	// ccvs can be handled completely by updating constants used
	// used to initialize admittance matrix on each iteration
	network.FindMatrixElement(j1,b1).gExp = 1;
	network.FindMatrixElement(j2,b1).gExp = -1;
	network.FindMatrixElement(b1,j1).gExp = 1;
	network.FindMatrixElement(b1,j2).gExp = -1;

	network.FindMatrixElement(k1,b2).gExp = 1;
	network.FindMatrixElement(k2,b2).gExp = -1;
	network.FindMatrixElement(b2,k1).gExp = 1;
	network.FindMatrixElement(b2,k2).gExp = -1;

	network.FindMatrixElement(b2,b1).gExp = -g;
    }
}
