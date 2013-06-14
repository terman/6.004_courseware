// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceDependentSource extends SpiceDevice {
    int j1,j2;		// control terminals
    int k1,k2;		// output terminals
    double gain;		// multiplicative factor

    public SpiceDependentSource(int pos,int neg,int cpos,int cneg,double g) {
	j1 = cpos;
	j2 = cneg;
	k1 = pos;
	k2 = neg;
	gain = g;
    }
}
