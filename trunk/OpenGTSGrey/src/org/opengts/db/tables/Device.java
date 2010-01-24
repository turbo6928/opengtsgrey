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
// Notes:
//  This 'Device' table currently assumes a 1-1 relationship between the device hardware
//  used to perform the tracking and communication, and the Vehicle being tracked.
//  However, it is possible to have more than one device on a given vehicle, or a single
//  hardware device may be moved between vehicles.  Ideally, this table should be split
//  into 2 separate tables: The Device table, and the MobileAsset table.
// ----------------------------------------------------------------------------
// Change History:
//  2006/03/26  Martin D. Flynn
//     -Initial release
//  2006/04/09  Martin D. Flynn
//     -Integrate DBException
//  2006/05/23  Martin D. Flynn
//     -Changed column 'uniqueID' to a 'VARCHAR(40)'
//  2007/01/25  Martin D. Flynn
//     -Moved to "OpenGTS"
//     -Various new fields added
//  2007/03/25  Martin D. Flynn
//     -Added 'equipmentType', 'groupID'
//     -Moved to 'org.opengts.db.tables'
//  2007/04/15  Martin D. Flynn
//     -Added 'borderCrossing' column.
//  2007/06/30  Martin D. Flynn
//     -Added 'getFirstEvent', 'getLastEvent'
//  2007/07/14  Martin D. Flynn
//     -Added '-uniqueid' command-line option.
//  2007/07/27  Martin D. Flynn
//     -Added 'notifyAction' column
//  2007/08/09  Martin D. Flynn
//     -Renamed command-line option "uniqid" to "uniqueid"
//     -Set 'deviceExists' to true when creating a new device.
//  2007/09/16  Martin D. Flynn
//     -Integrated DBSelect
//     -Added handlers for client device errors, diagnostics, and properties.
//     -Added device lookup for the specified unique-id.
//  2007/11/28  Martin D. Flynn
//     -Added columns 'lastBorderCrossTime', 'simPhoneNumber', 'lastInputState'.
//     -Added additional 'Entity' methods
//     -Added OpenDMTP 'CommandErrors' definition section.
//     -Added '-editall' command-line option to display all fields.
//  2007/12/13  Martin D. Flynn
//     -Added an EventData filter to check for invalid odometer values.
//  2007/01/10  Martin D. Flynn
//     -Added column 'notes', 'imeiNumber'
//     -Removed handlers for client device errors, diagnostics, and properties
//      (these handlers have been implemented in 'DeviceDBImpl.java')
//  2008/02/11  Martin D. Flynn
//     -Added columns 'FLD_deviceCode', 'FLD_vehicleID'
//  2008/03/12  Martin D. Flynn
//     -Added column 'FLD_notifyPriority'
//  2008/05/14  Martin D. Flynn
//     -Integrated Device DataTransport interface
//  2008/05/20  Martin D. Flynn
//     -Fixed 'UniqueID" to again make it visible to the CLI record editor.
//  2008/06/20  Martin D. Flynn
//     -Added column 'FLD_notifyDescription'
//  2008/07/21  Martin D. Flynn
//     -Added column 'FLD_linkURL'
//  2008/08/24  Martin D. Flynn
//     -Added 'validGPS' argument to 'getRangeEvents' and 'getLatestEvents'
//  2008/09/01  Martin D. Flynn
//     -Added optional field list "FixedLocationFieldInfo"
//     -Added field/column "FLD_smsEmail"
//  2008/10/16  Martin D. Flynn
//     -Added FLD_lastPingTime, FLD_totalPingCount
//  2008/12/01  Martin D. Flynn
//     -Added FLD_linkDescription, FLD_pushpinID
//     -Added optional field list 'GeoCorridorFieldInfo'
//  2009/05/24  Martin D. Flynn
//     -Added FLD_pendingPingCommand, FLD_remotePortCurrent
//     -Added FLD_lastValidLatitude/Longitude to optimize Geozone calculations.
//     -Added FLS_lastOdometerKM to optimize GPS odometer calculations.
//  2009/06/01  Martin D. Flynn
//     -Increased background thread pool size/limit to 25.
//  2009/09/23  Martin D. Flynn
//     -Added support for ignoring/truncating events with future timestamps
//     -Added FLD_maxPingCount
//  2009/10/02  Martin D. Flynn
//     -Changed "getGeozoneTransition" to return an array of Geozone transitions,
//      fixing the case where 2 adjacent events occur in 2 different geozones.
//  2009/11/01  Martin D. Flynn
//     -Added FLD_expectAck, FLD_lastAckCommand, FLD_lastAckTime
//  2009/12/16  Martin D. Flynn
//     -Added command-line check for "Periodic Maintenance/Service Due" (-maintkm=email)
// ----------------------------------------------------------------------------
package org.opengts.db.tables;

import java.lang.*;
import java.util.*;
import java.math.*;
import java.io.*;
import java.sql.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;

import org.opengts.dbtypes.*;
import org.opengts.geocoder.*;
import org.opengts.db.*;
import org.opengts.db.RuleFactory.NotifyAction;
import org.opengts.db.tables.Transport.Encodings;

/**
*** This class represents a tracked asset (ie. something that is being tracked).
*** Currently, this DBRecord also represents the tracking hardware device as well.
**/

public class Device // Asset
    extends DeviceRecord<Device>
    implements DataTransport
{

    // ------------------------------------------------------------------------

    /* optimization for caching status code descriptions */
    public static boolean CACHE_STATUS_CODE_DESCRIPTIONS = true;

    /* ReverseGeocodeProvider required on command-line "-insertGP" */
    public static boolean INSERT_REVERSEGEOCODE_REQUIRED = false;

    // ------------------------------------------------------------------------

    /* "Device" title (ie. "Taxi", "Tractor", "Vehicle", etc) */
    public static String[] GetTitles(Locale loc)
    {
        I18N i18n = I18N.getI18N(Device.class, loc);
        return new String[] {
            i18n.getString("Device.title.singular", "Vehicle"),
            i18n.getString("Device.title.plural"  , "Vehicles"),
        };
    }

    // ------------------------------------------------------------------------

    /* Event update background thread */
    private static final int BACKGROUND_THREAD_POOL_SIZE = 25;
    private static ThreadPool BackgroundThreadPool = new ThreadPool("DeviceEventUpdate", BACKGROUND_THREAD_POOL_SIZE);

    // ------------------------------------------------------------------------
    // border crossing flags (see 'borderCrossing' column)

    public enum BorderCrossingState implements EnumTools.StringLocale, EnumTools.IntValue {
        OFF         ( 0, I18N.getString(Device.class,"Device.boarderCrossing.off","off")),
        ON          ( 1, I18N.getString(Device.class,"Device.boarderCrossing.on" ,"on" ));
        // ---
        private int         vv = 0;
        private I18N.Text   aa = null;
        BorderCrossingState(int v, I18N.Text a)     { vv = v; aa = a; }
        public int     getIntValue()                { return vv; }
        public String  toString()                   { return aa.toString(); }
        public String  toString(Locale loc)         { return aa.toString(loc); }
    };

    // ------------------------------------------------------------------------
    // maximum reasonable odometer value for a vehicle

    // TODO: this should be device dependent
    public  static final double MAX_DEVICE_ODOM_KM                      = 500000.0 * GeoPoint.KILOMETERS_PER_MILE;

    // ------------------------------------------------------------------------
    // new asset defaults

    private static final String NEW_DEVICE_NAME_                        = "New Device";

    // ------------------------------------------------------------------------

    private static final int    EXT_UPDATE_MASK                         = 0xFFFF;
    private static final int    EXT_UPDATE_NONE                         = 0x0000;
    private static final int    EXT_UPDATE_ADDRESS                      = 0x0001;
    private static final int    EXT_UPDATE_BORDER                       = 0x0002;

    // ------------------------------------------------------------------------
    // (Vehicle) Rule factory

    private static RuleFactory ruleFactory = null;

    /* set the RuleFactory for event notification */
    public static void setRuleFactory(RuleFactory rf)
    {
        if (rf != null) {
            Device.ruleFactory = rf;
            Print.logDebug("Device RuleFactory installed: " + StringTools.className(Device.ruleFactory));
        } else
        if (Device.ruleFactory != null) {
            Device.ruleFactory = null;
            Print.logDebug("Device RuleFactory removed.");
        }
    }

    /* return ture if a RuleFactory has been defined */
    public static boolean hasRuleFactory()
    {
        return (Device.ruleFactory != null);
    }

    /* get the event notification RuleFactory */
    public static RuleFactory getRuleFactory()
    {
        return Device.ruleFactory;
    }

    // ------------------------------------------------------------------------
    // (Device) Session statistics

    private static SessionStatsFactory statsFactory = null;

    /* set the SessionStatsFactory */
    public static void setSessionStatsFactory(SessionStatsFactory rf)
    {
        if (rf != null) {
            Device.statsFactory = rf;
            Print.logDebug("Device SessionStatsFactory installed: " + StringTools.className(Device.statsFactory));
        } else
        if (Device.statsFactory != null) {
            Device.statsFactory = null;
            Print.logDebug("Device SessionStatsFactory removed.");
        }
    }

    /* return ture if a SessionStatsFactory has been defined */
    public static boolean hasSessionStatsFactory()
    {
        return (Device.statsFactory != null);
    }

    /* get the event notification SessionStatsFactory */
    public static SessionStatsFactory getSessionStatsFactory()
    {
        return Device.statsFactory;
    }

    // ------------------------------------------------------------------------
    // (Vehicle) Entity manager

    private static EntityManager entityManager = null;

    /* set the connect/disconnect EntityManager */
    public static void setEntityManager(EntityManager ef)
    {
        if (ef != null) {
            Device.entityManager = ef;
            Print.logDebug("Device EntityManager installed: " + StringTools.className(Device.entityManager));
        } else
        if (Device.entityManager != null) {
            Device.entityManager = null;
            Print.logDebug("Device EntityManager removed.");
        }
    }

    /* return true if an EntityManager has been defined */
    public static boolean hasEntityManager()
    {
        return (Device.entityManager != null);
    }

    /* return the EntityManager (or null if not defined) */
    public static EntityManager getEntityManager()
    {
        return Device.entityManager;
    }

    public static String getEntityDescription(String accountID, String entityID)
    {
        String eid = StringTools.trim(entityID);
        if (!eid.equals("") && Device.hasEntityManager()) {
            eid = Device.getEntityManager().getEntityDescription(accountID, eid);
        }
        return eid;
    }

    // ------------------------------------------------------------------------
    // (Vehicle) "Ping" dispatcher

    private static PingDispatcher pingDispatcher = null;

    /* set the PingDispatcher */
    public static void setPingDispatcher(PingDispatcher pd)
    {
        if (pd != null) {
            Device.pingDispatcher = pd;
            Print.logDebug("Device PingDispatcher installed: " + StringTools.className(Device.pingDispatcher));
        } else
        if (Device.pingDispatcher != null) {
            Device.pingDispatcher = null;
            Print.logDebug("Device PingDispatcher removed.");
        }
    }

    /* return true if an PingDispatcher has been defined */
    public static boolean hasPingDispatcher()
    {
        return (Device.pingDispatcher != null);
    }

    /* return the PingDispatcher (or null if not defined) */
    public static PingDispatcher getPingDispatcher()
    {
        return Device.pingDispatcher;
    }

    // ------------------------------------------------------------------------
    // Future EventDate timestamp check

    public static final int FUTURE_DATE_UNDEFINED   = -999;
    public static final int FUTURE_DATE_IGNORE      = -1;
    public static final int FUTURE_DATE_DISABLED    = 0;
    public static final int FUTURE_DATE_TRUNCATE    = 1;

    private static int  FutureEventDateAction = FUTURE_DATE_UNDEFINED;
    public static int futureEventDateAction()
    {
        // TODO: synchronize?
        if (FutureEventDateAction == FUTURE_DATE_UNDEFINED) {
            // "Device.futureDate.action="
            String act = RTConfig.getString(DBConfig.DEVICE_FUTURE_DATE_ACTION,"");
            if (act.equalsIgnoreCase("ignore")   ||
                act.equalsIgnoreCase("skip")     ||
                act.equalsIgnoreCase("-1")         ) {
                FutureEventDateAction = FUTURE_DATE_IGNORE;
            } else
            if (act.equalsIgnoreCase("truncate") ||
                act.equalsIgnoreCase("1")          ) {
                FutureEventDateAction = FUTURE_DATE_TRUNCATE;
            } else
            if (StringTools.isBlank(act)         ||
                act.equalsIgnoreCase("disabled") ||
                act.equalsIgnoreCase("disable")  ||
                act.equalsIgnoreCase("0")          ) {
                FutureEventDateAction = FUTURE_DATE_DISABLED;
            } else {
                Print.logError("Invalid property value %s => %s", DBConfig.DEVICE_FUTURE_DATE_ACTION, act);
                FutureEventDateAction = FUTURE_DATE_DISABLED;
            }
        }
        return FutureEventDateAction;
    }

    private static long FutureEventDateMaxSec = -999L;
    public static long futureEventDateMaximumSec()
    {
        // TODO: synchronize?
        if (FutureEventDateMaxSec == -999L) {
            FutureEventDateMaxSec = RTConfig.getLong(DBConfig.DEVICE_FUTURE_DATE_MAX_SEC,0L);
        }
        return FutureEventDateMaxSec;
    }

    // ------------------------------------------------------------------------
    // Invalid speed check

    public static final int INVALID_SPEED_UNDEFINED   = -999;
    public static final int INVALID_SPEED_IGNORE      = -1;
    public static final int INVALID_SPEED_DISABLED    = 0;
    public static final int INVALID_SPEED_TRUNCATE    = 1;

    private static int  InvalidSpeedAction = INVALID_SPEED_UNDEFINED;
    public static int invalidSpeedAction()
    {
        // TODO: synchronize?
        if (InvalidSpeedAction == INVALID_SPEED_UNDEFINED) {
            // "Device.invalidSpeed.action="
            String act = RTConfig.getString(DBConfig.DEVICE_INVALID_SPEED_ACTION,"");
            if (act.equalsIgnoreCase("ignore")   ||
                act.equalsIgnoreCase("skip")     ||
                act.equalsIgnoreCase("-1")         ) {
                InvalidSpeedAction = INVALID_SPEED_IGNORE;
            } else
            if (act.equalsIgnoreCase("truncate") ||
                act.equalsIgnoreCase("1")          ) {
                InvalidSpeedAction = INVALID_SPEED_TRUNCATE;
            } else
            if (StringTools.isBlank(act)         ||
                act.equalsIgnoreCase("disabled") ||
                act.equalsIgnoreCase("disable")  ||
                act.equalsIgnoreCase("0")          ) {
                InvalidSpeedAction = INVALID_SPEED_DISABLED;
            } else {
                Print.logError("Invalid property value %s => %s", DBConfig.DEVICE_INVALID_SPEED_ACTION, act);
                InvalidSpeedAction = INVALID_SPEED_DISABLED;
            }
        }
        return InvalidSpeedAction;
    }

    private static double InvalidSpeedMaxKPH = -999.0;
    public static double invalidSpeedMaximumKPH()
    {
        // TODO: synchronize?
        if (InvalidSpeedMaxKPH <= -999.0) {
            InvalidSpeedMaxKPH = RTConfig.getDouble(DBConfig.DEVICE_INVALID_SPEED_MAX_KPH,0.0);
        }
        return InvalidSpeedMaxKPH;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // SQL table definition below
    // Note: The following fields should be updated upon each connection from the client device:
    //  - FLD_lastInputState
    //  - FLD_ipAddressCurrent
    //  - FLD_remotePortCurrent
    //  - FLD_lastTotalConnectTime
    //  - FLD_lastDuplexConnectTime (OpenDMTP clients, otherwise optional)
    //  - FLD_totalProfileMask (OpenDMTP clients)
    //  - FLD_duplexProfileMask (OpenDMTP clients)

    /* table name */
    public static final String _TABLE_NAME               = "Device"; // "Asset"
    public static String TABLE_NAME() { return DBProvider.translateTableName(_TABLE_NAME); }

    /* field definition */
    // Device/Asset specific information:
    public static final String FLD_groupID               = "groupID";               // vehicle group (user informational only)
    public static final String FLD_equipmentType         = "equipmentType";         // equipment type
    public static final String FLD_vehicleID             = "vehicleID";             // vehicle id number (ie VIN)
    public static final String FLD_driverID              = "driverID";              // driver id
    // DataTransport specific attributes (see also Transport.java)
    // (These fields contain the default DataTransport attributes)
    public static final String FLD_uniqueID              = "uniqueID";              // unique device ID
    public static final String FLD_deviceCode            = "deviceCode";            // DCServerConfig ID ("serverID")
    public static final String FLD_deviceType            = "deviceType";            // reserved
    public static final String FLD_pushpinID             = "pushpinID";             // map pushpin ID
    public static final String FLD_serialNumber          = "serialNumber";          // device hardware serial#.
    public static final String FLD_simPhoneNumber        = "simPhoneNumber";        // SIM phone number
    public static final String FLD_smsEmail              = "smsEmail";              // SMS email address (to the device itself)
    public static final String FLD_imeiNumber            = "imeiNumber";            // IMEI number (or moblie ID)
    public static final String FLD_ignitionIndex         = "ignitionIndex";         // hardware ignition I/O index
    public static final String FLD_codeVersion           = "codeVersion";           // code version installed on device
    public static final String FLD_featureSet            = "featureSet";            // device features
    public static final String FLD_ipAddressValid        = "ipAddressValid";        // valid IP address block
    // Ping/Command
    public static final String FLD_pingCommandURI        = "pingCommandURI";        // ping command URL
    public static final String FLD_pendingPingCommand    = "pendingPingCommand";    // pending ping command (should just be 'pendingCommand')
    public static final String FLD_lastPingTime          = "lastPingTime";          // last ping time
    public static final String FLD_totalPingCount        = "totalPingCount";        // total ping count
    public static final String FLD_maxPingCount          = "maxPingCount";          // maximum allowed ping count
    public static final String FLD_expectAck             = "expectAck";             // expecting a returned ACK
    public static final String FLD_lastAckCommand        = "lastAckCommand";        // last command expecting an ACK
    public static final String FLD_lastAckTime           = "lastAckTime";           // last received ACK time
    // Device Communication Server Configuration
    public static final String FLD_dcsConfigMask         = "dcsConfigMask";         // DCS Config Mask
    public static final String FLD_dcsConfigString       = "dcsConfigString";       // DCS Config String
    // DMTP
    public static final String FLD_supportsDMTP          = "supportsDMTP";          // DMTP
    public static final String FLD_supportedEncodings    = "supportedEncodings";    // DMTP
    public static final String FLD_unitLimitInterval     = "unitLimitInterval";     // DMTP
    public static final String FLD_maxAllowedEvents      = "maxAllowedEvents";      // DMTP
    public static final String FLD_totalProfileMask      = "totalProfileMask";      // DMTP
    public static final String FLD_totalMaxConn          = "totalMaxConn";          // DMTP
    public static final String FLD_totalMaxConnPerMin    = "totalMaxConnPerMin";    // DMTP
    public static final String FLD_duplexProfileMask     = "duplexProfileMask";     // DMTP
    public static final String FLD_duplexMaxConn         = "duplexMaxConn";         // DMTP
    public static final String FLD_duplexMaxConnPerMin   = "duplexMaxConnPerMin";   // DMTP
    // Last Device IP Address:Port
    public static final String FLD_ipAddressCurrent      = "ipAddressCurrent";      // current(last) IP address
    public static final String FLD_remotePortCurrent     = "remotePortCurrent";     // current(last) remote port
    // Last Event
    public static final String FLD_lastTotalConnectTime  = "lastTotalConnectTime";  // last connect time
    public static final String FLD_lastDuplexConnectTime = "lastDuplexConnectTime"; // last TCP connect time
    public static final String FLD_lastInputState        = "lastInputState";        // last known digital input state
    public static final String FLD_lastValidLatitude     = "lastValidLatitude";     // last known valid latitude
    public static final String FLD_lastValidLongitude    = "lastValidLongitude";    // last known valid longitude
    public static final String FLD_lastGPSTimestamp      = "lastGPSTimestamp";      // timestamp of last valid GPS Location
    public static final String FLD_lastOdometerKM        = "lastOdometerKM";        // last odometer value (may be simulated)
    public static final String FLD_odometerOffsetKM      = "odometerOffsetKM";      // offset to reported odometer
    //
    private static DBField FieldInfo[] = {
        // Asset/Vehicle specific fields
        newField_accountID(true),
        newField_deviceID(true),
        new DBField(FLD_groupID              , String.class        , DBField.TYPE_STRING(32)  , "Group ID"                    , "edit=2"),
        new DBField(FLD_equipmentType        , String.class        , DBField.TYPE_STRING(40)  , "Equipment Type"              , "edit=2"),
        new DBField(FLD_vehicleID            , String.class        , DBField.TYPE_STRING(24)  , "VIN"                         , "edit=2"),
        new DBField(FLD_driverID             , String.class        , DBField.TYPE_STRING(32)  , "Driver ID"                   , "edit=2"),
        // DataTransport fields (These fields contain the default DataTransport attributes)
        new DBField(FLD_uniqueID             , String.class        , DBField.TYPE_STRING(40)  , "Unique ID"                   , "edit=2 altkey=true presep"),
        new DBField(FLD_deviceCode           , String.class        , DBField.TYPE_STRING(24)  , "Server ID"                   , "edit=2"),
        new DBField(FLD_deviceType           , String.class        , DBField.TYPE_STRING(24)  , "Device Type"                 , "edit=2"),
        new DBField(FLD_pushpinID            , String.class        , DBField.TYPE_STRING(32)  , "Pushpin ID"                  , "edit=2"),
        new DBField(FLD_serialNumber         , String.class        , DBField.TYPE_STRING(24)  , "Serial Number"               , "edit=2"),
        new DBField(FLD_simPhoneNumber       , String.class        , DBField.TYPE_STRING(24)  , "SIM Phone Number"            , "edit=2"),
        new DBField(FLD_smsEmail             , String.class        , DBField.TYPE_STRING(64)  , "SMS EMail Address"           , "edit=2"),
        new DBField(FLD_imeiNumber           , String.class        , DBField.TYPE_STRING(24)  , "IMEI Number"                 , "edit=2"),
        new DBField(FLD_ignitionIndex        , Integer.TYPE        , DBField.TYPE_INT16       , "Ignition I/O Index"          , "edit=2"),
        new DBField(FLD_codeVersion          , String.class        , DBField.TYPE_STRING(32)  , "Code Version"                , ""),
        new DBField(FLD_featureSet           , String.class        , DBField.TYPE_STRING(64)  , "Feature Set"                 , ""),
        new DBField(FLD_ipAddressValid       , DTIPAddrList.class  , DBField.TYPE_STRING(128) , "Valid IP Addresses"          , "edit=2"),
        new DBField(FLD_lastTotalConnectTime , Long.TYPE           , DBField.TYPE_UINT32      , "Last Total Connect Time"     , "format=time"),
        new DBField(FLD_lastDuplexConnectTime, Long.TYPE           , DBField.TYPE_UINT32      , "Last Duplex Connect Time"    , "format=time"),
        // Ping/Command
      //new DBField(FLD_pingCommandURI       , String.class        , DBField.TYPE_STRING(128) , "Ping Command URL"            , "edit=2"),
        new DBField(FLD_pendingPingCommand   , String.class        , DBField.TYPE_TEXT        , "Pending Ping Command"        , "edit=2"),
        new DBField(FLD_lastPingTime         , Long.TYPE           , DBField.TYPE_UINT32      , "Last 'Ping' Time"            , "format=time"),
        new DBField(FLD_totalPingCount       , Integer.TYPE        , DBField.TYPE_UINT16      , "Total 'Ping' Count"          , ""),
        new DBField(FLD_maxPingCount         , Integer.TYPE        , DBField.TYPE_UINT16      , "Maximum 'Ping' Count"        , "edit=2"),
        new DBField(FLD_expectAck            , Boolean.TYPE        , DBField.TYPE_BOOLEAN     , "Expecting an ACK"            , "edit=2"),
        new DBField(FLD_lastAckCommand       , String.class        , DBField.TYPE_TEXT        , "Last Command Expecting an ACK", ""),
        new DBField(FLD_lastAckTime          , Long.TYPE           , DBField.TYPE_UINT32      , "Last Received 'ACK' Time"    , "format=time"),
        // Device Communication Server Configuration
        new DBField(FLD_dcsConfigMask        , Long.TYPE           , DBField.TYPE_UINT32      , "DCS Configuration Mask"      , "edit=2"),
      //new DBField(FLD_dcsConfigString      , String.class        , DBField.TYPE_STRING(64)  , "DCS Configuration String"    , "edit=2"),
        // DMTP
        new DBField(FLD_supportsDMTP         , Boolean.TYPE        , DBField.TYPE_BOOLEAN     , "Supports DMTP"               , "edit=2"),
        new DBField(FLD_supportedEncodings   , Integer.TYPE        , DBField.TYPE_UINT8       , "Supported Encodings"         , "edit=2 format=X1 editor=encodings mask=Transport$Encodings"),
        new DBField(FLD_unitLimitInterval    , Integer.TYPE        , DBField.TYPE_UINT16      , "Accounting Time Interval Min", "edit=2"),
        new DBField(FLD_maxAllowedEvents     , Integer.TYPE        , DBField.TYPE_UINT16      , "Max Events per Interval"     , "edit=2"),
        new DBField(FLD_totalProfileMask     , DTProfileMask.class , DBField.TYPE_BLOB        , "Total Profile Mask"          , ""),
        new DBField(FLD_totalMaxConn         , Integer.TYPE        , DBField.TYPE_UINT16      , "Max Total Conn per Interval" , "edit=2"),
        new DBField(FLD_totalMaxConnPerMin   , Integer.TYPE        , DBField.TYPE_UINT16      , "Max Total Conn per Minute"   , "edit=2"),
        new DBField(FLD_duplexProfileMask    , DTProfileMask.class , DBField.TYPE_BLOB        , "Duplex Profile Mask"         , ""),
        new DBField(FLD_duplexMaxConn        , Integer.TYPE        , DBField.TYPE_UINT16      , "Max Duplex Conn per Interval", "edit=2"),
        new DBField(FLD_duplexMaxConnPerMin  , Integer.TYPE        , DBField.TYPE_UINT16      , "Max Duplex Conn per Minute"  , "edit=2"),
        // Last Event
        new DBField(FLD_ipAddressCurrent     , DTIPAddress.class   , DBField.TYPE_STRING(32)  , "Current IP Address"          , ""),
        new DBField(FLD_remotePortCurrent    , Integer.TYPE        , DBField.TYPE_UINT16      , "Current Remote Port"         , ""),
        new DBField(FLD_lastInputState       , Long.TYPE           , DBField.TYPE_UINT32      , "Last Input State"            , ""),
        new DBField(FLD_lastValidLatitude    , Double.TYPE         , DBField.TYPE_DOUBLE      , "Last Valid Latitude"         , "format=#0.00000"),
        new DBField(FLD_lastValidLongitude   , Double.TYPE         , DBField.TYPE_DOUBLE      , "Last Valid Longitude"        , "format=#0.00000"),
        new DBField(FLD_lastGPSTimestamp     , Long.TYPE           , DBField.TYPE_UINT32      , "Last Valid GPS Timestamp"    , "format=time"),
        new DBField(FLD_lastOdometerKM       , Double.TYPE         , DBField.TYPE_DOUBLE      , "Last Odometer km"            , "format=#0.0"),
        new DBField(FLD_odometerOffsetKM     , Double.TYPE         , DBField.TYPE_DOUBLE      , "Odometer Offset km"          , "format=#0.0"),
        // Common fields
        newField_isActive(),
        newField_displayName(),
        newField_description(),
        newField_notes(),
        newField_lastUpdateTime(),
        newField_creationTime(),
    };
    // Default Notification (RulesEngine support)
    public static final String FLD_allowNotify           = "allowNotify";           // allow notification
    public static final String FLD_lastNotifyTime        = "lastNotifyTime";        // last notification time
    public static final String FLD_lastNotifyCode        = "lastNotifyCode";        // last notification status code
    public static final String FLD_notifyEmail           = "notifyEmail";           // notification email address
    public static final String FLD_notifySelector        = "notifySelector";        // notification rule
    public static final String FLD_notifyAction          = "notifyAction";          // notification action
    public static final String FLD_notifyDescription     = "notifyDescription";     // notification description
    public static final String FLD_notifySubject         = "notifySubject";         // notification subject
    public static final String FLD_notifyText            = "notifyText";            // notification message
    public static final String FLD_notifyUseWrapper      = "notifyUseWrapper";      // notification email wrapper
    public static final String FLD_notifyPriority        = "notifyPriority";        // notification priority
    public static final DBField NotificationFieldInfo[] = {
        new DBField(FLD_allowNotify          , Boolean.TYPE        , DBField.TYPE_BOOLEAN     , "Allow Notification"          , "edit=2"),
        new DBField(FLD_lastNotifyTime       , Long.TYPE           , DBField.TYPE_UINT32      , "Last Notify Time"            , "format=time"),
        new DBField(FLD_lastNotifyCode       , Integer.TYPE        , DBField.TYPE_UINT32      , "Last Notify Status Code"     , "format=X2"),
        new DBField(FLD_notifyEmail          , String.class        , DBField.TYPE_STRING(128) , "Notification EMail Address"  , "edit=2"),
        new DBField(FLD_notifySelector       , String.class        , DBField.TYPE_TEXT        , "Notification Selector"       , "edit=2 editor=ruleSelector"),
        new DBField(FLD_notifyAction         , Integer.TYPE        , DBField.TYPE_UINT16      , "Notification Action"         , "edit=2 format=X2 editor=ruleAction mask=RuleFactory$NotifyAction"),
        new DBField(FLD_notifyDescription    , String.class        , DBField.TYPE_STRING(64)  , "Notification Description"    , "edit=2 utf8=true"),
        new DBField(FLD_notifySubject        , String.class        , DBField.TYPE_TEXT        , "Notification Subject"        , "edit=2 utf8=true"),
        new DBField(FLD_notifyText           , String.class        , DBField.TYPE_TEXT        , "Notification Message"        , "edit=2 editor=textArea utf8=true"),
        new DBField(FLD_notifyUseWrapper     , Boolean.TYPE        , DBField.TYPE_BOOLEAN     , "Notification Use Wrapper"    , "edit=2"),
        new DBField(FLD_notifyPriority       , Integer.TYPE        , DBField.TYPE_UINT16      , "Notification Priority"       , "edit=2"),
    };
    // Border Crossing
    public static final String FLD_borderCrossing        = "borderCrossing";        // border crossing flags
    public static final String FLD_lastBorderCrossTime   = "lastBorderCrossTime";   // timestamp of last border crossing calcs
    public static final DBField BorderCrossingFieldInfo[] = {
        new DBField(FLD_borderCrossing       , Integer.TYPE        , DBField.TYPE_UINT8       , "Border Crossing Flags"       , "edit=2 enum=Device$BorderCrossingState"),
        new DBField(FLD_lastBorderCrossTime  , Long.TYPE           , DBField.TYPE_UINT32      , "Last Border Crossing Time"   , "format=time"),
    };
    // Device/Asset Link information
    public static final String FLD_linkURL               = "linkURL";               // Link URL
    public static final String FLD_linkDescription       = "linkDescription";       // Link Description
    public static final DBField LinkFieldInfo[] = {
        new DBField(FLD_linkURL              , String.class        , DBField.TYPE_STRING(128) , "Link URL"                    , "edit=2"),
        new DBField(FLD_linkDescription      , String.class        , DBField.TYPE_STRING(64)  , "Link Description"            , "edit=2"),
    };
    // Fixed device location fields
    public static final String FLD_fixedLatitude         = "fixedLatitude";         // fixed latitude
    public static final String FLD_fixedLongitude        = "fixedLongitude";        // fixed longitude
    public static final String FLD_fixedAddress          = "fixedAddress";          // fixed address
    public static final String FLD_fixedContactPhone     = "fixedContactPhone";     // fixed contact phone#
    public static final String FLD_fixedServiceTime      = "fixedServiceTime";      // timestamp of last service
    public static final DBField FixedLocationFieldInfo[] = {
        new DBField(FLD_fixedLatitude        , Double.TYPE         , DBField.TYPE_DOUBLE      , "Fixed Latitude"              , "format=#0.00000 edit=2"),
        new DBField(FLD_fixedLongitude       , Double.TYPE         , DBField.TYPE_DOUBLE      , "Fixed Longitude"             , "format=#0.00000 edit=2"),
        new DBField(FLD_fixedAddress         , String.class        , DBField.TYPE_STRING(90)  , "Fixed Address (Physical)"    , "utf8=true"),
        new DBField(FLD_fixedContactPhone    , String.class        , DBField.TYPE_STRING(64)  , "Fixed Contact Phone"         , "utf8=true"),
        new DBField(FLD_fixedServiceTime     , Long.TYPE           , DBField.TYPE_UINT32      , "Last Service Time"           , "format=time edit=2"),
    };
    // GeoCorridor fields
    public static final String FLD_activeCorridor        = "activeCorridor";        // active GeoCorridor
    public static final DBField GeoCorridorFieldInfo[]   = {
        new DBField(FLD_activeCorridor       , String.class        , DBField.TYPE_STRING(32)  , "Active GeoCorridor"          , ""),
    };
    // Maintenance odometer fields
    public static final String FLD_maintIntervalKM0      = "maintIntervalKM0";      // maintenance interval #0
    public static final String FLD_maintOdometerKM0      = "maintOdometerKM0";      // maintenance odometer #0
    public static final DBField MaintOdometerFieldInfo[] = {
        new DBField(FLD_maintIntervalKM0     , Double.TYPE         , DBField.TYPE_DOUBLE      , "Maint. Interval #0"          , "format=#0.0"),
        new DBField(FLD_maintOdometerKM0     , Double.TYPE         , DBField.TYPE_DOUBLE      , "Maint. Odometer #0"          , "format=#0.0"),
      //new DBField(FLD_maintIntervalKM1     , Double.TYPE         , DBField.TYPE_DOUBLE      , "Maint. Interval #1"          , "format=#0.0"),
      //new DBField(FLD_maintOdometerKM1     , Double.TYPE         , DBField.TYPE_DOUBLE      , "Maint. Odometer #1"          , "format=#0.0"),
    };
    // Misc fields
    public static final String FLD_customAttributes       = "customAttributes";     // active GeoCorridor
    public static final DBField MiscFieldInfo[]   = {
        new DBField(FLD_customAttributes     , String.class        , DBField.TYPE_TEXT        , "Custom Fields"               , "edit=2"),
    };

    /* key class */
    public static class Key
        extends DeviceKey<Device>
    {
        public Key() {
            super();
        }
        public Key(String acctId, String devId) {
            super.setFieldValue(FLD_accountID, ((acctId != null)? acctId.toLowerCase() : ""));
            super.setFieldValue(FLD_deviceID , ((devId  != null)? devId.toLowerCase()  : ""));
        }
        public DBFactory<Device> getFactory() {
            return Device.getFactory();
        }
    }

    /* factory constructor */
    private static DBFactory<Device> factory = null;
    public static DBFactory<Device> getFactory()
    {
        if (factory == null) {
            EnumTools.registerEnumClass(NotifyAction.class);
            factory = DBFactory.createDBFactory(
                Device.TABLE_NAME(),
                Device.FieldInfo,
                DBFactory.KeyType.PRIMARY,
                Device.class,
                Device.Key.class,
                true/*editable*/, true/*viewable*/);
            factory.addParentTable(Account.TABLE_NAME());
        }
        return factory;
    }

    /* Bean instance */
    public Device()
    {
        super();
    }

    /* database record */
    public Device(Device.Key key)
    {
        super(key);
    }

    // ------------------------------------------------------------------------

    /* table description */
    public static String getTableDescription(Locale loc)
    {
        I18N i18n = I18N.getI18N(Device.class, loc);
        return i18n.getString("Device.description",
            "This table defines " +
            "Device/Vehicle specific information for an Account. " +
            "A 'Device' record typically represents something that is being 'tracked', such as a Vehicle."
            );
    }

    // SQL table definition above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Bean access fields below
    // ------------------------------------------------------------------------

    /* Group ID (user informational only, not used by DeviceGroup) */
    // currently used in various ReportLayout subclasses
    public String getGroupID()
    {
        String v = (String)this.getFieldValue(FLD_groupID);
        return StringTools.trim(v);
    }

    public void setGroupID(String v)
    {
        this.setFieldValue(FLD_groupID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getEquipmentType()
    {
        String v = (String)this.getFieldValue(FLD_equipmentType);
        return StringTools.trim(v);
    }

    public void setEquipmentType(String v)
    {
        this.setFieldValue(FLD_equipmentType, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getVehicleID()
    {
        String v = (String)this.getFieldValue(FLD_vehicleID);
        return StringTools.trim(v);
    }

    public void setVehicleID(String v)
    {
        this.setFieldValue(FLD_vehicleID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getDriverID()
    {
        String v = (String)this.getFieldValue(FLD_driverID);
        return StringTools.trim(v);
    }

    public void setDriverID(String v)
    {
        this.setFieldValue(FLD_driverID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getLinkURL()
    {
        String v = (String)this.getOptionalFieldValue(FLD_linkURL);
        return StringTools.trim(v);
    }

    public boolean hasLink()
    {
        return !StringTools.isBlank(this.getLinkURL());
    }

    public void setLinkURL(String v)
    {
        this.setOptionalFieldValue(FLD_linkURL, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getLinkDescription()
    {
        String v = (String)this.getOptionalFieldValue(FLD_linkDescription);
        return StringTools.trim(v);
    }

    public void setLinkDescription(String v)
    {
        this.setOptionalFieldValue(FLD_linkDescription, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public static boolean supportsNotification()
    {
        return Device.getFactory().hasField(FLD_allowNotify);
    }
    
    public boolean getAllowNotify()
    {
        Boolean v = (Boolean)this.getOptionalFieldValue(FLD_allowNotify);
        return (v != null)? v.booleanValue() : false;
    }

    public void setAllowNotify(boolean v)
    {
        this.setOptionalFieldValue(FLD_allowNotify, v);
    }

    public boolean allowNotify()
    {
        return this.getAllowNotify();
    }

    // ---

    public long getLastNotifyTime()
    {
        Long v = (Long)this.getOptionalFieldValue(FLD_lastNotifyTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void setLastNotifyTime(long v)
    {
        this.setOptionalFieldValue(FLD_lastNotifyTime, v);
    }

    // ---

    public int getLastNotifyCode()
    {
        return this.getFieldValue(FLD_lastNotifyCode, 0);
    }

    public void setLastNotifyCode(int v)
    {
        this.setFieldValue(FLD_lastNotifyCode, v);
    }

    // ---

    public String getNotifyEmail()
    {
        String v = (String)this.getOptionalFieldValue(FLD_notifyEmail);
        return StringTools.trim(v);
    }

    public void setNotifyEmail(String v)
    {
        this.setOptionalFieldValue(FLD_notifyEmail, StringTools.trim(v));
    }

    public String getNotifyEmail(boolean inclAccount)
    {
        String devEmail = this.getNotifyEmail();
        if (inclAccount) {
            Account acct = this.getAccount();
            if (acct != null) {
                String acctEmail = acct.getNotifyEmail();
                if (!acctEmail.equals("")) {
                    return !devEmail.equals("")? (devEmail + "," + acctEmail) : acctEmail;
                }
            }
        }
        return devEmail;
    }

    // ---

    /* get rule selector which be evaluated by the installed RuleFactory */
    public String getNotifySelector()
    {
        String v = (String)this.getOptionalFieldValue(FLD_notifySelector);
        return StringTools.trim(v);
    }

    /* set the notification rule selector */
    public void setNotifySelector(String v)
    {
        this.setOptionalFieldValue(FLD_notifySelector, StringTools.trim(v));
    }

    // ---

    public int getNotifyAction()
    {
        Integer v = (Integer)this.getOptionalFieldValue(FLD_notifyAction);
        return (v != null)? RuleFactoryAdapter.ValidateActionMask(v.intValue()) : RuleFactory.ACTION_NONE;
    }

    public void setNotifyAction(int v)
    {
        this.setOptionalFieldValue(FLD_notifyAction, RuleFactoryAdapter.ValidateActionMask(v));
    }

    // ---

    public String getNotifyDescription()
    {
        String v = (String)this.getOptionalFieldValue(FLD_notifyDescription);
        return StringTools.trim(v);
    }

    public void setNotifyDescription(String v)
    {
        this.setOptionalFieldValue(FLD_notifyDescription, StringTools.trim(v));
    }

    // ---

    public String getNotifySubject()
    {
        String v = (String)this.getFieldValue(FLD_notifySubject);
        return (v != null)? v : "";
    }

    public void setNotifySubject(String v)
    {
        this.setFieldValue(FLD_notifySubject, ((v != null)? v : ""));
    }

    // ---

    public String getNotifyText()
    {
        String v = (String)this.getFieldValue(FLD_notifyText);
        return (v != null)? v : "";
    }

    public void setNotifyText(String v)
    {
        String s = (v != null)? StringTools.encodeNewline(v) : "";
        this.setFieldValue(FLD_notifyText, s);
    }

    // ---

    public boolean getNotifyUseWrapper()
    {
        Boolean v = (Boolean)this.getFieldValue(FLD_notifyUseWrapper);
        return (v != null)? v.booleanValue() : true;
    }

    public void setNotifyUseWrapper(boolean v)
    {
        this.setFieldValue(FLD_notifyUseWrapper, v);
    }

    // ---

    public int getNotifyPriority()
    {
        Integer v = (Integer)this.getOptionalFieldValue(FLD_notifyPriority);
        return (v != null)? v.intValue() : 0;
    }

    public void setNotifyPriority(int v)
    {
        this.setOptionalFieldValue(FLD_notifyPriority, ((v < 0)? 0 : v));
    }

    // ------------------------------------------------------------------------

    public int getBorderCrossing()
    {
        Integer v = (Integer)this.getOptionalFieldValue(FLD_borderCrossing);
        return (v != null)? v.intValue() : 0;
    }

    public void setBorderCrossing(int flags)
    {
        this.setOptionalFieldValue(FLD_borderCrossing, flags);
    }

    // ---

    public long getLastBorderCrossTime()
    {
        Long v = (Long)this.getOptionalFieldValue(FLD_lastBorderCrossTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void setLastBorderCrossTime(long v)
    {
        this.setOptionalFieldValue(FLD_lastBorderCrossTime, v);
    }

    // Device/Asset specific data above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // DataTransport specific data below

    public String getUniqueID()
    {
        String v = (String)this.getFieldValue(FLD_uniqueID);
        return StringTools.trim(v);
    }

    public void setUniqueID(String v)
    {
        this.setFieldValue(FLD_uniqueID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getDeviceCode()
    {
        String v = (String)this.getFieldValue(FLD_deviceCode);  // serverID
        return StringTools.trim(v);
    }

    public void setDeviceCode(String v)
    {
        this.setFieldValue(FLD_deviceCode, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getDeviceType()
    {
        String v = (String)this.getFieldValue(FLD_deviceType);
        return StringTools.trim(v);
    }

    public void setDeviceType(String v)
    {
        this.setFieldValue(FLD_deviceType, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public boolean hasPushpinID()
    {
        return !StringTools.isBlank(this.getPushpinID());
    }

    public String getPushpinID()
    {
        String v = (String)this.getFieldValue(FLD_pushpinID);
        return StringTools.trim(v);
    }

    public void setPushpinID(String v)
    {
        this.setFieldValue(FLD_pushpinID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public void setMapLegend(String legend)
    {
        //
    }

    public String getMapLegend()
    {
        return "";
    }

    // ------------------------------------------------------------------------

    public String getSerialNumber()
    {
        String v = (String)this.getFieldValue(FLD_serialNumber);
        return StringTools.trim(v);
    }

    public void setSerialNumber(String v)
    {
        this.setFieldValue(FLD_serialNumber, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getSimPhoneNumber()
    {
        String v = (String)this.getFieldValue(FLD_simPhoneNumber);
        return StringTools.trim(v);
    }

    public void setSimPhoneNumber(String v)
    {
        this.setFieldValue(FLD_simPhoneNumber, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getSmsEmail()
    {
        String v = (String)this.getFieldValue(FLD_smsEmail);
        return StringTools.trim(v);
    }

    public void setSmsEmail(String v)
    {
        this.setFieldValue(FLD_smsEmail, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getImeiNumber()
    {
        String v = (String)this.getFieldValue(FLD_imeiNumber);
        return StringTools.trim(v);
    }

    public void setImeiNumber(String v)
    {
        this.setFieldValue(FLD_imeiNumber, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public long getLastInputState()
    {
        Long v = (Long)this.getFieldValue(FLD_lastInputState);
        return (v != null)? v.longValue() : 0L;
    }

    public void setLastInputState(long v)
    {
        this.setFieldValue(FLD_lastInputState, v & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------------

    public int getIgnitionIndex()
    {
        Integer v = (Integer)this.getFieldValue(FLD_ignitionIndex);
        return (v != null)? v.intValue() : -1;
    }

    public void setIgnitionIndex(int v)
    {
        this.setFieldValue(FLD_ignitionIndex, ((v >= 0)? v : -1));
    }

    public int[] getIgnitionStatusCodes()
    {
        int ndx = this.getIgnitionIndex();
        if (ndx >= 0) {
            int scOFF = StatusCodes.GetDigitalInputStatusCode(ndx, false);
            int scON  = StatusCodes.GetDigitalInputStatusCode(ndx, true );
            if (scOFF != StatusCodes.STATUS_NONE) {
                return new int[] { scOFF, scON };
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------

    public String getCodeVersion()
    {
        String v = (String)this.getFieldValue(FLD_codeVersion);
        return StringTools.trim(v);
    }

    public void setCodeVersion(String v)
    {
        this.setFieldValue(FLD_codeVersion, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getFeatureSet()
    {
        String v = (String)this.getFieldValue(FLD_featureSet);
        return StringTools.trim(v);
    }

    public void setFeatureSet(String v)
    {
        this.setFieldValue(FLD_featureSet, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public DTIPAddrList getIpAddressValid()
    {
        DTIPAddrList v = (DTIPAddrList)this.getFieldValue(FLD_ipAddressValid);
        return v; // May return null!!
    }

    public void setIpAddressValid(DTIPAddrList v)
    {
        this.setFieldValue(FLD_ipAddressValid, v);
    }

    public void setIpAddressValid(String v)
    {
        this.setIpAddressValid((v != null)? new DTIPAddrList(v) : null);
    }

    public boolean isValidIPAddress(String ipAddr)
    {
        DTIPAddrList ipList = this.getIpAddressValid();
        if ((ipList == null) || ipList.isEmpty()) {
            return true;
        } else
        if (!ipList.isMatch(ipAddr)) {
            return false;
        } else {
            return true;
        }
    }

    // ------------------------------------------------------------------------

    public DTIPAddress getIpAddressCurrent()
    {
        DTIPAddress v = (DTIPAddress)this.getFieldValue(FLD_ipAddressCurrent);
        return v; // May return null!!
    }

    public void setIpAddressCurrent(DTIPAddress v)
    {
        this.setFieldValue(FLD_ipAddressCurrent, v);
    }

    public void setIpAddressCurrent(String v)
    {
        this.setIpAddressCurrent((v != null)? new DTIPAddress(v) : null);
    }

    // ------------------------------------------------------------------------

    public int getRemotePortCurrent()
    {
        Integer v = (Integer)this.getFieldValue(FLD_remotePortCurrent);
        return (v != null)? v.intValue() : 0;
    }

    public void setRemotePortCurrent(int v)
    {
        this.setFieldValue(FLD_remotePortCurrent, ((v > 0)? v : 0));
    }

    // ------------------------------------------------------------------------

    public double getLastValidLatitude()
    {
        return this.getOptionalFieldValue(FLD_lastValidLatitude, 0.0);
    }

    public void setLastValidLatitude(double v)
    {
        this.setOptionalFieldValue(FLD_lastValidLatitude, v);
    }

    public double getLastValidLongitude()
    {
        return this.getOptionalFieldValue(FLD_lastValidLongitude, 0.0);
    }

    public void setLastValidLongitude(double v)
    {
        this.setOptionalFieldValue(FLD_lastValidLongitude, v);
    }

    public GeoPoint getLastValidLocation()
    {
        // returns null if invalid
        double lat = this.getLastValidLatitude();
        double lon = this.getLastValidLongitude();
        return GeoPoint.isValid(lat,lon)? new GeoPoint(lat,lon) : null;
    }

    public GeoPoint getLastValidLocation(boolean tryLastEvent)
    {
        GeoPoint gp = this.getLastValidLocation();
        if ((gp == null) && tryLastEvent) {
            try {
                EventData lastEv = this.getLastEvent(); // valid GPS only
                if ((lastEv != null) && lastEv.isValidGeoPoint()) {
                    gp = lastEv.getGeoPoint();
                    this.setLastValidLocation(gp);
                    this.setLastGPSTimestamp(lastEv.getTimestamp());
                    if (this.getLastOdometerKM() <= 0.0) {
                        this.setLastOdometerKM(lastEv.getOdometerKM()); // may still be '0.0'
                    }
                }
            } catch (DBException dbe) {
                // ignore error
            }
        }
        return gp;
    }

    public void setLastValidLocation(GeoPoint gp)
    {
        if ((gp != null) && gp.isValid()) {
            this.setLastValidLatitude(gp.getLatitude());
            this.setLastValidLongitude(gp.getLongitude());
        } else {
            this.setLastValidLatitude(0.0);
            this.setLastValidLongitude(0.0);
        }
    }

    public double getMetersToLastValidLocation(GeoPoint gp)
    {
        if (GeoPoint.isValid(gp)) {
            GeoPoint lastValidLoc = this.getLastValidLocation(true);
            if (lastValidLoc != null) {
                return gp.metersToPoint(lastValidLoc);
            }
        }
        return -1.0;
    }

    public boolean isNearLastValidLocation(GeoPoint gp, double meters)
    {
        if (meters > 0.0) {
            double deltaM = this.getMetersToLastValidLocation(gp);
            return ((deltaM >= 0.0) && (deltaM < meters)); // false if gp is invalid
        } else {
            return false;
        }
    }

    // ------------------------------------------------------------------------

    private static final Integer GEOZONE_ARRIVE = new Integer(StatusCodes.STATUS_GEOFENCE_ARRIVE);
    private static final Integer GEOZONE_DEPART = new Integer(StatusCodes.STATUS_GEOFENCE_DEPART);

    public static class GeozoneTransition
    {
        private Integer code = null;
        private Geozone zone = null;
        public GeozoneTransition(Integer code, Geozone zone) {
            this.code = code;
            this.zone = zone;
        }
        public int getStatusCode() {
            return this.code.intValue();
        }
        public Geozone getGeozone() {
            return this.zone;
        }
        public String getGeozoneID() {
            return this.zone.getGeozoneID();
        }
        public String getGeozoneDescription() {
            return this.zone.getDescription();
        }
        public long adjustFixtime(long fixtime) {
            if (GEOZONE_DEPART.equals(this.code)) {
                return fixtime - 2L; // make sure depart occurs before arrive
            } else {
                return fixtime - 1L;
            }
        }
    }

    public GeozoneTransition[] getGeozoneTransition(GeoPoint geoPoint)
    {
        if (GeoPoint.isValid(geoPoint)) {
            String accountID = this.getAccountID();
            GeoPoint prevGP  = this.getLastValidLocation(true);
            Geozone prevZone = (prevGP != null)? Geozone.getGeozone(accountID, null, prevGP, false) : null;
            Geozone thisZone = Geozone.getGeozone(accountID, null, geoPoint, false);
            if ((prevZone == null) && (thisZone != null)) {
                // Arrive only
                if (thisZone.isArrivalZone()) {
                    return new GeozoneTransition[] {
                        new GeozoneTransition(GEOZONE_ARRIVE, thisZone)
                    };
                }
            } else
            if ((prevZone != null) && (thisZone == null)) {
                // Depart only
                if (prevZone.isDepartureZone()) {
                    return new GeozoneTransition[] {
                        new GeozoneTransition(GEOZONE_DEPART, prevZone)
                    };
                }
            } else
            if ((prevZone != null) && (thisZone != null) && !prevZone.getGeozoneID().equals(thisZone.getGeozoneID())) {
                // Depart, then Arrive
                boolean isDepart = prevZone.isDepartureZone();
                boolean isArrive = thisZone.isArrivalZone();
                if (isDepart || isArrive) {
                    GeozoneTransition list[] = new GeozoneTransition[(isDepart?1:0) + (isArrive?1:0)];
                    int n = 0;
                    if (isDepart) {
                        list[n++] = new GeozoneTransition(GEOZONE_DEPART, prevZone);
                    }
                    if (isArrive) {
                        list[n++] = new GeozoneTransition(GEOZONE_ARRIVE, thisZone);
                    }
                    return list;
                }
            }
        }
        return null;

    }

    // ------------------------------------------------------------------------

    public long getLastGPSTimestamp()
    {
        Long v = (Long)this.getFieldValue(FLD_lastGPSTimestamp);
        return (v != null)? v.longValue() : 0L;
    }

    public void setLastGPSTimestamp(long v)
    {
        this.setFieldValue(FLD_lastGPSTimestamp, v);
    }

    // ------------------------------------------------------------------------

    /* return true if the Device record support the last odometer fields */
    public static boolean supportsLastOdometer()
    {
        return Device.getFactory().hasField(FLD_lastOdometerKM);
    }

    public double getLastOdometerKM()
    {
        return this.getOptionalFieldValue(FLD_lastOdometerKM, 0.0);
    }

    /*
    public double getLastOdometerKM(boolean tryLastEvent)
    {
        double odomKM = this.getLastOdometerKM();
        if (odomKM > 0.0) {
            return odomKM;
        } else
        if (tryLastEvent) {
            try {
                EventData lastEv = this.getLastEvent();
                if ((lastEv != null) && lastEv.isValidGeoPoint()) {
                    odomKM = lastEv.getOdometerKM(); // may be 0
                    this.setLastOdometerKM(odomKM);                         // FLD_lastOdometerKM
                    if (this.getLastValidLocation() == null) {
                        this.setLastValidLocation(lastEv.getGeoPoint());    // FLD_lastValidLocation
                        this.setLastGPSTimestamp(lastEv.getTimestamp());    // FLD_lastGPSTimestamp
                    }
                    return odomKM;
                } else {
                    return 0.0;
                }
            } catch (DBException dbe) {
                // ignore error
                return 0.0;
            }
        } else {
            return 0.0;
        }
    }
    */

    public void setLastOdometerKM(double v)
    {
        if (v < Device.MAX_DEVICE_ODOM_KM) {
            this.setOptionalFieldValue(FLD_lastOdometerKM, v);
        }
    }

    public double getNextOdometerKM(GeoPoint geoPoint)
    {
        GeoPoint lastValidLoc = this.getLastValidLocation(true); // try last event
        double odomKM = this.getLastOdometerKM(); // only try cached value
        if (GeoPoint.isValid(geoPoint) && (lastValidLoc != null)) {
            odomKM += geoPoint.kilometersToPoint(lastValidLoc);
        }
        return odomKM;
    }

    public double adjustOdometerKM(double v)
    {
        double lastOdomKM = this.getLastOdometerKM();
        if (v < lastOdomKM) {
            return lastOdomKM;
        } else
        if (v >= Device.MAX_DEVICE_ODOM_KM) {
            return lastOdomKM;
        } else {
            return v;
        }
    }

    // ------------------------------------------------------------------------

    public double getOdometerOffsetKM()
    {
        return this.getOptionalFieldValue(FLD_odometerOffsetKM, 0.0);
    }

    public void setOdometerOffsetKM(double v)
    {
        if (v < Device.MAX_DEVICE_ODOM_KM) {
            this.setOptionalFieldValue(FLD_odometerOffsetKM, v);
        }
    }

    // ------------------------------------------------------------------------

    /* not yet fully implemented */
    public String getPingCommandURI()
    {
        String v = (String)this.getFieldValue(FLD_pingCommandURI);
        return StringTools.trim(v);
    }

    /* not yet fully implemented */
    public void setPingCommandURI(String v)
    {
        // valid options:
        //   tcp://192.168.11.11:21500
        //   udp://192.168.11.11:31400
        //   sms://9165551212
        //   smtp://9165551212@example.com
        this.setFieldValue(FLD_pingCommandURI, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getPendingPingCommand()
    {
        String v = (String)this.getFieldValue(FLD_pendingPingCommand);
        return StringTools.trim(v);
    }

    public void setPendingPingCommand(String v)
    {
        this.setFieldValue(FLD_pendingPingCommand, StringTools.trim(v));
    }

    public boolean hasPendingPingCommand()
    {
        return !StringTools.isBlank(this.getPendingPingCommand());
    }

    // ------------------------------------------------------------------------

    public long getLastPingTime()
    {
        Long v = (Long)this.getFieldValue(FLD_lastPingTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void _setLastPingTime(long v)
    {
        this.setFieldValue(FLD_lastPingTime, v);
    }

    public void setLastPingTime(long v)
    {
        this._setLastPingTime(v);
        if (this.transport != null) {
            this.transport._setLastPingTime(v);
        }
    }

    // ------------------------------------------------------------------------

    public boolean getExpectAck()
    {
        Boolean v = (Boolean)this.getFieldValue(FLD_expectAck);
        return (v != null)? v.booleanValue() : true;
    }

    public void _setExpectAck(boolean v)
    {
        this.setFieldValue(FLD_expectAck, v);
    }

    public void setExpectAck(boolean v)
    {
        this._setExpectAck(v);
        if (this.transport != null) {
            this.transport._setExpectAck(v);
        }
    }

    // ------------------------------------------------------------------------

    public String getLastAckCommand()
    {
        String v = (String)this.getFieldValue(FLD_lastAckCommand);
        return StringTools.trim(v);
    }

    public void setLastAckCommand(String v)
    {
        this.setFieldValue(FLD_lastAckCommand, StringTools.trim(v));
    }

    public boolean isExpectingCommandAck()
    {
        return this.getExpectAck() && (this.getLastAckTime() <= 0L);
    }

    public boolean setExpectCommandAck(DCServerConfig.Command command, String cmdStr)
    {

        /* no command */
        if ((command == null) && StringTools.isBlank(cmdStr)) {
            // both 'command' and 'cmdStr' are null/blank
            return false;
        }

        /* no command specified? */
        if ((command != null) && !command.getExpectAck()) {
            Print.logWarn("Not expecting an ACK for Command: " + command.getName());
            return false;
        }

        /* already waiting for an ACK? */
        if (this.isExpectingCommandAck()) {
            // we are already expecting an ACK
            Print.logWarn("Already expecting an ACK for: " + this.getLastAckCommand());
        }

        /* save ACK command */
        try {
            String cs = !StringTools.isBlank(cmdStr)? cmdStr : command.getCommandString();
            this.setExpectAck(true);
            this.setLastAckCommand(cs);
            this.setLastAckTime(0L);
            this.update(FLD_expectAck, FLD_lastAckCommand, FLD_lastAckTime);
            Print.logInfo("ACK expected for command: " + cs);
            return true;
        } catch (DBException dbe) {
            Print.logException("Unable to set Device.lastAck...", dbe);
            return false;
        }

    }

    public boolean clearExpectCommandAck(DCServerConfig.Command command)
    {

        /* not expecting an ACK? */
        if (!this.isExpectingCommandAck()) {
            Print.logInfo("Device is not expecting an ACK");
            return false;
        }

        /* clear ACK command */
        try {
            String lastAckCmd = this.getLastAckCommand();
            this.setExpectAck(false);
            //this.setLastAckCommand("");
            this.setLastAckTime(DateTime.getCurrentTimeSec());
            this.update(FLD_expectAck, FLD_lastAckTime);
            Print.logInfo("ACK received for command: " + lastAckCmd);
            return true;
        } catch (DBException dbe) {
            Print.logException("Unable to set Device.lastAck...", dbe);
            return false;
        }

    }

    // ------------------------------------------------------------------------

    public long getLastAckTime()
    {
        Long v = (Long)this.getFieldValue(FLD_lastAckTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void _setLastAckTime(long v)
    {
        this.setFieldValue(FLD_lastAckTime, v);
    }

    public void setLastAckTime(long v)
    {
        this._setLastAckTime(v);
        if (this.transport != null) {
            this.transport._setLastAckTime(v);
        }
    }

    // ------------------------------------------------------------------------

    public int getTotalPingCount()
    {
        Integer v = (Integer)this.getFieldValue(FLD_totalPingCount);
        return (v != null)? v.intValue() : 0;
    }

    public void _setTotalPingCount(int v)
    {
        this.setFieldValue(FLD_totalPingCount, v);
    }

    public void setTotalPingCount(int v)
    {
        this._setTotalPingCount(v);
        if (this.transport != null) {
            this.transport._setTotalPingCount(v);
        }
    }

    public boolean incrementPingCount(long pingTime, boolean update)
    {
        this.setTotalPingCount(this.getTotalPingCount() + 1);
        if (pingTime > 0L) {
            this.setLastPingTime(pingTime);
        }
        if (update) {
            try {
                this.update( // may throw DBException
                    Device.FLD_lastPingTime,
                    Device.FLD_totalPingCount);
            } catch (DBException dbe) {
                Print.logException("Unable to update 'ping' count", dbe);
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------

    public int getMaxPingCount()
    {
        Integer v = (Integer)this.getFieldValue(FLD_maxPingCount);
        return (v != null)? v.intValue() : 0;
    }

    public void _setMaxPingCount(int v)
    {
        this.setFieldValue(FLD_maxPingCount, v);
    }

    public void setMaxPingCount(int v)
    {
        this._setMaxPingCount(v);
        if (this.transport != null) {
            this.transport._setMaxPingCount(v);
        }
    }

    // ------------------------------------------------------------------------

    /* DCS: General Config Mask (usage defined by specific DCS */
    public long getDcsConfigMask()
    {
        Long v = (Long)this.getOptionalFieldValue(FLD_dcsConfigMask);
        return (v != null)? v.longValue() : 0L;
    }

    public void setDcsConfigMask(long v)
    {
        this.setOptionalFieldValue(FLD_dcsConfigMask, v);
    }

    public String getDcsConfigString()
    {
        String v = (String)this.getOptionalFieldValue(FLD_dcsConfigString);
        return StringTools.trim(v);
    }

    public void setDcsConfigString(String v)
    {
        this.setOptionalFieldValue(FLD_dcsConfigString, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public boolean getSupportsDMTP()
    {
        Boolean v = (Boolean)this.getFieldValue(FLD_supportsDMTP);
        return (v != null)? v.booleanValue() : true;
    }

    public void setSupportsDMTP(boolean v)
    {
        this.setFieldValue(FLD_supportsDMTP, v);
    }

    public boolean supportsDMTP()
    {
        return this.getSupportsDMTP();
    }

    // ------------------------------------------------------------------------

    public int getSupportedEncodings()
    {
        Integer v = (Integer)this.getFieldValue(FLD_supportedEncodings);
        return (v != null)? v.intValue() : (int)Encodings.BINARY.getLongValue();
    }

    public void setSupportedEncodings(int v)
    {
        v &= (int)EnumTools.getValueMask(Encodings.class);
        if (v == 0) { v = (int)Encodings.BINARY.getLongValue(); }
        this.setFieldValue(FLD_supportedEncodings, v);
    }

    // ------------------------------------------------------------------------

    public int getUnitLimitInterval() // Minutes
    {
        Integer v = (Integer)this.getFieldValue(FLD_unitLimitInterval);
        return (v != null)? v.intValue() : 0;
    }

    public void setUnitLimitInterval(int v) // Minutes
    {
        this.setFieldValue(FLD_unitLimitInterval, v);
    }

    // ------------------------------------------------------------------------

    public int getMaxAllowedEvents()
    {
        Integer v = (Integer)this.getFieldValue(FLD_maxAllowedEvents);
        return (v != null)? v.intValue() : 1;
    }

    public void setMaxAllowedEvents(int v)
    {
        this.setFieldValue(FLD_maxAllowedEvents, v);
    }

    // ------------------------------------------------------------------------

    public DTProfileMask getTotalProfileMask()
    {
        DTProfileMask v = (DTProfileMask)this.getFieldValue(FLD_totalProfileMask);
        return v;
    }

    public void setTotalProfileMask(DTProfileMask v)
    {
        this.setFieldValue(FLD_totalProfileMask, v);
    }

    // ------------------------------------------------------------------------

    /* OpenDMTP: Maximum Total Connections per Interval */
    // The effective maximum value for this field is defined by the following:
    //   (org.opendmtp.server.base.ValidateConnections.BITS_PER_MINUTE_MASK * this.getUnitLimitIntervalMinutes())
    public int getTotalMaxConn()
    {
        Integer v = (Integer)this.getFieldValue(FLD_totalMaxConn);
        return (v != null)? v.intValue() : 0;
    }

    public void setTotalMaxConn(int v)
    {
        this.setFieldValue(FLD_totalMaxConn, v);
    }

    // ------------------------------------------------------------------------

    /* OpenDMTP: Maximum Total Connections per Minute */
    // The effective maximum value for this field is defined by the constant:
    //   "org.opendmtp.server.base.ValidateConnections.BITS_PER_MINUTE_MASK"
    public int getTotalMaxConnPerMin()
    {
        Integer v = (Integer)this.getFieldValue(FLD_totalMaxConnPerMin);
        return (v != null)? v.intValue() : 0;
    }

    public void setTotalMaxConnPerMin(int v)
    {
        this.setFieldValue(FLD_totalMaxConnPerMin, v);
    }

    // ------------------------------------------------------------------------

    public DTProfileMask getDuplexProfileMask()
    {
        DTProfileMask v = (DTProfileMask)this.getFieldValue(FLD_duplexProfileMask);
        return v;
    }

    public void setDuplexProfileMask(DTProfileMask v)
    {
        this.setFieldValue(FLD_duplexProfileMask, v);
    }

    // ------------------------------------------------------------------------

    /* OpenDMTP: Maximum Duplex Connections per Interval */
    // The effective maximum value for this field is defined by the following:
    //   (org.opendmtp.server.base.ValidateConnections.BITS_PER_MINUTE_MASK * this.getUnitLimitIntervalMinutes())
    public int getDuplexMaxConn()
    {
        Integer v = (Integer)this.getFieldValue(FLD_duplexMaxConn);
        return (v != null)? v.intValue() : 0;
    }

    public void setDuplexMaxConn(int v)
    {
        this.setFieldValue(FLD_duplexMaxConn, v);
    }

    // ------------------------------------------------------------------------

    /* OpenDMTP: Maximum Duplex Connections per Minute */
    // The effective maximum value for this field is defined by the constant:
    //   "org.opendmtp.server.base.ValidateConnections.BITS_PER_MINUTE_MASK"
    public int getDuplexMaxConnPerMin()
    {
        Integer v = (Integer)this.getFieldValue(FLD_duplexMaxConnPerMin);
        return (v != null)? v.intValue() : 0;
    }

    public void setDuplexMaxConnPerMin(int v)
    {
        this.setFieldValue(FLD_duplexMaxConnPerMin, v);
    }

    // ------------------------------------------------------------------------

    public long getLastDuplexConnectTime()
    {
        Long v = (Long)this.getFieldValue(FLD_lastDuplexConnectTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void _setLastDuplexConnectTime(long v)
    {
        this.setFieldValue(FLD_lastDuplexConnectTime, v);
    }

    public void setLastDuplexConnectTime(long v)
    {
        this._setLastDuplexConnectTime(v);
        if (this.transport != null) {
            this.transport._setLastDuplexConnectTime(v);
        }
    }

    // ------------------------------------------------------------------------

    public long getLastTotalConnectTime()
    {
        Long v = (Long)this.getFieldValue(FLD_lastTotalConnectTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void _setLastTotalConnectTime(long v)
    {
        this.setFieldValue(FLD_lastTotalConnectTime, v);
    }

    public void setLastTotalConnectTime(long v)
    {
        this._setLastTotalConnectTime(v);
        if (this.transport != null) {
            this.transport._setLastTotalConnectTime(v);
        }
    }

    public long getLastConnectTime()
    {
        return this.getLastTotalConnectTime();
    }

    public void setLastConnectTime(long v)
    {
        this.setLastTotalConnectTime(v);
    }

    // ------------------------------------------------------------------------

    public double getFixedLatitude()
    {
        return this.getOptionalFieldValue(FLD_fixedLatitude, 0.0);
    }

    public void setFixedLatitude(double v)
    {
        this.setOptionalFieldValue(FLD_fixedLatitude, v);
    }

    public double getFixedLongitude()
    {
        return this.getOptionalFieldValue(FLD_fixedLongitude, 0.0);
    }

    public void setFixedLongitude(double v)
    {
        this.setOptionalFieldValue(FLD_fixedLongitude, v);
    }

    public boolean hasFixedLocation()
    {
        // we assume FLD_fixedLongitude exists if FLD_fixedLatitude exists
        return this.hasField(FLD_fixedLatitude); // && this.isValidFixedLocation();
    }

    public boolean isValidFixedLocation()
    {
        return GeoPoint.isValid(this.getFixedLatitude(), this.getFixedLongitude());
    }

    public GeoPoint getFixedLocation()
    {
        return new GeoPoint(this.getFixedLatitude(), this.getFixedLongitude());
    }

    // ------------------------------------------------------------------------

    public String getFixedAddress()
    {
        String v = StringTools.trim((String)this.getFieldValue(FLD_fixedAddress));
        return v;
    }

    public void setFixedAddress(String v)
    {
        this.setFieldValue(FLD_fixedAddress, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getFixedContactPhone()
    {
        String v = StringTools.trim((String)this.getFieldValue(FLD_fixedContactPhone));
        return v;
    }

    public void setFixedContactPhone(String v)
    {
        this.setFieldValue(FLD_fixedContactPhone, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public long getFixedServiceTime()
    {
        Long v = (Long)this.getOptionalFieldValue(FLD_fixedServiceTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void setFixedServiceTime(long v)
    {
        this.setOptionalFieldValue(FLD_fixedServiceTime, v);
    }

    // ------------------------------------------------------------------------

    /* get the current active GeoCorridor */
    public String getActiveCorridor()
    {
        String v = (String)this.getFieldValue(FLD_activeCorridor);
        return StringTools.trim(v);
    }

    /* set the current active GeoCorridor */
    public void setActiveCorridor(String v)
    {
        this.setFieldValue(FLD_activeCorridor, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    /* return true if the Device record support the Periodic Maintenance fields */
    public static boolean supportsPeriodicMaintenance()
    {
        return Device.getFactory().hasField(FLD_maintOdometerKM0);
    }

    /* return true if within the specified number of kilometers of the next maintenance interval */
    public boolean isMaintenanceDueKM0(double deltaKM)
    {
        if (Device.supportsPeriodicMaintenance()) {
            double odomKM = this.getLastOdometerKM();
            if (odomKM > 0.0) {
                double lastKM = this.getMaintOdometerKM0();
                double intvKM = this.getMaintIntervalKM0();
                if ((odomKM + deltaKM) >= (lastKM + intvKM)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------

    /* get the maintenance service odometer value */
    public double getMaintOdometerKM0()
    {
        return this.getOptionalFieldValue(FLD_maintOdometerKM0, 0.0);
    }

    /* set the maintenance service odometer value */
    public void setMaintOdometerKM0(double v)
    {
        if (v < Device.MAX_DEVICE_ODOM_KM) {
            this.setOptionalFieldValue(FLD_maintOdometerKM0, ((v >= 0.0)? v : 0.0));
        }
    }

    // ---------------

    /* get the maintenance service limit */
    public double getMaintIntervalKM0()
    {
        return this.getOptionalFieldValue(FLD_maintIntervalKM0, 0.0);
    }

    /* set the maintenance service limit */
    public void setMaintIntervalKM0(double v)
    {
        this.setOptionalFieldValue(FLD_maintIntervalKM0, v);
    }

    // ------------------------------------------------------------------------

    private RTProperties customAttrRTP = null;
    private Collection<String> customAttrKeys = null;

    /* get the custom attributes as a String */
    public String getCustomAttributes()
    {
        String v = (String)this.getOptionalFieldValue(FLD_customAttributes);
        return StringTools.trim(v);
    }

    /* set the current active GeoCorridor */
    public void setCustomAttributes(String v)
    {
        this.setOptionalFieldValue(FLD_customAttributes, StringTools.trim(v));
        this.customAttrRTP  = null;
        this.customAttrKeys = null;
    }

    /* get custom attributes a an RTProperties */
    public RTProperties getCustomAttributesRTP()
    {
        if (this.customAttrRTP == null) {
            this.customAttrRTP = new RTProperties(this.getCustomAttributes());
        }
        return this.customAttrRTP;
    }

    /* get the custom attributes keys */
    public Collection<String> getCustomAttributeKeys()
    {
        if (this.customAttrKeys == null) {
            this.customAttrKeys = this.getCustomAttributesRTP().getPropertyKeys(null);
        }
        return this.customAttrKeys;
    }

    /* get the custom attributes as a String */
    public String getCustomAttribute(String key)
    {
        return this.getCustomAttributesRTP().getString(key,null);
    }

    /* get the custom attributes as a String */
    public String setCustomAttribute(String key, String value)
    {
        return this.getCustomAttributesRTP().getString(key,value);
    }

    // Bean access fields above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* overridden to set default values */
    public void setCreationDefaultValues()
    {
        this.setIsActive(true);
        this.setDescription(NEW_DEVICE_NAME_ + " [" + this.getDeviceID() + "]");
        this.setIgnitionIndex(-1);
        // Rules-Engine Allow Notification
        if (Device.hasRuleFactory()) {
            this.setAllowNotify(true);
        }
        // DataTransport attributes below
        this.setSupportedEncodings(Transport.DEFAULT_ENCODING);
        this.setTotalMaxConn(Transport.DEFAULT_TOTAL_MAX_CONNECTIONS);
        this.setDuplexMaxConn(Transport.DEFAULT_DUPLEX_MAX_CONNECTIONS);
        this.setUnitLimitInterval(Transport.DEFAULT_UNIT_LIMIT_INTERVAL_MIN); // Minutes
        this.setTotalMaxConnPerMin(Transport.DEFAULT_TOTAL_MAX_CONNECTIONS_PER_MIN);
        this.setDuplexMaxConnPerMin(Transport.DEFAULT_DUPLEX_MAX_CONNECTIONS_PER_MIN);
        this.setMaxAllowedEvents(Transport.DEFAULT_MAX_ALLOWED_EVENTS);
        // other defaults
        super.setRuntimeDefaultValues();
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* DataTransport interface */
    public String getAssocAccountID()
    {
        return this.getAccountID();
    }

    /* DataTransport interface */
    public String getAssocDeviceID()
    {
        return this.getDeviceID();
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* return a list of supported commands */
    public DCServerConfig getDCServerConfig()
    {
        return DCServerFactory.getServerConfig(this.getDeviceCode());
    }

    /* return a list of supported commands */
    public Map<String,String> getSupportedCommands(BasicPrivateLabel privLabel, User user, String type)
    {
        DCServerConfig dcs = this.getDCServerConfig();
        return (dcs != null)? dcs.getCommandDescriptionMap(privLabel,user,type) : null;
    }

    // ------------------------------------------------------------------------

    /* return true if 'ping' is supported */
    public boolean isPingSupported(BasicPrivateLabel privLabel, User user)
    {
        
        /* check ACL */
        DCServerConfig dcs = this.getDCServerConfig();
        if ((privLabel != null) && (dcs != null) && !privLabel.hasWriteAccess(user, dcs.getCommandsAclName())) {
            return false;
        }

        /* PingDispatcher */
        if (Device.hasPingDispatcher()) {
            return Device.getPingDispatcher().isPingSupported(this);
        } else {
            return false;
        }
        
    }

    /* dispatch device command */
    public boolean sendDeviceCommand(String cmdType, String cmdName, String cmdArgs[])
    {
        
        /* DCServerConfig */
        DCServerConfig dcs = this.getDCServerConfig();
        if (dcs != null) {
            // a DCServerConfig is defined
            RTProperties resp = DCServerFactory.sendServerCommand(this, cmdType, cmdName, cmdArgs);
            Print.logInfo("Ping Response: " + resp);
            return DCServerFactory.isCommandResultOK(resp);
        }
        
        /* PingDispatcher */
        if (Device.hasPingDispatcher()) {
            boolean sent = Device.getPingDispatcher().sendDeviceCommand(this, cmdType, cmdName, cmdArgs);
            return sent;
        } else {
            return false;
        }

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private static boolean  ENABLE_LOAD_TESTING = true;
    private static Object   loadTestingLock     = new Object();
    private static DateTime loadTestingTime     = null;
    private static long     loadTestingCount    = 0L;

    /* get the number of events between the specified timestamps (inclusive) */
    public long getEventCount(long timeStart, long timeEnd)
        throws DBException
    {
        long count = EventData.getRecordCount(
            this.getAccountID(), this.getDeviceID(),
            timeStart, timeEnd);
        return count;
    }

    /* get the total number of events for this Device/Vehicle */
    public long getEventCount()
        throws DBException
    {
        return this.getEventCount(-1L, -1L);
    }

    /* insert event into EventData table */
    public boolean insertEventData(final EventData evdb)
    {
        // Notes:
        // 1) This EventData record is populated, but hasn't been saved
        // 2) This Device record _should_ still contain old/last field values

        /* invalid EventData? */
        if (evdb == null) {
            //Print.logError("EventData is null");
            return false;
        }

        /* set device */
        evdb.setDevice(this);

        /* Transport ID */
        evdb.setTransportID(this.getTransportID());

        /* check for future timestamp */
        int futureDateAction = Device.futureEventDateAction();
        if (futureDateAction != FUTURE_DATE_DISABLED) {
            long maxFutureSec = Device.futureEventDateMaximumSec();
            if (maxFutureSec > 0L) { // must be greater-than 0
                long maxTime = DateTime.getCurrentTimeSec() + maxFutureSec;
                long evTime  = evdb.getTimestamp();
                if (evTime > maxTime) {
                    if (futureDateAction == FUTURE_DATE_IGNORE) {
                        // ignore this record
                        Print.logWarn("Invalid EventData time: "+new DateTime(evTime)+" [ingoring record]");
                        return false;
                    } else
                    if (futureDateAction == FUTURE_DATE_TRUNCATE) {
                        // truncate date
                        Print.logWarn("Invalid EventData time: "+new DateTime(evTime)+" [truncate "+new DateTime(maxTime)+"]");
                        evdb.setTimestamp(maxTime);
                    } else {
                        // should not occur (just continue)
                        Print.logWarn("Invalid EventData time: "+new DateTime(evTime)+" [unexpected action "+futureDateAction+"]");
                    }
                }
            }
        }

        /* no status code? */
        if (evdb.getStatusCode() == StatusCodes.STATUS_NONE) {
            // '0' status codes are quietly consumed.
            if (ENABLE_LOAD_TESTING) {
                // This section is for load testing.
                if (loadTestingTime == null) {
                    synchronized (loadTestingLock) {
                        if (loadTestingTime == null) { loadTestingTime = new DateTime(/*tz*/); }
                    }
                }
                long deltaSec = DateTime.getCurrentTimeSec() - loadTestingTime.getTimeSec();
                if (deltaSec > 60) {
                    // reset every minute
                    synchronized (loadTestingLock) {
                        loadTestingTime = new DateTime(/*tz*/);
                        loadTestingCount = 0L;
                        deltaSec = 0L;
                    }
                }
                loadTestingCount++;
                double eps = (deltaSec > 0L)? ((double)loadTestingCount / (double)deltaSec) : loadTestingCount;
                if ((loadTestingCount % 50) == 0) {
                    System.err.println("EventData LoadTest (" + eps + " ev/sec)");
                }
            }
            return true;
        }

        /* extended EventData record update */
        int extUpdate = EXT_UPDATE_NONE;

        /* set geozone / reverse-geocode */
        try {
            if (evdb.updateAddress(true/*fastOnly*/)) {
                Print.logInfo("EventData address: [%s/%s] %s: %s",
                    this.getAccountID(), this.getDeviceID(),
                    evdb.getGeoPoint().toString(), evdb.getAddress());
            }
        } catch (SlowOperationException soe) {
            // The address update has not been performed because the operation would have
            // taken too long [per 'isFastOperation()' method in ReverseGeocodeProvider instance].
            // This address update will need to be queued for background processing.
            extUpdate |= EXT_UPDATE_ADDRESS;
        }

        /* stateline border-crossing check */
        //if (this.getBorderCrossing() == Device.BorderCrossingState.ON.getIntValue()) {
        //   // border-crossing is always considered a slow operation
        //   //extUpdate |= EXT_UPDATE_BORDER;
        //}

        /* save EventData record */
        try {
            evdb.save(); // insert();
            // may be re-saved after deferred reverse-geocode
        } catch (DBException dbe) {
            // save failed
            Print.logError("EventData save failed: " + dbe);
            return false;
        }

        /* check rules and return */
        if (extUpdate != EXT_UPDATE_NONE) {
            // queue for background processing
            final int extUpd = extUpdate;
            Runnable job = new Runnable() {
                public void run() {
                    Device.this._postEventInsertionProcessing(evdb, extUpd);
                }
            };
            BackgroundThreadPool.run(job);
            Print.logDebug("Address update queued for background operation");
        } else {
            // check event rules now and perform appropriate action if necessary
            //this.checkEventRules(evdb);
        }

        /* check rules */
        if (this.checkEventRules(evdb)) {
            // Fields may have changed: (NOTE: not yet saved)
            //   FLD_lastNotifyTime
            //   FLD_lastNotifyCode
        }

        /* update fields to reflect this event */
        // NOTE: not yet saved!
        if (evdb.isValidGeoPoint()) {
            // update last valid location
            this.setLastValidLatitude(evdb.getLatitude());   // FLD_lastValidLatitude
            this.setLastValidLongitude(evdb.getLongitude()); // FLD_lastValidLongitude
            this.setLastGPSTimestamp(evdb.getTimestamp());   // FLD_lastGPSTimestamp
        }
        if (evdb.getOdometerKM() > 0.0) {
            this.setLastOdometerKM(evdb.getOdometerKM());    // FLD_lastOdometerKM
        }

        /* return success */
        return true;

    }

    /* background post-processing: address reverse-geocoding, and rule checking */
    private void _postEventInsertionProcessing(EventData evdb, int extUpdate)
    {
        boolean update = false;

        /* address */
        if ((extUpdate & EXT_UPDATE_ADDRESS) != 0) {
            try {
                if (evdb.updateAddress(false/*!fastOnly*/)) {
                    update = true;
                }
            } catch (SlowOperationException soe) {
                // this will not occur ('fastOnly' is false)
            }
        }

        /* stateline border-crossing check here */
        // check border-crossing in nightly cron

        /* update */
        if (update) {
            try {
                evdb.update(
                    EventData.FLD_geozoneID,
                    EventData.FLD_geozoneIndex,
                    EventData.FLD_address,
                    EventData.FLD_streetAddress,
                    EventData.FLD_city,
                    EventData.FLD_postalCode,
                    EventData.FLD_country,
                    EventData.FLD_subdivision,
                    EventData.FLD_speedLimitKPH,
                    EventData.FLD_isTollRoad
                );
                Print.logInfo("EventData address: [%s/%s] %s: %s",
                    this.getAccountID(), this.getDeviceID(),
                    evdb.getGeoPoint().toString(), evdb.getAddress());
            } catch (DBException dbe) {
                Print.logError("EventData update error: " + dbe);
            }
        }

        /* rule check */
        //Cannot defer rule check to here!!!
        //Rule triggers may be based on values which may be changing in the Device record,
        //which will have already changed by the time we get here!
        //this.checkEventRules(evdb);

    }

    // ------------------------------------------------------------------------

    private Set<String> _createUpdateFieldsSet()
    {
        return ListTools.toSet(new String[] {
            Device.FLD_deviceCode,              // serverID
            Device.FLD_imeiNumber,
            Device.FLD_ipAddressCurrent,
            Device.FLD_remotePortCurrent,
            Device.FLD_lastInputState,
            Device.FLD_lastValidLatitude,
            Device.FLD_lastValidLongitude,
            Device.FLD_lastGPSTimestamp,
            Device.FLD_lastOdometerKM,
            Device.FLD_lastTotalConnectTime,
            Device.FLD_lastNotifyTime,          // optional field
            Device.FLD_lastNotifyCode           // optional field
        },null);
    }

    public void updateChangedEventFields()
        throws DBException
    {
        this.updateChangedEventFields((String[])null);
    }

    public void updateChangedEventFields(Set<String> flds)
        throws DBException
    {
        Set<String> updFields = _createUpdateFieldsSet();
        if (flds != null) {
            ListTools.toSet(flds, updFields);
        }
        this.update(updFields);
    }

    public void updateChangedEventFields(String... flds)
        throws DBException
    {
        Set<String> updFields = _createUpdateFieldsSet();
        if (flds != null) {
            ListTools.toSet(flds, updFields);
        }
        this.update(updFields);
    }

    // ------------------------------------------------------------------------

    /* save connection statistics */
    public void insertSessionStatistic(long startTime, String ipAddr, boolean isDuplex, long bytesRead, long bytesWritten, long evtsRecv)
    {
        // save session statistics
        SessionStatsFactory csf = Device.getSessionStatsFactory();
        if (csf != null) {
            try {
                csf.addSessionStatistic(this,startTime,ipAddr,isDuplex,bytesRead,bytesWritten,evtsRecv);
            } catch (DBException dbe) {
                Print.logError("Session statistic: " + dbe);
            }
        }
    }

    // ------------------------------------------------------------------------

    /* validate rule selector syntax */
    public boolean checkSelectorSyntax(String selector)
    {
        if (StringTools.isBlank(selector)) {
            // a blank selector should always be valid
            return true;
        } else {
            RuleFactory ruleFact = Device.getRuleFactory();
            if (ruleFact != null) {
                return ruleFact.checkSelectorSyntax(selector);
            } else {
                Print.logWarn("No RuleFactory defined");
                return false;
            }
        }
    }

    /* check new event for specific rule triggers */
    // Any special event rules checking should go here.
    protected boolean checkEventRules(EventData event)
    {

        /* no event? */
        if (event == null) {
            // we have no event, don't bother with the rest
            return false;
        }

        /* set device */
        // This provides an optimization so that any Account/Device requests on the EventData
        // record won't have to explicitly query the database to retrieve the Account/Device.
        event.setDevice(this);

        /* Entity attach/detach (if installed) */
        if (Device.hasEntityManager()) {
            Device.getEntityManager().insertEntityChange(event);
        }

        /* check for rule factory */
        String ruleSelector = this.getNotifySelector();
        RuleFactory ruleFact = Device.getRuleFactory();
        if (ruleFact == null) {
            /* display message if a rule-selector has been specified */
            if (!ruleSelector.equals("")) {
                Print.logWarn("No RuleFactory to process rule: " + ruleSelector);
            } else {
                //Print.logWarn("RuleFactory not installed ...");
            }
            return false;
        }

        /* notification not allowed for this device? */
        if (!this.allowNotify()) {
            /* display message if a rule-selector has been specified */
            if (!StringTools.isBlank(ruleSelector)) {
                Print.logWarn("Notification disallowed [selector = " + ruleSelector + "]");
            }
            return false;
        }

        /* check local email notification selector */
        // This executes a single selector-based rule.
        boolean didTrigger = false;
        if (!StringTools.isBlank(ruleSelector)) {
            Print.logDebug("Processing Device rule: " + ruleSelector);
            int actionMask = ruleFact.executeSelector(ruleSelector, event);
            if ((actionMask >= 0) && (actionMask != RuleFactory.ACTION_NONE)) {
                didTrigger = true;
                if ((actionMask & RuleFactory.ACTION_SAVE_LAST) != 0) {
                    // not yet saved
                    this.setLastNotifyTime(event.getTimestamp());     // FLD_lastNotifyTime
                    this.setLastNotifyCode(event.getStatusCode());    // FLD_lastNotifyCode
                }
            }
        }

        /* test statusCode rule/action list */
        // This method allows for a complete check of multiple rules
        int accumActionMask = ruleFact.executeRules(event);
        if ((accumActionMask >= 0) && (accumActionMask != RuleFactory.ACTION_NONE)) {
            didTrigger = true;
            if ((accumActionMask & RuleFactory.ACTION_SAVE_LAST) != 0) {
                // not yet saved
                this.setLastNotifyTime(event.getTimestamp());     // FLD_lastNotifyTime
                this.setLastNotifyCode(event.getStatusCode());    // FLD_lastNotifyCode
            }
        }

        /* return trigger state */
        return didTrigger;

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Optimization for StatusCode description lookup (typically for map display)
    // This is a temporary cache of StatusCodes that are used for Events which
    // are either displayed on a map, or in a report.  Access to this cache does not need
    // to be synchronized since all status code lookups will occur within the same thread.
    // This cache is temporary and is garbage collected along with this Device record.

    private Map<Integer,StatusCode> statusCodeMap = null;

    /* get cached status code */
    public StatusCode getStatusCode(int code)
    {

        /* create map */
        if (this.statusCodeMap == null) {
            this.statusCodeMap = new HashMap<Integer,StatusCode>();
        }

        /* already in cache */
        Integer codeKey = new Integer(code);
        if (this.statusCodeMap.containsKey(codeKey)) {
            return this.statusCodeMap.get(codeKey); // may return null;
        }

        /* add to cache */
        String accountID = this.getAccountID();
        String deviceID  = this.getDeviceID();
        StatusCode sc = StatusCode.findStatusCode(accountID, deviceID, code);
        this.statusCodeMap.put(new Integer(code), sc);
        return sc; // may be null;

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* get entities currently attached to deviceID */
    public String[] getAttachedEntityDescriptions()
    {
        if (Device.hasEntityManager()) {
            String attEnt[] = null;
            try {
                String acctID = this.getAccountID();
                String devID  = this.getDeviceID();
                attEnt = Device.getEntityManager().getAttachedEntityDescriptions(acctID, devID);
            } catch (DBException dbe) {
                Print.logException("Error reading Device Entities", dbe);
            }
            return attEnt;
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* return events in specified time range */
    public EventData[] getRangeEvents(
        long timeStart, long timeEnd,
        int statusCodes[],
        boolean validGPS,
        EventData.LimitType limitType, long limit)
        throws DBException
    {

        /* get data */
        EventData ev[] = EventData.getRangeEvents(
            this.getAccountID(), this.getDeviceID(),
            timeStart, timeEnd,
            statusCodes,
            validGPS,
            limitType, limit, true,
            null/*additionalSelect*/);

        /* apply current Device to all EventData records */
        if (ev != null) {
            for (int i = 0; i < ev.length; i++) {
                ev[i].setDevice(this);
            }
        }
        return ev;

    }

    /* return events in specified time range */
    public EventData[] getRangeEvents(
        long timeStart, long timeEnd,
        boolean validGPS,
        EventData.LimitType limitType, long limit)
        throws DBException
    {
        return this.getRangeEvents(timeStart, timeEnd, null, validGPS, limitType, limit);
    }

    /* return the most recent 'limit' events */
    public EventData[] getLatestEvents(long limit, boolean validGPS)
        throws DBException
    {
        long timeStart = -1L;
        long timeEnd   = -1L;
        return this.getRangeEvents(timeStart, timeEnd, null, validGPS, EventData.LimitType.LAST, limit);
    }

    /* return the most recent 'limit' events */
    public EventData getFirstEvent(long startTime, boolean validGPS)
        throws DBException
    {
        long endTime = -1L;
        EventData ev[] = EventData.getRangeEvents(
            this.getAccountID(), this.getDeviceID(),
            startTime, endTime,
            null/*statusCodes[]*/,
            validGPS,
            EventData.LimitType.FIRST, 1, true,
            null/*additionalSelect*/);
        if ((ev == null) || (ev.length <= 0)) {
            return null;
        } else {
            ev[0].setDevice(this);
            return ev[0];
        }
    }

    /* return the last event <= specified endTime */
    public EventData getLastEvent(long endTime, boolean validGPS)
        throws DBException
    {
        long startTime = -1L;
        EventData ev[] = EventData.getRangeEvents(
            this.getAccountID(), this.getDeviceID(),
            startTime, endTime,
            null/*statusCodes[]*/,
            validGPS,
            EventData.LimitType.LAST, 1, true,
            null/*additionalSelect*/);
        if ((ev == null) || (ev.length <= 0)) {
            return null;
        } else {
            ev[0].setDevice(this);
            return ev[0];
        }
    }

    /* return the last event <= specified endTime */
    public EventData getLastEvent()
        throws DBException
    {
        // TODO: cache this event?
        return this.getLastEvent(-1L, true);
    }

    // ------------------------------------------------------------------------

    public interface EventDataHandler
    {
        public void handleEventDataRecord(EventData ev);
    }
    
    public void reprocessEventDataRecords(long timeStart, long timeEnd, final EventDataHandler edh)
        throws DBException
    {
        EventData.getRangeEvents(
            this.getAccountID(), this.getDeviceID(),
            timeStart, timeEnd,
            null/*statusCodes*/,
            false/*validGPS*/,
            EventData.LimitType.LAST, -1L/*limit*/, true/*ascending*/,
            null/*additionalSelect*/,
            new DBRecordHandler() {
                public int handleDBRecord(DBRecord rcd) throws DBException {
                    edh.handleEventDataRecord((EventData)rcd);
                    return DBRecordHandler.DBRH_SKIP;
                }
            });
    }

    // ------------------------------------------------------------------------

    /**
    *** Save this Device to db storage
    **/
    public void save()
        throws DBException
    {
        super.save();
        if (this.transport != null) { this.transport.save(); }
    }

    // ------------------------------------------------------------------------

    /**
    *** Return a String representation of this Device
    *** @return The String representation
    **/
    public String toString()
    {
        return this.getAccountID() + "/" + this.getDeviceID();
    }

    // ------------------------------------------------------------------------

    private Transport transport = null;

    /**
    *** Sets the Transport for this Device
    *** @param xport  The Transport instance
    **/
    public void setTransport(Transport xport)
    {
        this.transport = xport;
    }

    /**
    *** Gets the Transport-ID for this Device (if any)
    *** @return The Transport-ID for this Device, or an empty string is not defined
    **/
    public String getTransportID()
    {
        return (this.transport != null)? this.transport.getTransportID() : "";
    }

    /**
    *** Gets the DataTransport for this Device
    *** @return The DataTransport for this Device
    **/
    public DataTransport getDataTransport()
    {
        return (this.transport != null)? (DataTransport)this.transport : (DataTransport)this;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    public static boolean exists(String acctID, String devID)
        throws DBException // if error occurs while testing existence
    {
        if ((acctID != null) && (devID != null)) {
            Device.Key devKey = new Device.Key(acctID, devID);
            return devKey.exists();
        }
        return false;
    }

    // ------------------------------------------------------------------------

    /**
    *** This method is called by "Transport.loadDeviceByUniqueID(String)" to load a Device
    *** within a Device Communication Server, based on a Unique-ID.
    *** @param uniqId  The Unique-ID of the device (ie. IMEI, ESN, Serial#, etc)
    *** @return The loaded Device instance, or null if the Device was not found
    *** @throws DBException if a database error occurs
    **/
    public static Device loadDeviceByUniqueID(String uniqId)
        throws DBException
    {

        /* invalid id? */
        if ((uniqId == null) || uniqId.equals("")) {
            return null; // just say it doesn't exist
        }

        /* read device for unique-id */
        Device dev = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {

            /* select */
            // DBSelect: SELECT * FROM Device WHERE (uniqueID='unique')
            DBSelect<Device> dsel = new DBSelect<Device>(Device.getFactory());
            DBWhere dwh = dsel.createDBWhere();
            dsel.setWhere(dwh.WHERE_(dwh.EQ(Device.FLD_uniqueID,uniqId)));
            dsel.setLimit(2);
            // Note: The index on the column FLD_uniqueID does not enforce uniqueness
            // (since null/empty values are allowed and needed)

            /* get record */
            stmt = DBConnection.getDefaultConnection().execute(dsel.toString());
            rs = stmt.getResultSet();
            while (rs.next()) {
                String acctId = rs.getString(FLD_accountID);
                String devId  = rs.getString(FLD_deviceID);
                dev = new Device(new Device.Key(acctId,devId));
                dev.setFieldValues(rs);
                if (rs.next()) {
                    Print.logError("Found multiple occurances of this unique-id: " + uniqId);
                }
                break; // only one record
            }
            // it's possible at this point that we haven't even read 1 device

        } catch (SQLException sqe) {
            throw new DBException("Getting Device unique-id: " + uniqId, sqe);
        } finally {
            if (rs   != null) { try { rs.close();   } catch (Throwable t) {} }
            if (stmt != null) { try { stmt.close(); } catch (Throwable t) {} }
        }

        /* return device */
        // Note: 'dev' may be null if it wasn't found
        return dev;

    }

    /**
    *** This method is called by "Transport.loadDeviceByTransportID(...)" to load a Device
    *** within a Device Communication Server, based on the Account and Device IDs.
    *** @param account  The Account instance represetning the owning account
    *** @param devID    The Device-ID
    *** @return The loaded Device instance, or null if the Device was not found
    *** @throws DBException if a database error occurs
    **/
    public static Device loadDeviceByName(Account account, String devID)
        throws DBException
    {
        Device dev = Device.getDevice(account, devID);
        return dev;
    }

    // ------------------------------------------------------------------------

    /* get device (may return null) */
    public static Device getDevice(Account account, String devID)
        throws DBException
    {
        if ((account != null) && (devID != null)) {
            String acctID = account.getAccountID();
            Device.Key key = new Device.Key(acctID, devID);
            if (key.exists()) {
                Device dev = key.getDBRecord(true);
                dev.setAccount(account);
                return dev;
            } else {
                // device does not exist
                return null;
            }
        } else {
            return null; // just say it doesn't exist
        }
    }

    /* get device */
    // Note: does NOT return null (throws exception if not found)
    public static Device getDevice(Account account, String devID, boolean create)
        throws DBException
    {

        /* account-id specified? */
        if (account == null) {
            throw new DBNotFoundException("Account not specified.");
        }
        String acctID = account.getAccountID();

        /* device-id specified? */
        if (StringTools.isBlank(devID)) {
            throw new DBNotFoundException("Device-ID not specified for account: " + acctID);
        }

        /* get/create */
        Device dev = null;
        Device.Key devKey = new Device.Key(acctID, devID);
        if (!devKey.exists()) {
            if (create) {
                dev = devKey.getDBRecord();
                dev.setAccount(account);
                dev.setCreationDefaultValues();
                return dev; // not yet saved!
            } else {
                throw new DBNotFoundException("Device-ID does not exists: " + devKey);
            }
        } else
        if (create) {
            // we've been asked to create the device, and it already exists
            throw new DBAlreadyExistsException("Device-ID already exists '" + devKey + "'");
        } else {
            dev = Device.getDevice(account, devID);
            if (dev == null) {
                throw new DBException("Unable to read existing Device-ID: " + devKey);
            }
            return dev;
        }

    }

    // ------------------------------------------------------------------------

    public static Device createNewDevice(Account account, String devID, String uniqueID)
        throws DBException
    {
        if ((account != null) && !StringTools.isBlank(devID)) {
            Device dev = Device.getDevice(account, devID, true); // does not return null
            if (!StringTools.isBlank(uniqueID)) {
                dev.setUniqueID(uniqueID);
            }
            dev.save();
            return dev;
        } else {
            throw new DBException("Invalid Account/DeviceID specified");
        }
    }

    // ------------------------------------------------------------------------

    /* return list of all Devices owned by the specified Account (NOT SCALABLE) */
    // does not return null
    public static OrderedSet<String> getDeviceIDsForAccount(String acctId, User userAuth)
        throws DBException
    {
        return Device.getDeviceIDsForAccount(acctId, userAuth, -1L);
    }

    /* return list of all Devices owned by the specified Account (NOT SCALABLE) */
    // does not return null
    public static OrderedSet<String> getDeviceIDsForAccount(String acctId, User userAuth, long limit)
        throws DBException
    {

        /* no account specified? */
        if (StringTools.isBlank(acctId)) {
            if (userAuth != null) {
                acctId = userAuth.getAccountID();
            } else {
                Print.logError("Account not specified!");
                return new OrderedSet<String>();
            }
        }

        /* read devices for account */
        OrderedSet<String> devList = new OrderedSet<String>();
        Statement stmt = null;
        ResultSet rs = null;
        try {

            /* select */
            // DBSelect: SELECT * FROM Device WHERE (accountID='acct') ORDER BY deviceID
            DBSelect<Device> dsel = new DBSelect<Device>(Device.getFactory());
            dsel.setSelectedFields(Device.FLD_deviceID);
            DBWhere dwh = dsel.createDBWhere();
            dsel.setWhere(dwh.WHERE(dwh.EQ(Device.FLD_accountID,acctId)));
            dsel.setOrderByFields(Device.FLD_deviceID);
            dsel.setLimit(limit);

            /* get records */
            stmt = DBConnection.getDefaultConnection().execute(dsel.toString());
            rs = stmt.getResultSet();
            while (rs.next()) {
                String devId = rs.getString(Device.FLD_deviceID);
                if ((userAuth == null) || userAuth.isAuthorizedDevice(devId)) {
                    devList.add(devId);
                }
            }

        } catch (SQLException sqe) {
            throw new DBException("Getting Account Device List", sqe);
        } finally {
            if (rs   != null) { try { rs.close();   } catch (Throwable t) {} }
            if (stmt != null) { try { stmt.close(); } catch (Throwable t) {} }
        }

        /* return list */
        return devList;

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Main admin entry point below

    private static final String ARG_ACCOUNT[]   = new String[] { "account" , "acct"  , "a" };
    private static final String ARG_DEVICE[]    = new String[] { "device"  , "dev"   , "d" };
    private static final String ARG_UNIQID[]    = new String[] { "uniqueid", "unique", "uniq", "uid", "u" };
    private static final String ARG_CREATE[]    = new String[] { "create"              };
    private static final String ARG_EDIT[]      = new String[] { "edit"    , "ed"      };
    private static final String ARG_EDITALL[]   = new String[] { "editall" , "eda"     }; 
    private static final String ARG_DELETE[]    = new String[] { "delete"              };
    private static final String ARG_EVENTS[]    = new String[] { "events"  , "ev"      };
    private static final String ARG_FORMAT[]    = new String[] { "format"  , "fmt"     };
  //private static final String ARG_SETPROP[]   = new String[] { "setprop" };
    private static final String ARG_INSERT[]    = new String[] { "insertGP" };
    private static final String ARG_CLEARACK[]  = new String[] { "clearAck" };
    private static final String ARG_MAINTKM[]   = new String[] { "maint"   , "maintkm" };

    private static String _fmtDevID(String acctID, String devID)
    {
        return acctID + "/" + devID;
    }

    private static void usage()
    {
        Print.sysPrintln("Usage:");
        Print.sysPrintln("  java ... " + Device.class.getName() + " {options}");
        Print.sysPrintln("Common Options:");
        Print.sysPrintln("  -account=<id>   Acount ID which owns Device");
        Print.sysPrintln("  -device=<id>    Device ID to create/edit");
        Print.sysPrintln("  -uniqueid=<id>  Unique ID to create/edit");
        Print.sysPrintln("  -create         Create a new Device");
        Print.sysPrintln("  -edit           Edit an existing (or newly created) Device");
        Print.sysPrintln("  -delete         Delete specified Device");
        Print.sysPrintln("  -events=<limit> Retrieve the last <limit> events");
        System.exit(1);
    }

    public static void main(String args[])
    {
        DBConfig.cmdLineInit(args,true);  // main
        String acctID  = RTConfig.getString(ARG_ACCOUNT, "");
        String devID   = RTConfig.getString(ARG_DEVICE , "");
        String uniqID  = RTConfig.getString(ARG_UNIQID , "");

        /* account-id specified? */
        if (StringTools.isBlank(acctID)) {
            Print.logError("Account-ID not specified.");
            usage();
        }

        /* get account */
        Account acct = null;
        try {
            acct = Account.getAccount(acctID); // may throw DBException
            if (acct == null) {
                Print.logError("Account-ID does not exist: " + acctID);
                usage();
            }
        } catch (DBException dbe) {
            Print.logException("Error loading Account: " + acctID, dbe);
            //dbe.printException();
            System.exit(99);
        }
        BasicPrivateLabel privLabel = acct.getPrivateLabel();

        /* device-id specified? */
        if ((devID == null) || devID.equals("")) {
            Print.logError("Device-ID not specified.");
            usage();
        }

        /* device exists? */
        boolean deviceExists = false;
        try {
            deviceExists = Device.exists(acctID, devID);
        } catch (DBException dbe) {
            Print.logError("Error determining if Device exists: " + _fmtDevID(acctID,devID));
            System.exit(99);
        }

        /* option count */
        int opts = 0;

        /* delete */
        if (RTConfig.getBoolean(ARG_DELETE, false) && !acctID.equals("") && !devID.equals("")) {
            opts++;
            if (!deviceExists) {
                Print.logWarn("Device does not exist: " + _fmtDevID(acctID,devID));
                Print.logWarn("Continuing with delete process ...");
            }
            try {
                Device.Key devKey = new Device.Key(acctID, devID);
                devKey.delete(true); // also delete dependencies
                Print.logInfo("Device deleted: " + _fmtDevID(acctID,devID));
                deviceExists = false;
            } catch (DBException dbe) {
                Print.logError("Error deleting Device: " + _fmtDevID(acctID,devID));
                dbe.printException();
                System.exit(99);
            }
            System.exit(0);
        }

        /* create */
        if (RTConfig.getBoolean(ARG_CREATE, false)) {
            opts++;
            if (deviceExists) {
                Print.logWarn("Device already exists: " + _fmtDevID(acctID,devID));
            } else {
                try {
                    Device.createNewDevice(acct, devID, uniqID);
                    Print.logInfo("Created Device: " + _fmtDevID(acctID,devID));
                    deviceExists = true;
                } catch (DBException dbe) {
                    Print.logError("Error creating Device: " + _fmtDevID(acctID,devID));
                    dbe.printException();
                    System.exit(99);
                }
            }
        }

        /* edit */
        if (RTConfig.getBoolean(ARG_EDIT,false) || RTConfig.getBoolean(ARG_EDITALL,false)) {
            opts++;
            if (!deviceExists) {
                Print.logError("Device does not exist: " + _fmtDevID(acctID,devID));
            } else {
                try {
                    boolean allFlds = RTConfig.getBoolean(ARG_EDITALL,false);
                    Device device = Device.getDevice(acct, devID, false); // may throw DBException
                    DBEdit editor = new DBEdit(device);
                    editor.edit(allFlds); // may throw IOException
                } catch (IOException ioe) {
                    if (ioe instanceof EOFException) {
                        Print.logError("End of input");
                    } else {
                        Print.logError("IO Error");
                    }
                } catch (DBException dbe) {
                    Print.logError("Error editing Device: " + _fmtDevID(acctID,devID));
                    dbe.printException();
                }
            }
            System.exit(0);
        }

        /* events */
        if (RTConfig.hasProperty(ARG_EVENTS)) {
            opts++;
            if (!deviceExists) {
                Print.logError("Device does not exist: " + _fmtDevID(acctID,devID));
            } else {
                int limit = RTConfig.getInt(ARG_EVENTS, 10);
                int fmt   = EventUtil.parseOutputFormat(RTConfig.getString(ARG_FORMAT,null),EventUtil.FORMAT_CSV);
                try {
                    Device device = Device.getDevice(acct, devID, false); // may throw DBException
                    EventData evdata[] = device.getLatestEvents(limit,false);
                    EventUtil evUtil = EventUtil.getInstance();
                    evUtil.writeEvents((PrintWriter)null, evdata, fmt, true, privLabel);
                } catch (IOException ioe) {
                    Print.logError("IO Error");
                } catch (DBException dbe) {
                    Print.logError("Error getting events for Device: " + _fmtDevID(acctID,devID));
                    dbe.printException();
                }
            }
            System.exit(0);
        }

        /* insert GeoPoint */
        if (RTConfig.hasProperty(ARG_INSERT)) {
            opts++;
            GeoPoint gp = new GeoPoint(RTConfig.getString(ARG_INSERT,""));
            if (!deviceExists) {
                Print.logError("Device does not exist: " + _fmtDevID(acctID,devID));
                System.exit(1);
            } else
            if (!gp.isValid()) {
                Print.logError("Invalid GeoPoint: " + gp);
                System.exit(1);
            } else {
                SendMail.SetThreadModel(SendMail.THREAD_DEBUG);
                Print.sysPrintln("Account PrivateLabel: " + privLabel.getName());
                ReverseGeocodeProvider rgp = privLabel.getReverseGeocodeProvider();
                if (INSERT_REVERSEGEOCODE_REQUIRED && (rgp == null)) {
                    Print.sysPrintln("Account has no ReverseGeocodeProvider (record not inserted)");
                    System.exit(1);
                }
                Print.sysPrintln("Account ReverseGeocodeProvider: " + ((rgp!=null)?rgp.getName():"<none>"));
                if (INSERT_REVERSEGEOCODE_REQUIRED && !Account.getGeocoderMode(acct).equals(Account.GeocoderMode.FULL)) {
                    Print.sysPrintln("Overriding Account GeocoderMode to 'FULL'");
                    acct.setGeocoderMode(Account.GeocoderMode.FULL);
                }
                try {
                    Device device = Device.getDevice(acct, devID, false); // may throw DBException
                    long timestamp = DateTime.getCurrentTimeSec();
                    int statusCode = StatusCodes.STATUS_WAYMARK_0;
                    EventData.Key evKey = new EventData.Key(acctID,devID,timestamp,statusCode);
                    EventData evRcd = evKey.getDBRecord();
                    evRcd.setGeoPoint(gp);
                    evRcd.setAddress(null); // updated later
                    if (device.insertEventData(evRcd)) {
                        Print.sysPrintln("EventData record inserted ...");
                        //evRcd.setLatitude(11.0);
                        //device.insertEventData(evRcd);
                        //Print.sysPrintln("EventData record inserted (again) ...");
                    } else {
                        Print.logError("*** Unable to insert EventData record!!!");
                    }
                    BackgroundThreadPool.stopThreads();
                    if (BackgroundThreadPool.getSize() > 0) {
                        do {
                            Print.sysPrintln("Waiting for background threads to complete ...");
                            try { Thread.sleep(3000L); } catch (Throwable t) {}
                        } while (BackgroundThreadPool.getSize() > 0);
                    }
                    Print.sysPrintln("... done");
                    System.exit(0);
                } catch (DBException dbe) {
                    Print.logError("*** Error inserting EventData: " + _fmtDevID(acctID,devID));
                    dbe.printException();
                    System.exit(1);
                }
            }
        }

        /* clear any pending ACK */
        if (RTConfig.hasProperty(ARG_CLEARACK)) {
            opts++;
            if (!deviceExists) {
                Print.logError("Device does not exist: " + _fmtDevID(acctID,devID));
                System.exit(98);
            }
            try {
                Device device = Device.getDevice(acct, devID, false); // may throw DBException
                boolean didClear = device.clearExpectCommandAck(null);
                Print.logInfo("Cleared Device ACK: " + didClear);
                System.exit(0);
            } catch (DBException dbe) {
                Print.logError("Error clearing ACK for Device: " + _fmtDevID(acctID,devID));
                dbe.printException();
                System.exit(99);
            }
        }

        /* periodic maintenance check */
        if (RTConfig.hasProperty(ARG_MAINTKM)) {
            opts++;
            // device exists?
            if (!deviceExists) {
                Print.logError("Device does not exist: " + _fmtDevID(acctID,devID));
                System.exit(98);
            }
            // get device
            Device device = null;
            try {
                device = Device.getDevice(acct, devID, false); // may throw DBException
            } catch (DBException dbe) {
                Print.logError("Error clearing ACK for Device: " + _fmtDevID(acctID,devID));
                dbe.printException();
                System.exit(99);
            }
            // odometer/interval
            double odomKM   = device.getLastOdometerKM();
            double intervKM = device.getMaintIntervalKM0();
            if ((odomKM <= 0.0) || (intervKM <= 0.0)) {
                System.exit(2); // no odometer/interval
            }
            // check service interval
            double maintKM = device.getMaintOdometerKM0();
            if (odomKM >= (maintKM + intervKM)) {
                // send email
                Print.logInfo("Service Interval due for " + device.getDescription());
                String frEmail = SendMail.getUserFromEmailAddress();
                String toEmail = RTConfig.getString(ARG_MAINTKM, "");
                if (!StringTools.isBlank(frEmail) && !StringTools.isBlank(toEmail)) {
                    I18N   i18n = I18N.getI18N(Device.class, acct.getLocale());
                    String text = i18n.getString("Device.serviceMaint.dueFor","Periodic Maintenance due for {0}",device.getDescription());
                    String odom = i18n.getString("Device.serviceMaint.odometer","Odometer");
                    String subj = text;
                    String body = text + "\n" +
                        odom + ": " + odomKM + "\n" +
                        "\n";
                    try {
                        Print.logInfo("From:"     + frEmail);
                        Print.logInfo("To:"       + toEmail);
                        Print.logInfo("Subject: " + subj);
                        Print.logInfo("Body:\n"   + body);
                        Print.logInfo("Sending email ...");
                        SendMail.SetThreadModel(SendMail.THREAD_CURRENT);
                        SendMail.send(frEmail,toEmail,null,null,subj,body,null);
                        System.exit(0); // success
                    } catch (Throwable t) { // NoClassDefFoundException, ClassNotFoundException
                        // this will fail if JavaMail support for SendMail is not available.
                        Print.logWarn("SendMail error: " + t);
                        System.exit(97);
                    }
                }
                System.exit(1);
            } else {
                System.exit(2); // no interval
            }
        }

        /* no options specified */
        if (opts == 0) {
            Print.logWarn("Missing options ...");
            usage();
        }

    }

}
