// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package simulation;

import netlist.NetlistConsumer;
import java.util.HashMap;

abstract class SpiceMOSModel extends SpiceModel {
    // model parameters as specified by user
    double m_bex;	// (= -1.5) U0 temperature exponent
    double m_cgbo;	// (F/m) gate-bulk overlap cap
    double m_cgdo;	// (F/m) gate-drain overlap cap
    double m_cgso;	// (F/m) gate-source overlap cap
    double m_cj;	// (F/m**2) zero-bias junction cap
    double m_cjsw;	// (F/m = 0.0) zero-bias sidewall cap
    double m_cox;	// (F/m**2) gate area cap
    double m_dl;	// (m = 0.0) accounts for masking and etching effect
    double m_dw;	// (m = 0.0) accounts for masking and etching effect
    double m_gamma;	// (V**0.5) bulk threshold parameter
    double m_is;	// (A = 1e-14) bulk junction saturation current
    double m_js;	// (A/m**2) bulk junction saturation current
    double m_jsw;	// (A/u) sidewall junction saturation current
    double m_kp;	// (A/V**2) transconductance parameter
    double m_ld;	// (m) lateral diffusion from source/drain diffusion
    double m_level;	// (= 1) 1=shichman-hodges mode
    double m_mj;	// (= .5) junction grading coeff
    double m_mjsw;	// (= .33) junction side wall grading coeff
    double m_nss;	// (1/cm**2) surface state density
    double m_nsub;	// (1/cm**3 = 1e15) substrate doping
    double m_pb;	// (V = 0.8) junction potential
    double m_phi;	// (V) surface potential
    double m_rd;	// (Ohm) drain ohmic resistance
    double m_rdc;	// (Ohm) additional drain resistance due to contact
    double m_rs;	// (Ohm) source ohmic resistance
    double m_rsc;	// (Ohm) additional source resistance due to contact
    double m_rsh;	// (Ohm) diffusion sheet resistance
    double m_scale;	// (1) scale factor for mosfet dimensions
    double m_temp;	// (Celsius = 27) temperature at which model is evaluated
    double m_tnom;	// (Celsius = 27) temperature at which parameters measured
    double m_tox;	// (m = 1e-7) oxide thicknes
    double m_tpg;	// (= 1) type of gate +1=opp. of substrate, -1=same as substrate, 0=Al gate
    double m_type;	// -1 for PMOS, 1 from NMOS
    double m_uo;	// (cm**2/V-s = 600) surface mobility
    double m_vfb;	// (V) flat-band voltage
    double m_vto;	// (V) threshold voltage
    double m_wd;	// (m = 0.0) lateral diffusion from bulk along length
    double m_xj;	// (m = 0.0) metallurgical junction depth

    // temperature compensated and derived parameters
    double vt_temp,vbi,vto;
    double kp,dw,dl;
    double phi,sqrt_phi,inv_2_sqrt_phi;
    double is,js,jsw;
    double pb,cj,cjsw;

    public SpiceMOSModel(String n,int mtype,int level,
			 HashMap xoptions,double temp) {
	super(n,xoptions);

	m_type = (mtype == NetlistConsumer.PMOS) ? -1.0 : 1.0;
	m_level = level;
	m_scale = GetOption("scale",1);

	// determine values for model parameters, supplying defaults if needed

	m_nsub = GetOption("nsub",1e15) * 1e6;	// convert to MKS
	m_tox = GetOption("tox",1e-7);
	m_cox = GetOption("cox",EPSOX / m_tox);
	m_pb = GetOption("pb",0.8);

	m_tnom = GetOption("tnom",25.0);
	m_tnom += 273.15;
	m_temp = GetOption("temp",temp);
	m_temp += 273.15;
	m_bex = GetOption("bex",-1.5);

	double tratio = m_temp/m_tnom;
	double vt_tnom = vt(m_tnom);
	vt_temp = vt(m_temp);
	double eg_tnom = EG - 7.02e-4*((m_tnom * m_tnom)/(m_tnom + 1108.0));
	double eg_temp = EG - 7.02e-4*((m_temp * m_temp)/(m_temp + 1108.0));
	double facln = 3.0*Math.log(tratio) + eg_tnom/vt_tnom - eg_temp/vt_temp;
	double tcomp = vt_temp * facln;
	double ni_nom = NI * Math.pow(m_tnom/300.0,1.5) *
	    Math.exp((Q/2)*(eg_tnom/BOLTZ)*(1/300.0 - 1/m_tnom));

	m_uo = GetOption("uo",600);
	m_kp = GetOption("kp",m_uo * m_cox * 1e-4);
	m_phi = Math.max(GetOption("phi",2.0 * vt_tnom * Math.log(m_nsub/ni_nom)),0.1);
	m_gamma = GetOption("gamma",Math.sqrt(2.0 * Q * EPSSIL * m_nsub)/m_cox);

	m_tpg = GetOption("tpg",1.0);
	double workfun = (m_tpg != 0.0) ?
	    m_type*(-m_tpg*(eg_tnom/2.0) - m_phi/2.0) :
	    -(eg_tnom/2.0) - m_type*(m_phi/2.0) - 0.05;
	m_nss = GetOption("nss",0);
	m_vfb = GetOption("vfb",workfun - (Q*m_nss*1e4)/m_cox);
	m_vto = GetOption("vto",m_vfb + m_type*(m_gamma*Math.sqrt(m_phi) + m_phi));

	m_xj = GetOption("xj",0.0);
	m_ld = GetOption("ld",0.75 * m_xj);
	m_wd = GetOption("wd",0.0);
	m_dw = GetOption("dw",GetOption("xw",0.0));		// two names!
	m_dl = GetOption("dl",GetOption("xl",0.0));		// two names!

	m_rd = GetOption("rd",0.0);
	m_rdc = GetOption("rdc",0.0);
	m_rs = GetOption("rs",0.0);
	m_rsc = GetOption("rsc",0.0);
	m_rsh = GetOption("rsh",0.0);

	m_is = GetOption("is",1e-14);
	m_js = GetOption("js",0.0);
	m_jsw = GetOption("jsw",0.0);
	m_cj = GetOption("cj",Math.sqrt((EPSSIL*Q*m_nsub)/(2.0*m_pb)));
	m_cjsw = GetOption("cjsw",0.0);
	m_mj = GetOption("mj",0.5);
	m_mjsw = GetOption("mjsw",0.33);
	m_cgbo = GetOption("cgbo",2*m_wd*m_cox);
	m_cgso = GetOption("cgso",m_ld*m_cox);
	m_cgdo = GetOption("cgdo",m_ld*m_cox);

	// compute derived parameters
	vbi = m_vto - m_type*m_gamma*Math.sqrt(m_phi);
	kp = m_kp;
	dw = -m_dw + 2.0*m_wd;
	dl = -m_dl + 2.0*m_ld;

	// temperature compensation
	pb = m_pb*tratio - tcomp;
	double tfactor = 400e-6*(m_temp - m_tnom) - pb/m_pb + 1.0;
	phi = m_phi*tratio - tcomp;
	sqrt_phi = Math.sqrt(phi);
	inv_2_sqrt_phi = 1.0/(2.0 * Math.sqrt(phi));
	is = m_is * Math.exp(facln);
	js = m_js * Math.exp(facln);
	jsw = m_jsw * Math.exp(facln);
	cj = m_cj * (1.0 + m_mj*tfactor);
	cjsw = m_cjsw * (1.0 + m_mjsw*tfactor);
	kp *= Math.pow(tratio,m_bex);
	vbi += m_type*(phi - m_phi)/2.0 + (eg_tnom - eg_temp)/2.0;
	vto = vbi + m_type*m_gamma*sqrt_phi;

	//System.out.print("model "+name+":");
	//System.out.print(" vto="+vto);
	//System.out.print(" kp="+kp);
	//System.out.println("");
	//System.out.print(" gamma="+m_gamma);
	//System.out.print(" phi="+phi);
	//System.out.print(" pb="+pb);
	//System.out.println("");
	//System.out.print(" cj="+cj);
	//System.out.print(" mj="+m_mj);
	//System.out.print(" cjsw="+cjsw);
	//System.out.print(" mjsw="+m_mjsw);
	//System.out.println("");
	//System.out.print(" tox="+m_tox);
	//System.out.print(" nsub="+m_nsub);
	//System.out.print(" xj="+m_xj);
	//System.out.print(" ld="+m_ld);
	//System.out.println("");
	//System.out.print(" uo="+m_uo);
	//System.out.println("");
    }

    // assumes vds >= 0.  Fills in m.vton, m.ids, m.gds, m.gm, m.gmbs.
    abstract public void ids_gds(SpiceMosfet m,double vds,double vgs,double vbs);
  
    // set up model-specific parameters for specified mosfet
    public void Setup(SpiceMosfet m,double l,double w) {
	m.leff = l - dl;
	m.weff = w - dw;
	m.beta = kp*(m.weff/m.leff);
    }
}
