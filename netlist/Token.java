// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

class Token {
    public Netlist netlist;	// which netlist
    public int start;		// index of first char
    public int end;		// index of last char

    public Token(Netlist netlist,int start,int end) {
	this.netlist = netlist;
	this.start = start;
	this.end = end;
    }

    public String toString() {
	return "<token "+netlist+"["+start+":"+end+"]>";
    }
}
