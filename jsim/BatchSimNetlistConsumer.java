// Copyright (C) 2013 Christopher J. Terman - All Rights Reserved.

package jsim;

import netlist.NetlistConsumer;
import netlist.Parameter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashMap;

public class BatchSimNetlistConsumer implements NetlistConsumer {
    // keep track of device counts
    public Hashtable<String,Integer> counts = new Hashtable<String,Integer>();

    public BatchSimNetlistConsumer() {
    }

    // return JSON summary of device counts
    public String Summary() {
	StringBuffer result = new StringBuffer();
        result.append("{");
	boolean first = true;

	for (Enumeration<String> e = counts.keys(); e.hasMoreElements();) {
	    String devtype = e.nextElement();
	    if (first) first = false;
	    else result.append(",");
	    result.append("\""+devtype+"\":"+counts.get(devtype));
	}

        result.append("}");
	return result.toString();
    }

    public String Problem() { return null; }

    // models
    public Object MakeModel(String name,int mtype,HashMap options) {
	if (mtype == NetlistConsumer.NMOS) return "n";
	else if (mtype == NetlistConsumer.PMOS) return "p";
	else return "?";
    }

    // nodes
    public Object FindNode(String name,boolean create) {
	return name;
    }

    public Object MakeGndNode(String name) {
	return "GND";   // canoncialize name of ground node
    }

    public void NodeAlias(Object node,String alias) {}
    public void ConnectNodes(Object n1,Object n2) {}

    // increment count for a particular device type
    public void Increment(String devtype) {
	Integer count = counts.get(devtype);
	if (count == null) count = new Integer(0);
	counts.put(devtype,new Integer(count.intValue() + 1));
    }

    // built-in gates
    public boolean MakeGate(String id,String function,ArrayList nodes,Parameter params) {
	Increment(function);
	return true;
    }

    // devices
    public boolean MakeResistor(String id,Object n1,Object n2,double resistance) {
	Increment("resistor");
	return true;
    }

    public boolean MakeCapacitor(String id,Object n1,Object n2,double capacitance) {
	Increment("capacitor");
	return true;
    }

    public boolean MakeInductor(String id,Object n1,Object n2,double Inductance) {
	Increment("inductor");
	return true;
    }

    public boolean MakeMosfet(String id,Object d,Object g,Object s,Object b,
			      Object model,double l,double w,double sl,double sw,
			      double ad,double pd,double nrd,double rdc,
			      double as,double ps,double nrs,double rsc) {
	Increment((String)model + "fet");
	return true;
    }

    public boolean MakeIndependentVoltageSource(String id,Object npos,Object nneg,
					 double dc,double acmag,double acphase,
					 int trantype,double params[]) {
	Increment("v");
	return true;
    }

    public boolean MakeIndependentCurrentSource(String id,Object npos,Object nneg,
					 double dc,double acmag,double acphase,
					 int trantype,double params[]) {
	Increment("i");
	return true;
    }

    public boolean MakeVCVS(String id,Object npos,Object nneg,
			    Object ncpos,Object ncneg,double gain) {
	Increment("vcvs");
	return true;
    }

    public boolean MakeVCCS(String id,Object npos,Object nneg,
			    Object ncpos,Object ncneg,double gain) {
	Increment("vccs");
	return true;
    }

    public boolean MakeCCVS(String id,Object npos,Object nneg,
			    Object ncpos,Object ncneg,double gain) {
	Increment("ccvs");
	return true;
    }

    public boolean MakeCCCS(String id,Object npos,Object nneg,
			    Object ncpos,Object ncneg,double gain) {
	Increment("cccs");
	return true;
    }
}
