// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

// amplitude modulation source function
class SpiceAMSource extends SpiceSource {
    double amplitude;		// amplitude
    double fcarrier;		// carrier frequency
    double fmodulation;		// modulation frequency
    double offset;		// offset constant
    double delay;		// delay time before start of signal

    public SpiceAMSource(double xdc,double xacmag,double xacphase,double params[],double vil,double vih) {
	super(xdc,xacmag,xacphase);

	amplitude = (params == null || params.length < 1) ? 0 : params[0];
	offset = (params == null || params.length < 2) ? 0 : params[1];
	fmodulation = (params == null || params.length < 3) ? 0 : params[2];
	fcarrier = (params == null || params.length < 4) ? 0 : params[3];
	delay = (params == null || params.length < 5) ? 0 : params[4];

	fcarrier *= 2*Math.PI;
	fmodulation *= 2*Math.PI;
    }

    public String SourceName() { return "AM"; }

    public double TransientValue(double time) {
	if (time <= delay) return 0;
	else return amplitude*(offset + Math.sin(fmodulation*(time-delay)))*
		 Math.sin(fcarrier*(time-delay));
    }

    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) {
	network.AddBreakpoint(delay);
    }
}

