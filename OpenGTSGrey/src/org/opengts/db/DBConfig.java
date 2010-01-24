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
//  2006/03/26  Martin D. Flynn
//     -Initial release
//  2006/04/09  Martin D. Flynn
//     -Integrated 'DBException'
//  2006/04/23  Martin D. Flynn
//     -Integrated logging changes made to Print
//  2007/01/25  Martin D. Flynn
//     -Moved to "OpenGTS"
//  2007/05/25  Martin D. Flynn
//     -Added already-initialized check to "initDBFactories()"
//     -Check for 'NoClassDefFoundError' when creating optional DBFactories.
//  2007/06/30  Martin D. Flynn
//     -Added optional "-account" argument for use by "-dump=<table>" command.
//  2007/09/16  Martin D. Flynn
//     -Changed logging level on "Optional DBFactory" warnings to debug level.
//     -Added BasicPrivateLabel initialization in 'init' method
//  2007/11/28  Martin D. Flynn
//     -Added factory entry for table "org.opengts.db.tables.StatusCode"
//     -Added runtime property "startup.initializerClass" to provide custom 
//      statup initialization.
//  2008/02/27  Martin D. Flynn
//     -Added TRACK_JAVASCRIPT_DIR, TRACK_JS_MENUBAR, TRACK_JS_UTILS property keys
//  2008/05/14  Martin D. Flynn
//     -Added TRACK_ENABLE_COOKIES, DB_TRANSPORT_ENABLE_QUERY, DB_UNIQUE_ENABLE_QUERY 
//      properties
//     -Added factory entry for table "org.opengts.db.tables.Transport"
//     -Added factory entry for table "org.opengts.db.tables.UniqueXID"
//  2008/06/20  Martin D. Flynn
//     -Added 'DBInitialization' interface for startup initialization support.
//     -Moved custom DBFactory initialization to optional StartupInit.
//     -Added command-line 'Usage' display.
//  2008/07/08  Martin D. Flynn
//     -Removed TRACK_JS_MENUBAR, TRACK_JS_UTILS property keys.
//  2008/10/16  Martin D. Flynn
//     -Added lookup for default device authorization
//  2008/12/01  Martin D. Flynn
//     -Override DBAdmin '-schema' command (calling 'DBAdmin.printTableSchema'
//      directly, and include header.
//  2009/01/28  Martin D. Flynn
//     -Added TRACK_OFFLINE_FILE
//     -Renamed 'warInit' to 'servletInit' and separated from 'cmdLineInit'.
//     -Renamed 'DB_TRANSPORT_ENABLE_QUERY' to 'TRANSPORT_QUERY_ENABLED'
//     -Renamed 'DB_UNIQUE_ENABLE_QUERY' to 'UNIQUEXID_QUERY_ENABLED'
//  2009/02/20  Martin D. Flynn
//     -Renamed 'track.enableCookies' to 'track.requireCookies'
//  2009/12/16  Martin D. Flynn
//     -Added method for GTS_HOME validation check [check_GTS_HOME()]
// ----------------------------------------------------------------------------
package org.opengts.db;

import java.lang.*;
import java.util.*;
import java.math.*;
import java.io.*;
import java.sql.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.dbtools.DBAdmin.DBAdminExec;

import org.opengts.Version;
import org.opengts.StartupInit;
import org.opengts.dbtypes.*;
import org.opengts.db.tables.*;

public class DBConfig
{

    // ------------------------------------------------------------------------
    // default SQL database name

    private static final String DEFAULT_DB_NAME                 = "gts";

    // ------------------------------------------------------------------------
    // Runtime referenced package names

    private static final String PACKAGE_OPENGTS_                = "org.opengts.";

    private static final String PACKAGE_TABLES_                 = PACKAGE_OPENGTS_ + "db.tables.";

    private static final String PACKAGE_DMTP_                   = PACKAGE_OPENGTS_ + "db.dmtp.";

    public  static final String PACKAGE_OPT_                    = PACKAGE_OPENGTS_ + "opt.";
    public  static final String PACKAGE_OPT_UTIL_               = PACKAGE_OPT_     + "util.";
    private static final String PACKAGE_OPT_DB                  = PACKAGE_OPT_     + "db";
    public  static final String PACKAGE_OPT_DB_                 = PACKAGE_OPT_DB   + ".";

    public  static final String PACKAGE_EXTRA_                  = PACKAGE_OPENGTS_ + "extra.";
    private static final String PACKAGE_EXTRA_TABLES            = PACKAGE_EXTRA_   + "tables";
    public  static final String PACKAGE_EXTRA_TABLES_           = PACKAGE_EXTRA_   + "tables.";

    public  static final String PACKAGE_RULE_                   = PACKAGE_OPENGTS_ + "rule.";
    public  static final String PACKAGE_RULE_UTIL_              = PACKAGE_RULE_    + "util.";
    private static final String PACKAGE_RULE_TABLES             = PACKAGE_RULE_    + "tables";
    private static final String PACKAGE_RULE_TABLES_            = PACKAGE_RULE_    + "tables.";

    public  static final String PACKAGE_BCROSS_                 = PACKAGE_OPENGTS_ + "bcross.";
    private static final String PACKAGE_BCROSS_TABLES_          = PACKAGE_BCROSS_  + "tables.";

    public  static final String PACKAGE_WAR_                    = PACKAGE_OPENGTS_ + "war.";
    public  static final String PACKAGE_WAR_TRACK_              = PACKAGE_WAR_     + "track.";

    // ------------------------------------------------------------------------
    // Schema header

    /* schema header */
    private static final String SCHEMA_HEADER[] = new String[] {
        "This document contains database schema information for the tables defined within the OpenGTS " +
            "system.  Optional tables (if any) will be indicated by the term \"[optional]\" next to the " +
            "table name.",
        "",
        "Additional information may be obtained by examining the source module for the specified class.",
        "",
        "The schema listing below should match the installed configuration, however, there may still be " +
            "minor differences depending on the specific version installed, or changes that have been made " +
            "to the configuration.  The current schema configuration can be generated from the actual " +
            "database configuration by executing the following command: ",
        "(executed from within the OpenGTS directory)",
        "",
        "   bin/dbAdmin.pl -schema",
        "",
        "Or, on Windows:",
        "",
        "   bin\\dbConfig.bat -schema",
    };

    // ------------------------------------------------------------------------
    // Version

    public static boolean hasExtraPackage()
    {
        // TODO: optimize
        return (Package.getPackage(PACKAGE_EXTRA_TABLES) != null);
    }

    public static boolean hasRulePackage()
    {
        // TODO: optimize
        return (Package.getPackage(PACKAGE_RULE_TABLES) != null);
    }

    public static boolean hasOptPackage()
    {
        // TODO: optimize
        return (Package.getPackage(PACKAGE_OPT_DB) != null);
    }

    public static String getVersion() 
    {
        return org.opengts.Version.getVersion(); 
    }

    // ------------------------------------------------------------------------
    // default device authorization
    
    private static final boolean DEFAULT_DEVICE_AUTHORIZATION   = true;

    // ------------------------------------------------------------------------
    // custom property keys

    public static final String SERVICE_ACCOUNT_ID               = "ServiceAccount.ID";
    public static final String SERVICE_ACCOUNT_NAME             = "ServiceAccount.Name";
    public static final String SERVICE_ACCOUNT_ATTR             = "ServiceAccount.Attr";
    public static final String SERVICE_ACCOUNT_KEY              = "ServiceAccount.Key";

    public static final String DCS_PORT_OFFSET                  = "dcs.portOffset";
    public static final String DCS_BIND_INTERFACE               = "dcs.bindInterface";
    public static final String DCS_LISTEN_BACKLOG               = "dcs.listenBacklog";

    public static final String STARTUP_INIT_CLASS               = "StartupInit.class";
    public static final String STARTUP_INIT_CLASS_old           = "startup.initClass";
    public static final String STARTUP_INIT[] = { STARTUP_INIT_CLASS, STARTUP_INIT_CLASS_old };

    public static final String TRACK_BASE_URI                   = "track.baseURI";
    public static final String TRACK_ENABLE_COOKIES             = "track.enableCookies";
    public static final String TRACK_REQUIRE_COOKIES            = "track.requireCookies";
    public static final String TRACK_JAVASCRIPT_DIR             = "track.js.directory";
    public static final String TRACK_OFFLINE_FILE               = "track.offlineFile";
    public static final String TRACK_ENABLE_SERVICE             = "track.enableService";

    public static final String SUBDIVISION_PROVIDER_CLASS       = "SubdivisionProvider.class";

    public static final String RULE_FUNCTION_MAP_FACTORY        = "EventFunctionMapFactory.class";
    public static final String RULE_IDENTIFIER_MAP_FACTORY      = "EventIdentifierMapFactory.class";

    public static final String DEVICE_FUTURE_DATE_ACTION        = "Device.futureDate.action";
    public static final String DEVICE_FUTURE_DATE_MAX_SEC       = "Device.futureDate.maximumSec";
    public static final String DEVICE_INVALID_SPEED_ACTION      = "Device.invalidSpeed.action";
    public static final String DEVICE_INVALID_SPEED_MAX_KPH     = "Device.invalidSpeed.maximumKPH";

    public static final String TRANSPORT_QUERY_ENABLED          = "Transport.queryEnabled";
    public static final String UNIQUEXID_QUERY_ENABLED          = "UniqueXID.queryEnabled";

    public static final String DB_DEFAULT_DEVICE_AUTHORIZATION  = "db.defaultDeviceAuthorization";
    public static final String DB_DEFAULT_DEVICE_AUTHORIZATION_ = DB_DEFAULT_DEVICE_AUTHORIZATION + ".";

    public static final String SYSTEM_ADMIN_ACCOUNT_ID          = "sysAdmin.account";

    protected static RTKey.Entry runtimeKeys[] = {
        new RTKey.Entry("Custom GTS Properties"),
        new RTKey.Entry(STARTUP_INIT_CLASS              , null                          , "Startup Initialization class"),
        new RTKey.Entry(TRACK_BASE_URI                  , null                          , "'Track' Base URI"),
        new RTKey.Entry(TRACK_REQUIRE_COOKIES           , true                          , "'Track' Require Enabled Cookies"),
        new RTKey.Entry(TRACK_JAVASCRIPT_DIR            , null                          , "'Track' JavaScript Directory"),
        new RTKey.Entry(TRACK_OFFLINE_FILE              , null                          , "'Track' Offline File"),
        new RTKey.Entry(TRACK_ENABLE_SERVICE            , false                         , "'Track' Enable 'Service'"),
        new RTKey.Entry(SUBDIVISION_PROVIDER_CLASS      , null                          , "SubdivisionProvider class"),
        new RTKey.Entry(RULE_FUNCTION_MAP_FACTORY       , null                          , "EventFunctionMapFactory subclass"),
        new RTKey.Entry(RULE_IDENTIFIER_MAP_FACTORY     , null                          , "EventIdentifierMapFactory subclass"),
        new RTKey.Entry(DEVICE_FUTURE_DATE_ACTION       , ""                            , "Future Date Action"),
        new RTKey.Entry(DEVICE_FUTURE_DATE_MAX_SEC      , -1L                           , "Future Date Maximm Seconds"),
        new RTKey.Entry(TRANSPORT_QUERY_ENABLED         , false                         , "Enable DB Transport query"),
        new RTKey.Entry(UNIQUEXID_QUERY_ENABLED         , false                         , "Enable DB UniqueXID query"),
        new RTKey.Entry(DB_DEFAULT_DEVICE_AUTHORIZATION , DEFAULT_DEVICE_AUTHORIZATION  , "Default Device Authoirization"),
        new RTKey.Entry(SYSTEM_ADMIN_ACCOUNT_ID         , ""                            , "System Admin Account ID"),
    };

    // ------------------------------------------------------------------------
    // Default User device/group authorization when no groups are defined
    
    public static boolean GetDefaultDeviceAuthorization(String id)
    {
        if (!StringTools.isBlank(id)) {
            String idKey = DB_DEFAULT_DEVICE_AUTHORIZATION_ + id;
            if (RTConfig.hasProperty(idKey)) {
                return RTConfig.getBoolean(idKey, DEFAULT_DEVICE_AUTHORIZATION);
            }
        }
        return RTConfig.getBoolean(DB_DEFAULT_DEVICE_AUTHORIZATION, DEFAULT_DEVICE_AUTHORIZATION);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    
    /**
    *** Compares the GTS_HOME environment variable to the current directory.
    *** @return True if GTS_HOME matches current directory, false otherwise
    **/
    public static boolean check_GTS_HOME()
    {
        File gtsDir  = null;
        File curDir = null;

        /* GS_HOME environment variable */
        try {
            String gtsStr = System.getenv("GTS_HOME");
            if (StringTools.isBlank(gtsStr)) {
                Print.logWarn("GTS_HOME environment variable is not defined");
                return false;
            }
            gtsDir = new File(gtsStr);
            try {
                File dir = gtsDir.getCanonicalFile();
                gtsDir = dir;
            } catch (Throwable th) {
                //
            }
        } catch (Throwable th) {
            Print.logWarn("Error attempting to obtain GTS_HOME environment varable");
            return false;
        }

        /* current directory */
        try {
            String curStr = System.getProperty("user.dir","");
            if (StringTools.isBlank(curStr)) {
                Print.logWarn("'user.dir' system property is not defined");
                return false;
            }
            curDir = new File(curStr);
            try {
                File dir = curDir.getCanonicalFile();
                curDir = dir;
            } catch (Throwable th) {
                //
            }
        } catch (Throwable th) {
            Print.logWarn("Error attempting to determine current directory");
            return false;
        }

        /* match? */
        if (!curDir.equals(gtsDir)) {
            Print.logWarn("Warning: GTS_HOME directory does not match current directory!");
            Print.logWarn("GTS_HOME   : " + gtsDir);
            Print.logWarn("Current Dir: " + curDir);
            return false;
        }
        
        /* match */
        return true;
        
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    
    /**
    *** DBInitialization interface
    **/
    public interface DBInitialization
    {

        /**
        *** Called prior to DB initialization
        **/
        public void preInitialization();

        /**
        *** Called after the standard table factories have been initialized
        **/
        public void addTableFactories();

        /**
        *** Called after DB initialization
        **/
        public void postInitialization();

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Add the standard DBFactories as-is
    **/
    public static void addTableFactories()
    {

        /* Standard tables */
        String standardTables[] = new String[] {
            PACKAGE_TABLES_ + "Account"      ,
            PACKAGE_TABLES_ + "AccountString",
            PACKAGE_TABLES_ + "User"         ,
            PACKAGE_TABLES_ + "UserAcl"      ,
            PACKAGE_TABLES_ + "GroupList"    ,
            PACKAGE_TABLES_ + "Device"       ,
            PACKAGE_TABLES_ + "Transport"    ,
            PACKAGE_TABLES_ + "UniqueXID"    ,
            PACKAGE_TABLES_ + "DeviceGroup"  ,
            PACKAGE_TABLES_ + "DeviceList"   ,
            PACKAGE_TABLES_ + "EventData"    ,
            PACKAGE_TABLES_ + "Geozone"      ,
            PACKAGE_TABLES_ + "Resource"     ,
            PACKAGE_TABLES_ + "Role"         ,
            PACKAGE_TABLES_ + "RoleAcl"      ,
            PACKAGE_TABLES_ + "StatusCode"   ,
            PACKAGE_TABLES_ + "SystemProps"  ,
        };
        for (String tableClassName : standardTables) {
            DBAdmin.addTableFactory(tableClassName, true); // required
        }

        /* Extra tables (optional) */
        String extraTables[] = new String[] {
            PACKAGE_EXTRA_TABLES_ + "Entity",
            PACKAGE_EXTRA_TABLES_ + "SessionStats",
            PACKAGE_EXTRA_TABLES_ + "UnassignedDevices",
            PACKAGE_EXTRA_TABLES_ + "PendingCommands",
        };
        for (String tableClassName : extraTables) {
            DBAdmin.addTableFactory(tableClassName, false); // optional
        }

        /* OpenDMTP protocol tables (optional) */
        String dmtpTables[] = new String[] {
            PACKAGE_DMTP_   + "EventTemplate",
            PACKAGE_DMTP_   + "PendingPacket",
            PACKAGE_DMTP_   + "Property"     ,
            PACKAGE_DMTP_   + "Diagnostic"   ,
        };
        for (String tableClassName : dmtpTables) {
            DBAdmin.addTableFactory(tableClassName, false); // optional
        }

        /* Rule/GeoCorridor tables (optional) */
        String ruleTables[] = new String[] {
            PACKAGE_RULE_TABLES_ + "Rule",
            PACKAGE_RULE_TABLES_ + "RuleList",
            PACKAGE_RULE_TABLES_ + "GeoCorridor",
            PACKAGE_RULE_TABLES_ + "GeoCorridorList",
            PACKAGE_RULE_TABLES_ + "NotifyQueue",
        };
        for (String tableClassName : ruleTables) {
            DBAdmin.addTableFactory(tableClassName, false); // optional
        }

        /* BorderCrossing tables (optional) */
        String bcrossTables[] = new String[] {
            PACKAGE_BCROSS_TABLES_ + "BorderCrossing",
        };
        for (String tableClassName : bcrossTables) {
            DBAdmin.addTableFactory(tableClassName, false); // optional
        }

    }

    /**
    *** Initializes all DBFactory classes
    **/
    private static void _initDBFactories(Object startupInit)
    {

        /* set DBFactory CustomFactoryHandler */
        if (startupInit instanceof DBFactory.CustomFactoryHandler) {
            DBFactory.setCustomFactoryHandler((DBFactory.CustomFactoryHandler)startupInit);
        }

        /* register DBFactory classes */
        if (startupInit instanceof DBConfig.DBInitialization) {
            try {
                ((DBConfig.DBInitialization)startupInit).addTableFactories();
            } catch (Throwable th) {
                Print.logException("'<DBConfig.DBInitialization>.addTableFactories' failed!", th);
            }
        } else {
            DBConfig.addTableFactories();
        }
        if (DBAdmin.getTableFactoryCount() <= 0) {
            Print.logStackTrace("No DBFactory classes have been registered!!");
        }

        /* clear DBFactory CustomFactoryHandler (for garbage collection) */
        DBFactory.setCustomFactoryHandler(null);

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private static boolean       didInit = false;

    /**
    *** Initializes runtime keys and 'version' RTConfig constant property
    *** @return True if this is the first time this method was called, false otherwise
    **/
    private static boolean _dbPreInit()
    {

        /* skip if already initialized */
        if (didInit) {
            return false; // already initialized
        }
        didInit = true;

        /* add our custom default properties */
        RTKey.addRuntimeEntries(runtimeKeys);

        /* init version RTConfig constant property */
        Version.initVersionProperty();

        /* continue with init */
        return true;

    }

    /**
    *** Initializes the DBFactories
    **/
    private static void _dbPostInit()
    {

        /* default database name */
        String dbName = RTConfig.getString(RTKey.DB_NAME, null);
        if ((dbName == null) || dbName.equals("") || dbName.equals("?")) {
            RTConfig.setString(RTKey.DB_NAME, DBConfig.DEFAULT_DB_NAME);
        }

        /* load external startup initialization class */
        Object startupInit = null;
        String initClassName = RTConfig.getString(DBConfig.STARTUP_INIT);
        if (!StringTools.isBlank(initClassName)) {
            try {
                //Print.logInfo("Loading custom Startup initializer: " + initClassName);
                Class cfgClass = Class.forName(initClassName);
                startupInit = cfgClass.newInstance();
            } catch (Throwable th) { // ClassNotFoundException, NoSuchMethodException, etc
                Print.logException("Unable to load Startup initializer: " + initClassName, th);
                startupInit = new StartupInit();
            }
        } else {
            //Print.logInfo("Loading standard/default Startup initializer");
            startupInit = new StartupInit();
        }

        /* custom pre-initialization */
        if (startupInit instanceof DBConfig.DBInitialization) {
            ((DBConfig.DBInitialization)startupInit).preInitialization();
        }

        /* register tables */
        DBConfig._initDBFactories(startupInit);

        /* load 'private.xml' (also loads 'reports.xml' if this is a 'Track' servlet) */
        BasicPrivateLabelLoader.loadPrivateLabelXML();

        /* custom post-initialization */
        if (startupInit instanceof DBConfig.DBInitialization) {
            ((DBConfig.DBInitialization)startupInit).postInitialization();
        }

        /* DCServerFactory init */
        DCServerFactory.init();

    }

    /**
    *** Entry point for Servlet WAR programs, which initializes the DBFactories
    **/
    public static void servletInit(Properties srvCtxProps)
    {
        // servlet runtime initialization
        RTConfig.setWebApp(true); // force isWebapp=true

        /* skip if already initialized */
        if (!DBConfig._dbPreInit()) {
            return; // already initialized
        }

        /* Display warning if 'srvCtxProps' is null */
        // This means that this is not being called from 'RTConfigContextListener'
        if (srvCtxProps == null) {
            Print.logWarn("*** Servlet RTConfigContextListener was not configured propertly");
        }

        /* Servlet context properties */
        RTConfig.setServletContextProperties(srvCtxProps); // also loads run-time properties
        if (RTConfig.isDebugMode()) {
            Print.setLogLevel(Print.LOG_ALL);                       // log everything
            Print.setLogHeaderLevel(Print.LOG_ALL);                 // include log header on everything
        }

        /* db init */
        DBConfig._dbPostInit();

    }

    /**
    *** Entry point for various programs/tools which initializes the DBFactories
    *** @param argv  The Command-Line arguments, if any
    *** @param interactive  True if this is invoked from a user interactive command-line tool,
    ***                     False if this is invoked from a server non-interactive command-line tool.
    **/
    public static int cmdLineInit(String argv[], boolean interactive)
    {
        // command-line initialization

        /* skip if already initialized */
        if (!DBConfig._dbPreInit()) {
            return -1; // already initialized
        }
        
        // command-line
        int nextArg = RTConfig.setCommandLineArgs(argv);
        if (interactive) {
            Print.setLogFile(null);
            if (RTConfig.isDebugMode()) {
                Print.setLogLevel(Print.LOG_ALL);                   // log everything
                Print.setLogHeaderLevel(Print.LOG_ALL);             // include log header on everything
            } else {
                Print.setLogHeaderLevel(Print.LOG_WARN);            // include log header on WARN/ERROR/FATAL
            }
            RTConfig.setBoolean(RTKey.LOG_INCL_DATE, false);        // exclude date
            RTConfig.setBoolean(RTKey.LOG_INCL_STACKFRAME, true);   // include stackframe
        } else {
            if (RTConfig.isDebugMode()) {
                Print.setLogLevel(Print.LOG_ALL);                   // log everything
                Print.setLogHeaderLevel(Print.LOG_ALL);             // include log header on everything
            }
            //RTConfig.setBoolean(RTKey.LOG_INCL_DATE, true);
            //RTConfig.setBoolean(RTKey.LOG_INCL_STACKFRAME, false);
        }

        /* db init */
        DBConfig._dbPostInit();

        /* return pointer to next command line arg */
        return nextArg;

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    
    private static final String ARG_INIT_TABLES[]   = new String[] { "initTables"            };
    private static final String ARG_ACCOUNT[]       = new String[] { "account"               };
    private static final String ARG_NEW_ACCOUNT[]   = new String[] { "newAccount", "account" };
    private static final String ARG_NEW_DEVICE[]    = new String[] { "newDevice"             };
    private static final String ARG_TREE[]          = new String[] { "tree"                  };
    public  static final String ARG_SCHEMA[]        = new String[] { "schema"                };

    /**
    *** Displays command 'usage', then exists
    *** @param msg  The message to display before printing 'usage'
    **/
    private static void usage(String msg, boolean exit)
    {
        if (!StringTools.isBlank(msg)) { 
            Print.sysPrintln(msg);
        }
        Print.sysPrintln("");
        Print.sysPrintln("Usage:");
        Print.sysPrintln("  java ... " + DBConfig.class.getName() + " [-rootUser=<user> [-rootPass=<password>]] {options}");
        Print.sysPrintln("Options:");
        Print.sysPrintln("  Validating tables/columns:");
        Print.sysPrintln("     -tables[=<flags(below)>]");
        Print.sysPrintln("         't' - create missing tables [default]");
        Print.sysPrintln("         'c' - add missing columns");
        Print.sysPrintln("         'a' - alter columns with changed types");
        Print.sysPrintln("         'w' - display warnings");
        Print.sysPrintln("         'u' - check column character-encoding (must be used with 'a' or 'w')");
        Print.sysPrintln("         's' - show columns");
        Print.sysPrintln("  Loading tables from a CSV file:");
        Print.sysPrintln("     -load=<TableName>[.csv] -dir=<Source_Dir> [-overwrite]");
        Print.sysPrintln("  Displaying the DB schema:");
        Print.sysPrintln("     -schema[=<TableName>]");
      //Print.sysPrintln("  Dropping a table (WARNING: cannot be undone!):");
      //Print.sysPrintln("     -drop=<TableName>");
        Print.sysPrintln("");
        if (exit) {
            System.exit(1);
        }
    }

    /**
    *** Displays the DBFactory dependency tree
    *** @param level  The current tree level (used for indentation purposes)
    *** @param fact   The current factory node
    **/
    private static void _displayDependencyTree(int level, DBFactory fact)
    {
        if (fact != null) {
            String lvlStr = StringTools.replicateString("  ", level);
            Print.sysPrintln(lvlStr + "- " + fact.getTableName());
            DBFactory children[] = fact.getChildFactories();
            for (int i = 0; i < children.length; i++) {
                DBFactory child = children[i];
                _displayDependencyTree(level + 1, children[i]);
            }
        }
    }

    /**
    *** Main entry point for providing command-line DB administration tools
    *** @param argv  The command-line arguments
    **/
    public static int _main(String argv[])
    {
        DBConfig.cmdLineInit(argv,true);  // main

        /* default 'rootUser'/'rootPass' */
        // The following may be required for some of the following operations
        //  -rootUser=<root>
        //  -rootPass=<pass>
        if (!RTConfig.hasProperty(DBAdmin.ARG_ROOT_USER)) {
            // set default root user/pass
            RTConfig.setString(DBAdmin.ARG_ROOT_USER[0], "root");
            RTConfig.setString(DBAdmin.ARG_ROOT_PASS[0], "");
        } else
        if (!RTConfig.hasProperty(DBAdmin.ARG_ROOT_PASS)) {
            // 'rootUser' has been specified, but 'rootPass' is missing.
            RTConfig.setString(DBAdmin.ARG_ROOT_PASS[0], "");
        }

        /* create tables */
        if (RTConfig.hasProperty(ARG_INIT_TABLES)) {
            RTConfig.setBoolean(DBAdmin.ARG_CREATE_DB[0], true);
            RTConfig.setBoolean(DBAdmin.ARG_GRANT[0]    , true);
            if (RTConfig.hasProperty(DBAdmin.ARG_TABLES)) {
                // the command-line overrides anything we would put here
                //String tableOptions = RTConfig.getString(DBAdmin.ARG_TABLES,"");
                //if (tableOptions.indexOf("t") < 0) {
                //    RTConfig.setString(DBAdmin.ARG_TABLES[0], "t" + tableOptions);
                //}
            } else {
                String tableOptions = RTConfig.getString(ARG_INIT_TABLES,"");
                RTConfig.setString(DBAdmin.ARG_TABLES[0], tableOptions);
            }
            // The following are required:
            //   -rootUser=<root>
            //   -rootPass=<pass>
            // These should be available in the config file
            //   db.sql.user=<Grant_User>       - GRANT only
            //   db.sql.pass=<Grant_Pass>       - GRANT only
            //   db.sql.name=<DataBase_Name>    - GRANT only
        }

        /* dump a specific account? (only used by "-dump=<table>") */
        if (RTConfig.hasProperty(ARG_ACCOUNT)) {
            // Warning: this will work only for tables that have an 'accountID' column defined
            String accountID = RTConfig.getString(ARG_ACCOUNT,"");
            // [DB]WHERE (accountID=<account>)
            DBWhere dwh = new DBWhere(Account.getFactory());
            RTConfig.setString(DBAdmin.ARG_WHERE[0],dwh.WHERE_(dwh.EQ(Account.FLD_accountID,accountID)));
            //Print.logInfo("Set WHERE: " + RTConfig.getString("where"));
        }
 
        /* intercept DBAdmin schema: print table schema */
        if (RTConfig.hasProperty(ARG_SCHEMA)) {
            // -schema[=<table>]
            String schemaTable = RTConfig.getString(ARG_SCHEMA, null);
            DBAdmin.printTableSchema(95, SCHEMA_HEADER, schemaTable);
            return 0;
        }

        /* execute commands present in run-time config */
        DBAdminExec dbExecStatus = DBAdmin.execCommands();
        if ((dbExecStatus == null) || dbExecStatus.equals(DBAdminExec.ERROR)) {
            // command found, but an error occurred
            return 1;
        } else 
        if (dbExecStatus.equals(DBAdminExec.EXIT)) {
            // command found, executed successfully, but indicated that this command should terminate
            // (see "reload")
            return 0;
        }
        int execCmd = dbExecStatus.equals(DBAdminExec.OK)? 1 : 0;

        /* final tables update check */
        if (RTConfig.hasProperty(DBAdmin.ARG_TABLES)) {
            execCmd++;
            // update SystemProps versions
            SystemProps.updateVersions();
        }

        /* create a default account */
        if (RTConfig.hasProperty(ARG_NEW_ACCOUNT)) {
            execCmd++;
            String acctID = RTConfig.getString(ARG_NEW_ACCOUNT, null);
            if ((acctID != null) && !acctID.equals("")) {
                try {
                    Account.createNewAccount(acctID, Account.BLANK_PASSWORD);
                    Print.logInfo("Created account:" + acctID);
                } catch (DBException dbe) {
                    Print.logException("Error creating account: " + acctID, dbe);
                }
            } else {
                Print.logWarn("New Account name not specified. Account creation ignored.");
            }
        }

        /* create a default device */
        if (RTConfig.hasProperty(ARG_NEW_DEVICE)) {
            execCmd++;
            String acctID = RTConfig.getString(ARG_NEW_ACCOUNT, null);
            if ((acctID != null) && !acctID.equals("")) {
                String devID  = RTConfig.getString(ARG_NEW_DEVICE, null);
                if ((devID != null) && !devID.equals("")) {
                    try {
                        Account account = Account.getAccount(acctID);
                        if (account == null) {
                            throw new DBException("Account not found");
                        }
                        Device.createNewDevice(account, devID, null);
                        Print.logInfo("Created device: " + acctID + "," + devID);
                    } catch (DBException dbe) {
                        Print.logException("Error creating account:device: " + acctID + "," + devID, dbe);
                    }
                } else {
                    Print.logWarn("New Device name not specified. Device creation ignored.");
                }
            } else {
                Print.logWarn("New Account name not specified. Device creation ignored.");
            }
        }

        /* show dependency tree */
        if (RTConfig.hasProperty(ARG_TREE)) {
            execCmd++;
            Print.sysPrintln("Table dependency tree:");
            _displayDependencyTree(0, DBAdmin.getTableFactory(Account.TABLE_NAME()));
            //try {
            //    (new Account.Key("xtest")).delete(true);
            //    (new Device.Key("xtest","car")).delete(true);
            //} catch (Throwable t) {
            //    Print.logException("",t);
            //}
        }

        /* usage? */
        if (execCmd <= 0) {
            DBConfig.usage("",false);
            return 1;
        } else {
            return 0;
        }

    }

    /**
    *** Main entry point for providing command-line DB administration tools
    *** @param argv  The command-line arguments
    **/
    public static void main(String argv[])
    {
        System.exit(DBConfig._main(argv));
    }

}
