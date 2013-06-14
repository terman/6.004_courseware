// Copyright (C) 2003 Christopher J. Terman - All Rights Reserved.

package tmsim;

public class TMAction {
    public static int UP = -1;
    public static int DOWN = 1;

    public String nextState;
    public String writeSymbol;
    public int direction;
    public int lineNumber;

    public TMAction(String state,String symbol,int dir,int line) {
	nextState = state;
	writeSymbol = symbol;
	direction = dir;
	lineNumber = line;
    }
}
