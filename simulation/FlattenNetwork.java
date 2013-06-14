// Copyright (C) 2010 Christopher J. Terman - All Rights Reserved.

package simulation;

import gui.ProgressTracker;
import gui.UI;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import javax.swing.JFileChooser;
import netlist.Parameter;

public class FlattenNetwork extends Network {
    static int MAXADDR = 24;
    ArrayList devices;		// linked list of network's devices
    ArrayList gndNodes;		// all the ground nodes the user defined

    class FNode extends Node {
	FNode merged;
	boolean gndNode;

	public FNode(String name) {
	    super(name);
	    merged = null;
	    gndNode = false;
	}

	public double GetValue(Network network) { return 0; }

	public String Name() {
	    FNode n = this;
	    while (n.merged != null) n = n.merged;
	    return n.name;
	}

	public void setGnd() {
	    gndNode = true;
	}

	public boolean isGnd() {
	    FNode n = this;
	    while (n.merged != null) n = n.merged;
	    return n.gndNode;
	}
    }

    class Device {
	String type;
	ArrayList nodes;
	Parameter params;

	public Device(String type,ArrayList nodes,Parameter params) {
	    this.type = type;
	    this.nodes = nodes;
	    this.params = params;
	}

	public void Write(PrintWriter w,String devfile) {
	    w.print(type);
	    Iterator iter = nodes.iterator();
	    while (iter.hasNext()) {
		FNode n = (FNode)iter.next();
		w.printf(" %s",n.Name());
	    }
	    w.print("\n");
	}
    }

    class Memory extends Device {
	public Memory(ArrayList nodes,Parameter params) {
	    super("memory",nodes,params);
	}

	public void Write(PrintWriter w,String devfile) {
	    int width = (int)GetParameter(params,"width",0);
	    int nlocations = (int)GetParameter(params,"nlocations",0);
	    String filename = GetStringParameter(params,"file",null);
	    double[] contents = GetVectorParameter(params,"contents");

	    int naddr;
	    for (naddr = 1; naddr < 32; naddr += 1)
		if ((1 << naddr) >= nlocations) break;

	    int nportbits = 3 + naddr + width;
	    int nports = nodes.size() / nportbits;

	    // if no filename specified, figure out one to use
	    if (filename == null) {
		// no contents, so supply "no file" filename
		if (contents == null) filename = "-";
		else {
		    // dump contents into a new file and use that filename
		    File m = new File(devfile);
		    filename = m.getName();
		    try {
			PrintWriter ww = new PrintWriter(m);
			for (int i = 0; i < contents.length; i += 1)
			    ww.printf("%x\n",(int)contents[i]);
			ww.close();
		    }
		    catch (java.io.FileNotFoundException e) {
			filename = "-";
		    }
		}
	    }

	    w.printf("memory %d %d %s",nlocations,width,filename);
	    for (int p = 0; p < nports; p += 1) {
		int offset = p * nportbits;
		// check for read port
		FNode oe = (FNode)nodes.get(offset);
		if (!oe.isGnd()) {
		    w.printf(" r %s",oe.Name());
		    for (int i = 0; i < naddr; i = i+1) {
			FNode a = (FNode)nodes.get(offset + 3 + i);
			w.printf(" %s",a.Name());
		    }
		    for (int i = width-1; i >= 0; i = i-1) {
			FNode d = (FNode)nodes.get(offset + 3 + naddr + i);
			w.printf(" %s",d.Name());
		    }
		}
		// check for write port
		FNode clk = (FNode)nodes.get(offset+1);
		FNode we = (FNode)nodes.get(offset+2);
		if (!clk.isGnd()) {
		    w.printf(" w %s %s ",clk.Name(),we.Name());
		    for (int i = 0; i < naddr; i = i+1) {
			FNode a = (FNode)nodes.get(offset + 3 + i);
			w.printf(" %s",a.Name());
		    }
		    for (int i = width-1; i >= 0; i = i-1) {
			FNode d = (FNode)nodes.get(offset + 3 + naddr + i);
			w.printf(" %s",d.Name());
		    }
		}
	    }
	    w.print("\n");
	}
    }

    public FlattenNetwork(HashMap options,String tempdir) {
	super(options,tempdir);
	devices = new ArrayList();
	gndNodes = new ArrayList();
    }

    public String Size() {
	String result = devices.size()+" devices";
	return result;
    }

    public double GetTime() { return 0; }

    public Object FindDevice(String name) {
	return null;
    }

    public Object FindNode(String name,boolean create) {
	FNode n = (FNode)nodes.get(name);
	if (n == null && create) {
	    n = new FNode(name);
	    nodes.put(name,n);
	} else if (n != null && n.merged != null) {
	    while (n.merged != null) n = n.merged;
	    nodes.put(name,n);
	}
	return n;
    }

    public Object MakeGndNode(String name) {
	FNode n = (FNode)FindNode(name,true);
	if (!gndNodes.contains(n)) {
	    gndNodes.add(n);
	    n.setGnd();
	    ArrayList nodes = new ArrayList();
	    nodes.add(n);
	    devices.add(new Device("or",nodes,null));
	}
	return n;
    }

    // finish setting up the network after all nodes and devices
    // have been added
    public boolean Finalize() {
	return Finalize(false);
    }

    public boolean Finalize(boolean allowUndrivenNodes) {
	if (invalidDevice) return false;

	// merge nodes as requested by user
	int nmerges = mergedNodes.size();
	for (int i = 0; i < nmerges; i += 2) {
	    SimNode n1 = (SimNode)mergedNodes.get(i);
	    SimNode n2 = (SimNode)mergedNodes.get(i+1);
	    while (n1.merged != null) n1 = n1.merged;
	    while (n2.merged != null) n2 = n2.merged;
	    if (n1 != n2) n1.Merge(n2);
	}
	mergedNodes.clear();

	// finalize each node
	Iterator iter = nodes.values().iterator();
	while (iter.hasNext()) {
	    FNode n = (FNode)iter.next();
	    if (n.merged != null) {
		String name = n.name;
		while (n.merged != null) n = n.merged;
		nodes.put(name,n);
	    }
	}

	JFileChooser fc = new JFileChooser();
	int returnVal = fc.showSaveDialog(null);
	int count = 0;
	if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
	    try {
		String filename = file.getCanonicalPath();
		PrintWriter w = new PrintWriter(file);
		iter = devices.iterator();
		while (iter.hasNext()) {
		    Device d = (Device)iter.next();
		    count += 1;
		    d.Write(w,filename+"-"+count);
		}
		w.close();
	    }
	    catch (java.io.FileNotFoundException e) {
	    }
	    catch (java.io.IOException e) {
	    }
	}

	return true;
    }

    public boolean TransientAnalysis(double stopTime,double maxTimestep,ProgressTracker jpanel) {
	problem = "Transient Analysis not available!";
	return false;
    }

    public boolean DCAnalysis(String sweep1,double start1,double stop1,double step1,
			      String sweep2,double start2,double stop2,double step2,
			      ProgressTracker jpanel) {
	problem = "DC Analysis not available!";
	return false;
    }

    public boolean MakeIndependentVoltageSource(String id,Object npos,Object nneg,
					     double dc,double acmag,double acphase,
					     int trantype,double params[]) {
	// "neg" terminal has to be attached to ground
	if (!gndNodes.contains((FNode)nneg)) {
	    problem = "Can't simulate voltage source with NEG terminal not gnd "+id;
	    invalidDevice = true;
	    return false;
	}

	// DC voltage sources get turned into power supplies.
	FNode n = (FNode)npos;
	ArrayList nodes = new ArrayList();
	nodes.add(n);
	if (trantype == 0) {
	    int v = VtoL(dc);
	    String tbl = (v == Node.V1) ? "and" : "or";
	    devices.add(new Device(tbl,nodes,null));
	    if (v == Node.V0) {
		n.setGnd();
		gndNodes.add(n);
	    }
	} else {
	    problem = "Non-constant voltage source not available in flattening!";
	    invalidDevice = true;
	    return false;
	}
	return true;
    }

    public boolean MakeGate(String id,String function,ArrayList nodes,Parameter params) {
	String tbl = null;
	if (function.equalsIgnoreCase("$nand"))
	    tbl = "nand";
	else if (function.equalsIgnoreCase("$nor"))
	    tbl = "nor";
	else if (function.equalsIgnoreCase("$and"))
	    tbl = "and";
	else if (function.equalsIgnoreCase("$or"))
	    tbl = "or";
	else if (function.equalsIgnoreCase("$xor"))
	    tbl = "xor";
	else if (function.equalsIgnoreCase("$xnor"))
	    tbl = "xnor";
	else if (function.equalsIgnoreCase("$mux2")) {
	    if (nodes.size() != 4) {
		problem = "$mux2 requires exactly four nodes to be specified";
		invalidDevice = true;
		return false;
	    }
	    tbl = "mux2";
	} else if (function.equalsIgnoreCase("$tristate_buffer")) {
	    if (nodes.size() != 3) {
		problem = "$tristate_buffer requires exactly three nodes to be specified";
		invalidDevice = true;
		return false;
	    }
	    tbl = "tristate-buf";
	} else if (function.equalsIgnoreCase("$dreg")) {
	    if (nodes.size() != 3) {
		problem = "$dreg requires exactly three nodes to be specified";
		invalidDevice = true;
		return false;
	    }
	    tbl = "dreg";
	} else if (function.equalsIgnoreCase("$dlatch")) {
	    if (nodes.size() != 3) {
		problem = "$dlatch requires exactly three nodes to be specified";
		invalidDevice = true;
		return false;
	    }
	    tbl = "lp";
	} else if (function.equalsIgnoreCase("$memory")) {
	    int width = (int)GetParameter(params,"width",0);
	    int nlocations = (int)GetParameter(params,"nlocations",0);

	    if (width < 1 || width > 32) {
		problem = "memory must have between 1 and 32 bits";
		invalidDevice = true;
		return false;
	    }

	    int maxlocs = 1 << MAXADDR;
	    if (nlocations < 1 || nlocations > maxlocs) {
		problem = "memory must have between 1 and "+maxlocs+" locations";
		invalidDevice = true;
		return false;
	    }

	    int naddr;
	    for (naddr = 1; naddr < 32; naddr += 1)
		if ((1 << naddr) >= nlocations) break;

	    int nportbits = 3 + naddr + width;
	    if ((nodes.size() % nportbits) != 0) {
		problem = "wrong number of terminals, expected multiple of "+nportbits+" (3 control, "+naddr+" address, "+width+" data)";
		invalidDevice = true;
		return false;
	    }

	    devices.add(new Memory(nodes,params));
	    return true;
	}

	if (tbl == null) {
	    problem = "Unrecognized built-in gate "+function;
	    invalidDevice = true;
	    return false;
	} else {
	    devices.add(new Device(tbl,nodes,params));
	    return true;
	}
    }

    public String SimulationType() { return "none"; }

    static double GetParameter(Parameter params,String name,double vdefault) {
	if (params == null) return vdefault;
	return params.Value(name,vdefault);
    }

    static String GetStringParameter(Parameter params,String name,String vdefault) {
	if (params == null) return vdefault;
	return params.SValue(name,vdefault);
    }

    static double[] GetVectorParameter(Parameter params,String name) {
	if (params == null) return null;
	return params.VValue(name,null);
    }
}
