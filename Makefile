define jsim_version
grep "String version" jsim/JSim.java | sed -e "s/\./-/g" -e 's/.*"\(.*\)".*/jsim-\1/'
endef

define jade_version
grep "String version" jade/Jade.java | sed -e "s/\./-/g" -e 's/.*"\(.*\)".*/jade-\1/'
endef

define bsim_version
grep "String version" bsim/BSim.java | sed -e "s/\./-/g" -e 's/.*"\(.*\)".*/bsim-\1/'
endef

define ant_version
grep "String version" fsm/Ant.java | sed -e "s/\./-/g" -e 's/.*"\(.*\)".*/ant-\1/'
endef

define elevator_version
grep "String version" fsm/Elevator.java | sed -e "s/\./-/g" -e 's/.*"\(.*\)".*/elevator-\1/'
endef
      
define tmsim_version
grep "String version" tmsim/TMSim.java | sed -e "s/\./-/g" -e 's/.*"\(.*\)".*/tmsim-\1/'
endef
      
ETAGS = etags
JAVAC = javac -cp . 
JAR = jar

%.class:%.java
	$(JAVAC) $<

ANT =	fsm/Ant.class \
	fsm/AntPanel.class \
	fsm/FSM.class \
	fsm/Maze.class \

ELEVATOR = \
	fsm/Elevator.class \
	fsm/ElevatorPanel.class \
	fsm/ElevatorDiagram.class \
	fsm/FSM.class \
	fsm/Lemming.class

BSIM =  bsim/BSim.class \
	bsim/Beta.class \
	bsim/Macro.class \
	bsim/Memory.class \
	bsim/Program.class \
	bsim/Symbol.class

GUI = 	gui/EditBuffer.class \
	gui/EditCanvas.class \
	gui/EditFrame.class \
	gui/EditPanel.class \
	gui/GuiDialog.class \
	gui/GuiFrame.class \
	gui/GuiObservable.class \
	gui/POSTStream.class \
	gui/ProgressTracker.class \
	gui/Transform.class \
	gui/UI.class \
	gui/UndoList.class

JSIM = 	jsim/JSim.class

BATCHSIM = jsim/BatchSim.class \
	jsim/BatchSimNetlistConsumer.class

VSIM = 	verilog/Identifier.class \
	verilog/Number.class \
	verilog/Token.class \
	verilog/VerilogSource.class \
	verilog/VSim.class

TMSIM = tmsim/TMSim.class \
	tmsim/TMPanel.class \
	tmsim/TMAction.class \
	tmsim/TMTape.class

NETLIST = \
	netlist/Analysis.class \
	netlist/CapacitorPrototype.class \
	netlist/DependentSourcePrototype.class \
	netlist/DevicePrototype.class \
	netlist/Identifier.class \
	netlist/IndependentSourcePrototype.class \
	netlist/InductorPrototype.class \
	netlist/Model.class \
	netlist/MosfetPrototype.class \
	netlist/Netlist.class \
	netlist/NetlistConsumer.class \
	netlist/Node.class \
	netlist/Number.class \
	netlist/Parameter.class \
	netlist/PlotRequest.class \
	netlist/ResistorPrototype.class \
	netlist/Subcircuit.class \
	netlist/SubcircuitCall.class \
	netlist/SubcircuitObject.class \
	netlist/Token.class \
	netlist/VerifyData.class

PLOT =	plot/AnalogPlotCoordinate.class \
	plot/DigitalPlotCoordinate.class \
	plot/Plot.class \
	plot/PlotCanvas.class \
	plot/PlotCoordinate.class \
	plot/PlotData.class

SIMULATION = \
	simulation/FlattenNetwork.class \
	simulation/EmuCapacitor.class \
	simulation/EmuDevice.class \
	simulation/EmuMOSModel.class \
	simulation/EmuMeter.class \
	simulation/EmuMosfet.class \
	simulation/EmuNetwork.class \
	simulation/EmuNode.class \
	simulation/EmuRegion.class \
	simulation/EmuResistor.class \
	simulation/Event.class \
	simulation/HistoryRequest.class \
	simulation/Network.class \
	simulation/Node.class \
	simulation/SimDLatch.class \
	simulation/SimDReg.class \
	simulation/SimDevice.class \
	simulation/SimEvent.class \
	simulation/SimLogicDevice.class \
	simulation/SimLookupTable.class \
	simulation/SimMemory.class \
	simulation/SimNetwork.class \
	simulation/SimNode.class \
	simulation/SimSource.class \
	simulation/SpiceAMSource.class \
	simulation/SpiceCCCS.class \
	simulation/SpiceCCVS.class \
	simulation/SpiceCapacitor.class \
	simulation/SpiceCell.class \
	simulation/SpiceDependentSource.class \
	simulation/SpiceDevice.class \
	simulation/SpiceExpSource.class \
	simulation/SpiceIndependentCurrentSource.class \
	simulation/SpiceIndependentSource.class \
	simulation/SpiceIndependentVoltageSource.class \
	simulation/SpiceInductor.class \
	simulation/SpiceMOSModel.class \
	simulation/SpiceMOSModel_L1.class \
	simulation/SpiceMOSModel_L3.class \
	simulation/SpiceModel.class \
	simulation/SpiceMosfet.class \
	simulation/SpiceMosfetDiode.class \
	simulation/SpiceNetwork.class \
	simulation/SpiceNode.class \
	simulation/SpicePWLSource.class \
	simulation/SpicePulseSource.class \
	simulation/SpiceResistor.class \
	simulation/SpiceSFFMSource.class \
	simulation/SpiceSinSource.class \
	simulation/SpiceSource.class \
	simulation/SpiceStateDevice.class \
	simulation/SpiceVCCS.class \
	simulation/SpiceVCVS.class \
	simulation/TimingInfo.class \
	simulation/TCDComparator.class \
	simulation/TPDComparator.class

all: bsim.jar jsim.jar ant.jar tmsim.jar

bsim.jar: bsim.manifest $(BSIM) $(GUI)
	$(JAR) cfm bsim.jar bsim.manifest bsim/*.class gui/*.class icons/*.gif

jsim.jar: jsim.manifest $(JSIM) $(GUI) $(NETLIST) $(PLOT) $(SIMULATION)
	$(JAR) cfm jsim.jar jsim.manifest jsim/*.class gui/*.class netlist/*.class plot/*.class simulation/*.class icons/*.gif

batchsim.jar: batchsim.manifest $(BATCHSIM) $(GUI) $(NETLIST) $(PLOT) $(SIMULATION)
	$(JAR) cfm batchsim.jar batchsim.manifest jsim/BatchSim.class jsim/BatchSimNetlistConsumer.class gui/*.class netlist/*.class plot/*.class simulation/*.class icons/*.gif

vsim.jar: vsim.manifest $(VSIM) $(GUI) $(PLOT)
	$(JAR) cfm vsim.jar vsim.manifest verilog/*.class gui/*.class plot/*.class icons/*.gif

ant.jar: ant.manifest $(ANT) $(GUI)
	$(JAR) cfm ant.jar ant.manifest fsm/*.class gui/*.class icons/*.gif

tmsim.jar: tmsim.manifest $(TMSIM) $(GUI)
	$(JAR) cfm tmsim.jar tmsim.manifest tmsim/*.class gui/*.class icons/*.gif

elevator.jar: elevator.manifest $(ELEVATOR) $(GUI) fsm/*.gif fsm/*.mid
	$(JAR) cfm elevator.jar elevator.manifest fsm/*.class fsm/*.gif fsm/*.mid gui/*.class icons/*.gif

TAGS::
	$(ETAGS) $(JSIM:.class=.java) $(BSIM:.class=.java) $(ANT:.class=.java) $(ELEVATOR:.class=.java) $(GUI:.class=.java) $(PLOT:.class=.java) $(NETLIST:.class=.java) $(SIMULATION:.class=.java)

archives:: jsim.tgz bsim.tgz ant.tgz tmsim.tgz

jsim.tgz::
	tar cozf archive/`$(jsim_version)`.tgz $(JSIM:.class=.java) $(GUI:.class=.java) $(PLOT:.class=.java) $(NETLIST:.class=.java) $(SIMULATION:.class=.java) icons/*.gif

bsim.tgz::
	tar cozf archive/`$(bsim_version)`.tgz $(BSIM:.class=.java) $(GUI:.class=.java) icons/*.gif

ant.tgz::
	tar cozf archive/`$(ant_version)`.tgz $(ANT:.class=.java) $(GUI:.class=.java) icons/*.gif

elevator.tgz::
	tar cozf archive/`$(elevator_version)`.tgz $(ELEVATOR:.class=.java) $(GUI:.class=.java) icons/*.gif

tmsim.tgz::
	tar cozf archive/`$(tmsim_version)`.tgz $(TMSIM:.class=.java) $(GUI:.class=.java) icons/*.gif

jcad.tgz::
	tar cozf jcad.tgz Makefile *.manifest $(JSIM:.class=.java) $(BSIM:.class=.java) $(ANT:.class=.java) $(ELEVATOR:.class=.java) $(GUI:.class=.java) $(PLOT:.class=.java) $(NETLIST:.class=.java) $(SIMULATION:.class=.java) $(TMSIM:.class=.java) icons/*.gif

clean::
	rm */*.class */*~

rsync::
	rsync -e ssh -avz --delete --exclude="*.class" Makefile *.manifest fsm bsim gui icons jade jlb jsim netlist plot schematic Labs osiris.lcs.mit.edu:/home/6.004/Courseware/jcad
