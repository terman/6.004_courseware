// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

class InductorPrototype extends DevicePrototype {
    Node n1;		// two terminal nodes
    Node n2;
    Number inductance;

    public InductorPrototype(Identifier i_id,Node i_n1,Node i_n2,Number i) {
	id = i_id;
	n1 = i_n1;
	n2 = i_n2;
	inductance = i;
    }

    // add device to netlist
    public boolean Netlist(Netlist network,NetlistConsumer n) {
	n.MakeInductor(network.prefix + id.name,
		       n1.netlisterNode,n2.netlisterNode,inductance.value);
	return true;
    }
}
