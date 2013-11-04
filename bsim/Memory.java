// Copyright (C) 2000-2001 Christopher J. Terman - All Rights Reserved.

package bsim;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.border.EtchedBorder;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToolBar;

public class Memory extends JFrame implements ActionListener {
    static public final int MAX_MEMORY_SIZE = 1<<22;

    // replacement strategies
    static public final int LRU = 0;
    static public final int FIFO = 1;
    static public final int RANDOM = 2;
    static public final int CYCLE = 3;

    static public final int readCycleCount = 4;
    static public final int writeCycleCount = 4;

    static public final String[] cache_labels = {
	"off",
	"on"
    };
    static public final String[] lineSize_labels = {
	"1",
	"2",
	"4",
	"8",
	"16",
	"32"
    };
    static public final String[] totalLines_labels = {
	"1",
	"2",
	"4",
	"8",
	"16",
	"32",
	"64",
	"128",
	"256",
	"512",
	"1024",
	"2048",
	"4096",
    };
    static public final String[] nWays_labels = {
	"direct mapped",
	"2-way",
	"4-way",
	"8-way",
	"fully associative"
    };
    static public final String[] replacementStrategy_labels = {
	"LRU",
	"FIFO",
	"Random",
	"Cycle"
    };
    static public final String[] writeBack_labels = {
	"write-through",
	"write-back"
    };

    int[] memory;		// memory data
    int memMask;		// memory address mask

    // cache parameters
    boolean cache;		// is cache on?
    int lineSize;		// number of words/line (must be 2**N)
    int totalLines;		// total number of lines in the entire cache
    int nWays;			// number of lines/set
    int replacementStrategy;	// how to choose replacement line on miss
    boolean writeBack;		// use write back instead of write thru?
    int rWay;			// select which subcache will get replacement

    int nLines;			// number of lines in each subcache
    int lineShift;		// shift/mask info to retrieve line #
    int lineMask;
    int tagShift;		// shift/mask info to retrieve tag
    int tagMask;

    // cache state;
    boolean[] dirty;		// dirty bit for each line
    boolean[] valid;		// valid bit for each line
    int[] tag;			// tag for each line
    long[] age;			// pseudo-time since last use
    Random random;		// random number generator

    // cache statistics
    long cycles;
    long readHits;
    long readMisses;
    long writeHits;
    long writeMisses;
    long dirtyReplacements;
    long validReplacements;
    long totalReplacements;

    Beta parent;			// who we belong to
    JToolBar bbar;			// tool container

    JComboBox ctl_cache;		// controls
    JComboBox ctl_lineSize;
    JComboBox ctl_totalLines;
    JComboBox ctl_nWays;
    JComboBox ctl_replacementStrategy;
    JComboBox ctl_writeBack;

    // statistics
    int toffset;			// header lines don't work?
    JTable stat_address;
    JTable stat_perf;
    JTable stat_cost;

    JTextArea message;

    public Memory(Beta parent) {
	super("Cache information");
	this.parent = parent;

	// initialize state
	SetMemorySize(2);
	cache = false;
	lineSize = 1;
	totalLines = 1;
	nWays = 1;
	replacementStrategy = LRU;
	writeBack = false;
	random = new Random();

	Reset(null);

	GridBagLayout glayout = new GridBagLayout();
	GridBagConstraints gc = new GridBagConstraints();

	// set up controls
	JPanel ctlPanel = new JPanel(glayout);
	ctlPanel.setBorder(BorderFactory.createTitledBorder("Cache parameters"));
	ctl_cache = SetupControl(ctlPanel,glayout,gc,"Cache",cache_labels);
	ctl_lineSize = SetupControl(ctlPanel,glayout,gc,"Words/line",lineSize_labels);
	ctl_totalLines = SetupControl(ctlPanel,glayout,gc,"Total lines",totalLines_labels);
	ctl_nWays = SetupControl(ctlPanel,glayout,gc,"Associativity",nWays_labels);
	ctl_replacementStrategy = SetupControl(ctlPanel,glayout,gc,"Replacement strategy",replacementStrategy_labels);
	ctl_writeBack = SetupControl(ctlPanel,glayout,gc,"Write strategy",writeBack_labels);

	// set up statistics
	glayout = new GridBagLayout();
	JPanel statsPanel = new JPanel(glayout);
	statsPanel.setBorder(BorderFactory.createTitledBorder("Cache statistics"));

	toffset = 1;
	String[] address_labels = { "field", "# of bits" };
	Object[][] address_data = {
	    { "field", "# of bits" },
	    { "tag", "" },
	    { "cache index", "" },
	    { "data select", "" }
	};
	stat_address = new JTable(address_data,address_labels);
	SetupReadout(statsPanel,glayout,gc,"Address",stat_address);

	String[] cost_labels = { "item", "#", "cost" };
	Object[][] cost_data = {
	    { "item", "#", "cost" },
	    { "SRAM", "", "" },
	    { "comparator bits", "", "" },
	    { "2-to-1 mux bits", "", "" },
	    { "TOTAL COST", "", "" },
	};
	stat_cost = new JTable(cost_data,cost_labels);
	stat_cost.getColumnModel().getColumn(0).setPreferredWidth(100);
	SetupReadout(statsPanel,glayout,gc,"Cost",stat_cost);

	String[] perf_labels = { "event", "read", "write", "total" };
	Object[][] perf_data = {
	    { "event", "read", "write", "total" },
	    { "hits", "", "", "" },
	    { "misses", "", "", "" },
	    { "total", "", "", "" },
	    { "hit %", "", "", "" },
	    { "cycles", "", "", "" },
	};
	stat_perf = new JTable(perf_data,perf_labels);
	SetupReadout(statsPanel,glayout,gc,"Performance",stat_perf);

	ProcessCacheParameters();
	Container contentPane = getContentPane();
	//contentPane.setLayout(new BoxLayout(contentPane,BoxLayout.X_AXIS));
	//contentPane.add(ctlPanel);
	//contentPane.add(statsPanel);

	message = new JTextArea(2,10);
	message.setEditable(false);
	message.setHighlighter(null);
	message.setBackground(Color.white);
	message.setBorder(BorderFactory.createLoweredBevelBorder());

	contentPane.setLayout(new BorderLayout());
	bbar = new JToolBar();
	bbar.putClientProperty("JToolBar.isRollover",Boolean.TRUE);
	contentPane.add(bbar,BorderLayout.NORTH);
	contentPane.add(ctlPanel,BorderLayout.WEST);
	contentPane.add(statsPanel,BorderLayout.CENTER);
	contentPane.add(message,BorderLayout.SOUTH);

	pack();
	Dimension d = getPreferredSize();
	setSize(d.width,d.height+10);	// tables are cramped otherwise
    }

    private JComboBox SetupControl(JPanel p,GridBagLayout glayout,GridBagConstraints gc,String label,String[] values) {
	JLabel l = new JLabel(label+":");
	gc.weightx = 0.0;
	gc.insets = new Insets(2,2,2,2);
	gc.anchor = GridBagConstraints.EAST;
	gc.fill = GridBagConstraints.NONE;
	gc.gridwidth = 1;
	glayout.setConstraints(l,gc);
	p.add(l);

	JComboBox item = new JComboBox(values);
	item.addActionListener(this);
	gc.weightx = 1.0;
	gc.anchor = GridBagConstraints.WEST;
	gc.fill = GridBagConstraints.HORIZONTAL;
	gc.gridwidth = GridBagConstraints.REMAINDER;
	glayout.setConstraints(item,gc);
	p.add(item);

	return item;
    }

    private void SetupReadout(JPanel p,GridBagLayout glayout,GridBagConstraints gc,String label,JComponent readout) {
	JLabel l = new JLabel(label+":");
	gc.weightx = 0.0;
	gc.weighty = 1.0;
	gc.ipady = 0;
	gc.insets = new Insets(2,2,2,2);
	gc.anchor = GridBagConstraints.EAST;
	gc.fill = GridBagConstraints.NONE;
	gc.gridwidth = 1;
	glayout.setConstraints(l,gc);
	p.add(l);

	readout.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED,Color.white,Color.black));
	gc.ipady = 4;
	gc.weightx = 1.0;
	gc.fill = GridBagConstraints.HORIZONTAL;
	gc.anchor = GridBagConstraints.WEST;
	gc.gridwidth = GridBagConstraints.REMAINDER;
	glayout.setConstraints(readout,gc);
	p.add(readout);
    }

    public JButton AddToolButton(JButton button,String action,ActionListener al) {
	button.setToolTipText(action);
	button.setActionCommand(action);
	button.addActionListener(al);
	bbar.add(button);
	return button;
    }

    public void AddToolSeparator() {
	bbar.addSeparator();
    }

    public void actionPerformed(ActionEvent event) {
	Object source = event.getSource();

	if (source == ctl_cache) {
	    cache = ctl_cache.getSelectedIndex() == 1;
	} else if (source == ctl_lineSize) {
	    try {
		lineSize = Integer.parseInt((String)ctl_lineSize.getSelectedItem());
	    }
	    catch (NumberFormatException e) {
	    }
	} else if (source == ctl_totalLines) {
	    try {
		totalLines = Integer.parseInt((String)ctl_totalLines.getSelectedItem());
		// if cache is fully associative, update nWays
		if (ctl_nWays.getSelectedIndex() == 4) nWays = totalLines;
	    }
	    catch (NumberFormatException e) {
	    }
	} else if (source == ctl_nWays) {
	    switch (ctl_nWays.getSelectedIndex()) {
	    case 0:	nWays = 1; break;
	    case 1:	nWays = 2; break;
	    case 2:	nWays = 4; break;
	    case 3:	nWays = 8; break;
	    case 4:	nWays = totalLines; break;
	    }
        } else if (source == ctl_replacementStrategy) {
	    replacementStrategy = ctl_replacementStrategy.getSelectedIndex();
	} else if (source == ctl_writeBack) {
	    writeBack = ctl_writeBack.getSelectedIndex() == 1;
	} else return;

	ProcessCacheParameters();
    }

    // number of bits needed to represent N choices
    static public int Log2(int n) {
	int log = 0;
	int v = 1;

	while (log < 32) {
	    if (v >= n) break;
	    v <<= 1;
	    log += 1;
	}
	return log;
    }
	    
    static public int Mask(int n) {
	int log = Log2(n);
	if (log == 32) return 0xFFFFFFFF;
	else return (1 << log) - 1;
    }

    public int SetMemorySize(int nwords) {
	// look for a memory size that's a power of two
	int s = 2;
	while (s < MAX_MEMORY_SIZE && s < nwords) s *= 2;

	memory = new int[s];
	memMask = Mask(s-1);
	return s;
    }

    public int Size() {
	return memory.length;
    }

    public boolean ValidAddress(int addr) {
	return addr < (memory.length << 2);
    }

    public int Index(int addr) {
	return (addr >>> 2) & memMask;
    }

    public int[] Copy() {
	int[] copy = new int[memory.length];
	for (int i = 0; i < memory.length; i += 1) copy[i] = memory[i];
	return copy;
    }

    public void Reset(int[] initialValues) {
	for (int i = 0; i < memory.length; i += 1) memory[i] = 0;

	if (initialValues != null) {
	    int max = Math.max(initialValues.length,memory.length);
	    for (int i = 0; i < max; i += 1) memory[i] = initialValues[i];
	}

	CacheReset();
    }

    public int WriteWord(int addr,int value) {
	int index = Index(addr);
	if (index < memory.length) {
	    memory[index] = value;
	    return index;
	} else return -1;
    }

    public int WriteByte(int addr,int value) {
	int index = Index(addr);
	if (index < memory.length) {
	    value &= 0xFF;
	    switch (addr & 3) {
	    case 0:
		memory[index] = (memory[index] & 0xFFFFFF00) | value;
		break;
	    case 1:
		memory[index] = (memory[index] & 0xFFFF00FF) | (value << 8);
		break;
	    case 2:
		memory[index] = (memory[index] & 0xFF00FFFF) | (value << 16);
		break;
	    case 3:
		memory[index] = (memory[index] & 0x00FFFFFF) | (value << 24);
		break;
	    }
	    return index;
	} else return -1;
    }

    public int ReadWord(int addr) {
	int index = Index(addr);
	return (index < memory.length) ? memory[index] : 0;
    }

    public int ReadByte(int addr) {
	return (ReadWord(addr) >> (8 * (addr & 0x3))) & 0xFF;
    }

    private String Percent(long numerator,long denominator) {
	long v = (1000*numerator)/denominator;
	return (v/10)+"."+(v%10)+"%";
    }

    public void Repaint() {
	if (stat_perf != null) {
	    // update performance info
	    long reads = readHits + readMisses;
	    long writes = writeHits + writeMisses;
	    long total = reads + writes;
	    long hits = readHits + writeHits;
	    long misses = readMisses + writeMisses;
	    stat_perf.setValueAt(new Long(readHits),toffset+0,1);
	    stat_perf.setValueAt(new Long(writeHits),toffset+0,2);
	    stat_perf.setValueAt(new Long(hits),toffset+0,3);
	    stat_perf.setValueAt(new Long(readMisses),toffset+1,1);
	    stat_perf.setValueAt(new Long(writeMisses),toffset+1,2);
	    stat_perf.setValueAt(new Long(misses),toffset+1,3);
	    stat_perf.setValueAt(new Long(reads),toffset+2,1);
	    stat_perf.setValueAt(new Long(writes),toffset+2,2);
	    stat_perf.setValueAt(new Long(total),toffset+2,3);
	    stat_perf.setValueAt(reads > 0 ? Percent(readHits,reads) : "",toffset+3,1);
	    stat_perf.setValueAt(writes > 0 ? Percent(writeHits,writes) : "",toffset+3,2);
	    stat_perf.setValueAt(total > 0 ? Percent(hits,total) : "",toffset+3,3);
	    stat_perf.setValueAt(new Long(cycles),toffset+4,3);
	}

	// batch repaints...
	repaint(100);
    }

    private void Replace(int addr,int aline,int atag,boolean makeDirty) {
	if (nWays > 1) {
	    switch (replacementStrategy) {
	    case LRU:
	    case FIFO:
		{   long oldest = age[aline];
		    int index = aline + nLines;
		    rWay = 0;
		    for (int way = 1; way < nWays; way += 1) {
			if (age[index] < oldest) {
			    rWay = way;
			    oldest = age[index];
			}
			index += nLines;
		    }
		}
		break;
	    case RANDOM:
		rWay = random.nextInt(nWays);
		break;
	    case CYCLE:
		rWay = (rWay + 1) % nWays;
		break;
	    }
	}

	// fill in correct line in chosen subcache
	aline += rWay * nLines;

	// update statistics
	totalReplacements += 1;
	if (valid[aline]) {
	    validReplacements += 1;
	    // writeback line if dirty
	    if (dirty[aline]) {
		dirty[aline] = false;
		dirtyReplacements += 1;
		cycles += writeCycleCount + lineSize - 1;
	    }
	}

	if (parent.simulation == null)
	    Message("addr=0x"+Integer.toHexString(addr)+": MISS, replace entry @ way="+rWay+", line="+(aline-rWay*nLines)+", newtag=0x"+Integer.toHexString(atag));

	// refill line with new data
	valid[aline] = true;
	dirty[aline] = makeDirty;
	tag[aline] = atag;
	cycles += readCycleCount + lineSize - 1;
	age[aline] = cycles;
    }

    public int CachedWriteWord(int addr,int value) {
	int windex = WriteWord(addr,value);

	if (cache && windex >= 0) {
	    cycles += 1;	// cache lookup takes one cycle

	    // check the appropriate line of each subcache
	    int aline = (addr >> lineShift) & lineMask;
	    int atag = (addr >> tagShift) & tagMask;
	    int index = aline;
	    for (int way = 0; way < nWays; way += 1) {
		if (valid[index] && tag[index] == atag) {
		    // hit!
		    writeHits += 1;
		    if (writeBack) dirty[index] = true;
		    else cycles += writeCycleCount;
		    if (replacementStrategy == LRU) age[index] = cycles;

		    if (parent.simulation == null)
			Message("addr=0x"+Integer.toHexString(addr)+": HIT @ way="+way+", line="+aline);

		    return windex;
		}
		index += nLines;
	    }

	    // miss -- select replacement and refill
	    Replace(addr,aline,atag,writeBack);

	    // write-through caches also write word to memory
	    if (!writeBack) cycles += writeCycleCount;
	} else cycles += writeCycleCount;

	writeMisses += 1;
	return windex;
    }

    public int CachedReadWord(int addr) {
	int data = ReadWord(addr);

	if (cache) {
	    cycles += 1;	// cache lookup takes one cycle

	    // check the appropriate line of each subcache
	    int aline = (addr >> lineShift) & lineMask;
	    int atag = (addr >> tagShift) & tagMask;
	    int index = aline;
	    for (int way = 0; way < nWays; way += 1) {
		if (valid[index] && tag[index] == atag) {
		    // hit!
		    readHits += 1;
		    if (replacementStrategy == LRU) age[index] = cycles;

		    if (parent.simulation == null)
			Message("addr=0x"+Integer.toHexString(addr)+": HIT @ way="+way+", line="+aline);

		    return data;
		}
		index += nLines;
	    }

	    // miss -- select replacement and refill
	    Replace(addr,aline,atag,false);
	} else cycles += readCycleCount;

	readMisses += 1;
	return data;
    }

    public void Message(String msg) {
	if (message != null) {
	    if (msg.length() == 0) message.setText(msg);
	    else {
		String m = message.getText();
		if (m.length() == 0) m = msg;
		else m += "\n" + msg;
		message.setText(m);
	    }
	}
    }

    public void CacheReset() {
	cycles = 0;
	readMisses = 0;
	writeMisses = 0;
	readHits = 0;
	writeHits = 0;
	dirtyReplacements = 0;
	validReplacements = 0;
	totalReplacements = 0;
	random.setSeed(0);		// restart pseudorandom sequence
	rWay = 0;			// reset replacement pointer
	Message("");

	if (cache) {
	    int max = dirty.length;
	    for (int i = 0; i < max; i += 1) {
		dirty[i] = false;
		valid[i] = false;
		tag[i] = 0;
		age[i] = 0;
	    }
	}
	Repaint();
    }

    public void SetCacheParameters(boolean cache,int replacementStrategy,int lineSize,int nWays,int totalLines,boolean writeBack) {
	this.cache = cache;
	this.replacementStrategy = replacementStrategy;
	this.lineSize = lineSize;
	this.nWays = nWays;
	this.totalLines = totalLines;
	this.writeBack = writeBack;

	ProcessCacheParameters();
    }

    private void ProcessCacheParameters() {
	if (cache) {
	    dirty = new boolean[totalLines];
	    valid = new boolean[totalLines];
	    tag = new int[totalLines];
	    age = new long[totalLines];

	    nLines = totalLines / nWays;
	    lineShift = Log2(lineSize)+2;
	    lineMask = Mask(nLines);
	    tagShift = lineShift + Log2(nLines);
	    tagMask = (1 << (32 - tagShift)) - 1;

	    //System.out.println("nWays="+nWays+" nLines="+nLines+" lineShift="+lineShift+" lineMask=0x"+Integer.toHexString(lineMask)+" tagShift="+tagShift+" tagMask=0x"+Integer.toHexString(tagMask));

	    int ntagbits = 32 - tagShift;
	    stat_address.setValueAt(new Integer(ntagbits),toffset+0,1);
	    stat_address.setValueAt(new Integer(tagShift - lineShift),toffset+1,1);
	    stat_address.setValueAt(new Integer(lineShift),toffset+2,1);

	    int nbits = 32*lineSize + ntagbits + 1 + (writeBack ? 1 : 0);
	    int cost_sram = nLines == 1 ? totalLines*nbits*50 :   // registers
		totalLines*nbits*6 +		// ram bits
		(tagShift - lineShift)*20 +	// address buffers
		nLines*20 +			// address decode for each row
		nbits*nWays*30;			// sense amp + output drivers
	    int ncomparators = nWays*(32 - tagShift);
	    int cost_comparators = ncomparators * 20;
	    int nmuxes = nWays*32*(lineSize - 1);	// tree of 2:1 32-bit muxes
	    int cost_muxes = nmuxes * 8;
	    stat_cost.setValueAt(nLines > 1 ? "SRAM" : "Register bits",toffset+0,0);
	    stat_cost.setValueAt(nLines > 1 ? nLines+"x"+(nWays*nbits) : Integer.toString(nbits),toffset+0,1);
	    stat_cost.setValueAt(new Integer(cost_sram),toffset+0,2);
	    stat_cost.setValueAt(new Integer(ncomparators),toffset+1,1);
	    stat_cost.setValueAt(new Integer(cost_comparators),toffset+1,2);
	    stat_cost.setValueAt(new Integer(nmuxes),toffset+2,1);
	    stat_cost.setValueAt(new Integer(cost_muxes),toffset+2,2);
	    stat_cost.setValueAt(new Integer(cost_sram + cost_comparators + cost_muxes),toffset+3,2);

	    ctl_lineSize.setEnabled(true);
	    ctl_totalLines.setEnabled(true);
	    ctl_nWays.setEnabled(true);
	    ctl_replacementStrategy.setEnabled(nWays > 1);
	    ctl_writeBack.setEnabled(true);
	} else {
	    ctl_lineSize.setEnabled(false);
	    ctl_totalLines.setEnabled(false);
	    ctl_nWays.setEnabled(false);
	    ctl_replacementStrategy.setEnabled(false);
	    ctl_writeBack.setEnabled(false);
	}

	CacheReset();
    }
}
