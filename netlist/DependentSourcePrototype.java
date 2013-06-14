// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

class DependentSourcePrototype extends DevicePrototype {
    static final int VCVS = 1;	// voltage-controlled voltage source
    static final int VCCS = 2;	// voltage-controlled current source
    static final int CCVS = 3;	// current-controlled voltage source
    static final int CCCS = 4;	// current-controlled current source

    int type;		// type of source
    Node npos;		// two output terminal nodes
    Node nneg;
    Node ncpos;		// two control terminals
    Node ncneg;
    Number gain;		// multiplicative factor

    public DependentSourcePrototype(Identifier s_id,Node s_pos,Node s_neg,
				    Node s_cpos,Node s_cneg,Number s_gain) {
	id = s_id;
	npos = s_pos;
	nneg = s_neg;
	ncpos = s_cpos;
	ncneg = s_cneg;
	gain = s_gain;

	switch (Character.toLowerCase(id.name.charAt(0))) {
	case 'e':	type = VCVS; break;
	case 'f':	type = CCCS; break;
	case 'g':	type = VCCS; break;
	case 'h':	type = CCVS; break;
	}
    }

    // add device to netlist
    public boolean Netlist(Netlist network,NetlistConsumer n) {
	switch (type) {
	case VCVS:
	    n.MakeVCVS(network.prefix + id.name,
		       npos.netlisterNode,nneg.netlisterNode,
		       ncpos.netlisterNode,ncneg.netlisterNode,
		       gain==null ? 1 : gain.value);
	    break;
	case VCCS:
	    n.MakeVCCS(network.prefix + id.name,
		       npos.netlisterNode,nneg.netlisterNode,
		       ncpos.netlisterNode,ncneg.netlisterNode,
		       gain==null ? 1 : gain.value);
	    break;
	case CCVS:
	    n.MakeCCVS(network.prefix + id.name,
		       npos.netlisterNode,nneg.netlisterNode,
		       ncpos.netlisterNode,ncneg.netlisterNode,
		       gain==null ? 1 : gain.value);
	    break;
	case CCCS:
	    n.MakeCCCS(network.prefix + id.name,
		       npos.netlisterNode,nneg.netlisterNode,
		       ncpos.netlisterNode,ncneg.netlisterNode,
		       gain==null ? 1 : gain.value);
	    break;
	}
	return true;
    }
}
