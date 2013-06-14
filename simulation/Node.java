// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

abstract public class Node {
    static public final int V0 = 0;	// "0" logic low
    static public final int V1 = 1;	// "1" logic high
    static public final int VX = 2;	// "X" unknown
    static public final int VZ = 3;	// "Z" high-impedence
    static public final String VALUES = "01XZ";

    public String name;		// name of this node
    public boolean history;	// true if we're keeping a history
    public int hIndex;		// index for most recent history record

    public Node(String name) {
	this.name = name;
	history = true;
	hIndex = -1;
    }

    abstract public double GetValue(Network network);

    public void setEnabled(boolean which) {
	history = which;
    }

    public void ResetHistory() {
	hIndex = -1;
    }

    public void RecordValue(Network network,double time,double v) {
	if (history)
	    hIndex = network.WriteRecord(hIndex,time,(float)v);
    }

    public void RecordLogicValue(Network network,double time,int v) {
	if (history) {
	    float value;
	    if (v == V0) value = 0;
	    else if (v == V1) value = 1;
	    else if (v == VZ) value = Float.POSITIVE_INFINITY;
	    else value = Float.NaN;
	    hIndex = network.WriteRecord(hIndex,time,value);
	}
    }
}
