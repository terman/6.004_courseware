// Copyright (C) 2001 Christopher J. Terman - All Rights Reserved.

package gui;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

// recode stream using HTTP POST conventions
public class POSTStream extends FilterOutputStream {
    boolean firstTag = true;

    public POSTStream(OutputStream out) {
	super(out);
    }

    private int HexDigit(int b) {
	b &= 0xF;
	return (b > 9) ? (b + 'A' - 10) : b + '0';
    }

    public void write(int b) throws IOException {
	if (b == ' ')
	    out.write('+');
	else if ((b >= '0' && b <= '9') ||
		 (b >= 'A' && b <= 'Z') ||
		 (b >= 'a' && b <= 'z'))
	    out.write(b);
	else {
	    out.write('%');
	    out.write(HexDigit(b >> 4));
	    out.write(HexDigit(b));
	}
    }

    public void write(byte[] buf,int off,int len) throws IOException {
	if (off + len > buf.length) len = buf.length - off;
	for (int i = 0; i < len; i += 1) write(buf[i + off]);
    }

    public void write(byte[] buf) throws IOException {
	write(buf,0,buf.length);
    }

    public void write(char[] buf) throws IOException {
	int l = buf.length;
	for (int i = 0; i < l; i += 1)
	    write(buf[i]);
    }

    public void write(String s) throws IOException {
	int l = s.length();
	for (int i = 0; i < l; i += 1)
	    write(s.charAt(i));
    }

    public void writeTag(String tag) throws IOException {
	if (firstTag) firstTag = false;
	else out.write('&');
	for (int i = 0; i < tag.length(); i += 1)
	    out.write(tag.charAt(i));
	out.write('=');
	out.flush();
    }
}
