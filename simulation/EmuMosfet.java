// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class EmuMosfet extends EmuDevice {
    EmuNode d;
    EmuNode g;
    EmuNode s;
    EmuNode b;
    double w;
    double l;
    Object model;

    double vds;		// vds at which ids and gds were computed
    double gds;
    double ids;

    public EmuMosfet(EmuNode d,EmuNode g,EmuNode s,EmuNode b,
			SpiceMOSModel m,double l,double w,
			double ad,double pd,double as,double ps) {
	this.d = d;
	this.g = g;
	this.s = s;
	this.b = b;
	this.w = w;
	this.l = l;
	model = m;

	// approximate diffusion widths if user hasn't supplied dimensions
	double diffWidth = Math.min(w,l);
	if (as==0 && ps==0) { as = w * diffWidth; ps = w + 2*diffWidth; }
	if (ad==0 && pd==0) { ad = w * diffWidth; pd = w + 2*diffWidth; }

	// add gate and diffusion capacitors
	g.capacitance += w*l*m.m_cox + w*(m.m_cgso + m.m_cgdo) + l*m.m_cgbo;
	d.capacitance += ad*m.cj + pd*m.cjsw;
	s.capacitance += as*m.cj + ps*m.cjsw;

	d.AddDevice(this);
	s.AddDevice(this);
    }

    public void AddToRegion(EmuRegion r,EmuNode n) {
	super.AddToRegion(r,(n == d) ? s : d);
    }

    public void Finalize() {
	// build table-driven model
	model = region.network.MakeEmuMOSModel((SpiceMOSModel)model,w,l);

	g.AddFanoutRegion(region);
	b.AddFanoutRegion(region);
    }

    public void Reset() {
	gds = 0;
	ids = 0;
	vds = 0;
    }

    void RecomputeIdsGds(double vg,double vd,double vs,double vb) {
	double previousI = ids;

	// compute ids, gds using model...
	((EmuMOSModel)model).ids_gds(this,vg,vd,vs,vb);

	// update drain and source info on current flows
	double deltaI = ids - previousI;
	d.totalCurrent -= deltaI;
	s.totalCurrent += deltaI;
	d.totalTransconductance += gds;
	s.totalTransconductance += gds;

	vds = vd - vs;

	//System.out.println("vd="+vd+" vs="+vs+" vg="+vg+" vb="+vb+" ids="+ids+" gds="+gds);
    }

    public double Incremental(EmuNode n,double timestep) {
	double nvd = d.voltage + d.deltaV;
	double nvs = s.voltage + s.deltaV;
	// recompute ids and gds if voltages change a lot
	if (Math.abs((nvd - nvs) - vds) > .5) {
	    d.totalTransconductance -= gds;
	    s.totalTransconductance -= gds;
	    RecomputeIdsGds(g.voltage,nvs,nvd,b.voltage);
	}

	return gds*((n == d) ? s.deltaV : d.deltaV);
    }

    public double Update(double time) {
	RecomputeIdsGds(g.voltage,s.voltage,d.voltage,b.voltage);
	return EmuRegion.NO_EVENT;
    }
}
