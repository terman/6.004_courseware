// Copyright (C) 1998-2002 Christopher J. Terman - All Rights Reserved.

package gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

// A standard dialog with an ok and cancel button at the bottom.
public class GuiDialog extends JDialog implements ActionListener, WindowListener {
    boolean abort = false;
    JPanel buttons;
    public JButton exitButton;
    JButton okay;
    JButton cancel;

    JPanel centerPanel;
    GridBagLayout glayout;
    GridBagConstraints gc;

    public GuiDialog(Frame f,String title,boolean addButtons) {
	super(f,title,true);
	setResizable(false);

	Container contentPane = getContentPane();
	contentPane.setLayout(new BorderLayout());
	contentPane.setFont(new Font("SansSerif",Font.PLAIN,12));

	// add standard buttons at the bottom
	buttons = new JPanel();
	buttons.setLayout(new FlowLayout(FlowLayout.CENTER,15,5));
	contentPane.add("South",buttons);
	if (addButtons) AddButtons();

	addWindowListener(this);
    }

    public void AddButtons() {
	AddButton(okay = new JButton("Okay"));
	AddButton(cancel = new JButton("Cancel"));
    }

    public void AddButton(JButton b) {
	b.addActionListener(this);
	buttons.add(b);
    }

    public boolean ShowMessage(String msg) {
	SetupGridBag();
	gc.gridwidth = GridBagConstraints.REMAINDER;

	int last = 0;
	int len = msg.length();
	while (last < len) {
	    int next = msg.indexOf('\n',last);
	    if (next == -1) next = len;
	    JLabel l = new JLabel(msg.substring(last,next));
	    glayout.setConstraints(l,gc);
	    centerPanel.add(l);
	    last = next + 1;
	}
	pack();

	// finally show dialog and wait until user clicks okay
	// or closes window
	return Show(0,0);
    }

    public void ShowErrorMessage(String msg) {
	// remove cancel button
	buttons.remove(cancel);
	ShowMessage(msg);
    }

    public void SetupGridBag() {
	centerPanel = new JPanel();
	getContentPane().add("Center",centerPanel);
	glayout = new GridBagLayout();
	centerPanel.setLayout(glayout);

	gc = new GridBagConstraints();
	gc.anchor = GridBagConstraints.WEST;
    }

    public void AddItem(String label,Component item) {
	if (centerPanel != null) {
	    JLabel l = new JLabel(label+":");
	    gc.weightx = 0.0;
	    gc.fill = GridBagConstraints.NONE;
	    gc.gridwidth = 1;
	    glayout.setConstraints(l,gc);
	    centerPanel.add(l);

	    gc.weightx = 1.0;
	    gc.fill = GridBagConstraints.HORIZONTAL;
	    gc.gridwidth = GridBagConstraints.REMAINDER;
	    glayout.setConstraints(item,gc);
	    centerPanel.add(item);
	}
    }

    public void AddItem(String description,String label,Component edit) {
	if (centerPanel != null) {
	    JLabel desc = new JLabel(description);
	    JLabel pname = new JLabel(label+"=");
	    gc.anchor = GridBagConstraints.WEST;
	    gc.gridwidth = 1;
	    gc.fill = GridBagConstraints.NONE;
	    gc.weightx = 0.0;
	    glayout.setConstraints(desc,gc);
	    centerPanel.add(desc);
	    gc.anchor = GridBagConstraints.EAST;
	    glayout.setConstraints(pname,gc);
	    centerPanel.add(pname);
	    gc.anchor = GridBagConstraints.WEST;
	    gc.gridwidth = GridBagConstraints.REMAINDER;
	    gc.fill = GridBagConstraints.HORIZONTAL;
	    gc.weightx = 1.0;
	    glayout.setConstraints(edit,gc);
	    centerPanel.add(edit);
	}
    }

    public boolean Show(int x,int y) {
	abort = false;

	if (okay != null) {
	    // position over "Okay" buttom
	    Point blocn = buttons.getLocation();
	    Point oklocn = okay.getLocation();
	    Dimension oksize = okay.getSize();
	    x -= blocn.x + oklocn.x + oksize.width/2;
	    y -= blocn.y + oklocn.y + oksize.height/2;
	}

	// don't position off screen!
	Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
	Dimension d = getSize();
	x = Math.max(40,Math.min(x,screenSize.width-d.width));
	y = Math.max(40,Math.min(y,screenSize.height-d.height));

	setLocation(x,y);
	show();
	return abort;
    }

    public boolean Show(int x,int y,JTextField focus) {
	if (focus != null) {
	    focus.addActionListener(this);  // typing RETURN terminates dialog
	    focus.requestFocus();	// make it easy to type into field
	}
	boolean result = Show(x,y);
	if (focus != null) focus.removeActionListener(this);
	return result;
    }

    public void windowClosed(WindowEvent event) { }
    public void windowDeiconified(WindowEvent event) { }
    public void windowIconified(WindowEvent event) { }
    public void windowActivated(WindowEvent event) { }
    public void windowDeactivated(WindowEvent event) { }
    public void windowOpened(WindowEvent event) { }
    public void windowClosing(WindowEvent event) {
	dispose();
	abort = true;
    }

    public void actionPerformed(ActionEvent event) {
	if (event.getSource() == cancel) abort = true;
	exitButton = (JButton)event.getSource();
	dispose();
    }
}

