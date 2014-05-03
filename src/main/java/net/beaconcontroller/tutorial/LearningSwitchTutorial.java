/**
 * Copyright 2011, Stanford University. This file is licensed under GPL v2 plus
 * a special exception, as described in included LICENSE_EXCEPTION.txt.
 */
package net.beaconcontroller.tutorial;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.beaconcontroller.core.IBeaconProvider;
import net.beaconcontroller.core.IOFMessageListener;
import net.beaconcontroller.core.IOFSwitch;
import net.beaconcontroller.core.IOFSwitchListener;
import net.beaconcontroller.packet.Ethernet;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tutorial class used to teach how to build a simple layer 2 learning switch.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu) - 10/14/12
 */
@SuppressWarnings("unused")
public class LearningSwitchTutorial implements IOFMessageListener, IOFSwitchListener {
    protected static Logger log = LoggerFactory.getLogger(LearningSwitchTutorial.class);
    protected IBeaconProvider beaconProvider;
    protected Map<IOFSwitch, Map<Long,Short>> macTables =
        new HashMap<IOFSwitch, Map<Long,Short>>();
    private static final int MAX_FLOW_ENTRIES_PER_PORT =2;
    protected Map<Short,LinkedList<OFMatch>> match_track = new HashMap<Short,LinkedList<OFMatch>>();
    public Command receive(IOFSwitch sw, OFMessage msg) throws IOException {
        initMACTable(sw);
        OFPacketIn pi = (OFPacketIn) msg;
        forwardAsLearningSwitch(sw, pi);
        return Command.CONTINUE;
    }

    /**
     * Floods the packet out all switch ports except the port it
     * came in on.
     *
     * @param sw the OpenFlow switch object
     * @param pi the OpenFlow Packet In object
     * @throws IOException
     */
    public void forwardAsHub(IOFSwitch sw, OFPacketIn pi) throws IOException {
        // Create the OFPacketOut OpenFlow object
        OFPacketOut po = new OFPacketOut();

        // Create an output action to flood the packet, put it in the OFPacketOut
        OFAction action = new OFActionOutput(OFPort.OFPP_FLOOD.getValue());
        po.setActions(Collections.singletonList(action));

        // Set the port the packet originally arrived on
        po.setInPort(pi.getInPort());

        // Reference the packet buffered at the switch by id
        po.setBufferId(pi.getBufferId());
        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            /**
             * The packet was NOT buffered at the switch, therefore we must
             * copy the packet's data from the OFPacketIn to our new
             * OFPacketOut message.
             */
            po.setPacketData(pi.getPacketData());
        }
        // Send the OFPacketOut to the switch
        sw.getOutputStream().write(po);
    }

    /**
     * Learn the source MAC:port pair for each arriving packet, and send packets
     * out the port previously learned for the destination MAC of the packet,
     * if it exists.  Otherwise flood the packet similarly to forwardAsHub.
     * @param sw the OpenFlow switch object
     * @param pi the OpenFlow Packet In object
     * @throws IOException
     */
    public void forwardAsLearningSwitch(IOFSwitch sw, OFPacketIn pi) throws IOException {
        Map<Long,Short> macTable = macTables.get(sw);

        // Build the Match
        OFMatch match = OFMatch.load(pi.getPacketData(), pi.getInPort());
        match = match.setWildcards(OFMatch.OFPFW_DL_SRC)
                .setWildcards(OFMatch.OFPFW_DL_TYPE)
                .setWildcards(OFMatch.OFPFW_NW_PROTO);
        
        // Learn the port to reach the packet's source MAC
        short inPort = pi.getInPort();
        macTable.put(Ethernet.toLong(match.getDataLayerSource()), inPort);
        
        // match_track is HashTable<inPort, List<MacAddress>>
        if(!match_track.containsKey(inPort)) {   
            LinkedList<OFMatch> newMatchList=new LinkedList<OFMatch>();
            match_track.put(pi.getInPort(),newMatchList);
        }
        
        LinkedList<OFMatch> existingMatchList = match_track.get(inPort);
        if(existingMatchList.size() < MAX_FLOW_ENTRIES_PER_PORT) {
            existingMatchList.offer(match);
        } else {
            // remove the oldest one, add new one to the List
            OFMatch obsoleteMatch = existingMatchList.poll();
            existingMatchList.offer(match);
            //Send the remove action to switch
            OFFlowMod deletedFm = new OFFlowMod();
            deletedFm = (OFFlowMod) ((OFFlowMod) sw.getInputStream().getMessageFactory()
                    .getMessage(OFType.FLOW_MOD))
                    .setMatch(obsoleteMatch)
                    .setCommand(OFFlowMod.OFPFC_DELETE_STRICT)
                    .setOutPort(OFPort.OFPP_NONE)
                    .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));
            sw.getOutputStream().write(deletedFm);  
        }

        // Retrieve the port previously learned for the packet's dest MAC
        Short outPort = macTable.get(Ethernet.toLong(match.getDataLayerDestination()));

        if (outPort != null) {
            // Destination port known, push down a flow
            OFFlowMod addedFm = new OFFlowMod();
            addedFm.setBufferId(pi.getBufferId());
            // Use the Flow ADD command
            addedFm.setCommand(OFFlowMod.OFPFC_ADD);
            // Time out the flow after 5 seconds if inactivity
            addedFm.setIdleTimeout((short) 500);
            // Match the packet using the match created above
            addedFm.setMatch(match);
            // Send matching packets to outPort
            OFAction action = new OFActionOutput(outPort);
            addedFm.setActions(Collections.singletonList((OFAction)action));
            // Send this OFFlowMod to the switch
            sw.getOutputStream().write(addedFm);
            System.out.println("switch "+inPort+" learned: " + HexString.toHexString(match.getDataLayerSource()));
  /*          match = new OFMatch().setWildcards(OFMatch.OFPFW_ALL);

            fm = (OFFlowMod) ((OFFlowMod) sw.getInputStream().getMessageFactory()

                .getMessage(OFType.FLOW_MOD))

                .setMatch(match)

                .setCommand(OFFlowMod.OFPFC_DELETE)

                .setOutPort(OFPort.OFPP_NONE)

                .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));

            sw.getOutputStream().write(fm);*/
            //----------------------------------------------------------------
            if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
                /**
                 * EXTRA CREDIT: This is a corner case, the packet was not
                 * buffered at the switch so it must be sent as an OFPacketOut
                 * after sending the OFFlowMod
                 */
                OFPacketOut po = new OFPacketOut();
                action = new OFActionOutput(outPort);
                po.setActions(Collections.singletonList(action));
                po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
                po.setInPort(pi.getInPort());
                po.setPacketData(pi.getPacketData());
                sw.getOutputStream().write(po);
                
            }
        } else {
            // Destination port unknown, flood packet to all ports
            forwardAsHub(sw, pi);
        }
    }

    // ---------- NO NEED TO EDIT ANYTHING BELOW THIS LINE ----------

    /**
     * Ensure there is a MAC to port table per switch
     * @param sw
     */
    private void initMACTable(IOFSwitch sw) {
        Map<Long,Short> macTable = macTables.get(sw);
        if (macTable == null) {
            macTable = new HashMap<Long,Short>();
            macTables.put(sw, macTable);
        }
    }

    @Override
    public void addedSwitch(IOFSwitch sw) {
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        macTables.remove(sw);
    }

    /**
     * @param beaconProvider the beaconProvider to set
     */
    public void setBeaconProvider(IBeaconProvider beaconProvider) {
        this.beaconProvider = beaconProvider;
    }

    public void startUp() {
        log.trace("Starting");
        beaconProvider.addOFMessageListener(OFType.PACKET_IN, this);
        beaconProvider.addOFSwitchListener(this);
    }

    public void shutDown() {
        log.trace("Stopping");
        beaconProvider.removeOFMessageListener(OFType.PACKET_IN, this);
        beaconProvider.removeOFSwitchListener(this);
    }

    public String getName() {
        return "tutorial";
    }
}
