// Copyright (C) 1998-2007 Christopher J. Terman - All Rights Reserved.

package simulation;

import netlist.NetlistConsumer;
import gui.UI;
import gui.ProgressTracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.io.FileOutputStream;
import java.io.PrintWriter;

public class SpiceNetwork extends Network {
    // type for each element in solution vector
    static final byte T_VOLTAGE = 1;	// node voltage
    static final byte T_IBRANCH = 2;	// branch currents (V and L)

    // default option values
    static final double TEMP = 25.0;	// simulation temp
    static final double CMIN = 0;	// capacitance added to each node
    static final double GMIN = 12.0;	// min conductance = 1e12
    static final double VABSTOL = 1e-6;	// nodal V tolerance
    static final double ABSTOL = 1e-12;	// nodal I tolerance
    static final double IABSTOL = 1e-9;	// residue convergence tolerance
    static final double RELTOL = .001;	// relative nodal V tolerance
    static final double LTERATIO = 3.5; // how close we have to come to prediction

    static final double DEFAS = 0;	// default MOS drain diffusion area
    static final double DEFPS = 0;	// default MOS drain diffusion perimeter
    static final double DEFNRS = 0;	// default # of squares for drain parasitic R
    static final double DEFAD = 0;	// default MOS source diffusion area
    static final double DEFPD = 0;	// default MOS source diffusion perimeter
    static final double DEFNRD = 0;	// default # of squares for source parasitic R
    static final double SCALE = 1.0;	// default scaling for MOSFET w/l

    HashMap models;		// names to models
    HashMap devices;		// names to devices
    int nfets;			// mosfet count

    ArrayList breakpoints;	// list of times at which breaks occur

    ArrayList sourceElements;	// index by row
    ArrayList admittanceMatrix;	// index by row, sort list by increasing column
    int size;			// number of nodes/branches in network

    SpiceDevice eachIteration;	// devices to be called each iteration
    SpiceDevice endOfTimestep;	// devices to be called at end of timestep

    SpiceCell gndCell;		// dummy cell for ground node
    SpiceCell rows[];		// first element in each row of matix
    SpiceCell sources[];		// source elements
    SpiceCell diagElements[];	// diagonal elements of each row
    SpiceNode solNodes[];	// nodes for each entry in solution vector
    byte solTypes[];		// what each element of solution represents

    double vnmax[];		// max voltage seen during simulation
    double vmax;
    double solution[];		// current solution
    double nextSolution[];	// next solution during iteration
    double previousSolution1[];	// last accepted solution
    double previousSolution2[];	// used for computing LTE
    double previousSolution3[];	//
    double time1,time2,time3;	// time of previousSolutions

    double coeff0,coeff1;	// used in numerical integration routines

    int problemNode;		// index of node with a problem
    SpiceDevice problemDevice;	// device with a problem

    double temperature;		// simulation temperature
    double cmin;		// minimum capacitance of each node
    double gmin;		// minimum PN-junction and channel conductance
    double vabstol;		// absolute delta V required for convergence
    double abstol;		// absolute delta I required for convergence
    double iabstol;		// absolute I tolerated in KCL check
    double reltol;		// relative delta required for convergence
    double lteratio;		// how close we have to be to prediction
    double defas;		// default MOS drain diffusion area
    double defps;		// default MOS drain diffusion perimeter
    double defnrs;		// default # of squares for drain parasitic R
    double defad;		// default MOS source diffusion area
    double defpd;		// default MOS source diffusion perimeter
    double defnrd;		// default # of squares for source parasitic R
    double scale;		// default scaling for MOSFET w/l

    int maxIterations;		// maximum number of iterations per solution
    public double maxTimestep;	// maximum timestep allowed
    double minTimestep;		// minimum timestep allowed before giving up
    double timestepDecreaseFactor;	// percentage decrease in timestep
    double timestepIncreaseFactor;	// percentage increase in timestep
    int increaseLimit;		// iteration limit to allow increased timestep

    public SpiceNetwork(HashMap options,String tempdir) {
	super(options,tempdir);
	models = new HashMap();
	devices = new HashMap();
	nfets = 0;

	gndCell = new SpiceCell(-1);	// dummy entry for gnd
	sourceElements = new ArrayList();
	admittanceMatrix = new ArrayList();

	size = 0;			// keep track of # of nodes/branches
	eachIteration = null;
	endOfTimestep = null;

	// user-specified options
	temperature = GetOption(".temp",TEMP);
	cmin = GetOption("cmin",CMIN);
	gmin = Math.pow(10,-GetOption("gmin",GMIN));

	vabstol = GetOption("vabstol",VABSTOL);
	abstol = GetOption("abstol",ABSTOL);
	iabstol = GetOption("iabstol",IABSTOL);
	reltol = GetOption("reltol",RELTOL);
	lteratio = GetOption("lteratio",LTERATIO);

	defas = GetOption("defas",DEFAS);
	defps = GetOption("defps",DEFPS);
	defnrs = GetOption("defnrs",DEFNRS);
	defad = GetOption("defad",DEFAD);
	defpd = GetOption("defpd",DEFPD);
	defnrd = GetOption("defnrd",DEFNRD);
	scale = GetOption("scale",SCALE);

	// initialize other control parameters
	maxIterations = 50;
	increaseLimit = 4;
	maxTimestep = 1;
	minTimestep = 1e-18;
	timestepDecreaseFactor = 0.3;
	timestepIncreaseFactor = 2.0;
    }

    public String Size() {
	return nfets+" mosfets";
    }

    public double NetworkSize() {
	return nfets;
    }

    // search hashtable and return name of specified device
    public String DeviceName(SpiceDevice d) {
	Iterator iter = devices.keySet().iterator();
	while (iter.hasNext()) {
	    String name = (String)iter.next();
	    if (devices.get(name) == d) return name;
	}
	return "???";
    }

    // add a break point to the breakpoint table
    public void AddBreakpoint(double t) {
	if (t == 0) return;	// don't need breakpoints at t=0

	int i = 0;
	int nbreaks = breakpoints.size();
	while (i < nbreaks) {
	    double bp = ((Double)breakpoints.get(i)).doubleValue();
	    if (t < bp) break;
	    else if (t == bp) return;
	    i += 1;
	}

	// insert a new break point
	breakpoints.add(i,new Double(t));
    }

    // return a SpiceCell for the ith source element
    public SpiceCell FindSourceElement(int i) {
	if (i < 0) return gndCell;			// index < 0 => gnd
	else {
	    sourceElements.ensureCapacity(i+1);
	    while (i >= sourceElements.size()) sourceElements.add(null);

	    SpiceCell c = (SpiceCell)sourceElements.get(i);
	    if (c != null) return c;
	    c = new SpiceCell(0);
	    sourceElements.set(i,c);
	    return c;
	}
    }
  
    // return a SpiceCell for the matrix element at i,j
    public SpiceCell FindMatrixElement(int i,int j) {
	if (i < 0 || j < 0) return gndCell;
	else {
	    admittanceMatrix.ensureCapacity(i+1);
	    while (i >= admittanceMatrix.size()) admittanceMatrix.add(null);

	    SpiceCell c = (SpiceCell)admittanceMatrix.get(i);
	    // see if our entry needs to go first
	    if (c == null || j < c.column) {
		SpiceCell nc = new SpiceCell(j);
		nc.nextColumn = c;
		admittanceMatrix.set(i,nc);
		return nc;
	    }
	    // look down list until we find the correct entry or
	    // come to where that entry should go in the list
	    while (true) {
		if (j == c.column) return c;
		if (c.nextColumn==null || j < c.nextColumn.column) break;
		c = c.nextColumn;
	    }
	    SpiceCell nc = new SpiceCell(j);
	    nc.nextColumn = c.nextColumn;
	    c.nextColumn = nc;
	    return nc;
	}
    }

    // look for SpiceCell at i,j, creating one if requested
    private SpiceCell FindCell(int i,int j,boolean create) {
	SpiceCell c = rows[i];
	// see if our entry needs to go first
	if (c == null || j < c.column) {
	    if (!create) return null;
	    SpiceCell nc = new SpiceCell(j);
	    nc.nextColumn = c;
	    rows[i] = nc;
	    return nc;
	}
	// look down list until we find the correct entry or
	// come to where that entry should go in the list
	while (true) {
	    if (j == c.column) return c;
	    if (c.nextColumn==null || j < c.nextColumn.column) break;
	    c = c.nextColumn;
	}
	if (!create) return null;
	SpiceCell nc = new SpiceCell(j);
	nc.nextColumn = c.nextColumn;
	c.nextColumn = nc;
	return nc;
    }

    // print out admittance matrix and source vector
    public void PrintMatrix() {
	try {
	    FileOutputStream m = new FileOutputStream("matrix",true);
	    PrintWriter out = new PrintWriter(m);

	    out.println("time = "+time);
	    out.println("Shape:");
	    for (int i = 0; i < size; i += 1) {
		for (int j = 0; j < size; j += 1) {
		    SpiceCell c = FindCell(i,j,false);
		    if (c == null) out.print(' ');
		    else if (c.gExp != 0) out.print('X');
		    else if (c.nonzero) out.print('*');
		    else out.print('-');
		}
		out.print('\n');
	    }

	    out.println("Matrix:");
	    for (int i = 0; i < size; i += 1)
		for (SpiceCell c = rows[i]; c != null; c = c.nextColumn)
		    out.println("("+i+","+c.column+"="+solNodes[c.column].name+") "+c.luExp+", "+c.gExp+", "+c.nonzero);
	    out.println("Source vector:");
	    for (int i = 0; i < size; i += 1)
		out.println("("+i+") "+sources[i].luExp+", "+sources[i].gExp);
	    out.close();
	}
	catch (Exception e) { }
    }

    public void PrintSolution(double timestep,String msg) {
	try {
	    FileOutputStream ofile = new FileOutputStream("solutions",true);
	    PrintWriter out = new PrintWriter(ofile);

	    out.println("\n\ntime = "+time+", step ="+timestep);
	    if (msg != null) out.println(msg);
	    for (int i = 0; i < size; i += 1) {
		out.print(solNodes[i].name);
		out.print(" = ");
		out.print(solution[i]);
		out.print(", ");
		out.print(previousSolution1[i]);
		out.print(", ");
		out.print(previousSolution2[i]);
		out.print(", ");
		out.print(previousSolution3[i]);
		out.print("\n");
	    }

	    out.close();
	}
	catch (Exception e) { }
    }
    
    // set up admittance matrix and source vector for another try...
    // return value indicates if all devices have converged
    private boolean LoadMatricies(int mode,double timestep) {
	// initialize matrix and source vector
	gndCell.luExp = 0;		// avoid over/underflows
	for (int i = 0; i < size; i += 1) {
	    SpiceCell c = sources[i];
	    c.luExp = c.gExp;
	    for (c = rows[i]; c != null; c = c.nextColumn) c.luExp = c.gExp;
	}
	// let each device who wants to add its contribution to the matrix
	boolean devicesConverged = true;
	for (SpiceDevice d = eachIteration; d != null; d = d.iterationLink) {
	    if (!d.EachIteration(mode,time,timestep)) {
		devicesConverged = false;
		problemDevice = d;
	    }
	}

	/*PrintMatrix();*/

	return devicesConverged;
    }

    // return true if solution found, false if matrix is singular
    private boolean DecomposeAndSolve() {
	// pivot along the diagonal to form L and U.  Note that the matrix
	// has been reordered using the minimum degree rule by the Finalize
	// routine and any necessary fill-ins have already been generated.
	for (int k = 0; k < size; k += 1) {
	    SpiceCell kk = diagElements[k];
	    if (kk.luExp == 0) {
		problem = "Singular matrix: "+solNodes[k].name;
		System.out.println(problem);
		PrintMatrix();
		return false;
	    }
	    for (SpiceCell kj = kk.nextColumn; kj != null; kj = kj.nextColumn) {
		double mult = kj.luExp / kk.luExp;
		if (mult != 0) {
		    int j = kj.column;
		    kj.luExp = mult;
		    for (SpiceCell ik = kk.nextRow; ik != null; ik = ik.nextRow) {
			SpiceCell ij = ik;
			while (ij != null && ij.column < j) ij = ij.nextColumn;
			if (ij == null || ij.column != j) {
			    System.out.println("missing fillin at ("+ij.row+","+j+")");
			    return false;
			}
			ij.luExp -= mult*ik.luExp;
		    }
		}
	    }
	}

	// forward substitution to solve L equations
	for (int i = 0; i < size; i += 1) {
	    double dot = sources[i].luExp;
	    for (SpiceCell entry = rows[i]; entry != null; entry = entry.nextColumn)
		if (entry.column == i) {
		    nextSolution[i] = dot / entry.luExp;
		    break;
		} else dot -= entry.luExp * nextSolution[entry.column];
	}

	// backward substitution to solve U equations
	for (int i = size-2; i >= 0; i -= 1) {
	    double dot = nextSolution[i];
	    for (SpiceCell entry = diagElements[i].nextColumn;
		 entry != null;
		 entry = entry.nextColumn)
		dot -= entry.luExp * nextSolution[entry.column];
	    nextSolution[i] = dot;
	}

	return true;
    }

    // return number of iterations required for convergence, or
    // -1 if no convergence or matrix is singular
    private int FindNetworkSolution(int mode,double timestep,int iterationLimit,boolean breakpoint) {
	boolean devicesConverged = false;
	double problemI = 0;
	boolean loaded = false;
	problem = null;

	// setup numerical integration coefficients
	if (breakpoint) {
	    coeff0 = 1/timestep;
	    coeff1 = 0;
	} else {
	    coeff0 = 2/timestep;
	    coeff1 = 1;
	}

    iter_loop:
	for (int iter = 1; iter <= iterationLimit; iter += 1) {
	    // load up the source vector and admittance matrix
	    if (loaded) loaded = false;
	    else devicesConverged = LoadMatricies(mode,timestep);

	    // solve for voltages/currents using LU decomposition
	    if (!DecomposeAndSolve()) return -1;

	    // save computed solution as the current solution
	    double sol[] = solution;
	    solution = nextSolution;
	    nextSolution = sol;

	    // if all the devices reported convergence, see if nodes have
	    // converged too.  Also check that the solution comes reasonably
	    // close to satisfying KCL.
	    if (devicesConverged) {
		problemNode = -1;
		problemI = 0;

		// see if voltages have stabilized by comparing this
		// solution with the one from last iteration.
		for (int i = 0; i < size; i += 1) {
		    double xnew = solution[i];
		    double xold = nextSolution[i];
		    double atol;
		    double max;
		    if (solTypes[i] == T_VOLTAGE) {
			max =  vnmax[i];
			if (max == 0)
			    max = Math.max(Math.abs(xnew),Math.abs(xold));
			atol = vabstol;
		    } else {
			max = Math.max(Math.abs(xnew),Math.abs(xold));
			atol = abstol;
		    }
		    if (atol == 0) continue;
		    // Newton update convergence criterion
		    if (Math.abs(xnew - xold) >= (atol + reltol*max)) {
			problemNode = i;
			//System.out.println(solNodes[i].name+": xnew="+xnew+" xold="+xold+" max="+max);
			continue iter_loop;
		    }
		}

		// now check on residue criterion (ie, see if each equation
		// in the matix is actually close to a solution).
		devicesConverged = LoadMatricies(mode,timestep);
		loaded = true;
		if (!devicesConverged) continue iter_loop;
		if (iabstol > 0) {
		    for (int r = 0; r < size; r += 1) {
			double imax = 0;
			double inode = -sources[r].luExp;
			for (SpiceCell c = rows[r]; c != null; c = c.nextColumn) {
			    double i = c.luExp*solution[c.column];
			    imax = Math.max(imax,Math.abs(i));
			    inode += i;
			}
			if (Math.abs(inode) >= (iabstol + reltol*imax)) {
			    problemNode = r;
			    problemI = inode;
			    //System.out.println(solNodes[r].name+": inode="+inode+" imax="+imax);
			    continue iter_loop;
			}
		    }
		}

		// we converged!
		if (problemNode == -1) return iter;
	    }
	}

	// didn't converge in specified number of iterations
	if (!devicesConverged)
	    problem = "Device "+DeviceName(problemDevice)+
		" didn't converge: "+problemDevice;
	else if (problemI != 0)
	    problem = "KCL violated: "+
		 solNodes[problemNode].name+" ["+problemI+"]";
	else problem = "Exceeded convergence criteria: "+
		 solNodes[problemNode].name+" ["+solution[problemNode]+
		 ","+nextSolution[problemNode]+"]";
	//System.out.println("time="+time+" step="+timestep);
	//System.out.println(problem);
	return -1;
    }

    // retun device constraints on maximum next timestep, -1 if no constraint
    private void AcceptTimestep(double timestep,boolean breakpoint) {
	// remember solution at previous timesteps so we can check
	// solution at next timestep against a polynomial extrapolation
	double temp[] = previousSolution3;
	previousSolution3 = previousSolution2;
	time3 = breakpoint ? -1 : time2;
	previousSolution2 = previousSolution1;
	time2 = breakpoint ? -1 : time1;
	previousSolution1 = temp;
	time1 = time;
	for (int i = 0; i < size; i += 1) {
	    double v = solution[i];
	    previousSolution1[i] = v;
	    if (solTypes[i] == T_VOLTAGE) {
		v = Math.abs(v);
		vnmax[i] = Math.max(vnmax[i],v);
		vmax = Math.max(vmax,v);
	    }
	}

	// let each device do it's end of timestep processing
	for (SpiceDevice d = endOfTimestep; d != null; d = d.timestepLink)
	    d.EndOfTimestep(breakpoint,timestep);
    }

    public int FindOperatingPoint(int mode) {
	time = 0.0;			// reset time

	// initialize solution vector
	vmax = 0;
	for (int i = 0; i < size; i += 1) {
	    solution[i] = 0.0;
	    vnmax[i] = 0.0;
	}

	// handle initial voltages here...

	int result = FindNetworkSolution(mode,0.0,maxIterations,true);
	AcceptTimestep(0.0,true);
	return result;
    }

    // Check new voltages against prediction based on previous
    // points.  If difference gets large then we need to reduce
    // the timestep to keep LTE within reasonable bounds.
    public boolean CheckPrediction() {
	int npoints = 0;
	double f = 0;
	double h13 = 0;
	double h23 = 0;
	double h03 = 0;

	if (time3 == -1) {
	    if (time2 == -1) {
		npoints = 1;
	    } else {
		npoints = 2;
		f = (time - time2)/(time1 - time2);
	    }
	} else {
	    npoints = 3;
	    h23 = time2 - time3;
	    h13 = time1 - time3;
	    h03 = time - time3;
	    f = 1 / (h13 - h23);
	}

	for (int i = 0; i < size; i += 1)
	    if (solTypes[i] == T_VOLTAGE) {
		double vpred;
		if (npoints == 3) {
		    // fit a second degree polynomial to the previous 3 points
		    vpred = previousSolution3[i];
		    double dv23 = previousSolution2[i] - vpred;
		    double dv13 = previousSolution1[i] - vpred;
		    double B = dv23/h23;
		    double A = f*(dv13/h13 - B);
		    B -= A*h23;
		    vpred += (A*h03 + B)*h03;
		} else if (npoints == 2) {
		    // fit a line to the previous 2 points
		    vpred = previousSolution2[i];
		    vpred += f*(previousSolution1[i] - vpred);
		} else {
		    // just one previous point...
		    vpred = previousSolution1[i];
		}

		double max = vmax; //vnmax[i];
		if (max == 0)
		    max = Math.max(Math.abs(solution[i]),Math.abs(vpred));
		if (Math.abs(solution[i] - vpred) >= lteratio*(vabstol + reltol*max)) {
		    problemNode = i;
		    problem = "node "+solNodes[i].name+": v="+solution[i]+" pred="+vpred+" npts="+npoints+" max="+max;
		    //System.out.print("node "+solNodes[i].name);
		    //System.out.print(": v="+solution[i]+" pred="+vpred+" npts="+npoints);
		    //System.out.println(" prev="+previousSolution1[i]+","+previousSolution2[i]+","+previousSolution3[i]);
		    return false;
		}
	    }

	return true;
    }

    public double GetTime() {
	return time;
    }

    // simulator interface

    // all done adding devices, finish setting up the network
    public boolean Finalize() {
	if (invalidDevice) return false;
	if (mergedNodes.size() != 0) {
	    problem = "Can't use .connect in device-level simulation";
	    return false;
	}

	solution = new double[size];
	nextSolution = new double[size];
	previousSolution1 = new double[size];
	previousSolution2 = new double[size];
	previousSolution3 = new double[size];
	vnmax = new double[size];

	rows = new SpiceCell[size];	// admittance matrix
	sources = new SpiceCell[size];	// source elements
	diagElements = new SpiceCell[size];	// diagonal elements of each row
	solNodes = new SpiceNode[size];	// names for each entry in solution
	solTypes = new byte[size];	// types for each entry in solution

	// fill in names for each entry in solution vector
	Iterator iter = nodes.keySet().iterator();
	while (iter.hasNext()) {
	    String name = (String)iter.next();
	    SpiceNode node = (SpiceNode)nodes.get(name);
	    int index = node.index;
	    if (index >= 0) {
		solNodes[index] = node;
		if (name.length()>=2 && name.charAt(0)=='i' && name.charAt(1)=='(')
		    solTypes[index] = T_IBRANCH;
		else {
		    solTypes[index] = T_VOLTAGE;
		    if (cmin > 0) {
			SpiceDevice d = new SpiceCapacitor(this,index,-1,cmin);
			devices.put("CMIN-"+index,d);
		    }
		}
	    }
	}

	// initialize matrix and source vectors
	time1 = -1;
	time2 = -1;
	time3 = -1;
	for (int i = 0; i < size; i += 1) {
	    previousSolution1[i] = 0;
	    previousSolution2[i] = 0;
	    previousSolution3[i] = 0;
	    rows[i] = (SpiceCell)admittanceMatrix.get(i);

	    // add a large resistor from every node to ground
	    //if (solTypes[i] == T_VOLTAGE) {
	    //SpiceCell c = FindCell(i,i,true);
	    //if (c.gExp != 0) c.gExp = gmin;
	    //}

	    for (SpiceCell c = rows[i]; c != null; c = c.nextColumn)
		if (c.gExp != 0) c.nonzero = true;
	    sources[i] = FindSourceElement(i);
	    diagElements[i] = null;		// we'll fill this in later
	}

	//PrintMatrix();

	// reorder rows in the matrix so as to generate as few fill-ins
	// as possible
	for (int i = 0; i < size-1; i += 1) {
	    int minDegree = size+1;
	    int minDegreeRow = -1;
	    // find row with minimum remaining degree that has an element
	    // in column i (so that it can be swapped with current row i)
	    for (int j = i; j < size; j += 1) {
		SpiceCell diagonal = null;
		int degree = 0;
		for (SpiceCell c = rows[j]; c != null; c = c.nextColumn)
		    if (c.column >= i) {
			// cell on diagonal only counts if we can guarantee
			// it won't be zero, ie, it's gExp isn't zero
			if (c.column==i && c.nonzero) diagonal = c;
			degree += 1;
		    }
		if (diagonal != null && degree < minDegree) {
		    minDegree = degree;
		    minDegreeRow = j;
		}
	    }

	    // oops, can't find a row to use...
	    if (minDegreeRow == -1) {
		problem = "Can't find diagonal element: "+solNodes[i].name;
		//System.out.println(problem);
		PrintMatrix();
		return false;
	    }

	    // swap rows if we found one that will generate fewer fillins
	    if (i != minDegreeRow) {
		SpiceCell temp = rows[i];
		rows[i] = rows[minDegreeRow];
		rows[minDegreeRow] = temp;
		temp = sources[i];
		sources[i] = sources[minDegreeRow];
		sources[minDegreeRow] = temp;
	    }

	    // generate necessary fill-ins before considering other swaps.
	    SpiceCell rowI = FindCell(i,i,false).nextColumn;
	    for (int j = i+1; j < size; j += 1) {
		// for each row j following row i, if [j,i] is non-zero then
		// make sure there's an entry for [j,k] if [i,k] is non-zero
		// for each k > i
		SpiceCell cellJI = FindCell(j,i,false);
		if (cellJI != null)
		    for (SpiceCell c = rowI; c != null; c = c.nextColumn) {
			SpiceCell cellJK = FindCell(j,c.column,true);
			cellJK.nonzero |= c.nonzero & cellJI.nonzero;
		    }
	    }
	}

	// construct next row links starting with last row.  Use
	// diagElements vector as a temporary pointer to most recent
	// row seen for each column
	for (int i = size-1; i >= 0; i -= 1)
	    for (SpiceCell c = rows[i]; c != null; c = c.nextColumn) {
		c.nextRow = diagElements[c.column];
		diagElements[c.column] = c;
	    }

	// find diagonal elements and fill in row numbers
	for (int i = 0; i < size; i += 1)
	    for (SpiceCell c = rows[i]; c != null; c = c.nextColumn) {
		c.row = i;
		if (c.column == i) diagElements[i] = c;
	    }

	// PrintMatrix();

	return true;
    }

    public boolean TransientAnalysis(double stopTime,double maxTimestep,ProgressTracker jpanel) {
	mode = TRANSIENT_ANALYSIS;
	dcLabels.clear();
	if (jpanel != null) jpanel.ProgressStart(this);
	ResetHistory();

	//System.out.println("Transient Analysis: tstop="+stopTime);

	// build the break point list
	breakpoints = new ArrayList();
	Iterator iter = devices.values().iterator();
	while (iter.hasNext()) {
	    SpiceDevice d = (SpiceDevice)iter.next();
	    d.ComputeBreakpoints(this,stopTime);
	}
	int nbreaks = breakpoints.size();
	int breakIndex = 0;
	
	// start by finding operating point for network
	FindOperatingPoint(TRANSIENT_ANALYSIS_INIT);

	for (int i = 0; i < size; i += 1) {
	    solNodes[i].RecordValue(this,time,solution[i]);
	    //System.out.println(solNodes[i].name+"="+solution[i]);
	}

	double currentTimestep = Math.min(stopTime,maxTimestep);
	double timeAtStartOfStep = 0;
	boolean breakpoint = true;	// starting counts as a breakpoint

	while (time < stopTime) {
	    if (Thread.interrupted()) break;
	    Thread.yield();	// make sure other threads work too...

	    // if currentTimestep would take us past some interesting
	    // breakpoint, recalculate timestep
	    if (breakIndex < nbreaks) {
		double bp = ((Double)breakpoints.get(breakIndex)).doubleValue();
		double maxNextTimestep = bp - time;
		if (currentTimestep > maxNextTimestep)
		    currentTimestep = Math.max(maxNextTimestep,minTimestep);
	    }

	    time += currentTimestep;
	    int numberOfIterations = FindNetworkSolution(TRANSIENT_ANALYSIS,
							 currentTimestep,
							 maxIterations,
							 breakpoint);

	    /*
	    if (time >= 0) {
		String msg = "iter="+numberOfIterations;
		if (numberOfIterations > 0)
		    msg += " pred="+CheckPrediction()+" time1="+time1+" time2="+time2+" time3="+time3;
		if (problem != null) msg += " prob="+problem;
		PrintSolution(currentTimestep,msg);
		PrintMatrix();
		}*/

	    // if we didn't converge, try again with a smaller timestep
	    // Also check new voltages against prediction based on previous
	    // points.  If difference gets large then we need to reduce
	    // the timestep to keep LTE within reasonable bounds.
	    if (numberOfIterations < 0 || !CheckPrediction()) {
		// give up if we've reached minimum allowable timestep
		if (currentTimestep <= minTimestep) {
		    problem = "At time = "+time+": node "+solNodes[problemNode].name+" doesn't converge with minimum timestep; try Fast Transient Analysis";
		    if (numberOfIterations < 0) problem += " (iter)";
		    else problem += " (pred)";
		    //System.out.println(problem);
		    break;
		}

		// let's try again with a smaller timestep after restoring
		// previous solution
		time = timeAtStartOfStep;
		for (int i = 0; i < size; i += 1)
		    solution[i] = previousSolution1[i];
		Iterator diter = devices.values().iterator();
		while (diter.hasNext()) {
		    SpiceDevice d = (SpiceDevice)diter.next();
		    d.RestoreState(time);
		}
		currentTimestep = Math.max(currentTimestep*timestepDecreaseFactor,minTimestep);
		//System.out.println("time="+time+" step-="+currentTimestep);
		continue;
	    }


	    // have we reached break point? If we're within minTimestep
	    // of the break point, we pretend that we've reached it.
	    // Use a while loop in case there's more than one timepoint
	    // in our near future...
	    breakpoint = false;
	    while (breakIndex < nbreaks) {
		double bp = ((Double)breakpoints.get(breakIndex)).doubleValue();
		if (bp - time < minTimestep) {
		    breakpoint = true;
		    time = bp;
		    currentTimestep += bp - time;
		    breakIndex += 1;
		} else break;
	    }

	    // we did converge, so accept this timestep
	    AcceptTimestep(currentTimestep,breakpoint);
	    timeAtStartOfStep = time;

	    // report back on our progress
	    if (jpanel != null) jpanel.ProgressReport(this,time/stopTime);
	    for (int i = 0; i < size; i += 1)
		solNodes[i].RecordValue(this,time,solution[i]);

	    // if we converged fast enough, consider increasing timestep
	    if (numberOfIterations <= increaseLimit) {
		currentTimestep = Math.min(currentTimestep*timestepIncreaseFactor,maxTimestep);
		//System.out.println("time="+time+" step+="+currentTimestep);
	    }

	    // make sure we stop when requested
	    if (time + currentTimestep > stopTime) {
		currentTimestep = stopTime - time;
		if (currentTimestep < minTimestep) time = stopTime;
	    }
	}

	long elapsedTime = 0;
	if (jpanel != null) elapsedTime = jpanel.ProgressStop(this);
	//System.out.println("done, elapsed time "+(elapsedTime/1000)+" seconds");
	nsamples = hIndex;
	return problem == null;
    }

    public boolean DCAnalysis(String sweep1,double start1,double stop1,double step1,
			   String sweep2,double start2,double stop2,double step2,
			   ProgressTracker jpanel) {
	SpiceDevice d1,d2;
	SpiceIndependentSource s1,s2;
	double saveDC1,saveDC2 = 0;

	mode = DC_ANALYSIS;
	dcLabels.clear();

	// see if devices are usable in a DC analysis
	d1 = (SpiceDevice)devices.get(sweep1);
	if (d1 == null || !(d1 instanceof SpiceIndependentSource)) {
	    problem = "Device not independent source in DC analysis: "+sweep1;
	    return false;
	}
	s1 = (SpiceIndependentSource)d1;
	saveDC1 = s1.source.dc;		// we'll override this for a moment

	// second sweep source is optional
	if (sweep2 == null) s2 = null;
	else {
	    d2 = (SpiceDevice)devices.get(sweep2);
	    if (d2 == null || !(d2 instanceof SpiceIndependentSource)) {
		problem = "Device not independent source in DC analysis: "+sweep2;
		return false;
	    }
	    s2 = (SpiceIndependentSource)d2;
	    saveDC2 = s2.source.dc;		// we'll override this for a moment
	}

	if (jpanel != null) jpanel.ProgressStart(this);
	ResetHistory();
	//System.out.println("DC Analysis: "+sweep1+" "+sweep2);

	double v1 = start1;
	if (step1 == 0) step1 = (stop1 - start1)/10;
	double inc1 = (start1 < stop1) ? Math.abs(step1) : -Math.abs(step1);
	int index1 = 0;			// step number for first source
	double v2 = start2;
	if (step2 == 0) step2 = (stop2 - start2)/10;
	double inc2 = (start2 < stop2) ? Math.abs(step2) : -Math.abs(step2);
	int index2 = 0;			// step number for second source

	double progress = 0;
	double pinc = 1/(((start1 - stop1)/inc1 + 1) *
			 ((stop2 - start2)/inc2 + 1));
	while (true) {
	    if (Thread.interrupted()) break;
	    Thread.yield();	// make sure other threads work too...

	    // start by setting source values
	    s1.source.dc = v1;
	    if (s2 != null) s2.source.dc = v2;

	    // now find operating point of the network and report results
	    FindOperatingPoint(OPERATING_POINT);
	    if (problem != null) break;

  	    // report back on our progress
    	    if (jpanel != null) {
		jpanel.ProgressReport(this,progress);
		progress += pinc;
	    }

	    // record solution
	    for (int i = 0; i < size; i += 1)
		solNodes[i].RecordValue(this,v1,solution[i]);

	    // on to next step in the sweep
	    if (v1 == stop1) {
		nsamples = index1 + 1;

		// finished sweeping first source, bump the second source (if any)
		if (s2 == null) break;
		dcLabels.add(0,"|"+sweep2+"="+UI.EngineeringNotation(v2,3)+"V");
		if (v2 == stop2) break;
		v1 = start1;	// start over again
		index1 = 0;
		v2 += inc2;
		index2 += 1;
		if ((inc2 > 0 && v2 > stop2) || (inc2 < 0 && v2 < stop2))
		    v2 = stop2;
	    } else {
		v1 += inc1;
		index1 += 1;
		if ((inc1 > 0 && v1 > stop1) || (inc1 < 0 && v1 < stop1))
		    v1 = stop1;
	    }
	}

	// restore saved DC values
	s1.source.dc = saveDC1;
	if (s2 != null) s2.source.dc = saveDC2;
	if (jpanel != null) jpanel.ProgressStop(this);
	//System.out.println("done");

	return problem == null;
    }

    public Object MakeModel(String name,int mtype,HashMap options) {
	SpiceModel m = (SpiceModel)models.get(name);

	if (m == null) {
	    switch (mtype) {
	    case NetlistConsumer.NMOS:
	    case NetlistConsumer.PMOS:
		Double Level = (Double)options.get("level");
		int level = (Level == null) ? 1 : Level.intValue();
		if (level == 3)
		    m = new SpiceMOSModel_L3(name,mtype,options,temperature);
		else
		    m = new SpiceMOSModel_L1(name,mtype,options,temperature);
		break;
	    default:
		m = new SpiceModel(name,options);
		break;
	    }
	    models.put(name,m);
	} // else System.out.println("Duplicate MakeModel for "+name);
	return m;
    }

    public Object FindNode(String name,boolean create) {
	SpiceNode n = (SpiceNode)nodes.get(name);
	if (n == null && create) {
	    n = new SpiceNode(name,size++);
	    nodes.put(name,n);
	}
	return n;
    }

    public Object MakeGndNode(String name) {
	SpiceNode n = (SpiceNode)nodes.get(name);

	if (n == null) {
	    n = new SpiceNode(name,-1);
	    nodes.put(name,n);
	} // else System.out.println("Duplicate MakeGndNode for "+name);
	return n;
    }

    public Object FindDevice(String name) {
	return devices.get(name);
    }

    public boolean MakeResistor(String id,Object n1,Object n2,double resistance) {
	SpiceDevice d = new SpiceResistor(this,
					  ((SpiceNode)n1).index,
					  ((SpiceNode)n2).index,
					  1/resistance);
	devices.put(id,d);
	return true;
    }

    public boolean MakeCapacitor(String id,Object n1,Object n2,double capacitance) {
	SpiceDevice d = new SpiceCapacitor(this,
					   ((SpiceNode)n1).index,
					   ((SpiceNode)n2).index,
					   capacitance);
	devices.put(id,d);
	return true;
    }

    public boolean MakeInductor(String id,Object n1,Object n2,double inductance) {
	SpiceDevice d = new SpiceInductor(this,
					  ((SpiceNode)n1).index,
					  ((SpiceNode)n2).index,
					  inductance);
	// label new matrix row for branch current
	String name = "i("+id+")";
	nodes.put(name,new SpiceNode(name,size-1));
	devices.put(id,d);
	return true;
    }

    public boolean MakeMosfet(String id,Object d,Object g,Object s,Object b,
			      Object model,double l,double w,double sl,double sw,
			      double ad,double pd,double nrd,double rdc,
			      double as,double ps,double nrs,double rsc) {
	SpiceDevice dev = new SpiceMosfet(this,
					  ((SpiceNode)d).index,
					  ((SpiceNode)g).index,
					  ((SpiceNode)s).index,
					  ((SpiceNode)b).index,
					  (SpiceMOSModel)model,l,w,sl,sw,
					  ad,pd,nrd,rdc,as,ps,nrs,rsc);
	devices.put(id,dev);
	nfets += 1;
	return true;
    }

    public boolean MakeIndependentVoltageSource(String id,Object npos,Object nneg,
						double dc,double acmag,double acphase,
						int trantype,double params[]) {
	SpiceDevice d = new SpiceIndependentVoltageSource(this,
							  ((SpiceNode)npos).index,
							  ((SpiceNode)nneg).index,
							  dc,acmag,acphase,
							  trantype,params,Vil,Vih);
	// label new matrix row for branch current
	String name = "i("+id+")";
	nodes.put(name,new SpiceNode(name,size-1));
	devices.put(id,d);
	return true;
    }

    public boolean MakeIndependentCurrentSource(String id,Object npos,Object nneg,
						double dc,double acmag,double acphase,
						int trantype,double params[]) {
	SpiceDevice d = new SpiceIndependentCurrentSource(this,
							  ((SpiceNode)npos).index,
							  ((SpiceNode)nneg).index,
							  dc,acmag,acphase,
							  trantype,params,Vil,Vih);
	devices.put(id,d);
	return true;
    }

    public boolean MakeVCVS(String id,Object npos,Object nneg,
			    Object ncpos,Object ncneg,double gain) {
	SpiceDevice d = new SpiceVCVS(this,
				      ((SpiceNode)npos).index,
				      ((SpiceNode)nneg).index,
				      ((SpiceNode)ncpos).index,
				      ((SpiceNode)ncneg).index,
				      gain);
	// label new matrix row for branch current
	String name = "i("+id+")";
	nodes.put(name,new SpiceNode(name,size-1));
	devices.put(id,d);
	return true;
    }

    public boolean MakeVCCS(String id,Object npos,Object nneg,
			 Object ncpos,Object ncneg,double gain) {
	SpiceDevice d = new SpiceVCCS(this,
				      ((SpiceNode)npos).index,
				      ((SpiceNode)nneg).index,
				      ((SpiceNode)ncpos).index,
				      ((SpiceNode)ncneg).index,
				      gain);
	devices.put(id,d);
	return true;
    }

    public boolean MakeCCVS(String id,Object npos,Object nneg,
			 Object ncpos,Object ncneg,double gain) {
	SpiceDevice d = new SpiceCCVS(this,
				      ((SpiceNode)npos).index,
				      ((SpiceNode)nneg).index,
				      ((SpiceNode)ncpos).index,
				      ((SpiceNode)ncneg).index,
				      gain);
	// label new matrix rows for branch currents
	String name = "i(1,"+id+")";
	nodes.put(name,new SpiceNode(name,size-2));
	name = "i(2,"+id+")";
	nodes.put(name,new SpiceNode(name,size-1));
	devices.put(id,d);
	return true;
    }

    public boolean MakeCCCS(String id,Object npos,Object nneg,
			 Object ncpos,Object ncneg,double gain) {
	SpiceDevice d = new SpiceCCCS(this,
				      ((SpiceNode)npos).index,
				      ((SpiceNode)nneg).index,
				      ((SpiceNode)ncpos).index,
				      ((SpiceNode)ncneg).index,
				      gain);
	// label new matrix row for branch current
	String name = "i("+id+")";
	nodes.put(name,new SpiceNode(name,size-1));
	devices.put(id,d);
	return true;
    }

    public String SimulationType() { return "device-level simulation"; }
}
