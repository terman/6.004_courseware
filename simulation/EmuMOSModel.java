// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class EmuMOSModel {
    static final int VSBSIZE = 100;
    static final int VSATSIZE = 20;
    static final int VSATMIN = -1;
    static final int VDSSIZE = 50;

    SpiceMOSModel model;	// parameters for this model
    double width;
    double length;
    boolean nmos;

    // 1D table lookup for VSB -> VTH
    double vsbQuanta;
    double vthTable[];

    // 2D table lookup for VSAT, VDS -> IDS, GDS, GM
    double vsatQuanta;
    double vdsQuanta;
    double idsTable[];
    double gdsTable[];
    double gmTable[];

    public EmuMOSModel(SpiceMOSModel m,double w,double l,double vmax) {
	model = m;
	width = w;
	length = l;
	nmos = (m.m_type == 1);

	// we'll be using our Spice setup to build the tables
	SpiceMosfet fet = new SpiceMosfet(m,l,w);

	// set up VTH table
	vsbQuanta = vmax/VSBSIZE;
	vthTable = new double[VSBSIZE];
	for (int i = 0; i < VSBSIZE; i += 1) {
	    fet.QueryModel(0,0,-(i*vsbQuanta));
	    vthTable[i] = fet.vth;
	}

	// set up IDS, GDS and GM tables
	vsatQuanta = (vmax - VSATMIN)/VSATSIZE;
	vdsQuanta = vmax/VDSSIZE;
	idsTable = new double[VSATSIZE * VDSSIZE];
	gdsTable = new double[VSATSIZE * VDSSIZE];
	gmTable = new double[VSATSIZE * VDSSIZE];
	for (int i = 0; i < VSATSIZE; i += 1)
	    for (int j = 0; j < VDSSIZE; j += 1) {
		try {
		// vgs = vsat + vth
		double vgs = (i*vsatQuanta + VSATMIN) + vthTable[0];
		fet.QueryModel(j*vdsQuanta,vgs,0);

		int index = i*VDSSIZE + j;
		idsTable[index] = fet.ids;

		if (j > 0)
		    gdsTable[index-1] = (fet.ids - idsTable[index-1])/vdsQuanta;
		if (j == VDSSIZE - 1)
		    gdsTable[index] = gdsTable[index-1];

		int prevrow = index - VDSSIZE;
		if (i > 0)
		    gmTable[prevrow] = (fet.ids - idsTable[prevrow])/vsatQuanta;
		if (i == VSATSIZE - 1) 
		    gmTable[index] = gmTable[prevrow];
		}
		catch (Exception e) {
		    System.out.println("i="+i+" j="+j+" e="+e);
		}
	    }
    }

    public boolean Match(SpiceMOSModel m,double w,double l) {
	return m==model && w==width && l==length;
    }

    public void ids_gds(EmuMosfet fet,double vg,double vs,double vd,double vb) {
	double vds,vgs,vsb,sign;

	if (nmos) {
	    if (vd > vs) {
		vds = vd - vs;
		vgs = vg - vs;
		vsb = vs - vb;
		sign = 1;
	    } else {
		vds = vs - vd;
		vgs = vg - vd;
		vsb = vd - vb;
		sign = -1;
	    }
	} else {
	    if (vd > vs) {
		vds = vd - vs;
		vgs = vd - vg;
		vsb = vb - vd;
		sign = 1;
	    } else {
		vds = vs - vd;	
		vgs = vs - vg;
		vsb = vb - vs;
		sign = -1;
	    }
	}

	int vsbIndex;
	if (vsb < 0) vsbIndex = 0;
	else {
	    vsbIndex = (int)Math.floor(vsb/vsbQuanta);
	    if (vsbIndex >= VSBSIZE) vsbIndex = VSBSIZE - 1;
	}
	double vsat = vgs - vthTable[vsbIndex];
	//System.out.println("vsbIndex="+vsbIndex+" vsat="+vsat);
					  
	// we assume that the device is off when vsat < VSATMIN,
	// and start our table at vsat = VSATMIN
	if (vsat < VSATMIN) {
	    fet.ids = 0;
	    fet.gds = 0;
	} else {
	    vsat -= VSATMIN;
	    int vsatIndex = (int)Math.floor(vsat/vsatQuanta);
	    if (vsatIndex >= VSATSIZE) vsatIndex = VSATSIZE - 1;

	    // we know vds >= 0
	    int vdsIndex = (int)Math.floor(vds/vdsQuanta);
	    if (vdsIndex >= VDSSIZE) vdsIndex = VDSSIZE - 1;

	    int index = vsatIndex * VDSSIZE + vdsIndex;
	    fet.gds = gdsTable[index];
	    // use GDS and GM to compensate for the fact that we
	    // rounded off vsat and vds when we accessed the table
	    fet.ids = sign*(idsTable[index]
			    + fet.gds*(vds - vdsIndex*vdsQuanta)
			    + gmTable[index]*(vsat - vsatIndex*vsatQuanta));
	}
    }
}
