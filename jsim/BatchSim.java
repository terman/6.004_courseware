// Copyright (C) 1999-2011 Christopher J. Terman - All Rights Reserved.

package jsim;

import gui.GuiFrame;
import java.io.File;
import java.io.PrintStream;

import netlist.Netlist;
import netlist.NetlistConsumer;
import netlist.Analysis;
import netlist.Subcircuit;
import netlist.VerifyData;

import simulation.EmuNetwork;
import simulation.Network;
import simulation.SimNetwork;
import simulation.SpiceNetwork;

public class BatchSim extends GuiFrame {
    public int simulator = 0;   // 0: Simulate, 1: FastSimulate, 2: GateSimulate
    public String msg;
    public int start = 0;
    public int end = 0;

    public BatchSim(String args[]) throws java.io.IOException {
	super(args,"BatchSim",true,true);

	// look for switches
	for (int i = 0; i < cmdargs.length; i += 1) {
	    if (cmdargs[i].startsWith("-g")) simulator = 2;   // gate simulate
	    if (cmdargs[i].startsWith("-f")) simulator = 1;   // fast simulate
	    if (cmdargs[i].startsWith("-s")) simulator = 0;   // simulate
	}

	// look through command args; anything that's not a switch
	// should be the name of a directory full of files to simulate
	for (int i = 0; i < cmdargs.length; i += 1) {
	    if (cmdargs[i].startsWith("--")) i += 1;
	    else if (!cmdargs[i].startsWith("-")) {
		PrintStream report = new PrintStream(cmdargs[i]+"_report");
		File dir = new File(cmdargs[i]);
		File[] files = dir.listFiles();
		for (int j = 0; j < files.length; j += 1) {
		    report.println("file "+files[j].toString());

		    // read in the next netlist
		    Netlist n = new Netlist(this,files[j]);
		    switch (simulator) {
		    case 0: DoSimulate(n); break;
		    case 1: DoFastSimulate(n); break;
		    case 2: DoGateSimulate(n); break;
		    }
		    if (n.errors) {
			// report error
			report.println("error ["+start+":"+end+"] "+msg);
		    } else {
			// do verification
			int nverifications = n.verifications.size();
			for (int k = 0; k < nverifications; k += 1) {
			    VerifyData v = (VerifyData)n.verifications.get(k);
			    report.println(v.GenerateCheckoff(n.currentNetwork));
			}
		    }
		    n.CleanUp();  // done with this network
		}
		report.close();
		break;
	    }
	}
    }

    public void Simulate(Netlist n,Network s) {
	n.currentNetwork = s;

	// flatten our network
	if (n.Netlist((NetlistConsumer)s)) {
	    if (!s.Finalize()) {
		n.Error(s.Problem());
	    } else {
		// perform first analysis
		if (n.analyses.size() > 0) {
		    Analysis a = (Analysis)n.analyses.get(0);
		    switch (a.type) {
		    case Analysis.Transient:
			n.DoTransientAnalysis(s,a);
			break;
		    case Analysis.DC:
			n.DoDCAnalysis(s,a);
			break;
		    }
		}
	    }
	}
    }

    public void DoSimulate(Netlist n) {
	n.ReadNetlist(n,false,Subcircuit.DSUBCKT);	// parse netlist
	if (!n.errors) {
	    Simulate(n,new SpiceNetwork(n.options,n.tempdir));
	}
    }

    public void DoFastSimulate(Netlist n) {
	n.ReadNetlist(n,false,Subcircuit.DSUBCKT);	// parse netlist
	if (!n.errors) {
	    Simulate(n,new EmuNetwork(n.options,n.tempdir));
	}
    }

    public void DoGateSimulate(Netlist n) {
	n.ReadNetlist(n,false,Subcircuit.GSUBCKT);	// parse netlist
	if (!n.errors) {
	    Simulate(n,new SimNetwork(n.options,n.tempdir));
	}
    }


    public void Message(String msg,int start,int end) {
	this.msg = msg;
	this.start = start;
	this.end = end;
    }

    public void SetTab(java.awt.Component v) { }
    public void SetTab(int i) { }

    // used by stand-alone application
    public static void main(String args[]) {
	try {
	    new BatchSim(args);
	}
	catch (Exception e) {
	    System.out.println("Internal error: "+e);
	    e.printStackTrace(System.out);
	}
    }
}
