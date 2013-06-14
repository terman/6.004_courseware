// Copyright (C) 2007 Christopher J. Terman - All Rights Reserved.

package simulation;

public class TPDComparator implements java.util.Comparator {
    // sort with max tPDsum first
    public int compare(Object o1,Object o2) {
	TimingInfo t1 = (TimingInfo)o1;
	TimingInfo t2 = (TimingInfo)o2;
	return -Double.compare(t1.tPDsum,t2.tPDsum);
    }
}
