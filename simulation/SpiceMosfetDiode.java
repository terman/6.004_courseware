// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceMosfetDiode extends SpiceStateDevice {
    SpiceMosfet fet;		// we'll need some node voltages
    int diff;			// diffusion node
    int bulk;

    SpiceCell dd,db,bd,bb;
    SpiceCell s_d,s_b;		// source vector entries
    double isat,isat_div_by_vt,vcrit,a_cj,p_cjsw;
    double coeffA,coeffB;

    public SpiceMosfetDiode(SpiceMosfet fet,int diff,int bulk,
			    double rdiff,double area,double perimeter) {
	super(fet.network,area*fet.model.cj + perimeter*fet.model.cjsw,false);
	this.fet = fet;

	// see if we need to add diff resistor
	this.diff = diff;
	this.bulk = bulk;
	if (rdiff > 0) {
	    int xdiff = diff;
	    this.diff = diff = network.size++;
	    network.nodes.put("diff("+xdiff+")",new Integer(diff));
	    double gdiff = 1/rdiff;
	    network.FindMatrixElement(xdiff,xdiff).gExp += gdiff;
	    network.FindMatrixElement(xdiff,diff).gExp -= gdiff;
	    network.FindMatrixElement(diff,xdiff).gExp -= gdiff;
	    network.FindMatrixElement(diff,diff).gExp += gdiff;
	}

	// get all the matrix cells we'll need
	dd = network.FindMatrixElement(diff,diff);
	db = network.FindMatrixElement(diff,bulk);
	bb = network.FindMatrixElement(bulk,bulk);
	bd = network.FindMatrixElement(bulk,diff);
	s_d = network.FindSourceElement(diff);
	s_b = network.FindSourceElement(bulk);

	// add minimum conductances for PN junction
	double gmin = network.gmin;
	dd.gExp += gmin;
	db.gExp -= gmin;
	bd.gExp -= gmin;
	bb.gExp += gmin;

	// precompute diode parameters
	SpiceMOSModel model = fet.model;
	isat = area*model.js + perimeter*model.jsw;
	if (isat <=0) isat = area*model.is;
	isat_div_by_vt = isat/model.vt_temp;
	vcrit = model.vcrit(model.vt_temp,isat_div_by_vt);
	a_cj = area*model.cj;
	p_cjsw = perimeter*model.cjsw;

	coeffA = (model.pb * a_cj)/(1 - model.m_mj);
	coeffB = (model.pb * p_cjsw)/(1 - model.m_mjsw);
    }

    public void DiodeCurrents(double vbdo,int mode) {
	if (isat != 0) {
	    SpiceMOSModel model = fet.model;
	    double gbd;
	    if (vbdo > 0) {
		double evbd = Math.exp(Math.min(SpiceModel.MAX_EXP_ARG,vbdo/model.vt_temp));
		gbd = isat_div_by_vt * evbd;
		double ieqbd = model.m_type*(isat*(evbd - 1) - gbd*vbdo);
		s_b.luExp -= ieqbd;
		s_d.luExp += ieqbd;
		double arg = vbdo/model.pb;
		value = a_cj*(1 + model.m_mj*arg) + p_cjsw*(1 + model.m_mjsw*arg);
		// this isn't right but the diffusion diodes
		// are rarely forward biased so this will do for now...
		x = value * vbdo;	// set charge
	    } else {
		gbd = isat_div_by_vt;
		double arg = 1 - vbdo/model.pb;
		double sarg = Math.pow(arg,-model.m_mj);
		double sargsw = Math.pow(arg,-model.m_mjsw);
		value = a_cj*sarg + p_cjsw*sargsw;
		x = value * vbdo;	// set charge
		// doesn't converge: x = coeffA*(1 - arg*sarg) + coeffB*(1 - arg*sargsw));
	    }
	    bb.luExp += gbd;
	    dd.luExp += gbd;
	    bd.luExp -= gbd;
	    db.luExp -= gbd;

	    // capacitance calculations
	    if (mode == SpiceNetwork.TRANSIENT_ANALYSIS) {
		Integrate();
		xprimeEQ *= model.m_type;
		s_b.luExp -= xprimeEQ;
		s_d.luExp += xprimeEQ;
		dd.luExp += geq;
		db.luExp -= geq;
		bd.luExp -= geq;
		bb.luExp += geq;
	    }
	}
    }
}
