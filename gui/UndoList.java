// Copyright (C) 2002 Christopher J. Terman - All Rights Reserved.

package gui;

import java.util.Vector;

public class UndoList {
    public String name;
    public Vector list;
    UndoList nextLink;
    UndoList prevLink;

    public UndoList(String name,Vector list,UndoList prev) {
	this.name = name;
	this.list = list;
	if (prev != null) {
	    prevLink = prev;
	    prev.nextLink = this;
	}
    }

    public final UndoList next() {
	return nextLink;
    }
	
    public final UndoList prev() {
	return prevLink;
    }
	
    public final void ignoreNext() {
	if (nextLink != null)
	    nextLink = nextLink.next();
    }
	
    public final void ignorePrev() {
	if (prevLink != null)
	    prevLink = prevLink.prev();
    }
	
    public final void unlink() {
	if (prevLink != null)
	    prevLink.ignoreNext();
	if (nextLink != null)
	    nextLink.ignorePrev();
    }
	
    public final void insertAfter(UndoList x) {
	x.unlink();
	prevLink = this;
	x.nextLink = nextLink;
	if (nextLink!=null)
	    nextLink.prevLink = x;
	nextLink = x;
    }
	
    public boolean CanUndoRedo(boolean redo) {
	UndoList u = (UndoList)(redo ? next() : prev());
	return u != null;
    }
}
