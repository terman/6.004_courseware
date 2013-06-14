// Copyright (C) 1999-2001 Christopher J. Terman - All Rights Reserved.

package plot;

import gui.UI;

public class DigitalPlotCoordinate extends PlotCoordinate {
    double x;		// another point in the curve
    long v1;		// 4-value logic, 64 bits
    long v2;
    long vmask;		// which bits defined at this time

    public double GetX() { return x; }

    public DigitalPlotCoordinate(double nx) {
	x = nx;
	v1 = 0;
	v2 = 0;
	vmask = 0;
    }

    public void AddBit(int bit,int v1,int v2) {
	if (bit < 0 || bit > 63) return;
	long mask = ((long)1) << bit;
	this.v1 &= ~mask;
	if (v1 != 0) this.v1 |= mask;
	this.v2 &= ~mask;
	if (v2 != 0) this.v2 |= mask;
	vmask |= mask;
    }

    public boolean Merge(DigitalPlotCoordinate previous,long mask) {
	v1 |= ((previous == null) ? mask : previous.v1) & ~vmask;
	v2 |= ((previous == null) ? mask : previous.v2) & ~vmask;
	vmask = mask;
	return previous != null && previous.x == x;
    }

    public boolean Match(long v) {
	return ((v2 & vmask) == 0 && ((v1 ^ v) & vmask) == 0);
    }

    public String toString() {
	return "["+UI.EngineeringNotation(x,3)+"s: 0b"+toBinaryString()+"]";
    }

    public String toBinaryString() {
	String result = "";
	long tmask = vmask;
	long tv1 = v1;
	long tv2 = v2;
	for (int i = 0; i < 64; i += 1) {
	    if (tmask == 0) break;
	    if ((tv2 & 1) == 1) result = "X" + result;
	    else if ((tv1 & 1) == 1) result = "1" + result;
	    else result = "0" + result;
	    tmask >>= 1;
	    tv1 >>= 1;
	    tv2 >>= 1;
	}
	return result;
    }

    public String toOctalString() {
	String result = "";
	long tmask = vmask;
	long tv1 = v1;
	long tv2 = v2;
	for (int i = 0; i < 64; i += 3) {
	    if (tmask == 0) break;
	    if ((tv2 & 0x7) != 0) result = "X" + result;
	    else {
		int v = (int)(tv1 & 0x7);
		result = (char)('0' + v) + result;
	    }
	    tmask >>= 3;
	    tv1 >>= 3;
	    tv2 >>= 3;
	}
	return result;
    }

    public String toHexString() {
	String result = "";
	long tmask = vmask;
	long tv1 = v1;
	long tv2 = v2;
	for (int i = 0; i < 64; i += 4) {
	    if (tmask == 0) break;
	    if ((tv2 & 0xF) != 0) result = "X" + result;
	    else {
		int v = (int)(tv1 & 0xF);
		if (v <= 9) result = (char)('0' + v) + result;
		else result = (char)('A' + v - 10) + result;
	    }
	    tmask >>= 4;
	    tv1 >>= 4;
	    tv2 >>= 4;
	}
	return result;
    }
}
