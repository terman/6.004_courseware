// Copyright (C) 1999-2011 Christopher J. Terman - All Rights Reserved.

package netlist;

class Number extends Token {
    public double value;			// value

    public Number(double v,Netlist netlist,int s,int e) {
	super(netlist,s,e);
	value = v;
    }

    // see if we can interpret line[lineOffset...] as a number
    static public Number Parse(StringBuffer line,Netlist netlist,int lineOffset) {
	int lineLength = line.length();
	double multiplier = 1;		// multiply final number by this
	double value = 0;
	int ch;
	int start;

	// skip over blanks
	while (lineOffset < lineLength && line.charAt(lineOffset) <= ' ')
	    lineOffset += 1;
	start = lineOffset;

	// see if we can parse a signed number
	if (lineOffset < lineLength) {
	    if ((ch = line.charAt(lineOffset)) == '+') lineOffset += 1;
	    else if (ch == '-') { multiplier = -1; lineOffset +=1; }
	}
	int radix = 10;
	if (lineOffset < lineLength && ((ch = line.charAt(lineOffset))=='0')) {
	    lineOffset += 1;
	    if (lineOffset < lineLength) {
		ch = line.charAt(lineOffset);
		if (ch == 'x') { lineOffset +=1; radix = 16; }
		else if (ch == 'b') { lineOffset +=1; radix = 2; }
		else radix = 8;
	    }
	}
	while (lineOffset < lineLength) {
	    ch = line.charAt(lineOffset);
	    if (radix == 2 && ch == '_') {
		// skip over underscores in binary numbers
		lineOffset += 1;
		continue;
	    }
	    else if (radix <= 10) {
		ch -= '0';
		if (ch < 0 || ch >= radix) break;
	    } else {
		ch -= '0';
		if (ch < 0) break;
		if (ch > 9) {
		    ch -= 'A' - '0' - 10;
		    if (ch < 10) break;
		    if (ch >= radix) {
			ch -= 'a' - 'A';
			if (ch < 10 || ch >= radix) break;
		    }
		}
	    }
	    value = radix*value + ch;
	    lineOffset += 1;
	}

	// fractional part?
	if (lineOffset < lineLength && line.charAt(lineOffset)=='.') {
	    lineOffset += 1;
	    while (lineOffset < lineLength && 
		   ((ch = line.charAt(lineOffset))>='0' && ch<='9')) {
		value = 10*value + (ch - '0');
		multiplier /= 10;
		lineOffset += 1;
	    }
	}

	// see if we've found anything
	if (lineOffset == start) return null;

	// now see if there's an exponent specified
	value *= multiplier;
	if (lineOffset < lineLength) {
	    switch(Character.toLowerCase(line.charAt(lineOffset))) {
	    case 'e':
		int exponent = 0;
		boolean expNegative = false;
		lineOffset += 1;
		if (lineOffset < lineLength) {
		    if ((ch = line.charAt(lineOffset)) == '+') lineOffset += 1;
		    else if (ch == '-') { expNegative = true; lineOffset +=1; }
		}
		while (lineOffset < lineLength && 
		       ((ch = line.charAt(lineOffset))>='0' && ch<='9')) {
		    exponent = 10*exponent + (ch - '0');
		    lineOffset += 1;
		}
		while (exponent-- > 0) value *= (expNegative ? .1 : 10.0);
		break;
	    case 't':	value *= 1e12; break;
	    case 'g':	value *= 1e9; break;
	    case 'k':	value *= 1e3; break;
	    case 'u':	value *= 1e-6; break;
	    case 'n':	value *= 1e-9; break;
	    case 'p':	value *= 1e-12; break;
	    case 'f':	value *= 1e-15; break;
	    case 'm':
		if (lineOffset+2 < lineLength) {
		    if (Character.toLowerCase(line.charAt(lineOffset+1))=='e' &&
			Character.toLowerCase(line.charAt(lineOffset+2))=='g') {
			value *= 1e6;
			break;
		    } else if (Character.toLowerCase(line.charAt(lineOffset+1))=='i' &&
			       Character.toLowerCase(line.charAt(lineOffset+2))=='l') {
			value *= 25.4e-6;
			break;
		    }
		}
		value *= 1e-3;
		break;
	    }
	}

	// skip any remaining suffix
	while (lineOffset < lineLength && Identifier.IdChar(line.charAt(lineOffset)))
	    lineOffset += 1;
	return new Number(value,netlist,start,lineOffset);
    }
}

