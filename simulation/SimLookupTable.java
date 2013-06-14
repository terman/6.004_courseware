// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

public class SimLookupTable {
    public SimLookupTable table[];	// indexed by 4-value logic
    public int value;			// logic value for this table

    static public SimLookupTable LTable = InitializeTables();
    static public SimLookupTable HTable;
    static public SimLookupTable XTable;
    static public SimLookupTable ZTable;
    static public SimLookupTable SelectTable;
    static public SimLookupTable SelectNextTable;
    static public SimLookupTable Ensure0Table;
    static public SimLookupTable Ensure1Table;
    static public SimLookupTable EqualTable;

    static public SimLookupTable BusTable;
    static public SimLookupTable Bus0Table;
    static public SimLookupTable Bus1Table;
    static public SimLookupTable TristateBufferTable;

    static public SimLookupTable AndXTable;
    static public SimLookupTable AndTable;
    static public SimLookupTable NandXTable;
    static public SimLookupTable NandTable;
    static public SimLookupTable OrXTable;
    static public SimLookupTable OrTable;
    static public SimLookupTable NorXTable;
    static public SimLookupTable NorTable;
    static public SimLookupTable XorTable;
    static public SimLookupTable Xor1Table;
    static public SimLookupTable Mux2Table;

    public SimLookupTable(int value) {
	table = new SimLookupTable[4];
	this.value = value;
    }

    public void Setup(SimLookupTable t1,SimLookupTable t2,SimLookupTable t3,SimLookupTable t4) {
	table[0] = t1;
	table[1] = t2;
	table[2] = t3;
	table[3] = t4;
    }

    // set up some useful tables
    public static SimLookupTable InitializeTables() {
	if (LTable == null) {
	    // always "0"
	    LTable = new SimLookupTable(Node.V0);
	    LTable.Setup(LTable,LTable,LTable,LTable);
	    // always "1"
	    HTable = new SimLookupTable(Node.V1);
	    HTable.Setup(HTable,HTable,HTable,HTable);
	    // always "X"
	    XTable = new SimLookupTable(Node.VX);
	    XTable.Setup(XTable,XTable,XTable,XTable);
	    // always "Z"
	    ZTable = new SimLookupTable(Node.VZ);
	    ZTable.Setup(ZTable,ZTable,ZTable,ZTable);
	    // select this
	    SelectTable = new SimLookupTable(Node.VX);
	    SelectTable.Setup(LTable,HTable,XTable,XTable);
	    // select next
	    SelectNextTable = new SimLookupTable(Node.VX);
	    SelectNextTable.Setup(SelectTable,SelectTable,SelectTable,SelectTable);
	    // must be 0
	    Ensure0Table = new SimLookupTable(Node.VX);
	    Ensure0Table.Setup(LTable,XTable,XTable,XTable);
	    // must be 1
	    Ensure1Table = new SimLookupTable(Node.VX);
	    Ensure1Table.Setup(XTable,HTable,XTable,XTable);
	    // this == next
	    EqualTable = new SimLookupTable(Node.VX);
	    EqualTable.Setup(Ensure0Table,Ensure1Table,XTable,XTable);

	    // tristate bus resolution
	    // produces "Z" if all inputs are "Z"
	    // produces "1" if one input is "1" and other inputs are "1" or "Z"
	    // produces "0" if one input is "0" and other inputs are "0" or "Z"
	    // produces "X" otherwise
	    BusTable = new SimLookupTable(Node.VZ);
	    Bus0Table = new SimLookupTable(Node.V0);
	    Bus1Table = new SimLookupTable(Node.V1);
	    BusTable.Setup(Bus0Table,Bus1Table,XTable,BusTable);
	    Bus0Table.Setup(Bus0Table,XTable,XTable,Bus0Table);
	    Bus1Table.Setup(XTable,Bus1Table,XTable,Bus1Table);

	    // tristate buffer (node order: enable,in)
	    TristateBufferTable = new SimLookupTable(Node.VX);
	    TristateBufferTable.Setup(ZTable,SelectTable,XTable,XTable);

	    // and tables
	    AndXTable = new SimLookupTable(Node.VX);
	    AndXTable.Setup(LTable,AndXTable,AndXTable,AndXTable);
	    AndTable = new SimLookupTable(Node.V1);
	    AndTable.Setup(LTable,AndTable,AndXTable,AndXTable);

	    // nand tables
	    NandXTable = new SimLookupTable(Node.VX);
	    NandXTable.Setup(HTable,NandXTable,NandXTable,NandXTable);
	    NandTable = new SimLookupTable(Node.V0);
	    NandTable.Setup(HTable,NandTable,NandXTable,NandXTable);

	    // or tables
	    OrXTable = new SimLookupTable(Node.VX);
	    OrXTable.Setup(OrXTable,HTable,OrXTable,OrXTable);
	    OrTable = new SimLookupTable(Node.V0);
	    OrTable.Setup(OrTable,HTable,OrXTable,OrXTable);

	    // nor tables
	    NorXTable = new SimLookupTable(Node.VX);
	    NorXTable.Setup(NorXTable,LTable,NorXTable,NorXTable);
	    NorTable = new SimLookupTable(Node.V1);
	    NorTable.Setup(NorTable,LTable,NorXTable,NorXTable);

	    // xor tables
	    XorTable = new SimLookupTable(Node.V0);
	    Xor1Table = new SimLookupTable(Node.V1);
	    XorTable.Setup(XorTable,Xor1Table,XTable,XTable);
	    Xor1Table.Setup(Xor1Table,XorTable,XTable,XTable);

	    // 2-input mux table (node order: sel,d0,d1)
	    Mux2Table = new SimLookupTable(Node.VX);
	    Mux2Table.Setup(SelectTable,SelectNextTable,EqualTable,EqualTable);
	}
	return LTable;
    }
}

