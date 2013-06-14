// Copyright (C) 1999-2011 Christopher J. Terman - All Rights Reserved.

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

public class Ant extends GuiFrame implements ActionListener, WindowListener {
    public static String version = "2.0.9";
    public static String copyright = "Copyright (C) Christopher J. Terman 1997-2011";

    String cmdargs[] = null;	// command line args

    public static String mazeNames[] = { "m1", "m2", "m3", "m4", "hampton" };
    public static String mazes[] = {
	// m1
	"XXXXXXX\n"+
	"XXXXXfX\n"+
	"X     X\n"+
	"X XXXXX\n"+
	"X  X  X\n"+
	"XX X XX\n"+
	"X    sX\n"+
	"XXXXXXX",
	// m2
	"XXXXXXXX\n"+
	"X      X\n"+
	"X XX X X\n"+
	"X Xs X X\n"+
	"X XXXX X\n"+
	"X      X\n"+
	"XXXXfXXX\n"+
	"XXXXXXXX",
	// m3
	"XXXXXXXXXXXXXXX\n"+
	"X             X\n"+
	"X XXXXXXXXXXX X\n"+
	"X      X      X\n"+
    	"X XXXX X XXXX X\n"+
       	"X X  X X X  X X\n"+
       	"X X    X    X X\n"+
	"X X XXXXXXX X X\n"+
	"X X X  s  X X X\n"+
	"X X X X X X X X\n"+
	"X X XXX XXX X X\n"+
	"X X         X X\n"+
	"X XXXXXXXXXXX X\n"+
	"X             X\n"+
	"XXXXXXfXXXXXXXX\n"+
	"XXXXXXXXXXXXXXX",
	// m4
	"XXXXXXXXXXXXXXX\n"+
       	"X             X\n"+
	"X XXXXXXX     X\n"+
  	"X X           X\n"+
       	"X X XXXXXXXX  X\n"+
       	"X X X      X  X\n"+
       	"X X X XXXX X  X\n"+
     	"X X X Xs X X  X\n"+
       	"X X X XX X X  X\n"+
     	"X X X    X X  X\n"+
      	"X X XXXXXX X  X\n"+
    	"X X        X  X\n"+
	"X XXXXXXXXXX  X\n"+
	"X             X\n"+
	"XXXXXXXfXXXXXXX\n"+
	"XXXXXXXXXXXXXXX",
	// hampton
	"XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"+
	"X        X      X      X          X\n"+
	"X XXXXXX X XXXXXX XXXX X XXXXXXXX X\n"+
	"X X      X X         X X        X X\n"+
	"X X XXXXXX X XXXXXXX X XXXXXXXX X X\n"+
	"X X X      X    X    X        X X X\n"+
	"X X X XXXXXXXXX X XXXXX XXXXX X X X\n"+
	"X X X X   X     X     X X X X X X X\n"+
	"X X   X X X XXXXXXXXX X X X X   X X\n"+
	"X XXX X X X X       X X X X X XXX X\n"+
	"X   X X X X Xf      X X X X X X   X\n"+
	"XXX X X X X X       X X X X X X XXX\n"+
	"X   X X X X XXXX XXXX X X X X X   X\n"+
	"X XXX X X X    X X    X X X   X X X\n"+
	"X X   X X XXXX X X XXXX X XXXXX X X\n"+
	"X X X   X    X X X      X       X X\n"+
	"X X XXXXXXXXXX X XXXXXXXXXXXXXXXX X\n"+
	"X X            X                  X\n"+
	"X XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"+
	"X                                 X\n"+
	"XXXXXXXXXXXXXXXXsXXXXXXXXXXXXXXXXXX\n"+
	"XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    };

    String initialFSM =
	"; fsm from lecture\n"+
	"\n"+
	";            |        T T      \n"+
	";now   L R S | next   L R F M E\n"+
	";------------+-----------------\n"+
	"lost   0 0 - | lost   0 0 1 0 0\n"+
	"lost   1 - - | rotccw 0 0 1 0 0\n"+
	"lost   0 1 - | rotccw 0 0 1 0 0\n"+
	"rotccw 0 0 - | wall1  1 0 0 0 0\n"+
	"rotccw 1 - - | rotccw 1 0 0 0 0\n"+
	"rotccw 0 1 - | rotccw 1 0 0 0 0\n"+
	"wall1  - 0 - | wall1  0 1 1 0 0\n"+
	"wall1  - 1 - | wall2  0 1 1 0 0\n"+
	"wall2  1 - - | rotccw 1 0 1 0 0\n"+
	"wall2  0 1 - | wall2  1 0 1 0 0\n"+
	"wall2  0 0 - | wall1  1 0 1 0 0\n";

    public Ant(String args[]) {
	super(args,"Roboant "+version,true,false);
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
	Message("Roboant "+version+", "+copyright);

	SetTab(ReadFiles());

	// display our handiwork
	setVisible(true);
    }

    // used by stand-alone application
    public static void main(String args[]) {
	new Ant(args);
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
	AntPanel result = (AntPanel)super.ReadFiles();
	if (result.source == null) {
	    result.setText(initialFSM);
	    result.SetMaze(0);
	}
	return result;
    }

    public Component OpenFile(File file,boolean atEnd) {
	// set default directory for file chooser
	if (lastDirectory == null && file != null)
	    lastDirectory = file.getParent();

	AntPanel n = new AntPanel(this,file);
	AddTab(n.TabName(),n.ToolTip(),n,atEnd ? -1 : 0);

	return (Component)n;
    }

    public void Checkoff() {
	AntPanel n = (AntPanel)Selectee();
	int result = n.Solved();

	if ((result & 0x7) != 0x7) {
	    JOptionPane.showMessageDialog(this,"You must solve mazes m1, m2 and m3 without changing\nyour FSM in order to complete the checkoff.",
					  "Checkoff failure...",
					  JOptionPane.WARNING_MESSAGE);

	    String notsolved = null;
	    if ((result & 0x1) == 0) notsolved = mazeNames[0];
	    else if ((result & 0x2) == 0) notsolved = mazeNames[1];
	    else notsolved = mazeNames[2];
	    Message("maze "+notsolved+" hasn't been solved!");
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
