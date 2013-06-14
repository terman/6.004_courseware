// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package netlist;

import gui.UI;
import java.util.ArrayList;

class Identifier extends Token {
    public String name;			// identifier

    static private StringBuffer scratch = new StringBuffer();

    public Identifier(String n,Netlist netlist,int s,int e) {
	super(netlist,s,e);
	name = n.toLowerCase();
    }

    public String toString() {
	return "<id "+name+" "+super.toString()+">";
    }

    // some convenience functions
    public boolean equals(String s) { return name.equals(s); }
    public boolean equals(Identifier i) { return name.equals(i.name); }

    // expand any iterators and return vector of identifiers
    public ArrayList Expand() {
	ArrayList ids = UI.ExpandNodeName(name);

	// convert each string into an identifier.  Look for special
	// case where no expansion happened so we can just use ourself
	int nids = ids.size();
	if (nids == 1 && name.equals((String)ids.get(0)))
	    ids.set(0,this);
	else for (int i = 0; i < nids; i += 1)
	    ids.set(i,new Identifier((String)ids.get(i),netlist,start,end));
	return ids;
    }

    // true if ch is a legal character in an identifier
    static public boolean IdChar(int ch) {
	if (ch >= '0' && ch <= '9') return true;
	else if (ch >= 'a' && ch <= 'z') return true;
	else if (ch >= 'A' && ch <= 'Z') return true;
	else switch (ch) {
	case '[':
	case ']':
	case '$':
	case '_':
	case '.':
	case ':':
	case '#':
	    return true;
	default:
	    return false;
	}
    }

    // see if we can interpret line[lineOffset...] as an identifier
    synchronized static public Identifier Parse(StringBuffer line,Netlist netlist,int lineOffset) {
	int lineLength = line.length();
	int ch,start;

	scratch.setLength(0);
  
	// skip over blanks
	while (lineOffset < lineLength && line.charAt(lineOffset) <= ' ')
	    lineOffset += 1;
	start = lineOffset;

	while (lineOffset < lineLength && IdChar(ch = line.charAt(lineOffset))) {
	    scratch.append((char)ch);
	    lineOffset += 1;
	}

	// see if we've found anything
	if (lineOffset == start) return null;
	return new Identifier(scratch.toString(),netlist,start,lineOffset);
    }
}

