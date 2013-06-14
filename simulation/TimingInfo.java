// Copyright (C) 2007 Christopher J. Terman - All Rights Reserved.

package simulation;

public class TimingInfo {
    public SimNode node;	// what node this info is associated with
    public SimDevice device;    // what device determined this info
    public double tCDsum;	// min cummulative tCD from inputs to here
    public TimingInfo tCDlink;	// previous link in tCD path
    public double tPDsum;	// max cummulative tPD from inputs to here
    public TimingInfo tPDlink;	// previous link in tPD path

    public double tCD;		// specs for driving gate
    public double tPD;		// already accounts for node capacitance

    public TimingInfo(SimNode node,SimDevice device) {
	this.node = node;
	this.device = device;
	tCDsum = 0;
	tCDlink = null;
	tPDsum = 0;
	tPDlink = null;
	tCD = 0;
	tPD = 0;
    }

    public SimNode getTCDSource() {
	TimingInfo t = this;
	while (t.tCDlink != null) t = t.tCDlink;
	return t.node;
    }

    public SimNode getTPDSource() {
	TimingInfo t = this;
	while (t.tPDlink != null) t = t.tPDlink;
	return t.node;
    }

    public void setSpecs(double tCD,double tPD) {
	this.tCD = tCD;
	this.tPD = tPD;
    }

    // keep track of min tCD as we consider each path
    public void setDelays(TimingInfo link) {
	double t;

	// update min tCD
	t = link.tCDsum + tCD;
	if (tCDlink == null || t < tCDsum) {
	    tCDlink = link;
	    tCDsum = t;
	}

	// update max tPD
	t = link.tPDsum + tPD;
	if (tPDlink == null || t > tPDsum) {
	    tPDlink = link;
	    tPDsum = t;
	}
    }

    // recursively print out tPD path
    public void printTPD(java.io.PrintWriter out,int indent) {
	if (tPDlink != null) tPDlink.printTPD(out,indent);

	// skip internal nodes with tPD's of zero
	if (node.isInput() || tPD != 0) {
	    String driverName = "";
	    if (device != null) driverName = " [" + device.name + "]";

	    for (int i = 0; i < indent; i +=1) out.print(' ');
	    out.format("+ %1$5.3fns = %2$5.3fns %3$s%4$s\n",tPD*1e9,tPDsum*1e9,node.name,driverName);
	}
    }

    // recursively print out tCD path
    public void printTCD(java.io.PrintWriter out,int indent) {
	if (tCDlink != null) tCDlink.printTCD(out,indent);

	String driverName = "";
	if (device != null) driverName = " [" + device.name + "]";

	for (int i = 0; i < indent; i +=1) out.print(' ');
	out.format("%s %2$5.3fns = %3$5.3fns %4$s%5$s\n",tCD >= 0 ? "+":"-",Math.abs(tCD)*1e9,tCDsum*1e9,node.name,driverName);
    }
}
