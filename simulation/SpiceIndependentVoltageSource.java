// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceIndependentVoltageSource extends SpiceIndependentSource {
    SpiceCell sourceCell;		// our entry in the source vector

    public SpiceIndependentVoltageSource(SpiceNetwork network,int npos,int nneg,
					 double dc,double acmag,double acphase,
					 int trantype,double params[],
					 double vil,double vih) {
	int branch = network.size++;
	network.FindMatrixElement(npos,branch).gExp = 1;
	network.FindMatrixElement(nneg,branch).gExp = -1;
	network.FindMatrixElement(branch,npos).gExp = 1;
	network.FindMatrixElement(branch,nneg).gExp = -1;
	sourceCell = network.FindSourceElement(branch);
	source = SpiceSource.Allocate(dc,acmag,acphase,trantype,params,vil,vih);

	// we need to set up sourceCell each iteration
	iterationLink = network.eachIteration;
	network.eachIteration = this;
    }

    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) {
	source.ComputeBreakpoints(network,stopTime);
    }

    public boolean EachIteration(int mode,double time,double timestep) {
	if (mode == SpiceNetwork.OPERATING_POINT) sourceCell.luExp = source.dc;
	else sourceCell.luExp = source.TransientValue(time);
	return true;
    }
}
