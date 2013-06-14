// Copyright (C) 1998-2000 Christopher J. Terman - All Rights Reserved.

package gui;

import java.util.Observable;

public class GuiObservable extends Observable {
    boolean notify = true;

    public GuiObservable() {
	super();
    }

    public boolean setNotify(boolean which) {
	boolean old = notify;
	notify = which;
	return old;
    }

    public synchronized boolean hasChanged() {
	return notify;
    }

    public void notifyObservers(Object arg) {
	if (notify) {
	    setChanged();
	    super.notifyObservers(arg);
	}
    }
}
