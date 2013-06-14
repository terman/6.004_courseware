// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package netlist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class Subcircuit extends SubcircuitObject {
    public static final int SUBCKT = 0;
    public static final int DSUBCKT = 1;
    public static final int GSUBCKT = 2;
    public static final String SNAMES[] = {".subckt", ".dsubckt", ".gsubckt"};

    public HashMap nodes;		// local nodes
    private HashMap subcircuits;	// local subcircuits
    private HashMap dsubcircuits;	// local device subcircuits
    private HashMap gsubcircuits;	// local gate subcircuits
    public HashMap models;		// local models
    public HashMap devices;		// local devices
    public ArrayList externals;		// vector of external nodes
    public ArrayList connectedNodes;	// node pairs to be connected
    public boolean active;		// are we current being netlisted?
    public int stype;			// type of subcircuit

    public Subcircuit(Subcircuit p,Identifier name,int stype) {
	// fill in our name, add ourselves to our parent
	parent = p;
	id = name;
	this.stype = stype;
	if (p != null) p.AddSubcircuit(id.name,this,stype);

	// initialize hashtable for local definitions
	nodes = new HashMap();
	subcircuits = new HashMap();
	dsubcircuits = new HashMap();
	gsubcircuits = new HashMap();
	models = new HashMap();
	devices = new HashMap();
	externals = new ArrayList();
	connectedNodes = new ArrayList();
	active = false;
    }

    public void AddSubcircuit(String name,Subcircuit s,int stype) {
	if (stype == DSUBCKT) dsubcircuits.put(name,s);
	else if (stype == GSUBCKT) gsubcircuits.put(name,s);
	else subcircuits.put(name,s);
    }

    public Subcircuit GetSubcircuit(String name,int stype) {
	if (stype == DSUBCKT) return (Subcircuit)dsubcircuits.get(name);
	else if (stype == GSUBCKT) return (Subcircuit)gsubcircuits.get(name);
	else return (Subcircuit)subcircuits.get(name);
    }

    // add a device to this subcircuit
    public void AddDevice(Netlist network,DevicePrototype d) {
	if (devices.get(d.id.name) != null) {
	    network.Error(d.id,"Duplicate device name");
	    return;
	}
	devices.put(d.id.name,d);
    }

    public void ConnectNodes(Netlist network,Identifier n1,Identifier n2) {
	connectedNodes.add(FindNode(network,n1));
	connectedNodes.add(FindNode(network,n2));
    }

    // locate node given its name
    public Node FindNode(Netlist network,Identifier name) {
	Node n;

	// first see if it's a global node
	if ((n = (Node)network.globalNodes.get(name.name)) != null)
	    return(n);

	// if not, it must be a local node
	if ((n = (Node)nodes.get(name.name)) != null) return(n);

	return new Node(this,name);
    }

    // locate subcircuit definition given its name
    public Subcircuit FindSubcircuit(Identifier name,int stype) {
	Subcircuit search = this;

	// search up .subckt hierarchy until we find a winner
	while (search != null) {
	    HashMap tbl = (stype == DSUBCKT) ? search.dsubcircuits : search.gsubcircuits;
	    Subcircuit s = (Subcircuit)tbl.get(name.name);
	    if (s == null) s = (Subcircuit)search.subcircuits.get(name.name);
	    if (s != null) return s;
	    search = search.parent;
	}
	return null;
    }

    // locate model definition given its name
    public Model FindModel(Identifier name) {
	Subcircuit search = this;

	// search up .subckt hierarchy until we find a winner
	while (search != null) {
	    Model m = (Model)search.models.get(name.name);
	    if (m != null) return m;
	    search = search.parent;
	}
	return null;
    }

    // netlist each device in the subcircuit
    public boolean Netlist(Netlist network,NetlistConsumer nc) {
	String prefix = network.prefix;
	active = true;

	// get a netlister node for each non-external node in the subcircuit
	Iterator iter = nodes.values().iterator();
	while (iter.hasNext()) {
	    Node n = (Node)iter.next();
	    if (!n.external)
		n.netlisterNode = nc.FindNode(prefix + n.id.name,true);
	}

	// get a netlister model for each model in the subcircuit
	iter = models.values().iterator();
	while (iter.hasNext()) {
	    Model m = (Model)iter.next();
	    if (!m.Netlist(network,nc)) { active = false; return false; }
	}

	// connect node pairs as user requested
	int nconnects = connectedNodes.size();
	for (int i = 0; i < nconnects; i += 2) {
	    Node n1 = (Node)connectedNodes.get(i);
	    Node n2 = (Node)connectedNodes.get(i+1);
	    nc.ConnectNodes(n1.netlisterNode,n2.netlisterNode);
	}

	// netlist each device
	iter = devices.values().iterator();
	while (iter.hasNext()) {
	    DevicePrototype d = (DevicePrototype)iter.next();
	    if (!d.Netlist(network,nc)) { active = false; return false; }
	}
	active = false;
	return true;
    }
}
