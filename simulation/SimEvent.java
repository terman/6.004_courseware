// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

public class SimEvent extends Event {
    static public final int CONTAMINATION = 0;	// contamination event
    static public final int PROPAGATION = 1;	// propagation event

    int type;				// type of event
    SimNode node;			// node
    int v;				// new value

    public SimEvent(int type,SimNode node,int v) {
	super();
	Initialize(type,node,v);
    }

    public boolean Before(Event other) {
	// keep contamination events sorted before propagation events
	if (etime == other.etime)
	    return type == CONTAMINATION &&
		((SimEvent)other).type != CONTAMINATION;
	else return etime < other.etime;
    }

    public String toString() {
	if (node == null) return "SimEvent["+hashCode()+"]";
	else return ((type == CONTAMINATION) ? "Contam[" : "Prop[") +
		 node.name + "->" + SimNode.VALUES.charAt(v) +
		 "@" + etime + "]";
    }

    public void Initialize(int type,SimNode node,int v) {
	etime = NO_EVENT;
	this.type = type;
	this.node = node;
	this.v = v;
    }
}
