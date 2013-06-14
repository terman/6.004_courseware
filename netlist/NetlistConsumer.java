// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package netlist;

import java.util.ArrayList;
import java.util.HashMap;

public interface NetlistConsumer {
    String Problem();

    // models
    static final int NMOS = 1;	// model types
    static final int PMOS = 2;
    Object MakeModel(String name,int mtype,HashMap options);

    // nodes
    Object FindNode(String name,boolean create);
    Object MakeGndNode(String name);
    void NodeAlias(Object node,String alias);
    void ConnectNodes(Object n1,Object n2);

    // built-in gates
    boolean MakeGate(String id,String function,ArrayList nodes,Parameter params);

    // devices
    boolean MakeResistor(String id,Object n1,Object n2,double resistance);
    boolean MakeCapacitor(String id,Object n1,Object n2,double capacitance);
    boolean MakeInductor(String id,Object n1,Object n2,double Inductance);
    boolean MakeMosfet(String id,Object d,Object g,Object s,Object b,
		       Object model,double l,double w,double sl,double sw,
		       double ad,double pd,double nrd,double rdc,
		       double as,double ps,double nrs,double rsc);

    // sources
    static final int PWL = 1;	// transient source function types
    static final int PULSE = 2;
    static final int SIN = 3;
    static final int EXP = 4;
    static final int SFFM = 5;
    static final int AM = 6;

    boolean MakeIndependentVoltageSource(String id,Object npos,Object nneg,
					 double dc,double acmag,double acphase,
					 int trantype,double params[]);
    boolean MakeIndependentCurrentSource(String id,Object npos,Object nneg,
					 double dc,double acmag,double acphase,
					 int trantype,double params[]);
    boolean MakeVCVS(String id,Object npos,Object nneg,
		     Object ncpos,Object ncneg,double gain);
    boolean MakeVCCS(String id,Object npos,Object nneg,
		     Object ncpos,Object ncneg,double gain);
    boolean MakeCCVS(String id,Object npos,Object nneg,
		     Object ncpos,Object ncneg,double gain);
    boolean MakeCCCS(String id,Object npos,Object nneg,
		     Object ncpos,Object ncneg,double gain);
}
