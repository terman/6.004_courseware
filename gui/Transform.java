// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

package gui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.io.PrintWriter;
import javax.swing.JComboBox;

public class Transform {
    public static final int NORTH = 0;	// rotations
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;
    public static final int RNORTH = 4;
    public static final int REAST = 5;
    public static final int RSOUTH = 6;
    public static final int RWEST = 7;

    public static final int ROTATE = 0;	// transformations
    public static final int MIRRORX = 1;
    public static final int MIRRORY = 2;

    public static final int RECTANGLE = 0;
    public static final int ROUND_RECT = 1;
    public static final int OVAL = 2;
    public static final int FILLED_RECTANGLE = 3;
    public static final int FILLED_ROUND_RECT = 4;
    public static final int FILLED_OVAL = 5;
    public static final int BOLD_RECTANGLE = 6;

    public static final int TOP_LEFT = 0;	// alignments
    public static final int TOP_CENTER = 1;
    public static final int TOP_RIGHT = 2;
    public static final int CENTER_LEFT = 3;
    public static final int CENTER = 4;
    public static final int CENTER_RIGHT = 5;
    public static final int BOTTOM_LEFT = 6;
    public static final int BOTTOM_CENTER = 7;
    public static final int BOTTOM_RIGHT = 8;
	
    public static final String S_TOP_LEFT = "top-left";
    public static final String S_TOP_CENTER = "top-center";
    public static final String S_TOP_RIGHT = "top-right";
    public static final String S_CENTER_LEFT = "center-left";
    public static final String S_CENTER = "center-center";
    public static final String S_CENTER_RIGHT = "center-right";
    public static final String S_BOTTOM_LEFT = "bottom-left";
    public static final String S_BOTTOM_CENTER = "bottom-center";
    public static final String S_BOTTOM_RIGHT = "bottom-right";

    // result of rotating an alignment [rot*9 + align]
    public static int aOrient[] = {
	0, 1, 2, 3, 4, 5, 6, 7, 8,		// NORTH (identity)
	2, 5, 8, 1, 4, 7, 0, 3, 6, 		// EAST (rot270)
	8, 7, 6, 5, 4, 3, 2, 1, 0,		// SOUTH (rot180)
	6, 3, 0, 7, 4, 1, 8, 5, 3,		// WEST (rot90)
	2, 1, 0, 5, 4, 3, 8, 7, 6,		// RNORTH (negy)
	8, 5, 2, 7, 4, 2, 6, 3, 0, 		// REAST (int-neg)
	6, 7, 8, 3, 4, 5, 0, 1, 2,		// RSOUTH (negx)
	0, 3, 6, 1, 4, 7, 2, 5, 8		// RWEST (int-pos)
    };

    // result of composing two rotations
    private static int orient[] = {
	0, 1, 2, 3, 4, 5, 6, 7,		// NORTH (identity)
	1, 2, 3, 0, 7, 4, 5, 6,		// EAST (rot270)
	2, 3, 0, 1, 6, 7, 4, 5,		// SOUTH (rot180)
	3, 0, 1, 2, 5, 6, 7, 4,		// WEST (rot90)
	4, 5, 6, 7, 0, 1, 2, 3,		// RNORTH (negx)
	5, 6, 7, 4, 3, 0, 1, 2,		// REAST (int-neg)
	6, 7, 4, 5, 2, 3, 0, 1,		// RSOUTH (negy)
	7, 4, 5, 6, 1, 2, 3, 0		// RWEST (int-pos)
    };

    // result of rotating quadrants
    private static int qOrient[] = {
	0, 1, 2, 3,		// NORTH (identity)
	3, 0, 1, 2,		// EAST (rot270)
	2, 3, 0, 1,		// SOUTH (rot180)
	1, 2, 3, 0,		// WEST (rot90)
	1, 0, 3, 2,		// RNORTH (negy)
	0, 3, 2, 1,		// REAST (int-neg)
	3, 2, 1, 0,		// RSOUTH (negx)
	2, 1, 0, 3		// RWEST (int-pos)
    };

    public double orgx;		// offset
    public double orgy;
    public int rot;		// NORTH..RWEST
    public double scale;

    public Transform() {
	orgx = 0;
	orgy = 0;
	rot = NORTH;
	scale = 1.0;
    }

    public Transform(int x,int y,int rotation,double s) {
	orgx = x;
	orgy = y;
	rot = rotation;
	scale = s;
    }

    public Transform(Transform proto) {
	SetParams(proto);
    }

    public void SetParams(Transform proto) {
	orgx = proto.orgx;
	orgy = proto.orgy;
	rot = proto.rot;
	scale = proto.scale;
    }

    public void SetParams(int orgx,int orgy,int rot,double scale) {
	this.orgx = (double)orgx;
	this.orgy = (double)orgy;
	this.rot = rot;
	this.scale = scale;
    }

    double RealTransformX(double oldx,double oldy) {
	double x;
	switch (rot) {
	default:
	case 0:	x = oldx; break;
	case 1:	x = -oldy; break;
	case 2:	x = -oldx; break;
	case 3:	x = oldy; break;
	case 4:	x = -oldx; break;
	case 5:	x = -oldy; break;
	case 6:	x = oldx; break;
	case 7:	x = oldy; break;
	}
	return x*scale + orgx;
    }

    double RealTransformY(double oldx,double oldy) {
	double y;
	switch (rot) {
	default:
	case 0:	y = oldy; break;
	case 1:	y = oldx; break;
	case 2:	y = -oldy; break;
	case 3:	y = -oldx; break;
	case 4:	y = oldy; break;
	case 5:	y = -oldx; break;
	case 6:	y = -oldy; break;
	case 7:	y = oldx; break;
	}
	return y*scale + orgy;
    }

    public int TransformX(int oldx,int oldy) {
	return (int)Math.round(RealTransformX(oldx, oldy));
    }

    public int TransformY(int oldx,int oldy) {
	return (int)Math.round(RealTransformY(oldx, oldy));
    }
	
    public int TransformRot(int r) {
	return orient[r*8 + rot];
    }
	
    public int TransformAlignment(int a) {
	return aOrient[rot*9 + a];
    }

    public int TransformQuadrant(int q) {
	return qOrient[rot*4 + q];
    }

    public double RealInverseTransformX(int oldx, int oldy) {
	double x = (oldx - orgx) / scale;
	double y = (oldy - orgy) / scale;
	switch (rot) {
	default:
	case 0:	x = x; break;
	case 1:	x = y; break;
	case 2:	x = -x; break;
	case 3:	x = -y; break;
	case 4:	x = -x; break;
	case 5:	x = -y; break;
	case 6:	x = x; break;
	case 7:	x = y; break;
	}
	return x;
    }
	
    public double RealInverseTransformY(int oldx, int oldy) {
	double x = (oldx - orgx) / scale;
	double y = (oldy - orgy) / scale;
	switch (rot) {
	default:
	case 0:	y = y; break;
	case 1:	y = -x; break;
	case 2:	y = -y; break;
	case 3:	y = x; break;
	case 4:	y = y; break;
	case 5:	y = -x; break;
	case 6:	y = -y; break;
	case 7:	y = x; break;
	}
	return y;
    }
	
    public int InverseTransformX(int oldx,int oldy) {
	return (int)Math.round(RealInverseTransformX(oldx, oldy));
    }

    public int InverseTransformY(int oldx,int oldy) {
	return (int)Math.round(RealInverseTransformY(oldx, oldy));
    }

    public Rectangle TransformRectangle(Rectangle src) {
	Rectangle r = new Rectangle();
	TransformRectangle(src, r);
	return r;
    }
	
    public void TransformRectangle(Rectangle src,Rectangle dst) {
	// allow src and dst to be the same...
	int x,y,w,h;

	x = TransformX(src.x,src.y);
	y = TransformY(src.x,src.y);
	w = TransformX(src.x+src.width,src.y+src.height) - x;
	h = TransformY(src.x+src.width,src.y+src.height) - y;
	//w = (int)Math.round(RealTransformX(src.width,src.height) - orgx);
	//h = (int)Math.round(RealTransformY(src.width,src.height) - orgy);

	// canonicalize
	if (w < 0) { w = -w; x -= w; }
	if (h < 0) { h = -h; y -= h; }

	dst.x = x;
	dst.y = y;
	dst.width = w;
	dst.height = h;
    }

    public Rectangle InverseTransformRectangle(Rectangle src) {
	Rectangle r = new Rectangle();
	InverseTransformRectangle(src, r);
	return r;
    }
	
    public void InverseTransformRectangle(Rectangle src,Rectangle dst) {
	// allow src and dst to be the same...
	int x,y,w,h;

	x = InverseTransformX(src.x,src.y);
	y = InverseTransformY(src.x,src.y);
	w = InverseTransformX(src.x+src.width,src.y+src.height) - x;
	h = InverseTransformY(src.x+src.width,src.y+src.height) - y;

	// canonicalize
	if (w < 0) { w = -w; x -= w; }
	if (h < 0) { h = -h; y -= h; }

	dst.x = x;
	dst.y = y;
	dst.width = w;
	dst.height = h;
    }

    public void Compose(Transform d) {
	if (d != null) {
	    double x = d.RealTransformX(orgx,orgy);
	    double y = d.RealTransformY(orgx,orgy);
	    orgx = x;
	    orgy = y;
	    rot = d.TransformRot(rot);
	    scale *= d.scale;
	}
    }
	
    public void Debug() {
	System.out.println("orgx="+orgx+" orgyy="+orgy+" rot="+rot+" scale="+scale);
    }

    public void DrawLine(Graphics g,int x1,int y1,int x2,int y2) {
	int nx1 = TransformX(x1,y1);
	int ny1 = TransformY(x1,y1);
	int nx2 = TransformX(x2,y2);
	int ny2 = TransformY(x2,y2);
	g.drawLine(nx1,ny1,nx2,ny2);
    }

    public void DrawGridPoint(Graphics g,int x,int y,int grid,boolean big) {
	int nx = TransformX(x,y);
	int ny = TransformY(x,y);
	int csize = (int)Math.floor((grid*scale)/32);
	if (big) {
	    csize = Math.max(2,csize);
	    g.drawOval(nx-csize,ny-csize,2*csize,2*csize);
	} else {
	    g.drawLine(nx-csize,ny,nx+csize,ny);
	    if (csize > 0) g.drawLine(nx,ny-csize,nx,ny+csize);
	}
    }

    public void DebugLine(int x1,int y1,int x2,int y2) {
	int nx1 = TransformX(x1,y1);
	int ny1 = TransformY(x1,y1);
	int nx2 = TransformX(x2,y2);
	int ny2 = TransformY(x2,y2);
	System.out.println("("+nx1+","+ny1+") to ("+nx2+","+ny2+")");	
    }

    public void DrawRectShape(Graphics g,int shape,int x,int y,int w,int h) {
	int nx = TransformX(x,y);
	int ny = TransformY(x,y);
	int nw = TransformX(x+w,y+h) - nx;
	int nh = TransformY(x+w,y+h) - ny;
	//int nw = (int)Math.round(RealTransformX(w,h) - orgx);
	//int nh = (int)Math.round(RealTransformY(w,h) - orgy);

	// canonicalize
	if (nw < 0) { nw = -nw; nx -= nw; }
	if (nh < 0) { nh = -nh; ny -= nh; }

	//System.out.println("x="+x+" y="+y+" w="+w+" h="+h);
	//System.out.println("nx="+nx+" ny="+ny+" nw="+nw+" nh="+nh);
		
	if (w == h) nh = nw;

	switch (shape) {
	case FILLED_RECTANGLE:
	    g.fillRect(nx,ny,nw+1,nh+1);
	    break;
	case RECTANGLE:
	    g.drawRect(nx,ny,nw,nh);
	    break;
	case BOLD_RECTANGLE:
	    g.drawRect(nx,ny,nw,nh);
	    g.drawRect(nx+1,ny+1,nw-2,nh-2);
	    break;
	case FILLED_ROUND_RECT:
	    g.fillRoundRect(nx,ny,nw+1,nh+1,20,20);
	    break;
	case ROUND_RECT:
	    g.drawRoundRect(nx,ny,nw,nh,20,20);
	    break;
	case FILLED_OVAL:
	    g.fillOval(nx,ny,nw+1,nh+1);
	    break;
	case OVAL:
	    g.drawOval(nx,ny,nw,nh);
	    break;
	}
    }

    public void DebugRectShape(int shape,int x,int y,int w,int h) {
	int nx = TransformX(x,y);
	int ny = TransformY(x,y);
	int nw = TransformX(x+w,y+h) - nx;
	int nh = TransformY(x+w,y+h) - ny;
	//int nw = (int)Math.round(RealTransformX(w,h) - orgx);
	//int nh = (int)Math.round(RealTransformY(w,h) - orgy);

	// canonicalize
	if (nw < 0) { nw = -nw; nx -= nw; }
	if (nh < 0) { nh = -nh; ny -= nh; }
		
	System.out.println("("+nx+","+ny+") w="+nw+", h="+ny);
    }

    // draw a quarter circle:
    //   which==0 => 0 to 90
    //   which==1 => 90 to 180
    //   which==2 => 180 to 270
    //   which==3 => 270 to 0
    public void DrawQuadrant(Graphics g,int which,int x,int y,int w,int h) {
	int nx = TransformX(x,y);
	int ny = TransformY(x,y);
	int nw = TransformX(x+w,y+h) - nx;
	int nh = TransformY(x+w,y+h) - ny;
	//int nw = (int)Math.round(RealTransformX(w,h) - orgx);
	//int nh = (int)Math.round(RealTransformY(w,h) - orgy);

	// canonicalize
	if (nw < 0) { nw = -nw; nx -= nw; }
	if (nh < 0) { nh = -nh; ny -= nh; }

	int w2 = nw+nw;
	int h2 = nh+nh;
	switch (qOrient[rot*4 +which]) {
	case 0:	g.drawArc(nx-nw,ny,w2,h2,0,90); break;
	case 1:	g.drawArc(nx,ny,w2,h2,90,90); break;
	case 2:	g.drawArc(nx,ny-nh,w2,h2,180,90); break;
	case 3:	g.drawArc(nx-nw,ny-nh,w2,h2,270,90); break;
	}
    }
	
    public static Dimension TextSize(FontMetrics fm,String txt) {
	if (txt == null) return new Dimension(0,0);
	else {
	    int len = txt.length();
	    char chars[] = new char[len];
	    txt.getChars(0,len,chars,0);
	    return TextSize(fm,chars);
	}
    }

    public static Dimension TextSize(FontMetrics fm,char[] chars) {
	Dimension d = new Dimension(0,0);
	int lineHeight = fm.getAscent() + fm.getDescent();

	if (chars != null) {
	    int len = chars.length;
	    int position = 0;
	    while (position < len) {
		int offset = position;
		while (position < len && chars[position] != '\n')
		    position += 1;
		d.width = Math.max(d.width,
				   fm.charsWidth(chars,offset,position-offset));
		d.height += lineHeight;
		position += 1;
	    }
	}

	return d;
    }

    public void DrawText(Graphics g,int x,int y,String txt,String font,int pointsize,int alignment) {
	Font f;
	FontMetrics fm;

	if (txt == null) return;

	pointsize = (int)(scale * pointsize);
	if (pointsize < 1) pointsize = 1;
	f = Font.decode(font+pointsize);
	fm = g.getFontMetrics(f);

	// compute transformed position and alignment
	int len = txt.length();
	char chars[] = new char[len];
	txt.getChars(0,len,chars,0);
	
	Dimension d = TextSize(fm,chars);
	int nw = d.width;
	int nh = d.height;
	int nx = TransformX(x,y);
	int ny = TransformY(x,y);
	int nalign = TransformAlignment(alignment);

	// convert position to coords NW corner using alignment
	int w2 = nw >> 1;
	int h2 = nh >> 1;
	switch (nalign) {
	case 1:	nx -= w2; break;
	case 2:	nx -= nw; break;
	case 3:	ny -= h2; break;
	case 4:	nx -= w2; ny -= h2; break;
	case 5:	nx -= nw; ny -= h2; break;
	case 6:	ny -= nh; break;
	case 7:	nx -= w2; ny -= nh; break;
	case 8:	nx -= nw; ny -= nh; break;
	}

	// finally draw the string
	g.setFont(f);
	int position = 0;
	int lineHeight = fm.getAscent() + fm.getDescent();
	ny += fm.getAscent();
	while (position < len) {
	    int offset = position;
	    while (position < len && chars[position] != '\n')
		position += 1;
	    g.drawChars(chars,offset,position-offset,nx,ny);
	    ny += lineHeight;
	    position += 1;
	}
    }

    public String toString() {
	return "<"+orgx+","+orgy+","+rot+","+scale+">";
    }

    public void Print(PrintWriter out) {
	UI.PrintNumber(out,(int)orgx);
	UI.PrintNumber(out,(int)orgy);
	UI.PrintNumber(out,rot);
    }

    static public JComboBox ChooseAlignment(int alignment) {
	JComboBox align = new JComboBox();
	align.addItem(Transform.S_TOP_LEFT);
	align.addItem(Transform.S_TOP_CENTER);
	align.addItem(Transform.S_TOP_RIGHT);
	align.addItem(Transform.S_CENTER_LEFT);
	align.addItem(Transform.S_CENTER);
	align.addItem(Transform.S_CENTER_RIGHT);
	align.addItem(Transform.S_BOTTOM_LEFT);
	align.addItem(Transform.S_BOTTOM_CENTER);
	align.addItem(Transform.S_BOTTOM_RIGHT);
	align.setSelectedIndex(alignment);
	return align;
    }
}
