// Copyright (C) 1998-2011 Christopher J. Terman - All Rights Reserved.

package gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Observer;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing./*JTextPane*/JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import javax.swing.undo.UndoManager;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CannotRedoException;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.AbstractAction;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import java.awt.Toolkit;

public class EditBuffer extends EditPanel implements ActionListener, DocumentListener {
    public File source;
    public String original;		// the original source text
    public String tempdir;		// where to open temporary files
    public String title;		// title line from spice deck
    public boolean dirty;		// true if text has been edited
    public boolean errors;		// true if netlist has errors
    public JTextArea/*JTextPane*/ text;
    public JScrollPane scroll;
    public UndoManager undo;

    public EditBuffer(GuiFrame parent,File source,boolean fill) {
	super(parent);
	Init(source,fill);
    }

    public EditBuffer(GuiFrame parent,File source) {
	super(parent);
	Init(source,true);
    }

    void Init(File source,boolean fill) {
	text = new JTextArea/*JTextPane*/();
	String fname = parent.GetParameter("font");
	Font f = (fname != null) ? Font.decode(fname) :
	    new Font("Courier",Font.PLAIN,12);
	text.setFont(f);
	scroll = new JScrollPane(text);
	add(scroll,BorderLayout.CENTER);

	// see if we can suck in the source
	if (fill) ReadSource(source);
	else {
	    this.source = source;
	    setText("");
	}

	// do this *after* we've loaded text for the first time
	text.getDocument().addDocumentListener(this);

	// add undo/redo functionality
	undo = new UndoManager();
	text.getDocument().addUndoableEditListener(new UndoableEditListener() {
		public void undoableEditHappened(UndoableEditEvent evt) {
		    undo.addEdit(evt.getEdit());
		}
	    });

	// Create an undo action and add it to the text component
	text.getActionMap().put("Undo",
	    new AbstractAction("Undo") {
		public void actionPerformed(ActionEvent evt) {
		    try {
			if (undo.canUndo()) undo.undo();
		    } catch (CannotUndoException e) {
		    }
		}
	    });

	// Create a redo action and add it to the text component
	text.getActionMap().put("Redo",
	    new AbstractAction("Redo") {
		public void actionPerformed(ActionEvent evt) {
		    try {
			if (undo.canRedo()) undo.redo();
		    } catch (CannotRedoException e) {
		    }
		}
	    });

	// Bind the undo/redo actions to ctl-Z and ctl-Y
	text.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_Z,
				   Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),
	    "Undo");
	text.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_Y,
				   Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),
	    "Redo");
    }

    public String Source() {
	return source == null ? "untitled" : source.toString();
    }

    public String getText() {
	return text.getText();
    }

    public void setText(String s) {
	original = s;
	text.setText(original);
	MakeClean();
    }

    public String toString() {
	return "EditBuffer@"+source;
    }

    public void addObserver(Observer o) {
	observers.addObserver(o);
    }

    public void deleteObserver(Observer o) {
	observers.deleteObserver(o);
    }

    public void actionPerformed(ActionEvent event) {
    }

    public void CleanUp() {
    }

    public void ReadSource(File src) {
	original = "";
	text.setText(original);

	if (src == null) return;
	source = src;

	InputStream in;
	try {
	    in = new FileInputStream(src);
	}
	catch (Exception e) {
	    parent.ErrorMessage("Cannot open "+src);
	    return;
	}
	in = new BufferedInputStream(in);

	StringBuffer buffer = new StringBuffer();
	while (true) {
	    int ch;
	    try { ch = in.read(); }
	    catch (IOException e) {
		parent.ErrorMessage("IO exception reading file: "+e);
		break;
	    }
	    if (ch == '\r') continue;	// make ^M disappear
	    if (ch < 0) break;
	    else buffer.append((char)ch);
	}

	setText(buffer.toString());
    }

    public boolean CompareFile(File file) {
	if (source == null) return false;
	try {
	    String path1 = source.getCanonicalPath();
	    String path2 = file.getCanonicalPath();
	    return path1.equals(path2);
	}
	catch (IOException e) {
	    return false;
	}
    }

    public void Reload() {
	ReadSource(source);
    }

    public void Capture(PrintWriter out) {
	out.println("============== source: "+ToolTip());
	out.print(text.getText());
	out.println("==============");
    }

    public void Capture(POSTStream out) {
	try {
	    out.write("============== source: ");
	    out.write(ToolTip());
	    out.write('\n');
	    out.write(text.getText());
	    out.write("==============\n");
	}
	catch (Exception e) {
	}
    }

    public void CaptureAnon(POSTStream out) {
	try {
	    out.write("============== source: ");
	    out.write(TabName());   // don't include directory info
	    out.write('\n');
	    out.write(text.getText());
	    out.write("==============\n");
	}
	catch (Exception e) {
	}
    }

    // set up source and then call this method to save file
    private boolean SaveBuffer() {
	boolean error = false;

	// first try to output to a temporary file
	File tfile = new File(source.getParent(),"jsimtemp");

	try {
	    OutputStream lout = new FileOutputStream(tfile);
	    lout = new BufferedOutputStream(lout,1024);
			
	    // so we can use standard output routines...
	    PrintWriter out = new PrintWriter(lout);

	    original = text.getText();
	    out.print(original);
			
	    // now close up shop...
	    out.close();
	}
	catch (IOException e) {
	    error = true;
	    System.out.println("Exception during save: "+e);
	    parent.ErrorDialog("Could not save "+source+".\nFile may not be writeable. You might try saving with a different filename.");
	}
	catch (Exception e) {
	    error = true;
	    parent.ErrorMessage("Could not save library "+source+": "+e);
	    Thread.dumpStack();		// info to help debugging
	}

	if (!error) {
	    try {
		// turn current file (if there is one) into backup
		if (source.exists()) {
		    File backup = new File(source.getAbsolutePath()+".bak");
		    if (backup.exists()) backup.delete();
		    source.renameTo(backup);
		}
		// rename temp file to be the new
		tfile.renameTo(source);
	    }
	    catch (Exception e) { error = true; }
	}
		
	if (error) {
	    // complain if we had some errors...
	    parent.ErrorDialog("Internal error while saving "+source);
	    tfile.delete();	// delete temp file
	} else MakeClean();

	return !error;
    }

    public void Rename() {
	parent.RenameTab(this,TabName(),null);
    }

    public void Save(File nsource) {
	if (dirty) {
	    File old_source = source;
	    if (nsource != null) source = nsource;
	    else if (source == null) {
		source = parent.GetFile(JFileChooser.SAVE_DIALOG);
		if (source == null) return;
	    }

	    if (SaveBuffer()) Rename();
	    else source = old_source;
	}
    }

    public void SaveAs() {
	File old_source = source;
	source = parent.GetFile(JFileChooser.SAVE_DIALOG);
	if (source == null) return;
	if (SaveBuffer()) Rename();
	else source = old_source;
    }

    protected void MakeDirty() {
	if (!dirty) {
	    dirty = true;
	    Rename();
	}
    }

    protected void Changed() {
    }

    void MakeClean() {
	if (dirty) {
	    dirty = false;
	    Rename();
	}
    }

    public String TabName() {
	String n = (source != null) ? source.getName() : "untitled";
	if (dirty) n += "*";
	return n;
    }

    public String ToolTip() {
	return (source != null) ? source.getAbsolutePath() : "untitled";
    }

    public void changedUpdate(DocumentEvent e) {
	Changed();
	if (!dirty && !original.equals(text.getText())) MakeDirty();
    }
    public void insertUpdate(DocumentEvent e) {
	Changed();
	if (!dirty && !original.equals(text.getText())) MakeDirty();
    }
    public void removeUpdate(DocumentEvent e) {
	Changed();
	if (!dirty && !original.equals(text.getText())) MakeDirty();
    }

    public void setEnabled(boolean which) {
	super.setEnabled(which);
	if (which) requestFocus();
    }

    public void Message(EditBuffer buffer,int start,int end,String message) {
	parent.toFront();
	parent.SetTab(buffer);
	parent.Message(message,start,end);
	try {
	    buffer.text.select(start,end);
	}
	catch (Exception e) {
	    //e.printStackTrace(System.out);
	}
	buffer.text.requestFocus();
    }

    public void Error(EditBuffer buffer,int pos,String message) {
	errors = true;
	Message(buffer,pos,pos,message);
    }
}
