// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

// sin source function
class SpiceSinSource extends SpiceSource {
    double offset;		// offset
    double amplitude;		// amplitude
    double frequency;		// freq in Hz.
    double delay;		// delay in seconds
    double theta;		// damping factor in 1/seconds
    double phi;			// phase delay in degrees

    public SpiceSinSource(double xdc,double xacmag,double xacphase,double params[],double vil,double vih) {
	super(xdc,xacmag,xacphase);

	offset = (params == null || params.length < 1) ? 0 : params[0];
	amplitude = (params == null || params.length < 2) ? 0 : params[1];
	frequency = (params == null || params.length < 3) ? 1e6 : params[2];
	delay = (params == null || params.length < 4) ? 0 : params[3];
	theta = (params == null || params.length < 5) ? 0 : params[4];
	phi = (params == null || params.length < 6) ? 0 : Math.PI*params[5]/180.0;
    }

    public String SourceName() { return "SIN"; }

    public double TransientValue(double time) {
	if (time <= delay) return offset + amplitude*Math.sin(phi);
	else return offset + amplitude * Math.exp((delay-time)*theta) *
		 Math.sin(phi + 2*Math.PI*frequency*(time-delay));
    }

    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) {
	network.AddBreakpoint(delay);
    }
}
