// Copyright (C) 2001 Christopher J. Terman - All Rights Reserved.

package netlist;

import gui.UI;
import java.io.PrintWriter;
import java.util.ArrayList;
import plot.DigitalPlotCoordinate;
import plot.PlotData;
import simulation.Network;

public class VerifyData {
    static public final String PERIODIC = "periodic";
    static public final String TVPAIRS = "tvpairs";

    public String nodes;
    public String type;
    public ArrayList params;
    public ArrayList data;
    private int checksum;

    public VerifyData(String nodes,String type,ArrayList params,ArrayList data) {
	this.nodes = nodes;
	this.type = type;
	this.params = params;
	this.data = data;
    }

    // see if type is something we can handle
    public boolean ValidType() {
	if (type.equals(PERIODIC)) return true;
	else if (type.equals(TVPAIRS)) return true;
	else return false;
    }

    private String VerifyDataValue(double time,long expect,PlotData d) {
	DigitalPlotCoordinate c = (DigitalPlotCoordinate)d.FindCoordinate(time);
	if (c == null || !c.Match(expect)) {
	    String e = d.toBinaryString(expect);
	    StringBuffer aa = new StringBuffer(c == null ? "... no value ..." : "0b");

	    // highlight mismatches in red
	    if (c != null) {
		String a = c.toBinaryString();
		boolean red = false;
		for (int i = 0; i < e.length(); i += 1) {
		    int ch = a.charAt(i);
		    if (e.charAt(i) != ch) {
			if (!red) {
			    aa.append("<font color=red>");
			    red = true;
			}
		    } else if (red) {
			aa.append("</font>");
			red = false;
		    }
		    aa.append((char)ch);
		}
		if (red) aa.append("</font>");
	    }

	    return
		"<font size=5>Node value verification error...</font><tt><ul>"
		+"<li>node(s):&nbsp;&nbsp;"+nodes
		+"<li>time:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+UI.EngineeringNotation(time,3)+"s"
		+"<li>expected:&nbsp;0b"+e
		+"<li>actual:&nbsp;&nbsp;&nbsp;"+aa.toString()
		+"</tt></ul>";
	}
	return null;
    }

    private String VerifyPeriodicData(Network network) {
	// process sampling parameters
	if (params.size() != 2)
	    return "<font size=5>Internal error...</font><p>expected 2 parameters for PERIODIC sampling";
	double time = ((Number)(params.get(0))).value;
	double period = ((Number)(params.get(1))).value;

	// get actual values from network
	ArrayList dvector = network.RetrieveDigitalPlotData(nodes);
	if (dvector == null) {
	    return "<font size=5>Internal error...</font><p>can't get simulation data for "+nodes;
	}
	if (dvector.size() != 1) {
	    return "<font size=5>Internal error...</font><p>expected one-element vector, got "+dvector.size();
	}
	PlotData d = (PlotData)dvector.get(0);

	// check actual values vs. expected values
	int ndata = data.size();
	for (int i = 0; i < ndata; i += 1) {
	    long expect = (long)((Number)(data.get(i))).value;
	    String check = VerifyDataValue(time,expect,d);
	    if (check != null) return check;
	    checksum += (i+1)*((int)(time*1e12) + (int)expect);
	    time += period;
	}

	return null;
    }

    private String VerifyTVPairs(Network network) {
	// process sampling parameters
	if (params.size() != 0)
	    return "<font size=5>Internal error...</font><p>expected 0 parameters for TVPAIR sampling";

	// get actual values from network
	ArrayList dvector = network.RetrieveDigitalPlotData(nodes);
	if (dvector == null) {
	    return "<font size=5>Internal error...</font><p>can't get simulation data for "+nodes;
	}
	if (dvector.size() != 1) {
	    return "<font size=5>Internal error...</font><p>expected one-element vector, got "+dvector.size();
	}
	PlotData d = (PlotData)dvector.get(0);

	// check actual values vs. expected values
	int ndata = data.size()-1;
	for (int i = 0; i < ndata; i += 2) {
	    double time = ((Number)(data.get(i))).value;
	    long expect = (long)((Number)(data.get(i+1))).value;
	    String check = VerifyDataValue(time,expect,d);
	    if (check != null) return check;
	    checksum += (i+1)*((int)(time*1e12) + (int)expect);
	}

	return null;
    }

    // see if simulation data matches specified data values
    public String Verify(Network network) {
	checksum = nodes.hashCode() + type.hashCode();

	if (type.equals(PERIODIC)) return VerifyPeriodicData(network);
	if (type.equals(TVPAIRS)) return VerifyTVPairs(network);
	else return "<font size=5>Internal error...<p>unrecognized sample specification in .verify: "+type;
    }

    private void GeneratePeriodicData(PrintWriter out,Network network) {
	// process sampling parameters
	if (params.size() != 2) {
	    out.println("expected 2 parameters for PERIODIC sampling");
	    return;
	}
	double time = ((Number)(params.get(0))).value;
	double period = ((Number)(params.get(1))).value;

	// get actual values from network
	ArrayList dvector = network.RetrieveDigitalPlotData(nodes);
	if (dvector == null) {
	    out.println("can't get simulation data for "+nodes);
	    return;
	}
	if (dvector.size() != 1) {
	    out.println("expected one-element vector, got "+dvector.size());
	    return;
	}
	PlotData d = (PlotData)dvector.get(0);

	out.print(".verify "+nodes+" periodic("+time+","+period+")\n");

	double stopTime = network.GetTime();
	while (time <= stopTime) {
	    DigitalPlotCoordinate c = (DigitalPlotCoordinate)d.FindCoordinate(time);
	    if (c != null) out.print("+ 0x"+c.toHexString()+"  // "+(int)(time*1e9 + 0.1)+"ns\n");
	    time += period;
	}
    }

    private void GenerateTVPairs(PrintWriter out,Network network) {
	// process sampling parameters
	if (params.size() != 2) {
	    out.println("expected 2 parameters for TVPAIRS sampling");
	    return;
	}
	double time = ((Number)(params.get(0))).value;
	double period = ((Number)(params.get(1))).value;

	// get actual values from network
	ArrayList dvector = network.RetrieveDigitalPlotData(nodes);
	if (dvector == null) {
	    out.println("can't get simulation data for "+nodes);
	    return;
	}
	if (dvector.size() != 1) {
	    out.println("expected one-element vector, got "+dvector.size());
	    return;
	}
	PlotData d = (PlotData)dvector.get(0);

	out.print(".verify "+nodes+" tvpairs() // from periodic("+time+","+period+")\n");

	double stopTime = network.GetTime();
	while (time <= stopTime) {
	    DigitalPlotCoordinate c = (DigitalPlotCoordinate)d.FindCoordinate(time);
	    if (c != null) out.print("+ "+(int)(time*1e9+.1)+"ns 0x"+c.toHexString()+"\n");
	    time += period;
	}
    }

    // see if simulation data matches specified data values
    public void GenerateCheckoff(Network network,PrintWriter out) {
	if (type.equals(PERIODIC)) GeneratePeriodicData(out,network);
	else if (type.equals(TVPAIRS)) GenerateTVPairs(out,network);
	else out.println("oops... unrecognized sample specification in .generatecheckoff: "+type);
    }

    // return results as a string

    private String GeneratePeriodicData(Network network) {
	// process sampling parameters
	if (params.size() != 2) {
	    return "error expected 2 parameters for PERIODIC sampling";
	}
	double time = ((Number)(params.get(0))).value;
	double period = ((Number)(params.get(1))).value;

	// get actual values from network
	ArrayList dvector = network.RetrieveDigitalPlotData(nodes);
	if (dvector == null) {
	    return "error can't get simulation data for "+nodes;
	}
	if (dvector.size() != 1) {
	    return "error expected one-element vector, got "+dvector.size();
	}
	PlotData d = (PlotData)dvector.get(0);

	StringBuffer result = new StringBuffer();
	result.append("verify "+nodes);

	double stopTime = network.GetTime();
	while (time <= stopTime) {
	    DigitalPlotCoordinate c = (DigitalPlotCoordinate)d.FindCoordinate(time);
	    if (c != null) result.append(" "+c.toBinaryString());
	    time += period;
	}
	return result.toString();
    }

    private String GenerateTVPairs(Network network) {
	// process sampling parameters
	if (params.size() != 0)
	    return "error expected 0 parameters for TVPAIR sampling";

	// get actual values from network
	ArrayList dvector = network.RetrieveDigitalPlotData(nodes);
	if (dvector == null) {
	    return "error can't get simulation data for "+nodes;
	}
	if (dvector.size() != 1) {
	    return "error expected one-element vector, got "+dvector.size();
	}
	PlotData d = (PlotData)dvector.get(0);

	StringBuffer result = new StringBuffer();
	result.append("verify "+nodes);

	// grab value for each specified time
	int ndata = data.size()-1;
	for (int i = 0; i < ndata; i += 2) {
	    double time = ((Number)(data.get(i))).value;
	    DigitalPlotCoordinate c = (DigitalPlotCoordinate)d.FindCoordinate(time);
	    if (c != null) result.append(" "+c.toBinaryString());
	}
	return result.toString();
    }

    public String GenerateCheckoff(Network network) {
	if (type.equals(PERIODIC)) return GeneratePeriodicData(network);
	else if (type.equals(TVPAIRS)) return GenerateTVPairs(network);
	else return "";
    }

    // compute our contribution to the checksum
    public long Checksum() {
	return checksum;
    }
}
