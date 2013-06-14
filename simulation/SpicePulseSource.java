// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

// pulsed source function
class SpicePulseSource extends SpiceSource {
    double init;		// initial value
    double pulsed;		// pulsed value
    double delay;		// initial delay
    double period;		// how often it repeats
    double t2;			// end time of onset ramp
    double t3;			// start time of recovery ramp
    double t4;			// end time of recovery ramp

    double tc1,tc2,tp1,tp2;	// used during gate-level simulation

    public SpicePulseSource(double xdc,double xacmag,double xacphase,double params[],double vil,double vih) {
	super(xdc,xacmag,xacphase);

	init = (params == null || params.length < 1) ? 0 : params[0];
	pulsed = (params == null || params.length < 2) ? 0 : params[1];

	delay = (params == null || params.length < 3) ? 0 : params[2];
	if (delay < 0) delay = 0;
	double tr = (params == null || params.length < 4) ? 1e-9 : params[3];
	if (tr < 0) tr = 0;
	double tf = (params == null || params.length < 5) ? tr : params[4];
	if (tf < 0) tf = 0;
	double pw = (params == null || params.length < 6) ? 0 : params[5];
	if (pw < 0) pw = 0;

	t2 = tr;
	t3 = tr + pw;
	t4 = tr + pw + tf;

	period = (params == null || params.length < 7) ? t4 + pw : params[6];
	if (period < t4) period = t4;

	tc1 = -1;
	tc2 = -1;
	if (init <= vil && pulsed > vil)
	    tc1 = t2*(vil - init)/(pulsed - init);
	else if (init >= vih && pulsed < vih)
	    tc1 = t2*(vih - init)/(pulsed - init);

	if (pulsed <= vil && init > vil)
	    tc2 = t3 + (t4 - t3)*(vil - pulsed)/(init - pulsed);
	else if (pulsed >= vih && init < vih)
	    tc2 = t3 + (t4 - t3)*(vih - pulsed)/(init - pulsed);

	tp1 = -1;
	tp2 = -1;
	if (init < vih && pulsed >= vih)
	    tp1 = t2*(vih - init)/(pulsed - init);
	else if (init > vil && pulsed <= vil)
	    tp1 = t2*(vil - init)/(pulsed - init);

	if (pulsed > vil && init <= vil)
	    tp2 = t3 + (t4 - t3)*(vil - pulsed)/(init - pulsed);
	else if (pulsed < vih && init >= vih)
	    tp2 = t3 + (t4 - t3)*(vih - pulsed)/(init - pulsed);
    }

    public String SourceName() { return "PULSE"; }

    public double TransientValue(double time) {
	if (time <= delay) return init;
	time -= delay;
	if (time > period) time -= Math.floor(time/period)*period;
	if (time < t2) return init + (pulsed - init) * (time/t2);
	else if (time <= t3) return pulsed;
	else if (time < t4) return pulsed + (init - pulsed) * ((time - t3)/(t4 - t3));
	else return init;
    }

    public boolean SupportsGateLevelSimulation() { return true; }

    public double NextContaminationTime(double time) {
	double tbase = delay;
	double toffset = 0;
	if (time > delay) {
	    tbase = Math.floor((time - delay)/period)*period + delay;
	    toffset = time - tbase + 1e-13;
	}

	if (tc1 == -1) {
	    if (tc2 == -1) return -1;
	    return (toffset < tc2) ? tbase + tc2 : tbase + period + tc2;
	} else if (tc2 == -1)
	    return (toffset < tc1) ? tbase + tc1 : tbase + period + tc1;
	else if (toffset < tc1) return tbase + tc1;
	else if (toffset < tc2) return tbase + tc2;
	else return tbase + period + tc1;
    }

    public double NextPropagationTime(double time) {
	double tbase = delay;
	double toffset = 0;
	if (time > delay) {
	    tbase = Math.floor((time - delay)/period)*period + delay;
	    toffset = time - tbase + 1e-13;
	}

	if (tp1 == -1) {
	    if (tp2 == -1) return -1;
	    return (toffset < tp2) ? tbase + tp2 : tbase + period + tp2;
	} else if (tp2 == -1)
	    return (toffset < tp1) ? tbase + tp1 : tbase + period + tp1;
	else if (toffset < tp1) return tbase + tp1;
	else if (toffset < tp2) return tbase + tp2;
	else return tbase + period + tp1;
    }

    // return time of next breakpoint or voltage change of dv volts,
    // whichever is sooner
    public double NextBreakpoint(double time,double dv) {
	double tbase = delay;
	double toffset = 0;
	if (time > delay) {
	    tbase = Math.floor((time - delay)/period)*period + delay;
	    toffset = time - tbase;
	}

	if (toffset < t2) {
	    double sec_per_v = Math.abs(t2/(pulsed - init));
	    return Math.min(tbase + t2,time + dv * sec_per_v);
	} else if (toffset < t3) return tbase + t3;
	else if (toffset < t4) {
	    double sec_per_v = Math.abs((t4 - t3)/(init - pulsed));
	    return Math.min(tbase + t4,time + dv * sec_per_v);
	} else return tbase + period;
    }

    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) {
	for (double base = delay; base < stopTime; base += period) {
	    network.AddBreakpoint(base);
	    if (t2 > 0) network.AddBreakpoint(base + t2);
	    if (t3 > t2) network.AddBreakpoint(base + t3);
	    if (t4 > t3) network.AddBreakpoint(base + t4);
	}
    }
}
