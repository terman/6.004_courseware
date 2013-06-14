// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public class SimMemory extends SimDevice {
    public static final int MAXADDR = 20;	// log of max memory size

    double tcd;		// contamination delay from any input to output
    double tpdr;	// rising propagation delay from any input to output
    double tr;		// load dependent L->H delay (s/f)
    double tpdf;	// falling propagation delay from any input to output
    double tf;		// load dependent H->L delay (s/f)
    double ts;		// setup time (s)
    double th;		// hold time (s)
    double cin;		// capacitance of inputs
    double cout;	// capacitance of output
    public int width;	// number of data bits per location
    int naddr;		// number of address bits per port
    int nlocations;	// number of memory locations
    String filename;	// file from which to initialize contents
    
    int nports;		// number of ports to this memory
    int iOffset[];	// starting node's index for port's data inputs
    int oOffset[];	// starting node's index for port's data outputs

    int bits[];		// contents, packed 16 2-bit values per word
    int ibits[];	// initial contents, packed 16 2-bit values per word

    double minSetup;	// minimum setup time we've seen
    double minSetupTime;	// time we saw minSetup

    // layout of nodes array:
    //  (3 + naddr) inputs for each port
    //  (width) inputs for each WRITE port
    //  (width) outputs for each READ port

    public SimMemory(String name,ArrayList inout,double tcd,double tpdr,double tr,double tpdf,double tf,double ts,double th,double cin,double cout,int width,int naddr,int nlocations,String filename,double contents[]) {
	super(name);

	this.tcd = tcd;
	this.tpdr = tpdr;
	this.tr = tr;
	this.tpdf = tpdf;
	this.tf = tf;
	this.ts = ts;
	this.th = th;
	this.cin = cin;
	this.cout = cout;
	this.width = width;
	this.naddr = naddr;
	this.nlocations = nlocations;
	this.filename = filename;

	// set up storage array itself
	bits = new int[((2*nlocations*width) + 31) / 32];

	// each port has
	//   3 control terminals (OE, CLK, WEN)
	//   naddr address terminals (input)
	//   width data terminals (input, output, or tristate)
	int nportbits = 3 + naddr + width;
	nports = inout.size() / nportbits;
	iOffset = new int[nports];
	oOffset = new int[nports];

	// first pass: compute number of inputs and outputs
	int nReadPorts = 0;
	int nWritePorts = 0;
	ninputs = nports * (3 + naddr);
	for (int i = 0; i < nports; i += 1) {
	    SimNode oe = (SimNode)inout.get(i*nportbits);
	    if (oe.isAlwaysZero()) oOffset[i] = -1;
	    else {
		oOffset[i] = noutputs;
		noutputs += width;
		nReadPorts += 1;
	    }

	    SimNode clk = (SimNode)inout.get(i*nportbits + 1);
	    SimNode wen = (SimNode)inout.get(i*nportbits + 2);
	    if (clk.isAlwaysZero() || wen.isAlwaysZero()) iOffset[i] = -1;
	    else {
		iOffset[i] = ninputs;
		ninputs += width;
		nWritePorts += 1;
		clk.setClock();  // special treatment during timing analysis
	    }
	}

	// second pass: allocate and fill in nodes array
	nodes = new SimNode[ninputs + noutputs];
	int index = 0;
	for (int i = 0; i < nports; i += 1) {
	    // fill in 3 control nodes and address inputs
	    int offset = i * (3 + naddr);
	    for (int j = 0; j < 3 + naddr; j += 1)
		nodes[offset + j] = (SimNode)inout.get(index++);

	    // if CLK and WEN can be nonzero, data nodes are inputs
	    offset = iOffset[i];
	    if (offset != -1) {
		for (int j = 1; j <= width; j += 1)
		    nodes[offset++] = (SimNode)inout.get(index + width - j);
	    }

	    // if OE can be nonzero, data nodes are outputs
	    offset = oOffset[i];
	    if (offset != -1) {
		for (int j = 1; j <= width; j += 1)
		    nodes[ninputs + offset++] = (SimNode)inout.get(index + width - j);
	    }

	    // all done with data terminals for this port
	    index += width;
	}

	// set up driver and fanout links
	SetupNodes();
	SimNetwork network = nodes[0].network;

	// size of each storage cell (not including access fet)
	double cell;
	if (nReadPorts == 1 && nWritePorts == 0) cell = 0;  // ROM
	else if (nlocations <= 1024)			    // SRAM
	    cell = network.GetOption("mem_size_sram",5);
	else cell = 0;					    // DRAM

	// add 1 access fet per port
	cell += nports * network.GetOption("mem_size_access",1);

	// start with storage cell area = number of bits * cell size
	// (1 access fet per port + size of storage cell)
	double size = (nlocations * width) * cell;
	// size of address buffers
	size += nports * naddr * network.GetOption("mem_size_address_buffer",20);
	// size of address decoders (assuming 4-input ands)
	size += nports * naddr * network.GetOption("mem_size_address_decoder",20);
	// size of tristate output drivers
	size += nReadPorts * width * network.GetOption("mem_size_output_buffer",30);
	// size of write data drivers
	size += nWritePorts * width * network.GetOption("mem_size_write_buffer",20);

	network.AddDevice(this,size);

	// estimate delays if none provided
	if (this.cin == 0) this.cin = network.GetOption("mem_cin",.1e-12);
	if (this.cout == 0) this.cout = network.GetOption("mem_cout",0);
	if (this.tr == 0) this.tr = network.GetOption("mem_tr",1000);	// 1 ns/pf
	if (this.tf == 0) this.tf = network.GetOption("mem_tf",1000);	// 1 ns/pf
	if (this.tpdr == 0) {
	    if (nlocations <= 128)		// reg file
		this.tpdr = network.GetOption("mem_tpdr_regfile",
					      network.GetOption("mem_tpd_regfile",4e-9));
	    else if (nlocations <= 1024)	// SRAM
		this.tpdr = network.GetOption("mem_tpdr_sram",
					      network.GetOption("mem_tpd_sram",8e-9));
	    else this.tpdr = network.GetOption("mem_tpdr_dram",
					       network.GetOption("mem_tpd_dram",40e-9));
	}
	if (this.tpdf == 0) {
	    if (nlocations <= 128)		// reg file
		this.tpdf = network.GetOption("mem_tpdf_regfile",
					      network.GetOption("mem_tpd_regfile",4e-9));
	    else if (nlocations <= 1024)	// SRAM
		this.tpdf = network.GetOption("mem_tpdf_sram",
					      network.GetOption("mem_tpd_sram",8e-9));
	    else this.tpdf = network.GetOption("mem_tpdf_dram",
					       network.GetOption("mem_tpd_dram",40e-9));
	}
	if (this.tcd == 0) this.tcd = network.GetOption("mem_tcd",.2e-9);
	if (this.ts == 0) this.ts = network.GetOption("mem_ts",2*this.tcd);
	if (this.th == 0) this.th = network.GetOption("mem_th",this.tcd);

	// compute initial contents from "contents" parameter
	if (contents != null && contents.length > 0) {
	    int nlocs = contents.length;
	    ibits = new int[((2*nlocs*width) + 31) / 32];
	    ClearMemory(ibits);
	    for (int i = 0; i < nlocs; i += 1) {
		long v = (long)contents[i];
		for (int bit = 0; bit < width; bit += 1)
		    WriteBit(ibits,i,bit,(int)((v >> bit) & 0x1));
	    }
	} else if (filename != null) {
	    // read initial contents from binary file
	    ibits = null;
	    try {
		File f = new File(filename);

		if (!f.exists()) {
		    network.NetworkError("Can't open memory file "+filename);
		    return;
		}

		int bytesPerLocation = (width + 7)/8;
		int nbytes = (int)f.length();
		int nlocs = (nbytes + bytesPerLocation - 1)/bytesPerLocation;
		ibits = new int[((2*nlocs*width) + 31) / 32];
		ClearMemory(ibits);

		FileInputStream in = new FileInputStream(f);
		int location = 0;
		int bit = 0;
		while (nbytes-- > 0) {
		    int bdata = in.read();
		    for (int i = 0; i < 8; i += 1) {
			WriteBit(ibits,location,bit,bdata & 0x1);
			bdata >>= 1;
			bit += 1;
			if (bit == width) {
			    bit = 0;
			    location += 1;
			    // start a new byte when we start a new location
			    break;
			}
		    }
		}
		in.close();
	    }
	    catch (Exception e) {
		network.NetworkError("Exception while reading memory file "+filename+": "+e);
	    }
	}
    }

    public int ReadBit(int location,int bit) {
	if (bit >= width) return -1;
	return ReadBit(bits,location,bit);
    }

    int ReadBit(int b[],int location,int bit) {
	int offset = 2*(bit + width*location);
	int index = offset >> 5;
	int shift = (offset & 0x1F);

	return (b[index] >> shift) & 0x3;
    }

    void WriteBit(int b[],int location,int bit,int v) {
	int offset = 2*(bit + width*location);
	int index = offset >> 5;
	int shift = offset & 0x1F;

	b[index] &= ~(0x3 << shift);
	b[index] |= (v & 0x3) << shift;
    }

    SimNode OE(int port) {
	return nodes[port * (3 + naddr)];
    }

    // address from port's addr terminals, -1 if invalid
    int Address(int port) {
	int index = port*(3 + naddr) + 3;
	int addr = 0;
	for (int i = 0; i < naddr; i += 1) {
	    int v = nodes[index++].v;
	    if (v == Node.VX || v == Node.VZ) return -1;
	    addr <<= 1;
	    if (v == Node.V1) addr |= 1;
	}
	return addr;
    }

    void UpdateSetup(SimNode n) {
	double now = n.network.time;
	if (now > 0) {
	    double tsetup = now - n.lastEvent;
	    if (tsetup < minSetup) {
		minSetup = tsetup;
		minSetupTime = now;
	    }
	}
    }

    // like Address, but update min setup time
    int WriteAddress(int port) {
	int index = port*(3 + naddr) + 3;
	int addr = 0;
	for (int i = 0; i < naddr; i += 1) {
	    SimNode n = nodes[index++];
	    UpdateSetup(n);
	    int v = n.v;
	    if (v == Node.VX || v == Node.VZ) return -1;
	    addr <<= 1;
	    if (v == Node.V1) addr |= 1;
	}
	return addr;
    }

    boolean isReadPort(int port) {
	return oOffset[port] != -1;
    }

    boolean isWritePort(int port) {
	return iOffset[port] != -1;
    }

    // true if this is a read port that is affecting its outputs
    boolean ActiveReadPort(int port) {
	// make sure it's a read port
	if (!isReadPort(port)) return false;

	// port is active if OE was just changed or OE != 0 and
	// some address input just changed
	SimNode oe = OE(port);
	if (oe.Trigger()) return true;
	if (oe.v == Node.V0) return false;
	int index = port * (3 + naddr) + 3;
	for (int i = 0; i < naddr; i += 1)
	    if (nodes[index + i].Trigger()) return true;
	return false;
    }

    // schedule propagation events for data terminals of a read port
    void UpdateReadPort(int port) {
	SimNode oe = OE(port);
	SimLookupTable tbl = SimLookupTable.TristateBufferTable.table[oe.v];
	int addr = Address(port);
	int index = ninputs + oOffset[port];
	for (int bit = 0; bit < width; bit += 1) {
	    nodes[index + bit].ScheduleCEvent(tcd);
	    int v = (addr < 0 || addr >= nlocations) ? Node.VX : ReadBit(bits,addr,bit);
	    v = tbl.table[v].value;
	    double drive,tpd;
	    if (v == Node.V1) { tpd = tpdr; drive = tr; }
	    else if (v == Node.V0) { tpd = tpdf; drive = tf; }
	    else { tpd = Math.min(tpdr,tpdf); drive = 0; }
	    nodes[index + bit].SchedulePEvent(tpd,v,drive,true);
	}
    }

    // true if this is a write port that should capture a new data value
    boolean ActiveWritePort(int port) {
	// make sure it's a write port
	if (!isWritePort(port)) return false;

	// port is active ie OE was just changed or OE != 0 and
	// some address input just changed
	int index = port * (3 + naddr);
	double now = nodes[index].network.time;
	SimNode clk = nodes[index + 1];
	SimNode wen = nodes[index + 2];

	return now > 0 && clk.Trigger() && clk.v != Node.V0 && wen.v != Node.V0;
    }

    // set all memory bits to "X" (0b10)
    void ClearMemory(int b[]) {
	for (int i = 0; i < b.length; i += 1) b[i] = 0xAAAAAAAA;
    }

    // location has changed so update any read ports
    void LocationChanged(int addr) {
	for (int port = 0; port < nports; port += 1)
	    if (isReadPort(port) && OE(port).v != Node.V0) {
		int paddr = Address(port);
		if (addr < 0 || paddr < 0 || paddr == addr)
		    UpdateReadPort(port);
	    }
    }

    public void Reset() {
	minSetup = Double.POSITIVE_INFINITY;
	minSetupTime = -1;

	// set initial contents
	ClearMemory(bits);
	if (ibits != null) {
	    int nwords = (ibits.length < bits.length) ? ibits.length : bits.length;
	    for (int i = 0; i < nwords; i += 1) bits[i] = ibits[i];
	}
    }

    // report minimum observed setup time
    public double MinObservedSetup() { return minSetup; }
    public double MinObservedSetupTime() { return minSetupTime; }

    // some input node has just processed a contamination event
    public void EvaluateC() {
	for (int port = 0; port < nports; port += 1)
	    // only read ports have outputs to contaminate
	    if (ActiveReadPort(port)) {
		int index = ninputs + oOffset[port];
		// schedule contamination event with specified delay
		// for read port outputs
		for (int bit = 0; bit < width; bit += 1)
		    nodes[index++].ScheduleCEvent(tcd);
	    }
    }

    // some input node has just processed a propagation event
    public void EvaluateP() {
	for (int port = 0; port < nports; port += 1) {
	    if (ActiveReadPort(port))
		UpdateReadPort(port);
	    if (ActiveWritePort(port)) {
		int addr = WriteAddress(port);
		SimNode clk = nodes[port*(3+naddr) + 1];
		SimNode wen = nodes[port*(3+naddr) + 2];
		boolean valid = (clk.v == Node.V1) && (wen.v == Node.V1);
		if (addr < 0) ClearMemory(bits);
		else if (addr < nlocations) {
		    int offset = iOffset[port];
		    for (int bit = 0; bit < width; bit += 1, offset += 1) {
			SimNode n = nodes[offset];
			UpdateSetup(n);
			WriteBit(bits,addr,bit,valid ? n.v : Node.VX);
		    }
		}
		LocationChanged(addr);
	    }
	}
    }

    // determine whether specified output can be tristated
    public boolean Tristate(SimNode n) {
	for (int i = 0; i < noutputs; i += 1)
	    if (nodes[ninputs + i] == n) return true;
	return false;
    }

    // capacitance of terminal(s) connected to this node
    public double Capacitance(SimNode n) {
	double c = 0;
	for (int i = 0; i < ninputs; i += 1)
	    if (nodes[i] == n) c += cin;
	for (int i = 0; i < noutputs; i += 1)
	    if (nodes[ninputs + i] == n) c += cout;
	return c;
    }

    public TimingInfo getTimingInfo(SimNode output) throws Exception {
	TimingInfo result = super.getTimingInfo(output);

	// add delay info for this memory
	double t1 = tpdr + tr*output.capacitance;
	double t2 = tpdf + tf*output.capacitance;
	result.setSpecs(tcd,Math.max(t1,t2));

	// look through all the ports to find READ ports with
	// this node as an output
	for (int port = 0; port < nports; port += 1) {
	    if (!isReadPort(port)) continue;
	    int out_index = ninputs + oOffset[port];
	    for (int bit = 0; bit < width; bit += 1)
		if (nodes[out_index + bit] == output) {
		    int oe_index = port*(3+naddr);
		    // check timing of OE
		    if (!nodes[oe_index].isPowerSupply())
			result.setDelays(nodes[oe_index].getTimingInfo());
		    // check timing of address inputs
		    for (int i = 0; i < naddr; i += 1) {
			SimNode n = nodes[oe_index + 3 + i];
			if (n.isPowerSupply()) continue;
			result.setDelays(n.getTimingInfo());
		    }
		    // done with this port
		    break;
		}
	}

	return result;
    }

    public TimingInfo getClockInfo(SimNode clk) throws Exception {
	TimingInfo result = null;

	// look through all the ports to find WRITE ports clocked by clk
	for (int port = 0; port < nports; port += 1) {
	    if (!isWritePort(port)) continue;
	    int index = port*(3+naddr);
	    if (nodes[index+1] != clk) continue;

	    // this WRITE port is clocked by clk, so timing of its inputs
	    // will affect timing results
	    if (result == null)	{
		result = new TimingInfo(clk,this);
		result.setSpecs(-th,ts);
	    }

	    // look at timing of WEN and address lines
	    for (int i = -1; i < naddr; i += 1)
		result.setDelays(nodes[index + 3 + i].getTimingInfo());

	    // look at timing of DATA inputs
	    index = iOffset[port];
	    for (int bit = 0; bit < width; bit += 1)
		result.setDelays(nodes[index + bit].getTimingInfo());
	}

	return result;
    }
}
