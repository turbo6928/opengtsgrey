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
//  2007/01/25  Martin D. Flynn
//     -Initial release
//  2007/02/25  Martin D. Flynn
//     -Fixed possible exception when notification email has been disabled.
//  2007/06/03  Martin D. Flynn
//     -Added I18N support
//  2007/06/13  Martin D. Flynn
//     -Added support for browsers with disabled cookies
//  2007/06/30  Martin D. Flynn
//     -Added Device table view
//  2007/07/27  Martin D. Flynn
//     -Added 'getNavigationTab(...)'
//  2007/12/13  Martin D. Flynn
//     -Fixed NPE when no devices are yet defined for the account
//  2007/01/10  Martin D. Flynn
//     -Added edit fields for 'notes', 'simPhoneNumber'.
//  2008/05/14  Martin D. Flynn
//     -Integrated Device DataTransport interface
//  2008/10/16  Martin D. Flynn
//     -Added ability to create/delete devices.
//     -Added ability to edit unique-id and 'active' state.
//     -'setAllowNotify()' now properly set when rule selector has been specified.
//     -Update with new ACL usage
//  2008/12/01  Martin D. Flynn
//     -Added 'Map Pushpin ID' field to specify group map icon for a specific device.
//  2009/01/01  Martin D. Flynn
//     -Added Notification Description fields (if a valid RuleFactory is in place).
//  2009/05/01  Martin D. Flynn
//     -Added "Ignition Input Line" field.
//  2009/08/23  Martin D. Flynn
//     -Convert new entered IDs to lowercase
//  2009/11/10  Martin D. Flynn
//     -Added PushpinChooser support
// ----------------------------------------------------------------------------
package org.opengts.war.track.page;

import java.util.*;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.db.*;
import org.opengts.db.AclEntry.AccessLevel;
import org.opengts.db.tables.*;

import org.opengts.war.tools.*;
import org.opengts.war.track.*;
import org.opengts.war.track.page.devcmd.*;
import org.opengts.war.maps.JSMap;

public class DeviceInfo
    extends WebPageAdaptor
    implements Constants
{

    // ------------------------------------------------------------------------

    private static final boolean SHOW_LAST_CONNECT          = false;
    private static final boolean SHOW_NOTES                 = false;
    private static final boolean SHOW_FIXED_LOCATION        = false;

    // ------------------------------------------------------------------------

    public  static final String _ACL_RULES                  = "rules";
    public  static final String _ACL_UNIQUEID               = "uniqueID";
    private static final String _ACL_LIST[]                 = new String[] { _ACL_RULES, _ACL_UNIQUEID };

    // ------------------------------------------------------------------------
    // Parameters

    // forms
    public  static final String FORM_DEVICE_SELECT          = "DeviceInfoSelect";
    public  static final String FORM_DEVICE_EDIT            = "DeviceInfoEdit";
    public  static final String FORM_DEVICE_NEW             = "DeviceInfoNew";

    // commands
    public  static final String COMMAND_INFO_UPD_DEVICE     = "updateDev";
    public  static final String COMMAND_INFO_UPD_PROPS      = "updateProps";
  //public  static final String COMMAND_INFO_REFRESH        = "refreshList";
    public  static final String COMMAND_INFO_SEL_DEVICE     = "selectDev";
    public  static final String COMMAND_INFO_NEW_DEVICE     = "newDev";

    // submit
    public  static final String PARM_SUBMIT_EDIT            = "d_subedit";
    public  static final String PARM_SUBMIT_VIEW            = "d_subview";
    public  static final String PARM_SUBMIT_CHG             = "d_subchg";
    public  static final String PARM_SUBMIT_DEL             = "d_subdel";
    public  static final String PARM_SUBMIT_NEW             = "d_subnew";
    public  static final String PARM_SUBMIT_QUE             = "d_subque";
    public  static final String PARM_SUBMIT_PROP            = "d_subprop";

    // buttons
    public  static final String PARM_BUTTON_CANCEL          = "d_btncan";
    public  static final String PARM_BUTTON_BACK            = "d_btnbak";

    // device table fields
    public  static final String PARM_NEW_NAME               = "d_newname";
    public  static final String PARM_SERVER_ID              = "d_servid";
    public  static final String PARM_DEV_UNIQ               = "d_uniq";
    public  static final String PARM_DEV_DESC               = "d_desc";
    public  static final String PARM_DEV_NAME               = "d_name";
    public  static final String PARM_DEV_EQUIP_TYPE         = "d_equipt";
    public  static final String PARM_DEV_IMEI               = "d_imei";
    public  static final String PARM_DEV_SIMPHONE           = "d_simph";
    public  static final String PARM_ICON_ID                = "d_iconid";
    public  static final String PARM_DEV_ACTIVE             = "d_actv";
    public  static final String PARM_DEV_SERIAL             = "d_ser";
    public  static final String PARM_DEV_LAST_CONNECT       = "d_lconn";
    public  static final String PARM_DEV_LAST_EVENT         = "d_levnt";
    public  static final String PARM_DEV_NOTES              = "d_notes";
    public  static final String PARM_FIXED_LAT              = "d_fixlat";
    public  static final String PARM_FIXED_LON              = "d_fixlon";
    public  static final String PARM_IGNITION_INDEX         = "d_ignndx";
    public  static final String PARM_DEV_GROUP_             = "d_grp_";
    
    public  static final String PARM_REPORT_ODOM            = "d_rptodom";
    public  static final String PARM_MAINT_INTERVAL         = "d_mntintr";
    public  static final String PARM_MAINT_LAST             = "d_mntlast";  // read-only
    public  static final String PARM_MAINT_NEXT             = "d_mntnext";  // read-only
    public  static final String PARM_MAINT_RESET            = "d_mntreset"; // checkbox

    public  static final String PARM_DEV_RULE_ALLOW         = "d_ruleallw";
    public  static final String PARM_DEV_RULE_EMAIL         = "d_rulemail";
    public  static final String PARM_DEV_RULE_SEL           = "d_rulesel";
    public  static final String PARM_DEV_RULE_DESC          = "d_ruledesc";
    public  static final String PARM_DEV_RULE_SUBJ          = "d_rulesubj";
    public  static final String PARM_DEV_RULE_TEXT          = "d_ruletext";
    public  static final String PARM_DEV_RULE_WRAP          = "d_rulewrap";

    public  static final String PARM_DEV_CUSTOM_            = "d_c_";

    // ------------------------------------------------------------------------
    // Device command handlers

    private static Map<String,DeviceCmdHandler> DCMap = new HashMap<String,DeviceCmdHandler>();

    static {
        _initDeviceCommandHandlers();
    };

    private static void _addDeviceCommandHandler(DeviceCmdHandler dch)
    {
        Print.logDebug("Installing DeviceCmdHandler: %s", dch.getServerID());
        DCMap.put(dch.getServerID(), dch);
    }

    private static void _initDeviceCommandHandlers()
    {

        /* gtsdmtp */
        _addDeviceCommandHandler(new DeviceCmd_gtsdmtp());

        /* enfora */
        _addDeviceCommandHandler(new DeviceCmd_enfora());

        /* calamp */
        _addDeviceCommandHandler(new DeviceCmd_calamp());

    }
    
    private DeviceCmdHandler getDeviceCommandHandler(String dcid)
    {
        DCServerConfig dcs = DCServerFactory.getServerConfig(dcid);
        return (dcs != null)? DCMap.get(dcid) : null; // may return null;
    }
    
    // ------------------------------------------------------------------------
    // WebPage interface
    
    public DeviceInfo()
    {
        this.setBaseURI(Track.BASE_URI());
        this.setPageName(PAGE_DEVICE_INFO);
        this.setPageNavigation(new String[] { PAGE_LOGIN, PAGE_MENU_TOP });
        this.setLoginRequired(true);
    }
    
    // ------------------------------------------------------------------------
   
    public String getMenuName(RequestProperties reqState)
    {
        return MenuBar.MENU_ADMIN;
    }

    public String getMenuDescription(RequestProperties reqState, String parentMenuName)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(DeviceInfo.class);
        String devTitles[] = reqState.getDeviceTitles();
        return super._getMenuDescription(reqState,i18n.getString("DeviceInfo.editMenuDesc","View/Edit {0} Information", devTitles));
    }
   
    public String getMenuHelp(RequestProperties reqState, String parentMenuName)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(DeviceInfo.class);
        String devTitles[] = reqState.getDeviceTitles();
        return super._getMenuHelp(reqState,i18n.getString("DeviceInfo.editMenuHelp","View and Edit {0} information", devTitles));
    }

    // ------------------------------------------------------------------------

    public String getNavigationDescription(RequestProperties reqState)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(DeviceInfo.class);
        String devTitles[] = reqState.getDeviceTitles();
        return super._getNavigationDescription(reqState,i18n.getString("DeviceInfo.navDesc","{0} Admin", devTitles));
    }

    public String getNavigationTab(RequestProperties reqState)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(DeviceInfo.class);
        String devTitles[] = reqState.getDeviceTitles();
        return i18n.getString("DeviceInfo.navTab","{0} Admin", devTitles);
    }

    // ------------------------------------------------------------------------
    
    public String[] getChildAclList()
    {
        return _ACL_LIST;
    }

    // ------------------------------------------------------------------------

    private String _filterPhoneNum(String simPhone)
    {
        if (StringTools.isBlank(simPhone)) {
            return "";
        } else {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < simPhone.length(); i++) {
                char ch = simPhone.charAt(i);
                if (Character.isDigit(ch)) {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
    }

    private boolean _showNotificationFields(PrivateLabel privLabel)
    {
        String propShowNotify = PrivateLabel.PROP_DeviceInfo_showNotificationFields;
        String snf = (privLabel != null)? privLabel.getStringProperty(propShowNotify,null) : null;

        /* has notification fields? */
        if (!Device.supportsNotification()) {
            if ((snf != null) && snf.equalsIgnoreCase(StringTools.STRING_TRUE)) {
                Print.logWarn("Property "+propShowNotify+" is 'true', but Device notification fields are not supported");
            }
            return false;
        }
        
        /* show notification fields? */
        if (StringTools.isBlank(snf) || snf.equalsIgnoreCase("default")) {
            return Device.hasRuleFactory(); // default value
        } else {
            return StringTools.parseBoolean(snf,false);
        }
        
    }

    /* update Device table with user entered information */
    private String _updateDeviceTable(RequestProperties reqState)
    {
        final Account      currAcct    = reqState.getCurrentAccount();
        final User         currUser    = reqState.getCurrentUser();
        final PrivateLabel privLabel   = reqState.getPrivateLabel();
        final Device       selDev      = reqState.getSelectedDevice(); // 'selDev' is non-null
        final HttpServletRequest request = reqState.getHttpServletRequest();
        final I18N         i18n        = privLabel.getI18N(DeviceInfo.class);
        final Locale       locale      = privLabel.getLocale();
        final String       devTitles[] = reqState.getDeviceTitles();
        String  msg       = null;
        boolean ntfyOK    = _showNotificationFields(privLabel);
        boolean groupsChg = false;
        String  serverID  = AttributeTools.getRequestString(request, PARM_SERVER_ID         , "");
        String  uniqueID  = AttributeTools.getRequestString(request, PARM_DEV_UNIQ          , "");
        String  devActive = AttributeTools.getRequestString(request, PARM_DEV_ACTIVE        , "");
        String  devDesc   = AttributeTools.getRequestString(request, PARM_DEV_DESC          , "");
        String  devName   = AttributeTools.getRequestString(request, PARM_DEV_NAME          , "");
        String  pushpinID = AttributeTools.getRequestString(request, PARM_ICON_ID           , "");
        String  equipType = AttributeTools.getRequestString(request, PARM_DEV_EQUIP_TYPE    , "");
        String  imeiNum   = AttributeTools.getRequestString(request, PARM_DEV_IMEI          , "");
        String  simPhone  = this._filterPhoneNum(AttributeTools.getRequestString(request,PARM_DEV_SIMPHONE,""));
        String  noteText  = AttributeTools.getRequestString(request, PARM_DEV_NOTES         , "");
        double  fixedLat  = AttributeTools.getRequestDouble(request, PARM_FIXED_LAT         , 0.0);
        double  fixedLon  = AttributeTools.getRequestDouble(request, PARM_FIXED_LON         , 0.0);
        String  ignition  = AttributeTools.getRequestString(request, PARM_IGNITION_INDEX    , "");
        String  grpKeys[] = AttributeTools.getMatchingKeys( request, PARM_DEV_GROUP_);
        String  cstKeys[] = AttributeTools.getMatchingKeys( request, PARM_DEV_CUSTOM_);
        double  rptOdom   = AttributeTools.getRequestDouble(request, PARM_REPORT_ODOM       , 0.0);
        try {
            // unique id
            boolean editUniqID = privLabel.hasWriteAccess(currUser, this.getAclName(_ACL_UNIQUEID));
            if (editUniqID && !selDev.getUniqueID().equals(uniqueID)) {
                if (StringTools.isBlank(uniqueID)) {
                    selDev.setUniqueID("");
                } else {
                    try {
                        Device dev = Transport.loadDeviceByUniqueID(uniqueID);
                        if (dev == null) {
                            selDev.setUniqueID(uniqueID);
                        } else {
                            String devAcctID = dev.getAccountID();
                            String devDevID  = dev.getDeviceID();
                            if (devAcctID.equals(reqState.getCurrentAccountID())) {
                                // same account, this user can fix this himself
                                msg = i18n.getString("DeviceInfo.uniqueIdAlreadyAssignedToDevice",
                                    "UniqueID is already assigned to {0}: {1}", devTitles[0], devDevID);
                                selDev.setError(msg);
                            } else {
                                // different account, this user cannot fix this himself
                                Print.logWarn("UniqueID '%s' already assigned: %s/%s", uniqueID, devAcctID, devDevID);
                                msg = i18n.getString("DeviceInfo.uniqueIdAlreadyAssigned",
                                    "UniqueID is already assigned to another Account");
                                selDev.setError(msg);
                            }
                        }
                    } catch (DBException dbe) {
                        msg = i18n.getString("DeviceInfo.errorReadingUniqueID",
                            "Error while looking for other matching UniqueIDs");
                        selDev.setError(msg);
                    }
                }
            }
            // active
            boolean devActv = ComboOption.parseYesNoText(locale, devActive, true);
            if (selDev.getIsActive() != devActv) { 
                selDev.setIsActive(devActv); 
            }
            // description
            if (!selDev.getDescription().equals(devDesc)) {
                selDev.setDescription(devDesc);
            }
            // display name
            if (!selDev.getDisplayName().equals(devName)) {
                selDev.setDisplayName(devName);
            }
            // equipment type
            if (!selDev.getEquipmentType().equals(equipType)) {
                selDev.setEquipmentType(equipType);
            }
            // IMEI number
            if (!selDev.getImeiNumber().equals(imeiNum)) {
                selDev.setImeiNumber(imeiNum);
            }
            // SIM phone number
            if (!selDev.getSimPhoneNumber().equals(simPhone)) {
                selDev.setSimPhoneNumber(simPhone);
            }
            // Notes
            boolean notesOK = privLabel.getBooleanProperty(PrivateLabel.PROP_DeviceInfo_showNotes,SHOW_NOTES);
            if (notesOK && !selDev.getNotes().equals(noteText)) {
                selDev.setNotes(noteText);
            }
            // Fixed Latitude/Longitude
            if ((fixedLat != 0.0) || (fixedLon != 0.0)) {
                selDev.setFixedLatitude(fixedLat);
                selDev.setFixedLongitude(fixedLon);
            }
            // Ignition index
            if (!StringTools.isBlank(ignition)) {
                String ign = ignition.toLowerCase();
                int ignNdx = -1;
                if (ign.equals("n/a")) {
                    ignNdx = -1;
                } else
                if (ign.startsWith("ign")) {
                    ignNdx = StatusCodes.IGNITION_INPUT_INDEX;
                } else {
                    ignNdx = StringTools.parseInt(ignition,-1);
                }
                selDev.setIgnitionIndex(ignNdx);
            }
            // Pushpin ID
            if (!selDev.getPushpinID().equals(pushpinID)) {
                selDev.setPushpinID(pushpinID);
            }
            // Reported Odometer
            if (rptOdom >= 0.0) {
                Account.DistanceUnits distUnits = Account.getDistanceUnits(currAcct);
                double rptOdomKM  = distUnits.convertToKM(rptOdom);
                double lastOdomKM = selDev.getLastOdometerKM();
                double offsetKM   = rptOdomKM - lastOdomKM;
                if (Math.abs(offsetKM - selDev.getOdometerOffsetKM()) >= 0.1) {
                    selDev.setOdometerOffsetKM(offsetKM);
                }
            }
            // Maintenance Interval
            if (Device.supportsPeriodicMaintenance()) {
              //String  maintLast = AttributeTools.getRequestString(request, PARM_MAINT_LAST    , null);
              //String  maintNext = AttributeTools.getRequestString(request, PARM_MAINT_NEXT    , null);
                Account.DistanceUnits distUnits = Account.getDistanceUnits(currAcct);
                long    mInterval = AttributeTools.getRequestLong(request, PARM_MAINT_INTERVAL  , 0L);
                double  intrvKM   = distUnits.convertToKM((double)mInterval);
                boolean mReset    = !StringTools.isBlank(AttributeTools.getRequestString(request,PARM_MAINT_RESET,null));
                selDev.setMaintIntervalKM0(intrvKM);
                if (mReset) {
                    selDev.setMaintOdometerKM0(selDev.getLastOdometerKM());
                }
                // CheckBox
            }
            // Rule Engine Notification
            if (ntfyOK) {
                String  ruleAllow = AttributeTools.getRequestString(request, PARM_DEV_RULE_ALLOW, null);
                String  ruleEmail = AttributeTools.getRequestString(request, PARM_DEV_RULE_EMAIL, null);
                String  ruleSel   = AttributeTools.getRequestString(request, PARM_DEV_RULE_SEL  , null);
                String  ruleDesc  = AttributeTools.getRequestString(request, PARM_DEV_RULE_DESC , null);
                String  ruleSubj  = AttributeTools.getRequestString(request, PARM_DEV_RULE_SUBJ , null);
                String  ruleText  = AttributeTools.getRequestString(request, PARM_DEV_RULE_TEXT , null);
                String  ruleWrap  = AttributeTools.getRequestString(request, PARM_DEV_RULE_WRAP , null);
                // Allow Notification
                boolean allowNtfy = ComboOption.parseYesNoText(locale, ruleAllow, true);
                if (selDev.getAllowNotify() != allowNtfy) { 
                    selDev.setAllowNotify(allowNtfy); 
                }
                // Notification email
                if (ruleEmail != null) {
                    if (StringTools.isBlank(ruleEmail) || EMail.validateAddresses(ruleEmail)) {
                        if (!selDev.getNotifyEmail().equals(ruleEmail)) {
                            selDev.setNotifyEmail(ruleEmail);
                        }
                    } else {
                        msg = i18n.getString("DeviceInfo.enterEMail","Please enter a valid notification email address");
                        selDev.setError(msg);
                    }
                }
                // notification selector
                if (ruleSel != null) {
                    if (selDev.checkSelectorSyntax(ruleSel)) {
                        // update rule selector (if changed)
                        if (!selDev.getNotifySelector().equals(ruleSel)) {
                            selDev.setNotifySelector(ruleSel);
                        }
                        //selDev.setAllowNotify(!StringTools.isBlank(ruleSel));
                    } else {
                        Print.logInfo("Notification selector has a syntax error: " + ruleSel);
                        msg = i18n.getString("DeviceInfo.ruleError","Notification rule contains a syntax error");
                        selDev.setError(msg);
                    }
                }
                // notification description
                if (ruleDesc != null) {
                    if (!selDev.getNotifyDescription().equals(ruleDesc)) {
                        selDev.setNotifyDescription(ruleDesc);
                    }
                }
                // notification subject
                if (ruleSubj != null) {
                    if (!selDev.getNotifySubject().equals(ruleSubj)) {
                        selDev.setNotifySubject(ruleSubj);
                    }
                }
                // notification message
                if (ruleText != null) {
                    if (!selDev.getNotifyText().equals(ruleText)) {
                        selDev.setNotifyText(ruleText);
                    }
                }
                // notify wrapper
                boolean ntfyWrap = ComboOption.parseYesNoText(locale, ruleWrap, true);
                if (selDev.getNotifyUseWrapper() != ntfyWrap) { 
                    selDev.setNotifyUseWrapper(ntfyWrap); 
                }
            }
            // Custom Attributes
            if (!ListTools.isEmpty(cstKeys)) {
                String oldCustAttr = selDev.getCustomAttributes();
                RTProperties rtp = selDev.getCustomAttributesRTP();
                for (int i = 0; i < cstKeys.length; i++) {
                    String cstKey = cstKeys[i];
                    String rtpVal = AttributeTools.getRequestString(request, cstKey, "");
                    String rtpKey = cstKey.substring(PARM_DEV_CUSTOM_.length());
                    rtp.setString(rtpKey, rtpVal);
                }
                String rtpStr = rtp.toString();
                if (!rtpStr.equals(oldCustAttr)) {
                    //Print.logInfo("Setting custom attributes: " + rtpStr);
                    selDev.setCustomAttributes(rtpStr);
                }
            }
            // DeviceGroups
            if (!selDev.hasError()) {
                String accountID = selDev.getAccountID();
                String deviceID  = selDev.getDeviceID();
                // 'grpKey' may only contain 'checked' items!
                OrderedSet<String> fullGroupSet = reqState.getDeviceGroupList(true);
                // add checked groups
                if (!ListTools.isEmpty(grpKeys)) {
                    for (int i = 0; i < grpKeys.length; i++) {
                        String grpID = grpKeys[i].substring(PARM_DEV_GROUP_.length());
                        if (!grpID.equalsIgnoreCase(DeviceGroup.DEVICE_GROUP_ALL)) {
                            String chkStr = AttributeTools.getRequestString(request,grpKeys[i],"");
                            boolean chked = chkStr.equalsIgnoreCase("on");
                            boolean exists = DeviceGroup.isDeviceInDeviceGroup(accountID, grpID, deviceID);
                            //Print.logInfo("Checking group : " + grpID + " [checked=" + chked + "]");
                            if (chked) {
                                if (!exists) {
                                    DeviceGroup.addDeviceToDeviceGroup(accountID, grpID, deviceID);
                                    groupsChg = true;
                                }
                            } else {
                                if (exists) {
                                    DeviceGroup.removeDeviceFromDeviceGroup(accountID, grpID, deviceID);
                                    groupsChg = true;
                                }
                            }
                            fullGroupSet.remove(grpID);
                        }
                    }
                }
                // delete remaining (unchecked) groups
                for (Iterator i = fullGroupSet.iterator(); i.hasNext();) {
                    String grpID = (String)i.next();
                    if (!grpID.equalsIgnoreCase(DeviceGroup.DEVICE_GROUP_ALL)) {
                        boolean exists = DeviceGroup.isDeviceInDeviceGroup(accountID, grpID, deviceID);
                        //Print.logInfo("Removing group: " + grpID + " [" + exists + "]");
                        if (exists) {
                            DeviceGroup.removeDeviceFromDeviceGroup(accountID, grpID, deviceID);
                            groupsChg = true;
                        }
                    }
                }
            }
            // save
            if (selDev.hasError()) {
                // should stay on same page
                Print.logInfo("An error occured during Edit ...");
            } else
            if (selDev.hasChanged()) {
                selDev.save();
                msg = i18n.getString("DeviceInfo.updatedDevice","{0} information updated", devTitles);
            } else
            if (groupsChg) {
                String grpTitles[] = reqState.getDeviceGroupTitles();
                msg = i18n.getString("DeviceInfo.updatedDeviceGroups","{0} membership updated", grpTitles);
            } else {
                // nothing changed
                Print.logInfo("Nothing has changed for this Device ...");
            }
        } catch (Throwable t) {
            Print.logException("Updating Device", t);
            msg = i18n.getString("DeviceInfo.errorUpdate","Internal error updating {0}", devTitles);
            selDev.setError(msg);
        }
        return msg;
    }

    // ------------------------------------------------------------------------

    /* write html */
    public void writePage(
        final RequestProperties reqState,
        String pageMsg)
        throws IOException
    {
        final HttpServletRequest request = reqState.getHttpServletRequest();
        final PrivateLabel privLabel   = reqState.getPrivateLabel();
        final I18N         i18n        = privLabel.getI18N(DeviceInfo.class);
        final Locale       locale      = privLabel.getLocale();
        final String       devTitles[] = reqState.getDeviceTitles();
        final String       grpTitles[] = reqState.getDeviceGroupTitles();
        final Account      currAcct    = reqState.getCurrentAccount();
        final User         currUser    = reqState.getCurrentUser();
        final String       pageName    = this.getPageName();
        String m = pageMsg;
        boolean error = false;

        /* device */
        OrderedSet<String> devList = reqState.getDeviceList();
        if (devList == null) { devList = new OrderedSet<String>(); }
        Device selDev   = reqState.getSelectedDevice();
        String selDevID = (selDev != null)? selDev.getDeviceID() : "";

        /* ACL allow edit/view */
        boolean allowNew     = privLabel.hasAllAccess(currUser, this.getAclName());
        boolean allowDelete  = allowNew;
        boolean allowEdit    = allowNew  || privLabel.hasWriteAccess(currUser, this.getAclName());
        boolean allowView    = allowEdit || privLabel.hasReadAccess(currUser, this.getAclName());
        boolean allowProp    = allowView && privLabel.getBooleanProperty(PrivateLabel.PROP_DeviceInfo_showPropertiesButton,true);

        /* submit buttons */
        String  submitEdit   = AttributeTools.getRequestString(request, PARM_SUBMIT_EDIT, "");
        String  submitView   = AttributeTools.getRequestString(request, PARM_SUBMIT_VIEW, "");
        String  submitChange = AttributeTools.getRequestString(request, PARM_SUBMIT_CHG , "");
        String  submitNew    = AttributeTools.getRequestString(request, PARM_SUBMIT_NEW , "");
        String  submitDelete = AttributeTools.getRequestString(request, PARM_SUBMIT_DEL , "");
        String  submitQueue  = AttributeTools.getRequestString(request, PARM_SUBMIT_QUE , "");
        String  submitProps  = AttributeTools.getRequestString(request, PARM_SUBMIT_PROP, "");

        /* ACL view/edit rules/notification */
        boolean editRules    = privLabel.hasWriteAccess(currUser, this.getAclName(_ACL_RULES));
        boolean viewRules    = editRules || privLabel.hasReadAccess(currUser, this.getAclName(_ACL_RULES));

        /* command */
        String  deviceCmd    = reqState.getCommandName();
      //boolean refreshList  = deviceCmd.equals(COMMAND_INFO_REFRESH);
        boolean selectDevice = deviceCmd.equals(COMMAND_INFO_SEL_DEVICE);
        boolean newDevice    = deviceCmd.equals(COMMAND_INFO_NEW_DEVICE);
        boolean updateDevice = deviceCmd.equals(COMMAND_INFO_UPD_DEVICE);
        boolean updateProps  = deviceCmd.equals(COMMAND_INFO_UPD_PROPS);
        boolean deleteDevice = false;

        /* ui display */
        boolean uiList       = false;
        boolean uiEdit       = false;
        boolean uiView       = false;
        boolean uiProp       = false;
        
        /* DeviceCmdHandler */
        DeviceCmdHandler dcHandler = null;

        /* pre-qualify commands */
        String newDeviceID = null;
        if (newDevice) {
            if (!allowNew) {
                newDevice = false; // not authorized
            } else {
                HttpServletRequest httpReq = reqState.getHttpServletRequest();
                newDeviceID = AttributeTools.getRequestString(httpReq,PARM_NEW_NAME,"").trim();
                newDeviceID = newDeviceID.toLowerCase();
                if (StringTools.isBlank(newDeviceID)) {
                    m = i18n.getString("DeviceInfo.enterNewDevice","Please enter a new {0} ID.", devTitles);
                    error = true;
                    newDevice = false;
                } else
                if (!WebPageAdaptor.isValidID(reqState, PrivateLabel.PROP_DeviceInfo_validateNewIDs, newDeviceID)) {
                    m = i18n.getString("DeviceInfo.invalidIDChar","ID contains invalid characters");
                    error = true;
                    newDevice = false;
                }
            }
        } else
        if (updateDevice) {
            if (!allowEdit) {
                updateDevice = false; // not authorized
            } else
            if (!SubmitMatch(submitChange,i18n.getString("DeviceInfo.change","Change"))) {
                updateDevice = false;
            }
        } else
        if (updateProps) {
            if (!allowProp) {
                updateProps = false; // not authorized
            } else
            if (!SubmitMatch(submitQueue,i18n.getString("DeviceInfo.queue","Queue"))) {
                updateProps = false; // button not pressed
            } else
            if (selDev == null) {
                m = i18n.getString("DeviceInfo.pleaseSelectDevice","Please select a {0}", devTitles);
                error = true;
                updateProps = false; // no device selected
            } else
            if (StringTools.isBlank(selDev.getDeviceCode())) {
                Print.logInfo("DeviceCode/ServerID is blank");
                updateProps = false;
            } else {
                dcHandler = this.getDeviceCommandHandler(selDev.getDeviceCode());
                if (dcHandler == null) {
                    Print.logWarn("DeviceCmdHandler not found: " + selDev.getDeviceCode());
                    updateProps = false; // not found
                } else
                if (!dcHandler.deviceSupportsCommands(selDev)) {
                    Print.logWarn("DeviceCode/ServerID not supported by handler: " + selDev.getDeviceCode());
                    updateProps = false; // not supported
                }
            }
        } else
        if (selectDevice) {
            if (SubmitMatch(submitDelete,i18n.getString("DeviceInfo.delete","Delete"))) {
                if (!allowDelete) {
                    deleteDevice = false; // not authorized
                } else
                if (selDev == null) {
                    m = i18n.getString("DeviceInfo.pleaseSelectDevice","Please select a {0}", devTitles);
                    error = true;
                    deleteDevice = false; // not selected
                } else {
                    deleteDevice = true;
                }
            } else
            if (SubmitMatch(submitEdit,i18n.getString("DeviceInfo.edit","Edit"))) {
                if (!allowEdit) {
                    uiEdit = false; // not authorized
                } else
                if (selDev == null) {
                    m = i18n.getString("DeviceInfo.pleaseSelectDevice","Please select a {0}", devTitles);
                    error = true;
                    uiEdit = false; // not selected
                } else {
                    uiEdit = true;
                }
            } else
            if (SubmitMatch(submitView,i18n.getString("DeviceInfo.view","View"))) {
                if (!allowView) {
                    uiView = false; // not authorized
                } else
                if (selDev == null) {
                    m = i18n.getString("DeviceInfo.pleaseSelectDevice","Please select a {0}", devTitles);
                    error = true;
                    uiView = false; // not selected
                } else {
                    uiView = true;
                }
            } else
            if (SubmitMatch(submitProps,i18n.getString("DeviceInfo.commands","Commands"))) {
                if (!allowProp) {
                    uiProp = false; // not authorized
                } else
                if (selDev == null) {
                    m = i18n.getString("DeviceInfo.pleaseSelectDevice","Please select a {0}", devTitles);
                    error = true;
                    uiProp = false; // not selected
                } else {
                    dcHandler = this.getDeviceCommandHandler(selDev.getDeviceCode());
                    if (dcHandler == null) {
                        Print.logWarn("DeviceCmdHandler not found: " + selDev.getDeviceCode());
                        m = i18n.getString("DeviceInfo.deviceCommandsNotSupported","{0} Commands not supported", devTitles);
                        error = true;
                        uiProp = false; // not supported
                    } else
                    if (!dcHandler.deviceSupportsCommands(selDev)) {
                        Print.logWarn("DeviceCode/ServerID not supported by handler: " + selDev.getDeviceCode());
                        m = i18n.getString("DeviceInfo.deviceCommandsNotSupported","{0} Commands not supported", devTitles);
                        error = true;
                        uiProp = false; // not supported
                    } else {
                        uiProp = true;
                    }
                }
            }
        }

        /* delete device? */
        if (deleteDevice) {
            // 'selDev' guaranteed non-null here
            try {
                // delete device
                Device.Key devKey = (Device.Key)selDev.getRecordKey();
                Print.logWarn("Deleting Device: " + devKey);
                devKey.delete(true); // will also delete dependencies
                selDevID = "";
                selDev = null;
                reqState.clearDeviceList();
                // select another device
                devList = reqState.getDeviceList();
                if (!ListTools.isEmpty(devList)) {
                    selDevID = devList.get(0);
                    try {
                        selDev = !selDevID.equals("")? Device.getDevice(currAcct, selDevID) : null; // may still be null
                    } catch (DBException dbe) {
                        // ignore
                    }
                }
            } catch (DBException dbe) {
                Print.logException("Deleting Device", dbe);
                m = i18n.getString("DeviceInfo.errorDelete","Internal error deleting {0}", devTitles);
                error = true;
            }
            uiList = true;
        }

        /* new device? */
        if (newDevice) {
            boolean createDeviceOK = true;
            for (int d = 0; d < devList.size(); d++) {
                if (newDeviceID.equalsIgnoreCase(devList.get(d))) {
                    m = i18n.getString("DeviceInfo.alreadyExists","This {0} already exists", devTitles);
                    error = true;
                    createDeviceOK = false;
                    break;
                }
            }
            if (createDeviceOK) {
                try {
                    Device device = Device.createNewDevice(currAcct, newDeviceID, null); // already saved
                    reqState.clearDeviceList();
                    devList  = reqState.getDeviceList();
                    selDev   = device;
                    selDevID = device.getDeviceID();
                    m = i18n.getString("DeviceInfo.createdUser","New {0} has been created", devTitles);
                } catch (DBException dbe) {
                    Print.logException("Deleting Creating", dbe);
                    m = i18n.getString("DeviceInfo.errorCreate","Internal error creating {0}", devTitles);
                    error = true;
                }
            }
            uiList = true;
        }

        /* update the device info? */
        if (updateDevice) {
            // 'selDev' guaranteed non-null here
            selDev.clearChanged();
            m = _updateDeviceTable(reqState);
            if (selDev.hasError()) {
                // stay on this page
                uiEdit = true;
            } else {
                uiList = true;
            }
        }

        /* update properties */
        if (updateProps) {
            // 'selDev' and 'dcHandler' guaranteed non-null here
            m = dcHandler.handleDeviceCommands(reqState, selDev);
            Print.logInfo("Returned Message: " + m);
            error = true;
            uiList = true;
        }

        /* last event from device */
        try {
            EventData evd[] = (selDev != null)? selDev.getLatestEvents(1L,false) : null;
            if ((evd != null) && (evd.length > 0)) {
                reqState.setLastEventTime(new DateTime(evd[0].getTimestamp()));
            }
        } catch (DBException dbe) {
            // ignore
        }
        
        /* PushpinChooser */
        final boolean showPushpinChooser = privLabel.getBooleanProperty(PrivateLabel.PROP_DeviceInfo_showPushpinChooser,false);

        /* Style */
        HTMLOutput HTML_CSS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                String cssDir = DeviceInfo.this.getCssDirectory();
                WebPageAdaptor.writeCssLink(out, reqState, "DeviceInfo.css", cssDir);
                if (showPushpinChooser) {
                    WebPageAdaptor.writeCssLink(out, reqState, "PushpinChooser.css", cssDir);
                }
            }
        };

        /* JavaScript */
        HTMLOutput HTML_JS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                MenuBar.writeJavaScript(out, pageName, reqState);
                JavaScriptTools.writeJSInclude(out, JavaScriptTools.qualifyJSFileRef(SORTTABLE_JS));
                JavaScriptTools.writeJSInclude(out, JavaScriptTools.qualifyJSFileRef("DeviceInfo.js"));
                if (showPushpinChooser) {
                    PushpinIcon.writePushpinChooserJS(out, reqState, true);
                }
            }
        };

        /* Content */
        final Device _selDev       = selDev; // may be null !!!
        final OrderedSet<String> _deviceList = devList;
        final String _selDevID     = selDevID;
        final boolean _allowEdit   = allowEdit;
        final boolean _allowView   = allowView;
        final boolean _allowProp   = allowProp;
        final boolean _allowNew    = allowNew;
        final boolean _allowDelete = allowDelete;
        final boolean _uiProp      = _allowProp && uiProp;
        final boolean _uiEdit      = _allowEdit && uiEdit;
        final boolean _uiView      = _uiEdit || uiView;
        final boolean _uiList      = uiList || (!_uiEdit && !_uiView && !_uiProp);
        HTMLOutput HTML_CONTENT    = null;
        if (_uiList) {

            HTML_CONTENT = new HTMLOutput(CommonServlet.CSS_CONTENT_FRAME, m) {
                public void write(PrintWriter out) throws IOException {
                    String selectURL  = DeviceInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                    String refreshURL = DeviceInfo.this.encodePageURL(reqState); // TODO: add "refresh" page command
                    //String editURL    = DeviceInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                    String newURL     = DeviceInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                    
                    /* show expected ACKs */
                    boolean showAcks = privLabel.getBooleanProperty(PrivateLabel.PROP_DeviceInfo_showExpectedAcks,false);

                    // frame header
                    String frameTitle = _allowEdit? 
                        i18n.getString("DeviceInfo.viewEditDevice","View/Edit {0} Information", devTitles) : 
                        i18n.getString("DeviceInfo.viewDevice","View {0} Information", devTitles);
                    out.write("<span class='"+CommonServlet.CSS_MENU_TITLE+"'>"+frameTitle+"</span>&nbsp;\n");
                    out.write("<a href='"+refreshURL+"'><span class=''>"+i18n.getString("DeviceInfo.refresh","Refresh")+"</a>\n");
                    out.write("<br/>\n");
                    out.write("<hr>\n");

                    // device selection table (Select, Device ID, Description, ...)
                    out.write("<h1 class='"+CommonServlet.CSS_ADMIN_SELECT_TITLE+"'>"+i18n.getString("DeviceInfo.selectDevice","Select a {0}",devTitles)+":</h1>\n");
                    out.write("<div style='margin-left:25px;'>\n");
                    out.write("<form name='"+FORM_DEVICE_SELECT+"' method='post' action='"+selectURL+"' target='_top'>");
                    out.write("<input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_SEL_DEVICE+"'/>");
                    out.write("<table class='"+CommonServlet.CSS_ADMIN_SELECT_TABLE+"' cellspacing=0 cellpadding=0 border=0>\n");
                    out.write(" <thead>\n");
                    out.write("  <tr class='" +CommonServlet.CSS_ADMIN_TABLE_HEADER_ROW+"'>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL_SEL+"' nowrap>"+FilterText(i18n.getString("DeviceInfo.select","Select"))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+FilterText(i18n.getString("DeviceInfo.deviceID","{0} ID",devTitles))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+FilterText(i18n.getString("DeviceInfo.uniqueID","Unique ID"))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+FilterText(i18n.getString("DeviceInfo.decription","Description",devTitles))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+FilterText(i18n.getString("DeviceInfo.devEquipType","Equipment\nType"))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+FilterText(i18n.getString("DeviceInfo.simPhoneNumber","SIM Phone#"))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+FilterText(i18n.getString("DeviceInfo.devServerID","Server ID"))+"</th>\n");
                    if (showAcks) {
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+FilterText(i18n.getString("DeviceInfo.ackExpected","Expecting\nACK"))+"</th>\n");
                    }
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+FilterText(i18n.getString("DeviceInfo.active","Active"))+"</th>\n");
                    out.write("  </tr>\n");
                    out.write(" </thead>\n");
                    out.write(" <tbody>\n");
                    Device deviceRcd[] = new Device[_deviceList.size()];
                    for (int d = 0; d < _deviceList.size(); d++) {
                        String rowClass = ((d & 1) == 0)? 
                            CommonServlet.CSS_ADMIN_TABLE_BODY_ROW_ODD : CommonServlet.CSS_ADMIN_TABLE_BODY_ROW_EVEN;
                        out.write("  <tr class='"+rowClass+"'>\n");
                        try {
                            Device dev = Device.getDevice(currAcct, _deviceList.get(d));
                            if (dev != null) {
                                String deviceID     = FilterText(dev.getDeviceID());
                                String uniqueID     = FilterText(dev.getUniqueID());
                                String deviceDesc   = FilterText(dev.getDescription());
                                String equipType    = FilterText(dev.getEquipmentType());
                                String imeiNum      = FilterText(dev.getImeiNumber());
                                String simPhone     = FilterText(dev.getSimPhoneNumber());
                                String devCode      = FilterText(dev.getDeviceCode());
                                String pendingACK   = FilterText(dev.isExpectingCommandAck()?ComboOption.getYesNoText(locale,true):"");
                                String active       = FilterText(ComboOption.getYesNoText(locale,dev.isActive()));
                                String checked      = _selDevID.equals(dev.getDeviceID())? "checked" : "";
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL_SEL+"' "+SORTTABLE_SORTKEY+"='"+d+"'><input type='radio' name='"+PARM_DEVICE+"' id='"+deviceID+"' value='"+deviceID+"' "+checked+"></td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap><label for='"+deviceID+"'>"+deviceID+"</label></td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+uniqueID+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+deviceDesc+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+equipType+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+simPhone+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+devCode+"</td>\n");
                                if (showAcks) {
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap style='color:red'>"+pendingACK+"</td>\n");
                                }
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+active+"</td>\n");
                            }
                        } catch (DBException dbe) {
                            deviceRcd[d] = null;
                        }
                        out.write("  </tr>\n");
                    }
                    out.write(" </tbody>\n");
                    out.write("</table>\n");
                    out.write("<table cellpadding='0' cellspacing='0' border='0' style='width:95%; margin-top:5px; margin-left:5px; margin-bottom:5px;'>\n");
                    out.write("<tr>\n");
                    if (_allowView  ) { 
                        out.write("<td style='padding-left:5px;'>");
                        out.write("<input type='submit' name='"+PARM_SUBMIT_VIEW+"' value='"+i18n.getString("DeviceInfo.view","View")+"'>");
                        out.write("</td>\n"); 
                    }
                    if (_allowEdit  ) { 
                        out.write("<td style='padding-left:5px;'>");
                        out.write("<input type='submit' name='"+PARM_SUBMIT_EDIT+"' value='"+i18n.getString("DeviceInfo.edit","Edit")+"'>");
                        out.write("</td>\n"); 
                    }
                    if (_allowProp  ) { 
                        out.write("<td style='padding-left:5px;'>");
                        out.write("<input type='submit' name='"+PARM_SUBMIT_PROP+"' value='"+i18n.getString("DeviceInfo.properties","Properties")+"'>");
                        out.write("</td>\n"); 
                    }
                    out.write("<td style='width:100%; text-align:right; padding-right:10px;'>");
                    if (_allowDelete) {
                        out.write("<input type='submit' name='"+PARM_SUBMIT_DEL+"' value='"+i18n.getString("DeviceInfo.delete","Delete")+"' "+Onclick_ConfirmDelete(locale)+">");
                    } else {
                        out.write("&nbsp;");
                    }
                    out.write("</td>\n"); 
                    out.write("</tr>\n");
                    out.write("</table>\n");
                    out.write("</form>\n");
                    out.write("</div>\n");
                    out.write("<hr>\n");

                    /* new device */
                    if (_allowNew) {
                    out.write("<h1 class='"+CommonServlet.CSS_ADMIN_SELECT_TITLE+"'>"+i18n.getString("DeviceInfo.createNewDevice","Create a new device")+":</h1>\n");
                    out.write("<div style='margin-top:5px; margin-left:5px; margin-bottom:5px;'>\n");
                    out.write("<form name='"+FORM_DEVICE_NEW+"' method='post' action='"+newURL+"' target='_top'>");
                    out.write(" <input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_NEW_DEVICE+"'/>");
                    out.write(i18n.getString("DeviceInfo.deviceID","{0} ID",devTitles)+": <input type='text' name='"+PARM_NEW_NAME+"' value='' size='32' maxlength='32'><br>\n");
                    out.write(" <input type='submit' name='"+PARM_SUBMIT_NEW+"' value='"+i18n.getString("DeviceInfo.new","New")+"' style='margin-top:5px; margin-left:10px;'>\n");
                    out.write("</form>\n");
                    out.write("</div>\n");
                    out.write("<hr>\n");
                    }

                }
            };

        } else
        if (_uiEdit || _uiView) {

            final boolean _editUniqID  = _uiEdit && privLabel.hasWriteAccess(currUser, this.getAclName(_ACL_UNIQUEID));
            final boolean _viewUniqID  = _editUniqID || privLabel.hasReadAccess(currUser, this.getAclName(_ACL_UNIQUEID));
            final boolean _editRules   = _uiEdit && editRules;
            final boolean _viewRules   = _uiView && viewRules;
            HTML_CONTENT = new HTMLOutput(CommonServlet.CSS_CONTENT_FRAME, m) {
                public void write(PrintWriter out) throws IOException {
                    String editURL   = DeviceInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                    boolean ntfyOK   = _viewRules && _showNotificationFields(privLabel);
                    boolean ntfyEdit = ntfyOK && _editRules;
                    boolean ignOK    = true;
                    boolean ppidOK   = true;
                    boolean notesOK  = privLabel.getBooleanProperty(PrivateLabel.PROP_DeviceInfo_showNotes,SHOW_NOTES);
                    boolean fixLocOK = (_selDev != null) && _selDev.hasFixedLocation() && 
                        privLabel.getBooleanProperty(PrivateLabel.PROP_DeviceInfo_showFixedLocation,SHOW_FIXED_LOCATION);

                    /* distance units description */
                    Account.DistanceUnits distUnits = Account.getDistanceUnits(currAcct);
                    String distUnitsStr = distUnits.toString(locale);
                    
                    /* custom attributes */
                    Collection<String> customKeys = new OrderedSet<String>();
                    Collection<String> ppKeys = privLabel.getPropertyKeys(PrivateLabel.PROP_DeviceInfo_custom_);
                    for (String ppKey : ppKeys) {
                        customKeys.add(ppKey.substring(PrivateLabel.PROP_DeviceInfo_custom_.length()));
                    }
                    if (_selDev != null) {
                        customKeys.addAll(_selDev.getCustomAttributeKeys());
                    }

                    /* last connect times */
                    String lastConnectTime = (_selDev != null)? reqState.formatDateTime(_selDev.getLastTotalConnectTime()) : "";
                    if (StringTools.isBlank(lastConnectTime)) { lastConnectTime = i18n.getString("DeviceInfo.neverConnected","never"); }
                    String lastEventTime   = reqState.formatDateTime(reqState.getLastEventTime());
                    if (StringTools.isBlank(lastEventTime  )) { lastEventTime   = i18n.getString("DeviceInfo.noLastEvent"   ,"none" ); }

                    // frame header
                    String frameTitle = _allowEdit? 
                        i18n.getString("DeviceInfo.viewEditDevice","View/Edit {0} Information", devTitles) : 
                        i18n.getString("DeviceInfo.viewDevice","View {0} Information", devTitles);
                    out.write("<span class='"+CommonServlet.CSS_MENU_TITLE+"'>"+frameTitle+"</span><br/>\n");
                    out.write("<hr>\n");

                    /* start of form */
                    out.write("<form name='"+FORM_DEVICE_EDIT+"' method='post' action='"+editURL+"' target='_top'>\n");
                    out.write("<input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_UPD_DEVICE+"'/>\n");

                    /* Device fields */
                    ComboOption devActive = ComboOption.getYesNoOption(locale, ((_selDev != null) && _selDev.isActive()));
                    out.println("<table class='"+CommonServlet.CSS_ADMIN_VIEW_TABLE+"' cellspacing='0' callpadding='0' border='0'>");
                    out.println(FormRow_TextField(PARM_DEVICE           , false      , i18n.getString("DeviceInfo.deviceID","{0} ID",devTitles)+":"        , _selDevID, 30, 30));
                    out.println(FormRow_TextField(PARM_SERVER_ID        , false      , i18n.getString("DeviceInfo.serverID","Server ID")+":"               , (_selDev!=null)?_selDev.getDeviceCode():"", 18, 30));
                    if (_viewUniqID) {
                        out.println(FormRow_TextField(PARM_DEV_UNIQ     , _editUniqID, i18n.getString("DeviceInfo.uniqueID","Unique ID")+":"               , (_selDev!=null)?_selDev.getUniqueID():"", 30, 30));
                    }
                    out.println(FormRow_ComboBox (PARM_DEV_ACTIVE       , _uiEdit    , i18n.getString("DeviceInfo.active","Active")+":"                    , devActive, ComboMap.getYesNoMap(locale), "", -1));
                    out.println(FormRow_TextField(PARM_DEV_DESC         , _uiEdit    , i18n.getString("DeviceInfo.deviceDesc","{0} Description",devTitles) +":", (_selDev!=null)?_selDev.getDescription():"", 40, 64));
                    out.println(FormRow_TextField(PARM_DEV_NAME         , _uiEdit    , i18n.getString("DeviceInfo.displayName","Short Name") +":"          , (_selDev!=null)?_selDev.getDisplayName():"", 16, 64));
                    out.println(FormRow_TextField(PARM_DEV_EQUIP_TYPE   , _uiEdit    , i18n.getString("DeviceInfo.equipmentType","Equipment Type") +":"    , (_selDev!=null)?_selDev.getEquipmentType():"", 30, 40));
                    out.println(FormRow_TextField(PARM_DEV_IMEI         , _uiEdit    , i18n.getString("DeviceInfo.imeiNumber","IMEI Number") +":"          , (_selDev!=null)?_selDev.getImeiNumber():"", 16, 18));
                    out.println(FormRow_TextField(PARM_DEV_SIMPHONE     , _uiEdit    , i18n.getString("DeviceInfo.simPhoneNumber","SIM Phone#") +":"       , (_selDev!=null)?_selDev.getSimPhoneNumber():"", 14, 18));
                    if (ppidOK) {
                        String ppDesc = i18n.getString("DeviceInfo.mapPushpinID","{0} Pushpin ID",grpTitles)+":";
                        String ppid = (_selDev != null)? _selDev.getPushpinID() : "";
                        if (showPushpinChooser) {
                            String ID_ICONSEL = "PushpinChooser";
                            String onclick    = _uiEdit? "javascript:ppcShowPushpinChooser('"+ID_ICONSEL+"')" : null;
                            out.println(FormRow_TextField(ID_ICONSEL, PARM_ICON_ID, _uiEdit, ppDesc, ppid, onclick, 14, 14, null));
                        } else {
                            ComboMap ppList = new ComboMap(reqState.getMapProviderPushpinIDs());
                            ppList.insert(""); // insert a blank as the first entry
                            out.println(FormRow_ComboBox(PARM_ICON_ID, _uiEdit, ppDesc, ppid, ppList, "", -1));
                        }
                    }
                    if (ignOK) {
                        ComboMap ignList = new ComboMap(new String[] { "n/a", "0", "1", "2", "3", "4", "5", "6", "7", "ign" });
                        int ignNdx = (_selDev != null)? _selDev.getIgnitionIndex() : -1;
                        String ignSel = "";
                        if (ignNdx < 0) {
                            ignSel = "n/a";
                        } else
                        if (ignNdx == StatusCodes.IGNITION_INPUT_INDEX) {
                            ignSel = "ign";
                        } else {
                            ignSel = String.valueOf(ignNdx);
                        }
                        out.println(FormRow_ComboBox( PARM_IGNITION_INDEX, _uiEdit   , i18n.getString("DeviceInfo.ignitionIndex","Ignition Input") +":" , ignSel, ignList, "", -1, i18n.getString("DeviceInfo.ignitionIndexDesc","(ignition input line, if applicable)")));
                    }
                    if (Device.supportsPeriodicMaintenance()) {
                        // add separator before odometer if maintenance is supported
                        out.println(FormRow_Separator());
                    }
                    if (Device.supportsLastOdometer()) {
                        double odomKM   = (_selDev != null)? _selDev.getLastOdometerKM() : 0.0;
                        double offsetKM = (_selDev != null)? _selDev.getOdometerOffsetKM() : 0.0;
                        double rptOdom  = distUnits.convertFromKM(odomKM + offsetKM);
                        String odomStr  = StringTools.format(rptOdom, "0.0");
                        out.println(FormRow_TextField(PARM_REPORT_ODOM   , _uiEdit   , i18n.getString("DeviceInfo.reportOdometer","Reported Odometer") +":" , odomStr, 10, 11, distUnitsStr));
                    }
                    if (Device.supportsPeriodicMaintenance()) {
                        double lastMaintKM  = (_selDev != null)? _selDev.getMaintOdometerKM0() : 0.0;
                        double offsetKM     = (_selDev != null)? _selDev.getOdometerOffsetKM() : 0.0;
                        double lastMaint    = distUnits.convertFromKM(lastMaintKM + offsetKM);
                        String lastMaintStr = StringTools.format(lastMaint, "0.0");
                        out.println(FormRow_TextField(PARM_MAINT_LAST    , false     , i18n.getString("DeviceInfo.mainLast","Last Maintenance") +":" , lastMaintStr, 10, 11, distUnitsStr));
                        double intrvKM = (_selDev != null)? _selDev.getMaintIntervalKM0() : 0.0;
                        double intrv   = distUnits.convertFromKM(intrvKM);
                        out.print("<tr>");
                        out.print("<td class='"+CommonServlet.CSS_ADMIN_VIEW_TABLE_HEADER+"' nowrap>"+i18n.getString("DeviceInfo.maintInterval","Maintenance Interval")+":</td>");
                        out.print("<td class='"+CommonServlet.CSS_ADMIN_VIEW_TABLE_DATA+"'>");
                        out.print(Form_TextField(PARM_MAINT_INTERVAL, _uiEdit, String.valueOf((long)intrv), 10, 11));
                        out.print(" &nbsp;" + distUnitsStr);
                        out.print(" &nbsp;&nbsp;(" + i18n.getString("DeviceInfo.maintReset","Check to Reset Service") + " ");
                        out.print(Form_CheckBox(PARM_MAINT_RESET, PARM_MAINT_RESET, _uiEdit, false, null, null));
                        out.print(")</td>");
                        out.print("</tr>\n");
                    }
                    if (ntfyOK) {
                        ComboOption allowNotfy = ComboOption.getYesNoOption(locale, ((_selDev!=null) && _selDev.getAllowNotify()));
                        String emailText  = (_selDev!=null)? StringTools.decodeNewline(_selDev.getNotifyText()) : "";
                        out.println(FormRow_Separator());
                        out.println(FormRow_ComboBox (PARM_DEV_RULE_ALLOW, ntfyEdit  , i18n.getString("DeviceInfo.notifyAllow","Notify Enable")+":"     , allowNotfy, ComboMap.getYesNoMap(locale), "", -1));
                        out.println(FormRow_TextField(PARM_DEV_RULE_EMAIL, ntfyEdit  , i18n.getString("DeviceInfo.notifyEMail","Notify Email")+":"      , (_selDev!=null)?_selDev.getNotifyEmail():"", 60, 80));
                        out.println(FormRow_TextField(PARM_DEV_RULE_SEL  , ntfyEdit  , i18n.getString("DeviceInfo.notifyRule" ,"Notify Rule")+":"       , (_selDev!=null)?_selDev.getNotifySelector():"", 75, 90));
                      //out.println(FormRow_TextField(PARM_DEV_RULE_DESC , ntfyEdit  , i18n.getString("DeviceInfo.notifyDesc" ,"Notify Description")+":", (_selDev!=null)?_selDev.getNotifyDescription():"", 75, 90));
                        out.println(FormRow_TextField(PARM_DEV_RULE_SUBJ , ntfyEdit  , i18n.getString("DeviceInfo.notifySubj" ,"Notify Subject")+":"    , (_selDev!=null)?_selDev.getNotifySubject():"", 75, 90));
                        out.println(FormRow_TextArea( PARM_DEV_RULE_TEXT , ntfyEdit  , i18n.getString("DeviceInfo.notifyText" ,"Notify Message")+":"    , emailText, 5, 70));
                        //if (privLabel.hasEventNotificationEMail()) {
                            boolean editWrap = ntfyEdit; // && privLabel.hasEventNotificationEMail()
                            boolean useEMailWrapper = (_selDev!=null) && _selDev.getNotifyUseWrapper();
                            ComboOption wrapEmail = ComboOption.getYesNoOption(locale, useEMailWrapper);
                            out.println(FormRow_ComboBox(PARM_DEV_RULE_WRAP, editWrap, i18n.getString("DeviceInfo.notifyWrap" ,"Notify Use Wrapper")+":", wrapEmail, ComboMap.getYesNoMap(locale), "", -1, i18n.getString("DeviceInfo.seeEventNotificationEMail","(See 'EventNotificationEMail' tag in 'private.xml')")));
                        //} else {
                        //    Print.logInfo("PrivateLabel EventNotificationEMail Subject/Body not defined");
                        //}
                    }
                    if (fixLocOK) {
                        out.println(FormRow_Separator());
                        out.println(FormRow_TextField(PARM_FIXED_LAT    , _uiEdit    , i18n.getString("DeviceInfo.fixedLatitude","Fixed Latitude") +":"    , (_selDev!=null)?String.valueOf(_selDev.getFixedLatitude()) :"0.0",  9, 10));
                        out.println(FormRow_TextField(PARM_FIXED_LON    , _uiEdit    , i18n.getString("DeviceInfo.fixedLongitude","Fixed Longtitude") +":" , (_selDev!=null)?String.valueOf(_selDev.getFixedLongitude()):"0.0", 10, 11));
                    }
                    if (SHOW_LAST_CONNECT) {
                        out.println(FormRow_Separator());
                        out.println(FormRow_TextField(PARM_DEV_LAST_CONNECT , false  , i18n.getString("DeviceInfo.lastConnect","Last Connect")+":"      , lastConnectTime, 30, 30, i18n.getString("DeviceInfo.serverTime","(Server time)"))); // read-only
                        out.println(FormRow_TextField(PARM_DEV_LAST_EVENT   , false  , i18n.getString("DeviceInfo.lastEvent"  ,"Last Event"  )+":"      , lastEventTime  , 30, 30, i18n.getString("DeviceInfo.deviceTime","(Device time)"))); // read-only
                    }
                    if (notesOK) {
                        String noteText = (_selDev!=null)? StringTools.decodeNewline(_selDev.getNotes()) : "";
                        out.println(FormRow_Separator());
                        out.println(FormRow_TextArea(PARM_DEV_NOTES, _uiEdit, i18n.getString("DeviceInfo.notes" ,"Notes")+":", noteText, 5, 70));
                    }
                    if (!ListTools.isEmpty(customKeys)) {
                        out.println(FormRow_Separator());
                        for (String key : customKeys) {
                            String desc  = privLabel.getStringProperty(PrivateLabel.PROP_DeviceInfo_custom_ + key, key);
                            String value = (_selDev != null)? _selDev.getCustomAttribute(key) : "";
                            out.println(FormRow_TextField(PARM_DEV_CUSTOM_ + key, _uiEdit, desc + ":", value, 40, 50));
                        }
                    }
                    out.println(FormRow_Separator());
                    out.println("</table>");
                    
                    /* DeviceGroup membership */
                    out.write("<span style='margin-left:4px; margin-top:10px; font-weight:bold;'>");
                    out.write(  i18n.getString("DeviceInfo.groupMembership","{0} Membership:",grpTitles));
                    out.write(  "</span>\n");
                    out.write("<div style='border: 1px solid black; margin: 2px 20px 5px 10px; height:80px; width:400px; overflow-x: hidden; overflow-y: scroll;'>\n");
                    out.write("<table>\n");
                    final OrderedSet<String> grpList = reqState.getDeviceGroupList(true);
                    for (int g = 0; g < grpList.size(); g++) {
                        String grp  = grpList.get(g);
                        String name = PARM_DEV_GROUP_ + grp;
                        String desc = reqState.getDeviceGroupDescription(grp,false/*!rtnDispName*/);
                        desc += desc.equals(grp)? ":" : (" ["+grp+"]:");
                        out.write("<tr><td>"+desc+"</td><td>");
                        if (grp.equalsIgnoreCase(DeviceGroup.DEVICE_GROUP_ALL)) {
                            out.write(Form_CheckBox(null,name,false,true,null,null));
                        } else {
                            boolean devInGroup = (_selDev != null)? 
                                DeviceGroup.isDeviceInDeviceGroup(_selDev.getAccountID(), grp, _selDevID) :
                                false;
                            out.write(Form_CheckBox(null,name,_uiEdit,devInGroup,null,null));
                        }
                        out.write("</td></tr>\n");
                    }
                    out.write("</table>\n");
                    out.write("</div>\n");

                    /* end of form */
                    out.write("<hr style='margin-bottom:5px;'>\n");
                    out.write("<span style='padding-left:10px'>&nbsp;</span>\n");
                    if (_uiEdit) {
                        out.write("<input type='submit' name='"+PARM_SUBMIT_CHG+"' value='"+i18n.getString("DeviceInfo.change","Change")+"'>\n");
                        out.write("<span style='padding-left:10px'>&nbsp;</span>\n");
                        out.write("<input type='button' name='"+PARM_BUTTON_CANCEL+"' value='"+i18n.getString("DeviceInfo.cancel","Cancel")+"' onclick=\"javascript:openURL('"+editURL+"','_top');\">\n");
                    } else {
                        out.write("<input type='button' name='"+PARM_BUTTON_BACK+"' value='"+i18n.getString("DeviceInfo.back","Back")+"' onclick=\"javascript:openURL('"+editURL+"','_top');\">\n");
                    }
                    out.write("</form>\n");
                    
                }
            };

        } else
        if (_uiProp) {

            final boolean _editProps = _allowProp && (selDev != null);
            final DeviceCmdHandler _dcHandler = dcHandler; // non-null here
            HTML_CONTENT = new HTMLOutput(CommonServlet.CSS_CONTENT_FRAME, m) {
                public void write(PrintWriter out) throws IOException {

                    /* frame title */
                    String frameTitle = i18n.getString("DeviceInfo.setDeviceProperties","({0}) Set {1} Properties", 
                        _dcHandler.getServerDescription(), devTitles[0]);
                    out.write("<span class='"+CommonServlet.CSS_MENU_TITLE+"'>"+frameTitle+"</span><br/>\n");
                    out.write("<hr>\n");

                    /* Device Command/Properties content */
                    String editURL = DeviceInfo.this.encodePageURL(reqState);//, Track.BASE_URI());
                    _dcHandler.writeCommandForm(out, reqState, _selDev, editURL, _editProps);

                }
            };

        }

        /* write frame */
        String onloadAlert = error? JS_alert(true,m) : null;
        String onload = _uiProp? "javascript:devCommandOnLoad();" : onloadAlert;
        CommonServlet.writePageFrame(
            reqState,
            onload,null,                // onLoad/onUnload
            HTML_CSS,                   // Style sheets
            HTML_JS,                    // Javascript
            null,                       // Navigation
            HTML_CONTENT);              // Content

    }
    
    // ------------------------------------------------------------------------
}
