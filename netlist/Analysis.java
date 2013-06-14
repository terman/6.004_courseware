// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package netlist;

import java.util.ArrayList;

// Each requested analysis is represented by an instance of this class
// and added (in order) to the analyses vector of the current network
public class Analysis {
    static public final int OperatingPoint = 1;
    static public final int DC = 2;
    static public final int Transient = 3;
    static public final int AC = 4;

    public int type;		// type of analysis
    public Identifier command;	// used for reporting errors
    public ArrayList params;	// analysis parameters
    public ArrayList plots;	// each element is a vector of PlotRequests

    public Analysis(Identifier cmd,int atype) {
	command = cmd;
	type = atype;
	params = new ArrayList();
	plots = new ArrayList();
    }

    // convert string into appropriate analysis type
    static int AnalysisType(String name) {
	if (name.equalsIgnoreCase("op")) return OperatingPoint;
	else if (name.equalsIgnoreCase("dc")) return DC;
	else if (name.equalsIgnoreCase("tran")) return Transient;
	else if (name.equalsIgnoreCase("ac")) return AC;
	else return 0;
    }

    // look for most recent analysis of a given type
    static Analysis FindAnalysis(Netlist network,String name) {
	int atype = AnalysisType(name);
	ArrayList analyses = network.analyses;

	// search backwards through analyses vector for type we want
	for (int i = analyses.size() - 1; i >= 0; i -= 1) {
	    Analysis a = (Analysis)analyses.get(i);
	    if (a.type == atype) return a;
	}
	return null;
    }
}
