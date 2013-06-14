// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

// sparse matrix entry
class SpiceCell {
    int row,column;
    SpiceCell nextRow;
    SpiceCell nextColumn;
    double gExp;		// used to initialize gExp each iteration
    double luExp;		// value for this iteration
    boolean nonzero;		// true if this cell is known to be nonzero

    public SpiceCell(int c) {
	column = c;
	nextRow = null;
	nextColumn = null;
	gExp = 0.0;
	luExp = 0.0;
	nonzero = false;
    }
}
