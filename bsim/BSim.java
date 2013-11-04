// Copyright (C) 2000-2011 Christopher J. Terman - All Rights Reserved.

package bsim;

import gui.EditBuffer;
import gui.GuiFrame;
import gui.UI;
import gui.POSTStream;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import javax.swing.JOptionPane;

public class BSim extends GuiFrame implements ActionListener, WindowListener {
    public static String version = "1.2.3";
    public static String copyright = "Copyright (C) Christopher J. Terman 2000-2011";

    public static final String ASSEMBLE = "Run UASM";
    public static final String A2FILE = "UASM to .bin file";
    public static final String STOP = "Stop";
    public static final String RUN = "Run";
    public static final String STEP = "Single Step";
    public static final String RESET = "Reset";
    public static final String CACHE = "Cache control";
    public static final String DISPLAY = "Toggle state display";

    GuiFrame plot;
	
    public BSim(String args[]) {
	super(args,"BSim "+version,true,false);
	//setIconImage(GetImageResource("/icons/bsim.gif"));

	// set up edit window
	AddToolButton("/icons/exit.gif",UI.EXIT,this);
	AddToolSeparator();
	AddToolButton("/icons/new.gif",UI.NEW,this);
	AddToolButton("/icons/open.gif",UI.OPEN,this);
	AddToolButton("/icons/close.gif",UI.CLOSE,this);
	AddToolButton("/icons/reload.gif",UI.RELOAD,this);
	AddToolButton("/icons/save.gif",UI.SAVE,this);
	AddToolButton("/icons/saveas.gif",UI.SAVEAS,this);
	AddToolButton("/icons/saveall.gif",UI.SAVEALL,this);
	AddToolSeparator();
	AddToolButton("/icons/assemble.gif",ASSEMBLE,this);
	AddToolButton("/icons/a2file.gif",A2FILE,this);
	AddToolSeparator();
	AddToolButton("/icons/window.gif",UI.PLOTWINDOW,this);
	AddToolButton("/icons/submit.gif",UI.CHECKOFF,this);
	Message("BSim "+version+", "+copyright);

	// set up plot window
	plot = new GuiFrame(null,"Bsim Display Window",false,false);
	//plot.setIconImage(GetImageResource("/icons/bsim.gif"));

	plot.AddToolButton("/icons/bstop.gif",STOP,plot);
	plot.AddToolButton("/icons/breset.gif",RESET,plot);
	plot.AddToolButton("/icons/brun.gif",RUN,plot);
	plot.AddToolButton("/icons/bstep.gif",STEP,plot);
	plot.AddToolSeparator();
	plot.AddToolButton("/icons/display.gif",DISPLAY,plot);
	plot.AddToolSeparator();
	plot.AddToolButton("/icons/cache.gif",CACHE,plot);
	plot.AddToolButton("/icons/window.gif",UI.EDITWINDOW,this);
	plot.AddToolButton("/icons/submit.gif",UI.CHECKOFF,this);
	plot.setLocation(50,25);
	
	SetTab(ReadFiles());
	plot.SetTab(IndexFor(Selectee()));

	// display our handiwork
	setVisible(true);
    }

    // used by stand-alone application
    public static void main(String args[]) {
	new BSim(args);
    }

    public void actionPerformed(ActionEvent event) {
	String what = event.getActionCommand();
	try {
	    if (what.equals(UI.CHECKOFF)) {
		Checkoff();
	    } else if (what.equals(UI.EDITWINDOW)) {
		this.setVisible(true);
		this.toFront();
	    } if (what.equals(UI.PLOTWINDOW)) {
		plot.setVisible(true);
		plot.toFront();
	    } else super.actionPerformed(event);
	}
	catch (Exception e) {
	    Message("Internal error: "+e);

	    String ename = GetParameter("reporterrors");
	    if (ename != null) {
		if (ename.equals("-reporterrors")) e.printStackTrace(System.out);
		else {
		    try {
			PrintWriter out = new PrintWriter(new FileOutputStream(ename,true));
			String time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date());
			String user = System.getProperty("user.name","???");
			String host = System.getProperty("hostname","???");
			out.println("version=\""+version+"\" time=\""+time+"\" user=\""+user+"@"+host+"\" action=\""+what+"\"");
			e.printStackTrace(out);

			// capture buffer contents
			int nviews = tabPane.getTabCount();
			for (int i = 0; i < nviews; i += 1) {
			    EditBuffer n = (EditBuffer)tabPane.getComponentAt(i);
			    n.Capture(out);
			}
			out.close();
		    }
		    catch (Exception ee) {
		    }
		}
	    }
	}
    }

    public void SetPlot(Component v) {
	plot.setVisible(true);
	plot.toFront();
	plot.SetTab(v);
    }

    public void RemoveTab(Component v) {
	int index = tabPane.indexOfComponent(v);
	RemoveTab(index);
	plot.RemoveTab(index);
    }

    public EditBuffer ReadFiles() {
	EditBuffer result = super.ReadFiles();
	plot.SetTab(IndexFor(result));
	return result;
    }

    public Beta GetBeta(Component v) {
	return (Beta)plot.tabPane.getComponentAt(IndexFor(v));
    }

    public Component OpenFile(File file,boolean atEnd) {
	// set default directory for file chooser
	if (lastDirectory == null && file != null)
	    lastDirectory = file.getParent();

	Program n = new Program(this,file);
	AddTab(n.TabName(),n.ToolTip(),n,atEnd ? -1 : 0);

	plot.AddTab(n.TabName(),n.ToolTip(),new Beta(this,plot),atEnd ? -1 : 0);
	return (Component)n;
    }

    public void Checkoff() {
	Program n = (Program)Selectee();
	String chkmsg = n.Checkoff();

	// check with netlist to see if we're ready for checkoff
	if (chkmsg != null) {
	    ShowHTML("Checkoff failure...",
		     chkmsg,
		     JOptionPane.WARNING_MESSAGE);
	    return;
	}

	DoCheckoff(n.checkoffServer);
    }

    public void GeneratePostData(POSTStream o) throws IOException {
	Program n = (Program)Selectee();

	// indicate assignment
	o.writeTag("pcheckoff");
	o.write(n.assignment);

	// send checksum
	o.writeTag("checksum");
	o.write(n.checkoffChecksum.toString());

	// send cycle count
	o.writeTag("cycles");
	o.write(String.valueOf(GetBeta(n).cycles));

	// send program size
	o.writeTag("size");
	o.write(String.valueOf(n.Size()));

	// version
	o.writeTag("version");
	o.write("BSim"+version);

	// any serverInfo collected during execution
	Beta beta = n.beta;
	if (beta != null && beta.serverInfo.size() > 0) {
	    java.util.ArrayList serverInfo = beta.serverInfo;
	    o.writeTag("serverInfo");
	    for (int i = 0; i < serverInfo.size(); i += 1) {
		if (i != 0) o.write(",");
		o.write(serverInfo.get(i).toString());
	    }
	}
    }
}
