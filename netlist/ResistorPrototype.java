// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

class ResistorPrototype extends DevicePrototype {
    Node n1;		// two terminal nodes
    Node n2;
    Number resistance;

    public ResistorPrototype(Identifier r_id,Node r_n1,Node r_n2,Number r) {
	id = r_id;
	n1 = r_n1;
	n2 = r_n2;
	resistance = r;
    }

    // add device to netlist
    public boolean Netlist(Netlist network,NetlistConsumer n) {
	n.MakeResistor(network.prefix + id.name,
		       n1.netlisterNode,n2.netlisterNode,resistance.value);
	return true;
    }
}
