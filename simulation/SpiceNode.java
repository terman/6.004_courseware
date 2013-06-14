// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

public class SpiceNode extends Node {
    public int index;	// index into solution vector

    public SpiceNode(String name,int index) {
	super(name);
	this.index = index;
    }

    public double GetValue(Network network) {
	return ((SpiceNetwork)network).solution[index];
    }
}
