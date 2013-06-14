// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package gui;

import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;

// all the parameters for the user interface elements.  Group them
// here to maintain consistency and (someday) to help with
// internationalization...
public class UI {
    // color choices
    public static final Color JADECOLOR = new Color(160,208,160);
    public static final Color BGCOLOR = Color.lightGray;
    public static final Color LIGHTBGCOLOR = new Color(230,230,230);
    public static final Color DARKBGCOLOR = Color.gray;
    public static final Color EDITBGCOLOR = Color.white;
    public static final Color GRIDCOLOR = new Color(160,160,160);
    public static final Color SELECTCOLOR = new Color(128,192,128);

    // two flavors of plot colors: regular and screen-shot
    public static final Color PGRIDCOLOR1 = new Color(64,64,64);
    public static final Color PBGCOLOR1 = Color.black;
    public static final Color PSCALECOLOR1 = Color.yellow;
    public static final Color PSCALE2COLOR1 = Color.white;

    public static final Color PGRIDCOLOR2 = new Color(220,220,220);
    public static final Color PBGCOLOR2 = Color.white;
    public static final Color PSCALECOLOR2 = Color.black;
    public static final Color PSCALE2COLOR2 = Color.magenta;

    public static Color PGridColor = PGRIDCOLOR1;
    public static Color PBGColor = PBGCOLOR1;
    public static Color PScaleColor = PSCALECOLOR1;
    public static Color PScale2Color = PSCALE2COLOR1;

    // action strings
    public static final String CHAN1 = "Display 1 plot channel";
    public static final String CHAN2 = "Display 2 plot channels";
    public static final String CHAN4 = "Display 4 plot channels";
    public static final String CHAN8 = "Display 8 plot channels";
    public static final String CHAN16 = "Display 16 plot channels";
    public static final String CHECKOFF = "Complete checkoff";
    public static final String CLOSE = "Close file";
    public static final String EXIT = "Exit";
    public static final String FASTSIMULATE = "Fast Transient Analysis";
    public static final String FLATTEN = "Create Flat Netlist";
    public static final String GATESIMULATE = "Gate-level Simulation";
    public static final String NEW = "New File";
    public static final String OPEN = "Open File";
    public static final String PLOT = "Plot";
    public static final String PRINT = "Print";
    public static final String RELOAD = "Reload Files";
    public static final String SAVE = "Save File";
    public static final String SAVEALL = "Save All Files";
    public static final String SAVEBUFFERS = "Save Buffers";
    public static final String SAVEAS = "Save As";
    public static final String SIMULATE = "Device-level Simulation";
    public static final String STOP = "Stop Simulation";
    public static final String TIMINGANALYSIS = "Gate-level Timing Analysis";
    public static final String EDITWINDOW = "Switch to edit window";
    public static final String PLOTWINDOW = "Switch to plot window";

    private UI() { }	// you can't make one of these!

    // read a line from the input stream and break it into a separate
    // token for each whitespace-delimited word on the line.  Quoted
    // strings are treated as a single word and may contained escaped
    // characters and unicodes.
    public static int ReadLine(InputStream in,ArrayList argv,StringBuffer buffer) throws IOException {
	int argc = 0;
	argv.clear();

	// read the next line into the buffer
	int len = 0;
	buffer.setLength(0);	// start with a clean slate
	while (true) {
	    int ch;
	    try { ch = in.read(); }
	    catch (IOException e) {
		System.out.println("IO exception reading library file: "+e);
		throw e;
	    }
	    if (ch < 0) {
		if (len == 0) return -1;
		else break;
	    } else if ((ch == '\r' || ch == '\n' ) && len > 0) break;
	    else {
		buffer.append((char)ch);
		len += 1;
	    }
	}

	// now break it into a vector of strings
	argc = 0;
	int i = 0;
	String line = buffer.toString();
	while (true) {
	    // skip over white space
	    while (i<len && line.charAt(i)<=' ') i += 1;
	    if (i == len) break;

	    // check for start of comment
	    if (line.charAt(i) == ';') break;

	    // check for quote-delimited token
	    if (line.charAt(i) == '"') {
		i += 1;
		int start = i;
		int escapes = 0;
		while (i < len) {
		    if (line.charAt(i) == '"') break;
		    else if (line.charAt(i) == '\\') {
			escapes += 1;
			i += 1;
			//check for unicode escape
			if (line.charAt(i) == 'u') { escapes += 4; i += 4; }
		    }
		    i += 1;
		}
		// if we've found some escaped characters, we'll need to process
		// them specially, otherwise just let String do the work
		if (escapes > 0) {
		    StringBuffer temp = new StringBuffer(i-start-escapes);
		    while (start < i) {
			// process escapes
			if (line.charAt(start) == '\\') {
			    start += 1;
			    // check for special escapes
			    if (line.charAt(start) == 'n') temp.append('\n');
			    else if (line.charAt(start) == 'u') {
				// unicode
				int unicode = 0;
				for (int k = 0; k < 4; k += 1) {
				    start += 1;
				    if (start == i) break;
				    int uch = Character.digit(line.charAt(start),16);
				    if (uch >= 0) unicode = unicode*16 + uch;
				}
				temp.append((char)unicode);
			    } else temp.append(line.charAt(start));
			} else temp.append(line.charAt(start));
			start += 1;
		    }
		    argv.add(temp.toString());
		}
		else argv.add(line.substring(start,i));
		if (i < len) i += 1;	// skip final quote
	    } else {
		int start = i;
		// just look for next whitespace character
		while (i<len && line.charAt(i)>' ') i += 1;
		argv.add(line.substring(start,i));
	    }
	    argc += 1;
	}

	return argc;
    }

    public static int ReadInt(Object o) {
	try {
	    return Integer.parseInt((String) o);
	}
	catch (NumberFormatException e) {
	    return 0;
	}
    }

    // get rows and columns in a block of text
    public static Dimension RowsColumns(String v) {
	int lineCount = 0;
	int maxLineLength = 0;
	int position = 0;
	while (position < v.length()) {
	    int nl = v.indexOf('\n',position);
	    if (nl == -1) {
		maxLineLength = Math.max(maxLineLength,v.length()-position);
		break;
	    }
	    maxLineLength = Math.max(maxLineLength,nl-position);
	    lineCount += 1;
	    position = nl + 1;
	}
	return new Dimension(maxLineLength,lineCount+1);
    }

    // use engineering suffixes if possible
    public static String EngineeringNotation(double n,int nplaces) {
	if (n == 0) return("0");

	double sign = n < 0 ? -1 : 1;
	double log10 = Math.log(sign*n)/Math.log(10);
	double exp = Math.floor(log10/3);
	double mantissa = sign*Math.pow(10,log10 - 3*exp);

	// keep specified number of places following decimal point
	String mstring = Double.toString(mantissa + 0.5*Math.pow(10,-nplaces));
	int mlen = mstring.length();
	int endindex = mstring.indexOf('.');
	if (endindex != -1) {
	    if (nplaces > 0) {
		endindex += nplaces + 1;
		if (endindex > mlen) endindex = mlen;
		while (mstring.charAt(endindex-1) == '0') endindex -= 1;
		if (mstring.charAt(endindex-1) == '.') endindex -= 1;
	    }
	    if (endindex < mlen)
		mstring = mstring.substring(0,endindex);
	}

	switch((int)exp) {
	case -5:	return mstring+"f";
	case -4:	return mstring+"p";
	case -3:	return mstring+"n";
	case -2:	return mstring+"u";
	case -1:	return mstring+"m";
	case 0:	return mstring;
	case 1:	return mstring+"K";
	case 2:	return mstring+"Meg";
	case 3:	return mstring+"G";
	}

	// don't have a good suffix, so just print the number
	return Double.toString(n);
    }

    public static Color ParseColor(String cname) {
	if (cname == null) return null;

	if (cname.equalsIgnoreCase("black")) return Color.black;
	else if (cname.equalsIgnoreCase("blue")) return Color.blue;
	else if (cname.equalsIgnoreCase("cyan")) return Color.cyan;
	else if (cname.equalsIgnoreCase("darkGray")) return Color.darkGray;
	else if (cname.equalsIgnoreCase("gray")) return Color.gray;
	else if (cname.equalsIgnoreCase("green")) return Color.green;
	else if (cname.equalsIgnoreCase("lightGray")) return Color.lightGray;
	else if (cname.equalsIgnoreCase("magenta")) return Color.magenta;
	else if (cname.equalsIgnoreCase("orange")) return Color.orange;
	else if (cname.equalsIgnoreCase("pink")) return Color.pink;
	else if (cname.equalsIgnoreCase("red")) return Color.red;
	//else if (cname.equalsIgnoreCase("white")) return Color.white;
	else if (cname.equalsIgnoreCase("yellow")) return Color.yellow;

	//try { return Color.decode(cname); }
	//catch (NumberFormatException e) { return null; }
	return null;
    }

    // parse signed integer (use "C" style prefix), return 0 if parse error
    static public int ParseInteger(String line) {
	int lineLength = line.length();
	int lineOffset = 0;
	int multiplier = 1;		// multiply final number by this
	int value = 0;
	int radix = 10;
	int ch;

	// skip over blanks
	while (lineOffset < lineLength && line.charAt(lineOffset) <= ' ')
	    lineOffset += 1;

	// see if we can parse a signed number
	if (lineOffset < lineLength) {
	    if ((ch = line.charAt(lineOffset)) == '+') lineOffset += 1;
	    else if (ch == '-') { multiplier = -1; lineOffset +=1; }
	}

	// determine radix
	if (lineOffset < lineLength) {
	    if ((ch = line.charAt(lineOffset)) == '0') {
		lineOffset += 1;
		if (lineOffset < lineLength &&
		    ((ch = line.charAt(lineOffset)) == 'x' || ch == 'X')) {
		    lineOffset +=1;
		    radix = 16;
		} else radix = 8;
	    }
	}

	// read number
	while (lineOffset < lineLength) {
	    ch = line.charAt(lineOffset++);
	    if (ch >= '0' && ch < '9') {
		ch -= '0';
		if (ch >= radix) break;
	    } else if (ch >= 'A' && ch <= 'F') {
		if (radix != 16) break;
		ch -= 'A' + 10;
	    } else if (ch >= 'a' && ch <= 'f') {
		if (radix != 16) break;
		ch -= 'a' + 10;
	    } else break;
	    value = radix*value + ch;
	}

	return multiplier*value;
    }

    // see if we can interpret line[lineOffset...] as a number
    static public double ParseEngineeringNotation(String line) {
	int lineLength = line.length();
	int lineOffset = 0;
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
	while (lineOffset < lineLength && 
	       ((ch = line.charAt(lineOffset))>='0' && ch<='9')) {
	    value = 10*value + (ch - '0');
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
	if (lineOffset == start) return Double.NaN;

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

	return value;
    }

    public static void PrintNumber(PrintWriter out,int v) {
	out.print(' ');
	out.print(v);
    }

    // print string with appropriate quotes/escapes
    public static void Print(PrintWriter out,String v) {
	int vlen = v.length();
	boolean quoted = (vlen == 0);

	for (int i = 0; i < vlen; i += 1) {
	    char ch = v.charAt(i);
	    if (ch <= ' ' || ch > '~') { quoted = true; break; }
	}

	if (quoted) {
	    out.print('"');
	    for (int i = 0; i < vlen; i += 1) {
		char ch = v.charAt(i);
		if (ch == '"' || ch == '\\') { out.print('\\'); out.print(ch); }
		else if (ch >= ' ' && ch <= '~') out.print(ch);
		else if (ch == '\n') out.print("\\n");
		else {
		    out.print("\\u");
		    // urk
		    String hex = Integer.toHexString(ch);
		    int lhex = hex.length();
		    for (int k = 4; k > 0; k -= 1) 
			{
			    if (lhex - k < 0) out.print('0');
			    else out.print(hex.charAt(lhex - k));
			}
		}
	    }
	    out.print('"');
	} else out.print(v);
    }

    private static void ExpandIterators(ArrayList names,String prefix,String n) {
	if (n == null) {
	    if (prefix != null && prefix.length()>0)
		names.add(prefix);
	    return;
	}

	// see  bracketed index, return if we've got them all
	int leftBracket = n.indexOf('[');
	int rightBracket = n.indexOf(']');

	if (leftBracket == -1 || rightBracket-1 <= leftBracket) {
	    names.add(prefix+n);
	    return;
	} else if (leftBracket > 0)
	    prefix += n.substring(0,leftBracket);

	String suffix;
	if (rightBracket + 1 >= n.length()) suffix = null;
	else suffix = n.substring(rightBracket+1);

	// parse index
	int colon = n.indexOf(':',leftBracket);
	if (colon == -1 || colon >= rightBracket-1) {
	    // no range so we only have one name to build
	    prefix += n.substring(leftBracket+1,rightBracket);
	    ExpandIterators(names,prefix,suffix);
	    return;
	}

	// parse stride (if present)
	int colon2 = n.indexOf(':',colon+1);
	if (colon2 == -1 || colon2 >= rightBracket-1) colon2 = rightBracket;

	try {
	    int start = Integer.parseInt(n.substring(leftBracket+1,colon));
	    int end = Integer.parseInt(n.substring(colon+1,colon2));
	    int stride = 1;
	    if (colon2 != rightBracket)
		stride = Integer.parseInt(n.substring(colon2+1,rightBracket));
	    int index = start;
	    while (true) {
		String name = prefix + Integer.toString(index);
		if (suffix == null) names.add(name);
		else ExpandIterators(names,name,suffix);
		
		if (index == end) break;
		else if (index > end) {
		    index -= stride;
		    if (index < end) break;
		} else {
		    index += stride;
		    if (index > end) break;
		}
	    }
	}
	catch (NumberFormatException e) {
	    ExpandIterators(names,prefix+n.substring(leftBracket,rightBracket+1),suffix);
	}
    }

    // expand comma-separated list of node names w/ iterators into vector
    public static ArrayList ExpandNodeName(String n) {
	ArrayList names = new ArrayList();
	int nlen = n.length();
	int index = 0;

	while (index < nlen) {
	    int end = n.indexOf(',',index);
	    if (end == -1) end = nlen;

	    String next = (index > 0 || end < nlen) ? n.substring(index,end) : n;

	    int dup = next.indexOf('#');
	    if (dup == -1) ExpandIterators(names,"",next);
	    else {
		try {
		    int count = Integer.parseInt(next.substring(dup+1,end));
		    if (count > 0) {
			// found out what to duplicate
			ExpandIterators(names,"",next.substring(0,dup));
			int nnames = names.size();
			// now perform the duplication (remember first
			// copy is already in the names vector!)
			while (count-- > 1) {
			    for (int i = 0; i < nnames; i += 1)
				names.add(names.get(i));
			}
		    }
		}
		catch (NumberFormatException e) {
		    ExpandIterators(names,"",next);
		}
	    }

	    index = end + 1;
	}

	return names;
    }

    public static String CanonicalFileName(URL u) {
	String name = u.getFile();
	if (u.getProtocol().equals("file")) {
	    // attempt to cannonicalize pathnames across systems
	    name = name.replace(File.separatorChar,'/');
	    name = name.replace('\\','/');
	}

	// look for "//X:" and change to "X:"
	if (name.length() >= 4 && name.charAt(0)=='/' &&
	    name.charAt(1)=='/' && name.charAt(3) == ':')
	    name = name.substring(2);

	// change "/dir/../" to "/"
	int index = 0;
	while (true) {
	    index = name.indexOf('.',index) + 1;
	    if (index == 0 || index+1 >= name.length()) break;
	    if (index < 3 ||
		name.charAt(index) != '.' ||
		name.charAt(index+1) != '/' ||
		name.charAt(index-2) != '/')
		continue;
	    int i = name.lastIndexOf('/',index-3);
	    if (i != -1) {
		name = name.substring(0,i) + name.substring(index+1);
		index = i;
	    }
	}

	return name;
    }
}
