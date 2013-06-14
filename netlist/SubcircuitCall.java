// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package netlist;

import java.util.ArrayList;

class SubcircuitCall extends DevicePrototype {
    public Identifier subckt;	// name of circuit we're an instance of
    public ArrayList args;	// nodes to passed into subcircuit
    public Parameter params;	// list of supplied parameters

    public SubcircuitCall(Identifier sc_id,Identifier sc_subckt,ArrayList sc_args,Parameter sc_params) {
	id = sc_id;
	subckt = sc_subckt;
	args = sc_args;
	params = sc_params;
    }

    // expand subcircuit call by expanding appropriate subckt definition
    public boolean Netlist(Netlist network,NetlistConsumer nc) {
	// built-in gates get special treatment
	if (subckt.name.charAt(0) == '$') {
	    ArrayList nodes = (ArrayList)args.clone();
	    int nnodes = nodes.size();
	    for (int i = 0; i < nnodes; i += 1)
		nodes.set(i,((Node)nodes.get(i)).netlisterNode);
	    if (!nc.MakeGate(network.prefix + id.name,subckt.name,nodes,params)) {
		network.Error(subckt,nc.Problem());
		return false;
	    }
	    return true;
	}

	Subcircuit s = network.currentSubcircuit.FindSubcircuit(subckt,network.topLevel.stype);

	if (s == null) {
	    network.Error(subckt,"Can't find .subckt definition for "+subckt.name);
	    return false;
	}
	if (s.active) {
	    network.Error(subckt,"Recursive call of .subckt "+subckt.name);
	    return false;
	}

	int nexternals = s.externals.size();
	int nargs = args.size();
	int iterations = (nargs == 0 || nexternals == 0) ? 1 : nargs / nexternals;
	if (iterations * nexternals != nargs) {
	    network.Error(subckt,"Number of arguments ("+nargs+") doesn't match .subckt definition for "+subckt.name+" (need multiple of "+nexternals+")");
	    return false;
	}

	// fix up prefix to reflect new layer of hierarchy
	String oldPrefix = network.prefix;

	for (int iter = 0; iter < iterations; iter += 1) {
	    if (iterations == 1)
		network.prefix = oldPrefix + id.name + "." ;
	    else
		network.prefix = oldPrefix + id.name + "#" + iter + "." ;

	    // fill in netlister nodes for subcircuit's external nodes
	    for (int i = 0; i < nexternals; i += 1) {
		Node arg = (Node)args.get(i*iterations + iter);
		Node ext = (Node)s.externals.get(i);
		ext.netlisterNode = arg.netlisterNode;

		if (network.localNames) {
		    // allow user to refer to node by its "local" name too
		    nc.NodeAlias(arg.netlisterNode,network.prefix+ext.id.name);
		}
	    }

	    // netlist all devices in the subcircuit definition
	    if (!s.Netlist(network,nc)) {
		network.prefix = oldPrefix;
		return false;
	    }
	}

	// restore old prefix
	network.prefix = oldPrefix;
	return true;
    }
}
