// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceMosfet extends SpiceDevice {
    SpiceNetwork network;		// we'll need some node voltages
    int d,g,s,b;			// four terminal nodes
    SpiceMOSModel model;
    //boolean debug;
  
    double leff;			// effective channel length
    double weff;			// effective channel width
    double beta;			// kp*(weff/leff)
  
    double fn,eta,xjonxl,djonxj;	// level 3 parameters
    double oxideCap;

    double vgso,vgdo,vdso,vbso,vbdo;
    double vth,ids,gds,gm,gmbs;	// set by model.ids_gds()

    SpiceMosfetDiode sdiode,ddiode;

    SpiceCell ss,sd,sb,sg;	// admittance matrix entries
    SpiceCell dd,ds,db,dg;
    SpiceCell bb,bs,bd;
    SpiceCell s_s,s_d;		// source vector entries

    public SpiceMosfet(SpiceNetwork net,int nd,int ng,int ns,int nb,
		       SpiceMOSModel m,double l,double w,double sl,double sw,
		       double ad,double pd,double nrd,double rdc,
		       double as,double ps,double nrs,double rsc) {
	network = net;
	d = nd;
	g = ng;
	s = ns;
	b = nb;
	model = m;

	// compute effective channel size
	if (l == 0) l = sl * network.scale;
	if (w == 0) w = sw * network.scale;
    
	model.Setup(this,l,w);		// setup model-specific parameters

	// default missing geometric/electrical parameters
	if (as == 0) as = network.defas;
	if (ps == 0) ps = network.defps;
	if (nrs == 0) nrs = network.defnrs;
	if (rsc == 0) rsc = model.m_rsc;
	if (ad == 0) ad = network.defad;
	if (pd == 0) pd = network.defpd;
	if (nrd == 0) nrd = network.defnrs;
	if (rdc == 0) rdc = model.m_rdc;

	// for now: add a vanilla capacitor for gate
	new SpiceCapacitor(net,g,b,w*l*m.m_cox + w*(m.m_cgso + m.m_cgdo) + l*m.m_cgbo);

	// approximate diffusion widths if user hasn't supplied dimensions
	double diffWidth = Math.min(w,l);
	if (as==0 && ps==0) { as = w * diffWidth; ps = w + 2*diffWidth; }
	if (ad==0 && pd==0) { ad = w * diffWidth; pd = w + 2*diffWidth; }

	// add source diode
	double rsource = nrs*model.m_rsh;
	if (rsource <= 0) rsource = model.m_rs;
	sdiode = new SpiceMosfetDiode(this,s,b,rsource+rsc,as,ps);
	s = sdiode.diff;
    
	// add drain diode
	double rdrain = nrd*model.m_rsh;
	if (rdrain <= 0) rdrain = model.m_rd;
	ddiode = new SpiceMosfetDiode(this,d,b,rdrain+rdc,ad,pd);
	d = ddiode.diff;

	// get all the matrix cells we'll need
	ss = network.FindMatrixElement(s,s);
	sd = network.FindMatrixElement(s,d);
	sb = network.FindMatrixElement(s,b);
	sg = network.FindMatrixElement(s,g);
	dd = network.FindMatrixElement(d,d);
	ds = network.FindMatrixElement(d,s);
	db = network.FindMatrixElement(d,b);
	dg = network.FindMatrixElement(d,g);
	bb = network.FindMatrixElement(b,b);
	bs = network.FindMatrixElement(b,s);
	bd = network.FindMatrixElement(b,d);

	s_s = network.FindSourceElement(s);
	s_d = network.FindSourceElement(d);

	// add minimum conductances channels
	double gmin = network.gmin;
	dd.gExp += gmin;	// channel
	ds.gExp -= gmin;
	sd.gExp -= gmin;
	ss.gExp += gmin;

	// initialize remembered voltages
	vgso = model.vto;
	vgdo = model.vto;
	vdso = 0;
	vbso = -1;
	vbdo = -1;
	vth = model.vbi;

	// we need to set up source vector and admittance matrix each iteration
	iterationLink = network.eachIteration;
	network.eachIteration = this;
    }

    // use this constructor if you just want to query model...
    public SpiceMosfet(SpiceMOSModel m,double l,double w) {
	model = m;
	model.Setup(this,l,w);		// setup model-specific parameters
    }

    public void QueryModel(double vds,double vgs,double vbs) {
	model.ids_gds(this,vds,vgs,vbs);	// fill in ids, gds, gm, gmbs
    }

    public boolean EachIteration(int mode,double time,double timestep) {
	double type = model.m_type;
	double vt = model.vt_temp;

	// compute relative terminal voltages
	double vs = (s < 0) ? 0 : network.solution[s];
	double vds = type*(((d < 0) ? 0 : network.solution[d]) - vs);
	double vgs = type*(((g < 0) ? 0 : network.solution[g]) - vs);
	double vbs = type*(((b < 0) ? 0 : network.solution[b]) - vs);
	double vgd = vgs - vds;

	/*if (debug) {
	    System.out.println(network.DeviceName(this)+": vd="+(type*vds+vs)+" vg="+(type*vgs+vs)+" vb="+(type*vbs+vs)+" vs="+vs);
	    }*/

	// limit mos voltages
	if (vdso < 0) {
	    vgdo = model.fetlim(vgd,vgdo,vth);
	    vdso = -model.limvds(vgdo - vgs,-vdso);
	    vgso = vgdo + vdso;
	} else {
	    vgso = model.fetlim(vgs,vgso,vth);
	    vdso = model.limvds(vgso - vgd,vdso);
	    vgdo = vgso - vdso;
	}

	if (vdso < 0) {		// use newly calculated vdso
	    //double x = vbdo;
	    vbdo = model.pnjlim(vt,vbs - vds,vbdo,ddiode.vcrit);
	    //if (model.limited) System.out.println("vt="+vt+" vnew="+(vbs-vds)+" vold="+x+" dvcrit="+ddiode.vcrit);
	    vbso = vbdo + vdso;
	} else {
	    //double x = vbso;
	    vbso = model.pnjlim(vt,vbs,vbso,sdiode.vcrit);
	    //if (model.limited) { System.out.println("vt="+vt+" vnew="+vbs+" vold="+x+" svcrit="+sdiode.vcrit);	    System.out.println(network.DeviceName(this)+": vd="+(type*vds+vs)+" vg="+(type*vgs+vs)+" vb="+(type*vbs+vs)+" vs="+vs); }

	    vbdo = vbso - vdso;
	}
	// don't quit until we stop limiting PN junction voltages
	boolean converged = !model.limited;

	// calculate mos diode contributions
	sdiode.DiodeCurrents(vbso,mode);
	ddiode.DiodeCurrents(vbdo,mode);

	/*if (debug) {
	    System.out.println(network.DeviceName(this)+": vds="+vds+" vgs="+vgs+" vbs="+vbs+" vgd="+vgd);
	    System.out.println(network.DeviceName(this)+": vdso="+vdso+" vgso="+vgso+" vgdo="+vgdo+" vbso="+vbso+" vbdo="+vbdo);
	    }*/

	// check for mode of operation (normal or inverse)
	double ieqds;
	if (vdso < 0) {		// inverse region of operation
	    model.ids_gds(this,-vdso,vgdo,vbdo);	// fill in ids, gds, gm, gmbs
	    ieqds = -type*(ids - gds*(-vdso) - gm*vgdo - gmbs*vbdo);
	    dd.luExp += gm + gmbs;
	    dg.luExp -= gm;
	    db.luExp -= gmbs;
	    sg.luExp += gm;
	    sb.luExp += gmbs;
	    sd.luExp -= gm + gmbs;
	    //System.out.println("d="+d+" g="+g+" s="+s+" b="+b);
	    //System.out.println("dd="+dd+" dg="+dg+" db="+db+" sg="+sg+" sb="+sb+" sd="+sd);
	} else {
	    model.ids_gds(this,vdso,vgso,vbso);	// fill in ids, gds, gm, gmbs
	    ieqds = type*(ids - gds*vdso - gm*vgso - gmbs*vbso);
	    ss.luExp += gm + gmbs;
	    dg.luExp += gm;
	    db.luExp += gmbs;
	    ds.luExp -= gm + gmbs;
	    sg.luExp -= gm;
	    sb.luExp -= gmbs;
	    //System.out.println("d="+d+" g="+g+" s="+s+" b="+b);
	    //System.out.println("ss="+ss+" sg="+sg+" sb="+sb+" dg="+dg+" db="+db+" ds="+ds);
	}

	/*if (debug) {
	    System.out.println(network.DeviceName(this)+": ids="+ids+" ieqds="+ieqds+" gds="+gds+" gm="+gm+" gmbs="+gmbs);
	    }*/

	s_d.luExp -= ieqds;
	s_s.luExp += ieqds;
	dd.luExp += gds;
	ss.luExp += gds;
	ds.luExp -= gds;
	sd.luExp -= gds;

	return converged;
	//return (Math.abs(vds-vdso) + Math.abs(vgs-vgso) + Math.abs(vbs-vbso) + Math.abs(vgd-vgdo)) < 1e-4;
    }

    public void RestoreState(double time) {
	double type = model.m_type;
	double vs = (s < 0) ? 0 : network.solution[s];
	double vd = (d < 0) ? 0 : network.solution[d];
	double vg = (g < 0) ? 0 : network.solution[g];
	double vb = (b < 0) ? 0 : network.solution[b];

	vgso = type*(vg - vs);
	vgdo = type*(vg - vd);
	vdso = type*(vd - vs);
	vbso = type*(vb - vs);
	vbdo = type*(vb - vd);
    }
}
