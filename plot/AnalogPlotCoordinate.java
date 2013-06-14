// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package plot;

public class AnalogPlotCoordinate extends PlotCoordinate {
    double x,y;			// another point in the curve

    public double GetX() { return x; }
    public double GetY() { return y; }

    public AnalogPlotCoordinate(double nx,double ny) {
	x = nx;
	y = ny;
    }
}
