// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

public class Parameter {
    public static final int NUMBER = 0;
    public static final int STRING = 1;
    public static final int VECTOR = 2;

    public Parameter next;		// next parameter in list
    public int type;
    public String name;			// identifier
    public boolean defined;		// true if user specified value

    public double ndefaultValue;	// value if not specified by user
    public double nvalue;		// parameter's value

    public Object oDefaultValue;	// value if not specified by user
    public Object ovalue;		// parameter's value

    public Parameter(String n,double v,Parameter nxt) {
	next = nxt;
	name = n;
	defined = false;

	type = NUMBER;
	ndefaultValue = v;
	nvalue = v;
    }

    public Parameter(String n,String v,Parameter nxt) {
	next = nxt;
	name = n;
	defined = false;

	type = STRING;
	oDefaultValue = v;
	ovalue = v;
    }

    public Parameter(String n,double v[],Parameter nxt) {
	next = nxt;
	name = n;
	defined = false;

	type = VECTOR;
	oDefaultValue = v;
	ovalue = v;
    }

    public Parameter Find(String n) {
	Parameter search = this;

	while (search != null) {
	    if (search.name.equalsIgnoreCase(n)) return search;
	    search = search.next;
	}
	return null;
    }

    public void Initialize() {
	nvalue = ndefaultValue;
	ovalue = oDefaultValue;
	defined = false;
    }

    public double Value(String n,double vdefault) {
	Parameter p = Find(n);
	return (p == null || p.type != NUMBER) ? vdefault : p.nvalue;
    }

    public String SValue(String n,String vdefault) {
	Parameter p = Find(n);
	return (p == null || p.type != STRING) ? vdefault : (String)p.ovalue;
    }

    public double[] VValue(String n,double vdefault[]) {
	Parameter p = Find(n);
	return (p == null || p.type != VECTOR) ? vdefault : (double [])p.ovalue;
    }
}
