// Copyright (C) 2003-2011 Christopher J. Terman - All Rights Reserved.

package tmsim;

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

public class TMSim extends GuiFrame implements ActionListener, WindowListener {
    public static String version = "1.2.2";
    public static String copyright = "Copyright (C) Christopher J. Terman 2003-2013";

    public static String DUMMY_TM = 
	"// 5-state busy beaver Turing Machine example\n\n"+
	"// See how many 1's we can write on a blank tape using\n"+
	"// only a five-state Turing Machine\n\n"+
	"states A B C D E  // list of state names, first is starting state\n"+
	"symbols 1         // list of symbols (- is blank cell)\n"+
	"tape test -      // initial tape contents, blank in this case\n\n"+
	"// Uhing's 5-state machine: writes 1915 1s in 2,133,492 steps before halting.\n"+
	"// Note that the best known 5-state writes 4098 1s...\n"+
	"// See http://grail.cba.csuohio.edu/~somos/busy.html\n\n"+
	"// specify transistions: action state symbol state' write move\n"+
	"//    state = the current state of the FSM\n"+
        "//    symbol = the symbol read from the current cell\n"+
	"//    state' = state on the next cycle \n"+
	"//    write = symbol to be written into the current cell\n"+
	"//    move = tape movement (\"l\" = left, \"r\" = right, \"-\"=stay put)\n"+
	"//    old  R     new  W M\n"+
	"action  A   -      B  1 r\n"+
	"action  A   1      C  1 l\n"+
	"action  B   -      A  - l\n"+
	"action  B   1      D  - l\n"+
	"action  C   -      A  1 l\n"+
	"action  C   1 *halt*  1 l\n"+
	"action  D   -      B  1 l\n"+
	"action  D   1      E  1 r\n"+
	"action  E   -      D  - r\n"+
	"action  E   1      B  - r\n";

    String cmdargs[] = null;	// command line args

    public TMSim(String args[]) {
	super(args,"TMSim "+version,true,false);
	//setIconImage(GetImageResource("/icons/ant.gif"));

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
	Message("TMSim "+version+", "+copyright);

	SetTab(ReadFiles());

	// display our handiwork
	setVisible(true);
    }

    // used by stand-alone application
    public static void main(String args[]) {
	new TMSim(args);
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
	TMPanel result = (TMPanel)super.ReadFiles();
	if (result.source == null) {
	    result.setText(DUMMY_TM);
	}
	result.Reset();
	return result;
    }

    public Component OpenFile(File file,boolean atEnd) {
	// set default directory for file chooser
	if (lastDirectory == null && file != null)
	    lastDirectory = file.getParent();

	TMPanel n = new TMPanel(this,file);
	AddTab(n.TabName(),n.ToolTip(),n,atEnd ? -1 : 0);

	return (Component)n;
    }

    public void Checkoff() {
	TMPanel n = (TMPanel)Selectee();

	if (n.checkoffChecksum == 123456) {
	    System.out.println("checksum = "+n.tapeChecksum);
	    return;
	}

	if (!n.Solved()) {
	    JOptionPane.showMessageDialog(this,"Your TM must be run successfully on all the test tapes\nwithout changing your FSM in order to complete the checkoff.",
					  "TMSim Checkoff",
					  JOptionPane.WARNING_MESSAGE);
	    return;
	}

	if (n.tapeChecksum != n.checkoffChecksum) {
	    JOptionPane.showMessageDialog(this,"It appears that the checkoff information has been modified in some way.  Please verify that you haven't modified any 'tape' or 'result' statements.",
					  "TMSim Checkoff",
					  JOptionPane.WARNING_MESSAGE);
	    return;
	}

	DoCheckoff(n.checkoffServer);
    }

    public void GeneratePostData(POSTStream o) throws IOException {
	TMPanel n = (TMPanel)Selectee();

	// indicate assignment
	o.writeTag("checkoff");
	o.write(n.checkoffAssignment);

	// send number of states
	o.writeTag("size");
	o.write(Integer.toString(n.getNStates()));

	// send checksum
	o.writeTag("checksum");
	o.write(Integer.toString(n.checkoffChecksum));

	// version
	o.writeTag("version");
	o.write("TMSim"+version);
    }
}
