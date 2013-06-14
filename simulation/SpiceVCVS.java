// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class SpiceVCVS extends SpiceDependentSource {
  public SpiceVCVS(SpiceNetwork network,int pos,int neg,int cpos,int cneg,double g) {
    super(pos,neg,cpos,cneg,g);

    int branch = network.size++;

    // vcvs can be handled completely by updating constants used
    // used to initialize admittance matrix on each iteration
    network.FindMatrixElement(k1,branch).gExp = 1;
    network.FindMatrixElement(k2,branch).gExp = -1;
    network.FindMatrixElement(branch,k1).gExp = 1;
    network.FindMatrixElement(branch,k2).gExp = -1;
    network.FindMatrixElement(branch,j1).gExp = -g;
    network.FindMatrixElement(branch,j2).gExp = g;
  }
}
