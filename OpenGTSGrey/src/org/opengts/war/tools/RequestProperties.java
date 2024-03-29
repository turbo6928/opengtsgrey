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
//  2007/03/11  Martin D. Flynn
//     -Added 'isLoggedIn()' as convenience to determine if an account user is
//      currently logged-in.
//  2007/03/30  Martin D. Flynn
//     -Added access control support
//     -Added 'User' table support (not yet fully supported)
//  2007/05/20  Martin D. Flynn
//     -Added 'getMapProperties' method.
//  2007/06/13  Martin D. Flynn
//     -Added support for browsers with disabled cookies (see "setCookiesEnabled")
//     -Added User ACL convenience methods "hasReadAccess" & "hasWriteAccess".
//  2007/07/27  Martin D. Flynn
//     -Added support for MapDimension
//  2007/11/28  Martin D. Flynn
//     -Added convenience method 'getMapEvents()' for returning the mappable events
//      for the current selection
//     -Increase default map size
//  2007/12/13  Martin D. Flynn
//     -Added methods to allow customizing 'Device', 'Device Group', and 'Entity' 
//      titles.
//  2008/04/11  Martin D. Flynn
//     -Removed 'getMapProperties' method (the MapProvider now contains its own properties)
//  2008/07/21  Martin D. Flynn
//     -Optimized the "StringTools.KeyValueMap" 'getKeyValue' lookup, and added some 
//      additional keys.
//  2008/08/15  Martin D. Flynn
//     -The 'admin' user [see "User.getAdminUserID()"] is always granted "ALL" access.
//  2009/02/20  Martin D. Flynn
//     -Renamed 'setCookiesEnabled' to 'setCookiesRequired'
//  2009/05/24  Martin D. Flynn
//     -Added "i18n.User" property string key
//  2009/09/23  Martin D. Flynn
//     -Added "isSoapRequest()" method
// ----------------------------------------------------------------------------
package org.opengts.war.tools;

import java.util.*;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.geocoder.*;

import org.opengts.Version;
import org.opengts.db.*;
import org.opengts.db.AclEntry.AccessLevel;
import org.opengts.db.tables.*;

import org.opengts.war.track.Constants;
import org.opengts.war.track.page.UserInfo;
import org.opengts.war.track.page.TrackMap;

public class RequestProperties
    implements StringTools.KeyValueMap
{

    // ------------------------------------------------------------------------
    // PrivateLabel used when none are defined

    public  static final PrivateLabel NullPrivateLabel  = new PrivateLabel("null");

    // ------------------------------------------------------------------------
    // Demo account information
    // This is the section that specifies the date ranges for the sample data found
    // in the "sampleData" directory.

    private static String       DemoAccount             = "demo";
    private static String       DemoDevice_1            = "demo";
    private static String       DemoRange_1[]           = new String[] { "2007/03/13", "2007/03/13" };
    private static String       DemoDevice_2            = "demo2";
    private static String       DemoRange_2[]           = new String[] { "2007/03/13", "2007/03/13" };

    // ------------------------------------------------------------------------
    // Web-page elements (see 'getPageFrameSection', 'writePageFrameSection')
    
    public  static final int    PAGE_FRAME_HEADER           = 0x0001;
    public  static final int    PAGE_FRAME_NAVIGATION       = 0x0002;
    public  static final int    PAGE_FRAME_FOOTER           = 0x0004;
    public  static final int    PAGE_FRAME_LEFT             = 0x0010;
    public  static final int    PAGE_FRAME_RIGHT            = 0x0020;
    public  static final int    PAGE_FRAME_CONTENT          = 0x0100;
    public  static final int    PAGE_FRAME_CONTENT_MENUBAR  = 0x0200;
    public  static final int    PAGE_FRAME_ALL              = 
        PAGE_FRAME_HEADER | 
        PAGE_FRAME_NAVIGATION | 
        PAGE_FRAME_LEFT | 
        PAGE_FRAME_CONTENT | 
        PAGE_FRAME_CONTENT_MENUBAR | 
        PAGE_FRAME_RIGHT | 
        PAGE_FRAME_FOOTER;

    // ------------------------------------------------------------------------
    // Instance vars

    private HttpServletResponse response                = null;
    private HttpServletRequest  request                 = null;
    private boolean             isSoapRequest           = false;
    
    private String              baseURI                 = null;
    
    private String              webPageURI              = null; // non-null to override default
    
    private boolean             cookiesRequired         = true; // default to true

    private String              ipAddress               = "";

    private String              pageName                = "";
    private String              pageNavHTML             = null;
    private int                 pageFrameSections       = PAGE_FRAME_ALL;

    private String              cmdName                 = "";
    private String              cmdArg                  = "";
    
    private boolean             isFleet                 = false;

    private PrivateLabel        privLabel               = null;
    
    private MapProvider         mapProvider             = null;

    private Account             account                 = null;
    private User                user                    = null;
    
    private Device              selDevice               = null;
    private String              selDeviceID             = null;
    private boolean             isActualSelDevID        = false;
    
    private DeviceGroup         selDeviceGroup          = null;
    private String              selDeviceGroupID        = null;
    private boolean             isActualSelGrpID        = false;

    private boolean             loginErrorAlert         = false;

    private String              userList[]              = null;
    private OrderedSet<String>  devList                 = null;
    private OrderedSet<String>  devGrpSet               = null;
    private OrderedSet<String>  devGrpSetAll            = null;

    private DateTime            dateFrom                = null;
    private DateTime            dateTo                  = null;
    private TimeZone            timeZone                = null;
    private String              timeZoneShortStr        = null;
    private String              timeZoneLongStr         = null;

    private long                eventLimitCnt           = 100L;
    private EventData.LimitType eventLimitType          = EventData.LimitType.LAST;
    private DateTime            lastEvent               = null;
    
    private int                 enableDemo              = -1; // tri-state
    
    private boolean             encodeEmailHTML         = false;

    /* new ReguestProperties instance */
    public RequestProperties()
    {
        super();
    }
    
    // ------------------------------------------------------------------------

    public boolean getEncodeEMailHTML()
    {
        return this.encodeEmailHTML;
    }
    
    public void setEncodeEMailHTML(boolean state)
    {
        this.encodeEmailHTML = state;
    }
    
    // ------------------------------------------------------------------------

    /* set the base URI */
    public void setBaseURI(String baseUri)
    {
        this.baseURI = baseUri;
    }
    
    /* get the base URI */
    public String getBaseURI()
    {
        return (this.baseURI != null)? this.baseURI : "/";
    }
    
    // ------------------------------------------------------------------------

    /* set the HttpServletRequest instance for this session */
    public void setHttpServletResponse(HttpServletResponse response)
    {
        this.response = response;
    }
    
    /* return the HttpServletRequest instance for this session */
    public HttpServletResponse getHttpServletResponse()
    {
        return this.response;
    }

    // ------------------------------------------------------------------------

    /* set the HttpServletRequest instance for this session */
    public void setHttpServletRequest(HttpServletRequest request)
    {
        this.request = request;
    }
    
    /* return the HttpServletRequest instance for this session */
    public HttpServletRequest getHttpServletRequest()
    {
        return this.request;
    }
    
    /* return the request URL */
    // Should not be used!
    //public String getHttpServletRequestURL()
    //{
    //    if (this.request != null) {
    //        return this.request.getRequestURL().toString();
    //    } else {
    //        return "";
    //    }
    //}
    
    /* return the URL for this request */
    public URIArg getHttpServletRequestURIArg(boolean inclUserPass)
    {
        if (this.request != null) {
            //URIArg url = WebPageAdaptor.MakeURL(this.getBaseURI());
            URIArg url = new URIArg(this.getBaseURI(), true); // EncodeURL
            url.addArg(Constants.PARM_ACCOUNT, this.getCurrentAccountID());
            url.addArg(Constants.PARM_USER   , this.getCurrentUserID());
            if (inclUserPass) {
                String pass = this.getCurrentUserPassword();
                if (!pass.equals(Account.BLANK_PASSWORD)) {
                    url.addArg(Constants.PARM_ENCPASS, pass);
                }
            }
            for (Enumeration e = this.request.getParameterNames(); e.hasMoreElements();) {
                String key = (String)e.nextElement();
                String val = this.request.getParameter(key);
                //Print.logInfo("Key:" + key + " ==> " + val);
                url.addArg(key, val);
            }
            return url;
        } else {
            return null;
        }
    }
    
    /* SOAP request? */
    public void setSoapRequest(boolean soap)
    {
        this.isSoapRequest = soap;
    }
    
    /* SOAP request? */
    public boolean isSoapRequest()
    {
        return this.isSoapRequest;
    }

    // ------------------------------------------------------------------------

    /* set the cookies enabled flag */
    public void setCookiesRequired(boolean cookiesReq)
    {
        this.cookiesRequired = cookiesReq;
    }
    
    /* return the cookies enabled flag */
    public boolean getCookiesRequired()
    {
        return this.cookiesRequired;
    }

    // ------------------------------------------------------------------------

    /* set the current IP address */
    public void setIPAddress(String ipAddr)
    {
        this.ipAddress = ipAddr;
    }
    
    /* return the current IP address */
    public String getIPAddress()
    {
        return (this.ipAddress != null)? this.ipAddress : "";
    }

    // ------------------------------------------------------------------------

    /* set the current page name */
    public void setPageName(String page)
    {
        this.pageName = page;
    }
    
    /* return the current page name */
    public String getPageName()
    {
        return (this.pageName != null)? this.pageName : "";
    }
    
    /* return the current web-page */
    public WebPage getWebPage()
    {
        if (this.pageName != null) {
            PrivateLabel privLabel = this.getPrivateLabel();
            return privLabel.getWebPage(this.pageName);
        } else {
            return null;
        }
    }

    /* return the current JSP URI */
    public String getJspURI()
    {
        String jspURI  = this.getWebPageURI();
        String jspFile = this.getPrivateLabel().getWebPageJSP(jspURI, this);
        //Print.logInfo("Returning JSP uri: " + jspFile);
        return jspFile;
    }

    // ------------------------------------------------------------------------

    /* set WebPage JSP URI override */
    public void setWebPageURI(String uri)
    {
        this.webPageURI = uri;
    }

    /* get WebPage JSP URI */
    public String getWebPageURI()
    {
        if (!StringTools.isBlank(this.webPageURI)) {
            return this.webPageURI;
        } else {
            WebPage page = this.getWebPage();
            return (page != null)? page.getJspURI() : null;
        }
    }

    // ------------------------------------------------------------------------

    /* set "fleet" request */
    public void setFleet(boolean fleet)
    {
        this.isFleet = fleet;
    }
    
    /* return true if this is a "fleet" request */
    public boolean isFleet()
    {
        return this.isFleet;
    }
    
    /* return max number of events per device for fleet map */
    public long getFleetDeviceEventCount()
    {
        PrivateLabel privLabel = this.getPrivateLabel();
        
        /* check current web-page for override */
        WebPage webPage = this.getWebPage();
        if (webPage != null) {
            // NOTE: this may not work for AJAX map event updates.
            long dec = webPage.getProperties().getLong(TrackMap.PROP_fleetDeviceEventCount,-1L);
            if (dec > 0L) {
                return dec;
            }
        }
        
        /* default to global PrivateLabel property */
        long dec = privLabel.getLongProperty(PrivateLabel.PROP_TrackMap_fleetDeviceEventCount,1L);
        return (dec >= 1L)? dec : 1L;
        
    }

    // ------------------------------------------------------------------------

    /* set the current page navigation */
    public void setPageNavigationHTML(String pageNav)
    {
        this.pageNavHTML = pageNav;
    }
    
    /* return the current page navigation */
    public String getPageNavigationHTML()
    {
        return this.pageNavHTML;
    }

    // ------------------------------------------------------------------------
    
    /* set page frame sections written to client */
    public void setPageFrameSections(int pfs)
    {
        this.pageFrameSections = pfs | PAGE_FRAME_CONTENT;
    }
    
    /* return page frame sections to write to client */
    public int getPageFrameSections()
    {
        return this.pageFrameSections;
    }

    /* set content only */
    public void setPageFrameContentOnly(boolean contentOnly)
    {
        if (contentOnly) {
            this.setPageFrameSections(PAGE_FRAME_CONTENT);
        } else {
            this.setPageFrameSections(PAGE_FRAME_ALL);
        }
    }

    /* gett content only state */
    public boolean getPageFrameContentOnly()
    {
        return (this.getPageFrameSections() == PAGE_FRAME_CONTENT);
    }

    /* return true if specified page frame section is enabled */
    public boolean writePageFrameSection(int pfs)
    {
        return ((pfs & this.pageFrameSections) != 0);
    }

    // ------------------------------------------------------------------------

    /* set the URL command name */
    public void setCommandName(String cmd)
    {
        this.cmdName = cmd;
    }
    
    /* return the URL command name */
    public String getCommandName()
    {
        return (this.cmdName != null)? this.cmdName : "";
    }

    /* set the URL argument string */
    public void setCommandArg(String arg)
    {
        this.cmdArg = arg;
    }
    
    /* return the URL argument string */
    public String getCommandArg()
    {
        return (this.cmdArg != null)? this.cmdArg : "";
    }

    // ------------------------------------------------------------------------

    /* set the PrivateLabel for this domain */
    public void setPrivateLabel(PrivateLabel privLabel)
    {
        this.privLabel = privLabel;
    }
    
    /* get the PrivateLabel for this domain */
    // does(must) not return null
    public PrivateLabel getPrivateLabel()
    {
        return (this.privLabel != null)? this.privLabel : NullPrivateLabel;
    }

    /* get the current PrivateLabel Locale */
    public Locale getLocale()
    {
        return this.getPrivateLabel().getLocale();
    }

    // ------------------------------------------------------------------------

    /* set the current map provider */
    public void setMapProvider(MapProvider mapProv)
    {
        this.mapProvider = mapProv;
    }

    /* return the current map provider */
    public MapProvider getMapProvider()
    {
        if (this.mapProvider == null) {
            this.mapProvider = this.getPrivateLabel().getMapProvider();
        }
        return this.mapProvider;
    }

    /* return list of pushpin icons (in order) */
    public java.util.List<String> getMapProviderPushpinIDs()
    {
        MapProvider mapProv = this.getMapProvider();
        if (mapProv != null) {
            return ListTools.toList(mapProv.getPushpinIconMap(this).keySet(), new Vector<String>());
        } else {
            return new Vector<String>();
        }
    }

    // ------------------------------------------------------------------------

    public void _setLoginErrorAlert()
    {
        this.loginErrorAlert = true;
    }
    
    public boolean _isLoginErrorAlert()
    {
        return this.loginErrorAlert;
    }

    // ------------------------------------------------------------------------

    /* set the current Account */
    public void setCurrentAccount(Account account)
    {
        this.account = account;
        if (this.account != null) {
            //Print.logInfo("Set Account: " + this.account.getAccountID());
        } else {
            Print.logWarn("Account not specified!");
        }
    }
    
    /* return the current Account */
    public Account getCurrentAccount()
    {
        return this.account;
    }

    /* return the current account ID/name */
    public String getCurrentAccountID()
    {
        return (this.account != null)? this.account.getAccountID() : "";
    }

    /* return true if we have an account */
    public boolean isLoggedIn()
    {
        return (this.getCurrentAccount() != null);
    }

    // ------------------------------------------------------------------------

    /* set the current User */
    public void setCurrentUser(User user)
    {
        this.user = user;
    }

    /* get the current User */
    public User getCurrentUser()
    {
        return this.user;
    }

    /* get the current User */
    public String getCurrentUserPassword()
    {
        if (this.user != null) {
            return this.user.getPassword();
        } else
        if (this.account != null) {
            return this.account.getPassword();
        } else {
            return null;
        }
    }

    /* return the current User ID/name */
    public String getCurrentUserID()
    {
        return (this.user != null)? this.user.getUserID() : User.getAdminUserID();
    }

    /* set the list of known users for this account */
    public void setUserList(String[] userList)
    {
        this.userList = userList;
    }

    /* return a list of known users for this account */
    public String[] getUserList()
    {
        if (this.userList == null) {
            try {
                User user = this.getCurrentUser();
                PrivateLabel privLabel = this.getPrivateLabel(); // never null
                WebPage userPage = privLabel.getWebPage(Constants.PAGE_USER_INFO);
                if (userPage == null) {
                    this.userList = new String[0];
                } else
                if (privLabel.hasReadAccess(user, userPage.getAclName(UserInfo._ACL_ALL))) {
                    this.userList = User.getUsersForAccount(this.getCurrentAccountID());
                } else
                if (privLabel.hasReadAccess(user, userPage.getAclName())) {
                    this.userList = new String[] { user.getUserID() };
                } else {
                    this.userList = new String[0];
                }
            } catch (DBException dbe) {
                String uid = this.getCurrentUserID();
                this.userList = StringTools.isBlank(uid)? new String[0] : new String[] { uid };
            }
        }
        return this.userList;
    }

    // ------------------------------------------------------------------------

    /* return the current device group ID/name */
    public void setSelectedDeviceGroupID(String groupID)
    {
        this.setSelectedDeviceGroupID(groupID, true);
    }
    
    /* return the current device group ID/name */
    public void setSelectedDeviceGroupID(String groupID, boolean isActualSpecifiedGroup)
    {
        this.selDeviceGroupID = groupID;
        this.isActualSelGrpID = isActualSpecifiedGroup;
        this.selDeviceGroup   = null;
    }

    /* return the current device group ID/name */
    public String getSelectedDeviceGroupID()
    {
        if (!StringTools.isBlank(this.selDeviceGroupID)) {
            return this.selDeviceGroupID;
        } else {
            OrderedSet<String> grpList = this.getDeviceGroupList(false);
            if (!ListTools.isEmpty(grpList)) {
                return grpList.get(0);
            } else {
                return DeviceGroup.DEVICE_GROUP_ALL;
            }
        }
    }

    /* is actual specified group ID (ie. not a 'default' selection) */
    public boolean isActualSpecifiedGroup()
    {
        return !StringTools.isBlank(this.selDeviceGroupID) && this.isActualSelGrpID;
    }

    /* get the current DeviceGroup */
    public DeviceGroup getSelectedDeviceGroup()
    {
        if (this.selDeviceGroup == null) {
            String groupID = this.getSelectedDeviceGroupID();
            if (!groupID.equalsIgnoreCase(DeviceGroup.DEVICE_GROUP_ALL)) {
                try {
                    Account account = this.getCurrentAccount();
                    this.selDeviceGroup = DeviceGroup.getDeviceGroup(account, groupID);
                    if (this.selDeviceGroup == null) {
                        this.selDeviceGroupID = DeviceGroup.DEVICE_GROUP_ALL;
                    }
                } catch (DBException dbe) {
                    this.selDeviceGroupID = DeviceGroup.DEVICE_GROUP_ALL;
                    Print.logException("Error reading DeviceGroup: " + this.getCurrentAccountID() + "/" + groupID, dbe);
                }
            }
        }
        return this.selDeviceGroup;
    }

    /* clear the list of device groups */
    public void clearDeviceGroupList()
    {
        this.devGrpSet        = null;
        this.devGrpSetAll     = null;
        this.selDeviceGroupID = null;
        this.selDeviceGroup   = null;
    }

    /* return a list of authorized devices for this account/user */
    // does not return null
    public OrderedSet<String> getDeviceGroupList(boolean inclAll)
    {
        // Warning: this caches the returned value!  Currently, 'inclAll' should be 'true'!
        if (this.devGrpSet == null) {
            User user = this.getCurrentUser();
            if (!User.isAdminUser(user)) {
                // (user is not null) get User authorized groups
                try {
                    java.util.List<String> grpList = user.getDeviceGroups(true/*refresh*/);
                    if (!ListTools.isEmpty(grpList)) {
                        // user has been given a specific list of authorized groups
                        this.devGrpSet    = new OrderedSet<String>(grpList);
                        this.devGrpSetAll = new OrderedSet<String>(this.devGrpSet); // shallow copy
                    } else {
                        // list is empty, 'ALL' is ok
                        //this.devGrpSet    = DeviceGroup.GROUP_LIST_EMPTY;
                        //this.devGrpSetAll = DeviceGroup.GROUP_LIST_ALL;
                        this.devGrpSet    = DeviceGroup.getDeviceGroupsForAccount(this.getCurrentAccountID(),false);
                        this.devGrpSetAll = new OrderedSet<String>(this.devGrpSet); // shallow copy
                        this.devGrpSetAll.add(0, DeviceGroup.DEVICE_GROUP_ALL);
                    }
                } catch (DBException dbe) {
                    Print.logException("Retrieving user groups: " + user.getUserID(), dbe);
                    this.devGrpSet    = DeviceGroup.GROUP_LIST_EMPTY;
                    this.devGrpSetAll = DeviceGroup.GROUP_LIST_EMPTY;
                }
            } else {
                // no user (assume admin) get all groups for current account
                try {
                    this.devGrpSet    = DeviceGroup.getDeviceGroupsForAccount(this.getCurrentAccountID(),false);
                    this.devGrpSetAll = new OrderedSet<String>(this.devGrpSet);
                    this.devGrpSetAll.add(0, DeviceGroup.DEVICE_GROUP_ALL);
                } catch (DBException dbe) {
                    this.devGrpSet    = DeviceGroup.GROUP_LIST_EMPTY;
                    this.devGrpSetAll = DeviceGroup.GROUP_LIST_ALL;
                }
            }
        }
        return inclAll? this.devGrpSetAll : this.devGrpSet;
    }
    
    /* get the description of a specific device */
    private DeviceGroup descLastGroup = null;
    public String getDeviceGroupDescription(String grpID, boolean rtnDispName)
    {

        /* no group ID specified? */
        if (StringTools.isBlank(grpID)) {
            return "";
        }

        /* previous device group? */
        if ((this.descLastGroup != null) && 
            this.descLastGroup.getAccountID().equals(this.getCurrentAccountID()) && 
            this.descLastGroup.getGroupID().equals(grpID)) {
            String n = rtnDispName? this.descLastGroup.getDisplayName() : this.descLastGroup.getDescription();
            return !n.equals("")? n : grpID;
        }

        /* get account group-id description */
        Account acct = this.getCurrentAccount();
        if (acct != null) {
            try {
                this.descLastGroup = DeviceGroup.getDeviceGroup(acct, grpID);
                if (this.descLastGroup != null) {
                    String n = rtnDispName? this.descLastGroup.getDisplayName() : this.descLastGroup.getDescription();
                    return !n.equals("")? n : grpID;
                } else {
                    if (grpID.equalsIgnoreCase(DeviceGroup.DEVICE_GROUP_ALL)) {
                        return DeviceGroup.GetDeviceGroupAll(this.getLocale());
                    } else {
                        return grpID;
                    }
                }
            } catch (DBException dbe) {
                // unable to read group description, return group-id
                return grpID;
            }
        }

        /* default to returning groupID */
        return grpID;

    }

    // ------------------------------------------------------------------------

    /* return the current device group ID/name */
    public void setSelectedDeviceID(String devID)
    {
        this.setSelectedDeviceID(devID, true);
    }

    /* return the current device group ID/name */
    public void setSelectedDeviceID(String devID, boolean isActualSpecifiedDevice)
    {
        this.selDeviceID      = devID;
        this.isActualSelDevID = isActualSpecifiedDevice;
        this.selDevice        = null;
    }

    /* return the current device ID/name */
    public String getSelectedDeviceID()
    {
        return (this.selDeviceID != null)? this.selDeviceID : "";
    }

    /* is actual specified device ID (ie. not a 'default' selection) */
    public boolean isActualSpecifiedDevice()
    {
        return !StringTools.isBlank(this.selDeviceID) && this.isActualSelDevID;
    }

    /* get the current Device */
    public Device getSelectedDevice()
    {
        if (this.selDevice == null) {
            String deviceID = this.getSelectedDeviceID();
            if (!StringTools.isBlank(deviceID)) {
                try {
                    Account account = this.getCurrentAccount();
                    this.selDevice = Device.getDevice(account, deviceID);
                    if (this.selDevice == null) {
                        Print.logWarn("Device not found: " + deviceID);
                    }
                } catch (DBException dbe) {
                    Print.logException("Error reading Device: " + this.getCurrentAccountID() + "/" + deviceID, dbe);
                }
            }
        }
        return this.selDevice;
    }

    /* set the list of known devices for this account */
    public void clearDeviceList()
    {
        this.devList = null;
    }

    /* return a list of known devices for this account */
    public OrderedSet<String> getDeviceList()
    {
        if (this.devList == null) {
            try {
                this.devList = User.getAuthorizedDeviceIDs(this.getCurrentUser(), this.getCurrentAccountID());
            } catch (DBException dbe) {
                this.devList = new OrderedSet<String>();
            }
        }
        return this.devList;
    }

    /* return a list of known devices for this account */
    protected OrderedSet<String> _getDeviceIDsForSelectedGroup(boolean isFleet)
        throws DBException
    {
        if (isFleet) {
            String accountID = this.getCurrentAccountID();
            String groupID   = this.getSelectedDeviceGroupID();
            if (!groupID.equals("") && !groupID.equalsIgnoreCase(DeviceGroup.DEVICE_GROUP_ALL)) {
                OrderedSet<String> dList = DeviceGroup.getDeviceIDsForGroup(accountID, groupID, null/*User*/);
                // TODO: filter authorized devices for current user?
                return dList;
            } else {
                // return all Account devices
                return this.getDeviceList();
            }
        } else {
            return this.getDeviceList();
        }
    }

    /* get the description of a specific device */
    private Device descLastDevice = null;
    public String getDeviceDescription(String devID, boolean rtnDispName)
    {

        /* no device ID specified? */
        if (StringTools.isBlank(devID)) {
            return "";
        }

        /* previous device group? */
        if ((this.descLastDevice != null) && 
            this.descLastDevice.getAccountID().equals(this.getCurrentAccountID()) && 
            this.descLastDevice.getDeviceID().equals(devID)) {
            String n = rtnDispName? this.descLastDevice.getDisplayName() : this.descLastDevice.getDescription();
            return !n.equals("")? n : devID;
        }

        /* get account device-id description */
        Account acct = this.getCurrentAccount();
        if (acct != null) {
            try {
                this.descLastDevice = Device.getDevice(acct, devID);
                if (this.descLastDevice != null) {
                    String n = rtnDispName? this.descLastDevice.getDisplayName() : this.descLastDevice.getDescription();
                    return !n.equals("")? n : devID;
                } else {
                    return devID;
                }
            } catch (DBException dbe) {
                // drop through below
            }
        }

        /* default to returning deviceID */
        return devID;

    }

    // ------------------------------------------------------------------------

    /* create Device/DeviceGroup IDDescription list */
    public java.util.List<IDDescription> createIDDescriptionList(boolean isFleet, IDDescription.SortBy sortBy)
    {
        boolean inclAll = true; // if DeviceGroup
        OrderedSet<String> dgList = isFleet? this.getDeviceGroupList(inclAll) : this.getDeviceList();
        java.util.List<IDDescription> idList = new Vector<IDDescription>();
        if (!ListTools.isEmpty(dgList)) {
            sortBy = IDDescription.GetSortBy(sortBy); // make sure 'sortBy' is not null
            boolean rtnDispName = sortBy.equals(IDDescription.SortBy.NAME);
            for (int i = 0; i < dgList.size(); i++) {
                String dgid = dgList.get(i); // Device/Group ID
                String desc = isFleet? 
                    this.getDeviceGroupDescription(dgid,rtnDispName) : 
                    this.getDeviceDescription     (dgid,rtnDispName);
                idList.add(new IDDescription(dgid, desc));
            }
            if (rtnDispName) { sortBy = IDDescription.SortBy.DESCRIPTION; }
            IDDescription.SortList(idList, sortBy);
        }
        return idList;
    }

    /* create <Device,Description> map (sorted by description) */
    public OrderedMap<String,String> createDeviceDescriptionMap(boolean inclID)
    {
        java.util.List<IDDescription> list = this.createIDDescriptionList(false/*!isFleet*/, null);
        OrderedMap<String,String> map = new OrderedMap<String,String>();
        for (IDDescription idd : list) {
            String id   = idd.getID();
            String desc = idd.getDescription();
            if (inclID) { desc += " [" + id + "]"; }
            map.put(id, desc);
        }
        return map;
    }

    // ------------------------------------------------------------------------

    private boolean _didCacheZoomeRegionShapes = false;
    private Map<String,MapShape> _cacheZoomRegionShapes = null;
    public Map<String,MapShape> getZoomRegionShapes()
    {
        if (!_didCacheZoomeRegionShapes) {
            _didCacheZoomeRegionShapes = true;

            /* read Geozone defined zoom-regions */
            // TODO:

            /* read global defined zoom-regions */
            Map<String,MapShape> msList = this.getPrivateLabel().getMapShapes();
            if (!ListTools.isEmpty(msList)) {
                if (_cacheZoomRegionShapes != null) {
                    // may be used when we eventually read zoom-reagions from the Geozone table
                    _cacheZoomRegionShapes.putAll(msList);
                } else {
                    _cacheZoomRegionShapes = msList;
                }
            }
            
        }
        return _cacheZoomRegionShapes;
    }
    
    // ------------------------------------------------------------------------
    
    /* return array of events based on requested parameters */
    public EventData[] getMapEvents()
        throws DBException
    {
        return this.getMapEvents(null, -1L);
    }

    /* return array of events based on requested parameters */
    public EventData[] getMapEvents(int statusCodes[], long perDevLimit)
        throws DBException
    {
        PrivateLabel privLabel = this.getPrivateLabel();
        // this assumes that the number of returned records is reasonable and fits in memory
        long limitCnt = this.getEventLimit(); // total record limit
        EventData.LimitType limitType = this.getEventLimitType();
        
        /* per device record limit */
        if (perDevLimit <= 0L) {
            perDevLimit = this.isFleet()? this.getFleetDeviceEventCount() : limitCnt;
        }
        //Print.logInfo("Limit Count: " + limitCnt + " [per device: " + perDevLimit);

        /* date range */
        long startTime = this.getEventDateFromSec();
        long endTime   = this.getEventDateToSec();
        //Print.logInfo("Date Range: " + new DateTime(startTime) + " to " + new DateTime(endTime));

        /* get events */
        if (this.isFleet()) {
            // fleet events

            // get account
            Account account = this.getCurrentAccount();
            if (account == null) {
                return EventData.EMPTY_ARRAY;
            }

            // get user
            User user = this.getCurrentUser();

            // get list of devices
            OrderedSet<String> devList = this._getDeviceIDsForSelectedGroup(true);
            if (ListTools.isEmpty(devList)) {
                Print.logInfo("No devices ...");
                return EventData.EMPTY_ARRAY;
            }

            // not every device may have an event
            java.util.List<EventData> evList = new Vector<EventData>();
            for (int i = 0; i < devList.size(); i++) { // apply limit?
                String deviceID = devList.get(i);

                // omit unauthorized devices
                if ((user != null) && !user.isAuthorizedDevice(deviceID)) {
                    continue;
                }

                // get Device
                Device device = Device.getDevice(account, deviceID);
                if (device == null) {
                    // skip this deviceID
                    continue;
                }

                // get last event(s) for Device
                EventData ev[] = device.getRangeEvents(
                    startTime,                  // startTime
                    endTime,                    // endTime
                    statusCodes,                // status codes
                    true,                       // validGPS
                    limitType,                  // limitType (LAST)
                    perDevLimit);               // max points
                    // 'ev' already points to 'device'
                if (ev != null) {
                    for (int e = 0; e < ev.length; e++) {
                        evList.add(ev[e]);
                    }
                }
                
                // limit?
                if ((limitCnt > 0L) && (evList.size() >= limitCnt)) {
                    //Print.logWarn("Limit Reached: " + evList.size());
                    break;
                }

            } // Device loop
            
            /* sort by Device Descrption */
            Collections.sort(evList, EventData.getDeviceDescriptionComparator());

            // return fleet events
            //Print.logWarn("Event Count: " + evList.size());
            return evList.toArray(new EventData[evList.size()]);
                
        } else {
            // individual device events
            
            // selected device
            Device device = this.getSelectedDevice();
            if (device == null) {
                // no events for a null device
                return EventData.EMPTY_ARRAY;
            }
            
            // return device events
            EventData[] ed;
            if ((startTime <= 0L) && (endTime <= 0L)) {
                ed = device.getRangeEvents( // may return null
                    -1L,                        // startTime
                    -1L,                        // endTime
                    statusCodes,                // status codes
                    true,                       // validGPS
                    limitType,                  // limitType
                    perDevLimit);               // max points
            } else {
                ed = device.getRangeEvents( // may return null
                    startTime,                  // startTime
                    endTime,                    // endTime
                    statusCodes,                // status codes
                    true,                       // validGPS
                    limitType,                  // limitType
                    perDevLimit);               // max points
            }
            // 'ed' already points to 'device'

            /* no data? */
            if (ed == null) {
                return EventData.EMPTY_ARRAY;
            }

            /* return events */
            return ed;

        }
 
    }

    // ------------------------------------------------------------------------

    /* return true if addresses are to be displayed */
    public boolean getShowAddress()
    {
        Account acct = this.getCurrentAccount();
        if (acct == null) {
            // account not available
            return false;
        } else
        if (Account.getGeocoderMode(acct).isNone()) {
            // no reverse-geocoding performed/allowed, thus no address
            return false;
        } else {
            return true;
        }
    }

    // ------------------------------------------------------------------------

    /* set the event-range date "From" value */
    public void setEventDateFrom(DateTime fr)
    {
        this.dateFrom = fr;
    }

    /* return the event-range date "From" value */
    public DateTime getEventDateFrom()
    {
        return this.dateFrom; // may be null
    }
    public long getEventDateFromSec()
    {
        return (this.dateFrom != null)? this.dateFrom.getTimeSec() : -1L;
    }

    /* set the event-range date "To" value */
    public void setEventDateTo(DateTime to)
    {
        this.dateTo = to;
    }

    /* return the event-range date "To" value */
    public DateTime getEventDateTo()
    {
        return this.dateTo; // may be null
    }
    public long getEventDateToSec()
    {
        return (this.dateTo != null)? this.dateTo.getTimeSec() : -1L;
    }

    // ------------------------------------------------------------------------

    /* return date format (ie. "yyyy/MM/dd", etc) */
    public String getDateFormat()
    {
        return this.getPrivateLabel().getDateFormat();
    }
    
    // ------------------------------------------------------------------------

    /* set the current time zone */
    public void setTimeZone(TimeZone tz, String tzStr)
    {
        this.timeZone = tz;
        this.timeZoneLongStr = tzStr;
    }

    /* set the current time zone string representation */
    //public void setTimeZoneString(String tzStr)
    //{
    //    this.timeZoneLongStr = tzStr;
    //}

    /* get the curent time zone as a String */
    public String getTimeZoneString(DateTime dt)
    {

        /* initialize Timezone String */
        if (StringTools.isBlank(this.timeZoneLongStr)) {
            String tzStr = null;
            
            /* User/Account timezone string */
            Account a = this.getCurrentAccount();
            User    u = this.getCurrentUser();
            if (u != null) {
                // try User timezone
                tzStr = u.getTimeZone(); // may be blank
                if (StringTools.isBlank(tzStr) && (a != null)) {
                    // override with Account timezone
                    tzStr = a.getTimeZone();
                    //Print.logInfo("Account TimeZone: " + tzStr);
                } else {
                    //Print.logInfo("User TimeZone: " + tzStr);
                }
            } else 
            if (a != null) {
                // get Account timezone
                tzStr = a.getTimeZone();
                //Print.logInfo("Account TimeZone: " + tzStr);
            }

            /* still no timezone? */
            if (StringTools.isBlank(tzStr)) {
                // make sure we have a timezone 
                tzStr = Account.DEFAULT_TIMEZONE;
            }

            /* set timezone string */
            this.timeZoneLongStr = tzStr;
            //Print.logInfo("Using TimeZone: " + tzStr);

        }

        /* return short/long name? */
        if (dt != null) {
            if (StringTools.isBlank(this.timeZoneShortStr)) {
                TimeZone tz = DateTime.getTimeZone(this.timeZoneShortStr, null);
                if (tz != null) {
                    boolean dst = dt.isDaylightSavings(tz);
                    this.timeZoneShortStr = tz.getDisplayName(dst, TimeZone.SHORT);
                } else {
                    // timezone iz invalid
                    this.timeZoneShortStr = this.timeZoneLongStr;
                }
            }
            return this.timeZoneShortStr;
        } else {
            return this.timeZoneLongStr;
        }

    }

    /* set the current time zone */
    public void setTimeZone(TimeZone tz)
    {
        this.timeZone = tz;
    }

    /* get the current time zone */
    // does not return null
    public TimeZone getTimeZone()
    {
        if (this.timeZone != null) {
            return this.timeZone;
        } else
        if (this.dateFrom != null) {
            this.timeZone = this.dateFrom.getTimeZone();
            return this.timeZone;
        } else {
            this.timeZone = DateTime.getTimeZone(this.getTimeZoneString(null));
            return this.timeZone;
        }
    }
    
    /* return the list of time zones */
    public java.util.List getTimeZonesList()
    {
        String tmz = this.getTimeZoneString(null);
        java.util.List<String> tzList = this.getPrivateLabel().getTimeZonesList();
        if (!StringTools.isBlank(tmz) && !tzList.contains(tmz)) {
            tzList = new Vector<String>(tzList);
            tzList.add(tmz);
        }
        return tzList;
    }

    // ------------------------------------------------------------------------

    /* set the event retrieval limit */
    public void setEventLimit(long limitCnt)
    {
        this.eventLimitCnt = limitCnt;
    }

    /* get event retrieval limit */
    public long getEventLimit()
    {
        return this.eventLimitCnt;
    }

    // ------------------------------------------------------------------------

    /* set event retrieval limit type [first/last] */
    public void setEventLimitType(EventData.LimitType limitType)
    {
        this.eventLimitType = (limitType != null)? limitType : EventData.LimitType.LAST;
    }

    /* set event retrieval limit type [first/last] */
    public void setEventLimitType(String limitType)
    {
        this.eventLimitType = (limitType != null) && limitType.equalsIgnoreCase("first")? 
            EventData.LimitType.FIRST : EventData.LimitType.LAST;
    }

    /* get event retrieval limit type [first/last] */
    public EventData.LimitType getEventLimitType()
    {
        return this.isFleet()? EventData.LimitType.LAST : 
            ((this.eventLimitType != null)? this.eventLimitType : EventData.LimitType.LAST);
    }

    // ------------------------------------------------------------------------
    
    /* set the last event time */
    public void setLastEventTime(DateTime lastTime)
    {
        this.lastEvent = lastTime;
    }

    /* return true if the last event time was defined */
    public boolean hasLastEvent()
    {
        return (this.lastEvent != null) && (this.lastEvent.getTimeSec() > 0L);
    }

    /* return the last event time */
    public DateTime getLastEventTime()
    {
        return this.lastEvent;
    }

    // ------------------------------------------------------------------------

    /* return the last event time as a string */
    public String formatDateTime(DateTime dt)
    {
        return this.formatDateTime(dt, this.getTimeZone(), "");
    }

    /* return the last event time as a string */
    public String formatDateTime(DateTime dt, String dft)
    {
        return this.formatDateTime(dt, this.getTimeZone(), dft);
    }

    /* return the last event time as a string */
    public String formatDateTime(long timestamp)
    {
        return this.formatDateTime(timestamp, this.getTimeZone(), "");
    }

    /* return the last event time as a string */
    public String formatDateTime(long timestamp, String dft)
    {
        return this.formatDateTime(timestamp, this.getTimeZone(), dft);
    }

    /* return the last event time as a string */
    public String formatDateTime(long timestamp, TimeZone tmz)
    {
        return this.formatDateTime(timestamp, tmz, "");
    }

    /* return the last event time as a string */
    public String formatDateTime(long timestamp, TimeZone tmz, String dft)
    {
        return (timestamp > 0L)? this.formatDateTime(new DateTime(timestamp,tmz),tmz,dft) : dft;
    }

    /* return the last event time as a string */
    public String formatDateTime(DateTime dt, TimeZone tmz)
    {
        return this.formatDateTime(dt, tmz, "");
    }

    /* return the last event time as a string */
    public String formatDateTime(DateTime dt, TimeZone tmz, String dft)
    {
        if ((dt != null) && (dt.getTimeSec() > 0L)) {
            Account a = this.getCurrentAccount();
            String dateFmt = (a != null)? a.getDateFormat() : BasicPrivateLabel.getDefaultDateFormat();
            String timeFmt = (a != null)? a.getTimeFormat() : BasicPrivateLabel.getDefaultTimeFormat();
            return dt.format(dateFmt + " " + timeFmt + " z", tmz);
        } else {
            return dft;
        }
    }

    // ------------------------------------------------------------------------

    /* return the speed units */
    public Account.SpeedUnits getSpeedUnits()
    {
        return Account.getSpeedUnits(this.getCurrentAccount());
    }

    /* return the distance units */
    public Account.DistanceUnits getDistanceUnits()
    {
        return Account.getDistanceUnits(this.getCurrentAccount());
    }

    // ------------------------------------------------------------------------
    // This section provide opportunity for the Account to orverride the default name/title
    // of the element "title".

    /* return the "Device" title for this account */
    // IE. "Device", "Tractor", "Taxi", etc
    public String[] getDeviceTitles()
    {
        Locale  locale  = this.getLocale();
        Account account = this.getCurrentAccount();
        return (account != null)? account.getDeviceTitles(locale) : Device.GetTitles(locale);
    }

    /* return the "Device Group" title for this account */
    // IE. "Group", "Fleet", etc.
    public String[] getDeviceGroupTitles()
    {
        Locale  locale  = this.getLocale();
        Account account = this.getCurrentAccount();
        return (account != null)? account.getDeviceGroupTitles(locale) : DeviceGroup.GetTitles(locale);
    }

    /* return the "Entity" title for this account */
    // IE. "Entity", "Trailer", "Package", etc.
    public String[] getEntityTitles()
    {
        Locale  locale  = this.getLocale();
        Account account = this.getCurrentAccount();
        return (account != null)? account.getEntityTitles(locale) : new String[] { "", "" };
    }

    // ------------------------------------------------------------------------

    /* set 'demo' mode */
    public void setEnableDemo(boolean enableDemo)
    {
        this.enableDemo = enableDemo? 1 : 0;
    }
    
    /* get 'demo' mode */
    public boolean getEnableDemo()
    {
        if (this.enableDemo < 0) {
            return this.getPrivateLabel().getEnableDemo();
        } else {
            return (this.enableDemo > 0)? true : false;
        }
    }
    
    /* return 'demo' accountID */
    public String getDemoAccountID()
    {
        return RequestProperties.DemoAccount;
    }
    
    /* 'true' if this is the demo account */
    public boolean isDemoAccount()
    {
        return this.getEnableDemo() && this.getCurrentAccountID().equals(this.getDemoAccountID());
    }
    
    /* get demo device date range */
    public String[] getDemoDateRange()
    {
        String selDevID = this.getSelectedDeviceID();
        if (selDevID.equals(RequestProperties.DemoDevice_1)) {
            return DemoRange_1;
        } else
        if (selDevID.equals(RequestProperties.DemoDevice_2)) {
            return DemoRange_2;
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private static interface KeyValue
    {
        public String getValue(RequestProperties reqState, String arg);
    }

    private static Map<String,KeyValue> propKeyMap = null;

    private static Map<String,KeyValue> _getKeyMap()
    {
        
        if (propKeyMap == null) {
            propKeyMap = new OrderedMap<String,KeyValue>();
            ((OrderedMap)propKeyMap).setIgnoreCase(true);
            propKeyMap.put("locale",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    PrivateLabel privLabel = reqState.getPrivateLabel();
                    return privLabel.getLocaleString();
                }
            });
            propKeyMap.put("isLoggedIn",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    boolean isLoggedIn = (reqState.account != null);
                    //Print.logInfo("Logged In: " + isLoggedIn);
                    return isLoggedIn? "true" : "false"; // I18N?
                }
            });
            propKeyMap.put("loginCount",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    int count = reqState.GetLoginCount();
                    if (count < 0) {
                        // unable to determine
                        return "?";
                    } else
                    if (reqState.isLoggedIn()) {
                        // count current Account/User login sessions
                        return String.valueOf(count);
                    } else {
                        // count all login sessions
                        return String.valueOf(count) + "*";
                    }
                }
            });
            propKeyMap.put("accountID",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    String acctID = reqState.getCurrentAccountID();
                    return !StringTools.isBlank(acctID)? acctID : null;
                }
            });
            propKeyMap.put("accountDesc",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    return (reqState.account != null)? reqState.account.getDescription() : null;
                }
            });
            propKeyMap.put("accountJsp",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    Account acct = reqState.account; // may be null;
                    return (acct != null)? StringTools.blankDefault(acct.getPrivateLabelJsp(),null) : null;
                }
            });
            propKeyMap.put("userID",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    String userID = reqState.getCurrentUserID();
                    return !StringTools.isBlank(userID)? userID : null;
                }
            });
            propKeyMap.put("userDesc",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    return (reqState.user != null)? reqState.user.getDescription() : User.getAdminUserID();
                }
            });
            propKeyMap.put("deviceID",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    return (reqState.selDevice != null)? reqState.selDevice.getDeviceID() : null;
                }
            });
            propKeyMap.put("deviceDesc",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    return (reqState.selDevice != null)? reqState.selDevice.getDescription() : null;
                }
            });
            propKeyMap.put("navigation",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    return (reqState.pageNavHTML != null)? reqState.pageNavHTML : null;
                }
            });
            propKeyMap.put("ipAddress",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    return reqState.getIPAddress();
                }
            });
            propKeyMap.put("pageTitle",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    PrivateLabel privLabel = reqState.getPrivateLabel();
                    return privLabel.getPageTitle();
                }
            });
            propKeyMap.put("copyright",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    PrivateLabel privLabel = reqState.getPrivateLabel();
                    return privLabel.getCopyright();
                }
            });
            propKeyMap.put("version",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    return Version.getVersion();
                }
            });
            propKeyMap.put("i18n.Account",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    if (reqState.account != null) {
                        try {
                            AccountString as = AccountString.getAccountString(reqState.account, AccountString.ID_ACCOUNT);
                            if ((as != null) && as.hasSingularTitle()) {
                                return as.getSingularTitle();
                            }
                        } catch (DBException dbe) {
                            // ignore
                        }
                    }
                    return Account.GetTitles(reqState.getLocale())[0];
                }
            });
            propKeyMap.put("i18n.Accounts",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    if (reqState.account != null) {
                        try {
                            AccountString as = AccountString.getAccountString(reqState.account, AccountString.ID_ACCOUNT);
                            if ((as != null) && as.hasPluralTitle()) {
                                return as.getPluralTitle();
                            }
                        } catch (DBException dbe) {
                            // ignore
                        }
                    }
                    return Account.GetTitles(reqState.getLocale())[1];
                }
            });
            propKeyMap.put("i18n.Vehicle",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    if (reqState.account != null) {
                        try {
                            AccountString as = AccountString.getAccountString(reqState.account, AccountString.ID_DEVICE);
                            if ((as != null) && as.hasSingularTitle()) {
                                return as.getSingularTitle();
                            }
                        } catch (DBException dbe) {
                            // ignore
                        }
                    }
                    return Device.GetTitles(reqState.getLocale())[0];
                }
            });
            propKeyMap.put("i18n.Vehicles",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    if (reqState.account != null) {
                        try {
                            AccountString as = AccountString.getAccountString(reqState.account, AccountString.ID_DEVICE);
                            if ((as != null) && as.hasPluralTitle()) {
                                return as.getPluralTitle();
                            }
                        } catch (DBException dbe) {
                            // ignore
                        }
                    }
                    return Device.GetTitles(reqState.getLocale())[1];
                }
            });
            propKeyMap.put("i18n.User",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    if (reqState.account != null) {
                        try {
                            AccountString as = AccountString.getAccountString(reqState.account, AccountString.ID_USER);
                            if ((as != null) && as.hasSingularTitle()) {
                                return as.getSingularTitle();
                            }
                        } catch (DBException dbe) {
                            // ignore
                        }
                    }
                    I18N i18n = I18N.getI18N(RequestProperties.class, reqState.getLocale());
                    return i18n.getString("RequestProperties.user","User");
                }
            });
            propKeyMap.put("i18n.Login",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    I18N i18n = I18N.getI18N(RequestProperties.class, reqState.getLocale());
                    return i18n.getString("RequestProperties.login","Login");
                }
            });
            propKeyMap.put("statusCodeDesc",new KeyValue() {
                public String getValue(RequestProperties reqState, String arg) {
                    int sc = StringTools.parseInt(arg,-1);
                    if (sc <= 0) {
                        return StatusCodes.GetDescription(StatusCodes.STATUS_NONE,null);
                    } else {
                        PrivateLabel privLabel = reqState.getPrivateLabel();
                        String acctID = reqState.getCurrentAccountID();
                        return StatusCode.getDescription(acctID, sc, privLabel, null);
                    }
                }
            });
        }

        return propKeyMap;

    }

    // "StringTools.KeyValueMap" interface (may return null if key not found/defined)
    public String _getKeyValue(String key, String arg)
    {
        //Print.logInfo("Searching for key: " + key);
        
        /* no key? */
        if (StringTools.isBlank(key)) {
            return null;
        }

        /* PrivateLabel i18n Strings */
        PrivateLabel privLabel = this.getPrivateLabel();
        String i18nVal = privLabel.getI18NTextString(key, null);
        if (i18nVal != null) {
            //Print.logInfo("Found key: " + key + " ==> " + i18nVal);
            return i18nVal;
        }
        
        /* PrivateLabel Strings properties */
        String propVal = privLabel.getStringProperty(key, null);
        if (propVal != null) {
            //Print.logInfo("Found key: " + key + " ==> " + propVal);
            return propVal;
        }

        /* try custom keys */
        KeyValue kv = RequestProperties._getKeyMap().get(key);
        if (kv != null) {
            String v = kv.getValue(this, arg);
            //Print.logInfo("Found key: " + key + " ==> " + v);
            return v;
        }

        /* try session attributes */
        if (this.request != null) {
            Object v = AttributeTools.getSessionAttribute(this.request, key, null);
            if (v != null) {
                //Print.logInfo("Found key: " + key + " ==> " + v);
                return v.toString();
            }
        }

        /* try runtime properties */
        if (RTConfig.hasProperty(key)) {
            String v = RTConfig.getString(key,null);
            //Print.logInfo("Found key: " + key + " ==> " + v);
            return v;
        }

        /* still nothing, return null */
        //Print.logWarn("Key not Found: " + key);
        return null;

    }

    // "StringTools.KeyValueMap" interface (may return null)
    public String getKeyValue(String key, String arg)
    {
        String v = this._getKeyValue(key, arg);
        return (v != null)? v : null; // "";
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Returns the number of active login session matching the current AccountID/UserID
    *** @return The current number of login sessions for the current Account/User
    **/
    public int GetLoginCount()
    {
        HttpSession hs = AttributeTools.getSession(this.getHttpServletRequest());
        if (hs != null) {
            String aid = this.getCurrentAccountID();
            String uid = this.getCurrentUserID();
            if (StringTools.isBlank(uid)) {
                uid = User.getAdminUserID();
            }
            return RequestProperties.GetLoginCount(hs.getServletContext(), aid, uid);
        } else {
            return -1; // I18N?
        }
    }

    /**
    *** Returns the current number of login session matching the specified AccountID/UserID
    *** @param sc        The ServletContext
    *** @param accountID The AccountID
    *** @param userID    The UserID, or null for all users
    *** @return The current number of login sessions for the specified Account/User
    **/
    public static int GetLoginCount(ServletContext sc, String accountID, String userID)
    {
        if (StringTools.isBlank(accountID)) {
            return RTConfigContextListener.GetSessionCount(sc);
        } else {
            final String aid = StringTools.trim(accountID);
            final String uid = !StringTools.isBlank(userID)? StringTools.trim(userID) : null;
            return RTConfigContextListener.GetSessionCount(sc,
                new RTConfigContextListener.HttpSessionFilter() {
                    public boolean countSession(HttpSession s) {
                        Object sa = AttributeTools.getSessionAttribute(s,Constants.PARM_ACCOUNT,"");
                        if (aid.equals(sa)) {
                            if (uid == null) {
                                return true;
                            } else {
                                Object su = AttributeTools.getSessionAttribute(s,Constants.PARM_USER,"");
                                return uid.equals(su);
                            }
                        } else {
                            return false;
                        }
                    }
                }
            );
        }
    }

    // ------------------------------------------------------------------------

}
