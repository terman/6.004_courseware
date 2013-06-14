// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package plot;

abstract class PlotCoordinate {
    public String toString() {
	return "coord["+GetX()+","+GetY()+"]";
    }

    public double GetX() { return Double.NaN; }
    public double GetY() { return Double.NaN; }
}
