// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

// single-frequency FM source function
class SpiceSFFMSource extends SpiceSource {
    double offset;		// offset
    double amplitude;		// amplitude
    double fcarrier;		// carrier frequency
    double mindex;		// modulation index
    double fsignal;		// signal frequency

    public SpiceSFFMSource(double xdc,double xacmag,double xacphase,double params[],double vil,double vih) {
	super(xdc,xacmag,xacphase);

	offset = (params == null || params.length < 1) ? 0 : params[0];
	amplitude = (params == null || params.length < 2) ? 0 : params[1];
	fcarrier = (params == null || params.length < 3) ? 0 : params[2];
	mindex = (params == null || params.length < 4) ? 0 : params[3];
	fsignal = (params == null || params.length < 5) ? 0 : params[4];

	fcarrier *= 2*Math.PI;
	fsignal *= 2*Math.PI;
    }

    public String SourceName() { return "SFFM"; }

    public double TransientValue(double time) {
	return offset + amplitude*Math.sin(fcarrier*time + mindex*Math.sin(fsignal*time));
    }
}
