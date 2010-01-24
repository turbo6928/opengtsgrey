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
// ----------------------------------------------------------------------------
package org.opengts.war.track;

import java.util.*;

public interface Constants
{

    // ------------------------------------------------------------------------
    // Page form ids

    public  static final String FORM_MONTH_CHG              = "MonthChgForm";
    public  static final String FORM_COMMAND                = "CommandForm";
    public  static final String FORM_COMMAND_CSV            = "CommandFormCSV";

    // ------------------------------------------------------------------------
    // URI base address

    public  static final String DEFAULT_BASE_URI            = "." + "/Track";

    // ------------------------------------------------------------------------
    // Query string parameters (argument keys)
    // (Do not use '.' as an arugment-tree level separator!)
    
  //public  static final String PARM_PAGE                   = CommonServlet.PARM_PAGE;
  //public  static final String PARM_COMMAND                = CommonServlet.PARM_COMMAND;
  //public  static final String PARM_ARGUMENT               = CommonServlet.PARM_ARGUMENT;

    public  static final String PARM_REQSTATE               = "$REQSTATE";

    public  static final String PARM_USEREMAIL              = "userEmail";

    public  static final String PARM_ACCOUNT                = "account";
    public  static final String PARM_USER                   = "user";
    public  static final String PARM_PASSWORD               = "password";
    public  static final String PARM_ENCPASS                = "encpass";
    
    public  static final String PARM_REGION                 = "region";

    public  static final String PARM_ACCOUNT_A[]            = new String[] { PARM_ACCOUNT , "act" };
    public  static final String PARM_USER_A[]               = new String[] { PARM_USER    , "usr" };
    public  static final String PARM_PASSWORD_A[]           = new String[] { PARM_PASSWORD, "pwd" };

    public  static final String PARM_DEVICE                 = "device";
    public  static final String PARM_GROUP                  = "group";
    public  static final String PARM_LIMIT                  = "limit";
    public  static final String PARM_LIMIT_TYPE             = "limType";
    
    public  static final String PARM_DEVICE_COMMAND         = "devcmd";

    // ------------------------------------------------------------------------
    // Page definitions [PARM_PAGE argument values]

    public  static final String PAGE_LOGIN                  = "login";                  // login page
    public  static final String PAGE_OFFLINE                = "offline";                // offline page

    public  static final String PAGE_MENU_TOP               = "menu.top";               // Top menu

    public  static final String PAGE_ACCOUNT_NEW            = "acct.new";               // new account
    public  static final String PAGE_ACCOUNT_INFO           = "acct.info";              // Account information

    public  static final String PAGE_USER_INFO              = "user.info";              // User information

    public  static final String PAGE_ROLE_INFO              = "role.info";              // Role information

    public  static final String PAGE_CODE_INFO              = "code.info";              // StatusCode information

    public  static final String PAGE_DEVICE_INFO            = "dev.info";               // Device information
    public  static final String PAGE_DEVICE_PROPS           = "dev.props";              // Device properties

    public  static final String PAGE_GROUP_INFO             = "group.info";             // DeviceGroup information
    
    public  static final String PAGE_DEVICE_ALERTS          = "dev.alerts";             // Device alerts

    public  static final String PAGE_MENU_REPORT            = "menu.report";            // Report menu
    public  static final String PAGE_MENU_REPORT_DETAIL     = "menu.report.detail";     // Device detail Report menu
    public  static final String PAGE_MENU_REPORT_SUMMARY    = "menu.report.summary";    // Device summary Report menu
    public  static final String PAGE_MENU_REPORT_PERFORM    = "menu.report.perform";    // Driver performance Report menu
    public  static final String PAGE_MENU_REPORT_IFTA       = "menu.report.ifta";       // IFTA Report menu
    public  static final String PAGE_MENU_REPORT_SYSADMIN   = "menu.report.sysadmin";   // SysAdmin Report menu

    public  static final String PAGE_REPORT_SHOW            = "report.show";            // Report display

    public  static final String PAGE_J1587_SHOW             = "j1587.show";             // J1587 description display

    public  static final String PAGE_PASSWD                 = "passwd";                 // Change password
    public  static final String PAGE_PASSWD_EMAIL           = "passwd.email";           // Forgot password

    public  static final String PAGE_MAP_DEVICE             = "map.device";             // GPS map tracking
    public  static final String PAGE_MAP_FLEET              = "map.fleet";              // GPS map tracking

    public  static final String PAGE_ZONE_INFO              = "zone.info";              // Geozone information

    public  static final String PAGE_SYSADMIN_INFO          = "sysAdmin.info";          // System Administration Information
    public  static final String PAGE_SYSADMIN_ACCOUNTS      = "sysAdmin.accounts";      // System Administration Account
    public  static final String PAGE_SYSADMIN_DEVICES       = "sysAdmin.devices";       // System Administration Devices

    public  static final String PAGE_RULE_INFO              = "rule.info";              // Rule info

    // ------------------------------------------------------------------------
    // Global command definitions [may be page specific]

    public  static final String COMMAND_LOGOUT              = "logout";                 // arg=YYYY/MM

    // ------------------------------------------------------------------------

}
