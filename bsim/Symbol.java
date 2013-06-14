// Copyright (C) 2000 Christopher J. Terman - All Rights Reserved.

package bsim;

import java.util.Hashtable;
import java.util.Vector;

public class Symbol {
    public static final int UNDEF = 0;	// symbol has no value
    public static final int ASSIGN = 1;	// value established with "="
    public static final int LABEL = 2;	// value established with ":"

    public String name;		// symbol's name
    public int value;		// numeric value
    public int type;		// tells how symbol got its value
    public Vector mdefs;	// macro definitions with this name

    public Symbol(Hashtable symbols,String name,int value,int type) {
	this.name = name;
	this.value = value;
	this.type = type;
	mdefs = new Vector();
	if (symbols != null) symbols.put(name,this);
    }

    public Symbol(Hashtable symbols,String name) {
	this(symbols,name,0,UNDEF);
    }

    public Symbol(Hashtable symbols,String name,int value) {
	this(symbols,name,value,ASSIGN);
    }

    public String toString() {
	return "<Symbol "+name+"="+value+">";
    }

    public Macro LookupMacro(int nparams) {
	for (int i = 0; i < mdefs.size(); i += 1) {
	    Macro m = (Macro)mdefs.elementAt(i);
	    if (m.NParams() == nparams) return m;
	}
	return null;
    }

    public void ClearMacroDefinitions() {
	mdefs.setSize(0);
    }

    public void AddMacroDefinition(Macro m) {
	mdefs.addElement(m);
    }

    public static Symbol Lookup(Hashtable symbols,String name,boolean create) {
	Symbol s = (Symbol)symbols.get(name);
	if (s == null && create) s = new Symbol(symbols,name,0,UNDEF);
	return s;
    }

    public static Symbol Lookup(Hashtable symbols,String name) {
	return Lookup(symbols,name,true);
    }
}

