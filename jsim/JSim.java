// Copyright (C) 1999-2011 Christopher J. Terman - All Rights Reserved.

package jsim;


import gui.EditBuffer;
import gui.GuiFrame;
import gui.POSTStream;
import gui.UI;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import netlist.Netlist;
import plot.Plot;

public class JSim extends GuiFrame implements ActionListener, WindowListener {
    public static String version = "2.1.8";
    public static String copyright = "Copyright (C) Christopher J. Terman 1997-2013";

    String cmdargs[] = null;	// command line args
    GuiFrame plot;
    JButton chan1,chan2,chan4,chan8,chan16;

    public JSim(String args[]) {
	super(args,"JSim "+version,true,true);
	//setIconImage(GetImageResource("/icons/jsim.gif"));

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
	AddToolButton("/icons/stop.gif",UI.STOP,this);
	AddToolButton("/icons/simulate.gif",UI.SIMULATE,this);
	AddToolButton("/icons/fastsim.gif",UI.FASTSIMULATE,this);
	AddToolButton("/icons/gatesim.gif",UI.GATESIMULATE,this);
	AddToolButton("/icons/timinganalysis.gif",UI.TIMINGANALYSIS,this);
	AddToolButton("/icons/flatten.gif",UI.FLATTEN,this);
	AddToolSeparator();
	AddToolButton("/icons/window.gif",UI.PLOTWINDOW,this);
	AddToolButton("/icons/submit.gif",UI.CHECKOFF,this);
	Message("JSim "+version+", "+copyright);

	// set up plot window
	plot = new GuiFrame(null,"JSim Plot Window",false,false);
	//plot.setIconImage(GetImageResource("/icons/jsim.gif"));

	chan1 = plot.AddToolButton("/icons/chan1.gif",UI.CHAN1,plot);
	chan2 = plot.AddToolButton("/icons/chan2.gif",UI.CHAN2,plot);
	chan4 = plot.AddToolButton("/icons/chan4.gif",UI.CHAN4,plot);
	chan8 = plot.AddToolButton("/icons/chan8.gif",UI.CHAN8,plot);
	chan16 = plot.AddToolButton("/icons/chan16.gif",UI.CHAN16,plot);

	plot.AddToolSeparator();
	plot.AddToolButton("/icons/print.gif",UI.PRINT,plot);
	plot.AddToolButton("/icons/reload.gif",UI.RELOAD,this);
	plot.AddToolSeparator();
	plot.AddToolButton("/icons/stop.gif",UI.STOP,this);
	plot.AddToolButton("/icons/simulate.gif",UI.SIMULATE,this);
	plot.AddToolButton("/icons/fastsim.gif",UI.FASTSIMULATE,this);
	plot.AddToolButton("/icons/gatesim.gif",UI.GATESIMULATE,this);
	plot.AddToolSeparator();
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
	new JSim(args);
    }

    public void actionPerformed(ActionEvent event) {
	eventThread = Thread.currentThread();
	String what = event.getActionCommand();
	try {
	    if (what.equals(UI.CHECKOFF)) {
		Checkoff();
	    } else if (what.equals(UI.EDITWINDOW)) {
		this.setVisible(true);
		this.toFront();
	    } else if (what.equals(UI.PLOTWINDOW)) {
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
			    Netlist n = (Netlist)tabPane.getComponentAt(i);
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

    public void SetChannels(int chan) {
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

    public Component OpenFile(File file,boolean atEnd) {
	// set default directory for file chooser
	if (lastDirectory == null && file != null)
	    lastDirectory = file.getParent();

	Netlist n = new Netlist(this,file);
	AddTab(n.TabName(),n.ToolTip(),n,atEnd ? -1 : 0);

	plot.AddTab(n.TabName(),n.ToolTip(),new Plot(this,plot,n),atEnd ? -1 : 0);
	return (Component)n;
    }

    public void Checkoff() {
	Netlist n = (Netlist)Selectee();
	String chkmsg = n.Checkoff();

	// check with netlist to see if we're ready for checkoff
	if (chkmsg != null) {
	    ShowHTML("Checkoff failure...",
		     chkmsg,
		     JOptionPane.WARNING_MESSAGE);
	    return;
	}

	if (n.hasCheckoffServer()) DoCheckoff(n.checkoffServer);
	else {
	    ShowHTML("Verification results...",
		     "<font size=5>Verification succeeded</font><p>The simulation results for \""+n.assignment+"\" match the verification values.",
		     JOptionPane.INFORMATION_MESSAGE);
	}
    }

    // JSim modules not to include in data gathering
    public String[] ignore = {
	"stdcell.jsim",
	"nominal.jsim",
	"8clocks.jsim",
	"lab1checkoff.jsim",
	"lab2checkoff.jsim",
	"lab3_test_adder.jsim",
	"lab3_test_bool.jsim",
	"lab3_test_cmp.jsim",
	"lab3_test_mult.jsim",
	"lab3_test_shift.jsim",
	"lab3checkoff_10.jsim",
	"lab3checkoff_6.jsim",
	"lab6basicblock.jsim",
	"lab6checkoff.jsim",
	"lab6ctl.jsim",
	"lab6pc.jsim",
	"lab6regfile.jsim",
	"projcheckoff.jsim"
    };

    public void GeneratePostData(POSTStream o) throws IOException {
	Netlist n = (Netlist)Selectee();

	// send checkoff type
	o.writeTag(n.checkoffCommand);
	o.write(n.assignment);

	// send checksum
	o.writeTag("checksum");
	o.write(Integer.toString(n.checkoffChecksum));

	// send min observed setup
	String minSetup = n.MinObservedSetup();
	if (minSetup != null) {
	    o.writeTag("minsetup");
	    o.write(minSetup);
	}

	// time
	o.writeTag("time");
	o.write(String.valueOf(n.NetworkTime()));

	// send circuit size
	o.writeTag("size");
	o.write(String.valueOf(n.NetworkSize()));

	// send figure-of-merit
	o.writeTag("figure_of_merit");
	double merit = 1.0e-10/(n.NetworkTime()*n.NetworkSize());
	o.write(String.valueOf(merit));

	// version
	o.writeTag("version");
	o.write("JSim"+version);
    }
}
