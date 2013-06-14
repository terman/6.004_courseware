// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceResistor extends SpiceDevice {
  int npos,nneg;		// just in case we might need them
  double conductance;

  public SpiceResistor(SpiceNetwork network,int n1,int n2,double y) {
    npos = n1;
    nneg = n2;
    conductance = y;

    // resistor can be handled completely by updating constant used
    // used to initialize admittance matrix on each iteration
    network.FindMatrixElement(n1,n1).gExp += y;
    network.FindMatrixElement(n1,n2).gExp -= y;
    network.FindMatrixElement(n2,n1).gExp -= y;
    network.FindMatrixElement(n2,n2).gExp += y;
  }
}
