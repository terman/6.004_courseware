// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package netlist;

import java.util.HashMap;

class Model extends SubcircuitObject {
    public Object netlisterModel;		// used during netlisting
    public int type;			// what kind of model we are
    public HashMap options;		// values for model options

    public Model(Subcircuit p,Identifier name,int t) {
	// fill in our name, add ourselves to our parent
	parent = p;
	id = name;
	if (p != null) p.models.put(id.name,this);
	type = t;
	options = new HashMap();
    }

    public boolean Netlist(Netlist network,NetlistConsumer nc) {
	netlisterModel = nc.MakeModel(network.prefix+id.name,type,options);
	return true;
    }

    // convert string into appropriate model type
    static int ModelType(String name) {
	if (name.equalsIgnoreCase("nmos")) return NetlistConsumer.NMOS;
	else if (name.equalsIgnoreCase("pmos")) return NetlistConsumer.PMOS;
	else return 0;
    }
}
