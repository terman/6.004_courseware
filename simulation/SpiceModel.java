// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.HashMap;

class SpiceModel {
    static final double EPSOX = 3.45314379969e-11;	// (F/m) permittivity of silicon dioxide
    static final double EPSSIL = 1.035943139907e-10;	// (F/m) permittivity of silicon
    static final double BOLTZ = 1.3806226e-23;		// (J/K) Boltzmann constant
    static final double Q = 1.6021918e-19;		// (C) electron charge
    static final double EG = 1.16;			// energy gap
    static final double NI = 1.45e16;			// (1/(cm^3)) intrinsic carrier concentration
    static final double TNOM = 25.0;
    static final double MAX_EXP_ARG = 709.0;

    String name;
    HashMap options;		// name to Double
    boolean limited;

    public SpiceModel(String n,HashMap xoptions) {
	name = n;
	options = xoptions;
    }

    public double GetOption(String name,double defaultv) {
	Double v = (Double)options.get(name);
	return ((v == null) ? defaultv : v.doubleValue());
    }

    // compute vt for a given temperature
    public double vt(double temp) {
	return BOLTZ*temp/Q;
    }

    // compute vcrit
    public double vcrit(double vt,double isat_div_by_vt) {
	return vt * Math.log(1/(1.4142135 * isat_div_by_vt));
    }

    // limit PN junction voltages
    public double pnjlim(double vt,double vnew,double vold,double vcrit) {
	limited = false;
	if (vnew <= vcrit) return vnew;
	double delv = vnew - vold;
	if (Math.abs(delv) <= vt+vt) return vnew;
	limited = true;
	if (vold > 0) {
	    double arg = 1 + delv/vt;
	    if (arg > 0) return vold + vt*Math.log(arg);
	    else return vcrit;
	} else return vt * Math.log(vnew/vt);
    }

    // limit FET voltages
    static final double vdd = 3.3;
    static final double v40pct = 0.4 * vdd;
    static final double v70pct = 0.7 * vdd;
    static final double v80pct = 0.8 * vdd;

    public double fetlim(double vnew,double vold,double vtn) {
	double vtsthi = 2*Math.abs(vold - vtn) + v40pct;
	double vtstlo = vtsthi/2 + v40pct;
	double vtox = vtn + v70pct;
	double delv = vnew - vold;
	if (vold < vtn) {
	    if (delv > 0) {
		double vtemp = vtn + 0.5;
		if (vnew > vtemp) return vtemp;
		else if (delv > vtstlo) return vold + vtstlo;
		else return vnew;
	    } else if (-delv > vtsthi) return vold - vtsthi;
	    else return vnew;
	} else if (vold < vtox) {
	    if (delv > 0) return Math.min(vnew,vtn + v80pct);
	    else return Math.max(vnew,vtn - 0.5);
	} else if (delv > 0) {
	    if (delv < vtsthi) return vnew;
	    else return vold + vtsthi;
	} else if (vnew < vtox) return Math.max(vnew,vtn + v40pct);
	else if (-delv > vtstlo) return vold - vtstlo;
	else return vnew;
    }

    // limit drain-source voltage
    public double limvds(double vnew,double vold) {
	if (vold < v70pct) {
	    if (vnew > vold) return Math.min(vnew,v80pct);
	    else return Math.max(vnew,-0.5);
	} else if (vnew > vold) return Math.min(vnew,3*vold+v40pct);
	else if (vnew < v70pct) return Math.max(vnew,v40pct);
	else return vnew;
    }
}
