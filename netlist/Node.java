// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

class Node extends SubcircuitObject {
    Object netlisterNode;	// used during netlisting
    boolean external;		// true if node appears on .subckt arg list

    public Node(Subcircuit p,Identifier name) {
	// fill in our name, add ourselves to our parent
	parent = p;
	id = name;
	if (p != null) p.nodes.put(id.name,this);
	netlisterNode = null;
	external = false;
    }
}
