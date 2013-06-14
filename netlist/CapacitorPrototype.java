// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

class CapacitorPrototype extends DevicePrototype {
    Node n1;		// two terminal nodes
    Node n2;
    Number capacitance;

    public CapacitorPrototype(Identifier c_id,Node c_n1,Node c_n2,Number c) {
	id = c_id;
	n1 = c_n1;
	n2 = c_n2;
	capacitance = c;
    }

    // add device to netlist
    public boolean Netlist(Netlist network,NetlistConsumer n) {
	n.MakeCapacitor(network.prefix + id.name,
			n1.netlisterNode,n2.netlisterNode,capacitance.value);
	return true;
    }
}
