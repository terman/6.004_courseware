// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

import gui.ProgressTracker;
import gui.UI;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import netlist.NetlistConsumer;
import netlist.Parameter;
import plot.AnalogPlotCoordinate;
import plot.DigitalPlotCoordinate;
import plot.PlotData;

abstract public class Network implements NetlistConsumer {
    // solution modes
    static final int OPERATING_POINT = 1;
    static final int TRANSIENT_ANALYSIS = 2;
    static final int TRANSIENT_ANALYSIS_INIT = 3;
    static final int DC_ANALYSIS = 4;

    static final int RSIZE = 16;	// size of a history record
    static final int BSIZE = 16;	// log base 2 of number of records
					// in the memory buffer

    int mode;			// mode of last simulation
    int nsamples;		// number of samples in each data set
    ArrayList dcLabels;		// labels for multisource DC sweeps

    HashMap nodes;		// names => Node
    HashMap options;		// option name => Double
    String tempdir;		// where to open temporary files
    String problem;		// description of what went wrong
    boolean invalidDevice;	// if we were passed an invalid device
    ArrayList mergedNodes;	// pairs of nodes to be merged

    double Vih;			// high logic threshold
    double Vil;			// low logic threshold
    double time;		// current simulation time

    RandomAccessFile history;	// where we keep the history
    int hIndex;			// index of next record to be written
    byte[] hBuffer;		// memory buffer for history
    int hBlock;			// index of first record in memory buffer
    int maxBlock;		// last block we've written
    int hMask;			// for converting indicies to block numbers
    boolean hDirty;		// true if memory buffer has been written to

    public Network(HashMap options,String tempdir) {
	this.options = options;
	this.tempdir = tempdir;
	nodes = new HashMap();
	dcLabels = new ArrayList();
	problem = "";
	invalidDevice = false;
	mergedNodes = new ArrayList();

	Vih = GetOption("vih",2.7);
	Vil = GetOption("vil",0.6);

	// set up history file
	try {
	    File tdir = null;
	    if (this.tempdir != null) tdir = new File(this.tempdir);
	    File tfile = File.createTempFile("jsim",null,tdir);
	    tfile.deleteOnExit();
	    history = new RandomAccessFile(tfile,"rw");
	}
	catch (IOException e) {
	    System.out.println("Can't open history file: "+e);
	    history = null;
	}

	// set up memory buffer
	hBuffer = new byte[(1 << BSIZE) * RSIZE];
	hBlock = -1;
	maxBlock = -1;
	hDirty = false;
	hIndex = 0;
	hMask = (1 << BSIZE) - 1;
    }

    public double NetworkTime() {
	return time;
    }

    public double NetworkSize() {
	return 0;
    }

    public String Size() {
	return null;
    }

    public double MinObservedSetup() {
	return Double.POSITIVE_INFINITY;
    }

    public double GetOption(String name,double defaultv) {
	Double v = (Double)options.get(name);
	return ((v == null) ? defaultv : v.doubleValue());
    }

    public int VtoL(double v) {
	// permit a little slack to account for small interpolation errors
	if (v > Vih-.001) return Node.V1;
	else if (v < Vil+.001) return Node.V0;
	else return Node.VX;
    }

    public void CleanUp() {
	try {
	    history.close();
	    history = null;
	    nodes.clear();
	}
	catch (Exception e) {
	    System.out.println("Exception during clean up: "+e);
	}
    }

    public void ResetHistory() {
	hIndex = 0;
	Iterator iter = nodes.values().iterator();
	while (iter.hasNext()) {
	    Node n = (Node)iter.next();
	    n.ResetHistory();
	}
    }

    // make sure history buffer has the record we want; return
    // appropriate offset to first byte of record
    public int EnsureHistory(int index) {
	if (history == null) return -1;
	int block = index & ~hMask;

	// see if we already have record we want in memory buffer
	if (block != hBlock) {
	    // have to reload memory buffer
	    try {
		if (hDirty) {
		    // write out previous contents
		    history.seek((long)hBlock * RSIZE);
		    history.write(hBuffer);
		    //System.out.println("writing block "+hBlock);
		    hDirty = false;
		    if (hBlock > maxBlock) maxBlock = hBlock;
		}
		// read in appropriate block of records
	    }
	    catch (IOException e) {
		System.out.println("IO exception writing block "+hBlock+" (index="+index+") of history buffer: "+e);
	    }
	    try {
		hBlock = block;
		if (hBlock <= maxBlock) {
		    history.seek((long)hBlock * RSIZE);
		    history.readFully(hBuffer);
		}
	    }
	    catch (IOException e) {
		System.out.println("IO exception reading block "+hIndex+" (index="+index+") of history buffer: "+e);
	    }
	}
	return (index & hMask) * RSIZE;
    }

    // write an int value into the history buffer
    public void WriteInt(int offset,int value) {
	hBuffer[offset++] = (byte)(value >> 24);
	hBuffer[offset++] = (byte)(value >> 16);
	hBuffer[offset++] = (byte)(value >> 8);
	hBuffer[offset] = (byte)(value);
    }

    // read an int value out of the history buffer
    public int ReadInt(int offset) {
	int result = hBuffer[offset++] & 0xFF;
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	result <<= 8;
	result |= (hBuffer[offset] & 0xFF);
	return result;
    }

    // write an long value into the history buffer
    public void WriteLong(int offset,long value) {
	hBuffer[offset++] = (byte)(value >> 56);
	hBuffer[offset++] = (byte)(value >> 48);
	hBuffer[offset++] = (byte)(value >> 40);
	hBuffer[offset++] = (byte)(value >> 32);
	hBuffer[offset++] = (byte)(value >> 24);
	hBuffer[offset++] = (byte)(value >> 16);
	hBuffer[offset++] = (byte)(value >> 8);
	hBuffer[offset] = (byte)(value);
    }

    // read a long value out of the history buffer
    public long ReadLong(int offset) {
	long result = hBuffer[offset++] & 0xFF;
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	result <<= 8;
	result |= (hBuffer[offset++] & 0xFF);
	return result;
    }

    // add a record to the history, return it's index
    synchronized public int WriteRecord(int previousIndex,double time,float value) {
	//System.out.println("index="+hIndex+" prev="+previousIndex+" time="+time+" value="+value);

	int offset = EnsureHistory(hIndex);
	if (offset >= 0) {
	    WriteInt(offset,previousIndex);
	    WriteLong(offset+4,Double.doubleToLongBits(time));
	    WriteInt(offset+12,Float.floatToIntBits(value));
	    hDirty = true;
	    int index = hIndex;
	    hIndex += 1;
	    return index;
	} else return -1;
    }

    abstract public double GetTime();
    abstract public String SimulationType();

    public boolean isAnalogSimulation() { return true; }

    synchronized public ArrayList RetrieveAnalogPlotData(String name) {
	if (!isAnalogSimulation())
	    return RetrieveDigitalPlotData(name);

	ArrayList result = new ArrayList();
	if (history == null) return result;
	ArrayList names = UI.ExpandNodeName(name);
	int nbits = names.size();

	for (int bit = 0; bit < nbits; bit += 1) {
	    String node = (String)names.get(bit);
	    Node n = (Node)FindNode(node,false);
	    if (n == null) {
		problem = node;
		return null;
	    }
	    String units = name.startsWith("i(") ? "A" : "V";
	    PlotData d = null;

	    // retrieve info from the history
	    int index = n.hIndex;
	    int remaining = 0;
	    while (index != -1) {
		if (remaining == 0) {
		    d = new PlotData(name,mode == TRANSIENT_ANALYSIS ? "s" : "V",units,1);
		    result.add(d);
		    int i = result.size() - 1;
		    if (i < dcLabels.size()) 
			d.name += (String)dcLabels.get(i);
		    remaining = nsamples;
		}

		int offset = EnsureHistory(index);
		double time = Double.longBitsToDouble(ReadLong(offset+4));
		float v = Float.intBitsToFloat(ReadInt(offset+12));
		d.AddPoint(new AnalogPlotCoordinate(time,(double)v));
		index = ReadInt(offset);
		remaining -= 1;
	    }
	}

	return result;
    }

    synchronized public ArrayList RetrieveDigitalPlotData(String name) {
	if (mode != TRANSIENT_ANALYSIS) return null;

	ArrayList result = new ArrayList();
	if (history == null) return result;
	boolean analog = isAnalogSimulation();

	ArrayList names = UI.ExpandNodeName(name);
	int nbits = names.size();
	PlotData d = new PlotData(name,"s","",nbits);
	result.add(d);

	HistoryRequest requests = null;
	for (int bit = 0; bit < nbits; bit += 1) {
	    String node = (String)names.get(bit);
	    Node n = (Node)FindNode(node,false);
	    if (n == null) {
		problem = node;
		return null;
	    }
	    if (n.hIndex != -1)
		requests = HistoryRequest.Insert(requests,new HistoryRequest(bit,n.hIndex));
	}

	// make one pass through the history filling in info for
	// the bits as we go
	while (requests != null) {
	    HistoryRequest r = requests;
	    requests = r.next;

	    int offset = EnsureHistory(r.index);
	    double time = Double.longBitsToDouble(ReadLong(offset+4));
	    float v = Float.intBitsToFloat(ReadInt(offset+12));
	    int nextIndex = ReadInt(offset);

	    int v1,v2;
	    double actualTime = time;
	    boolean changed = false;

	    if (analog) {
		double threshold = 0;
		if (v >= Vih) {		// logic high
		    v1 = 1; v2 = 0;
		    threshold = Vih;
		} else if (v <= Vil) {	// logic low
		    v1 = 0; v2 = 0;
		    threshold = Vil;
		} else {			// invalid value
		    v1 = 0; v2 = 1;
		    threshold = (r.lv1 == 0) ? Vil : Vih;
		}

		if (v1 != r.lv1 || v2 != r.lv2) {
		    changed = true;
		    r.lv1 = v1; r.lv2 = v2;
		    // interpolate when we actually crossed the threshold
		    if (!r.firstTime)
			actualTime += (r.tlast - actualTime)*(threshold - v)/(r.vlast - v);
		}
		r.tlast = time;
		r.vlast = v;
	    } else {
		changed = true;
		if (Float.isNaN(v)) { v1 = 0; v2 = 1; }	// X
		else if (Float.isInfinite(v)) { v1 = 1; v2 = 1; }	// Z
		else if (v == 0) { v1 = 0; v2 = 0; }	// 0	
		else { v1 = 1; v2 = 0; }			// 1
	    }

	    if (r.firstTime || nextIndex == -1 || changed) {
		DigitalPlotCoordinate c = new DigitalPlotCoordinate(actualTime);
		d.AddPoint(c);
		c.AddBit(nbits - r.bit - 1,v1,v2);
	    }

	    if (nextIndex != -1) {
		r.firstTime = false;
		r.index = nextIndex;
		requests = HistoryRequest.Insert(requests,r);
	    }
	}

	// propogate defined bits through the data points
	DigitalPlotCoordinate last = null;
	long mask = (nbits < 64) ? ((long)1 << nbits)-1 : -1;
	ArrayList coords = d.coords;
	int ncoords = coords.size();
	for (int i = ncoords - 1; i >=0 ; i -= 1) {
	    DigitalPlotCoordinate c = (DigitalPlotCoordinate)coords.get(i);
	    if (c.Merge(last,mask)) coords.remove(i+1);
	    last = c;
	}

	return result;
    }

    // Simulator interface
    abstract public Object FindDevice(String name);
    abstract public boolean Finalize();
    public String Problem() {
	return problem;
    }
    abstract public boolean TransientAnalysis(double stopTime,double maxTimestep,ProgressTracker jpanel);
    abstract public boolean DCAnalysis(String sweep1,double start1,double stop1,double step1,
			   String sweep2,double start2,double stop2,double step2,
			   ProgressTracker jpanel);

    // NetlistConsumer Interface
    public Object MakeModel(String name,int mtype,HashMap options) {
	return null;
    }
    abstract public Object FindNode(String name,boolean create);
    abstract public Object MakeGndNode(String name);
    public void NodeAlias(Object node,String alias) {
	if (node != null) nodes.put(alias,node);
    }
    public void ConnectNodes(Object n1,Object n2) {
	mergedNodes.add(n1);
	mergedNodes.add(n2);
    }
    public boolean MakeGate(String id,String function,ArrayList nodes,Parameter params) {
	problem = "Can't simulate built-in gate (type="+function+") "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeResistor(String id,Object n1,Object n2,double resistance) {
	problem = "Can't simulate resistor "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeCapacitor(String id,Object n1,Object n2,double capacitance) {
	problem = "Can't simulate capacitor "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeInductor(String id,Object n1,Object n2,double Inductance) {
	problem = "Can't simulate inductor "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeMosfet(String id,Object d,Object g,Object s,Object b,
			   Object model,double l,double w,double sl,double sw,
			   double ad,double pd,double nrd,double rdc,
			   double as,double ps,double nrs,double rsc) {
	problem = "Can't simulate mosfet "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeIndependentVoltageSource(String id,Object npos,Object nneg,
					     double dc,double acmag,double acphase,
					     int trantype,double params[]) {
	problem = "Can't simulate independent voltage source "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeIndependentCurrentSource(String id,Object npos,Object nneg,
					     double dc,double acmag,double acphase,
					     int trantype,double params[]) {
	problem = "Can't simulate independent current source "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeVCVS(String id,Object npos,Object nneg,
			 Object ncpos,Object ncneg,double gain) {
	problem = "Can't simulate VCVS "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeVCCS(String id,Object npos,Object nneg,
			 Object ncpos,Object ncneg,double gain) {
	problem = "Can't simulate VCCS "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeCCVS(String id,Object npos,Object nneg,
			 Object ncpos,Object ncneg,double gain) {
	problem = "Can't simulate CCVS "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
    public boolean MakeCCCS(String id,Object npos,Object nneg,
			 Object ncpos,Object ncneg,double gain) {
	problem = "Can't simulate CCCS "+id+" using "+SimulationType();
	invalidDevice = true;
	return false;
    }
}
