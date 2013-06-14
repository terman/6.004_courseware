// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

import java.util.ArrayList;

class IndependentSourcePrototype extends DevicePrototype {
    static final int VoltageSource = 1;
    static final int CurrentSource = 2;

    int type;		// type of source
    Node npos;		// two terminal nodes
    Node nneg;
    Number dc;		// dc source value
    Number acmag;		// ac source magnitude
    Number acphase;	// ac source phase
    int tranfun;		// transient source function
    ArrayList params;	// parameters for transient source

    public IndependentSourcePrototype(Identifier s_id,int s_type,Node s_pos,Node s_neg,
				      Number s_dc,Number s_acmag,Number s_acphase,
				      int s_tranfun,ArrayList s_params) {
	id = s_id;
	type = s_type;
	npos = s_pos;
	nneg = s_neg;
	dc = s_dc;
	acmag = s_acmag;
	acphase = s_acphase;
	tranfun = s_tranfun;
	params = s_params;
    }

    // convert string into appropriate transient function type
    static int SourceFunctionType(String name) {
	if (name.equalsIgnoreCase("pwl")) return NetlistConsumer.PWL;
	else if (name.equalsIgnoreCase("pulse")) return NetlistConsumer.PULSE;
	else if (name.equalsIgnoreCase("sin")) return NetlistConsumer.SIN;
	else if (name.equalsIgnoreCase("exp")) return NetlistConsumer.EXP;
	else if (name.equalsIgnoreCase("sffm")) return NetlistConsumer.SFFM;
	else if (name.equalsIgnoreCase("am")) return NetlistConsumer.AM;
	else return 0;
    }

    // check parameters supplied for a transient source function
    static boolean CheckParams(Netlist v,int trantype,ArrayList p) {
	switch (trantype) {
	case NetlistConsumer.PWL:
	    double last = 0;
	    for (int i = 0; i < p.size(); i += 2) {
		Number n = (Number)p.get(i);
		if (i > 0 && n.value <= last) {
		    v.Error(n,"PWL times not monotonically increasing");
		    return false;
		}
		last = n.value;
		if (i+1 == p.size()) {
		    v.Error("Expected even number of parameters for PWL");
		    return false;
		}
	    }
	    return true;
	case NetlistConsumer.PULSE:
	    if (p.size() < 6) {
		v.Error("Expected at least six parameters for PULSE");
		return false;
	    }
	    Number n = (Number)p.get(2);
	    if (n.value < 0) {
		v.Error(n,"PULSE delay parameter cannot be negative");
		return false;
	    }
	    n = (Number)p.get(3);
	    if (n.value <= 0) {
		v.Error(n,"PULSE rise time parameter must be greater than zero");
		return false;
	    }
	    n = (Number)p.get(4);
	    if (n.value <= 0) {
		v.Error(n,"PULSE fall time parameter must be greater than zero");
		return false;
	    }
	    n = (Number)p.get(5);
	    if (n.value < 0) {
		v.Error(n,"PULSE width parameter cannot be negative");
		return false;
	    }
	    return true;
	case NetlistConsumer.SIN:
	    return true;
	case NetlistConsumer.EXP:
	    return true;
	case NetlistConsumer.SFFM:
	    return true;
	case NetlistConsumer.AM:
	    return true;
	}
	return false;
    }

    // add device to netlist
    public boolean Netlist(Netlist network,NetlistConsumer n) {
	int nparams = (params!=null) ? params.size() : 0;
	double p[] = null;

	if (nparams > 0) {
	    p = new double[nparams];
	    for (int i = 0; i < nparams; i += 1)
		p[i] = ((Number)params.get(i)).value;
	}

	switch (type) {
	case VoltageSource:
	    n.MakeIndependentVoltageSource(network.prefix + id.name,
					   npos.netlisterNode,nneg.netlisterNode,
					   dc==null ? Double.NEGATIVE_INFINITY : dc.value,
					   acmag==null ? 0 : acmag.value,
					   acphase==null ? 0 : acphase.value,
					   tranfun,p);
	    break;
	case CurrentSource:
	    n.MakeIndependentCurrentSource(network.prefix + id.name,
					   npos.netlisterNode,nneg.netlisterNode,
					   dc==null ? Double.NEGATIVE_INFINITY : dc.value,
					   acmag==null ? 0 : acmag.value,
					   acphase==null ? 0 : acphase.value,
					   tranfun,p);
	    break;
	}
	return true;
    }
}
