// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceIndependentCurrentSource extends SpiceIndependentSource {
    SpiceCell spos,sneg;		// our entries in the source vector

    public SpiceIndependentCurrentSource(SpiceNetwork network,int npos,int nneg,
					 double dc,double acmag,double acphase,
					 int trantype,double params[],
					 double vil,double vih) {
	spos = network.FindSourceElement(npos);
	sneg = network.FindSourceElement(nneg);
	source = SpiceSource.Allocate(dc,acmag,acphase,trantype,params,vil,vih);

	// we need to set up sourceCell each iteration
	iterationLink = network.eachIteration;
	network.eachIteration = this;
    }

    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) {
	source.ComputeBreakpoints(network,stopTime);
    }

    public boolean EachIteration(int mode,double time,double timestep) {
	double i;

	if (mode == SpiceNetwork.OPERATING_POINT) i = source.dc;
	else i = source.TransientValue(time);
	spos.luExp -= i;
	sneg.luExp += i;
	return true;
    }
}
