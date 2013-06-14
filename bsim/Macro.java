// Copyright (C) 2000 Christopher J. Terman - All Rights Reserved.

package bsim;

import java.util.Hashtable;
import java.util.Vector;

public class Macro {
    Symbol name;	// symbol which is name of this macro
    String body;	// body is just a character string
    Vector params;	// Symbol for each formal parameter
    boolean called;	// used to catch recursive macro calls

    public Macro(Symbol name,String body,Vector params) {
	this.name = name;
	this.body = body;
	this.params = params;
	called = false;

	name.AddMacroDefinition(this);
    }

    public String toString() {
	String result = "<Macro "+name.name+"(";
	for (int i = 0; i < params.size(); i += 1) {
	    Symbol s = (Symbol)params.elementAt(i);
	    if (i != 0) result += ",";
	    result += s.name;
	}
	result += "): "+body+">";
	return result;
    }

    // convenience functions for defining built-in macros

    // 0-argument macro
    public Macro(Hashtable symbols,String name,String body) {
	this(Symbol.Lookup(symbols,name),body,new Vector());
    }

    // 1-argument macro
    public Macro(Hashtable symbols,String name,String p1,String body) {
	this(Symbol.Lookup(symbols,name),body,new Vector());
	params.addElement(Symbol.Lookup(symbols,p1));
    }

    // 2-argument macro
    public Macro(Hashtable symbols,String name,String p1,String p2,String body) {
	this(Symbol.Lookup(symbols,name),body,new Vector());
	params.addElement(Symbol.Lookup(symbols,p1));
	params.addElement(Symbol.Lookup(symbols,p2));
    }

    // 3-argument macro
    public Macro(Hashtable symbols,String name,String p1,String p2,String p3,String body) {
	this(Symbol.Lookup(symbols,name),body,new Vector());
	params.addElement(Symbol.Lookup(symbols,p1));
	params.addElement(Symbol.Lookup(symbols,p2));
	params.addElement(Symbol.Lookup(symbols,p3));
    }

    // 4-argument macro
    public Macro(Hashtable symbols,String name,String p1,String p2,String p3,String p4,String body) {
	this(Symbol.Lookup(symbols,name),body,new Vector());
	params.addElement(Symbol.Lookup(symbols,p1));
	params.addElement(Symbol.Lookup(symbols,p2));
	params.addElement(Symbol.Lookup(symbols,p3));
	params.addElement(Symbol.Lookup(symbols,p4));
    }

    public int NParams() {
	return params.size();
    }
}

