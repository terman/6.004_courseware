// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

// superclass for things defined in a subcircuit: nodes, devices, models,
// other subcircuits...
abstract class SubcircuitObject {
    Subcircuit parent;			// what circuit we belong to
    Identifier id;			// user's name for this object
}

