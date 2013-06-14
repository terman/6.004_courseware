// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

// exponential source function
class SpiceExpSource extends SpiceSource {
    double offset;		// offset
    double amplitude;		// amplitude
    double ramplitude;		// amplitude at end of rise time
    double delay1;		// rise delay in seconds
    double rtau;		// rise time constant
    double delay2;		// fall delay in seconds
    double ftau;		// fall time constant

    public SpiceExpSource(double xdc,double xacmag,double xacphase,double params[],double vil,double vih) {
	super(xdc,xacmag,xacphase);

	offset = (params == null || params.length < 1) ? 0 : params[0];
	amplitude = (params == null || params.length < 2) ? 0 : params[1];
	delay1 = (params == null || params.length < 3) ? 0 : params[2];
	rtau = (params == null || params.length < 4) ? 1e-9 : params[3];
	delay2 = (params == null || params.length < 5) ? 1e-9 : params[4];
	ftau = (params == null || params.length < 6) ? 1e-9 : params[5];

	if (delay2 < delay1) delay2 = delay1;
	amplitude -= offset;
	ramplitude = amplitude * (1-Math.exp((delay1-delay2)/rtau));
    }

    public String SourceName() { return "EXP"; }

    public double TransientValue(double time) {
	if (time <= delay1) return offset;
	else if (time <= delay2)
	    return offset + amplitude*(1-Math.exp((delay1-time)/rtau));
	else
	    return offset + ramplitude*Math.exp((delay2-time)/ftau);
    }

    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) {
	network.AddBreakpoint(delay1);
	network.AddBreakpoint(delay2);
    }
}
