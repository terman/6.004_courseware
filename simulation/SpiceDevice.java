// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceDevice {
    SpiceDevice iterationLink;	// link in eachIteration device list
    SpiceDevice timestepLink;	// link in endOfTimestep device list

    public SpiceDevice() {
	iterationLink = null;
	timestepLink = null;
    }

    // add any necessary break points
    public void ComputeBreakpoints(SpiceNetwork network,double stopTime) { }

    // return true if device has converged, false otherwise
    // devices that need to load something new into the admittance
    // matrix before each solution should override this method and
    // add themselves to the eachIteration list of the network.
    // Since that list is used to determine which devices' EachIteration
    // method needs to be called this default method should never be
    // called.  Devices whose contribution to the admittance matrix
    // doesn't change iteration-to-iteration just add their contributions
    // to the gExp slot of the apropriate cell.
    public boolean EachIteration(int mode,double time,double timestep) {
	return true;
    }

    // some devices need to reset their state if we redo the timestep
    public void RestoreState(double time) { }

    // devices that need to set up for the next timestep (eg, capacitors
    // and inductors) add themselves to the endOfTimestep list of the
    // network.  Since that list is used to determine which devices'
    // EndOfTimestep method needs to be called this default method should
    // never be called.
    public void EndOfTimestep(boolean breakpoint,double timestep) {
	System.out.println("No one should get here!");
    }
}
