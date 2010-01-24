// ----------------------------------------------------------------------------
// Copyright 2006-2009, GeoTelematic Solutions, Inc.
// All rights reserved
// ----------------------------------------------------------------------------
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
// http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// ----------------------------------------------------------------------------
// Description:
//  Device Communication Server configuration (central registry for port usage)
// ----------------------------------------------------------------------------
// Change History:
//  2009/04/02  Martin D. Flynn
//     -Initial release
//  2009/07/01  Martin D. Flynn
//     -Added support for sending commands to the appropriate DCS.
//  2009/08/23  Martin D. Flynn
//     -Added several additional common runtime property methods.
//  2009/09/23  Martin D. Flynn
//     -Changed 'getSimulateDigitalInputs' to return a mask
// ----------------------------------------------------------------------------
package org.opengts.db;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;

import org.opengts.db.tables.*;

public class DCServerConfig
{

    // ------------------------------------------------------------------------
    // flags

    public static final long    F_NONE                          = 0x00000000L;
    public static final long    F_VEHICLE_MOUNT                 = 0x00000001L;
    public static final long    F_HAS_INPUTS                    = 0x00000002L;
    public static final long    F_HAS_OUTPUTS                   = 0x00000004L;
    public static final long    F_PING_SMS                      = 0x00000100L;
    public static final long    F_PING_UDP                      = 0x00000200L;
    public static final long    F_PING_TCP                      = 0x00000400L;
    public static final long    F_TCP                           = 0x00001000L;
    public static final long    F_UDP                           = 0x00002000L;
    public static final long    F_SMTP                          = 0x00004000L;
    public static final long    F_SAT                           = 0x00008000L;
    
    public static final long    F_STD_VEHICLE                   = F_VEHICLE_MOUNT | F_HAS_INPUTS | F_HAS_OUTPUTS | F_TCP | F_UDP;
    public static final long    F_STD_PERSONAL                  = F_TCP | F_UDP;

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    public enum CommandProtocol implements EnumTools.IsDefault, EnumTools.IntValue {
        UDP(0,"udp"),
        TCP(1,"tcp"),
        SMS(2,"sms");
        // ---
        private int         vv = 0;
        private String      ss = "";
        CommandProtocol(int v, String s) { vv = v; ss = s; }
        public int getIntValue()   { return vv; }
        public String toString()   { return ss; }
        public boolean isDefault() { return this.equals(UDP); }
    };

    /* get the CommandProtocol Enum valud, based on the value of the specified String */
    public static CommandProtocol getCommandProtocol(String v)
    {
        // returns 'null' if protocol value is invalid
        return EnumTools.getValueOf(CommandProtocol.class, v);
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Device event code to status code translation
    **/
    public static class EventCode
    {
        private int    oldCode     = 0;
        private int    statusCode  = StatusCodes.STATUS_NONE;
        private String dataString  = null;
        private long   dataLong    = Long.MIN_VALUE;
        public EventCode(int oldCode, int statusCode, String data) {
            this.oldCode    = oldCode;
            this.statusCode = statusCode;
            this.dataString = data;
            this.dataLong   = StringTools.parseLong(data, Long.MIN_VALUE);
        }
        public int getCode() {
            return this.oldCode;
        }
        public int getStatusCode() {
            return this.statusCode;
        }
        public String getDataString(String dft) {
            return !StringTools.isBlank(this.dataString)? this.dataString : dft;
        }
        public long getDataLong(long dft) {
            return (this.dataLong != Long.MIN_VALUE)? this.dataLong : dft;
        }
        public String toString() {
            return StringTools.toHexString(this.getCode(),16) + " ==> 0x" + StringTools.toHexString(this.getStatusCode(),16);
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    public static final String  COMMAND_CONFIG                  = "config";     // arg=deviceCommandString
    public static final String  COMMAND_PING                    = "ping";       // arg=commandID
  //public static final String  COMMAND_OUTPUT                  = "output";     // arg=gpioOutputState
    public static final String  COMMAND_GEOZONE                 = "geozone";    // arg=""
    
    public static final String  ARG_NAME                        = "arg";

    public class Command
    {
        private String          name      = null;
        private String          desc      = null;
        private String          types[]   = null;
        private String          aclName   = null;
        private String          cmdStr    = null;
        private boolean         hasArgs   = false;
        private String          cmdProto  = null;
        private boolean         expectAck = false;
        private int             cmdStCode = StatusCodes.STATUS_NONE;
        private OrderedMap<String,I18N.Text> argList = null;
        public Command(
            String name, String desc, 
            String types[], String aclName, 
            String cmdStr, boolean hasArgs,
            String cmdProto, boolean expectAck,
            int cmdStCode) {
            this.name      = StringTools.trim(name);
            this.desc      = StringTools.trim(desc);
            this.types     = (types != null)? types : new String[0];
            this.aclName   = StringTools.trim(aclName);
            this.cmdStr    = (cmdStr != null)? cmdStr : "";
            this.hasArgs   = hasArgs || (this.cmdStr.indexOf("${") >= 0);
            this.cmdProto  = StringTools.trim(cmdProto);
            this.expectAck = expectAck;
            this.cmdStCode = (cmdStCode > 0)? cmdStCode : StatusCodes.STATUS_NONE;
        }
        public DCServerConfig getDCServerConfig() {
            return DCServerConfig.this;
        }
        public String getName() {
            return this.name;
        }
        public String getDescription() {
            return this.desc;
        }
        public String[] getTypes() {
            return this.types;
        }
        public boolean isType(String type) {
            return ListTools.contains(this.types, type);
        }
        public String getAclName() {
            return this.aclName;
        }
        public String getCommandString() {
            return this.cmdStr;
        }
        public String getCommandString(String cmdArgs[]) {
            String cs = this.getCommandString();
            if (this.hasCommandArgs()) {
                final String args[] = (cmdArgs != null)? cmdArgs : new String[0];
                return StringTools.replaceKeys(cs, new StringTools.KeyValueMap() {
                    public String getKeyValue(String key, String arg) {
                        if (key.equals(ARG_NAME)) { 
                            return ((args.length > 0) && (args[0] != null))? args[0] : ""; 
                        }
                        for (int i = 0; i < 4; i++) {
                            if (key.equals(ARG_NAME+i)) { 
                                return ((args.length > i) && (args[i] != null))? args[i] : ""; 
                            }
                        }
                        return "";
                    }
                });
            }
            return cs;
        }
        public boolean hasCommandArgs() {
            //return (this.cmdStr != null)? (this.cmdStr.indexOf("${arg") >= 0) : false;
            return this.hasArgs;
        }
        public int getArgCount() {
            return this.hasArgs? 1 : 0;
        }
        public String getArgDescription(int argNdx) {
            return (argNdx > 0)? ("Arg"+argNdx) : "";
        }
        public CommandProtocol getCommandProtocol() {
            return this.getCommandProtocol(null);
        }
        public CommandProtocol getCommandProtocol(CommandProtocol dftProto) {
            if (!StringTools.isBlank(this.cmdProto)) {
                // may return 'null' if 'cmdProto' is invalid
                return DCServerConfig.getCommandProtocol(this.cmdProto);
            } else {
                return dftProto;
            }
        }
        public boolean getExpectAck() {
            return this.expectAck;
        }
        public int getStatusCode() {
            return this.cmdStCode;
        }
    }

    public static class CommandArg
    { // static because the 'Args' are initialized before the 'Command' is
        private Command command   = null;
        private String  name      = null;
        private String  desc      = null;
        private boolean save      = false;
        public CommandArg(String name, String desc, boolean save) {
            this.name    = StringTools.trim(name);
            this.desc    = StringTools.trim(desc);
            this.save    = save;
        }
        public String getName() {
            return this.name;
        }
        public String getDescription() {
            return this.desc;
        }
        public boolean save() {
            return this.save;
        }
        public void setCommand(Command cmd) {
            this.command = cmd;
        }
        public Command getCommand() {
            return this.command;
        }
        public String getResourceName() {
            Command cmd = this.getCommand();
            if (this.save() && (cmd != null)) {
                // ie. "DCServerConfig.enfora.DriverMessage.arg"
                StringBuffer sb = new StringBuffer();
                sb.append("DCServerConfig.");
                sb.append(cmd.getDCServerConfig().getName());
                sb.append(".");
                sb.append(cmd.getName());
                sb.append(".");
                sb.append(this.getName());
                return sb.toString();
            } else {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private String                  dcName              = "";
    private String                  dcDesc              = "";

    private String                  uniquePrefix[]      = null;

    private int                     tcpPorts[]          = null;
    private int                     udpPorts[]          = null;

    private boolean                 customCodeEnabled   = true;
    private Map<Integer,EventCode>  customCodeMap       = new HashMap<Integer,EventCode>();

    private String                  commandHost         = null;
    private int                     commandPort         = 0;
    private CommandProtocol         commandProtocol     = null;

    private long                    flags               = F_NONE;

    private RTProperties            rtProps             = new RTProperties();
    
    private String                  commandsAclName     = null;
    
    private OrderedMap<String,Command> commandMap       = null;
    
    /**
    *** Blank Constructor
    **/
    public DCServerConfig()
    {
        this.setName("unregistered");
        this.setDescription("Unregistered DCS");
        this.flags = F_NONE;
        this.setTcpPorts(null);
        this.setUdpPorts(null);
        this.setCommandDispatcherPort(0);
        this.setUniquePrefix(null);
    }

    /**
    *** Constructor
    **/
    public DCServerConfig(String name, String desc, int tcpPorts[], int udpPorts[], int commandPort, long flags, String... uniqPfx)
    {
        this.setName(name);
        this.setDescription(desc);
        this.flags = flags;
        this.setTcpPorts(tcpPorts, true);
        this.setUdpPorts(udpPorts, true);
        this.setCommandDispatcherPort(commandPort, true);
        this.setUniquePrefix(uniqPfx);
        //Print.logInfo("Name="+name+ " TCP=" + StringTools.join(tcpPorts,","));
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the server name/id
    **/
    protected void setName(String n)
    {
        this.dcName = StringTools.trim(n);
    }

    /**
    *** Gets the server name/id
    **/
    public String getName()
    {
        return this.dcName;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the server description
    **/
    public void setDescription(String d)
    {
        this.dcDesc = StringTools.trim(d);
    }

    /**
    *** Gets the server description
    **/
    public String getDescription()
    {
        return this.dcDesc;
    }
    
    // ------------------------------------------------------------------------

    /**
    *** Gets an array of server ports from the specified runtime keys.
    *** (first check command-line, then config file, then default) 
    *** @param name The server name
    *** @param argKey  The runtime key names
    *** @param dft  The default array of server ports if not defined otherwise
    *** @return The array of server ports
    **/
    private int[] getServerPorts(String argKey[], int dft[])
    {
        String name = this.getName();
        String ak[] = this.normalizeKeys(argKey);

        /* no port override? */
        if (!this.hasProperty(ak,false)) { // exclude 'defaults'
            return dft;
        }

        /* get overridden ports */
        String portStr[] = this.getStringArrayProperty(ak, null);
        if (ListTools.isEmpty(portStr)) {
            // ports explicitly removed
            //Print.logInfo(name + ": Returning 'null' ports");
            return null;
        }

        /* parse/return port numbers */
        int p = 0;
        int srvPorts[] = new int[portStr.length];
        for (int i = 0; i < portStr.length; i++) {
            int port = StringTools.parseInt(portStr[i], 0);
            if (ServerSocketThread.isValidPort(port)) {
                srvPorts[p++] = port;
            }
        }
        if (p < srvPorts.length) {
            // list contains invalid port numbers
            int newPorts[] = new int[p];
            System.arraycopy(srvPorts, 0, newPorts, 0, p);
            srvPorts = newPorts;
        }
        if (!ListTools.isEmpty(srvPorts)) {
            //Print.logInfo(name + ": Returning server ports: " + StringTools.join(srvPorts,","));
            return srvPorts;
        } else {
            //Print.logInfo(name + ": Returning 'null' ports");
            return null;
        }

    }

    /**
    *** Sets the default TCP port for this server
    **/
    public void setTcpPorts(int tcp[], boolean checkRTP)
    {
        if (checkRTP) {
            tcp = this.getServerPorts(DCServerFactory.CONFIG_TCP_PORT(this.getName()), tcp);
        }
        this.tcpPorts = !ListTools.isEmpty(tcp)? tcp : null;
    }

    /**
    *** Sets the default TCP port for this server
    **/
    public void setTcpPorts(int tcp[])
    {
        this.setTcpPorts(tcp, false);
    }

    /**
    *** Gets the default TCP port for this server
    **/
    public int[] getTcpPorts()
    {
        return this.tcpPorts;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the default UDP port for this server
    **/
    public void setUdpPorts(int udp[], boolean checkRTP)
    {
        if (checkRTP) {
            udp = this.getServerPorts(DCServerFactory.CONFIG_UDP_PORT(this.getName()), udp);
        }
        this.udpPorts = !ListTools.isEmpty(udp)? udp : null;
    }

    /**
    *** Sets the default UDP port for this server
    **/
    public void setUdpPorts(int udp[])
    {
        this.setUdpPorts(udp, false);
    }

    /**
    *** Gets the default UDP port for this server
    **/
    public int[] getUdpPorts()
    {
        return this.udpPorts;
    }

    // ------------------------------------------------------------------------

    /**
    *** Returns an array of all TCP/UDP 'listen' ports
    *** @return An array of all TCP/UDP 'listen' ports
    **/
    public int[] getListenPorts()
    {
        if (ListTools.isEmpty(this.tcpPorts)) {
            return this.udpPorts; // may still be null/empty
        } else
        if (ListTools.isEmpty(this.udpPorts)) {
            return this.tcpPorts; // may still be null/empty
        } else {
            java.util.List<Integer> portList = new Vector<Integer>();
            for (int t = 0; t < this.tcpPorts.length; t++) {
                Integer tcp = new Integer(this.tcpPorts[t]);
                portList.add(tcp); 
            }
            for (int u = 0; u < this.udpPorts.length; u++) {
                Integer udp = new Integer(this.udpPorts[u]);
                if (!portList.contains(udp)) {
                    portList.add(udp);
                }
            }
            int ports[] = new int[portList.size()];
            for (int p = 0; p < portList.size(); p++) {
                ports[p] = portList.get(p).intValue();
            }
            return ports;
        }
        
    }

    // ------------------------------------------------------------------------

    /**
    *** Load device record from unique-id (not yet used/tested)
    *** @param modemID The unique modem ID (IMEI, ESN, etc)
    *** @return The Device record
    **/
    public Device loadDeviceUniqueID(String modemID)
    {
        return DCServerFactory.loadDeviceUniqueID(this.getUniquePrefix(), modemID);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Returns true if the named server is defined
    **/
    public boolean serverJarExists()
    {
        return DCServerFactory.serverJarExists(this.getName());
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Sets the command protocol to use when communicating with remote devices
    *** @param proto  The CommandProtocol
    **/
    public void setCommandProtocol(String proto)
    {
        this.commandProtocol = DCServerConfig.getCommandProtocol(proto);
    }

    /**
    *** Sets the command protocol to use when communicating with remote devices
    *** @param proto  The CommandProtocol
    **/
    public void setCommandProtocol(CommandProtocol proto)
    {
        this.commandProtocol = proto;
    }

    /**
    *** Gets the command protocol to use when communicating with remote devices
    *** @return The Command Protocol
    **/
    public CommandProtocol getCommandProtocol()
    {
        return (this.commandProtocol != null)? this.commandProtocol : CommandProtocol.UDP;
    }

    /**
    *** Gets the "Client Command Port" 
    *** @param dft  The default Client Command Port
    *** @return The Client Command Port
    **/
    public int getClientCommandPort_udp(int dft)
    {
        return this.getIntProperty(DCServerFactory.CONFIG_CLIENT_COMMAND_PORT_UDP(this.getName()), dft);
    }

    /**
    *** Gets the "Client Command Port" 
    *** @param dft  The default Client Command Port
    *** @return The Client Command Port
    **/
    public int getClientCommandPort_tcp(int dft)
    {
        return this.getIntProperty(DCServerFactory.CONFIG_CLIENT_COMMAND_PORT_TCP(this.getName()), dft);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Gets the "ACK Response Port" 
    *** @param dft  The ACK response port
    *** @return The ack response port
    **/
    public int getAckResponsePort(int dft)
    {
        return this.getIntProperty(DCServerFactory.CONFIG_ACK_RESPONSE_PORT(this.getName()), dft);
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the "TCP idle timeout" 
    *** @param dft  The default timeout value
    *** @return The default timeout value
    **/
    public long getTcpIdleTimeoutMS(long dft)
    {
        return this.getLongProperty(DCServerFactory.CONFIG_TCP_IDLE_TIMEOUT(this.getName()), dft);
    }

    /**
    *** Gets the "TCP packet timeout"
    *** @param dft  The default timeout value
    *** @return The default timeout value
    **/
    public long getTcpPacketTimeoutMS(long dft)
    {
        return this.getLongProperty(DCServerFactory.CONFIG_TCP_PACKET_TIMEOUT(this.getName()), dft);
    }

    /**
    *** Gets the "TCP session timeout"
    *** @param dft  The default timeout value
    *** @return The default timeout value
    **/
    public long getTcpSessionTimeoutMS(long dft)
    {
        return this.getLongProperty(DCServerFactory.CONFIG_TCP_SESSION_TIMEOUT(this.getName()), dft);
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the "UDP idle timeout"
    *** @param dft  The default timeout value
    *** @return The default timeout value
    **/
    public long getUdpIdleTimeoutMS(long dft)
    {
        return this.getLongProperty(DCServerFactory.CONFIG_UDP_IDLE_TIMEOUT(this.getName()), dft);
    }

    /**
    *** Gets the "UDP packet timeout"
    *** @param dft  The default timeout value
    *** @return The default timeout value
    **/
    public long getUdpPacketTimeoutMS(long dft)
    {
        return this.getLongProperty(DCServerFactory.CONFIG_UDP_PACKET_TIMEOUT(this.getName()), dft);
    }

    /**
    *** Gets the "UDP session timeout"
    *** @param dft  The default timeout value
    *** @return The default timeout value
    **/
    public long getUdpSessionTimeoutMS(long dft)
    {
        return this.getLongProperty(DCServerFactory.CONFIG_UDP_SESSION_TIMEOUT(this.getName()), dft);
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the array of allowed UniqueID prefixes
    *** @return The array of allowed UniqueID prefixes
    **/
    public String[] getUniquePrefix()
    {
        if (this.uniquePrefix == null) {
            this.uniquePrefix = new String[] { "" };
        }
        return this.uniquePrefix;
    }

    /**
    *** Sets the array of allowed UniqueID prefixes
    *** @param pfx  The default UniqueID prefixes
    **/
    public void setUniquePrefix(String pfx[])
    {
        if (!ListTools.isEmpty(pfx)) {
            for (int i = 0; i < pfx.length; i++) {
                String p = pfx[i].trim();
                if (p.equals("<blank>") || p.equals("*")) { 
                    p = ""; 
                } else
                if (p.endsWith("*")) {
                    p = p.substring(0, p.length() - 1);
                }
                pfx[i] = p;
            }
            this.uniquePrefix = pfx;
        } else {
            this.uniquePrefix = new String[] { "" };;
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the "Minimum Moved Meters"
    *** @param dft  The default minimum speed
    *** @return The Minimum Moved Meters
    **/
    public double getMinimumMovedMeters(double dft)
    {
        return this.getDoubleProperty(DCServerFactory.CONFIG_MIN_MOVED_METERS(this.getName()), dft);
    }

    /**
    *** Gets the "Minimum Speed KPH"
    *** @param dft  The default minimum speed
    *** @return The Minimum Speed KPH
    **/
    public double getMinimumSpeedKPH(double dft)
    {
        return this.getDoubleProperty(DCServerFactory.CONFIG_MIN_SPEED_KPH(this.getName()), dft);
    }

    /**
    *** Gets the "Minimum Speed KPH"
    *** @param dft  The default minimum speed
    *** @return The Minimum Speed KPH
    **/
    public boolean getEstimateOdometer(boolean dft)
    {
        return this.getBooleanProperty(DCServerFactory.CONFIG_ESTIMATE_ODOM(this.getName()), dft);
    }

    /**
    *** Gets the "Simulate Geozones"
    *** @param dft  The default Simulate Geozones state
    *** @return The Simulate Geozones
    **/
    public boolean getSimulateGeozones(boolean dft)
    {
        return this.getBooleanProperty(DCServerFactory.CONFIG_SIMEVENT_GEOZONE(this.getName()), dft);
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the "Save Raw Data Packet" config
    *** @param dft  The default "Save Raw Data Packet" state
    *** @return The "Save Raw Data Packet" state
    **/
    public boolean getSaveRawDataPackets(boolean dft)
    {
        return this.getBooleanProperty(DCServerFactory.CONFIG_SAVE_RAW_DATA_PACKETS(this.getName()), dft);
    }

    /**
    *** Gets the "Start/Stop StatusCode supported" config
    *** @param dft  The default "Start/Stop StatusCode supported" state
    *** @return The "Start/Stop StatusCode supported" state
    **/
    public boolean getStartStopSupported(boolean dft)
    {
        String n = this.getName();
        if (n.equals(DCServerFactory.OPENDMTP_NAME)) {
            return true;
        } else {
            return this.getBooleanProperty(DCServerFactory.CONFIG_START_STOP_SUPPORTED(this.getName()), dft);
        }
    }

    /**
    *** Gets the "Status Location/InMotion Translation" config
    *** @param dft  The default "Status Location/InMotion Translation" state
    *** @return The "Status Location/InMotion Translation" state
    **/
    public boolean getStatusLocationInMotion(boolean dft)
    {
        return this.getBooleanProperty(DCServerFactory.CONFIG_STATUS_LOCATION_IN_MOTION(this.getName()), dft);
    }

    // ------------------------------------------------------------------------

    /**
    *** Convenience for converting the initial/final packet to a byte array.
    *** If the string begins with "0x" the the remain string is assumed to be hex
    *** @return The byte array
    **/
    public static byte[] convertToBytes(String s)
    {
        if (s == null) {
            return null;
        } else
        if (s.startsWith("0x")) {
            byte b[] = StringTools.parseHex(s,null);
            if (b != null) {
                return b;
            } else {
                return null;
            }
        } else {
            return StringTools.getBytes(s);
        }
    }

    /**
    *** Gets the "Initial Packet" byte array
    *** @param dft  The default "Initial Packet" byte array
    *** @return The "Initial Packet" byte array
    **/
    public String getInitialPacket(String dft)
    {
        String s = this.getStringProperty(DCServerFactory.CONFIG_INITIAL_PACKET(this.getName()), null);
        return (s != null)? s : dft;
    }

    /**
    *** Gets the "Final Packet" byte array
    *** @param dft  The default "Final Packet" byte array
    *** @return The "Final Packet" byte array
    **/
    public String getFinalPacket(String dft)
    {
        String s = this.getStringProperty(DCServerFactory.CONFIG_FINAL_PACKET(this.getName()), null);
        return (s != null)? s : dft;
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the "Simulate Geozones" mask
    *** @param dft  The default Simulate Geozones mask
    *** @return The Simulate Geozones mask
    **/
    public long getSimulateDigitalInputs(long dft)
    {
        String maskStr = this.getStringProperty(DCServerFactory.CONFIG_SIMEVENT_DIGITAL_INPUT(this.getName()), null);
        if (StringTools.isBlank(maskStr)) {
            // not specified (or blank)
            return dft;
        } else
        if (maskStr.equalsIgnoreCase("default")) {
            // explicit "default"
            return dft;
        } else
        if (maskStr.equalsIgnoreCase("true")) {
            // explicit "true"
            return 0xFFFFFFFFL;
        } else
        if (maskStr.equalsIgnoreCase("false")) {
            // explicit "false"
            return 0x00000000L;
        } else {
            // mask specified
            long mask = StringTools.parseLong(maskStr, -1L);
            return (mask >= 0L)? mask : dft;
        }
    }

    /**
    *** Returns true if this device supports digital inputs
    *** @return True if this device supports digital inputs
    **/
    public boolean hasDigitalInputs()
    {
        return ((this.flags & F_HAS_INPUTS) != 0);
    }

    /**
    *** Returns true if this device supports digital outputs
    *** @return True if this device supports digital outputs
    **/
    public boolean hasDigitalOutputs()
    {
        return ((this.flags & F_HAS_OUTPUTS) != 0);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Sets the "Event Code Map Enable" config
    *** @param enabled  The "Event Code Map Enable" state
    **/
    public void setEventCodeEnabled(boolean enabled)
    {
        this.customCodeEnabled = enabled;
        Print.logDebug("[" + this.getName() + "] EventCode translation enabled=" + this.customCodeEnabled);
    }

    /**
    *** Gets the "Event Code Map Enable" config
    *** @return The "Event Code Map Enable" state
    **/
    public boolean getEventCodeEnabled()
    {
        return this.customCodeEnabled;
    }
    
    /**
    **/
    public void setEventCodeMap(Map<Integer,EventCode> codeMap)
    {
        this.customCodeMap = (codeMap != null)? codeMap : new HashMap<Integer,EventCode>();
    }

    /**
    *** Returns the EventCode instance for the specified code
    *** @param code  The code
    *** @return The EventCode
    **/
    public EventCode getEventCode(int code)
    {
        return this.customCodeEnabled? this.customCodeMap.get(new Integer(code)) : null;
    }

    /**
    *** Returns the EventCode instance for the specified code
    *** @param code  The code
    *** @return The EventCode
    **/
    public EventCode getEventCode(long code)
    {
        return this.customCodeEnabled? this.customCodeMap.get(new Integer((int)code)) : null;
    }

    /**
    *** Translates the specified device status code into a GTS status code
    *** @param code           The code to translate
    *** @param dftStatusCode  The default code returned if the runtime config returns '0'
    *** @return The translated GTS status code
    **/
    public int translateStatusCode(int code, int dftStatusCode)
    {
        EventCode sci = this.getEventCode(code);
        return (sci != null)? sci.getStatusCode() : dftStatusCode;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Sets the device command listen host (may be null to use default bind-address)
    *** @param cmdHost The device command listen host
    **/
    public void setCommandDispatcherHost(String cmdHost)
    {
        this.commandHost = cmdHost;
    }

    /**
    *** Gets the device command listen host
    *** @return The device command listen host
    **/
    public String getCommandDispatcherHost()
    {
        if (!StringTools.isBlank(this.commandHost)) {
            return this.commandHost;
        } else
        if (!StringTools.isBlank(DCServerFactory.BIND_ADDRESS)) {
            return DCServerFactory.BIND_ADDRESS;
        } else {
            String bindAddr = this.getStringProperty(DBConfig.DCS_BIND_INTERFACE,null);
            return !StringTools.isBlank(bindAddr)? bindAddr : "localhost";
        }
    }

    /**
    *** Sets the device command listen port
    *** @param cmdPort  The device command listen port
    *** @param checkRTP True to allow the RTConfig propertiesto override this value
    **/
    public void setCommandDispatcherPort(int cmdPort, boolean checkRTP)
    {
        if (checkRTP) {
            cmdPort = this.getIntProperty(DCServerFactory.CONFIG_COMMAND_PORT(this.getName()), cmdPort);
        }
        this.commandPort = cmdPort;
    }

    /**
    *** Sets the device command listen port
    *** @param cmdPort  The device command listen port
    **/
    public void setCommandDispatcherPort(int cmdPort)
    {
        this.setCommandDispatcherPort(cmdPort,false);
    }

    /**
    *** Gets the device command listen port (returns '0' if not supported)
    *** @return The device command listen port
    **/
    public int getCommandDispatcherPort()
    {
        return this.commandPort;
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the Commands Acl name
    *** @return The Commands Acl name
    **/
    public String getCommandsAclName()
    {
        return this.commandsAclName;
    }

    /**
    *** Sets the Commands Acl name
    *** @param aclName The Commands Acl name
    **/
    public void setCommandsAclName(String aclName)
    {
        this.commandsAclName = StringTools.trim(aclName);
    }

    /**
    *** Returns True if the specified user has access to the named command
    **/
    public boolean userHasAccessToCommand(BasicPrivateLabel privLabel, User user, String commandName)
    {

        /* BasicPrivateLabel must be specified */
        if (privLabel == null) {
            return false;
        }

        /* get command */
        Command command = this.getCommand(commandName);
        if (command == null) {
            return false;
        }

        /* has access to commands */
        if (privLabel.hasWriteAccess(user, this.getCommandsAclName())) {
            return false;
        }

        /* has access to specific command? */
        if (privLabel.hasWriteAccess(user, command.getAclName())) {
            return false;
        }

        /* access granted */
        return true;

    }
    
    // ------------------------------------------------------------------------
    
    public void addCommand(
        String cmdName, String cmdDesc, 
        String cmdTypes[], 
        String cmdAclName, 
        String cmdString, boolean hasArgs,
        String cmdProto, boolean expectAck,
        int cmdSCode)
    {
        if (StringTools.isBlank(cmdName)) {
            Print.logError("Ignoreing blank command name");
        } else
        if ((this.commandMap != null) && this.commandMap.containsKey(cmdName)) {
            Print.logError("Command already defined: " + cmdName);
        } else {
            Command cmd = new Command(
                cmdName, cmdDesc, 
                cmdTypes, 
                cmdAclName, 
                cmdString, hasArgs,
                cmdProto, expectAck,
                cmdSCode);
            if (this.commandMap == null) {
                this.commandMap = new OrderedMap<String,Command>();
            }
            this.commandMap.put(cmdName, cmd);
        }
    }
    
    public Command getCommand(String name)
    {
        return (this.commandMap != null)? this.commandMap.get(name) : null;
    }

    /**
    *** Gets the "Command List"
    *** @return The "Command List"
    **/
    public String[] getCommandList()
    {
        if (ListTools.isEmpty(this.commandMap)) {
            return null;
        } else {
            return this.commandMap.keyArray(String.class);
        }
    }

    /**
    *** Gets the "Command Description" for the specified command
    *** @param dft  The default "Command Description"
    *** @return The "Command Description" for the specified command
    **/
    public String getCommandDescription(String cmdName, String dft)
    {
        Command cmd = this.getCommand(cmdName);
        return (cmd != null)? cmd.getDescription() : dft;
    }

    /**
    *** Gets the "Command String" for the specified command
    *** @param dft  The default "Command String"
    *** @return The "Command String" for the specified command
    **/
    public String getCommandString(String cmdName, String dft)
    {
        Command cmd = this.getCommand(cmdName);
        return (cmd != null)? cmd.getCommandString() : dft;
    }

    /**
    *** Gets the status-code for the specified command.  An event with this 
    *** status code will be inserted into the EventData table when this command
    *** is sent to the device.
    *** @param code  The default status-code
    *** @return The status-code for the specified command
    **/
    public int getCommandStatusCode(String cmdName, int code)
    {
        Command cmd = this.getCommand(cmdName);
        return (cmd != null)? cmd.getStatusCode() : code;
    }

    /**
    *** Gets the command's (name,description) map
    *** @param type The description type 
    *** @return The command's (name,description) map
    **/
    public Map<String,Command> getCommandMap(BasicPrivateLabel privLabel, User user, String type)
    {
        boolean inclReplCmds = true; // for now, include all commands
        String cmdList[] = this.getCommandList();
        if (!ListTools.isEmpty(cmdList)) {
            Map<String,Command> cmdMap = new OrderedMap<String,Command>();
            for (Command cmd : this.commandMap.values()) {
                if (!DCServerFactory.isCommandTypeAll(type) && !cmd.isType(type)) {
                    // ignore this command
                    //Print.logInfo("Command '%s' is not property type '%s'", cmd.getName(), type);
                } else
                if ((privLabel != null) && !privLabel.hasWriteAccess(user,cmd.getAclName())) {
                    // user does not have access to this command
                    //Print.logInfo("User does not have access to command '%s'", cmd.getName());
                } else {
                    String key  = cmd.getName();
                    String desc = cmd.getDescription();
                    String cstr = cmd.getCommandString();
                    if (StringTools.isBlank(desc) && StringTools.isBlank(cstr)) {
                        // skip commands with blank description and commands
                        Print.logInfo("Command does not have a descripton, or command is blank");
                        continue;
                    } else
                    if (!inclReplCmds) {
                        if (cstr.indexOf("${") >= 0) { //}
                            // should not occur ('type' should not include commands that require parameters)
                            // found "${text}"
                            continue;
                        }
                    }
                    cmdMap.put(key,cmd);
                }
            }
            return cmdMap;
        } else {
            return null;
        }
    }

    /**
    *** Gets the command's (name,description) map
    *** @param type The description type 
    *** @return The command's (name,description) map
    **/
    public Map<String,String> getCommandDescriptionMap(BasicPrivateLabel privLabel, User user, String type)
    {
        Map<String,Command> cmdMap = this.getCommandMap(privLabel, user, type);
        if (!ListTools.isEmpty(cmdMap)) {
            Map<String,String> cmdDescMap = new OrderedMap<String,String>();
            for (Command cmd : cmdMap.values()) {
                String key  = cmd.getName();
                String desc = cmd.getDescription();
                cmdDescMap.put(key,desc); // Commands are pre-qualified
            }
            return cmdDescMap;
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    public RTProperties getProperties()
    {
        return this.rtProps;
    }

    // ------------------------------------------------------------------------

    public String normalizeKey(String key)
    {
        if (StringTools.isBlank(key)) {
            return "";
        } else {
            String normKey = this.getName() + ".";
            if (key.startsWith(normKey)) {
                return key;
            } else {
                return normKey + key;
            }
        }
    }
    
    public String[] normalizeKeys(String key[])
    {
        if (!ListTools.isEmpty(key)) {
            for (int i = 0; i < key.length; i++) {
                key[i] = this.normalizeKey(key[i]);
            }
        }
        return key;
    }

    // ------------------------------------------------------------------------

    public boolean hasProperty(String key[], boolean inclDft)
    {
        if (this.rtProps.hasProperty(key)) {
            return true;
        } else {
            String k[] = this.normalizeKeys(key);
            if (this.rtProps.hasProperty(k)) {
                return true;
            } else {
                return RTConfig.hasProperty(k, inclDft);
            }
        }
    }

    public Set<String> getPropertyKeys(String prefix)
    {
        Set<String> propKeys = new HashSet<String>();
        
        /* regualr keys */
        propKeys.addAll(this.rtProps.getPropertyKeys(prefix));
        propKeys.addAll(RTConfig.getPropertyKeys(prefix));

        /* normalized keys */
        String pfx = this.normalizeKey(prefix);
        propKeys.addAll(this.rtProps.getPropertyKeys(pfx));
        propKeys.addAll(RTConfig.getPropertyKeys(pfx));

        return propKeys;
    }

    // ------------------------------------------------------------------------

    public String[] getStringArrayProperty(String key, String dft[])
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getStringArray(key, dft);
        } else {
            String k = this.normalizeKey(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getStringArray(k, dft);
            } else {
                return RTConfig.getStringArray(k, dft);
            }
        }
    }

    public String[] getStringArrayProperty(String key[], String dft[])
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getStringArray(key, dft);
        } else {
            String k[] = this.normalizeKeys(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getStringArray(k, dft);
            } else {
                return RTConfig.getStringArray(k, dft);
            }
        }
    }
    
    // ------------------------------------------------------------------------

    public String getStringProperty(String key, String dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getString(key, dft);
        } else {
            String k = this.normalizeKey(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getString(k, dft);
            } else {
                return RTConfig.getString(k, dft);
            }
        }
    }

    public String getStringProperty(String key[], String dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getString(key, dft);
        } else {
            String k[] = this.normalizeKeys(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getString(k, dft);
            } else {
                return RTConfig.getString(k, dft);
            }
        }
    }
    
    // ------------------------------------------------------------------------

    public int getIntProperty(String key, int dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getInt(key, dft);
        } else {
            String k = this.normalizeKey(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getInt(k, dft);
            } else {
                return RTConfig.getInt(k, dft);
            }
        }
    }

    public int getIntProperty(String key[], int dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getInt(key, dft);
        } else {
            String k[] = this.normalizeKeys(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getInt(k, dft);
            } else {
                return RTConfig.getInt(k, dft);
            }
        }
    }
    
    // ------------------------------------------------------------------------

    public long getLongProperty(String key, long dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getLong(key, dft);
        } else {
            String k = this.normalizeKey(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getLong(k, dft);
            } else {
                return RTConfig.getLong(k, dft);
            }
        }
    }

    public long getLongProperty(String key[], long dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getLong(key, dft);
        } else {
            String k[] = this.normalizeKeys(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getLong(k, dft);
            } else {
                return RTConfig.getLong(k, dft);
            }
        }
    }
    
    // ------------------------------------------------------------------------

    public double getDoubleProperty(String key, double dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getDouble(key, dft);
        } else {
            String k = this.normalizeKey(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getDouble(k, dft);
            } else {
                return RTConfig.getDouble(k, dft);
            }
        }
    }

    public double getDoubleProperty(String key[], double dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getDouble(key, dft);
        } else {
            String k[] = this.normalizeKeys(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getDouble(k, dft);
            } else {
                return RTConfig.getDouble(k, dft);
            }
        }
    }

    // ------------------------------------------------------------------------

    public boolean getBooleanProperty(String key, boolean dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getBoolean(key, dft);
        } else {
            String k = this.normalizeKey(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getBoolean(k, dft);
            } else {
                return RTConfig.getBoolean(k, dft);
            }
        }
    }

    public boolean getBooleanProperty(String key[], boolean dft)
    {
        if (this.rtProps.hasProperty(key)) {
            return this.rtProps.getBoolean(key, dft);
        } else {
            String k[] = this.normalizeKeys(key);
            if (this.rtProps.hasProperty(k)) {
                return this.rtProps.getBoolean(k, dft);
            } else {
                return RTConfig.getBoolean(k, dft);
            }
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Returns a String representation of this instance
    *** @return A String representation
    **/
    public String toString()
    {
        return this.toString(true);
    }

    /**
    *** Returns a String representation of this instance
    *** @param inclName True to include the name in the returnsed String representation
    *** @return A String representation
    **/
    public String toString(boolean inclName)
    {
        // "(opendmtp) OpenDMTP Server [TCP=31000 UDP=31000]
        int      tcp[]  = this.getTcpPorts();
        boolean  hasTCP = !ListTools.isEmpty(tcp);
        int      udp[]  = this.getUdpPorts();
        boolean  hasUDP = !ListTools.isEmpty(udp);
        StringBuffer sb = new StringBuffer();
        
        /* name/description */
        if (inclName) {
            sb.append("(").append(this.getName()).append(") ");
        }
        sb.append(this.getDescription()).append(" ");

        /* ports */
        sb.append("[");
        if (hasTCP || hasUDP) {
            sb.append(hasTCP? ("TCP="+StringTools.join(tcp,",")) : "");
            if (hasTCP && hasUDP) { sb.append(" "); }
            sb.append(hasUDP? ("UDP="+StringTools.join(udp,",")) : "");
        } else {
            sb.append("no-ports");
        }
        sb.append("]");
        
        /* String representation */
        return sb.toString();

    }

}
