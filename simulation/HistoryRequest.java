// Copyright (C) 1999-2000 Christopher J. Terman - All Rights Reserved.

package simulation;

class HistoryRequest {
    HistoryRequest next;
    int index;
    int bit;
    double tlast;
    double vlast;
    int lv1;
    int lv2;
    boolean firstTime;

    HistoryRequest(int bit,int index) {
	this.index = index;
	this.bit = bit;
	tlast = 0;
	vlast = 0;
	lv1 = 0;
	lv2 = 0;
	firstTime = true;
    }

    static HistoryRequest Insert(HistoryRequest list,HistoryRequest request) {
	HistoryRequest last = null;
	HistoryRequest r;
	for (r = list; r != null; last = r, r = r.next)
	    if (request.index >= r.index) break;
	if (last == null) list = request;
	else last.next = request;
	request.next = r;
	return list;
    }
}

