// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.util.HashMap;

class SpiceMOSModel_L3 extends SpiceMOSModel {
    static final double COEFF0 = 0.0631353;
    static final double COEFF1 = 0.8013292;
    static final double COEFF2 = -0.01110777;

    double m_delta;	// narrow width factor for adjusting threshold
    double m_eta;	// static feedback factor for adjusting threshold
    double m_nfs;	// fast surface state density
    double m_theta;	// Vgs dependence on mobility
    double m_vmax;	// max drift velocity
    double m_kappa;
    double m_alpha;
  
    double m_xd;	// depletion layer width

    public SpiceMOSModel_L3(String n,int mtype,HashMap xoptions,double temp) {
	// process common model parameters
	super(n,mtype,3,xoptions,temp);
    
	// model parameters specific to Level 3
	m_delta = GetOption("delta",0.0);
	m_eta = GetOption("eta",0.0);
	m_nfs = GetOption("nfs",0.0);
	m_theta = GetOption("theta",0.0);
	m_vmax = GetOption("vmax",0.0);
	m_kappa = GetOption("kappa",0.2);
	m_alpha = GetOption("alpha",0.0);	// check default

	m_xd = Math.sqrt((2 * EPSSIL)/(Q * m_nsub));

	//System.out.print(" nfs="+Double.toString(m_nfs));
	//System.out.print(" vmax="+Double.toString(m_vmax));
	//System.out.print(" delta="+Double.toString(m_delta));
	//System.out.print(" theta="+Double.toString(m_theta));
	//System.out.print(" eta="+Double.toString(m_eta));
	//System.out.print(" kappa="+Double.toString(m_kappa));
	//System.out.println("");
    }

    public void Setup(SpiceMosfet m,double l,double w) {
	super.Setup(m,l,w);
    
	// compute various Level 3 quantities ahead of time
	m.fn = (m_delta/m.weff) * 0.25 * ((2 * Math.PI * EPSSIL)/m_cox);
	m.eta = m_eta * 8.15e-22/(m_cox*m.leff*m.leff*m.leff);
	m.xjonxl = m_xj / m.leff;
	m.djonxj = m_ld / m_xj;
	m.oxideCap = m_cox * m.leff * m.weff;
    }

    public void ids_gds(SpiceMosfet m,double vds,double vgs,double vbs) {
	// square root term
	double phibs,sqphbs,dsqdvb;  	
	if (vbs <= 0) {
	    phibs = phi - vbs;
	    sqphbs = Math.sqrt(phibs);
	    dsqdvb = -0.5 / sqphbs;
	} else {
	    sqphbs = sqrt_phi/(1 + vbs/(2*phi));
	    phibs = sqphbs * sqphbs;
	    dsqdvb = -phibs /(2 * phi * sqrt_phi);
	}
  	
	// short channel effect factor
	double fshort,dfsdvb;
	{	
	    double wps = m_xd * sqphbs;
	    double oneoverxj = 1.0/ m_xj;
	    double wponxj = wps*oneoverxj;
	    double wconxj = COEFF0 + (COEFF1 + COEFF2*wponxj)*wponxj;
	    double arga = wconxj + m.djonxj;
	    double argc = wponxj/(1.0 + wponxj);
	    double argb = Math.sqrt(1.0 - argc*argc);
	    fshort = 1.0 - m.xjonxl*(arga*argb - m.djonxj);

	    double dwpdvb = m_xd*dsqdvb;
	    double dadvb = (COEFF1 + COEFF2*(wponxj + wponxj))*dwpdvb*oneoverxj;
	    double dbdvb = -argc*argc*(1.0 - argc)*dwpdvb/(argb*wps);
	    dfsdvb = -m.xjonxl*(dadvb*argb + arga*dbdvb);
	}
  
	// body effect
	double qbonco,dfbdvb,dqbdvb,fbody,onfbdy;
	{
	    double gammas = m_gamma*fshort;
	    double fbodys = 0.5*gammas/(sqphbs + sqphbs);
	    fbody = fbodys + m.fn;
	    onfbdy = 1.0/(1.0 + fbody);
	    dfbdvb = -fbodys*dsqdvb/sqphbs + fbodys*dfsdvb/fshort;
	    qbonco = gammas*sqphbs + m.fn*phibs;
	    dqbdvb = gammas*dsqdvb + m_gamma*dfsdvb*sqphbs - m.fn;
	}

	// threshold voltage
	m.vth = vbi*m_type - m.eta*vds + qbonco;
	double dvtdvd = -m.eta;
	double dvtdvb = dqbdvb;

	double von = m.vth;
	double xn = 0.0;
	double dxndvb = 0.0,dvodvd = 0.0,dvodvb = 0.0;
	if (m_nfs != 0.0) {
	    // 1e4 = cm**2/m**2
	    double csonco = Q*m_nfs*1e4*m.leff*m.weff/m.oxideCap;
	    double cdonco = qbonco/(phibs + phibs);
	    xn = 1.0 + csonco + cdonco;
	    von = m.vth + vt_temp*xn;
	    dxndvb = dqbdvb/(phibs+phibs) - qbonco*dsqdvb/(phibs*sqphbs);
	    dvodvd = dvtdvd;
	    dvodvb = dvtdvb + vt_temp*dxndvb;
	} else if (vgs <= von) {	// cutoff region if no weak inversion
	    m.ids = 0.0;
	    m.gm = 0.0;
	    m.gds = 0.0;
	    m.gmbs = 0.0;
	    return;
	}

	// device is on
	double vgsx = Math.max(vgs,von);

	// mobility modulation by gate voltage
	double onfg = 1.0 + m_theta*(vgsx - m.vth);
	double fgate = 1.0/onfg;
	double us = m_uo*1e-4*fgate;	// 1e-4 = m**2/cm**2
	double dfgdvg = -m_theta*fgate*fgate;
	double dfgdvd = -dfgdvg*dvtdvd;
	double dfgdvb = -dfgdvg*dvtdvb;

	// saturation voltage
	double vdsat = (vgsx - m.vth)*onfbdy;
	double dvsdvg,dvsdvd,dvsdvb,onvdsc = 0.0;
	if (m_vmax <= 0.0) {
	    dvsdvg = onfbdy;
	    dvsdvd = -dvsdvg*dvtdvd;
	    dvsdvb = -dvsdvg*dvtdvb - vdsat*dfbdvb*onfbdy;
	} else {
	    double vdsc = m.leff*m_vmax/us;
	    onvdsc = 1.0/vdsc;
	    double arga = (vgsx - m.vth)*onfbdy;
	    double argb = Math.sqrt(arga*arga + vdsc*vdsc);
	    vdsat = arga+vdsc-argb;
	    double dvsdga = (1.0 - arga/argb)*onfbdy;
	    dvsdvg = dvsdga - (1.0 - vdsc/argb)*vdsc*dfgdvg*onfg;
	    dvsdvd = -dvsdvg*dvtdvd;
	    dvsdvb = -dvsdvg*dvtdvb - arga*dvsdga*dfbdvb;
	}

	// current factors in linear region
	double vdsx = Math.min(vds,vdsat);
	double Beta = m.beta*fgate;
	if (vdsx == 0.0) {
	    m.ids = 0.0;
	    m.gm = 0.0;
	    m.gds = Beta*(vgsx - m.vth);
	    m.gmbs = 0.0;
	    if (m_nfs != 0.0 && vgs < von)
		m.gds *= Math.exp((vgs - von)/(vt_temp*xn));
	    return;
	}
	double cdo = vgsx - m.vth - 0.5*(1.0+fbody)*vdsx;
	double dcodvb = -dvtdvb - 0.5*dfbdvb*vdsx;

	// normalized drain current
	double cdnorm = cdo*vdsx;
	m.gm = vdsx;
	m.gds = vgsx - m.vth - (1.0 + fbody + dvtdvd)*vdsx;
	m.gmbs = dcodvb*vdsx;

	// drain current without velocity saturation effect
	double cd1 = m.beta*cdnorm;
	m.ids = Beta*cdnorm;
	m.gm = Beta*m.gm + dfgdvg*cd1;
	m.gds = Beta*m.gds + dfgdvd*cd1;
	m.gmbs *= Beta;

	// velocity saturation factor
	double fdrain = 0.0;
	double dfddvg = 0.0;
	double dfddvd = 0.0;
	double dfddvb = 0.0;
	if (m_vmax > 0.0) {
	    fdrain = 1.0/(1.0 + vdsx*onvdsc);
	    double fd2 = fdrain*fdrain;
	    double arga = fd2*vdsx*onvdsc*onfg;
	    dfddvg = -dfgdvg*arga;
	    dfddvd = -dfgdvd*arga-fd2*onvdsc;
	    dfddvb = -dfgdvb*arga;
	    // drain current
	    m.gm = fdrain*m.gm + dfddvg*m.ids;
	    m.gds = fdrain*m.gds + dfddvd*m.ids;
	    m.gmbs = fdrain*m.gmbs + dfddvb*m.ids;
	    m.ids = fdrain*m.ids;
	    Beta *= fdrain;
	}

	// channel length modulation
	double gds0 = 0.0;
	if (vds > vdsat && (m_vmax <= 0.0 || m_alpha != 0.0)) {
	    double delxl,dldvd,ddldvg,ddldvd,ddldvb;
	    if (m_vmax <= 0.0) {
		delxl = Math.sqrt(m_kappa*(vds - vdsat)*m_alpha);
		dldvd = 0.5*delxl/(vds - vdsat);
		ddldvg = 0.0;
		ddldvd = -dldvd;
		ddldvb = 0.0;
	    } else {
		double cdsat = m.ids;
		double gdsat = Math.max(1.0e-12,cdsat*(1.0 - fdrain)*onvdsc);
		double gdoncd = gdsat/cdsat;
		double gdonfd = gdsat/(1.0 - fdrain);
		double gdonfg = gdsat*onfg;
		double dgdvg = gdoncd*m.gm - gdonfd*dfddvg + gdonfg*dfgdvg;
		double dgdvd = gdoncd*m.gds - gdonfd*dfddvd + gdonfg*dfgdvd;
		double dgdvb = gdoncd*m.gmbs - gdonfd*dfddvb + gdonfg*dfgdvb;

		double emax = //(ckt->CKTbadMos3) ? cdsat*oneoverxl/gdsat :
		    m_kappa*cdsat/(m.leff*gdsat);
		double emoncd = emax/cdsat;
		double emongd = emax/gdsat;
		double demdvg = emoncd*m.gm - emongd*dgdvg;
		double demdvd = emoncd*m.gds - emongd*dgdvd;
		double demdvb = emoncd*m.gmbs - emongd*dgdvb;

		double arga = 0.5*emax*m_alpha;
		double argc = m_kappa*m_alpha;
		double argb = Math.sqrt(arga*arga+argc*(vds - vdsat));
		delxl = argb-arga;
		dldvd = argc/(argb+argb);
		double dldem = 0.5*(arga/argb-1.0)*m_alpha;
		ddldvg = dldem*demdvg;
		ddldvd = dldem*demdvd-dldvd;
		ddldvb = dldem*demdvb;
	    }

	    // punch through approximation
	    if (delxl > 0.5*m.leff) {
		delxl = m.leff - (m.leff*m.leff/(4.0*delxl));
		double arga = 4.0*(m.leff - delxl)*(m.leff - delxl)/(m.leff*m.leff);
		ddldvg = ddldvg*arga;
		ddldvd = ddldvd*arga;
		ddldvb = ddldvb*arga;
		dldvd =  dldvd*arga;
	    }

	    // saturation region
	    double dlonxl = delxl/m.leff;
	    double xlfact = 1.0/(1.0 - dlonxl);
	    m.ids *= xlfact;
	    double diddl = m.ids/(m.leff - delxl);
	    m.gm = m.gm*xlfact + diddl*ddldvg;
	    gds0 = m.gds*xlfact + diddl*ddldvd;
	    m.gmbs = m.gmbs*xlfact + diddl*ddldvb;
	    m.gm += gds0*dvsdvg;
	    m.gmbs += gds0*dvsdvb;
	    m.gds = gds0*dvsdvd + diddl*dldvd;
	}

	if (vgs < von) {    // weak inversion
	    // only get here when m_nfs != 0
	    double onxn = 1.0/xn;
	    double ondvt = onxn/vt_temp;
	    double wfact = Math.exp((vgs - von)*ondvt);
	    m.ids *= wfact;
	    double gms = m.gm*wfact;
	    double gmw = m.ids*ondvt;
	    m.gm = gmw;
	    if (vds > vdsat) m.gm += gds0*dvsdvg*wfact;
	    m.gds = m.gds*wfact + (gms - gmw)*dvodvd;
	    m.gmbs = m.gmbs*wfact +
		(gms - gmw)*dvodvb-gmw*(vgs - von)*onxn*dxndvb;
	}
    }
}
