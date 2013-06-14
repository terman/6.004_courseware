// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

// plot requests are accumulated in a vector which in turn is added to
// the plots vector of the appropriate Analysis
public class PlotRequest {
    public Identifier property;		// V, I, ...
    public Identifier element;		// node or element name

    public PlotRequest(Identifier prop,Identifier elem) {
	property = prop;
	element = elem;
    }

    public String Property() {
	return property.name.toLowerCase();
    }

    public String Element() {
	return element.name;
    }
}
