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

public class DeviceCmd_calamp
    implements DeviceCmdHandler
{

    // ------------------------------------------------------------------------
  
    /* device code */
    public static final String  DEVICE_CODE                 = DCServerFactory.CALAMP_NAME;

    /* default command */
    public static final String  DEFAULT_COMMAND             = "LocateNow";

    // ------------------------------------------------------------------------

    public  static final String FORM_PROPERTY_EDIT          = "DevicePropEdit";

    // DeviceInfo commands
    public  static final String COMMAND_INFO_UPD_PROPS      = DeviceInfo.COMMAND_INFO_UPD_PROPS;

    // DeviceInfo parameters
    public  static final String PARM_COMMAND                = DeviceInfo.PARM_COMMAND;
    public  static final String PARM_DEVICE                 = DeviceInfo.PARM_DEVICE;
    public  static final String PARM_DEV_DESC               = DeviceInfo.PARM_DEV_DESC;

    // command properties
    public  static final String PARM_CMDSEL                 = "p_cmdsel";

    // submit
    public  static final String PARM_SUBMIT_SEND            = DeviceInfo.PARM_SUBMIT_QUE;

    // buttons
    public  static final String PARM_BUTTON_CANCEL          = "d_btncan";
    public  static final String PARM_BUTTON_BACK            = "d_btnbak";

    // ------------------------------------------------------------------------
    
    private Map<String,String>  commands    = null;

    /* DeviceCommand constructor */
    public DeviceCmd_calamp()
    {
        //
    }
    
    private Map<String,String> getCommandDescriptionMap(BasicPrivateLabel privLabel, User user)
    {
        if (this.commands == null) {
            DCServerConfig dcs = DCServerFactory.getServerConfig(DEVICE_CODE);
            if (dcs != null) {
                this.commands = dcs.getCommandDescriptionMap(privLabel,user,null);
            } else {
                Print.logInfo("DCServer not found: " + DEVICE_CODE);
                this.commands = null;
            }
            if ((this.commands == null) || !this.commands.containsKey(DEFAULT_COMMAND)) {
                if (this.commands == null) { this.commands = new OrderedMap<String,String>(); }
                ((OrderedMap<String,String>)this.commands).put(0, DEFAULT_COMMAND, DEFAULT_COMMAND);
            }
        }
        return this.commands;
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
        return "CalAmp";
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
        I18N         i18n        = privLabel.getI18N(DeviceCmd_calamp.class);
        Locale       locale      = privLabel.getLocale();
        String       selDevID    = selDev.getDeviceID();
        String       devTitles[] = reqState.getDeviceTitles();

        /* start of form */
        out.write("<form name='"+FORM_PROPERTY_EDIT+"' method='POST' action='"+actionURL+"' target='_top'>\n");
        out.write("<input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_UPD_PROPS+"'/>\n");

        //
        out.println("<table border='0' cellpadding='0' cellspacing='0'>");

        // device id/description
        out.println(DeviceInfo.FormRow_TextField(PARM_DEVICE  , false, i18n.getString("DeviceCmd_calamp.deviceID","{0} ID",devTitles)+":"            , selDevID, 30, 30));
        out.println(DeviceInfo.FormRow_TextField(PARM_DEV_DESC, false, i18n.getString("DeviceCmd_calamp.deviceDesc","{0} Description",devTitles) +":", (selDev!=null)?selDev.getDescription():"", 40, 40));

        // available commands
        ComboMap commandMap = new ComboMap(this.getCommandDescriptionMap(privLabel,reqState.getCurrentUser()));
        String   commandSel = ""; // DEFAULT_COMMAND;
        commandMap.insert(""); // insert a blank as the first entry
        out.println(DeviceInfo.FormRow_ComboBox(PARM_CMDSEL , editProps, i18n.getString("DeviceCmd_calamp.commands","Command")+":", commandSel, commandMap, "", -1));

        // 
        out.println("</table>");

        /* end of form */
        out.write("<hr style='margin-bottom:5px;'>\n");
        if (editProps) {
            out.write("<input type='submit' name='"+PARM_SUBMIT_SEND+"' value='"+i18n.getString("DeviceCmd_calamp.send","Send")+"'>\n");
            out.write("<span style='padding-left:10px'>&nbsp;</span>\n");
            out.write("<input type='button' name='"+PARM_BUTTON_CANCEL+"' value='"+i18n.getString("DeviceCmd_calamp.cancel","Cancel")+"' onclick=\"javascript:openURL('"+actionURL+"','_self');\">\n");
        } else {
            out.write("<input type='button' name='"+PARM_BUTTON_BACK+"' value='"+i18n.getString("DeviceCmd_calamp.back","Back")+"' onclick=\"javascript:openURL('"+actionURL+"','_self');\">\n");
        }
        out.write("</form>\n");
        return true;

    }

    // ------------------------------------------------------------------------

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
        I18N               i18n        = privLabel.getI18N(DeviceCmd_calamp.class);
        String             devTitles[] = reqState.getDeviceTitles();
        String             server      = this.getServerID();
        String             acctID      = selDev.getAccountID();
        String             devID       = selDev.getDeviceID();

        /* supported device? */
        if (!this.deviceSupportsCommands(selDev)) {
            return i18n.getString("DeviceCmd_calamp.doesNotSupport","Device does not support {0}", this.getServerDescription());
        }
        
        /* selected command */
        String cmdSel = (String)AttributeTools.getRequestAttribute(request, PARM_CMDSEL, "");
        if (StringTools.isBlank(cmdSel)) {
            return i18n.getString("DeviceCmd_calamp.noCommandSelected","No command selected");
        }

        /* send command to DCS command server */
        String     cmdType = DCServerConfig.COMMAND_CONFIG;
        String     cmdName = cmdSel;
        String     cmdArgs[] = null;
        RTProperties resp  = DCServerFactory.sendServerCommand(selDev, cmdType, cmdName, cmdArgs);
        if (resp == null) {
            return i18n.getString("DeviceCmd_calamp.unableToQueueCommand","Unable to queue command for transmission");
        } else {
            // TODO: check response for other possible errors
            return i18n.getString("DeviceCmd_calamp.commandQueued","Requested command has been queued for transmission");
        }

    }

    // ------------------------------------------------------------------------

}

