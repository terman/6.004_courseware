// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

class MosfetPrototype extends DevicePrototype {
    Node d,g,s,b;		// four terminal nodes
    Identifier model;	// mosfet model
    double l;		// length
    double w;		// width
    double sl;		// scaled length
    double sw;		// scaled width
    double ad;		// drain area
    double pd;		// drain perimeter
    double nrd;		// number of squares of drain diffusion
    double rdc;		// additional drain resistance
    double as;		// source area
    double ps;		// source perimeter
    double nrs;		// number of squares of source diffusion
    double rsc;		// additional source resistance

    public static Parameter mparams = InitializeParameters();

    public MosfetPrototype(Identifier m_id,Node m_d,Node m_g,Node m_s,Node m_b,
			   Identifier m_model,double m_l,double m_w,
			   double m_sl,double m_sw,
			   double m_ad,double m_pd,double m_nrd,double m_rdc,
			   double m_as,double m_ps,double m_nrs,double m_rsc) {
	id = m_id;
	d = m_d;
	g = m_g;
	s = m_s;
	b = m_b;
	model = m_model;
	l = m_l;
	w = m_w;
	sl = m_sl;
	sw = m_sw;
	ad = m_ad;
	pd = m_pd;
	nrd = m_nrd;
	rdc = m_rdc;
	as = m_as;
	ps = m_ps;
	nrs = m_nrs;
	rsc = m_rsc;
    }

    // add device to netlist
    public boolean Netlist(Netlist network,NetlistConsumer n) {
	Model m = network.currentSubcircuit.FindModel(model);

	if (m == null) {
	    network.Error(model,"Can't find .model definition with this name");
	    return false;
	}

	n.MakeMosfet(network.prefix + id.name,
		     d.netlisterNode,g.netlisterNode,s.netlisterNode,b.netlisterNode,
		     m.netlisterModel,l,w,sl,sw,ad,pd,nrd,rdc,as,ps,nrs,rsc);
	return true;
    }

    // set up list of parameters and their default values
    static Parameter InitializeParameters() {
	Parameter p = null;

	p = new Parameter("l",0.0,p);
	p = new Parameter("w",0.0,p);
	p = new Parameter("sl",0.0,p);
	p = new Parameter("sw",0.0,p);
	p = new Parameter("ad",0.0,p);
	p = new Parameter("pd",0.0,p);
	p = new Parameter("nrd",0.0,p);
	p = new Parameter("rdc",0.0,p);
	p = new Parameter("as",0.0,p);
	p = new Parameter("ps",0.0,p);
	p = new Parameter("nrs",0.0,p);
	p = new Parameter("rsc",0.0,p);

	return p;
    }
}
