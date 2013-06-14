// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

package fsm;

import gui.EditBuffer;
import gui.GuiFrame;
import gui.POSTStream;
import gui.UI;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class Elevator extends GuiFrame implements ActionListener, WindowListener {
    public static String version = "1.0.1";
    public static String copyright = "Copyright (C) Christopher J. Terman 2002";

    String cmdargs[] = null;	// command line args

    String initialFSM =
	"; dumb elevator: just sits on first floor!\n"+
	"\n"+
	";state  F2 F3 F4 | next   U D O C\n"+
	";----------------+---------------\n"+
	"start   -  -  -  | start  0 0 1 0\n";

    public Elevator(String args[]) {
	super(args,"Elevator "+version,true,false);
	setIconImage(GetImageResource("/icons/ant.gif"));

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
	AddToolButton("/icons/submit.gif",UI.CHECKOFF,this);
	Message("Elevator "+version+", "+copyright);

	SetTab(ReadFiles());

	// display our handiwork
	setVisible(true);
    }

    // used by stand-alone application
    public static void main(String args[]) {
	new Elevator(args);
    }

    public void actionPerformed(ActionEvent event) {
	eventThread = Thread.currentThread();
	String what = event.getActionCommand();
	try {
	    if (what.equals(UI.CHECKOFF)) {
		Checkoff();
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

    public void RemoveView(Component v) {
	RemoveTab(v);
    }

    public EditBuffer ReadFiles() {
	ElevatorPanel result = (ElevatorPanel)super.ReadFiles();
	if (result.source == null) {
	    result.setText(initialFSM);
	}
	return result;
    }

    public Component OpenFile(File file,boolean atEnd) {
	// set default directory for file chooser
	if (lastDirectory == null && file != null)
	    lastDirectory = file.getParent();

	ElevatorPanel n = new ElevatorPanel(this,file);
	AddTab(n.TabName(),n.ToolTip(),n,atEnd ? -1 : 0);

	return (Component)n;
    }

    public void Checkoff() {
	ElevatorPanel n = (ElevatorPanel)Selectee();
	int result = n.Solved();

	if (result != 1) {
	    JOptionPane.showMessageDialog(this,"You must get all the lemmings to the ground floor before submitting your FSM.",
					  "Checkoff failure...",
					  JOptionPane.WARNING_MESSAGE);
	    return;
	}

	DoCheckoff("6004.lcs.mit.edu/currentsemester/6004assignment.doit");
    }

    public void GeneratePostData(POSTStream o) throws IOException {
	// indicate assignment
	o.writeTag("checkoff");
	o.write("Lab #3");
    }
}
