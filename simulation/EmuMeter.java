// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class EmuMeter {
    EmuMeter netLink;		// next meter in the network
    EmuNode node;		// node we're metering
    double totalTime;
    double vAverage;
    double vRMS;
    double vPeak;
    double vPeakTime;
    double iAverage;
    double iRMS;
    double iPeak;
    double iPeakTime;

    public EmuMeter() {
    }

    public void Reset() {
	totalTime = 0;
	vAverage = 0;
	vRMS = 0;
	vPeakTime = 0;
	iAverage = 0;
	iRMS = 0;
	iPeakTime = 0;
    }

    public void ResetState() {
	Reset();
	node.Reset();
    }

    static public void UpdateMeters(EmuMeter m,double time,double timestep) {
	while (m != null) {
	    m.Update(time,timestep);
	    m = m.netLink;
	}
    }

    public void Update(double time,double timestep) {
	double v;

	totalTime += timestep;

	// update average and RMS voltage accumulators
	v = node.voltage * timestep;
	vAverage += v;
	vRMS += node.voltage * v;

	// update peak voltage accumulator
	v = Math.abs(node.voltage);
	if (vPeakTime < 0 || vPeak < v) {
	    vPeakTime = time;
	    vPeak = v;
	}

	// update average and RMS current accumulators
	v = node.totalCurrent * timestep;
	iAverage += v;
	iRMS += node.totalCurrent * v;

	// update peak current accumulator
	v = Math.abs(node.totalCurrent);
	if (iPeakTime < 0 || iPeak < v) {
	    iPeakTime = time;
	    iPeak = v;
	}
    }
}
