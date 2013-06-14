// Copyright (C) 1999-2007 Christopher J. Terman - All Rights Reserved.

package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MediaTracker;
import java.awt.Point;
import java.awt.PrintJob;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.ImageProducer;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.zip.CRC32;

public class GuiFrame extends JFrame implements ActionListener, WindowListener, ProgressTracker {
    public String cmdargs[];	// command line args
    public JToolBar bbar;
    public JTabbedPane tabPane;	// for organizing tabs
    boolean mainWindow;		// top level of application?
    Font displayFont;		// handy font for display
    public JTextArea message;		// message for the user
    JScrollPane mscroll;	// message wrapper
    String nextMessage;
    public String lastDirectory;  // last directory user specified in GetFile

    JProgressBar progress;	// progress indicator for long tasks
    Object progressOwner;	// who owns progress bar at the moment
    int progressValue;
    public Thread eventThread;
    Runnable UpdateProgress;
    Runnable UpdateMessage;

    HashMap imageResources;

    public GuiFrame(String[] args,String name,boolean main,boolean progressBar) {
	super(name);
	// override JFrame's default action of hiding the frame
	setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

	imageResources = new HashMap();
	eventThread = null;
	mainWindow = main;
	cmdargs = args;
	setBackground(UI.BGCOLOR);
	displayFont = new Font("SansSerif",Font.PLAIN,12);
	UIManager.put("ScrollBar.width",new Integer(10));

	// only update progress bar from event thread
	UpdateProgress = new Runnable() {
		public void run() {
		    if (progress != null) {
			progress.setStringPainted(progressValue != 0);
			progress.setValue(progressValue);
		    }
		}
	    };

	// only update message from event thread
	UpdateMessage = new Runnable() {
		public void run() {
		    if (nextMessage != null) {
			if (!message.getText().equals(nextMessage))
			    message.setText(nextMessage);
		    } else message.setText("");
		}
	    };

	// build contents
	Container contentPane = getContentPane();
	contentPane.setLayout(new BorderLayout());
	
	// tool bar goes at top
	bbar = new JToolBar();
	bbar.putClientProperty("JToolBar.isRollover",Boolean.TRUE);
	contentPane.add(bbar,BorderLayout.NORTH);

	// tabs are next
	tabPane = new JTabbedPane();
	contentPane.add(tabPane,BorderLayout.CENTER);

	// set up message area
	JPanel bottom = new JPanel();
	bottom.setLayout(new BorderLayout());
	contentPane.add(bottom,BorderLayout.SOUTH);

	message = new JTextArea(1,10);
	message.setEditable(false);
	message.setHighlighter(null);
	message.setBackground(Color.white);
	message.setFont(displayFont);
	mscroll = new JScrollPane(message,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
	mscroll.setBorder(BorderFactory.createLoweredBevelBorder());
	bottom.add(mscroll,BorderLayout.CENTER);

	// set up progress bar
	if (progressBar) {
	    progress = new JProgressBar();
	    progress.setPreferredSize(new Dimension(75,20));
	    progress.setBorder(BorderFactory.createLoweredBevelBorder());
	    bottom.add(progress,BorderLayout.EAST);
	} else progress = null;

	// set frame size
	Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
	screenSize.width = Math.min(815,screenSize.width - 50);
	screenSize.height = Math.min(630,screenSize.height - 50);
	setSize(screenSize.width,screenSize.height);

	addWindowListener(this);
    }

    public JButton ImageButton(String image) {
	Image i = GetImageResource(image);
	return new JButton(new ImageIcon(i));
    }

    public JButton AddToolButton(String image,String action,ActionListener al) {
	return AddToolButton(bbar,image,action,al);
    }

    public JButton AddToolButton(JToolBar toolbar,String image,String action,ActionListener al) {
	JButton button = ImageButton(image);
	button.setToolTipText(action);
	button.setActionCommand(action);
	button.addActionListener(al);
	toolbar.add(button);
	return button;
    }

    public JToggleButton ImageToggleButton(String image) {
	Image i = GetImageResource(image);
	return new JToggleButton(new ImageIcon(i));
    }

    public JToggleButton AddToolToggleButton(String image,String action,ActionListener al) {
	return AddToolToggleButton(bbar,image,action,al);
    }

    public JToggleButton AddToolToggleButton(JToolBar toolbar,String image,String action,ActionListener al) {
	JToggleButton button = ImageToggleButton(image);
	button.setToolTipText(action);
	button.setActionCommand(action);
	button.addActionListener(al);
	toolbar.add(button);
	return button;
    }

    public void AddToolSeparator() {
	AddToolSeparator(bbar);
    }

    public void AddToolSeparator(JToolBar toolbar) {
	toolbar.addSeparator();
    }

    public Component Selectee() {
	return tabPane.getSelectedComponent();
    }

    public void AddTab(String name,String tip,Component v,int where) {
	if (where == -1) tabPane.addTab(name,null,v,tip);
	else tabPane.insertTab(name,null,v,tip,where);
    }

    public void RemoveTab(Component v) {
	tabPane.remove(v);
    }

    public void RemoveTab(int i) {
	tabPane.remove(i);
    }

    public void RenameTab(Component v,String name,String tip) {
	int i = tabPane.indexOfComponent(v);
	if (i != -1) {
	    tabPane.setTitleAt(i,name);
	    tabPane.setToolTipTextAt(i,tip);
	}
    }

    public int IndexFor(Component v) {
	return tabPane.indexOfComponent(v);
    }

    // used by JSim, ignored for others
    public void SetChannels(int n) { }
    public void SetPlot(Component v) { }

    public void SetTab(Component v) {
	tabPane.setSelectedComponent(v);
    }

    public void SetTab(int i) {
	tabPane.setSelectedIndex(i);
    }

    public void ErrorDialog(String msg) {
	GuiDialog d = new GuiDialog(this,"Error Message",true);
	d.ShowErrorMessage(msg);
    }

    public void ErrorMessage(String msg) {
	System.out.println(msg);
	Message(msg);
    }

    public void MessageSetup(int nrows,boolean scroll) {
	message.setRows(nrows);
	mscroll.setHorizontalScrollBarPolicy(scroll ? JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED : JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
	mscroll.setVerticalScrollBarPolicy(scroll ? JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED : JScrollPane.VERTICAL_SCROLLBAR_NEVER);
    }

    public void Message(String msg,int start,int end) {
	Message(msg);
    }

    public void Message(String msg) {
	if (Thread.currentThread() == eventThread || eventThread == null) {
	    if (msg != null) {
		if (!message.getText().equals(msg))
		    message.setText(msg);
	    } else message.setText("");
	    Toolkit.getDefaultToolkit().sync();
	} else {
	    nextMessage = msg;
	    SwingUtilities.invokeLater(UpdateMessage);
	}
    }

    public void ClearMessage(String msg) {
	if (msg==null || message.getText().equals(msg))
	    Message("");
    }

    public void actionPerformed(ActionEvent event) {
	String what = event.getActionCommand();
	Point loc = getLocationOnScreen();
	int x = loc.x;
	int y = loc.y;

	if (what.equals(UI.EXIT)) {
	    windowClosing(null);
	} else if (what.equals(UI.NEW)) MakeUntitled();
	else if (what.equals(UI.CLOSE)) {
	    EditBuffer n = (EditBuffer)Selectee();
	    if (n != null) {
		if (!DiscardChanges(n)) return;
		RemoveTab(n);
	    }
	    if (tabPane.getTabCount() == 0) MakeUntitled();
	} else if (what.equals(UI.OPEN)) {
	    File file = GetFile(JFileChooser.OPEN_DIALOG);
	    if (file != null) {
		int nviews = tabPane.getTabCount();
		for (int i = 0; i < nviews; i += 1) {
		    EditBuffer old = (EditBuffer)tabPane.getComponentAt(i);
		    if (old.CompareFile(file)) {
			if (DiscardChanges(old)) old.ReadSource(file);
			SetTab(old);
			return;
		    }
		}
		SetTab(OpenFile(file,false));
	    }
	} else if (what.equals(UI.SAVE)) {
	    EditBuffer n = (EditBuffer)Selectee();
	    if (n != null) {
		n.Save(null);
		DoDataGathering("save");
	    }
	} else if (what.equals(UI.SAVEAS)) {
	    EditBuffer n = (EditBuffer)Selectee();
	    if (n != null) {
		n.SaveAs();
		DoDataGathering("save");
	    }
	} else if (what.equals(UI.RELOAD)) {
	    int nviews = tabPane.getTabCount();
	    for (int i = 0; i < nviews; i += 1) {
		EditBuffer n = (EditBuffer)tabPane.getComponentAt(i);
		if (DiscardChanges(n)) n.Reload();
	    }
	    Message("File reloaded");
	} else if (what.equals(UI.SAVEALL)) {
	    int nviews = tabPane.getTabCount();
	    for (int i = 0; i < nviews; i += 1) {
		EditBuffer n = (EditBuffer)tabPane.getComponentAt(i);
		n.Save(null);
	    }
	    DoDataGathering("save");
	} else ((ActionListener)Selectee()).actionPerformed(event);
    }

    // window listener interface
    public void windowClosed(WindowEvent event) { }
    public void windowDeiconified(WindowEvent event) { }
    public void windowIconified(WindowEvent event) { }
    public void windowActivated(WindowEvent event) { }
    public void windowDeactivated(WindowEvent event) { }
    public void windowOpened(WindowEvent event) { }
    public void windowClosing(WindowEvent event) {
	if (mainWindow) {
	    if (!SaveAllFiles()) {
		setVisible(true);
		return;
	    }
	    System.exit(0);
	} else setVisible(false);
    }

    // helper routines
    static public void MemStat() {
	Runtime rt = Runtime.getRuntime();
	rt.gc();
	long free = rt.freeMemory();
	long total = rt.totalMemory();
	System.out.println("used "+(total-free)+" free "+free+" total "+total);
    }

    // merge parent pathname with given filename
    public String MergePathnames(File source,String fname) {
	String parent;
	try {
	    parent = source.getCanonicalPath();
	    int sep = parent.lastIndexOf(File.separatorChar);
	    if (sep != -1) parent = parent.substring(0,sep);
	}
	catch (Exception e) {
	    parent = source.getParent();
	}

	return MergePathnames(parent,fname);
    }

    public String MergePathnames(String parent,String fname) {
	if (fname.startsWith(File.separator) ||
	    (fname.length() > 2 && fname.charAt(1) == ':') ||
	    parent == null)
	    return fname;
	else return parent + File.separator + fname;
    }

    // prompt user for a filename
    public File GetFile(int mode) {
	JFileChooser chooser = new JFileChooser(lastDirectory);
	int result = (mode == JFileChooser.OPEN_DIALOG) ?
	    chooser.showOpenDialog(this) :
	    chooser.showSaveDialog(this);
	if (result == JFileChooser.CANCEL_OPTION)
	    return null;
	else if (result == JFileChooser.APPROVE_OPTION) {
	    lastDirectory = chooser.getCurrentDirectory().toString();
	    return chooser.getSelectedFile();
	} else {
	    ErrorMessage("File open failed");
	    return null;
	}
    }

    // return image given a name
    public Image GetImage(String name,boolean code) {
	Image image;

	// see if we can open a stream to read in the netlist
	try { image = getToolkit().getImage(name); }
	catch (Exception e) {
	    System.out.println("Exception from getImage " + name);
	    return null;
	}
	if (image == null) {
	    System.out.println("Can't find image " + name);
	    return null;
	}
	return image;
    }

    public Image GetImageResource(String resource) {
	// check in cache to avoid overhead of searching out image
	Image i = (Image)imageResources.get(resource);

	if (i == null) {
	    try {
		URL url = getClass().getResource(resource);			
		if (url == null) {
		    System.out.println("using GetImage: "+resource);
		    i = GetImage("icons/"+resource,true);
		} else i = createImage((ImageProducer)url.getContent());
		imageResources.put(resource,i);
	    }
	    catch (Exception e) {
		System.out.println("Exception in GetImageResource: "+e);
		i = null;
	    }
	}
	return i;
    }
	
    public static void waitForImage(Image image, Component where) {
	boolean error = false;

	// wait until all the pixels have been retreived
	try {
	    MediaTracker tracker = new MediaTracker(where);
	    tracker.addImage(image,0);
	    tracker.waitForID(0);
	    error = tracker.isErrorID(0);
	}
	catch (Exception e) {
	    error = true;
	}
	if (error == true)
	    image = null;
    }

    // interface to progress bar
    public boolean ProgressStart(Object owner) {
	if (progress != null && progressOwner == null) {
	    progressOwner = owner;
	    progressValue = 0;
	    SwingUtilities.invokeLater(UpdateProgress);
	    return true;
	} else return false;
    }

    public boolean ProgressStart(Object owner,Color c) {
	return ProgressStart(owner);
    }

    public long ProgressStop(Object owner) {
	if (progress != null && progressOwner == owner) {
	    progressOwner = null;
	    progressValue = 0;
	    SwingUtilities.invokeLater(UpdateProgress);
	}
	return 0;
    }

    public void ProgressReport(Object owner,double v) {
	if (progress != null && progressOwner == owner) {
	    progressValue = (int)(100 * v);
	    SwingUtilities.invokeLater(UpdateProgress);
	}
    }

    public PrintJob StartPrinting(Dimension d,Dimension offset) {
	Properties props = new Properties();
	PrintJob pj = null;
		
	try {
	    pj = Toolkit.getDefaultToolkit().getPrintJob(this,"Plot",props);
	}
	catch (Exception e) {
	    Message("can't get print job: "+e);
	    //System.out.println("can't get print job: "+e);
	    //e.printStackTrace(System.out);
	    pj = null;
	}
		
	if (pj != null) {
	    Font tfont = Font.decode("SansSerif-bold-14");
	    FontMetrics tfm = Toolkit.getDefaultToolkit().getFontMetrics(tfont);
	    int fheight = tfm.getMaxDescent() + tfm.getMaxAscent();
	    int margin = 5;
	    int theight = fheight + 2*margin;

	    // find out what we can about how much space we got.  Some
	    // printers aren't serious about the last pixel...
	    Dimension pd = pj.getPageDimension();
	    int vmargin = 40;
	    int hmargin = 35;
	    //System.out.println("res="+pj.getPageResolution()+" size="+pd);

	    // leave room for banner at bottom of page
	    d.height = pd.height - vmargin - theight;
	    d.width = pd.width - hmargin;
	    offset.width = 20;
	    offset.height = 20;

	    //System.out.println("dimension="+d);

	    return pj;
	} else Message("Can't get print job");
	return null;
    }
		
    // output banner at bottom of page
    public void PrintBanner(Graphics g,Dimension d,Dimension offset,String text) {
	Font tfont = Font.decode("SansSerif-bold-14");
	FontMetrics tfm = g.getFontMetrics(tfont);
	int fheight = tfm.getMaxDescent() + tfm.getMaxAscent();
	int margin = 5;
	int theight = fheight + 2*margin;

	String time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date());
	int ty = offset.height + d.height + theight - tfm.getMaxDescent() - margin;

	g.setColor(UI.BGCOLOR);
	g.fillRect(offset.width+1,offset.height+d.height,d.width-1,theight-1);
	g.setFont(tfont);
	g.setColor(Color.black);
	g.drawString(text,offset.width + margin,ty);
	g.drawString(time,offset.width + d.width - margin - tfm.stringWidth(time),ty);
	g.drawRect(offset.width,offset.height + d.height-1,d.width-1,theight);
    }

    // new stuff
    public EditBuffer FindBuffer(File file) {
	int nviews = tabPane.getTabCount();
	for (int i = 0; i < nviews; i += 1) {
	    Component v = tabPane.getComponentAt(i);
	    if (!(v instanceof EditBuffer)) continue;
	    EditBuffer n = (EditBuffer)v;
	    if (n.CompareFile(file)) return n;
	}

	return (EditBuffer)OpenFile(file,true);
    }

    public EditBuffer FindTab(File file) {
	int nviews = tabPane.getTabCount();
	for (int i = 0; i < nviews; i += 1) {
	    Component v = tabPane.getComponentAt(i);
	    if (!(v instanceof EditBuffer)) continue;
	    EditBuffer n = (EditBuffer)v;
	    if (n.CompareFile(file)) return n;
	}

	EditBuffer n = new EditBuffer(this,file,false);
	AddTab(n.TabName(),n.ToolTip(),n,-1);
	return n;
    }

    // gets overridden
    public Component OpenFile(File file,boolean atEnd) {
	return null;
    }

    public EditBuffer ReadFiles() {
	// look through command args; anything that's not a switch
	// should be the name of a file
	for (int i = 0; i < cmdargs.length; i += 1) {
	    if (cmdargs[i].startsWith("--")) i += 1;
	    else if (!cmdargs[i].startsWith("-")) {
		File file = new File(cmdargs[i]);
		if (file != null) OpenFile(file,true);
	    }
	}

	// cons up empty buffer if user didn't specify at least
	// one file
	if (tabPane.getTabCount() == 0) OpenFile(null,true);

	// initially select first file
	tabPane.setSelectedIndex(0);
	return (EditBuffer)tabPane.getComponentAt(0);
    }

    public void MakeUntitled() {
	SetTab(OpenFile(null,false));
    }

    public boolean DiscardChanges(EditBuffer n) {
	if (n != null && n.dirty) {
	    GuiDialog d = new GuiDialog(this,"Discard changes?",true);
	    return !d.ShowMessage("File has been changed since it was\nread from disk.  Discard changes?");
	} else return true;	// no changes
    }

    public boolean SaveAllFiles() {
	// see if anything needs saving...
	ArrayList checkboxes = new ArrayList();
	ArrayList saves = new ArrayList();
	int nviews = tabPane.getTabCount();
	for (int i = 0; i < nviews; i += 1) {
	    Component v = tabPane.getComponentAt(i);
	    if (!(v instanceof EditBuffer)) continue;
	    EditBuffer n = (EditBuffer)v;
	    if (n.dirty) {
		String name = n.TabName();
		if (name.endsWith("*"))
		    name = name.substring(0,name.length()-1);
		JCheckBox cbox = new JCheckBox(name,null,true);
		checkboxes.add(cbox);
		saves.add(n);
	    }
	}

	// if there's something to save, ask about it
	if (!checkboxes.isEmpty()) {
	    GuiDialog d = new GuiDialog(this,UI.SAVEBUFFERS,false);
	    JButton e1 = new JButton("Exit without saving");
	    JButton e2 = new JButton("Exit");
	    JButton e3 = new JButton("Cancel");
	    d.AddButton(e2);
	    d.AddButton(e1);
	    d.AddButton(e3);

	    JPanel p = new JPanel();
	    d.getContentPane().add("North",new JLabel("Select which buffers to save:"));
	    d.getContentPane().add("Center",p);
	    p.setLayout(new GridLayout(0,1));
	    int nboxes = checkboxes.size();
	    for (int i = 0; i < nboxes; i += 1)
		p.add((JCheckBox)checkboxes.get(i));
	    d.pack();
	    boolean cancelled = d.Show(0,0);
	    if (cancelled || d.exitButton == e3) return false;
	    if (d.exitButton == e2) {
		for (int i = 0; i < nboxes; i += 1) {
		    JCheckBox c = (JCheckBox)checkboxes.get(i);
		    if (c.isSelected())
			((EditBuffer)saves.get(i)).Save(null);
		}
		DoDataGathering("save");
	    }
	}

	Message("Cleaning up temporary files");
	for (int i = 0; i < nviews; i += 1) {
	    EditBuffer n = (EditBuffer)tabPane.getComponentAt(i);
	    n.CleanUp();
	}
	Message("");
	return true;
    }

    // return parameter value given parameter name
    public String GetParameter(String pname) {
	// if we're an application, search for "-key" or "--key value" pair
	// in command line args
	for (int i = 0; i < cmdargs.length; i += 1) {
	    if (cmdargs[i].startsWith("--") && cmdargs[i].endsWith(pname))
		return cmdargs[i+1];
	    else if (cmdargs[i].startsWith("-") && cmdargs[i].endsWith(pname))
		return cmdargs[i];
	}
	return null;
    }

    // create a dialog with a small window to view HTML
    public void ShowHTML(String title,String results,int type) {
	JEditorPane html = new JEditorPane("text/html","");
	html.setEditable(false);
	JOptionPane pane = new JOptionPane(new JScrollPane(html),type);
	JDialog dialog = pane.createDialog(this,title);
	dialog.setSize(500,300);
	html.setText(results.toString());
	dialog.show();
    }

    // override this
    public void GeneratePostData(POSTStream o) throws IOException { }

    public void DoCheckoff(String serverName) {
	// get username and password from user
	JPanel msg = new JPanel();
	GridBagLayout g = new GridBagLayout();
	GridBagConstraints gc = new GridBagConstraints();
	msg.setLayout(g);

	// intro text
	gc.insets = new Insets(2,2,2,2);
	gc.anchor = GridBagConstraints.NORTHEAST;
	gc.fill = GridBagConstraints.HORIZONTAL;
	gc.gridwidth = GridBagConstraints.REMAINDER;
	JLabel l = new JLabel("Verification succeeded!");
	g.setConstraints(l,gc);
	msg.add(l);
	l = new JLabel("Please enter your 6.004 user name and password:");
	g.setConstraints(l,gc);
	msg.add(l);

	// user name
	String sender = System.getProperty("user.name","???");
	gc.gridwidth = 1;
	l = new JLabel("User name:");
	g.setConstraints(l,gc);
	msg.add(l);
	gc.gridwidth = GridBagConstraints.REMAINDER;
	JTextField username = new JTextField(sender);
	g.setConstraints(username,gc);
	msg.add(username);

	// password
	gc.gridwidth = 1;
	l = new JLabel("Password:");
	g.setConstraints(l,gc);
	msg.add(l);
	gc.gridwidth = GridBagConstraints.REMAINDER;
	JPasswordField password = new JPasswordField();
	g.setConstraints(password,gc);
	msg.add(password);

	// collaboration statement
	gc.gridwidth = 1;
	l = new JLabel("Collaboration:");
	g.setConstraints(l,gc);
	msg.add(l);
	gc.gridwidth = GridBagConstraints.REMAINDER;
	JTextArea collaboration = new JTextArea(5,30);
	collaboration.setLineWrap(true);
	collaboration.setWrapStyleWord(true);
	JScrollPane scroll = new JScrollPane(collaboration);
	g.setConstraints(scroll,gc);
	msg.add(scroll);

	int response = JOptionPane.showOptionDialog(this,msg,
						    "Complete checkoff",
						    JOptionPane.OK_CANCEL_OPTION,
						    JOptionPane.QUESTION_MESSAGE,
						    null,
						    null,
						    null
						    );

	if (response != JOptionPane.OK_OPTION) return;

	// POST the results back to the server
	try {
	    URL url = new URL("http://"+serverName);

	    URLConnection conn = url.openConnection();
	    conn.setDoOutput(true);
	    conn.setDoInput(true);
	    POSTStream o = new POSTStream(conn.getOutputStream());

	    // username, password, sender, collaboration
	    o.writeTag("username");
	    o.write(username.getText());
	    o.writeTag("userpassword");
	    o.write(password.getPassword());
	    o.writeTag("sender");
	    o.write(sender);
	    o.writeTag("collaboration");
	    o.write(collaboration.getText());

	    GeneratePostData(o);

	    // capture buffer contents
	    o.writeTag("circuits");
	    int nviews = tabPane.getTabCount();
	    for (int i = 0; i < nviews; i += 1) {
		EditBuffer n = (EditBuffer)tabPane.getComponentAt(i);
		n.Capture(o);
	    }

	    o.close();

	    // Retrieve POST results
	    StringBuffer results = new StringBuffer();
	    InputStream istream = conn.getInputStream();
	    int ch;
	    while ((ch = istream.read()) != -1) results.append((char)ch);
	    istream.close();

	    // use modal dialog to show results to user
	    ShowHTML("Checkoff results",
		     results.toString(),
		     JOptionPane.INFORMATION_MESSAGE);
	}
	catch (Exception e) {
	    /*
	    System.out.println("Exception during checkoff: "+e);
	    e.printStackTrace(System.out);
	    */
	    JOptionPane.showMessageDialog(this,"Exception during checkoff: "+e,
					  "Checkoff failure...",
					  JOptionPane.WARNING_MESSAGE);
	}
    }

    // may be overridden...
    public String[] ignore = {};

    // send de-identified buffers to server
    public String  DoDataGathering(String assignment) {
	// use CRC calculated from user name as the unique user id
	CRC32 hash = new CRC32();
	hash.update(System.getProperty("user.name","???").getBytes());
	String uid = java.lang.Long.toHexString(hash.getValue());

	if (assignment == null) assignment = "unknown";

	// POST the results back to the server
	try {
	    URL url = new URL("http://cjt.csail.mit.edu/6.004/data/capture.cgi");

	    URLConnection conn = url.openConnection();
	    conn.setDoOutput(true);
	    conn.setDoInput(true);
	    POSTStream o = new POSTStream(conn.getOutputStream());

	    // username, password, sender, collaboration
	    o.writeTag("uid");
	    o.write(uid);
	    o.writeTag("assignment");
	    o.write(assignment);

	    // capture buffer contents
	    o.writeTag("buffers");
	    int nviews = tabPane.getTabCount();
	    for (int i = 0; i < nviews; i += 1) {
		EditBuffer n = (EditBuffer)tabPane.getComponentAt(i);
		String name = n.TabName();
		boolean capture = true;

		// weed out course-supplied design files
		if (ignore != null)
		    for (int j = 0; j < ignore.length; j += 1)
			if (name.equals(ignore[j])) {
			    capture = false;
			    break;
			}
		
		if (capture) n.CaptureAnon(o);
	    }

	    o.close();

	    // Retrieve POST results
	    StringBuffer results = new StringBuffer();
	    InputStream istream = conn.getInputStream();
	    int ch;
	    while ((ch = istream.read()) != -1) results.append((char)ch);
	    istream.close();

	    return results.toString();
	}
	catch (Exception e) {
	    return null;
	}
    }
}
