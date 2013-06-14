// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package netlist;

abstract class DevicePrototype extends SubcircuitObject {
    // "netlist" the device to make it part of new network
    abstract public boolean Netlist(Netlist network,NetlistConsumer n);
}
