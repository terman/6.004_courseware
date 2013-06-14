// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

// piecewise linear source function
class SpicePWLSource extends SpiceSource {
    double tvpairs[];		// time,value pairs
    int npairs;			// number of pairs
    double vih,vil;

    public SpicePWLSource(double xdc,double xacmag,double xacphase,double params[],double vil,double vih) {
	super(xdc,xacmag,xacphase);

	tvpairs = params;
	npairs = (tvpairs == null) ? 0 : tvpairs.length;
	this.vih = vih;
	this.vil = vil;
    }

    public String SourceName() { return "PWL"; }

    public boolean SupportsGateLevelSimulation() { return true; }

    public double NextContaminationTime(double time) {
	time += 1e-13;	// get past current time by epsilon
	double tlast = 0;
	double vlast = 0;
	for (int i = 0; i < npairs; i += 2) {
	    double t = tvpairs[i];
	    double v = tvpairs[i+1];
	    if (i > 0 && time <= t) {
		if (vlast >= vih && v < vih) {
		    double et = tlast + (t - tlast)*(vih - vlast)/(v - vlast);
		    if (et > time) return et;
		}
		else if (vlast <= vil && v > vil) {
		    double et = tlast + (t - tlast)*(vil - vlast)/(v - vlast);
		    if (et > time) return et;
		}
	    }
	    tlast = t;
	    vlast = v;
	}
	return -1;
    }

    public double NextPropagationTime(double time) {
	time += 1e-13;	// get past current time by epsilon
	double tlast = 0;
	double vlast = 0;
	for (int i = 0; i < npairs; i += 2) {
	    double t = tvpairs[i];
	    double v = tvpairs[i+1];
	    if (i > 0 && time <= t) {
		if (vlast < vih && v >= vih) {
		    double et = tlast + (t - tlast)*(vih - vlast)/(v - vlast);
		    if (et > time) return et;
		}
		else if (vlast > vil && v <= vil) {
		    double et = tlast + (t - tlast)*(vil - vlast)/(v - vlast);
		    if (et > time) return et;
		}
	    }
	    tlast = t;
	    vlast = v;
	}
	return -1;
    }

    public double TransientValue(double time) {
	double tlast = 0;
	double vlast = 0;
	for (int i = 0; i < npairs; i += 2)
	    if (time <= tvpairs[i]) {
		if (i == 0) {
		    //System.out.println("pwl: t="+time+" v="+tvpairs[1]);
		    return tvpairs[1];
		} else {
		    //System.out.println("pwl: t="+time+" v="+(vlast+((time-tlast)/(tvpairs[i]-tlast))*(tvpairs[i+1]-vlast)));
		    return vlast+((time-tlast)/(tvpairs[i]-tlast))*(tvpairs[i+1]-vlast);
		}
	    } else {
		tlast = tvpairs[i];
		vlast = tvpairs[i+1];
	    }
	//System.out.println("pwl: t="+time+" v="+vlast);
	return vlast;
    }

    // return time of next breakpoint or voltage change of dv volts,
    // whichever is sooner
    public double NextBreakpoint(double time,double dv) {
	double tlast = 0;
	double vlast = 0;
	for (int i = 0; i < npairs; i += 2)
	    if (time < tvpairs[i]) {
		if (i == 0) return tvpairs[i];
		else {
		    double sec_per_v = Math.abs((tvpairs[i] - tlast)/(tvpairs[i+1] - vlast));
		    return Math.min(tvpairs[i],time + dv * sec_per_v);
		}
	    } else {
		tlast = tvpairs[i];
		vlast = tvpairs[i+1];
	    }
	return -1;
    }

    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) {
	for (int i = 0; i < npairs; i += 2)
	    network.AddBreakpoint(tvpairs[i]);
    }
}
