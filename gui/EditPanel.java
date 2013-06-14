// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

package gui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Observer;
import javax.swing.JPanel;

public class EditPanel extends JPanel implements ActionListener { 
    public GuiFrame parent;
    public GuiObservable observers;	// who's watching us

    public EditPanel(GuiFrame parent) {
	super();
	setLayout(new BorderLayout());
	this.parent = parent;
	observers = new GuiObservable();
    }

    public void actionPerformed(ActionEvent event) {
    }

    public void Kill() {
    }

    public void Message(String message) {
	parent.Message(message);
    }
}
