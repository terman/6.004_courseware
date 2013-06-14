// Copyright (C) 1998-2001 Christopher J. Terman - All Rights Reserved.

package plot;

import java.util.ArrayList;

public class PlotData {
    public String name;		// name of element
    public ArrayList coords;	// [Analog|Digital]PlotCoordinates
    String haxis,vaxis;		// what's being plotted on each axis
    double xmin,xmax;		// bounds on values
    double ymin,ymax;		// bounds on values
    public int width;		// for digital data: width in bits

    public PlotData(String name,String haxis,String vaxis,int width) {
	this.name = name;
	this.haxis = haxis;
	this.vaxis = vaxis;
	this.width = width;
	coords = new ArrayList();

	xmax = Double.NEGATIVE_INFINITY;
	xmin = Double.POSITIVE_INFINITY;
	ymax = Double.NEGATIVE_INFINITY;
	ymin = Double.POSITIVE_INFINITY;
    }

    public String toString() {
	return name+"[x="+xmin+","+xmax+" y="+ymin+","+ymax+"]";
    }

    // add a new coordinate to the list
    public void AddPoint(PlotCoordinate c) {
	// find where it goes: list is sorted by increasing X
	int ncoords = coords.size();
	boolean inserted = false;
	double x = c.GetX();
	for (int i = 0; i < ncoords; i += 1) {
	    PlotCoordinate cc = (PlotCoordinate)coords.get(i);
	    if (x <= cc.GetX()) {
		coords.add(i,c);
		inserted = true;
		break;
	    }
	}
	if (!inserted) coords.add(c);

	if (!Double.isNaN(x) && !Double.isInfinite(x)) {
	    if (x < xmin) xmin = x;
	    if (x > xmax) xmax = x;
	}
	double y = c.GetY();
	if (!Double.isNaN(y) && !Double.isInfinite(y)) {
	    if (y < ymin) ymin = y;
	    if (y > ymax) ymax = y;
	}
    }

    // find first plot coordinate with X greater than specified value
    public PlotCoordinate FindCoordinate(double x) {
	int ncoords = coords.size();
	for (int i = 0; i < ncoords; i += 1) {
	    PlotCoordinate cc = (PlotCoordinate)coords.get(i);
	    double xx = cc.GetX();
	    if (xx > x) return cc;
	}
	return null;
    }

    public String toBinaryString(long v) {
	return toBinaryString(v,0);
    }

    public String toBinaryString(long v1,long v2) {
	String result = "";
	for (int i = 0; i < width; i += 1) {
	    if ((v2 & 1) != 0) result = "X" + result;
	    else if ((v1 & 1) != 0) result = "1" + result;
	    else result = "0" + result;
	    v1 >>= 1;
	    v2 >>= 1;
	}
	return result;
    }
}
