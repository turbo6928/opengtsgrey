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
// Change History:
//  2009/07/01  Martin D. Flynn
//     -Initial release
//  2009/12/16  Martin D. Flynn
//     -Modified to dynamically present commands fro dcserver_enfora.xml
// ----------------------------------------------------------------------------
package org.opengts.war.track.page.devcmd;

import java.util.*;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.war.tools.*;
import org.opengts.war.track.*;
import org.opengts.war.track.page.*;

import org.opengts.db.dmtp.*;

public class DeviceCmd_enfora
    implements DeviceCmdHandler
{

    // ------------------------------------------------------------------------
  
    /* device code */
    public static final String  DEVICE_CODE                 = DCServerFactory.ENFORA_NAME;

    // ------------------------------------------------------------------------

    public  static final String FORM_PROPERTY_EDIT          = "DeviceCommandForm";

    // DeviceInfo commands
    public  static final String COMMAND_INFO_UPD_PROPS      = DeviceInfo.COMMAND_INFO_UPD_PROPS;

    // DeviceInfo parameters
    public  static final String PARM_COMMAND                = DeviceInfo.PARM_COMMAND;
    public  static final String PARM_DEVICE                 = DeviceInfo.PARM_DEVICE;
    public  static final String PARM_DEV_DESC               = DeviceInfo.PARM_DEV_DESC;
    public  static final String PARM_DEV_LAST_CONNECT       = DeviceInfo.PARM_DEV_LAST_CONNECT;
    public  static final String PARM_DEV_LAST_EVENT         = DeviceInfo.PARM_DEV_LAST_EVENT;

    // device properties
    public  static final String PARM_COMMAND_SELECT         = "cmdRadioSel";

    // radio button commands
    public  static final String RADIO_ZONES                 = "Geozones";
    public  static final String RADIO_CMD_SEL_              = "rc_";
    public  static final String RADIO_CMD_TEXT_             = "rct_";

    // submit
    public  static final String PARM_SUBMIT_SEND            = DeviceInfo.PARM_SUBMIT_QUE;

    // buttons
    public  static final String PARM_BUTTON_CANCEL          = "d_btncan";
    public  static final String PARM_BUTTON_BACK            = "d_btnbak";


    // ------------------------------------------------------------------------

    public  static final String CSS_deviceCommandLabel      = "deviceCommandLabel";
    public  static final String CSS_deviceCommand           = "deviceCommand";
    public  static final String CSS_deviceCommandArg        = "deviceCommandArg";
    public  static final String CSS_deviceCommandSep        = "deviceCommandSep";
    public  static final String CSS_deviceCommandSpacer     = "deviceCommandSpacer";

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* DeviceCommand constructor */
    public DeviceCmd_enfora()
    {
        //
    }

    // ------------------------------------------------------------------------

    private Map<String,DCServerConfig.Command> getCommandMap(BasicPrivateLabel privLabel, User user, String type)
    {
        DCServerConfig dcs = DCServerFactory.getServerConfig(DEVICE_CODE);
        if (dcs != null) {
            return dcs.getCommandMap(privLabel, user, type);
        } else {
            Print.logInfo("DCServer not found: " + DEVICE_CODE);
            return null;
        }
    }

    private Map<String,String> getCommandDescriptionMap(BasicPrivateLabel privLabel, User user, String type)
    {
        DCServerConfig dcs = DCServerFactory.getServerConfig(DEVICE_CODE);
        if (dcs != null) {
            return dcs.getCommandDescriptionMap(privLabel, user, type);
        } else {
            Print.logInfo("DCServer not found: " + DEVICE_CODE);
            return null;
        }
    }

    // ------------------------------------------------------------------------

    /* DCS id */
    public String getServerID()
    {
        return DEVICE_CODE;
    }

    /* DCS name */
    public String getServerDescription()
    {
        return "Enfora";
    }
    
    // ------------------------------------------------------------------------

    /* true if this UI supports the specified device */
    public boolean deviceSupportsCommands(Device dev)
    {
        if (dev == null) {
            Print.logWarn("Device is null");
            return false;
        } else
        if (StringTools.isBlank(dev.getDeviceCode())) {
            Print.logWarn("DeviceCode is null/blank");
            return false;
        } else
        if (!dev.getDeviceCode().equalsIgnoreCase(DEVICE_CODE)) {
            Print.logWarn("DeviceCode does not match Enfora: " + dev.getDeviceCode());
            return false;
        } else {
            return true;
        }
    }
    
    // ------------------------------------------------------------------------

    /* write the command form html */
    public boolean writeCommandForm(PrintWriter out, RequestProperties reqState, Device selDev,
        String actionURL, boolean editProps)
        throws IOException
    {

        /* check for nulls */
        if ((out == null) || (reqState == null) || (selDev == null)) {
            return false;
        }
        
        /* supported device? */
        if (!this.deviceSupportsCommands(selDev)) {
            return false;
        }

        /* init */
        PrivateLabel privLabel   = reqState.getPrivateLabel();
        I18N         i18n        = privLabel.getI18N(DeviceCmd_enfora.class);
        Locale       locale      = privLabel.getLocale();
        String       selDevID    = selDev.getDeviceID();
        String       devTitles[] = reqState.getDeviceTitles();
        TimeZone     timeZone    = reqState.getTimeZone();

        /* start of form */
        out.write("<form name='"+FORM_PROPERTY_EDIT+"' method='POST' action='"+actionURL+"' target='_top'>\n");
        out.write("<input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_UPD_PROPS+"'/>\n");

        //
        out.println("<table border='0' cellpadding='0' cellspacing='0' width='100%'>");

        /* device id/description */
        out.println(DeviceInfo.FormRow_TextField(PARM_DEVICE        , false, i18n.getString("DeviceCmd_enfora.deviceID","{0} ID",devTitles)+":"            , selDevID, 30, 30));
        out.println(DeviceInfo.FormRow_TextField(PARM_DEV_DESC      , false, i18n.getString("DeviceCmd_enfora.deviceDesc","{0} Description",devTitles) +":", (selDev!=null)?selDev.getDescription():"", 40, 40));

        /* last communication times from the device */
        String lastEventTime = "";
        try {
            EventData lastEv = (selDev != null)? selDev.getLastEvent(-1L, false) : null;
            long evTS = (lastEv != null)? lastEv.getTimestamp() : 0L;
            lastEventTime = (evTS > 0L)? (new DateTime(evTS,timeZone)).toString() : "?";
        } catch (DBException dbe) {
            lastEventTime = "E";
        }
        long lastCommTS = (selDev != null)? selDev.getLastConnectTime() : 0L;
        String lastCommTime  = (lastCommTS > 0L)? (new DateTime(lastCommTS,timeZone)).toString() : "?";
        out.println(DeviceInfo.FormRow_TextField(PARM_DEV_LAST_CONNECT, false, i18n.getString("DeviceCmd_enfora.lastCommunication","Last Communication") +":", lastCommTime , 30, 30));
        out.println(DeviceInfo.FormRow_TextField(PARM_DEV_LAST_EVENT  , false, i18n.getString("DeviceCmd_enfora.lastEvent","Last Event") +":"                , lastEventTime, 30, 30));

        /* commands */
        out.println("<tr class='deviceCommandList'>");
        out.println("<td class='"+CommonServlet.CSS_ADMIN_VIEW_TABLE_HEADER+"' nowrap style=\"vertical-align:top; padding-top:5px;\">"+i18n.getString("DeviceCmd_enfora.commandSelect","Command Select")+":</td>");
        out.println("<td class='"+CommonServlet.CSS_ADMIN_VIEW_TABLE_DATA+"' width='100%' style=\"padding-top:5px;\">");
        out.println("<table border='0' cellpadding='0' cellspacing='0' width='100%'>");

        /* include configured commands */
        int cmdCount = 0;
        DCServerConfig dcs = DCServerFactory.getServerConfig(DEVICE_CODE);
        if (dcs != null) {
            Map<String,DCServerConfig.Command> cmdMap_admin = this.getCommandMap(privLabel, reqState.getCurrentUser(), DCServerFactory.CMDTYPE_ADMIN);
            if (!ListTools.isEmpty(cmdMap_admin)) {
                cmdCount += this._writeCommands(out,
                    i18n.getString("DeviceCmd_enfora.standardCommands","Standard Commands"),
                    cmdMap_admin, editProps, i18n);
            }
            Map<String,DCServerConfig.Command> cmdMap_garmin = this.getCommandMap(privLabel, reqState.getCurrentUser(), DCServerFactory.CMDTYPE_GARMIN);
            if (!ListTools.isEmpty(cmdMap_garmin)) {
                cmdCount += this._writeCommands(out,
                    i18n.getString("DeviceCmd_enfora.garminCommands","Garmin Commands"),
                    cmdMap_garmin, editProps, i18n);
            }
            Map<String,DCServerConfig.Command> cmdMap_sysadmin = this.getCommandMap(privLabel, reqState.getCurrentUser(), DCServerFactory.CMDTYPE_SYSADMIN);
            if (!ListTools.isEmpty(cmdMap_sysadmin)) {
                cmdCount += this._writeCommands(out,
                    i18n.getString("DeviceCmd_enfora.sysadminCommands","SysAdmin Commands"),
                    cmdMap_sysadmin, editProps, i18n);
            }
        }
        
        /* if no commands were displayed */
        if (cmdCount == 0) {
            out.print("<tr class='"+CSS_deviceCommand+"'>");
            out.print("<td class='"+CSS_deviceCommand+"'>&nbsp;</td>");
            out.print("<td class='"+CSS_deviceCommand+"' width='100%'>");
            out.print("<span>"+i18n.getString("DeviceCmd_enfora.noCommandsAvailable","No Commands Available")+"</span>");
            out.print("</td>");
            out.print("</tr>\n");
        }

        out.println("</table>");
        out.println("</td>");
        out.println("</tr>");

        //
        out.println("</table>");

        /* end of form */
        out.write("<hr style='margin-bottom:5px;'>\n");
        if (editProps) {
            out.write("<input type='submit' name='"+PARM_SUBMIT_SEND+"' value='"+i18n.getString("DeviceCmd_enfora.send","Send")+"'>\n");
            out.write("<span style='padding-left:10px'>&nbsp;</span>\n");
            out.write("<input type='button' name='"+PARM_BUTTON_CANCEL+"' value='"+i18n.getString("DeviceCmd_enfora.cancel","Cancel")+"' onclick=\"javascript:openURL('"+actionURL+"','_self');\">\n");
        } else {
            out.write("<input type='button' name='"+PARM_BUTTON_BACK+"' value='"+i18n.getString("DeviceCmd_enfora.back","Back")+"' onclick=\"javascript:openURL('"+actionURL+"','_self');\">\n");
        }
        out.write("</form>\n");
        return true;

    }

    private int _writeCommands(PrintWriter out,
        String title,
        Map<String,DCServerConfig.Command> cmdMap, boolean editProps, I18N i18n)
    {
        out.print("<tr class='"+CSS_deviceCommandSep+"'>");
        out.print("<td class='"+CSS_deviceCommandSep+"' width='100%'>");
        out.print("<span>"+title+":</span>");
        out.print("</td>");
        out.print("</tr>\n");

        int cmdCount = 0;
        if (!ListTools.isEmpty(cmdMap)) {
            for (DCServerConfig.Command cmd : cmdMap.values()) {
                String cmdID     = cmd.getName();
                String radioID   = RADIO_CMD_SEL_ + cmdID;
                String radioDesc = cmd.getDescription();
                String atCmd     = cmd.getCommandString();
                boolean hasArgs  = cmd.hasCommandArgs();
                String cssClass  = hasArgs? CSS_deviceCommandArg : CSS_deviceCommand;
                out.print("<tr class='"+cssClass+"'>");
                out.print("<td class='"+cssClass+"' width='100%'>");
                
                out.print("<table border='0' cellpadding='0' cellspacing='0' width='100%'>");
                out.print("<tr>");
                out.print("<td class='"+cssClass+"' style=\"vertical-align:top; padding-top:3px;\">");
                out.print("<input type='radio' name='"+PARM_COMMAND_SELECT+"' id='"+radioID+"' value='"+cmdID+"' onchange=\"javascript:devCommandRadioChanged();\">");
                out.print("</td>");
                out.print("<td class='"+cssClass+"'style=\"text-align:left; width:100%;\">");
                out.print("<label for='"+radioID+"' class='"+CSS_deviceCommandLabel+"'>"+radioDesc+"</label>");
                if (hasArgs) {
                    int argCnt = cmd.getArgCount();
                    for (int i = 0; i < argCnt; i++) {
                        String radioTextID = RADIO_CMD_TEXT_ + cmdID + "_" + i;
                        String argDesc = cmd.getArgDescription(i);
                        if (!StringTools.isBlank(argDesc)) {
                            out.print("<br>");
                            out.print("<label for='"+radioID+"' class='"+CSS_deviceCommandLabel+"'>"+argDesc+":</label>");
                        } else {
                            out.println(": ");
                        }
                        out.print(DeviceInfo.Form_TextField(radioTextID, radioTextID, editProps, "", 70, 500));
                    }
                }
                out.print("</td>");
                out.print("</tr>");
                out.print("</table>\n");
                
                out.print("</td>");
                out.print("</tr>\n");
                cmdCount++;
            }
        }
        
        out.print("<tr class='"+CSS_deviceCommandSpacer+"'>");
        out.print("<td class='"+CSS_deviceCommandSpacer+"' width='100%'>");
        out.print("<span>&nbsp;</span>");
        out.print("</td>");
        out.print("</tr>\n");

        return cmdCount;
    }

    // ------------------------------------------------------------------------
    
    /* encode a PendingPacket.SetPropertyPacket, and add it to the property change list */
    private static boolean _addPropertyPacket(HttpServletRequest request, String reqAttr, int propCode, 
        java.util.List<PendingPacket.SetPropertyPacket> propList)
    {

        /* get new property value */
        String val = AttributeTools.getRequestString(request, reqAttr, "");
        if (StringTools.isBlank(val) || val.equals("?")) {
            return true;
        }

        /* insert PendingPacket */
        PendingPacket.SetPropertyPacket spp = PendingPacket.createSetPropertyPacket(propCode, val);
        if (spp != null) {
            propList.add(spp);
            return true;
        } else {
            return false;
        }

    }

    // ------------------------------------------------------------------------
    
    private static String GetResponseMessage(RTProperties resp, I18N i18n)
    {
        if (resp == null) {
            return i18n.getString("DeviceCmd_enfora.unableToQueueCommand","Unable to queue command for transmission");
        } else {
            // TODO: check response for other possible errors
            Print.logInfo("Response: " + resp);
            return i18n.getString("DeviceCmd_enfora.commandQueued","Requested command has been queued for transmission");
        }
    }

    /* update Device table with user entered information */
    public String handleDeviceCommands(RequestProperties reqState, Device selDev)
    {

        /* check for nulls */
        if ((reqState == null) || (selDev == null)) {
            return "Invalid 'queueDeviceProperties' parameters";
        }

        /* init */
        HttpServletRequest request     = reqState.getHttpServletRequest();
        PrivateLabel       privLabel   = reqState.getPrivateLabel();
        I18N               i18n        = privLabel.getI18N(DeviceCmd_enfora.class);
        String             devTitles[] = reqState.getDeviceTitles();
        String             serverID    = this.getServerID();
        String             acctID      = selDev.getAccountID();
        String             devID       = selDev.getDeviceID();

        /* supported device? */
        if (!this.deviceSupportsCommands(selDev)) {
            return i18n.getString("DeviceCmd_enfora.doesNotSupport","Device does not support {0}", this.getServerDescription());
        }

        /* get selected command */
        String cmdSel = AttributeTools.getRequestString(request, PARM_COMMAND_SELECT, "");
        if (StringTools.isBlank(cmdSel)) {
            return i18n.getString("DeviceCmd_enfora.noCommandSelected","No command selected");
        }
        Print.logInfo("Selected Command: " + cmdSel);
        
        /* send geozones */
        boolean showClientUploadZone = privLabel.getBooleanProperty(PrivateLabel.PROP_ZoneInfo_showClientUploadZone,false);
        if (showClientUploadZone && cmdSel.equalsIgnoreCase(RADIO_ZONES)) {
            String cmdArgs[] = null;
            RTProperties resp = DCServerFactory.sendServerCommand(selDev, DCServerConfig.COMMAND_GEOZONE, "", cmdArgs);
            return GetResponseMessage(resp,i18n);
        }

        /* check other custom commands */
        Map<String,DCServerConfig.Command> cmdMap_all = this.getCommandMap(privLabel, reqState.getCurrentUser(), null);
        if (!ListTools.isEmpty(cmdMap_all)) {
            for (DCServerConfig.Command cmd : cmdMap_all.values()) {
                String cmdID = cmd.getName();
                if (cmdSel.equals(cmdID)) {
                    String cmdArgs[] = null;
                    if (cmd.hasCommandArgs()) {
                        cmdArgs = new String[] { null, null, null, null };
                        for (int i = 0; i < cmdArgs.length; i++) {
                            String radioTextID = RADIO_CMD_TEXT_ + cmdID + "_" + i;
                            cmdArgs[i] = AttributeTools.getRequestString(request, radioTextID, null);
                        }
                    }
                    String cmdType = DCServerConfig.COMMAND_CONFIG;
                    RTProperties resp = DCServerFactory.sendServerCommand(selDev, cmdType, cmdID, cmdArgs);
                    return GetResponseMessage(resp, i18n);
                }
            }
        }

        /* unknown command */
        return i18n.getString("DeviceCmd_calamp.unknownCommand","No command selected");

    }

    // ------------------------------------------------------------------------

}

