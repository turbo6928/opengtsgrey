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
//     -Changed XML to place PageHeader/PageFooter/PageLeft/PageRight inside a
//      PageDecorations tag.
//     -Implemented two flavors of page decorations, one which is displayed when
//      no user is logged in, another which is displayed when a user is logged in.
//     -Added ReportFactory support
//  2007/03/30  Martin D. Flynn
//     -Added 'User' login support
//     -Added access control support
//  2007/05/06  Martin D. Flynn
//     -Added support for 'Page' tags 'menuText', 'menuHelp', and 'navText'
//  2007/05/20  Martin D. Flynn
//     -Added 'properties' attribute to 'MapProvider tag.
//     -Removed 'Geocoder' tag (use GeocodeProvider/ReverseGeocodeProvider instead)
//  2007/05/25  Martin D. Flynn
//     -Added 'restricted' attribute
//  2007/06/03  Martin D. Flynn
//     -Added 'locale' attribute (for I18N support)
//     -Removed 'menuText', 'menuHelp', 'navText' attributes (replaced by i18n)
//  2007/06/30  Martin D. Flynn
//     -Added host 'alias' support method 'addHostAlias(...)'
//     -Added support for overriding the default map dimensions.
//  2007/07/27  Martin D. Flynn
//     -'MapProvider' properties now supports ';' property separator.
//  2007/09/16  Martin D. Flynn
//     -Moved to package 'org.opengts.db', renamed to BasicPrivateLabel.jar
//     -Components specific to WAR property left in 'org.opengts.war.tools.PrivateLabel'
//     -XML loading moved to 'BasicPrivateLabelLoader.java'
//     -Added method 'setEventNotificationEMail'
//  2008/08/24  Martin D. Flynn
//     -Added 'setDefaultLoginUser' and 'getDefaultLoginUser' methods.
//  2009/02/20  Martin D. Flynn
//     -Added 'setDefaultLoginAccount' and 'getDefaultLoginAccount' methods.
//  2009/05/24  Martin D. Flynn
//     -Moved all property definitions from 'PrivateLabel.java' to here
//  2009/07/01  Martin D. Flynn
//     -"SendMail.getUserFromEmailAddress" is used to override 'From' email.
//  2009/09/23  Martin D. Flynn
//     -Added "getIntProperty".  Added property "topMenu.maximumIconsPerRow".
// ----------------------------------------------------------------------------
// The features this class provides are highly configurable through the external
// XML file 'private.xml'.  However, this code may also be modified to provide
// special custom features for the GPS tracking page.
// ----------------------------------------------------------------------------
package org.opengts.db;

import java.util.*;
import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.awt.Color;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;
                        
import org.opengts.util.*;

import org.opengts.db.AclEntry.AccessLevel;
import org.opengts.db.tables.*;
import org.opengts.geocoder.*;

public class BasicPrivateLabel
    implements RTConfig.PropertySetter
{

    // ------------------------------------------------------------------------

    public  static final String EMAIL_TYPE_DEFAULT                      = "default";
    public  static final String EMAIL_TYPE_PASSWORD                     = "password";
    public  static final String EMAIL_TYPE_ACCOUNTS                     = "accounts";
    public  static final String EMAIL_TYPE_SUPPORT                      = "support";
    public  static final String EMAIL_TYPE_NOTIFY                       = "notify";

    // ------------------------------------------------------------------------

    public  static final String TIMEZONE_CONF                           = "timezones.conf";

    // ------------------------------------------------------------------------

    public  static final String ALL_HOSTS                               = "*";
    public  static final String DEFAULT_HOST                            = ALL_HOSTS;
    private static final String DEFAULT_TITLE                           = "Example GPS Tracking";
    private static final String DEFAULT_EMAIL_ADDRESS                   = null;

    // ------------------------------------------------------------------------
    // "Domain" level property definitions

    /* top-level Track properties */
    public  static final String PROP_Track_enableAuthenticationService  = "track.enableAuthenticationService";

    /* AccountLogin properties */
    public  static final String PROP_AccountLogin_showLoginLink         = "accountLogin.showLoginLink";

    /* MenuBar properties */
    public  static final String PROP_MenuBar_openOnMouseOver            = "menuBar.openOnMouseOver";
    public  static final String PROP_MenuBar_usePullDownMenus           = "menuBar.usePullDownMenus";
    public  static final String PROP_MenuBar_includeTextAnchor          = "menuBar.includeTextAnchor";

    /* TopMenu properties */
    public  static final String PROP_TopMenu_showHeader                 = "topMenu.showHeader";
    public  static final String PROP_TopMenu_menuType                   = "topMenu.menuType";
    public  static final String PROP_TopMenu_showMenuDescription        = "topMenu.showMenuDescription";
    public  static final String PROP_TopMenu_showMenuHelp               = "topMenu.showMenuHelp";
    public  static final String PROP_TopMenu_maximumIconsPerRow         = "topMenu.maximumIconsPerRow";

    /* DeviceInfo properties */
    public  static final String PROP_DeviceInfo_showNotificationFields  = "deviceInfo.showNotificationFields";  // true|false
    public  static final String PROP_DeviceInfo_showPropertiesButton    = "deviceInfo.showPropertiesButton";    // true|false
    public  static final String PROP_DeviceInfo_validateNewIDs          = "deviceInfo.validateNewIDs";          // true|false
    public  static final String PROP_DeviceInfo_showNotes               = "deviceInfo.showNotes";               // true|false
    public  static final String PROP_DeviceInfo_showFixedLocation       = "deviceInfo.showFixedLocation";       // true|false
    public  static final String PROP_DeviceInfo_showExpectedAcks        = "deviceInfo.showExpectedAcks";        // true|false
    public  static final String PROP_DeviceInfo_showPushpinChooser      = "deviceInfo.showPushpinChooser";      // true|false

    /* DeviceInfo custom fields */
    public  static final String PROP_DeviceInfo_custom_                 = "deviceInfo.custom.";                 // custom attr

    /* GroupInfo properties */
    public  static final String PROP_GroupInfo_validateNewIDs           = "groupInfo.validateNewIDs";           // true|false
    public  static final String PROP_GroupInfo_showPropertiesButton     = "groupInfo.showPropertiesButton";     // true|false

    /* TrackMap calendar properties */
    public  static final String PROP_TrackMap_calendarAction            = "trackMap.calendarAction";
    public  static final String PROP_TrackMap_calendarDateOnLoad        = "trackMap.calendarDateOnLoad";        // last|current
    public  static final String PROP_TrackMap_showTimezoneSelection     = "trackMap.showTimezoneSelection";     // true|false

    /* TrackMap map update properties */
    public  static final String PROP_TrackMap_mapUpdateOnLoad           = "trackMap.mapUpdateOnLoad";           // all|last
    public  static final String PROP_TrackMap_autoUpdateRecenter        = "trackMap.autoUpdateRecenter";        // no|last|zoom
    public  static final String PROP_TrackMap_showUpdateLast            = "trackMap.showUpdateLast";            // true|false

    /* TrackMap detail report properties */
    public  static final String PROP_TrackMap_detailAscending           = "trackMap.detailAscending";           // true|false
    public  static final String PROP_TrackMap_detailCenterPushpin       = "trackMap.detailCenterPushpin";       // true|false

    /* TrackMap overflow limit type */
    public  static final String PROP_TrackMap_limitType                 = "trackMap.limitType";                 // first|last

    /* TrackMap misc properties */
    public  static final String PROP_TrackMap_fleetDeviceEventCount     = "trackMap.fleetDeviceEventCount";     // 1
    public  static final String PROP_TrackMap_showCursorLocation        = "trackMap.showCursorLocation";        // true|false
    public  static final String PROP_TrackMap_showDistanceRuler         = "trackMap.showDistanceRuler";         // true|false
    public  static final String PROP_TrackMap_showLocateNow             = "trackMap.showLocateNow";             // true|false|device
    public  static final String PROP_TrackMap_showDeviceLink            = "trackMap.showDeviceLink";
    public  static final String PROP_TrackMap_pageLinks                 = "trackMap.pageLinks";                 // <pageIDs>
    public  static final String PROP_TrackMap_showGoogleKML             = "trackMap.showGoogleKML";             // true|false
    public  static final String PROP_TrackMap_mapControlLocation        = "trackMap.mapControlLocation";        // left|right|true|false

    /* ReportMenu properties */
    public  static final String PROP_ReportMenu_useMapDates             = "reportMenu.useMapDates";             // true|false
    public  static final String PROP_ReportMenu_showTimezoneSelection   = "reportMenu.showTimezoneSelection";   // true|false
    public  static final String PROP_ReportMenu_enableReportEmail       = "reportMenu.enableReportEmail";       // true|false

    /* ReportDisplay properties */
    public  static final String PROP_ReportDisplay_showGoogleKML        = "reportDisplay.showGoogleKML";        // true|false

    /* UserInfo properties */
    public  static final String PROP_UserInfo_showPreferredDeviceID     = "userInfo.showPreferredDeviceID";     // true|false
    public  static final String PROP_UserInfo_showPassword              = "userInfo.showPassword";              // true|false
    public  static final String PROP_UserInfo_validateNewIDs            = "userInfo.validateNewIDs";            // true|false

    /* ZoneInfo properties */
    public  static final String PROP_ZoneInfo_mapControlLocation        = "zoneInfo.mapControlLocation";        // left|right|true|false
    public  static final String PROP_ZoneInfo_validateNewIDs            = "zoneInfo.validateNewIDs";            // true|false
    public  static final String PROP_ZoneInfo_enableGeocode             = "zoneInfo.enableGeocode";             // true|false
    public  static final String PROP_ZoneInfo_showOverlapPriority       = "zoneInfo.showOverlapPriority";       // true|false
    public  static final String PROP_ZoneInfo_showArriveDepartZone      = "zoneInfo.showArriveDepartZone";      // true|false
    public  static final String PROP_ZoneInfo_showClientUploadZone      = "zoneInfo.showClientUploadZone";      // true|false

    /* RoleInfo properties */
    public  static final String PROP_RoleInfo_validateNewIDs            = "roleInfo.validateNewIDs";            // true|false

    /* RuleInfo properties */
    public  static final String PROP_RuleInfo_validateNewIDs            = "ruleInfo.validateNewIDs";            // true|false
    public  static final String PROP_RuleInfo_showEMailWrapper          = "ruleInfo.showEMailWrapper";          // true|false
    public  static final String PROP_RuleInfo_showSysRulesOnly          = "ruleInfo.showSysRulesOnly";          // true|false

    /* DeviceChooser misc properties */
    public  static final String PROP_DeviceChooser_sortBy               = "deviceChooser.sortBy";               // id|name|description
    public  static final String PROP_DeviceChooser_useTable             = "deviceChooser.useTable";             // true|false
    public  static final String PROP_DeviceChooser_idPosition           = "deviceChooser.idPosition";           // none|first|last (table only)
    public  static final String PROP_DeviceChooser_search               = "deviceChooser.search";               // true|false (table only)
    public  static final String PROP_DeviceChooser_singleItemTextField  = "deviceChooser.singleItemTextField";  // true|false (hint)
    public  static final String PROP_DeviceChooser_includeListHtml      = "deviceChooser.includeListHtml";      // include iniitial HTML

    // ---
    public  static final String PROP_DeviceChooser_extraDebugEntries    = "deviceChooser.extraDebugEntries";    // int

    /* StatusCodeInfo misc properties */
    public  static final String PROP_StatusCodeInfo_showIconSelector    = "statusCodeInfo.showIconSelector";    // true|false "Rule" selector
    public  static final String PROP_StatusCodeInfo_showPushpinChooser  = "statusCodeInfo.showPushpinChooser";  // true|false

    /* DeviceAlerts misc properties */
    public  static final String PROP_DeviceAlerts_refreshInterval       = "deviceAlerts.refreshInterval";       // #seconds
    public  static final String PROP_DeviceAlerts_mapPageName           = "deviceAlerts.mapPageName";           // map page name
    public  static final String PROP_DeviceAlerts_showAllDevices        = "deviceAlerts.showAllDevices";        // true|false
    public  static final String PROP_DeviceAlerts_maxActiveAlertAge     = "deviceAlerts.maxActiveAlertAge";     // #seconds

    /* SysAdminAccounts misc properties */
    public  static final String PROP_SysAdminAccounts_showPasswords     = "sysAdminAccounts.showPasswords";     // true|false
    public  static final String PROP_SysAdminAccounts_validateNewIDs    = "sysAdminAccounts.validateNewIDs";    // true|false
    public  static final String PROP_SysAdminAccounts_allowAccountLogin = "sysAdminAccounts.allowAccountLogin"; // true|false
    public  static final String PROP_SysAdminAccounts_accountProperties = "sysAdminAccounts.accountProperties"; // true|false

    /* Calendar properties */
    public  static final String PROP_Calendar_firstDayOfWeek            = "calendar.firstDayOfWeek";            // 0..6 (Sun..Sat)
    public  static final String PROP_Calendar_timeTextField             = "calendar.timeTextField";             // true|false
  //public  static final String PROP_Calendar_timeTextField_hourInc     = "calendar.timeTextField.hourInc";     // int
  //public  static final String PROP_Calendar_timeTextField_minuteInc   = "calendar.timeTextField.minuteInc";   // int

    /* "loginSession_banner.jsp" properties */
    public  static final String PROP_Banner_width                       = "banner.width";
    public  static final String PROP_Banner_style                       = "banner.style";
    public  static final String PROP_Banner_imageSource                 = "banner.imageSource";
    public  static final String PROP_Banner_imageWidth                  = "banner.imageWidth";
    public  static final String PROP_Banner_imageHeight                 = "banner.imageHeight";
    public  static final String PROP_Banner_imageLink                   = "banner.imageLink";

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    // host name key
    private String                      primaryHostName         = DEFAULT_HOST;
    private java.util.List<String>      hostAliasList           = null;
    private String                      aliasName               = null;

    // base URL
    private String                      defaultBaseURL          = null;

    // account/user login (display account/user name on login screen)
    private boolean                     acctLogin               = true;  // if true, Account displayed
    private boolean                     userLogin               = false; // if true, User    displayed
    private boolean                     allowEmailLogin         = false; // if true, contact-email login allowed
    private String                      defaultLoginAccount     = null;
    private String                      defaultLoginUser        = null;

    // restricted (requires account to explicitly specify this private label name)
    private boolean                     isRestricted            = false;
    
    // locale (IE. "en_US")
    private String                      localeStr               = null;
    private String                      dateFormat              = null;
    private String                      timeFormat              = null;

    // enable demo (display 'Demo' button on login screen)
    private boolean                     enableDemo              = true;

    // Properties
    private OrderedMap<String,Object>   propMap                 = new OrderedMap<String,Object>();
    private RTProperties                rtProps                 = new RTProperties(this.propMap);

    // TimeZones
    private OrderedSet<String>          timeZonesList           = null;
    private String                      timeZonesArray[]        = null;

    // (Reverse)GeocodeProviders
    private OrderedMap<String,GeocodeProvider>          geocodeProvider = null;
    private OrderedMap<String,ReverseGeocodeProvider>   revgeoProvider  = null;

    // map of PrivateLabel ACLs
    private AccessLevel                 dftAccLevel             = null;
    private Map<String,AclEntry>        privateAclMap           = null;
    private AclEntry                    allAclEntries[]         = null;

    // Event Notification EMail
    private String                      eventNotifyFrom         = null;
    private I18N.Text                   eventNotifySubj         = null;
    private I18N.Text                   eventNotifyBody         = null;
    private boolean                     eventNotifyDefault      = false;

    // StatusCode description overrides
    private boolean                     statusCodeOnly          = false;
    private Map<Integer,StatusCodes.Code> statusCodes           = null;

    /**
    *** Constructor 
    **/
    protected BasicPrivateLabel()
    {
        super();
    }

    /**
    *** Constructor 
    *** @param host  The primary host name associated with this BasicPrivateLabel
    **/
    public BasicPrivateLabel(String host)
    {
        this();
        this.setHostName(host);
    }

    //public BasicPrivateLabel(String host, String title)
    //{
    //    this();
    //    this.setHostName(host);
    //    this.setPageTitle(title);
    //}

    // ------------------------------------------------------------------------

    /**
    *** Sets the default BaseURL (ie. "http://localhost:8080/track/Track")
    *** @param baseURL  The base URL
    **/
    public void setDefaultBaseURL(String baseURL)
    {
        this.defaultBaseURL = !StringTools.isBlank(baseURL)? baseURL : null;
    }
    
    /**
    *** Returns true if a base URL has been defined
    *** @return True if a base URL has been defined
    **/
    public boolean hasDefaultBaseURL()
    {
        return !StringTools.isBlank(this.defaultBaseURL);
    }

    /**
    *** Gets the default BaseURL (or null if no base URL is defined)
    *** @return The default BaseURL
    **/
    public String getDefaultBaseURL()
    {
        return this.defaultBaseURL;
    }

    // ------------------------------------------------------------------------

    /**
    *** Return String representation of this instance
    *** @return String representation of this instance
    **/
    public String toString()
    {
        return this.getHostName();
    }

    /**
    *** Sets the primary host name associated with this BasicPrivateLabel
    *** @param host  The primary host name to associate with this BasicPrivateLabel
    **/
    public void setHostName(String host)
    {
        this.primaryHostName = host;
    }
    
    /**
    *** Gets the primary host name associated with this BasicPrivateLabel
    *** @return The primary host name associated with this BasicPrivateLabel
    **/
    public String getHostName()
    {
        return (this.primaryHostName != null)? this.primaryHostName : DEFAULT_HOST;
    }
    
    /**
    *** Adds a host alias to this BasicPrivateLabel
    *** @param host The host alias to add
    **/
    public void addHostAlias(String host)
    {
        if (!StringTools.isBlank(host)) {
            if (this.hostAliasList == null) {
                this.hostAliasList = new Vector<String>();
            }
            this.hostAliasList.add(host.trim());
        }
    }
    
    /**
    *** Gets the list of host name aliases
    *** @return The list of host nmae aliases
    **/
    public java.util.List<String> getHostAliasList()
    {
        return this.hostAliasList;
    }

    /**
    *** Sets the host alias name
    *** @param name  The host alias name
    **/
    public void setAliasName(String name)
    {
        if (StringTools.isBlank(name)) {
            this.aliasName = null;
        } else {
            this.aliasName = name;
            this.setProperty(RTKey.SESSION_NAME, this.aliasName);
            //this.printProperties();
        }
    }
    
    /**
    *** Gets the host alias name
    *** @return The host alias name
    **/
    public String getAliasName()
    {
        return this.aliasName; // may be null
    }

    /**
    *** Gets the name of this BasicPrivateLabel.  
    *** @return If specified, the alias name will be returned, otherwise the host name will be reutrned
    **/
    public String getName()
    {
        String alias = this.getAliasName();
        if (!StringTools.isBlank(alias)) {
            return alias;
        } else {
            return this.getHostName();
        }
    }
    
    /**
    *** Returns the primary host name and alias name
    *** (TODO: should also return all added aliases?)
    *** @return An array of primary host/alias names
    **/
    public String[] getNames()
    {
        String host  = this.getHostName();
        String alias = this.getAliasName();
        if (!StringTools.isBlank(alias)) {
            return new String[] { host, alias };
        } else {
            return new String[] { host };
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the account login display state
    *** @param acctLogin True to display account login, false otherwise
    **/
    public void setAccountLogin(boolean acctLogin)
    {
        this.acctLogin = acctLogin;
    }
    
    /**
    *** Gets the account login display state 
    *** @return True to display the account login, false to hide account login (if implemented)
    **/
    public boolean getAccountLogin()
    {
        return this.acctLogin;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the user login display state
    *** @param userLogin True to display user login, false otherwise
    **/
    public void setUserLogin(boolean userLogin)
    {
        this.userLogin = userLogin;
    }
    
    /**
    *** Gets the user login display state 
    *** @return True to display the user login, false to hide user login
    **/
    public boolean getUserLogin()
    {
        return this.userLogin;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the Enabled/Disabled contact-email login state
    *** @param emailLogin True to enable contact-email login
    **/
    public void setAllowEmailLogin(boolean emailLogin)
    {
        this.allowEmailLogin = emailLogin;
    }
    
    /**
    *** Gets the Enabled/Disabled contact-email login state
    *** @return True if contact-email login is enabled
    **/
    public boolean getAllowEmailLogin()
    {
        return this.allowEmailLogin;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the default login account name
    *** @param defaultAccount The default login account
    **/
    public void setDefaultLoginAccount(String defaultAccount)
    {
        this.defaultLoginAccount = defaultAccount;
    }
    
    /**
    *** Gets the default login account name
    *** @return The default login account name
    **/
    public String getDefaultLoginAccount()
    {
        if (!StringTools.isBlank(this.defaultLoginAccount)) {
            return this.defaultLoginAccount;
        } else {
            return "";
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the default login user name
    *** @param defaultUser The default login user
    **/
    public void setDefaultLoginUser(String defaultUser)
    {
        this.defaultLoginUser = defaultUser;
    }
    
    /**
    *** Gets the default login user name
    *** @return The default login user name
    **/
    public String getDefaultLoginUser()
    {
        if (!StringTools.isBlank(this.defaultLoginUser)) {
            return this.defaultLoginUser;
        } else {
            return User.getAdminUserID();
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the locale String code for this BasicPrivateLabel
    *** @param localeStr  The locale String associated with this BasicPrivateLabel
    **/
    public void setLocaleString(String localeStr)
    {
        this.localeStr = localeStr;
        this.setProperty(RTKey.SESSION_LOCALE, this.localeStr);
        this.setProperty(RTKey.LOCALE, this.localeStr);
    }
    
    /**
    *** Gets the locale String code for this BasicPrivateLabel
    *** @return The locale String code for this BasicPrivateLabel
    **/
    public String getLocaleString()
    {
        return (this.localeStr != null)? this.localeStr : "";
    }
    
    /**
    *** Gets the Locale for the current locale String code
    *** @return The Locale associated with this BasicPrivateLabel
    **/
    public Locale getLocale()
    {
        return I18N.getLocale(this.getLocaleString());
    }
    
    /**
    *** Gets the I18N instance for the specified class using the Locale associated with this BasicPrivateLabel
    *** @param clazz  The class for which the I18N instance will be returned
    *** @return The I18N instance for the specified class
    **/
    public I18N getI18N(Class clazz)
    {
        return I18N.getI18N(clazz, this.getLocale());
    }
    
    /**
    *** Gets the I18N instance for the specified package using the Locale associated with this BasicPrivateLabel
    *** @param pkg  The package for which the I18N instance will be returned
    *** @return The I18N instance for the specified package
    **/
    public I18N getI18N(Package pkg)
    {
        return I18N.getI18N(pkg, this.getLocale());
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the date format for the current BasicPrivateLabel
    *** @param dateFmt  The date format
    **/
    public void setDateFormat(String dateFmt)
    {
        this.dateFormat = ((dateFmt != null) && !dateFmt.equals(""))? dateFmt : null;
    }

    /**
    *** Gets the date format for this BasicPrivateLabel
    *** @return The date format
    **/
    public String getDateFormat()
    {
        if (this.dateFormat == null) {
            this.dateFormat = BasicPrivateLabel.getDefaultDateFormat();
        }
        return this.dateFormat;
    }
    
    /**
    *** Gets the default date format
    *** @return The default date format
    **/
    public static String getDefaultDateFormat()
    {
        // ie. "yyyy/MM/dd"
        String fmt = RTConfig.getString(RTKey.LOCALE_DATEFORMAT,RTKey.DEFAULT_DATEFORMAT);
        return ((fmt != null) && !fmt.equals(""))? fmt : DateTime.DEFAULT_DATE_FORMAT;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the time format for this BasicPrivateLabel
    *** @param timeFmt  The time format
    **/
    public void setTimeFormat(String timeFmt)
    {
        this.timeFormat = ((timeFmt != null) && !timeFmt.equals(""))? timeFmt : null;
    }

    /**
    *** Gets the time format for this BasicPrivateLabel
    *** @return The time format
    **/
    public String getTimeFormat()
    {
        if (this.timeFormat == null) {
            this.timeFormat = BasicPrivateLabel.getDefaultTimeFormat();
        }
        return this.timeFormat;
    }
    
    /**
    *** Gets the default time format
    *** @return The default time format
    **/
    public static String getDefaultTimeFormat()
    {
        // ie. "HH:mm:ss"
        String fmt = RTConfig.getString(RTKey.LOCALE_TIMEFORMAT,RTKey.DEFAULT_TIMEFORMAT);
        return ((fmt != null) && !fmt.equals(""))? fmt : DateTime.DEFAULT_TIME_FORMAT;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the 'restricted mode' for this BasicPrivateLabel.  This means that only Account
    *** that reference this particular BasicPrivalLable name have access to the resources
    *** defined by this BasicPrivateLabel.
    *** @param restricted  True to enforce restricted access, false otherwise
    **/
    public void setRestricted(boolean restricted)
    {
        this.isRestricted = restricted;
    }
    
    /**
    *** Returns true this is BasicPrivateLabel has restricted access
    *** @return True if this BasicPrivateLabel has restricted access
    **/
    public boolean isRestricted()
    {
        return this.isRestricted;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the 'demo' mode for this BasicPrivateLabel
    *** @param isDemo  True to enable 'demo' support, false to disable.
    **/
    public void setEnableDemo(boolean isDemo)
    {
        this.enableDemo = isDemo;
    }
    
    /**
    *** Returns true if this BasicPrivateLabel supports a 'demo' mode
    *** @return True if 'demo' mode is supported, false otherwise
    **/
    public boolean getEnableDemo()
    {
        return this.enableDemo;
    }
    
    // ------------------------------------------------------------------------

    /**
    *** Clears TimeZone cache
    **/
    public void clearTimeZones()
    {
        this.timeZonesList  = null;
        this.timeZonesArray = null;
    }

    /**
    *** Sets the TimeZones supported by this BasicPrivateLabel
    *** @param tmz  The list of supported TimeZones
    **/
    public void setTimeZones(OrderedSet<String> tmz)
    {
        this.clearTimeZones();
        this.timeZonesList = new OrderedSet<String>(tmz); // clone
    }

    /**
    *** Sets the TimeZones supported by this BasicPrivateLabel
    *** @param tmz  The list of supported TimeZones
    **/
    public void setTimeZones(java.util.List<String> tmz)
    {
        this.clearTimeZones();
        this.timeZonesList = new OrderedSet<String>(tmz); // clone
    }

    /**
    *** Sets the TimeZones supported by this BasicPrivateLabel
    *** @param tmz  The array of supported TimeZones
    **/
    public void setTimeZones(String tmz[])
    {
        this.clearTimeZones();
        this.timeZonesList = new OrderedSet<String>();
        if (tmz != null) {
            for (int i = 0; i < tmz.length; i++) {
                String tz = tmz[i].trim();
                if (!tz.equals("")) {
                    this.timeZonesList.add(tz);
                }
            }
        }
    }

    /**
    *** Gets the list of supported TimeZones
    *** @return The list of supported TimeZones
    **/
    public java.util.List<String> getTimeZonesList()
    {
        if (this.timeZonesList == null) { 
            // 'this.timeZonesList' is initialized at startup
            this.clearTimeZones();
            this.timeZonesList = new OrderedSet<String>();
        }
        return this.timeZonesList;
    }

    /**
    *** Gets an array of supported TimeZones
    *** @return The array of supported TimeZones
    **/
    public String[] getTimeZones()
    {
        if (this.timeZonesArray == null) {
            java.util.List<String> tzList = this.getTimeZonesList();
            synchronized (tzList) {
                if (this.timeZonesArray == null) {
                    // reconstruct TimeZone Array from TimeZone List
                    this.timeZonesArray = tzList.toArray(new String[tzList.size()]);
                }
            }
        }
        return this.timeZonesArray;
    }

    /* array of all time-zones */
    private static boolean didInitAllTimeZones = false;
    private static String  allTimeZones[] = null;
    
    /**
    *** Returns an array of all possible TimeZone names
    *** @return An array of all possible TimeZone names
    **/
    public static String[] getAllTimeZones()
    {
        if (!BasicPrivateLabel.didInitAllTimeZones) {
            BasicPrivateLabel.didInitAllTimeZones = true;
            File cfgFile = RTConfig.getLoadedConfigFile();
            if (cfgFile != null) {
                File tmzFile = new File(cfgFile.getParentFile(), TIMEZONE_CONF);
                BasicPrivateLabel.allTimeZones = DateTime.readTimeZones(tmzFile); // may still be null
            }
        }
        return BasicPrivateLabel.allTimeZones;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the title used on HTML pages displayed for this BasicPrivateLabel
    *** @param text  The title of the HTML page
    **/
    public void setPageTitle(I18N.Text text)
    {
        this.setI18NTextProperty(BasicPrivateLabelLoader.TAG_PageTitle, text);
    }

    /**
    *** Gets the HTML page title for this BasicPrivateLabel
    *** @return The HTML page title
    **/
    public String getPageTitle()
    {
        return this.getI18NTextString(BasicPrivateLabelLoader.TAG_PageTitle, DEFAULT_TITLE);
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the GeocodeProvider for this BasicPrivateLabel
    *** @param gp The GeocodeProvider
    **/
    public void addGeocodeProvider(GeocodeProvider gp)
    {
        if (gp != null) {
            if (this.geocodeProvider == null) {
                this.geocodeProvider = new OrderedMap<String,GeocodeProvider>();
            }
            this.geocodeProvider.put(gp.getName().toLowerCase(), gp);
        }
    }

    /**
    *** Returns the active GeocodeProvider for this BasicPrivatelabel
    *** @return The active GeocodeProvider for this BasicPrivatelabel
    **/
    public GeocodeProvider getGeocodeProvider()
    {
        if ((this.geocodeProvider != null) && (this.geocodeProvider.size() > 0)) {
            return this.geocodeProvider.getValue(0);
        } else {
            // TODO: return a default?
            return null;
        }
    }

    /**
    *** Returns the named GeocodeProvider for this BasicPrivatelabel
    *** @param name  The named GeocodeProvider to return
    *** @return The named GeocodeProvider for this BasicPrivatelabel
    **/
    public GeocodeProvider getGeocodeProvider(String name)
    {
        if ((name != null) && (this.geocodeProvider != null)) {
            return this.geocodeProvider.get(name.toLowerCase());
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the ReverseGeocodeProvider for this BasicPrivateLabel
    *** @param rgp  The ReverseGeocodeProvider
    **/
    public void addReverseGeocodeProvider(ReverseGeocodeProvider rgp)
    {
        if (rgp != null) {
            if (this.revgeoProvider == null) {
                this.revgeoProvider = new OrderedMap<String,ReverseGeocodeProvider>();
            }
            this.revgeoProvider.put(rgp.getName().toLowerCase(), rgp);
            //Print.logInfo("Added ReverseGeocodeProvider: [%s] %s", this, rgp.getName());
        }
    }

    /**
    *** Returns the active ReverseGeocodeProvider for this BasicPrivatelabel
    *** @return The active ReverseGeocodeProvider for this BasicPrivatelabel
    **/
    public ReverseGeocodeProvider getReverseGeocodeProvider()
    {
        if ((this.revgeoProvider != null) && (this.revgeoProvider.size() > 0)) {
            return this.revgeoProvider.getValue(0);
        } else {
            // TODO: return a default?
            return null;
        }
    }

    /**
    *** Returns the named ReverseGeocodeProvider for this BasicPrivatelabel
    *** @param name  The named ReverseGeocodeProvider to return
    *** @return The named ReverseGeocodeProvider for this BasicPrivatelabel
    **/
    public ReverseGeocodeProvider getReverseGeocodeProvider(String name)
    {
        if ((name != null) && (this.revgeoProvider != null)) {
            return this.revgeoProvider.get(name.toLowerCase());
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Return the MapProvider's Pushpin index for the specified pushpin ID
    *** @param pushpinID      The pushpin ID
    *** @param dftIndex       The default index value (0..9 are always defined)
    *** @return The pushpin icon index
    **/
    public int getPushpinIconIndex(String pushpinID, int dftIndex)
    {
        return this.getPushpinIconIndex(null, pushpinID, dftIndex);
    }

    /**
    *** Return the MapProvider's Pushpin index for the specified pushpin ID
    *** @param mapProviderID  The MapProvider ID (may be null)
    *** @param pushpinID      The pushpin ID
    *** @param dftIndex       The default index value (0..9 are always defined)
    *** @return The pushpin icon index
    **/
    public int getPushpinIconIndex(String mapProviderID, String pushpinID, int dftIndex)
    {
        // PrivateLabel overrides this method to provide the specific MapProvider index
        return dftIndex;
    }
    
    // ------------------------------------------------------------------------

    /**
    *** Sets the copyright notice for this BasicPrivateLabel
    *** @param copyright  The copyright notice
    **/
    public void setCopyright(String copyright)
    {
        String c = (copyright != null)? copyright.trim() : null;
        this.setStringProperty(BasicPrivateLabelLoader.TAG_Copyright, c);
    }

    /**
    *** Gets the copyright notice for this BasicPrivateLabel
    *** @return The copyright notice
    **/
    public String getCopyright()
    {
        String copyright = this.getStringProperty(BasicPrivateLabelLoader.TAG_Copyright, null);
        if (copyright != null) {
            return copyright;
        } else {
            return "Copyright (C) " + this.getPageTitle();
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the user Access-Control for this BasicPrivateLabel
    *** @param dftAccess  The defalt access level
    *** @param aclMap     The access-control map
    **/
    public void setAclMap(AccessLevel dftAccess, Map<String,AclEntry> aclMap)
    {
        
        /* default access (only if not already set) */
        if ((this.dftAccLevel == null) && (dftAccess != null)) {
            this.dftAccLevel = dftAccess;
        }
        
        /* ACL map */
        if (this.privateAclMap == null) {
            this.privateAclMap = aclMap;
        } else {
            this.privateAclMap.putAll(aclMap);
        }
        
        /* clean ALL Acl entries (reloaded later) */
        this.allAclEntries = null;
        
    }

    /**
    *** Gets the maximum access-control level for this BasicPrivateLabel
    *** @return The maximum acces-control level
    **/
    public AccessLevel getMaximumAccessLevel(String aclName)
    {
        if (this.privateAclMap != null) {
            AclEntry acl = this.privateAclMap.get(aclName);
            return (acl != null)? acl.getMaximumAccessLevel() : AccessLevel.ALL;
        } else {
            return AccessLevel.ALL;
        }
    }

    /**
    *** Gets the global default access-control level for this BasicPrivateLabel
    *** @return The default access-control level (does not reutrn null)
    **/
    public AccessLevel getDefaultAccessLevel()
    {
        return (this.dftAccLevel != null)? this.dftAccLevel : AccessLevel.ALL;
    }

    /**
    *** Gets the default access-control level for this BasicPrivateLabel
    *** @param aclName  The ACL key
    *** @return The default acces-control level (does not reutrn null)
    **/
    public AccessLevel getDefaultAccessLevel(String aclName)
    {
        if (this.privateAclMap != null) {
            AclEntry acl = this.privateAclMap.get(aclName);
            return (acl != null)? acl.getDefaultAccessLevel() : this.getDefaultAccessLevel();
        } else {
            return this.getDefaultAccessLevel();
        }
    }

    /**
    *** Returns the AclEntry for the specified key
    *** @return The AclEntry, or null if the key does not exist
    **/
    public AclEntry getAclEntry(String aclName)
    {
        if ((this.privateAclMap != null) && !StringTools.isBlank(aclName)) {
            return this.privateAclMap.get(aclName);
        } else {
            return null;
        }
    }

    /**
    *** Returns true if the AclEntry key is defined
    *** @return True if the AclEntry key is defined
    **/
    public boolean hasAclEntry(String aclName)
    {
        return (this.getAclEntry(aclName) != null);
    }

    /**
    *** Returns all defined AclEntries
    *** @return An array of AclEntry items
    **/
    public AclEntry[] getAllAclEntries()
    {
        // TODO: postInit
        if (this.allAclEntries == null) {
            if (this.privateAclMap != null) {
                this.allAclEntries = new AclEntry[this.privateAclMap.size()];
                int a = 0;
                for (Iterator i = this.privateAclMap.values().iterator(); i.hasNext();) {
                    this.allAclEntries[a++] = (AclEntry)i.next();
                }
            } else {
                this.allAclEntries = new AclEntry[0];
            }
        }
        return this.allAclEntries;
    }

    // ------------------------------------------------------------------------

    /**
    *** Returns true is user has 'ALL' access rights for the specified ACL key
    *** @param role     The Role
    *** @param aclName  The ACL key
    *** @return True is user has 'ALL' access rights for the specified ACL key
    **/
    public AccessLevel getAccessLevel(Role role, String aclName)
    {
        AclEntry aclEntry = this.getAclEntry(aclName);
        AccessLevel dft = (aclEntry != null)? aclEntry.getDefaultAccessLevel() : this.getDefaultAccessLevel();
        AccessLevel acl = (role != null)? RoleAcl.getAccessLevel(role,aclName,dft) : dft;
        return acl;
    }

    /**
    *** Returns true is user has 'ALL' access rights for the specified ACL key
    *** @param user     The User
    *** @param aclName  The ACL key
    *** @return True is user has 'ALL' access rights for the specified ACL key
    **/
    public AccessLevel getAccessLevel(User user, String aclName)
    {
        if (User.isAdminUser(user)) {
            AclEntry aclEntry = this.getAclEntry(aclName);
            return (aclEntry != null)? aclEntry.getMaximumAccessLevel() : AccessLevel.ALL; // 'admin' has all rights
        } else {
            AccessLevel acl = UserAcl.getAccessLevel(user, aclName, null);
            return (acl != null)? acl : this.getAccessLevel(user.getRole(),aclName);
        }
    }

    /**
    *** Returns true is user has 'ALL' access rights for the specified ACL key
    *** @param user     The User
    *** @param aclName  The ACL key
    *** @return True is user has 'ALL' access rights for the specified ACL key
    **/
    public boolean hasAllAccess(User user, String aclName)
    {
        return AclEntry.okAll(this.getAccessLevel(user,aclName));
    }

    /**
    *** Returns true is user has 'WRITE' access rights for the specified ACL key
    *** @param user     The User
    *** @param aclName  The ACL key
    *** @return True is user has 'WRITE' access rights for the specified ACL key
    **/
    public boolean hasWriteAccess(User user, String aclName)
    {
        return AclEntry.okWrite(this.getAccessLevel(user,aclName));
    }

    /**
    *** Returns true is user has 'READ' access rights for the specified ACL key
    *** @param user     The User
    *** @param aclName  The ACL key
    *** @return True is user has 'READ' access rights for the specified ACL key
    **/
    public boolean hasReadAccess(User user, String aclName)
    {
        return AclEntry.okRead(this.getAccessLevel(user,aclName));
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the event notification Email attributes
    *** @param emailFrom     The EMail 'From' address
    *** @param emailSubj     The EMail 'Subject'
    *** @param emailBody     The EMail 'Body'
    *** @param useAsDefault  True to use this subj/body as the default entry for Rule notifications
    **/
    public void setEventNotificationEMail(String emailFrom, 
        I18N.Text emailSubj, I18N.Text emailBody, boolean useAsDefault)
    {
        this.eventNotifyFrom    = SendMail.getUserFromEmailAddress(StringTools.trim(emailFrom));
        this.eventNotifySubj    = emailSubj;   // may be null
        this.eventNotifyBody    = emailBody;   // may be null
        this.eventNotifyDefault = useAsDefault && ((emailSubj != null) || (emailBody != null));
        this.setEMailAddress(EMAIL_TYPE_NOTIFY, this.eventNotifyFrom);
    }
    
    /**
    *** Gets the EMail notification 'From' address
    *** @return The Email notification 'From' address
    **/
    public String getEventNotificationFrom()
    {
        return SendMail.getUserFromEmailAddress(this.eventNotifyFrom);
    }
    
    /**
    *** Return true if the notification subject and/or message is defined
    *** @return True if the notification subject and/or message is defined
    **/
    public boolean hasEventNotificationEMail()
    {
        return (this.eventNotifySubj != null) || (this.eventNotifyBody != null);
    }

    /**
    *** Gets the EMail notification 'Subject'
    *** @return The Email notification 'Subject'
    **/
    public String getEventNotificationSubject()
    {
        if (this.eventNotifySubj != null) {
            return this.eventNotifySubj.toString(this.getLocale());
        } else {
            return null;
        }
    }

    /**
    *** Gets the EMail notification message 'Body'
    *** @return The Email notification message 'Body'
    **/
    public String getEventNotificationBody()
    {
        if (this.eventNotifyBody != null) {
            return this.eventNotifyBody.toString(this.getLocale());
        } else {
            return null;
        }
    }

    /**
    *** Returns true if the email notification subject/body is to be used as the
    *** default entry for new created Rule definitions.
    *** @return True if the event notification subject/body is to be used as the default.
    **/
    public boolean getEventNotificationDefault()
    {
        return this.eventNotifyDefault;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the default EMail 'From' addresses
    *** @param type  The 'type' of default EMail address
    *** @param emailAddr  The EMail address
    **/
    public void setEMailAddress(String type, String emailAddr)
    {
        if (!StringTools.isBlank(emailAddr)) {
            emailAddr = SendMail.getUserFromEmailAddress(emailAddr.trim());
            //if (emailAddr.endsWith("example.com")) {
            //    String t = (StringTools.isBlank(type) || type.equals(EMAIL_TYPE_DEFAULT))? "<default>" : type;
            //    Print.logWarn("EMail address not yet customized ["+t+"] '"+emailAddr+"'");
            //}
            if (StringTools.isBlank(type) || type.equals(EMAIL_TYPE_DEFAULT)) {
                // explicitly set default email address
                this.setProperty(BasicPrivateLabelLoader.TAG_EMailAddress, emailAddr);
            } else {
                this.setProperty(BasicPrivateLabelLoader.TAG_EMailAddress + "_" + type, emailAddr);
                if (!this.hasProperty(BasicPrivateLabelLoader.TAG_EMailAddress)) {
                    // set default email address, if not already defined
                    this.setProperty(BasicPrivateLabelLoader.TAG_EMailAddress, emailAddr);
                }
            }
        }
    }

    /**
    *** Gets the 'From' EMail address for the specified type
    *** @param type  The 'type' of EMail address to return
    *** @return The 'From' EMail address for the specified type
    **/
    public String getEMailAddress(String type)
    {
        String email = null;
        if (StringTools.isBlank(type) || type.equals(EMAIL_TYPE_DEFAULT)) {
            email = this.getStringProperty(BasicPrivateLabelLoader.TAG_EMailAddress, null);
            if (email == null) { email = DEFAULT_EMAIL_ADDRESS; }
        } else {
            email = this.getStringProperty(BasicPrivateLabelLoader.TAG_EMailAddress + "_" + type, null);
            if (email == null) {
                email = this.getStringProperty(BasicPrivateLabelLoader.TAG_EMailAddress, null);
                if (email == null) { email = DEFAULT_EMAIL_ADDRESS; }
            }
        }
        return SendMail.getUserFromEmailAddress(email);
    }
    
    /**
    *** Returns an array of all defined EMail addresses (used by CHeckInstall)
    *** @return An array of defined EMail addresses
    **/
    public String[] getEMailAddresses()
    {
        java.util.List<String> list = new Vector<String>();
        for (Iterator i = this.propMap.keySet().iterator(); i.hasNext();) {
            String key = (String)i.next();
            if (key.startsWith(BasicPrivateLabelLoader.TAG_EMailAddress)) {
                list.add(this.getStringProperty(key,"?"));
            }
        }
        return list.toArray(new String[list.size()]);
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets (appends) the the specified properties to this BasicPrivateLabel
    **/
    public void setRTProperties(RTProperties rtp)
    {
        this.rtProps.setProperties(rtp);
    }

    /**
    *** Gets the properties of this BasicPrivateLabel
    **/
    public RTProperties getRTProperties()
    {
        return this.rtProps;
    }

    /**
    *** Pushes the properties of this BasicPrivateLabel on the temporary RTConfig properties stack
    **/
    public void pushRTProperties()
    {
        RTConfig.pushTemporaryProperties(this.rtProps);
    }

    /**
    *** Pops the properties of this BasicPrivateLabel from the temporary RTConfig properties stack
    **/
    public void popRTProperties()
    {
        RTConfig.popTemporaryProperties(this.rtProps);
        // stack count should always now be '0'
    }
    
    /**
    *** Prints the current properties to stdout
    **/
    public void printProperties()
    {
        this.rtProps.printProperties("PrivateLabel Properties: " + this.getName());
    }

    /**
    *** Sets the property value for the specified key
    *** @param key  The property key
    *** @param value  The property value
    **/
    public void setProperty(Object key, Object value)
    {
        String k = StringTools.trim(key);
        if (value != null) {
            this.propMap.put(k, value);
        } else {
            this.propMap.remove(k);
        }
    }

    /**
    *** Sets the property String value for the specified key
    *** @param key    The property key
    *** @param value  The property String value
    **/
    public void setStringProperty(String key, String value)
    {
        this.setProperty(key, value);
    }

    /**
    *** Sets the property I18N value for the specified key
    *** @param key    The property key
    *** @param value  The property I18N value
    **/
    public void setI18NTextProperty(String key, I18N.Text value)
    {
        this.setProperty(key, value);
    }

    /**
    *** Gets the property value for the specified key
    *** @param key  The property key
    *** @return The property value
    **/
    public Object getProperty(String key)
    {
        String k = (key != null)? key : "";
        Object obj = this.propMap.get(k);
        return obj;
    }

    /**
    *** Gets the property keys matching the specified key prefix
    *** @param keyPrefix  The property key prefix
    *** @return A collection of property keys (a show copy)
    **/
    public Collection<String> getPropertyKeys(String keyPrefix)
    {
        return this.rtProps.getPropertyKeys(keyPrefix);
    }

    /**
    *** Returns true if the property key is defined by thie BasicPrivateLabel
    *** @param key  The property key
    *** @return True if the specified property key is defined by this BasicPrivateLabel
    **/
    public boolean hasProperty(String key)
    {
        return (key != null)? this.propMap.containsKey(key) : false;
    }

    /**
    *** Gets the String property value for the specified key
    *** @param key  The property key
    *** @param dft  The default value returned if the property key is not defined
    *** @return The property String value
    **/
    public String getStringProperty(String key, String dft)
    {
        Object obj = this.getProperty(key);
        return (obj != null)? obj.toString() : dft;
    }

    /**
    *** Gets the I18N property value for the specified key
    *** @param key  The property key
    *** @param dft  The default value returned if the property key is not defined
    *** @return The property I18N value
    **/
    public I18N.Text getI18NTextProperty(String key, I18N.Text dft)
    {
        Object obj = this.getProperty(key);
        return (obj instanceof I18N.Text)? (I18N.Text)obj : dft;
    }

    /**
    *** Gets the Localized text for the specified String key
    *** @return The HTML page title
    **/
    public String getI18NTextString(String key, String dft)
    {
        I18N.Text text = this.getI18NTextProperty(key, null);
        return (text != null)? text.toString(this.getLocale()) : dft;
    }

    /**
    *** Gets the double property value for the specified key
    *** @param key  The property key
    *** @param dft  The default value returned if the property key is not defined
    *** @return The property double value
    **/
    public double getDoubleProperty(String key, double dft)
    {
        Object obj = this.getProperty(key);
        return (obj != null)? StringTools.parseDouble(obj,dft) : dft;
    }

    /**
    *** Gets the long property value for the specified key
    *** @param key  The property key
    *** @param dft  The default value returned if the property key is not defined
    *** @return The property long value
    **/
    public long getLongProperty(String key, long dft)
    {
        Object obj = this.getProperty(key);
        return (obj != null)? StringTools.parseLong(obj,dft) : dft;
    }

    /**
    *** Gets the int property value for the specified key
    *** @param key  The property key
    *** @param dft  The default value returned if the property key is not defined
    *** @return The property int value
    **/
    public int getIntProperty(String key, int dft)
    {
        Object obj = this.getProperty(key);
        return (obj != null)? StringTools.parseInt(obj,dft) : dft;
    }

    /**
    *** Gets the boolean property value for the specified key
    *** @param key  The property key
    *** @param dft  The default value returned if the property key is not defined
    *** @return The property boolean value
    **/
    public boolean getBooleanProperty(String key, boolean dft)
    {
        Object obj = this.getProperty(key);
        return (obj != null)? StringTools.parseBoolean(obj.toString(),dft) : dft;
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the exclusive status codes state
    *** @param only  If true, only status codes set by this PrivateLabel will be visible
    **/
    public void setStatusCodeOnly(boolean only)
    {
        this.statusCodeOnly = only;
    }

    /**
    *** Gets the exclusive status codes state
    *** @return True if only status codes set by this PrivateLabel will be visible
    **/
    public boolean getStatusCodeOnly()
    {
        return this.statusCodeOnly && (this.statusCodes != null);
    }

    /**
    *** Adds a customized status code description override
    *** @param code  The StatusCode to add
    **/
    public void addStatusCode(StatusCodes.Code code)
    {
        if (code != null) {
            if (this.statusCodes == null) {
                this.statusCodes = new HashMap<Integer,StatusCodes.Code>();
            }
            this.statusCodes.put(new Integer(code.getCode()), code);
            //Print.logInfo("Added Code: " + code);
        }
    }

    /**
    *** Returns a Map of custom status codes
    *** @return A Map of custom status codes (or null if there are no custom status codes)
    **/
    public Map<Integer,StatusCodes.Code> getCustomStatusCodeMap()
    {
        return this.statusCodes;
    }

    /**
    *** Returns a Map of StatusCodes to their desriptions
    *** @return a Map of StatusCodes to their desriptions
    **/
    public Map<Integer,String> getStatusCodeDescriptionMap()
    {
        Locale locale = this.getLocale();
        Map<Integer,String> descMap = this.getStatusCodeOnly()?
            new OrderedMap<Integer,String>() :
            StatusCodes.GetDescriptionMap(locale);
        Map<Integer,StatusCodes.Code> csc = this.getCustomStatusCodeMap();
        if (csc != null) {
            for (Integer sci : csc.keySet()) {
                descMap.put(sci, csc.get(sci).getDescription(locale));
            }
        }
        return descMap;
    }

    /**
    *** Return specific code (from statusCode)
    *** @param code The status code
    *** @return The StatusCode.Code instance
    **/
    public StatusCodes.Code getStatusCode(Integer code)
    {
        //Print.logInfo("Looking up Code: " + code);
        if (this.statusCodes != null) {
            return this.statusCodes.get(code);
        } else {
            return null;
        }
    }

    /**
    *** Return specific code (from statusCode)
    *** @param code The status code
    *** @return The StatusCode.Code instance
    **/
    public StatusCodes.Code getStatusCode(int code)
    {
        if (this.statusCodes != null) {
            return this.statusCodes.get(new Integer(code));
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the global PointsOfInterest (overridden)
    *** @return The PointsOfInterest list
    **/
    public java.util.List<PoiProvider> getPointsOfInterest()
    {
        return null;
    }

    // ------------------------------------------------------------------------

}
