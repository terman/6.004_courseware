// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.HashMap;

class SpiceMOSModel_L1 extends SpiceMOSModel {
    double m_lambda;	// (1/V = 0.0) channel-length modulatio

    public SpiceMOSModel_L1(String n,int mtype,HashMap xoptions,double temp) {
	// process common model parameters
	super(n,mtype,1,xoptions,temp);

	// model parameters specific to Level 1
	m_lambda = GetOption("lambda",0.0);
    }
  
    public void Setup(SpiceMosfet m,double l,double w) {
	super.Setup(m,l,w);
    }

    // assumes vds >= 0
    public void ids_gds(SpiceMosfet m,double vds,double vgs,double vbs) {
	double vdsat,temp1;

	temp1 = (vbs > 0) ? Math.max(0,sqrt_phi - vbs*inv_2_sqrt_phi) :
	    Math.sqrt(phi - vbs);
	// can we do vbi*mtype once during setup?
	m.vth = vbi*m_type + m_gamma*temp1;
	if (temp1 > 0) temp1 = m_gamma/(2*temp1);
	else temp1 = 0;

	vdsat = Math.max(0,vgs - m.vth);
	//if (m.debug) System.out.println("vbi="+vbi+" vth="+m.vth+" vdsat="+vdsat);

	if (vdsat > 0) {
	    double betap = m.beta*(1 + m_lambda*vds);
	    if (vdsat > vds) {	// linear region
		double temp = vds*(vdsat - 0.5*vds);
		m.ids = betap*temp;
		m.gds = betap*(vdsat-vds) + m_lambda*m.beta*temp;
		m.gm = betap*vds;
		m.gmbs = m.gm*temp1;
	    } else {			// saturation region
		double temp = vdsat*vdsat*0.5;
		m.ids = betap*temp;
		m.gds = m_lambda*m.beta*temp;
		m.gm = betap*vdsat;
		m.gmbs = m.gm*temp1;
	    }
	} else {			// cut-off region
	    m.ids = 0;
	    m.gds = 0;
	    m.gm = 0;
	    m.gmbs = 0;
	}
    }
}
