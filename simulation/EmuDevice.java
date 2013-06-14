// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

abstract class EmuDevice {
    EmuRegion region;		// region we belong to
    EmuDevice regionLink; 	// next device in region's device list

    public EmuDevice() {
	region = null;
    }

    public void Print() { }
    public void Finalize() { }
    public void Reset() { }
    public double Incremental(EmuNode n,double timestep) { return 0; }
    public double Update(double time) { return EmuRegion.NO_EVENT; }
    public boolean Conducting() { return false; }
    public double PowerPaths() { return 0; }

    public void AddToRegion(EmuRegion r,EmuNode n) {
	if (region == null) {
	    region = r;
	    r.AddDevice(this,n);
	}
    }
}
