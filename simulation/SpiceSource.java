// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

import netlist.NetlistConsumer;

// simple DC source
class SpiceSource {
    double dc;			// dc value
    double acmag;		// ac magnitude
    double acphase;		// ac phase

    public SpiceSource(double xdc,double xacmag,double xacphase) {
	dc = xdc;
	acmag = xacmag;
	acphase = xacphase;
    }

    public double TransientValue(double time) { return dc; }

    public double NextBreakpoint(double time,double dv) { return -1; }

    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) { };

    public boolean SupportsGateLevelSimulation() { return false; }

    public double NextContaminationTime(double time) { return -1; }

    public double NextPropagationTime(double time) { return -1; }

    public String SourceName() { return "DC"; }

    public static SpiceSource Allocate(double dc,double acmag,double acphase,
				       int trantype,double params[],
				       double vil,double vih) {
	SpiceSource s;

	if (trantype == NetlistConsumer.PWL)
	    s = new SpicePWLSource(dc,acmag,acphase,params,vil,vih);
	else if (trantype == NetlistConsumer.PULSE)
	    s = new SpicePulseSource(dc,acmag,acphase,params,vil,vih);
	else if (trantype == NetlistConsumer.SIN)
	    s = new SpiceSinSource(dc,acmag,acphase,params,vil,vih);
	else if (trantype == NetlistConsumer.EXP)
	    s = new SpiceExpSource(dc,acmag,acphase,params,vil,vih);
	else if (trantype == NetlistConsumer.SFFM)
	    s = new SpiceSFFMSource(dc,acmag,acphase,params,vil,vih);
	else if (trantype == NetlistConsumer.AM)
	    s = new SpiceAMSource(dc,acmag,acphase,params,vil,vih);
	else
	    s = new SpiceSource(dc,acmag,acphase);

	// if no DC value specified, use transient value at time 0
	if (s.dc == Double.NEGATIVE_INFINITY) s.dc = s.TransientValue(0);
	// if still no DC value, default to 0
	if (s.dc == Double.NEGATIVE_INFINITY) s.dc = 0;

	return s;
    }
}
