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
//  2007/03/30  Martin D. Flynn
//     -Initial release
//  2007/06/03  Martin D. Flynn
//     -Added I18N support
//  2007/06/13  Martin D. Flynn
//     -Added support for browsers with disabled cookies
//  2007/06/30  Martin D. Flynn
//     -Added User table view
//  2007/07/27  Martin D. Flynn
//     -Added 'getNavigationTab(...)'
//  2008/08/15  Martin D. Flynn
//     -The 'admin' user [see "User.getAdminUserID()"] is always granted "ALL" access.
//  2008/08/17  Martin D. Flynn
//     -Fix display of View/Edit buttons on creation of first user.
//  2008/09/01  Martin D. Flynn
//     -Added editable field for User first login page.
//     -Added delete confirmation
//  2008/10/16  Martin D. Flynn
//     -Update with new ACL usage
//     -Added support to user Roles.
//  2008/12/01  Martin D. Flynn
//     -Disable password field for current user
//  2009/08/23  Martin D. Flynn
//     -Convert new entered IDs to lowercase
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

public class UserInfo
    extends WebPageAdaptor
    implements Constants
{

    // ------------------------------------------------------------------------

    public  static final String _ACL_ALL                = "all";
    public  static final String _ACL_ACLS               = "acls";
    public  static final String _ACL_GROUPS             = "groups";
    public  static final String _ACL_ROLE               = "role";
    public  static final String _ACL_PASS               = "pass";
    private static final String _ACL_LIST[]             = new String[] { _ACL_ALL, _ACL_ACLS, _ACL_GROUPS, _ACL_ROLE };

    private static final String ACL_DEFAULT             = "default";

    // ------------------------------------------------------------------------

    // password holder/indicator
    private static final String PASSWORD_HOLDER         = "**********";
    private static final char   PASSWORD_INVALID_CHAR   = '*'; // password can't have all '*'

    // ------------------------------------------------------------------------
    // Parameters

    // forms 
    public  static final String FORM_USER_SELECT        = "UserInfoSelect";
    public  static final String FORM_USER_EDIT          = "UserInfoEdit";
    public  static final String FORM_USER_NEW           = "UserInfoNew";

    // commands
    public  static final String COMMAND_INFO_UPDATE     = "update";
    public  static final String COMMAND_INFO_SELECT     = "select";
    public  static final String COMMAND_INFO_NEW        = "new";

    // submit
    public  static final String PARM_SUBMIT_EDIT        = "u_subedit";
    public  static final String PARM_SUBMIT_VIEW        = "u_subview";
    public  static final String PARM_SUBMIT_CHG         = "u_subchg";
    public  static final String PARM_SUBMIT_DEL         = "u_subdel";
    public  static final String PARM_SUBMIT_NEW         = "u_subnew";

    // buttons
    public  static final String PARM_BUTTON_CANCEL      = "u_btncan";
    public  static final String PARM_BUTTON_BACK        = "u_btnbak";

    // parameters
    public  static final String PARM_NEW_NAME           = "u_newname";
    public  static final String PARM_USER_SELECT        = "u_user";
    public  static final String PARM_USER_NAME          = "u_name";
    public  static final String PARM_USER_PASSWORD      = "u_pass";
    public  static final String PARM_USER_ROLE          = "u_role";
    public  static final String PARM_CONTACT_NAME       = "u_contact";
    public  static final String PARM_CONTACT_PHONE      = "u_phone";
    public  static final String PARM_CONTACT_EMAIL      = "u_email";
    public  static final String PARM_TIMEZONE           = "u_tmz";
    public  static final String PARM_ACL_               = "u_acl_";
    public  static final String PARM_USER_ACTIVE        = "u_actv";
    public  static final String PARM_DEVICE_GROUP       = "u_devgrp";
    public  static final String PARM_LOGIN_PAGE         = "u_1stpage";
    public  static final String PARM_PREF_DEVICE        = "u_devid";

    // ------------------------------------------------------------------------

    public enum FirstLogin implements EnumTools.StringLocale, EnumTools.StringValue {
        TOP_MENU        (Constants.PAGE_MENU_TOP           , I18N.getString(UserInfo.class,"UserInfo.firstLogin.topMenu"      ,"Main Menu"      )), // default
        MAP_DEVICE      (Constants.PAGE_MAP_DEVICE         , I18N.getString(UserInfo.class,"UserInfo.firstLogin.mapDevice"    ,"Device Map"     )),
        MAP_FLEET       (Constants.PAGE_MAP_FLEET          , I18N.getString(UserInfo.class,"UserInfo.firstLogin.mapFleet"     ,"Fleet Map"      )),
        REPORT_DETAIL   (Constants.PAGE_MENU_REPORT_DETAIL , I18N.getString(UserInfo.class,"UserInfo.firstLogin.reportDetail" ,"Detail Reports" )),
        REPORT_SUMMARY  (Constants.PAGE_MENU_REPORT_SUMMARY, I18N.getString(UserInfo.class,"UserInfo.firstLogin.reportSummary","Summary Reports"));
        // ---
        private String      pn = "";
        private I18N.Text   aa = null;
        FirstLogin(String p, I18N.Text a)           { pn = p; aa = a; }
        public String  getStringValue()             { return pn; }
        public String  toString(Locale loc)         { return aa.toString(loc); }
        public boolean isDefault()                  { return this.equals(TOP_MENU); }
    };

    public static FirstLogin GetFirstLogin(String pageName)
    {
        return EnumTools.getValueOf(FirstLogin.class, pageName);
    }

    public static FirstLogin[] GetFirstLoginList()
    {
        return FirstLogin.class.getEnumConstants();
    }
    
    private static ComboMap GetFirstLoginListMap(Locale locale)
    {
        ComboMap map = new ComboMap();
        for (FirstLogin f : GetFirstLoginList()) {
            map.add(f.getStringValue(), f.toString(locale));
        }
        return map;
    }

    // ------------------------------------------------------------------------
    // WebPage interface
    
    public UserInfo()
    {
        this.setBaseURI(Track.BASE_URI());
        this.setPageName(PAGE_USER_INFO);
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
        I18N i18n = privLabel.getI18N(UserInfo.class);
        return super._getMenuDescription(reqState,i18n.getString("UserInfo.editMenuDesc","View/Edit User Information"));
    }
   
    public String getMenuHelp(RequestProperties reqState, String parentMenuName)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(UserInfo.class);
        return super._getMenuHelp(reqState,i18n.getString("UserInfo.editMenuHelp","View and Edit User information"));
    }

    // ------------------------------------------------------------------------

    public String getNavigationDescription(RequestProperties reqState)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(UserInfo.class);
        return super._getNavigationDescription(reqState,i18n.getString("UserInfo.navDesc","User Admin"));
    }

    public String getNavigationTab(RequestProperties reqState)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(UserInfo.class);
        return super._getNavigationTab(reqState,i18n.getString("UserInfo.navTab","User Admin"));
    }

    // ------------------------------------------------------------------------
    
    public String[] getChildAclList()
    {
        return _ACL_LIST;
    }

    // ------------------------------------------------------------------------
    
    private boolean isValidPassword(String pwd)
    {
        if (StringTools.isBlank(pwd)) {
            return true; // user is not allowed to log-in
        } else
        if (pwd.equals(PASSWORD_HOLDER)) {
            return false;
        } else {
            for (int i = 0; i < pwd.length(); i++) {
                if (pwd.charAt(i) != PASSWORD_INVALID_CHAR) {
                    return true;
                }
            }
            return false; // all '*'
        }
    }
    
    public void writePage(
        final RequestProperties reqState,
        String pageMsg)
        throws IOException
    {
        final HttpServletRequest request = reqState.getHttpServletRequest();
        final PrivateLabel privLabel = reqState.getPrivateLabel(); // never null
        final I18N    i18n       = privLabel.getI18N(UserInfo.class);
        final Locale  locale     = privLabel.getLocale();
        final Account currAcct   = reqState.getCurrentAccount(); // never null
        final String  currAcctID = reqState.getCurrentAccountID();
        final User    currUser   = reqState.getCurrentUser(); // may be null
        final String  currUserID = reqState.getCurrentUserID();
        final String  pageName   = this.getPageName();
        String m = pageMsg;
        boolean error = false;

        /* argument user-id */
        String userList[] = reqState.getUserList();
        String selUserID = AttributeTools.getRequestString(reqState.getHttpServletRequest(), PARM_USER_SELECT, "");
        if (StringTools.isBlank(selUserID)) {
            if ((userList.length > 0) && (userList[0] != null)) {
                selUserID = userList[0];
            } else {
                selUserID = currUserID;
            }
        }
        final boolean isCurrentUserSelected = selUserID.equals(currUserID);
        final boolean isSelectedAdminUser = User.isAdminUser(selUserID);
        if (userList.length == 0) {
            userList = new String[] { selUserID };
        }

        /* user db */
        User selUser = null;
        try {
            selUser = !StringTools.isBlank(selUserID)? User.getUser(currAcct, selUserID) : null; // may still be null
        } catch (DBException dbe) {
            // ignore
        }

        /* command */
        String  userCmd      = reqState.getCommandName();
        boolean listUsers    = false;
        boolean updateUser   = userCmd.equals(COMMAND_INFO_UPDATE);
        boolean selectUser   = userCmd.equals(COMMAND_INFO_SELECT);
        boolean newUser      = userCmd.equals(COMMAND_INFO_NEW);
        boolean deleteUser   = false;
        boolean editUser     = false;
        boolean viewUser     = false;

        /* submit buttons */
        String  submitEdit   = AttributeTools.getRequestString(request, PARM_SUBMIT_EDIT, "");
        String  submitView   = AttributeTools.getRequestString(request, PARM_SUBMIT_VIEW, "");
        String  submitChange = AttributeTools.getRequestString(request, PARM_SUBMIT_CHG , "");
        String  submitNew    = AttributeTools.getRequestString(request, PARM_SUBMIT_NEW , "");
        String  submitDelete = AttributeTools.getRequestString(request, PARM_SUBMIT_DEL , "");

        /* show role */
        boolean showRole     = (privLabel.getWebPage(Constants.PAGE_ROLE_INFO) != null);
        boolean showPrefDev  = privLabel.getBooleanProperty(PrivateLabel.PROP_UserInfo_showPreferredDeviceID,false);

        /* CACHE_ACL: ACL allow edit/view */
        // see also "CACHE_ACL" below
        AccessLevel aclALL   = privLabel.getAccessLevel(currUser, this.getAclName(_ACL_ALL));
        AccessLevel aclSELF  = privLabel.getAccessLevel(currUser, this.getAclName());
        boolean allowNew     = AclEntry.okAll(aclALL);
        boolean allowDelete  = allowNew;  // 'delete' allowed if 'new' allowed
        boolean allowEditAll = allowNew     || AclEntry.okWrite(aclALL);
        boolean viewAll      = allowEditAll || AclEntry.okRead(aclALL);
        boolean editSelf     = allowEditAll || AclEntry.okWrite(aclSELF);
        boolean viewSelf     = editSelf     || AclEntry.okRead(aclSELF);
        boolean allowEdit    = allowEditAll || editSelf;
        boolean allowView    = allowEdit    || viewAll || viewSelf;

        /* sub-command */
        String newUserID = null;
        if (newUser) {
            if (!allowNew) {
               newUser = false; // not authorized
            } else {
                HttpServletRequest httpReq = reqState.getHttpServletRequest();
                newUserID = AttributeTools.getRequestString(httpReq,PARM_NEW_NAME,"").trim();
                newUserID = newUserID.toLowerCase();
                if (StringTools.isBlank(newUserID)) {
                    m = i18n.getString("UserInfo.enterNewUser","Please enter a new user name.");
                    error = true;
                    newUser = false;
                } else
                if (!WebPageAdaptor.isValidID(reqState, PrivateLabel.PROP_UserInfo_validateNewIDs, newUserID)) {
                    m = i18n.getString("UserInfo.invalidIDChar","ID contains invalid characters");
                    error = true;
                    newUser = false;
                }
            }
        } else
        if (updateUser) {
            //Print.logInfo("Change User ...");
            if (!allowEdit) {
                // not authorized to update users
                updateUser = false;
            } else
            if (!SubmitMatch(submitChange,i18n.getString("UserInfo.change","Change"))) {
                updateUser = false;
            } else
            if (!isCurrentUserSelected && !allowEditAll) {
                updateUser = false;
            }
        } else
        if (selectUser) {
            //Print.logInfo("Select User: " + submit);
            if (SubmitMatch(submitDelete,i18n.getString("UserInfo.delete","Delete"))) {
                if (allowDelete) {
                    if (selUser == null) {
                        m = i18n.getString("UserInfo.pleaseSelectUser","Please select a User");
                        error = true;
                        listUsers = true;
                    } else
                    if (isCurrentUserSelected) {
                        m = i18n.getString("UserInfo.cannotDeleteCurrentUser","Cannot delete current logged-in user");
                        error = true;
                        listUsers = true;
                    } else {
                        deleteUser = true;
                    }
                }
            } else
            if (SubmitMatch(submitEdit,i18n.getString("UserInfo.edit","Edit"))) {
                if (allowEdit) {
                    if (selUser == null) {
                        m = i18n.getString("UserInfo.pleaseSelectUser","Please select a User");
                        error = true;
                        listUsers = true;
                    } else {
                        editUser = allowEditAll || (isCurrentUserSelected && editSelf);
                        viewUser = true;
                    }
                }
            } else
            if (SubmitMatch(submitView,i18n.getString("UserInfo.view","View"))) {
                if (allowView) {
                    if (selUser == null) {
                        m = i18n.getString("UserInfo.pleaseSelectUser","Please select a User");
                        error = true;
                        listUsers = true;
                    } else {
                        viewUser = true;
                    }
                }
            } else {
                listUsers = true;
            }
        } else {
            listUsers = true;
        }

        /* delete user? */
        if (deleteUser) {
            if (selUser == null) {
                m = i18n.getString("UserInfo.pleaseSelectUser","Please select a User");
                error = true;
            } else {
                try {
                    User.Key userKey = (User.Key)selUser.getRecordKey();
                    Print.logWarn("Deleting User: " + userKey);
                    userKey.delete(true); // will also delete dependencies
                    selUserID = "";
                    selUser = null;
                    reqState.setUserList(null);
                    userList = reqState.getUserList();
                    if (!ListTools.isEmpty(userList)) {
                        selUserID = userList[0];
                        try {
                            selUser = !selUserID.equals("")? User.getUser(currAcct, selUserID) : null; // may still be null
                        } catch (DBException dbe) {
                            // ignore
                        }
                    }
                } catch (DBException dbe) {
                    Print.logException("Deleting User", dbe);
                    m = i18n.getString("UserInfo.errorDelete","Internal error deleting User");
                    error = true;
                }
            }
            listUsers = true;
        }

        /* new user? */
        if (newUser) {
            boolean createUserOK = true;
            for (int u = 0; u < userList.length; u++) {
                if (newUserID.equalsIgnoreCase(userList[u])) {
                    m = i18n.getString("UserInfo.alreadyExists","This user already exists");
                    error = true;
                    createUserOK = false;
                    break;
                }
            }
            if (createUserOK) {
                try {
                    User user = User.createNewUser(currAcct, newUserID); // already saved
                    reqState.setUserList(null);
                    userList = reqState.getUserList();
                    selUser = user;
                    selUserID = user.getUserID();
                    m = i18n.getString("UserInfo.createdUser","New user has been created");
                } catch (DBException dbe) {
                    Print.logException("Creating User", dbe);
                    m = i18n.getString("UserInfo.errorCreate","Internal error creating User");
                    error = true;
                }
            }
            listUsers = true;
        }

        /* change/update the user info? */
        if (updateUser) {
            if (selUser == null) {
                m = i18n.getString("UserInfo.noUsers","There are currently no defined users for this account.");
            } else {
                String userActive   = AttributeTools.getRequestString(request, PARM_USER_ACTIVE  , "");
                String userDesc     = AttributeTools.getRequestString(request, PARM_USER_NAME    , "");
                String userPassword = AttributeTools.getRequestString(request, PARM_USER_PASSWORD, "");
                String contactName  = AttributeTools.getRequestString(request, PARM_CONTACT_NAME , "");
                String contactPhone = AttributeTools.getRequestString(request, PARM_CONTACT_PHONE, "");
                String contactEmail = AttributeTools.getRequestString(request, PARM_CONTACT_EMAIL, "");
                String timeZone     = AttributeTools.getRequestString(request, PARM_TIMEZONE     , "");
                String deviceGroup  = AttributeTools.getRequestString(request, PARM_DEVICE_GROUP , "");
                String loginPage    = AttributeTools.getRequestString(request, PARM_LOGIN_PAGE   , "");
                String prefDevice   = AttributeTools.getRequestString(request, PARM_PREF_DEVICE  , "");
                String userRole     = AttributeTools.getRequestString(request, PARM_USER_ROLE    , "");
                String aclKeys[]    = AttributeTools.getMatchingKeys( request, PARM_ACL_);
                listUsers = true;
                // update
                try {
                    boolean saveOK = true;
                    // active
                    if (isCurrentUserSelected) {
                        if (!selUser.getIsActive()) {
                            selUser.setIsActive(true);
                        }
                    } else {
                        boolean userActv = ComboOption.parseYesNoText(locale, userActive, true);
                        if (selUser.getIsActive() != userActv) { 
                            selUser.setIsActive(userActv); 
                        }
                    }
                    // password
                    //WebPage passPage = privLabel.getWebPage(Constants.PAGE_PASSWD);
                    //boolean editPass = (passPage != null)? privLabel.hasWriteAccess(currUser,passPage.getAclName()) : false;
                    if (!isCurrentUserSelected) {
                        if (userPassword.equals(PASSWORD_HOLDER) || StringTools.isBlank(userPassword)) {
                            // password not entered
                        } else
                        if (Account.checkPassword(userPassword,selUser.getPassword())) {
                            // password did not change
                        } else
                        if (this.isValidPassword(userPassword)) {
                            selUser.setPassword(Account.encodePassword(userPassword));
                        } else {
                            m = i18n.getString("UserInfo.pleaseEnterValidPassword","Please enter a valid password");
                            error = true;
                            saveOK = false;
                            editUser = true;
                            listUsers = false;
                        }
                    }
                    // description
                    if (!userDesc.equals("")) {
                        selUser.setDescription(userDesc);
                    }
                    // contact name
                    selUser.setContactName(contactName);
                    // contact phone
                    selUser.setContactPhone(contactPhone);
                    // contact email
                    if (StringTools.isBlank(contactEmail) || EMail.validateAddress(contactEmail)) {
                        selUser.setContactEmail(contactEmail);
                    } else {
                        m = i18n.getString("UserInfo.pleaseEnterEMail","Please enter a valid email address");
                        error = true;
                        saveOK = false;
                        editUser = true;
                        listUsers = false;
                    }
                    // timezone
                    if (!StringTools.isBlank(timeZone)) {
                        selUser.setTimeZone(timeZone);
                    } else {
                        selUser.setTimeZone(DateTime.GMT_TIMEZONE);
                    }
                    // first login page
                    if (!selUser.getFirstLoginPageID().equals(loginPage)) {
                        selUser.setFirstLoginPageID(loginPage);
                    }
                    // preferred device
                    if (showPrefDev && !selUser.getPreferredDeviceID().equals(prefDevice)) {
                        selUser.setPreferredDeviceID(prefDevice);
                    }
                    // authorized device group
                    if (privLabel.hasWriteAccess(currUser, this.getAclName(_ACL_GROUPS))) {
                        selUser.setDeviceGroups(new String[] { deviceGroup });
                    }
                    // ACL Role
                    if (showRole && privLabel.hasWriteAccess(currUser, this.getAclName(_ACL_ROLE))) {
                        if (StringTools.isBlank(userRole) || userRole.equals(i18n.getString("UserInfo.none","None"))) {
                            if (!selUser.getRoleID().equals("")) {
                                //Print.logInfo("Setting user role to blank");
                                selUser.setRoleID("");
                            }
                        } else {
                            if (!selUser.getRoleID().equals(userRole)) {
                                //Print.logInfo("Setting user role to %s", userRole);
                                selUser.setRoleID(userRole);
                            }
                        }
                    }
                    // ACLs
                    if (!ListTools.isEmpty(aclKeys)) {
                        for (int i = 0; i < aclKeys.length; i++) {
                            String aclID  = aclKeys[i].substring(PARM_ACL_.length());
                            String aclVal = AttributeTools.getRequestString(request, aclKeys[i], "");
                            if (aclVal.equalsIgnoreCase(ACL_DEFAULT) ||
                                aclVal.equalsIgnoreCase(i18n.getString("UserInfo.default","Default"))) {
                                try {
                                    UserAcl.deleteAccessLevel(selUser, aclID);
                                } catch (DBException dbe) {
                                    Print.logException("Deleting UserAcl: "+ selUser.getAccountID() + "/" + selUser.getUserID() + "/" + aclID, dbe);
                                    m = i18n.getString("UserInfo.errorDeleteAcl","Internal error deleting UserAcl");
                                    error = true;
                                }
                            } else {
                                AccessLevel selLevel = EnumTools.getValueOf(AccessLevel.class, aclVal, locale);
                                AccessLevel dftLevel = privLabel.getDefaultAccessLevel(aclID);
                                //Print.logInfo("Found ACL: " + aclID + " ==> " + level);
                                try {
                                    if (isSelectedAdminUser) {
                                        // The UserAcl table is not consulted for 'admin' user, delete if present
                                        UserAcl.deleteAccessLevel(selUser, aclID);
                                    //} else
                                    //if (dftLevel.equals(selLevel)) {
                                    //    // matches the default, no reason for this ACL to be in the UserAcl table, delete if present
                                    //    UserAcl.deleteAccessLevel(selUser, aclID);
                                    } else {
                                        // differs from the UserAcl table, set ACL
                                        UserAcl.setAccessLevel(selUser, aclID, selLevel);
                                    }
                                } catch (DBException dbe) {
                                    Print.logException("Updating UserAcl: "+ selUser.getAccountID() + "/" + selUser.getUserID() + "/" + aclID, dbe);
                                    m = i18n.getString("UserInfo.errorUpdate","Internal error updating User");
                                    error = true;
                                }
                            }
                        }
                        // CACHE_ACL: update cached ACL
                        aclALL       = privLabel.getAccessLevel(currUser, this.getAclName(_ACL_ALL));
                        aclSELF      = privLabel.getAccessLevel(currUser, this.getAclName());
                        allowNew     = AclEntry.okAll(aclALL);
                        allowDelete  = allowNew;  // 'delete' allowed if 'new' allowed
                        allowEditAll = allowNew     || AclEntry.okWrite(aclALL);
                        viewAll      = allowEditAll || AclEntry.okRead(aclALL);
                        editSelf     = allowEditAll || AclEntry.okWrite(aclSELF);
                        viewSelf     = editSelf     || AclEntry.okRead(aclSELF);
                        allowEdit    = allowEditAll || editSelf;
                        allowView    = allowEdit    || viewAll || viewSelf;
                    }
                    // save
                    if (saveOK) {
                        selUser.save();
                        //Track.writeMessageResponse(reqState, 
                        //    i18n.getString("UserInfo.userUpdated","User information updated"));
                        m = i18n.getString("UserInfo.userUpdated","User information updated");
                    } else {
                        // should stay on this page
                        editUser = true;
                        listUsers = false;
                    }
                } catch (Throwable t) {
                    //Track.writeErrorResponse(reqState, 
                    //    i18n.getString("UserInfo.errorUpdate","Internal error updating User"));
                    Print.logException("Updating User", t);
                    m = i18n.getString("UserInfo.errorUpdate","Internal error updating User");
                    error = true;
                    //return;
                }
            }
        }

        /* Style */
        HTMLOutput HTML_CSS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                String cssDir = UserInfo.this.getCssDirectory();
                WebPageAdaptor.writeCssLink(out, reqState, "UserInfo.css", cssDir);
            }
        };

        /* JavaScript */
        HTMLOutput HTML_JS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                MenuBar.writeJavaScript(out, pageName, reqState);
                JavaScriptTools.writeJSInclude(out, JavaScriptTools.qualifyJSFileRef(SORTTABLE_JS));
            }
        };

        /* Content */
        final boolean _showPrefDev  = showPrefDev;
        final String  _selUserID    = selUserID;
        final User    _selUser      = selUser;
        final String  _userList[]   = userList;
        final boolean _allowEdit    = allowEdit;
        final boolean _allowEditAll = allowEditAll;
        final boolean _allowView    = allowView;
        final boolean _allowNew     = allowNew;
        final boolean _allowDelete  = allowDelete;
        final boolean _editUser     = _allowEdit && editUser;
        final boolean _viewUser     = _editUser || viewUser;
        final boolean _listUsers    = listUsers || (!_editUser && !_viewUser);
        final ComboMap _tzMap       = privLabel.getTimeZoneComboMap();
        AccessLevel   aclROLE       = showRole? privLabel.getAccessLevel(currUser, UserInfo.this.getAclName(_ACL_ROLE)) : AccessLevel.NONE;
        final boolean _editRole     = _editUser && AclEntry.okWrite(aclROLE);
        final boolean _viewRole     = _editRole || AclEntry.okRead( aclROLE);
        HTMLOutput HTML_CONTENT = new HTMLOutput(CommonServlet.CSS_CONTENT_FRAME, m) {
            public void write(PrintWriter out) throws IOException {
                String pageName = UserInfo.this.getPageName();

                // frame header
              //String menuURL    = EncodeMakeURL(reqState,Track.BASE_URI(),PAGE_MENU_TOP);
                String menuURL    = privLabel.getWebPageURL(reqState, PAGE_MENU_TOP);
                String editURL    = UserInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                String selectURL  = UserInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                String newURL     = UserInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                String frameTitle = _allowEdit? 
                    i18n.getString("UserInfo.viewEditUser","View/Edit User Information") : 
                    i18n.getString("UserInfo.viewUser","View User Information");
              //String selectUserJS = "javascript:dinfoSelectUser()";
                out.write("<span class='"+CommonServlet.CSS_MENU_TITLE+"'>"+frameTitle+"</span><br/>\n");
                out.write("<hr>\n");

                // user selection table (Select, User ID, User Name)
                if (_listUsers) {
                    
                    // user selection table (Select, User ID, User Name)
                    out.write("<h1 class='"+CommonServlet.CSS_ADMIN_SELECT_TITLE+"'>"+i18n.getString("UserInfo.selectUser","Select a User")+":</h1>\n");
                    out.write("<div style='margin-left:25px;'>\n");
                    out.write("<form name='"+FORM_USER_SELECT+"' method='post' action='"+selectURL+"' target='_top'>");
                    out.write("<input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_SELECT+"'/>");
                    out.write("<table class='"+CommonServlet.CSS_ADMIN_SELECT_TABLE+"' cellspacing=0 cellpadding=0 border=0>\n");
                    out.write(" <thead>\n");
                    out.write("  <tr class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_ROW+"'>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL_SEL+"' nowrap>"+i18n.getString("UserInfo.select","Select")+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+i18n.getString("UserInfo.userID","User ID")+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+i18n.getString("UserInfo.userName","User Name")+"</th>\n");
                    if (_viewRole) {
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+i18n.getString("UserInfo.role","Role")+"</th>\n");
                    }
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+i18n.getString("UserInfo.contactName","Contact Name")+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+i18n.getString("UserInfo.contactEmail","Contact Email")+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+i18n.getString("UserInfo.timeZone","Time Zone")+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"' nowrap>"+i18n.getString("UserInfo.active","Active")+"</th>\n");
                    out.write("  </tr>\n");
                    out.write(" </thead>\n");
                    out.write(" <tbody>\n");
                    for (int u = 0; u < _userList.length; u++) {
                        if ((u & 1) == 0) {
                            out.write("  <tr class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_ROW_ODD+"'>\n");
                        } else {
                            out.write("  <tr class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_ROW_EVEN+"'>\n");
                        }
                        try {
                            User usr = User.getUser(currAcct, _userList[u]);
                            if (usr != null) {
                                String uid          = usr.getUserID();
                                String userID       = FilterText(uid);
                                String userDesc     = FilterText(usr.getDescription());
                                String contactName  = FilterText(usr.getContactName());
                                String contactEmail = FilterText(usr.getContactEmail());
                                String timeZone     = FilterText(usr.getTimeZone());
                                String active       = FilterText(ComboOption.getYesNoText(locale,usr.isActive()));
                                String checked      = _selUserID.equals(uid)? " checked" : "";
                                String viewStyle    = (_allowEdit && !_allowEditAll)? ((currUserID.equals(uid))?"background-color:#FFFFFF;":"background-color:#E5E5E5;") : ""; 
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL_SEL+"' "+SORTTABLE_SORTKEY+"='"+u+"' style='"+viewStyle+"'><input type='radio' name='"+PARM_USER_SELECT+"' id='"+userID+"' value='"+userID+"' "+checked+"></td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap><label for='"+userID+"'>"+userID+"</label></td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+userDesc+"</td>\n");
                                if (_viewRole) {
                                String userRoleID   = FilterText(Role.getDisplayRoleID(usr.getRoleID()));
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+userRoleID+"</td>\n");
                                }
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+contactName+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+contactEmail+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+timeZone+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+active+"</td>\n");
                            }
                        } catch (DBException dbe) {
                            // 
                        }
                        out.write("  </tr>\n");
                    }
                    out.write(" </tbody>\n");
                    out.write("</table>\n");
                    out.write("<table cellpadding='0' cellspacing='0' border='0' style='width:95%; margin-top:5px; margin-left:5px; margin-bottom:5px;'>\n");
                    out.write("<tr>\n");
                    if (_allowView  ) { 
                        out.write("<td style='padding-left:5px;'>");
                        out.write("<input type='submit' name='"+PARM_SUBMIT_VIEW+"' value='"+i18n.getString("UserInfo.view","View")+"'>");
                        out.write("</td>\n"); 
                    }
                    if (_allowEdit  ) { 
                        out.write("<td style='padding-left:5px;'>");
                        out.write("<input type='submit' name='"+PARM_SUBMIT_EDIT+"' value='"+i18n.getString("UserInfo.edit","Edit")+"'>");
                        out.write("</td>\n"); 
                    }
                    out.write("<td style='width:100%; text-align:right; padding-right:10px;'>");
                    if (_allowDelete) {
                        out.write("<input type='submit' name='"+PARM_SUBMIT_DEL+"' value='"+i18n.getString("UserInfo.delete","Delete")+"' "+Onclick_ConfirmDelete(locale)+">");
                    } else {
                        out.write("&nbsp;"); 
                    }
                    out.write("</td>\n"); 
                    out.write("</tr>\n");
                    out.write("</table>\n");
                    out.write("</form>\n");
                    out.write("</div>\n");
                    out.write("<hr>\n");
                    
                    /* new user */
                    if (_allowNew) {
                    out.write("<h1 class='"+CommonServlet.CSS_ADMIN_SELECT_TITLE+"'>"+i18n.getString("UserInfo.createNewUser","Create a new user")+":</h1>\n");
                    out.write("<div style='margin-top:5px; margin-left:5px; margin-bottom:5px;'>\n");
                    out.write("<form name='"+FORM_USER_NEW+"' method='post' action='"+newURL+"' target='_top'>");
                    out.write(" <input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_NEW+"'/>");
                    out.write(i18n.getString("UserInfo.userID","User ID")+": <input type='text' name='"+PARM_NEW_NAME+"' value='' size='32' maxlength='32'><br>\n");
                    out.write(" <input type='submit' name='"+PARM_SUBMIT_NEW+"' value='"+i18n.getString("UserInfo.new","New")+"' style='margin-top:5px; margin-left:10px;'>\n");
                    out.write("</form>\n");
                    out.write("</div>\n");
                    out.write("<hr>\n");
                    }

                } else {
                    // user view/edit form

                    /* start of form */
                    out.write("<form name='"+FORM_USER_EDIT+"' method='post' action='"+editURL+"' target='_top'>\n");
                    out.write("  <input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_UPDATE+"'/>\n");

                    /* password */
                    //AccessLevel aclPASS = privLabel.getAccessLevel(currUser, UserInfo.this.getAclName(_ACL_PASS));
                    boolean _editPass = _editUser && !isCurrentUserSelected; // && AclEntry.okWrite(aclPASS);
                    boolean _viewPass = _editPass || true; // _editPass || AclEntry.okRead(aclPASS);

                    /* device group(s) */
                    AccessLevel aclGROUP = privLabel.getAccessLevel(currUser, UserInfo.this.getAclName(_ACL_GROUPS));
                    boolean _editGroup  = _editUser  && AclEntry.okWrite(aclGROUP);
                    boolean _viewGroup  = _editGroup || AclEntry.okRead( aclGROUP);
                    ComboMap _grpMap    = null;
                    String devGroup     = DeviceGroup.DEVICE_GROUP_ALL;
                    if (_viewGroup) {
                        java.util.List<String> devGroups = null;
                        try {
                            devGroups = (_selUser!=null)? _selUser.getDeviceGroups(true) : null;
                        } catch (DBException dbe) {
                            devGroups = null;
                        }
                        devGroup = !ListTools.isEmpty(devGroups)? devGroups.get(0) : DeviceGroup.DEVICE_GROUP_ALL;
                        if (_editGroup) {
                            //OrderedSet<String> grpList = reqState.getDeviceGroupList(true);
                            try {
                                OrderedSet<String> grpList = DeviceGroup.getDeviceGroupsForAccount(currAcctID,true);
                                if (!grpList.contains(devGroup)) {
                                    Print.logWarn("Obsolete device group: " + devGroup);
                                    grpList.add(devGroup);
                                }
                                _grpMap = new ComboMap(grpList);
                            } catch (DBException dbe) {
                                Print.logException("Getting DeviceGroups for Account", dbe);
                                _grpMap = new ComboMap(devGroup);
                            }
                        } else {
                            _grpMap = new ComboMap(devGroup);
                        }
                    }
                    String grpTitles[]  = reqState.getDeviceGroupTitles();
                    String devTitles[]  = reqState.getDeviceTitles();

                    /* first login page */
                    ComboMap loginList = UserInfo.GetFirstLoginListMap(locale);
                    String loginPage = (_selUser != null)? _selUser.getFirstLoginPageID() : Constants.PAGE_MENU_TOP;

                    /* roles */
                    AccessLevel aclACLS = privLabel.getAccessLevel(currUser, UserInfo.this.getAclName(_ACL_ACLS));
                    String textNone     = i18n.getString("UserInfo.none","None");
                    String userRoleID   = (_selUser != null)? _selUser.getRoleID() : "";
                    ComboMap roleMap    = null;
                    boolean editAcls    = _editUser && !isSelectedAdminUser && AclEntry.okWrite(aclACLS);
                    boolean viewAcls    = editAcls || AclEntry.okRead(aclACLS);
                    if (editAcls) {
                        try {
                            String roleIDs[] = Role.getRolesForAccount(currAcct.getAccountID(),true);
                            roleMap = new ComboMap();
                            roleMap.put(textNone,textNone);
                            for (int i = 0; i < roleIDs.length; i++) {
                                roleMap.add(roleIDs[i], Role.getDisplayRoleID(roleIDs[i]));
                            }
                        } catch (DBException dbe) {
                            roleMap = new ComboMap(textNone);
                        }
                        if (StringTools.isBlank(userRoleID) || !ListTools.containsKey(roleMap,userRoleID)) {
                            userRoleID = textNone;
                        }
                    } else {
                        if (StringTools.isBlank(userRoleID)) {
                            userRoleID = textNone;
                        }
                    }

                    /* password */
                    String password = PASSWORD_HOLDER;
                    boolean showPass = privLabel.getBooleanProperty(PrivateLabel.PROP_UserInfo_showPassword,false);
                    if (showPass && (_selUser != null)) {
                        password = Account.decodePassword(_selUser.getPassword());
                    }

                    /* User fields */
                    ComboOption userActive = ComboOption.getYesNoOption(locale, ((_selUser != null) && _selUser.isActive()));
                    out.println("<table class='"+CommonServlet.CSS_ADMIN_VIEW_TABLE+"' cellspacing='0' callpadding='0' border='0'>");
                  //out.println(FormRow_ComboBox (PARM_USER_SELECT   , !isCurrentUserSelected, i18n.getString("UserInfo.userID","User ID")+":", _selUserID, _userList, selectUserJS, -1));
                    out.println(FormRow_TextField(PARM_USER_SELECT   , false     , i18n.getString("UserInfo.userID","User ID")+":"            , _selUserID, 40, 40));
                    out.println(FormRow_ComboBox (PARM_USER_ACTIVE   , _editUser && !isCurrentUserSelected, i18n.getString("UserInfo.active","Active")+":", userActive, ComboMap.getYesNoMap(locale), "", -1));
                    out.println(FormRow_TextField(PARM_USER_NAME     , _editUser , i18n.getString("UserInfo.userName","User Name")+":"        , (_selUser!=null)?_selUser.getDescription() :"", 40, 40));
                    if (_viewPass) {
                        out.println(FormRow_TextField(PARM_USER_PASSWORD , _editPass , i18n.getString("UserInfo.password","Password")+":"         , password, 20, 20));
                    }
                    out.println(FormRow_TextField(PARM_CONTACT_NAME  , _editUser , i18n.getString("UserInfo.contactName","Contact Name")+":"  , (_selUser!=null)?_selUser.getContactName() :"", 50, 60));
                    out.println(FormRow_TextField(PARM_CONTACT_PHONE , _editUser , i18n.getString("UserInfo.contactPhone","Contact Phone")+":", (_selUser!=null)?_selUser.getContactPhone():"", 20, 20));
                    out.println(FormRow_TextField(PARM_CONTACT_EMAIL , _editUser , i18n.getString("UserInfo.contactEmail","Contact Email")+":", (_selUser!=null)?_selUser.getContactEmail():"", 60, 100));
                    out.println(FormRow_ComboBox (PARM_TIMEZONE      , _editUser , i18n.getString("UserInfo.timeZone","Time Zone")+":"        , (_selUser!=null)?_selUser.getTimeZone()    :"", _tzMap, null, 24));
                    if (_viewGroup) {
                        out.println(FormRow_ComboBox (PARM_DEVICE_GROUP  , _editGroup, i18n.getString("UserInfo.authDeviceGroup","Authorized {0}",grpTitles)+":", devGroup, _grpMap, "", 20));
                    }
                    out.println(FormRow_ComboBox (PARM_LOGIN_PAGE    , _editUser , i18n.getString("UserInfo.firstLoginPage","First Login Page")+":" , loginPage, loginList, "", 16));
                    if (_showPrefDev) {
                        String selDevID = (_selUser != null)? _selUser.getPreferredDeviceID() : "";
                        ComboMap devMap = new ComboMap(reqState.createDeviceDescriptionMap(true/*includeID*/)); 
                        devMap.insert("", i18n.getString("UserInfo.noDevice","None",devTitles)); // "No {0}"
                      //OrderedSet<String> devList = new OrderedSet<String>();
                      //devList.add(""); // none
                      //devList.addAll(reqState.getDeviceList());
                      //String devArry[] = (String[])devList.toArray(new String[devList.size()]);
                      //out.println(FormRow_TextField(PARM_PREF_DEVICE , _editUser, i18n.getString("UserInfo.preferredDevice","Preferred {0} ID",devTitles)+":", selDevID, 32, 32));
                        out.println(FormRow_ComboBox (PARM_PREF_DEVICE , _editUser, i18n.getString("UserInfo.preferredDevice","Preferred {0} ID",devTitles)+":", selDevID, devMap, "", -1));
                    }
                    if (_viewRole) {
                        out.println(FormRow_ComboBox (PARM_USER_ROLE , _editRole , i18n.getString("UserInfo.defaultRole","Default ACL Role")+":", userRoleID, roleMap, null, 20));
                    }
                    out.println("</table>");
                    //out.write("<hr>\n");

                    /* ACL entries */
                    if (viewAcls) {
                        out.write("<span style='margin-left: 4px; margin-top: 8px; font-weight: bold;'>");
                        out.write(i18n.getString("UserInfo.userAccessControl","User Access Control") + ":</span>\n");
                        out.write("<div class='userAclViewDiv'>\n");
                        out.write("<table>\n");
                        AclEntry aclEntries[] = privLabel.getAllAclEntries();
                        String aclLevels[] = EnumTools.getValueNames(AccessLevel.class, locale);
                        Role userRole = (_selUser != null)? _selUser.getRole() : null;
                        for (int a = 0; a < aclEntries.length; a++) {
                            AclEntry acl = aclEntries[a];
                            if (!acl.isHidden()) {
                                String      aclName  = acl.getName();
                                String      argKey   = PARM_ACL_ + aclName;
                                AccessLevel valAcc[] = acl.getAccessLevelValues();  // is not null
                                AccessLevel maxAcc   = acl.getMaximumAccessLevel(); // is not null
                                AccessLevel dftAcc   = User.isAdminUser(_selUser)? maxAcc : privLabel.getAccessLevel(userRole,aclName); // is not null
                                String      desc     = acl.getDescription(locale);
                                ComboMap    aclOpt   = privLabel.getEnumComboMap(AccessLevel.class, valAcc);
                                aclOpt.insert(ACL_DEFAULT, i18n.getString("UserInfo.default","Default"));
                                AccessLevel usrAcc   = (!isSelectedAdminUser && (_selUser != null))? UserAcl.getAccessLevel(_selUser,aclName,null) : null;
                                if ((usrAcc != null) && !ListTools.contains(valAcc,usrAcc)) { usrAcc = maxAcc; } // to prevent selecting non-existant levels
                                if (editAcls) {
                                    ComboOption accSel = (usrAcc != null)?
                                        privLabel.getEnumComboOption(usrAcc) :
                                        new ComboOption(ACL_DEFAULT, i18n.getString("UserInfo.default","Default"));
                                    String  dftHtml  = i18n.getString("UserInfo.defaultIsAcl","[Default is ''{0}'']","<b>"+dftAcc.toString(locale)+"</b>");
                                    out.write(FormRow_ComboBox(argKey, true , desc+":", accSel, aclOpt, "", 18, dftHtml) + "\n");
                                } else {
                                    ComboOption accSel = (usrAcc != null)? 
                                        privLabel.getEnumComboOption(usrAcc) :
                                        privLabel.getEnumComboOption(dftAcc);
                                    out.write(FormRow_ComboBox(argKey, false, desc+":", accSel, aclOpt, "", 18, null) + "\n");
                                }
                            }
                        }
                        out.write("</table>\n");
                        out.write("</div>\n");
                    }

                    /* end of form */
                    out.write("<hr style='margin-bottom:5px;'>\n");
                    out.write("<span style='padding-left:10px'>&nbsp;</span>\n");
                    if (_editUser) {
                        out.write("<input type='submit' name='"+PARM_SUBMIT_CHG+"' value='"+i18n.getString("UserInfo.change","Change")+"'>\n");
                        out.write("<span style='padding-left:10px'>&nbsp;</span>\n");
                        out.write("<input type='button' name='"+PARM_BUTTON_CANCEL+"' value='"+i18n.getString("UserInfo.cancel","Cancel")+"' onclick=\"javascript:openURL('"+editURL+"','_top');\">\n");
                    } else {
                        out.write("<input type='button' name='"+PARM_BUTTON_BACK+"' value='"+i18n.getString("UserInfo.back","Back")+"' onclick=\"javascript:openURL('"+editURL+"','_top');\">\n");
                    }
                    out.write("</form>\n");

                }

            }
        };

        /* write frame */
        String onload = error? JS_alert(true,m) : null;
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
