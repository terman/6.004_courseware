// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class Event {
    static final double NO_EVENT = -1;

    double etime;		// time of this queue entry
    Event parent;		// leftist tree pointers
    Event left;
    Event right;
    int distance;		// minimum distance to leaf

    public Event() {
	etime = NO_EVENT;
	parent = null;
	left = null;
	right = null;
	distance = 0;
    }

    // used to determine sorting order of events in queue. Override
    // if a more complicated predicate is needed for subclasses of Event.
    // It's better to return false if possible since that means less work...
    public boolean Before(Event other) {
	return etime < other.etime;
    }

    // adjust distance from leafs all the way up the tree, rearranging
    // children to keep the shortest path on the right branch
    static private void UpdateDistance(Event p) {
	while (p != null) {
	    int ldist = (p.left == null) ? 0 : p.left.distance;
	    int rdist = (p.right == null) ? 0 : p.right.distance;
	    // maintain right child as having minimum distance
	    if (ldist < rdist) {
		Event r = p.left;
		p.left = p.right;
		p.right = r;
		rdist = ldist;
	    }
	    p.distance = 1 + rdist;
	    p = p.parent;
	}
    }

    static private Event MergeWithQueue(Event queue,Event q) {
	if (queue == null) {
	    queue = q;
	    q.parent = null;
	    return queue;
	}

	Event p = queue;
	Event parent = null;

	// merge this Q into tree.  If P has a later time
	// Q will take its place and we'll continue the merge with
	// this event.  Otherwise just keep passing Q down the tree,
	// using the right branch since it's guaranteed to be the
	// shortest.
	while (true) {
	    // Q is earlier, so swap places with current node
	    if (q.Before(p)) {
		Event temp = p;
		p = q;
		q = temp;
		p.parent = parent;
		if (parent == null) queue = p;
		else parent.right = p;
	    }
	    // got to a leaf node so add Q as right child and
	    // then update distances on the way back up
	    if (p.right == null) {
		p.right = q;
		q.parent = p;
		UpdateDistance(p);
		return queue;
	    }
	    // keep descending
	    parent = p;
	    p = p.right;
	}
    }

    public Event RemoveFromQueue(Event queue) {
	etime = NO_EVENT;
	if (parent == null) queue = left;
	else {
	    if (parent.left == this) parent.left = left;
	    else parent.right = left;
	    UpdateDistance(parent);
	}
	if (left != null) left.parent = parent;
	if (right != null) queue = MergeWithQueue(queue,right);
	return queue;
    }

    public Event AddToQueue(Event queue,double eventTime) {
	if (etime != NO_EVENT) queue = RemoveFromQueue(queue);
	etime = eventTime;
	left = null;
	right = null;
	distance = 1;
	return MergeWithQueue(queue,this);
    }
}
