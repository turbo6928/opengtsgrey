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
//  2006/04/02  Martin D. Flynn
//     -Added field formatting support for CSV output
//  2007/01/25  Martin D. Flynn
//     -Moved to "OpenGTS"
//     -Added new 'FLD_address' field
//     -Added 'FLD_thermoAverage#' fields.
//  2007/02/26  Martin D. Flynn
//     -Added 'FLD_odometerKM' table column ('FLD_distanceKM' is used for
//      'tripometer' purposes).
//  2007/02/28  Martin D. Flynn
//     -Added column 'FLD_horzAccuracy' (meters)
//     -Removed columns FLD_geofenceID2, FLD_thermoAverage#, & FLD_topSpeedKPH.  
//      For specific custom solutions these can easily be added back, but for a  
//      general solution they are not necessary.
//  2007/03/11  Martin D. Flynn
//     -Added convenience methods 'getSpeedString' and 'getHeadingString'.
//     -Added 'statusCodes[]' and 'additionalSelect' arguments to 'getRangeEvents'
//      method.
//  2007/03/25  Martin D. Flynn
//     -Changed FLD_geofenceID1 to FLD_geozoneIndex, and added FLD_geozoneID
//     -Moved to 'org.opengts.db.tables'
//  2007/05/06  Martin D. Flynn
//     -Added 'FLD_creationTime' column support.
//  2007/06/13  Martin D. Flynn
//     -Added 'FLD_subdivision' column support (state/province/etc).
//  2007/07/14  Martin D. Flynn
//     -Added various optional fields/columns
//  2007/07/27  Martin D. Flynn
//     -Added custom/optional column 'FLD_driver'
//  2007/09/16  Martin D. Flynn
//     -Added 'getFieldValueString' method to return a formatted String 
//      representation of the specified field.
//     -Integrated DBSelect
//  2007/11/28  Martin D. Flynn
//     -Added columns FLD_brakeGForce, FLD_city, FLD_postalCode
//     -"getTimestampString()" now returns a time based on the Account TimeZone.
//     -Apply 'Departed' geozone description for STATUS_GEOFENCE_DEPART events.
//  2007/01/10  Martin D. Flynn
//     -Added method 'countRangeEvents(...)' to return the number of events matching  
//      the specified criteria.
//  2008/02/04  Martin D. Flynn
//     -Added custom/optional column 'FLD_fuelTotal', 'FLD_fuelIdle', 'FLD_engineRpm'
//  2008/02/17  Martin D. Flynn
//     -Added column 'FLD_inputMask'
//  2008/02/21  Martin D. Flynn
//     -Moved J1708/J1587 encoding/decoding to 'org.opengts.dbtools.DTJ1708Fault'
//  2008/03/12  Martin D. Flynn
//     -Added additional date/time key values for 'getFieldValueString' method.
//  2008/03/28  Martin D. Flynn
//     -Incorporate "DBRecord.select(DBSelect,...) method
//  2008/04/11  Martin D. Flynn
//     -Added status code icon index lookup to "getMapIconIndex(...)"
//  2008/05/14  Martin D. Flynn
//     -Added FLD_country, FLD_stateProvince, FLD_streetAddress
//  2008/05/20  Martin D. Flynn
//     -Added message to assist in determining reason for lack of ReverseGeocoding
//  2008/06/20  Martin D. Flynn
//     -Moved custom field initialization to StartupInit.
//     -EventData record now ignores invalid field references (ie. no displayed errors).
//  2008/07/08  Martin D. Flynn
//     -Added field FLD_costCenter to 'CustomFieldInfo' group.
//     -Rearranged fields/columns to reduce the size of the basic record structure.
//  2008/09/12  Martin D. Flynn
//     -Added field/column FLD_satelliteCount, FLD_batteryLevel
//  2008/10/16  Martin D. Flynn
//     -Modified "getDefaultMapIconIndex" to use the 'iconKeys' table to look up the
//      custom icon index.
//  2008/12/01  Martin D. Flynn
//     -'getDefaultMapIconIndex' now returns Device pushpinID for fleet maps.
//     -Added KEY_HEADING to 'getFieldValueString(...)' support.
//  2009/02/20  Martin D. Flynn
//     -Added field FLD_vertAccuracy
//  2009/05/01  Martin D. Flynn
//     -Added fields FLD_speedLimitKPH, FLD_isTollRoad
//  2009/07/01  Martin D. Flynn
//     -Renamed "getMapIconIndex(...)" to "getPushpinIconIndex(...)"
//  2009/11/01  Martin D. Flynn
//     -Changed 'FLD_driver' to 'FLD_driverID', and 'FLD_entity' to 'FLD_entityID'
//  2009/12/16  Martin D. Flynn
//     -Added field FLD_driverMessage, FLD_jobNumber to 'CustomFieldInfo' group.
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

public class EventData
    extends DeviceRecord<EventData>
    implements EventDataProvider, GeoPointProvider
{

    // ------------------------------------------------------------------------

    /* static initializers */
    static {
        EventData.getDeviceDescriptionComparator();
    }

    // ------------------------------------------------------------------------

    public enum LimitType {
        FIRST,
        LAST
    };

    // ------------------------------------------------------------------------

    public  static final double  INVALID_TEMPERATURE    = -9999.0;
    public  static final double  TEMPERATURE_LIMIT      = 126.0;    // degrees C

    // ------------------------------------------------------------------------
    // standard map icons (see "getPushpinIconIndex")

    /* color pushpins */
    // This represents the standard color "position" for the first 10 pushpin icons
    // The ordering is established in the method "PushpinIcon._DefaultPushpinIconMap()"
    public  static final int     ICON_PUSHPIN_BLACK     = 0;
    public  static final int     ICON_PUSHPIN_BROWN     = 1;
    public  static final int     ICON_PUSHPIN_RED       = 2;
    public  static final int     ICON_PUSHPIN_ORANGE    = 3;
    public  static final int     ICON_PUSHPIN_YELLOW    = 4;
    public  static final int     ICON_PUSHPIN_GREEN     = 5;
    public  static final int     ICON_PUSHPIN_BLUE      = 6;
    public  static final int     ICON_PUSHPIN_PURPLE    = 7;
    public  static final int     ICON_PUSHPIN_GRAY      = 8;
    public  static final int     ICON_PUSHPIN_WHITE     = 9;
 
    public static int getPushpinIconIndex(String val, OrderedSet<String> iconKeys, int dft)
    {
        if (val == null) { 
            return dft; 
        } else
        if (iconKeys != null) {
            int ndx = iconKeys.indexOf(val);
            return (ndx >= 0)? ndx : dft;
        } else {
            // 'iconKeys' should not be null, however, if it is, this will return the index
            // of the standard colors.
            if (val.equalsIgnoreCase("black" )) { return ICON_PUSHPIN_BLACK ; }
            if (val.equalsIgnoreCase("brown" )) { return ICON_PUSHPIN_BROWN ; }
            if (val.equalsIgnoreCase("red"   )) { return ICON_PUSHPIN_RED   ; }
            if (val.equalsIgnoreCase("orange")) { return ICON_PUSHPIN_ORANGE; }
            if (val.equalsIgnoreCase("yellow")) { return ICON_PUSHPIN_YELLOW; }
            if (val.equalsIgnoreCase("green" )) { return ICON_PUSHPIN_GREEN ; }
            if (val.equalsIgnoreCase("blue"  )) { return ICON_PUSHPIN_BLUE  ; }
            if (val.equalsIgnoreCase("purple")) { return ICON_PUSHPIN_PURPLE; }
            if (val.equalsIgnoreCase("gray"  )) { return ICON_PUSHPIN_GRAY  ; }
            if (val.equalsIgnoreCase("white" )) { return ICON_PUSHPIN_WHITE ; }
            return dft;
        }
    }

    // ------------------------------------------------------------------------

    public static final EventData[] EMPTY_ARRAY         = new EventData[0];

    // ------------------------------------------------------------------------
    // GPS fix type

    public enum GPSFixType implements EnumTools.StringLocale, EnumTools.IntValue {
        UNKNOWN             (0, I18N.getString(EventData.class,"EventData.gpsFix.unknown", "Unknown")),
        NONE                (1, I18N.getString(EventData.class,"EventData.gpsFix.none"   , "None"   )),
        n2D                 (2, I18N.getString(EventData.class,"EventData.gpsFix.2D"     , "2D"     )),
        n3D                 (3, I18N.getString(EventData.class,"EventData.gpsFix.3D"     , "3D"     ));
        private int         vv = 0;
        private I18N.Text   aa = null;
        GPSFixType(int v, I18N.Text a)          { vv=v; aa=a; }
        public int     getIntValue()            { return vv; }
        public String  toString()               { return aa.toString(); }
        public String  toString(Locale loc)     { return aa.toString(loc); }
    }

    public static GPSFixType getGPSFixType(EventData e)
    {
        return (e != null)? EnumTools.getValueOf(GPSFixType.class,e.getGpsFixType()) : EnumTools.getDefault(GPSFixType.class);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // SQL table definition below

    /* table name */
    public static final String _TABLE_NAME              = "EventData";
    public static String TABLE_NAME() { return DBProvider.translateTableName(_TABLE_NAME); }

    /* pseudo field definition */
    // currently only used by EventUtil
    public static final String PFLD_deviceDesc          = DBRecord.PSEUDO_FIELD_CHAR + "deviceDesc";

    /* field definition */
    // Standard fields
    public static final String FLD_timestamp            = "timestamp";              // Unix Epoch time
    public static final String FLD_statusCode           = "statusCode";
    public static final String FLD_latitude             = "latitude";
    public static final String FLD_longitude            = "longitude";
    public static final String FLD_gpsAge               = "gpsAge";                 // fix age (seconds)
    public static final String FLD_speedKPH             = "speedKPH";
    public static final String FLD_heading              = "heading";
    public static final String FLD_altitude             = "altitude";               // meters
    public static final String FLD_transportID          = Transport.FLD_transportID;
    public static final String FLD_inputMask            = "inputMask";              // bitmask
    // Address fields
    public static final String FLD_address              = "address";                // custom or reverse-geocoded address
    // Misc fields
    public static final String FLD_dataSource           = "dataSource";             // gprs, satellite, etc.
    public static final String FLD_rawData              = "rawData";                // optional
    public static final String FLD_distanceKM           = "distanceKM";             // tripometer
    public static final String FLD_odometerKM           = "odometerKM";             // vehicle odometer
    public static final String FLD_geozoneIndex         = "geozoneIndex";           // Geozone Index
    public static final String FLD_geozoneID            = Geozone.FLD_geozoneID;    // Geozone ID
    private static final DBField StandardFieldInfo[] = {
        // Key fields
        newField_accountID(true),
        newField_deviceID(true),
        // Standard fields
        new DBField(FLD_timestamp     , Long.TYPE     , DBField.TYPE_UINT32      , "Timestamp"                  , "key=true"),
        new DBField(FLD_statusCode    , Integer.TYPE  , DBField.TYPE_UINT32      , "Status Code"                , "key=true editor=statusCode format=X2"),
        new DBField(FLD_latitude      , Double.TYPE   , DBField.TYPE_DOUBLE      , "Latitude"                   , "format=#0.00000"),
        new DBField(FLD_longitude     , Double.TYPE   , DBField.TYPE_DOUBLE      , "Longitude"                  , "format=#0.00000"),
        new DBField(FLD_gpsAge        , Long.TYPE     , DBField.TYPE_UINT32      , "GPS Fix Age"                , ""),
        new DBField(FLD_speedKPH      , Double.TYPE   , DBField.TYPE_DOUBLE      , "Speed KPH"                  , "format=#0.0"),
        new DBField(FLD_heading       , Double.TYPE   , DBField.TYPE_DOUBLE      , "Heading Degrees"            , "format=#0.0"),
        new DBField(FLD_altitude      , Double.TYPE   , DBField.TYPE_DOUBLE      , "Altitude Meters"            , "format=#0.0"),
        new DBField(FLD_transportID   , String.class  , DBField.TYPE_STRING(32)  , "Transport ID"               , ""),
        new DBField(FLD_inputMask     , Long.TYPE     , DBField.TYPE_UINT32      , "Input Mask"                 , "format=X4"),
        // Address fields
        new DBField(FLD_address       , String.class  , DBField.TYPE_STRING(90)  , "Full Address"               , "utf8=true"),
        // Misc fields
        new DBField(FLD_dataSource    , String.class  , DBField.TYPE_STRING(32)  , "Data Source"                , ""),
        new DBField(FLD_rawData       , String.class  , DBField.TYPE_TEXT        , "Raw Data"                   , ""),
        new DBField(FLD_distanceKM    , Double.TYPE   , DBField.TYPE_DOUBLE      , "Distance KM"                , "format=#0.0"),
        new DBField(FLD_odometerKM    , Double.TYPE   , DBField.TYPE_DOUBLE      , "Odometer KM"                , "format=#0.0"),
        new DBField(FLD_geozoneIndex  , Long.TYPE     , DBField.TYPE_UINT32      , "Geozone Index"              , ""),
        new DBField(FLD_geozoneID     , String.class  , DBField.TYPE_STRING(32)  , "Geozone ID"                 , ""),
        // Common fields
        newField_creationTime(),
    };
    // Extra Address fields
    public static final String FLD_streetAddress        = "streetAddress";          // reverse-geocoded street address
    public static final String FLD_city                 = "city";                   // reverse-geocoded city
    public static final String FLD_stateProvince        = "stateProvince";          // reverse-geocoded state
    public static final String FLD_postalCode           = "postalCode";             // reverse-geocoded postal code
    public static final String FLD_country              = "country";                // reverse-geocoded country
    public static final String FLD_subdivision          = "subdivision";            // reverse-geocoded subdivision (ie "US/CA")
    public static final String FLD_speedLimitKPH        = "speedLimitKPH";          // reverse-geocoded speed-limit ('0' for unavailable)
    public static final String FLD_isTollRoad           = "isTollRoad";             // reverse-geocoded toll-road indicator
    public static final DBField AddressFieldInfo[] = {
        new DBField(FLD_streetAddress , String.class  , DBField.TYPE_STRING(90)  , "Street Address"             , "utf8=true"),
        new DBField(FLD_city          , String.class  , DBField.TYPE_STRING(40)  , "City"                       , "utf8=true"),
        new DBField(FLD_stateProvince , String.class  , DBField.TYPE_STRING(40)  , "State/Privince"             , "utf8=true"), 
        new DBField(FLD_postalCode    , String.class  , DBField.TYPE_STRING(16)  , "Postal Code"                , "utf8=true"),
        new DBField(FLD_country       , String.class  , DBField.TYPE_STRING(40)  , "Country"                    , "utf8=true"), 
        new DBField(FLD_subdivision   , String.class  , DBField.TYPE_STRING(32)  , "Subdivision"                , "utf8=true"),
        new DBField(FLD_speedLimitKPH , Double.TYPE   , DBField.TYPE_DOUBLE      , "Speed Limit km/h"           , ""),
        new DBField(FLD_isTollRoad    , Boolean.TYPE  , DBField.TYPE_BOOLEAN     , "Toll road Indicator"        , ""),
    };
    // Device/GPS fields
    public static final String FLD_gpsFixType           = "gpsFixType";             // fix type (0/1=None, 2=2D, 3=3D)
    public static final String FLD_horzAccuracy         = "horzAccuracy";           // horizontal accuracy (meters)
    public static final String FLD_vertAccuracy         = "vertAccuracy";           // vertical accuracy (meters)
    public static final String FLD_HDOP                 = "HDOP";                   // HDOP
    public static final String FLD_satelliteCount       = "satelliteCount";         // number of satellites
    public static final String FLD_batteryLevel         = "batteryLevel";           // battery level %
    public static final DBField GPSFieldInfo[] = {
        new DBField(FLD_gpsFixType    , Integer.TYPE  , DBField.TYPE_UINT16      , "GPS Fix Type"               , "enum=EventData$GPSFixType"),
        new DBField(FLD_horzAccuracy  , Double.TYPE   , DBField.TYPE_DOUBLE      , "Horizontal Accuracy Meters" , "format=#0.0"),
        new DBField(FLD_vertAccuracy  , Double.TYPE   , DBField.TYPE_DOUBLE      , "Vertical Accuracy Meters"   , "format=#0.0"),
        new DBField(FLD_HDOP          , Double.TYPE   , DBField.TYPE_DOUBLE      , "HDOP"                       , "format=#0.0"),
        new DBField(FLD_satelliteCount, Integer.TYPE  , DBField.TYPE_UINT16      , "Number of Satellites"       , ""),
        new DBField(FLD_batteryLevel  , Double.TYPE   , DBField.TYPE_DOUBLE      , "Battery Level %"            , "format=#0.000"),
    };
    // Misc custom fields
    public static final String FLD_entityID             = "entityID";               // trailer/package
    public static final String FLD_driverID             = "driverID";               // user/driver
    public static final String FLD_driverMessage        = "driverMessage";          // driver message
    public static final String FLD_topSpeedKPH          = "topSpeedKPH";            // kph
    public static final String FLD_sensorLow            = "sensorLow";              //
    public static final String FLD_sensorHigh           = "sensorHigh";             //
    public static final String FLD_dataPush             = "dataPush";               //
    public static final String FLD_costCenter           = "costCenter";             // associated cost center
    public static final String FLD_jobNumber            = "jobNumber";              // associated job number
    public static final DBField CustomFieldInfo[] = {
        // (may be externally accessed by DBConfig.DBInitialization)
        // Custom fields (must also need to be supported by "org.opengts.servers.gtsdmtp.DeviceDBImpl")
        new DBField(FLD_entityID      , String.class  , DBField.TYPE_STRING(32)  , "Trailer/Entity"             , "utf8=true"),
        new DBField(FLD_driverID      , String.class  , DBField.TYPE_STRING(32)  , "Driver/User"                , "utf8=true"),
        new DBField(FLD_driverMessage , String.class  , DBField.TYPE_STRING(200) , "Driver Message"             , "utf8=true"),
      //new DBField(FLD_topSpeedKPH   , Double.TYPE   , DBField.TYPE_DOUBLE      , "Top Speed KPH"              , "format=#0.0"),
        new DBField(FLD_sensorLow     , Long.TYPE     , DBField.TYPE_UINT32      , "Sensor Low"                 , "format=X4"),
        new DBField(FLD_sensorHigh    , Long.TYPE     , DBField.TYPE_UINT32      , "Sensor High"                , "format=X4"),
        new DBField(FLD_dataPush      , Boolean.TYPE  , DBField.TYPE_BOOLEAN     , "Data Push Indicator"        , ""),
        new DBField(FLD_costCenter    , Long.TYPE     , DBField.TYPE_UINT32      , "Cost Center"                , ""),
        new DBField(FLD_jobNumber     , String.class  , DBField.TYPE_STRING(32)  , "Job Number"                 , ""),
    };
    // J1708 fields
    public static final String FLD_fuelLevel            = "fuelLevel";              // %
    public static final String FLD_fuelEconomy          = "fuelEconomy";            // average
    public static final String FLD_fuelTotal            = "fuelTotal";              // liters
    public static final String FLD_fuelIdle             = "fuelIdle";               // liters
    public static final String FLD_engineRpm            = "engineRpm";              // rpm
    public static final String FLD_engineHours          = "engineHours";            // hours
    public static final String FLD_coolantLevel         = "coolantLevel";           // %
    public static final String FLD_coolantTemp          = "coolantTemp";            // C
    public static final String FLD_brakeGForce          = "brakeGForce";            // 
    public static final String FLD_j1708Fault           = "j1708Fault";             // 
    public static final DBField J1708FieldInfo[] = { 
        // (may be externally accessed by DBConfig.DBInitialization)
        // Custom fields (must also need to be supported by "org.opengts.servers.gtsdmtp.DeviceDBImpl")
        new DBField(FLD_fuelLevel     , Double.TYPE   , DBField.TYPE_DOUBLE      , "Fuel Level"                 , "format=#0.00"),
        new DBField(FLD_fuelEconomy   , Double.TYPE   , DBField.TYPE_DOUBLE      , "Fuel Economy"               , "format=#0.0"),
        new DBField(FLD_fuelTotal     , Double.TYPE   , DBField.TYPE_DOUBLE      , "Total Fuel Used"            , "format=#0.0"),
        new DBField(FLD_fuelIdle      , Double.TYPE   , DBField.TYPE_DOUBLE      , "Idle Fuel Used"             , "format=#0.0"),
        new DBField(FLD_engineRpm     , Long.TYPE     , DBField.TYPE_UINT32      , "Engine RPM"                 , ""),
      //new DBField(FLD_engineHours   , Double.TYPE   , DBField.TYPE_DOUBLE      , "Engine Hours"               , "format=#0.0"),
        new DBField(FLD_coolantLevel  , Double.TYPE   , DBField.TYPE_DOUBLE      , "Coolant Level"              , "format=#0.00"),
        new DBField(FLD_coolantTemp   , Double.TYPE   , DBField.TYPE_DOUBLE      , "Coolant Temperature"        , "format=#0.00"),
        new DBField(FLD_brakeGForce   , Double.TYPE   , DBField.TYPE_DOUBLE      , "Brake G Force"              , ""),
        new DBField(FLD_j1708Fault    , Long.TYPE     , DBField.TYPE_UINT32      , "J1708 Fault"                , ""),
    };
    // Temperature fields
    public static final String FLD_thermoAverage0       = "thermoAverage0";         // C
    public static final String FLD_thermoAverage1       = "thermoAverage1";         // C
    public static final String FLD_thermoAverage2       = "thermoAverage2";         // C
    public static final String FLD_thermoAverage3       = "thermoAverage3";         // C
    public static final String FLD_thermoAverage4       = "thermoAverage4";         // C
    public static final String FLD_thermoAverage5       = "thermoAverage5";         // C
    public static final String FLD_thermoAverage6       = "thermoAverage6";         // C
    public static final String FLD_thermoAverage7       = "thermoAverage7";         // C
    public static final DBField ThermoFieldInfo[] = {
        new DBField(FLD_thermoAverage0, Double.TYPE   , DBField.TYPE_DOUBLE      , "Temperature Average 0"      , "format=#0.0"),
        new DBField(FLD_thermoAverage1, Double.TYPE   , DBField.TYPE_DOUBLE      , "Temperature Average 1"      , "format=#0.0"),
        new DBField(FLD_thermoAverage2, Double.TYPE   , DBField.TYPE_DOUBLE      , "Temperature Average 2"      , "format=#0.0"),
        new DBField(FLD_thermoAverage3, Double.TYPE   , DBField.TYPE_DOUBLE      , "Temperature Average 3"      , "format=#0.0"),
        new DBField(FLD_thermoAverage4, Double.TYPE   , DBField.TYPE_DOUBLE      , "Temperature Average 4"      , "format=#0.0"),
        new DBField(FLD_thermoAverage5, Double.TYPE   , DBField.TYPE_DOUBLE      , "Temperature Average 5"      , "format=#0.0"),
        new DBField(FLD_thermoAverage6, Double.TYPE   , DBField.TYPE_DOUBLE      , "Temperature Average 6"      , "format=#0.0"),
        new DBField(FLD_thermoAverage7, Double.TYPE   , DBField.TYPE_DOUBLE      , "Temperature Average 7"      , "format=#0.0"),
    };
    // Auto increment
    public static final String FLD_autoIndex            = DBRecordKey.FLD_autoIndex;
    public static final DBField AutoIncrementIndex[] = {
        new DBField(FLD_autoIndex     , Long.TYPE     , DBField.TYPE_INT64       , "Auto Increment Index"       , "key=true auto=true"),
    };
    
    /* key class */
    public static class Key
        extends DeviceKey<EventData>
    {
        public Key() {
            this.getFieldValues().setIgnoreInvalidFields(true);
        }
        public Key(String acctId, String devId, long timestamp, int statusCode) {
            super.setFieldValue(FLD_accountID , ((acctId != null)? acctId.toLowerCase() : ""));
            super.setFieldValue(FLD_deviceID  , ((devId  != null)? devId.toLowerCase()  : ""));
            super.setFieldValue(FLD_timestamp , timestamp);
            super.setFieldValue(FLD_statusCode, statusCode);
            this.getFieldValues().setIgnoreInvalidFields(true);
        }
        public Key(String acctId, String devId, long timestamp, int statusCode, String entity) {
            super.setFieldValue(FLD_accountID , ((acctId != null)? acctId.toLowerCase() : ""));
            super.setFieldValue(FLD_deviceID  , ((devId  != null)? devId.toLowerCase()  : ""));
            super.setFieldValue(FLD_timestamp , timestamp);
            super.setFieldValue(FLD_statusCode, statusCode);
            super.setFieldValue(FLD_entityID  , ((entity != null)? entity.toLowerCase() : ""));
            this.getFieldValues().setIgnoreInvalidFields(true);
        }
        public DBFactory<EventData> getFactory() {
            return EventData.getFactory();
        }
    }

    /* factory constructor */
    private static DBFactory<EventData> factory = null;
    public static DBFactory<EventData> getFactory()
    {
        if (factory == null) {
            factory = DBFactory.createDBFactory(
                EventData.TABLE_NAME(),
                EventData.StandardFieldInfo,
                DBFactory.KeyType.PRIMARY,
                EventData.class, 
                EventData.Key.class,
                false/*editable*/,false/*viewable*/);
            factory.addParentTable(Account.TABLE_NAME());
            factory.addParentTable(Device.TABLE_NAME());
        }
        return factory;
    }

    /* Bean instance */
    public EventData()
    {
        super();
    }

    /* database record */
    public EventData(EventData.Key key)
    {
        super(key);
        // init?
    }
    
    // ------------------------------------------------------------------------

    /* table description */
    public static String getTableDescription(Locale loc)
    {
        I18N i18n = I18N.getI18N(EventData.class, loc);
        return i18n.getString("EventData.description", 
            "This table contains " +
            "events which have been generated by all client devices."
            );
    }

    // SQL table definition above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Common Bean access fields below

    /**
    *** Gets the timestamp of this event in Unix/Epoch time
    *** @return The timestamp of this event
    **/
    public long getTimestamp()
    {
        return this.getFieldValue(FLD_timestamp, 0L);
    }
    
    /**
    *** Sets the timestamp of this event in Unix/Epoch time
    *** @param v The timestamp of this event
    **/
    public void setTimestamp(long v)
    {
        this.setFieldValue(FLD_timestamp, v);
    }

    /**
    *** Gets the String representation of the timestamp of this event
    *** @return The String representation of the timestamp of this event
    **/
    public String getTimestampString()
    {
        Account a      = this.getAccount();
        String dateFmt = (a != null)? a.getDateFormat()   : BasicPrivateLabel.getDefaultDateFormat();
        String timeFmt = (a != null)? a.getTimeFormat()   : BasicPrivateLabel.getDefaultTimeFormat();
        TimeZone tmz   = (a != null)? a.getTimeZone(null) : DateTime.getGMTTimeZone();
        DateTime dt    = new DateTime(this.getTimestamp(), tmz);
        //return dt.gmtFormat(dateFmt + " " + timeFmt + " z");
        return dt.format(dateFmt + " " + timeFmt + " z");
    }

    /**
    *** Gets the String representation of the timestamp time-of-day of this event
    *** @return The String representation of the timestamp time-of-day of this event
    **/
    public String getTimestampTime()
    {
        Account a      = this.getAccount();
        String timeFmt = (a != null)? a.getTimeFormat()   : BasicPrivateLabel.getDefaultTimeFormat();
        TimeZone tmz   = (a != null)? a.getTimeZone(null) : DateTime.getGMTTimeZone();
        DateTime dt    = new DateTime(this.getTimestamp(), tmz);
        return dt.format(timeFmt);
    }

    /**
    *** Gets the String representation of the timestamp year of this event
    *** @return The String representation of the timestamp year of this event
    **/
    public String getTimestampYear()
    {
        Account a      = this.getAccount();
        TimeZone tmz   = (a != null)? a.getTimeZone(null) : DateTime.getGMTTimeZone();
        DateTime dt    = new DateTime(this.getTimestamp(), tmz);
        return String.valueOf(dt.getYear());
    }

    /**
    *** Gets the String representation of the timestamp month of this event
    *** @param abbrev  True to return the month abbreviation, false to return the full month name
    *** @return The String representation of the timestamp month of this event
    **/
    public String getTimestampMonth(boolean abbrev, Locale locale)
    {
        Account a      = this.getAccount();
        TimeZone tmz   = (a != null)? a.getTimeZone(null) : DateTime.getGMTTimeZone();
        DateTime dt    = new DateTime(this.getTimestamp(), tmz);
        return DateTime.getMonthName(dt.getMonth1(), abbrev);
    }

    /**
    *** Gets the String representation of the timestamp day-of-month of this event
    *** @return The String representation of the timestamp day-of-month of this event
    **/
    public String getTimestampDayOfMonth()
    {
        Account a      = this.getAccount();
        TimeZone tmz   = (a != null)? a.getTimeZone(null) : DateTime.getGMTTimeZone();
        DateTime dt    = new DateTime(this.getTimestamp(), tmz);
        return String.valueOf(dt.getDayOfMonth());
    }

    /**
    *** Gets the String representation of the timestamp day-of-week of this event
    *** @param abbrev  True to return the day abbreviation, false to return the full day name
    *** @return The String representation of the timestamp day-of-week of this event
    **/
    public String getTimestampDayOfWeek(boolean abbrev, Locale locale)
    {
        Account a      = this.getAccount();
        TimeZone tmz   = (a != null)? a.getTimeZone(null) : DateTime.getGMTTimeZone();
        DateTime dt    = new DateTime(this.getTimestamp(), tmz);
        return DateTime.getDayName(dt.getDayOfWeek(), abbrev);
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the status code of this event
    *** @return The status code of this event
    **/
    public int getStatusCode()
    {
        return this.getFieldValue(FLD_statusCode, 0);
    }

    /**
    *** Gets the String representation of the status code of this event
    *** @return The String representation of the status code of this event
    **/
    public String getStatusCodeDescription(BasicPrivateLabel bpl)
    {
        Device dev  = this.getDevice();
        int    code = this.getStatusCode();
        return StatusCode.getDescription(dev, code, bpl, null);
    }

    /**
    *** Gets the map icon-selector for the status code of this event
    *** @return The map icon-selector for the status code of this event
    **/
    public String getStatusCodeIconSelector(BasicPrivateLabel bpl)
    {
        Device dev  = this.getDevice();
        int    code = this.getStatusCode();
        return StatusCode.getIconSelector(dev, code, bpl);
    }

    /**
    *** Gets the icon-name for the status code of this event
    *** @param bpl  The domain BasicPrivateLabel
    *** @return The icon-name for the status code of this event
    **/
    public String getStatusCodeIconName(BasicPrivateLabel bpl)
    {
        Device dev  = this.getDevice();
        int    code = this.getStatusCode();
        return StatusCode.getIconName(dev, code, bpl);
    }

    /**
    *** Sets the status code of this event
    *** @param v The status code of this event
    **/
    public void setStatusCode(int v)
    {
        this.setFieldValue(FLD_statusCode, v);
    }

    // ------------------------------------------------------------------------

    /**
    *** Sets the data source for this event.  The data source is an optional field defined by the 
    *** remote client tracking device.  
    *** @return The event data source
    **/
    public String getDataSource()
    {
        return this.getFieldValue(FLD_dataSource, "");
    }
    
    /**
    *** Sets the data source for this event.
    *** @param v  The data source
    **/
    public void setDataSource(String v)
    {
        this.setFieldValue(FLD_dataSource, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------
    
    /**
    *** Sets the transport-id for this event.  This is the 'transportID' from the Transport 
    *** record used to identify this Device.
    *** @return The transport-id used to identify this device.
    **/
    public String getTransportID()
    {
        return this.getFieldValue(FLD_transportID, "");
    }
    
    /**
    *** Sets the transport-id for this event.
    *** @param v  The transport-id used to identify this device.
    **/
    public void setTransportID(String v)
    {
        this.setFieldValue(FLD_transportID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getRawData()
    {
        return this.getFieldValue(FLD_rawData, "");
    }
    
    public void setRawData(String v)
    {
        this.setFieldValue(FLD_rawData, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public double getLatitude()
    {
        return this.getFieldValue(FLD_latitude, 0.0);
    }
    
    public void setLatitude(double v)
    {
        this.setFieldValue(FLD_latitude, v);
    }
    
    public GeoPoint getGeoPoint()
    {
        return new GeoPoint(this.getLatitude(), this.getLongitude());
    }
    
    public void setGeoPoint(double lat, double lng)
    {
        this.setLatitude(lat);
        this.setLongitude(lng);
    }
    
    public void setGeoPoint(GeoPoint gp)
    {
        if ((gp != null) && gp.isValid()) {
            this.setLatitude(gp.getLatitude());
            this.setLongitude(gp.getLongitude());
        } else {
            this.setLatitude(0.0);
            this.setLongitude(0.0);
        }
    }
    
    public boolean isValidGeoPoint()
    {
        return GeoPoint.isValid(this.getLatitude(), this.getLongitude());
    }

    // ------------------------------------------------------------------------

    public double getLongitude()
    {
        return this.getFieldValue(FLD_longitude, 0.0);
    }
    
    public void setLongitude(double v)
    {
        this.setFieldValue(FLD_longitude, v);
    }
    
    // ------------------------------------------------------------------------

    public long getGpsAge()
    {
        return this.getFieldValue(FLD_gpsAge, 0L);
    }
    
    public void setGpsAge(long v)
    {
        this.setFieldValue(FLD_gpsAge, v);
    }

    // ------------------------------------------------------------------------

    public double getSpeedKPH()
    {
        return this.getFieldValue(FLD_speedKPH, 0.0);
    }
    
    public void setSpeedKPH(double v)
    {
        this.setFieldValue(FLD_speedKPH, v);
    }

    public double getSpeedMPH()
    {
        return this.getSpeedKPH() * GeoPoint.MILES_PER_KILOMETER;
    }

    // ------------------------------------------------------------------------

    public double getHeading()
    {
        return this.getFieldValue(FLD_heading, 0.0);
    }

    public void setHeading(double v)
    {
        this.setFieldValue(FLD_heading, v);
    }
    
    // ------------------------------------------------------------------------

    public double getAltitude() // meters
    {
        return this.getFieldValue(FLD_altitude, 0.0);
    }

    public String getAltitudeString(boolean inclUnits, Locale locale)
    {
        I18N i18n = I18N.getI18N(EventData.class, locale);
        double alt = this.getAltitude(); // meters
        String distUnitsStr = "?";
        if (Account.getDistanceUnits(this.getAccount()).isMiles()) {
            alt *= GeoPoint.FEET_PER_METER; // convert to feet
            distUnitsStr = i18n.getString("EventData.units.feet", "feet");
        } else {
            distUnitsStr = i18n.getString("EventData.units.meters", "meters");
        }
        String altStr = StringTools.format(alt,"0");
        return inclUnits? (altStr + " " + distUnitsStr) : altStr;
    }

    public void setAltitude(double v) // meters
    {
        this.setFieldValue(FLD_altitude, v);
    }

    // ------------------------------------------------------------------------

    public double getDistanceKM()
    {
        return this.getFieldValue(FLD_distanceKM, 0.0);
    }

    public void setDistanceKM(double v)
    {
        this.setFieldValue(FLD_distanceKM, v);
    }

    // ------------------------------------------------------------------------

    public double getOdometerKM()
    {
        return this.getFieldValue(FLD_odometerKM, 0.0);
    }

    public void setOdometerKM(double v)
    {
        this.setFieldValue(FLD_odometerKM, v);
    }

    // ------------------------------------------------------------------------

    public long getGeozoneIndex()
    {
        return this.getFieldValue(FLD_geozoneIndex, 0L);
    }

    public void setGeozoneIndex(long v)
    {
        this.setFieldValue(FLD_geozoneIndex, v);
    }

    // ------------------------------------------------------------------------

    public String getGeozoneID()
    {
        return this.getFieldValue(FLD_geozoneID, "");
    }

    public void setGeozoneID(String v)
    {
        this.setFieldValue(FLD_geozoneID, StringTools.trim(v));
    }

    public String getGeozoneDescription()
    {
        String gid = this.getGeozoneID();
        if (StringTools.isBlank(gid)) {
            return "";
        } else {
            try {
                Geozone gz[] = Geozone.getGeozone(this.getAccount(), gid);
                return !ListTools.isEmpty(gz)? gz[0].getDescription() : "";
            } catch (DBException dbe) {
                return "";
            }
        }
    }

    // ------------------------------------------------------------------------

    public String getEntityID()
    {
        return this.getFieldValue(FLD_entityID, "");
    }
    
    public void setEntityID(String v)
    {
        this.setFieldValue(FLD_entityID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the GPS fix type
    *** @return The GPS fix type
    **/
    public int getGpsFixType()
    {
        Integer v = (Integer)this.getFieldValue(FLD_gpsFixType);
        return (v != null)? v.intValue() : EnumTools.getDefault(GPSFixType.class).getIntValue();
    }

    /**
    *** Sets the GPS fix type
    *** @param v The GPS fix type
    **/
    public void setGpsFixType(int v)
    {
        this.setFieldValue(FLD_gpsFixType, EnumTools.getValueOf(GPSFixType.class,v).getIntValue());
    }

    /**
    *** Sets the GPS fix type
    *** @param v The GPS fix type
    **/
    public void setGpsFixType(GPSFixType v)
    {
        this.setFieldValue(FLD_gpsFixType, EnumTools.getValueOf(GPSFixType.class,v).getIntValue());
    }

    /**
    *** Sets the GPS fix type
    *** @param v The GPS fix type
    **/
    public void setGpsFixType(String v, Locale locale)
    {
        this.setFieldValue(FLD_gpsFixType, EnumTools.getValueOf(GPSFixType.class,v,locale).getIntValue());
    }

    public String getGpsFixTypeDescription(Locale loc)
    {
        return EventData.getGPSFixType(this).toString(loc);
    }

    // ------------------------------------------------------------------------

    public double getHorzAccuracy()
    {
        return this.getFieldValue(FLD_horzAccuracy, 0.0);
    }
    
    public void setHorzAccuracy(double v)
    {
        this.setFieldValue(FLD_horzAccuracy, v);
    }

    // ------------------------------------------------------------------------

    public double getVertAccuracy()
    {
        return this.getFieldValue(FLD_vertAccuracy, 0.0);
    }
    
    public void setVertAccuracy(double v)
    {
        this.setFieldValue(FLD_vertAccuracy, v);
    }

    // ------------------------------------------------------------------------

    public double getHDOP()
    {
        return this.getFieldValue(FLD_HDOP, 0.0);
    }
    
    public void setHDOP(double v)
    {
        this.setFieldValue(FLD_HDOP, v);
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the Satellite count
    *** @return The Satellite count
    **/
    public int getSatelliteCount()
    {
        return this.getFieldValue(FLD_satelliteCount, 0);
    }

    /**
    *** Sets the Satellite count
    *** @param v The Satellite count
    **/
    public void setSatelliteCount(int v)
    {
        this.setFieldValue(FLD_satelliteCount, ((v < 0)? 0 : v));
    }

    // ------------------------------------------------------------------------

    public double getBatteryLevel()
    {
        return this.getFieldValue(FLD_batteryLevel, 0.0);
    }
    
    public void setBatteryLevel(double v)
    {
        this.setFieldValue(FLD_batteryLevel, v);
    }

    // ------------------------------------------------------------------------

    /**
    *** Returns true if an address has been defined
    *** @return True if an address has been defined
    **/
    public boolean hasAddress()
    {
        return !this.getAddress().equals("");
    }

    public String getAddress()
    {
        String v = (String)this.getFieldValue(FLD_address);
        if ((v == null) || v.equals("")) {
            // should we try to go get the reverse-geocode?
            v = ""; // in case it was null
        }
        return v;
    }
    
    public void setAddress(String v)
    {
        this.setFieldValue(FLD_address, StringTools.trim(v));
    }

    public boolean updateAddress(boolean fastOnly)
        throws SlowOperationException
    {
        return this.updateAddress(fastOnly, false/*force*/);
    }

    public boolean updateAddress(boolean fastOnly, boolean force)
        throws SlowOperationException
    {
        // If the caller does not want to wait for a time-consuming operation, specifying 
        // 'fastOnly==true' will cause this method to throw a 'SlowOperationException' if 
        // it determines that the reverse-geocoding will take too long.  The reason that 
        // reverse-geocoding  might take a while is because it might be using an outside 
        // service (ie. linking to a remote web-based service) to perform it's function.
        // (SlowOperationException is not thrown if 'fastOnly' is false.)
        
        /* already have an address? */
        if (!force && this.hasAddress()) {
            // we already have an address 
            // (and 'force' did not indicate we should update the address)
            return false;
        }

        /* invalid GeoPoint? */
        if (!this.isValidGeoPoint()) {
            // Can't reverse-geocode an invalid point
            return false;
        }

        /* get Account */
        Account acct = this.getAccount();
        if (acct == null) {
            // no account, not reverse-geocoding
            return false;
        }

        /* get geocoder mode */
        Account.GeocoderMode geocoderMode = Account.getGeocoderMode(acct);
        if (geocoderMode.isNone()) {
            // no geocoding is performed for this account
            return false;
        }

        /* set "Departed" geozone description for STATUS_GEOFENCE_DEPART events */
        int statusCode = this.getStatusCode();
        if (statusCode == StatusCodes.STATUS_GEOFENCE_DEPART) {
            // On departure events, get the departed Geozone description
            long clientId = this.getGeozoneIndex(); // ClientID of departed geozone
            if (clientId > 0L) {
                Geozone gz[] = Geozone.getClientIDZones(acct.getAccountID(), clientId);
                if ((gz != null) && (gz.length > 0)) {
                    this.setGeozoneID(gz[0].getGeozoneID()); // departed zone
                    this.setAddress(gz[0].getDescription());
                    this.setStreetAddress(gz[0].getStreetAddress());
                    this.setCity(gz[0].getCity());
                    this.setStateProvince(gz[0].getStateProvince());
                    this.setPostalCode(gz[0].getPostalCode());
                    this.setCountry(gz[0].getCountry());
                    this.setSubdivision(gz[0].getSubdivision());
                    return true;
                }
            }
        } else
        if (statusCode == StatusCodes.STATUS_GEOFENCE_ARRIVE) {
            // On arrival events, get the arrival Geozone description
            // (due to rounding error, the server may think we are not yet within a zone)
            long clientId = this.getGeozoneIndex(); // ClientID of arrival geozone
            if (clientId > 0L) {
                Geozone gz[] = Geozone.getClientIDZones(acct.getAccountID(), clientId);
                if ((gz != null) && (gz.length > 0)) {
                    this.setGeozoneID(gz[0].getGeozoneID());
                    this.setAddress(gz[0].getDescription());
                    this.setStreetAddress(gz[0].getStreetAddress());
                    this.setCity(gz[0].getCity());
                    this.setStateProvince(gz[0].getStateProvince());
                    this.setPostalCode(gz[0].getPostalCode());
                    this.setCountry(gz[0].getCountry());
                    this.setSubdivision(gz[0].getSubdivision());
                    return true;
                }
            }
        }

        /* (at least GeocoderMode.GEOZONE) get address from Geozone factory */
        GeoPoint gp = this.getGeoPoint();
        Geozone gzone = Geozone.getGeozone(acct.getAccountID(), null, gp, true); // <Geozone>.getReverseGeocode() == true
        if (gzone != null) {
            Print.logInfo("Found Geozone : " + gzone.getGeozoneID() + " - " + gzone.getDescription());
            if (gzone.getClientUpload() && (this.getGeozoneIndex() == 0L)) {
                this.setGeozoneIndex(gzone.getClientID());
            }
            this.setGeozoneID(gzone.getGeozoneID());
            this.setAddress(gzone.getDescription());
            this.setStreetAddress(gzone.getStreetAddress());
            this.setCity(gzone.getCity());
            this.setStateProvince(gzone.getStateProvince());
            this.setPostalCode(gzone.getPostalCode());
            this.setCountry(gzone.getCountry());
            this.setSubdivision(gzone.getSubdivision());
            return true;
        }

        /* reverse-geocoding iff FULL, or PARTIAL with high-priority status code */
        BasicPrivateLabel privLabel = acct.getPrivateLabel();
        if (!geocoderMode.okFull() && !StatusCodes.IsHighPriority(statusCode,privLabel)) {
            // PARTIAL reverse-geocoding requested and this is not a high-pri status code
            return false;
        }

        /* get reverse-geocoder */
        ReverseGeocodeProvider rgp = privLabel.getReverseGeocodeProvider();
        if (rgp == null) {
            // no ReverseGeocodeProvider, no reverse-geocoding
            String acctID = this.getAccountID();
            if (acct.hasPrivateLabel()) {
                Print.logInfo("[Account '%s'] PrivateLabel '%s' does not define a ReverseGeocodeProvider", acctID, privLabel); 
            } else {
                Print.logInfo("No PrivateLabel (thus no ReverseGeocodeProvider) for Account '%s'", acctID); 
            }
            return false;
        }

        /* fast operations only? */
        if (fastOnly && !rgp.isFastOperation()) {
            // We've requested a fast operation only, and this operation is slow.
            // It's up to the caller to see that this operation is queued in a background thread.
            throw new SlowOperationException("'fast' requested, and this operation is 'slow'");
        }

        /* finally, get the address for this point */
        ReverseGeocode rg = null;
        try {
            // make sure the Domain properties are available to RTConfig
            privLabel.pushRTProperties();   // stack properties (may be redundant in servlet environment)
            rg = rgp.getReverseGeocode(gp); // get the reverse-geocode
        } catch (Throwable th) {
            // ignore
        } finally {
            privLabel.popRTProperties();    // remove from stack
        }
        if (rg != null) {
            if (rg.hasFullAddress()) {
                this.setAddress(rg.getFullAddress());
            }
            if (rg.hasStreetAddress()) {
                this.setStreetAddress(rg.getStreetAddress());
            }
            if (rg.hasCity()) {
                this.setCity(rg.getCity());
            }
            if (rg.hasStateProvince()) {
                this.setStateProvince(rg.getStateProvince());
            }
            if (rg.hasPostalCode()) {
                this.setPostalCode(rg.getPostalCode());
            }
            if (rg.hasCountryCode()) {
                this.setCountry(rg.getCountryCode());
            }
            if (rg.hasSubdivision()) {
                this.setSubdivision(rg.getSubdivision());
            }
            if (rg.hasSpeedLimitKPH()) {
                this.setSpeedLimitKPH(rg.getSpeedLimitKPH());
            }
            if (rg.hasIsTollRoad()) {
                this.setIsTollRoad(rg.getIsTollRoad());
            }
            return true;
        }

        /* still no address after all of this */
        Print.logInfo("No reverse-geocode found for this location: " + gp);
        return false;
        
    }

    // ------------------------------------------------------------------------

    public String getStreetAddress()
    {
        String v = (String)this.getFieldValue(FLD_streetAddress);
        if ((v == null) || v.equals("")) {
            // should we try to go get the reverse-geocode?
            v = ""; // in case it was null
        }
        return v;
    }
    
    public void setStreetAddress(String v)
    {
        this.setFieldValue(FLD_streetAddress, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getCity()
    {
        String v = (String)this.getFieldValue(FLD_city);
        if ((v == null) || v.equals("")) {
            // should we try to go get the reverse-geocode?
            v = ""; // in case it was null
        }
        return v;
    }
    
    public void setCity(String v)
    {
        this.setFieldValue(FLD_city, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getStateProvince()
    {
        String v = (String)this.getFieldValue(FLD_stateProvince);
        if ((v == null) || v.equals("")) {
            // should we try to go get the reverse-geocode?
            v = ""; // in case it was null
        }
        return v;
    }
    
    public void setStateProvince(String v)
    {
        this.setFieldValue(FLD_stateProvince, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getPostalCode()
    {
        String v = (String)this.getFieldValue(FLD_postalCode);
        if ((v == null) || v.equals("")) {
            // should we try to go get the reverse-geocode?
            v = ""; // in case it was null
        }
        return v;
    }
    
    public void setPostalCode(String v)
    {
        this.setFieldValue(FLD_postalCode, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getCountry()
    {
        String v = (String)this.getFieldValue(FLD_country);
        if ((v == null) || v.equals("")) {
            // should we try to go get the reverse-geocode?
            v = ""; // in case it was null
        }
        return v;
    }
    
    public void setCountry(String v)
    {
        this.setFieldValue(FLD_country, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    /* return subdivision */
    public String getSubdivision()
    {
        String v = (String)this.getFieldValue(FLD_subdivision);
        if ((v == null) || v.equals("")) {
            // should we try to go get the reverse-geocode?
            v = ""; // in case it was null
        }
        return v;
    }
    
    /* set subdivision */
    public void setSubdivision(String v)
    {
        this.setFieldValue(FLD_subdivision, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    /* get speed limit */
    public double getSpeedLimitKPH()
    {
        return this.getFieldValue(FLD_speedLimitKPH, 0.0);
    }
    
    /* set speed limit */
    public void setSpeedLimitKPH(double v)
    {
        this.setFieldValue(FLD_speedLimitKPH, ((v > 0.0)? v : 0.0));
    }

    // ------------------------------------------------------------------------

    public boolean getIsTollRoad()
    {
        return this.getFieldValue(FLD_isTollRoad, false);
    }

    public void setIsTollRoad(boolean v)
    {
        this.setFieldValue(FLD_isTollRoad, v);
    }

    public boolean isTollRoad()
    {
        return this.getIsTollRoad();
    }

    // ------------------------------------------------------------------------

    /* get digital input mask */
    public long getInputMask()
    {
        Long v = (Long)this.getFieldValue(FLD_inputMask);
        return (v != null)? v.intValue() : 0;
    }
    
    /* return state of input mask bit */
    public boolean getInputMaskBitState(int bit)
    {
        long m = this.getInputMask();
        return (((1L << bit) & m) != 0L);
    }
    
    /* set digital input mask */
    public void setInputMask(long v)
    {
        this.setFieldValue(FLD_inputMask, v);
    }

    // Common Bean access fields above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Temerature Bean access fields below

    public static boolean isValidTemperature(double t)
    {
        // IE. ((T >= -126) && (T <= 126))
        return ((t >= -TEMPERATURE_LIMIT) && (t <= TEMPERATURE_LIMIT));
    }

    public double getThermoAverage(int ndx)
    {
        switch (ndx) {
            case 0: return this.getThermoAverage0();
            case 1: return this.getThermoAverage1();
            case 2: return this.getThermoAverage2();
            case 3: return this.getThermoAverage3();
            case 4: return this.getThermoAverage4();
            case 5: return this.getThermoAverage5();
            case 6: return this.getThermoAverage6();
            case 7: return this.getThermoAverage7();
        }
        return INVALID_TEMPERATURE;
    }

    public void setThermoAverage(int ndx, double v)
    {
        switch (ndx) {
            case 0: this.setThermoAverage0(v); break;
            case 1: this.setThermoAverage1(v); break;
            case 2: this.setThermoAverage2(v); break;
            case 3: this.setThermoAverage3(v); break;
            case 4: this.setThermoAverage4(v); break;
            case 5: this.setThermoAverage5(v); break;
            case 6: this.setThermoAverage6(v); break;
            case 7: this.setThermoAverage7(v); break;
        }
    }

    public double getThermoAverage0()
    {
        return this.getFieldValue(FLD_thermoAverage0, INVALID_TEMPERATURE);
    }
    public void setThermoAverage0(double v)
    {
        this.setFieldValue(FLD_thermoAverage0, v);
    }

    public double getThermoAverage1()
    {
        return this.getFieldValue(FLD_thermoAverage1, INVALID_TEMPERATURE);
    }
    public void setThermoAverage1(double v)
    {
        this.setFieldValue(FLD_thermoAverage1, v);
    }

    public double getThermoAverage2()
    {
        return this.getFieldValue(FLD_thermoAverage2, INVALID_TEMPERATURE);
    }
    public void setThermoAverage2(double v)
    {
        this.setFieldValue(FLD_thermoAverage2, v);
    }

    public double getThermoAverage3()
    {
        return this.getFieldValue(FLD_thermoAverage3, INVALID_TEMPERATURE);
    }
    public void setThermoAverage3(double v)
    {
        this.setFieldValue(FLD_thermoAverage3, v);
    }

    public double getThermoAverage4()
    {
        return this.getFieldValue(FLD_thermoAverage4, INVALID_TEMPERATURE);
    }
    public void setThermoAverage4(double v)
    {
        this.setFieldValue(FLD_thermoAverage4, v);
    }

    public double getThermoAverage5()
    {
        return this.getFieldValue(FLD_thermoAverage5, INVALID_TEMPERATURE);
    }
    public void setThermoAverage5(double v)
    {
        this.setFieldValue(FLD_thermoAverage5, v);
    }

    public double getThermoAverage6()
    {
        return this.getFieldValue(FLD_thermoAverage6, INVALID_TEMPERATURE);
    }
    public void setThermoAverage6(double v)
    {
        this.setFieldValue(FLD_thermoAverage6, v);
    }

    public double getThermoAverage7()
    {
        return this.getFieldValue(FLD_thermoAverage7, INVALID_TEMPERATURE);
    }
    public void setThermoAverage7(double v)
    {
        this.setFieldValue(FLD_thermoAverage7, v);
    }

    // Temerature Bean access fields above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Custom Bean access fields below

    public String getDriverID()
    {
        return this.getFieldValue(FLD_driverID, "");
    }
    
    public void setDriverID(String v)
    {
        this.setFieldValue(FLD_driverID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getDriverMessage()
    {
        return this.getFieldValue(FLD_driverMessage, "");
    }
    
    public void setDriverMessage(String v)
    {
        this.setFieldValue(FLD_driverMessage, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public double getTopSpeedKPH()
    {
        return this.getFieldValue(FLD_topSpeedKPH, 0.0);
    }
    
    public void setTopSpeedKPH(double v)
    {
        this.setFieldValue(FLD_topSpeedKPH, v);
    }

    // ------------------------------------------------------------------------

    public long getSensorLow()
    {
        return this.getFieldValue(FLD_sensorLow, 0L);
    }
    
    public void setSensorLow(long v)
    {
        this.setFieldValue(FLD_sensorLow, v);
    }

    // ------------------------------------------------------------------------

    public long getSensorHigh()
    {
        return this.getFieldValue(FLD_sensorHigh, 0L);
    }
    
    public void setSensorHigh(long v)
    {
        this.setFieldValue(FLD_sensorHigh, v);
    }

    // ------------------------------------------------------------------------

    public double getBrakeGForce()
    {
        return this.getFieldValue(FLD_brakeGForce, 0.0);
    }
    
    public void setBrakeGForce(double v)
    {
        this.setFieldValue(FLD_brakeGForce, v);
    }

    // ------------------------------------------------------------------------

    public boolean getDataPush()
    {
        return this.getFieldValue(FLD_dataPush, true);
    }

    public void setDataPush(boolean v)
    {
        this.setFieldValue(FLD_dataPush, v);
    }

    // ------------------------------------------------------------------------

    public long getCostCenter()
    {
        return this.getFieldValue(FLD_costCenter, 0L);
    }

    public void setCostCenter(long v)
    {
        this.setFieldValue(FLD_costCenter, v);
    }

    // ------------------------------------------------------------------------

    public String getJobNumber()
    {
        return this.getFieldValue(FLD_jobNumber, "");
    }

    public void setJobNumber(String v)
    {
        this.setFieldValue(FLD_jobNumber, StringTools.trim(v));
    }

    // Common Bean access fields above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // J1708 Bean access fields below

    public double getFuelLevel()
    {
        return this.getFieldValue(FLD_fuelLevel, 0.0);
    }
    
    public void setFuelLevel(double v)
    {
        this.setFieldValue(FLD_fuelLevel, v);
    }

    // ------------------------------------------------------------------------

    public double getFuelEconomy()
    {
        return this.getFieldValue(FLD_fuelEconomy, 0.0);
    }
    
    public void setFuelEconomy(double v)
    {
        this.setFieldValue(FLD_fuelEconomy, v);
    }

    // ------------------------------------------------------------------------

    public double getFuelTotal()
    {
        return this.getFieldValue(FLD_fuelTotal, 0.0);
    }
    
    public void setFuelTotal(double v)
    {
        this.setFieldValue(FLD_fuelTotal, v);
    }

    // ------------------------------------------------------------------------

    public double getFuelIdle()
    {
        return this.getFieldValue(FLD_fuelIdle, 0.0);
    }
    
    public void setFuelIdle(double v)
    {
        this.setFieldValue(FLD_fuelIdle, v);
    }

    // ------------------------------------------------------------------------

    public long getEngineRpm()
    {
        return this.getFieldValue(FLD_engineRpm, 0L);
    }
    
    public void setEngineRpm(long v)
    {
        this.setFieldValue(FLD_engineRpm, v);
    }

    // ------------------------------------------------------------------------

    public double getEngineHours()
    {
        return this.getFieldValue(FLD_engineHours, 0.0);
    }
    
    public void setEngineHours(double v)
    {
        this.setFieldValue(FLD_engineHours, v);
    }

    // ------------------------------------------------------------------------

    public double getCoolantLevel()
    {
        return this.getFieldValue(FLD_coolantLevel, 0.0);
    }
    
    public void setCoolantLevel(double v)
    {
        this.setFieldValue(FLD_coolantLevel, v);
    }

    // ------------------------------------------------------------------------

    public double getCoolantTemp()
    {
        return this.getFieldValue(FLD_coolantTemp, 0.0);
    }
    
    public void setCoolantTemp(double v)
    {
        this.setFieldValue(FLD_coolantTemp, v);
    }

    // ------------------------------------------------------------------------

    public long getJ1708Fault()
    {
        return this.getFieldValue(FLD_j1708Fault, 0L);
    }
    
    public void setJ1708Fault(long v)
    {
        this.setFieldValue(FLD_j1708Fault, v);
    }

    // J1708 Bean access fields above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* overridden to set default values */
    public void setCreationDefaultValues()
    {
        //super.setRuntimeDefaultValues();
    }

    // ------------------------------------------------------------------------

    /* icon selector properties */
    private boolean isLastEventInList = false;
    public void setIsLastEvent(boolean isLast)
    {
        this.isLastEventInList = isLast;
    }
    
    public boolean getIsLastEvent()
    {
        return this.isLastEventInList;
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the default map icon index
    *** @param iconKeys The defined icon keys (the returned index must be within the
    ***                 the range of this list).
    *** @param isFleet  True if obtaining an icon index for a 'fleet' map
    *** @return An icon index
    **/
    protected int getDefaultMapIconIndex(OrderedSet<String> iconKeys, boolean isFleet)
    {
        // Equivalent to the following icon 'selectors':
        //   "iconSelector"       : ($DIN? "orange" : (speed<8)? "red" : (speed<32)? "yellow" : "green")
        //   "iconSelector.fleet" : ("blue")

        /* fleet map? */
        if (isFleet) {
            Device dev = this.getDevice();
            String devIcon = ((dev != null) && dev.hasPushpinID())? dev.getPushpinID() : "fleet";
            return EventData.getPushpinIconIndex(devIcon, iconKeys, ICON_PUSHPIN_BLUE);
        }

        /* "all" pushpins? */
        int allIconNdx = EventData.getPushpinIconIndex("all", iconKeys, -1);
        if (allIconNdx >= 0) { // '0' is a valid index
            return allIconNdx;
        }

        /* last event? */
        if (this.getIsLastEvent()) {
            int iconNdx = EventData.getPushpinIconIndex("last", iconKeys, -1);
            if (iconNdx >= 0) { // '0' is a valid index
                return iconNdx;
            }
        }

        /* default to speed [ie. ((speed<8)? "red" : (speed<32)? "yellow" : "green") ] */
        double kph = this.getSpeedKPH();
        if (kph < 5.0) {
            return EventData.getPushpinIconIndex("stop"  , iconKeys, ICON_PUSHPIN_RED);
        } else
        if (kph < 32.0) {
            return EventData.getPushpinIconIndex("slow"  , iconKeys, ICON_PUSHPIN_YELLOW);
        } else {
            return EventData.getPushpinIconIndex("moving", iconKeys, ICON_PUSHPIN_GREEN);
        }

    }
    
    /**
    *** Gets the default map icon index
    *** @param iconSelector  An icon 'selector' to be analyzed by the installed 'RuleFactory' to
    ***         determine the icon index.
    *** @param iconKeys The defined icon keys (the returned index must be within the
    ***                 the range of this list).
    *** @param isFleet  True if obtaining an icon index for a 'fleet' map
    *** @return An icon index
    **/
    public int getPushpinIconIndex(String iconSelector, OrderedSet<String> iconKeys,
        boolean isFleet, BasicPrivateLabel bpl)
    {

        /* icon name */
        if (!isFleet) {
            // custom status code pushpins override the 'iconSelector'
            String iconName = this.getStatusCodeIconName(bpl);
            if (!StringTools.isBlank(iconName)) {
                int iconNdx = EventData.getPushpinIconIndex(iconName,iconKeys,-1);
                if (iconNdx >= 0) {
                    return iconNdx;
                }
            }
        }

        /* RuleFactory? */
        RuleFactory ruleFact = Device.getRuleFactory();

        /* status code icon selector */
        if (ruleFact != null) {
            String scIconSel = this.getStatusCodeIconSelector(bpl);
            if (!StringTools.isBlank(scIconSel)) {
                try {
                    Object result = ruleFact.evaluateSelector(scIconSel,this);
                    if (result instanceof Number) {
                        // ie. ($DIN? 3 : (speed<5)? 2 : (speed<32)? 4 : 5)
                        int iconNdx = ((Number)result).intValue();
                        if (iconNdx >= 0) { // '0' is a valid index
                            return iconNdx;
                        }
                    } else
                    if (result instanceof String) {
                        // ie. ($DIN? "orange" : (speed<5)? "red" : (speed<32)? "yellow" : "green")
                        int iconNdx = EventData.getPushpinIconIndex((String)result,iconKeys,-1);
                        if (iconNdx >= 0) {
                            return iconNdx;
                        }
                    } else {
                        Print.logError("Invalid icon selector result type: " + StringTools.className(result));
                    }
                } catch (RuleParseException rpe) {
                    Print.logError("Icon selector parse error: " + rpe.getMessage());
                }
            }
        }

        /* check rule factory */
        if (ruleFact != null) {
            if (!StringTools.isBlank(iconSelector)) {
                try {
                    Object result = ruleFact.evaluateSelector(iconSelector,this);
                    if (result instanceof Number) {
                        int iconNdx = ((Number)result).intValue();
                        if (iconNdx >= 0) { // '0' is a valid index
                            return iconNdx;
                        }
                    } else
                    if (result instanceof String) {
                        int iconNdx = EventData.getPushpinIconIndex((String)result,iconKeys,-1);
                        if (iconNdx >= 0) {
                            return iconNdx;
                        }
                    } else {
                        Print.logError("Invalid icon selector result type: " + StringTools.className(result));
                    }
                } catch (RuleParseException rpe) {
                    Print.logError("Icon selector parse error: " + rpe.getMessage());
                }
            }
        }

        /* default icon index */
        return this.getDefaultMapIconIndex(iconKeys, isFleet);

    }

    // ------------------------------------------------------------------------

    /* format this EventData record in CSV format according to the specified fields */
    public String formatAsCSVRecord(String fields[])
    {
        String csvSep = ",";
        StringBuffer sb = new StringBuffer();
        if (fields != null) {
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) { sb.append(csvSep); }
                DBField dbFld = this.getRecordKey().getField(fields[i]);
                Object val = (dbFld != null)? this.getFieldValue(fields[i]) : null;
                if (val != null) {
                    Class typeClass = dbFld.getTypeClass();
                    if (fields[i].equals(FLD_statusCode)) {
                        int code = ((Integer)val).intValue();
                        StatusCodes.Code c = StatusCodes.GetCode(code,Account.getPrivateLabel(this.getAccount()));
                        if (c != null) {
                            sb.append("\"" + c.getDescription(null) + "\"");
                        } else {
                            sb.append("\"0x" + StringTools.toHexString(code,16) + "\"");
                        }
                    } else 
                    if ((typeClass == Double.class) || (typeClass == Double.TYPE)) {
                        double d = ((Double)val).doubleValue();
                        String fmt = dbFld.getFormat();
                        if ((fmt != null) && !fmt.equals("")) {
                            sb.append("\"" + StringTools.format(d,fmt) + "\"");
                        } else {
                            sb.append("\"" + String.valueOf(d) + "\"");
                        }
                    } else 
                    if ((typeClass == Float.class) || (typeClass == Float.TYPE)) {
                        float d = ((Float)val).floatValue();
                        String fmt = dbFld.getFormat();
                        if ((fmt != null) && !fmt.equals("")) {
                            sb.append("\"" + StringTools.format(d,fmt) + "\"");
                        } else {
                            sb.append("\"" + String.valueOf(d) + "\"");
                        }
                    } else {
                        sb.append(StringTools.quoteCSVString(val.toString()));
                    }
                }
            }
        }
        return sb.toString();
    }
    
    // ------------------------------------------------------------------------

    private EventData previousEventData          = null;
    private EventData previousEventData_validGPS = null;

    public void setPreviousEventData(EventData ev)
    {
        if (ev != null) {
            this.previousEventData = ev;
            if (this.previousEventData.isValidGeoPoint()) {
                this.previousEventData_validGPS = this.previousEventData;
            }
        }
    }
    
    public EventData getPreviousEventData(boolean validGPS)
        throws DBException
    {
        return this.getPreviousEventData(null, validGPS);
    }

    public EventData getPreviousEventData(int statusCodes[], boolean validGPS)
        throws DBException
    {

        /* check previous event cache */
        if (statusCodes == null) {
            // check for cached previous event
            if (!validGPS && (this.previousEventData != null)) {
                return this.previousEventData;
            } else
            if (validGPS && (this.previousEventData_validGPS != null)) {
                return this.previousEventData_validGPS;
            }
        }

        /* get previous event */
        // 'endTime' should be this events timestamp, 
        // and 'additionalSelect' should be (statusCode != this.getStatusCode())
        long startTime = -1L; // start of time
        long endTime   = this.getTimestamp() - 1L; // previous to this event
        EventData ed[] = EventData.getRangeEvents(
            this.getAccountID(), this.getDeviceID(),
            startTime, endTime,
            statusCodes,
            validGPS,
            EventData.LimitType.LAST, 1/*limit*/, true/*ascending*/,
            null/*additionalSelect*/);
        if (!ListTools.isEmpty(ed)) {
            EventData ev = ed[0];
            if (statusCodes == null) {
                // cache event
                if (validGPS) {
                    this.previousEventData_validGPS = ev;
                } else {
                    this.previousEventData = ev;
                    if (this.previousEventData.isValidGeoPoint()) {
                        this.previousEventData_validGPS = this.previousEventData;
                    }
                }
            }
            return ev;
        } else {
            return null;
        }
    }
    
    // ------------------------------------------------------------------------

    private EventData nextEventData          = null;
    private EventData nextEventData_validGPS = null;
    
    public EventData getNextEventData(boolean validGPS)
        throws DBException
    {
        if ((!validGPS && (this.nextEventData != null)) ||
            ( validGPS && (this.nextEventData_validGPS == null))) {
            // 'startTime' should be this events timestamp, 
            // and 'additionalSelect' should be (statusCode != this.getStatusCode())
            long startTime   = this.getTimestamp() + 1L;
            long endTime     = -1L;
            EventData ed[] = EventData.getRangeEvents(
                this.getAccountID(), this.getDeviceID(),
                startTime, endTime,
                null/*statusCodes[]*/,
                validGPS,
                EventData.LimitType.FIRST, 1/*limit*/, true/*ascending*/,
                null/*additionalSelect*/);
            if ((ed != null) && (ed.length > 0)) {
                if (validGPS) {
                    this.nextEventData_validGPS = ed[0];
                } else {
                    this.nextEventData = ed[0];
                    if (this.nextEventData.isValidGeoPoint()) {
                        this.nextEventData_validGPS = this.nextEventData;
                    }
                }
            }
        }
        return validGPS? this.nextEventData_validGPS : this.nextEventData;
    }
    
    // ------------------------------------------------------------------------

    /* override DBRecord.getFieldValue(...) */
    public Object getFieldValue(String fldName)
    {
        //if ((fldName != null) && fldName.startsWith(DBRecord.PSEUDO_FIELD_CHAR)) {
        //    if (fldName.equals(EventData.PFLD_deviceDesc)) {
        //        return this.getDeviceDescription();
        //    } else {
        //        return null;
        //    }
        //} else {
            return super.getFieldValue(fldName);
        //}
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Callback when record is about to be inserted into the table
    **/
    protected void recordWillInsert()
    {
        // override to optimize (DBRecordListnener not allowed)
    }

    /**
    *** Callback after record has been be inserted into the table
    **/
    protected void recordDidInsert()
    {
        // override to optimize (DBRecordListnener not allowed)
    }

    /**
    *** Callback when record is about to be updated in the table
    **/
    protected void recordWillUpdate()
    {
        // override to optimize (DBRecordListnener not allowed)
    }

    /**
    *** Callback after record has been be updated in the table
    **/
    protected void recordDidUpdate()
    {
        // override to optimize (DBRecordListnener not allowed)
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    // [DB]WHERE ( <Condition...> )
    public static String getWhereClause(long autoIndex)
    {
        DBWhere dwh = new DBWhere(EventData.getFactory());
        dwh.append(dwh.EQ(EventData.FLD_autoIndex,autoIndex));
        return dwh.WHERE(dwh.toString());
    }
    
    // [DB]WHERE ( <Condition...> )
    public static String getWhereClause(
        String acctId, String devId,
        long timeStart, long timeEnd, 
        int statCode[], 
        boolean gpsRequired, 
        String andSelect)
    {
        DBWhere dwh = new DBWhere(EventData.getFactory());

        /* Account/Device */
        // ( (accountID='acct') AND (deviceID='dev') )
        dwh.append(dwh.AND(
            dwh.EQ(EventData.FLD_accountID,acctId),
            dwh.EQ(EventData.FLD_deviceID ,devId)
        ));

        /* status code(s) */
        // AND ( (statusCode=2) OR (statusCode=2) [OR ...] )
        if ((statCode != null) && (statCode.length > 0)) {
            dwh.append(dwh.AND_(dwh.INLIST(EventData.FLD_statusCode,statCode)));
        }

        /* gps required */
        if (gpsRequired) {
            // AND ( (latitude!=0) OR (longitude!=0) )
            // This section states that if either of the latitude/longitude are '0',
            // then do not include the record in the select.  This may not be valid
            // for all circumstances and may need better fine tuning.
            dwh.append(dwh.AND_(
                dwh.OR(
                    dwh.NE(EventData.FLD_latitude ,0L),
                    dwh.NE(EventData.FLD_longitude,0L)
                )
            ));
        }
        
        /* event time */
        if (timeStart >= 0L) {
            // AND (timestamp>=123436789)
            dwh.append(dwh.AND_(dwh.GE(EventData.FLD_timestamp,timeStart)));
        }
        if ((timeEnd >= 0L) && (timeEnd >= timeStart)) {
            // AND (timestamp<=123456789)
            dwh.append(dwh.AND_(dwh.LE(EventData.FLD_timestamp,timeEnd)));
        }
        
        /* additional selection */
        if (!StringTools.isBlank(andSelect)) {
            // AND ( ... )
            dwh.append(dwh.AND_(andSelect));
        }
        
        /* end of where */
        return dwh.WHERE(dwh.toString());
        
    }

    // ------------------------------------------------------------------------

    /* return the EventData record for the specified 'autoIndex' value */
    public static EventData getAutoIndexEvent(long autoIndex)
        throws DBException
    {
        DBFactory<EventData> dbFact = EventData.getFactory();

        /* has FLD_autoIndex? */
        if (!dbFact.hasField(EventData.FLD_autoIndex)) {
            return null;
        }
        
        /* create key */
        //DBFactory dbFact = EventData.getFactory();
        //DBRecordKey<EventData> evKey = dbFact.createKey();
        //evKey.setFieldValue(EventData.FLD_autoIndex, autoIndex);

        /* create selector */
        DBSelect<EventData> dsel = new DBSelect<EventData>(dbFact);
        dsel.setWhere(EventData.getWhereClause(autoIndex));

        /* get events */
        EventData ed[] = null;
        try {
            DBProvider.lockTables(new String[] { TABLE_NAME() }, null);
            ed = DBRecord.select(dsel, null); // select:DBSelect
        } finally {
            DBProvider.unlockTables();
        }

        /* return result */
        return !ListTools.isEmpty(ed)? ed[0] : null;

    }
    
    // ------------------------------------------------------------------------
    
    /* create range event selector */
    private static DBSelect<EventData> _createRangeEventSelector(
        String acctId, String devId,
        long timeStart, long timeEnd,
        int statCode[],
        boolean validGPS,
        EventData.LimitType limitType, long limit, boolean ascending,
        String addtnlSelect)
    {

        /* invalid account/device */
        if (StringTools.isBlank(acctId)) {
            //Print.logWarn("No AccountID specified ...");
            return null;
        } else
        if (StringTools.isBlank(devId)) {
            //Print.logWarn("No DeviceID specified ...");
            return null;
        }

        /* invalid time range */
        if ((timeStart > 0L) && (timeEnd > 0L) && (timeStart > timeEnd)) {
            //Print.logWarn("Invalid time range specified ...");
            return null;
        }

        /* ascending/descending */
        boolean isAscending = ascending;
        if ((limit > 0L) && ((limitType == null) || EventData.LimitType.LAST.equals(limitType))) {
            // NOTE: records will be in descending order (will need to reorder)
            isAscending = false;
        }

        /* create/return DBSelect */
        // DBSelect: [SELECT * FROM EventData] <Where> ORDER BY <FLD_timestamp> [DESC] LIMIT <Limit>
        DBSelect<EventData> dsel = new DBSelect<EventData>(EventData.getFactory());
        dsel.setWhere(EventData.getWhereClause(
            acctId, devId,
            timeStart, timeEnd,
            statCode,
            validGPS,
            addtnlSelect));
        dsel.setOrderByFields(FLD_timestamp);
        dsel.setOrderAscending(isAscending);
        dsel.setLimit(limit);
        return dsel;
        
    }

    /* get range of EventData records (does not return null) */
    public static EventData[] getRangeEvents(
        String acctId, String devId,
        long timeStart, long timeEnd,
        int statCode[],
        boolean validGPS,
        EventData.LimitType limitType, long limit, boolean ascending,
        String addtnlSelect)
        throws DBException
    {
        return EventData.getRangeEvents(
            acctId, devId, 
            timeStart, timeEnd,
            statCode,
            validGPS, 
            limitType, limit, ascending,
            addtnlSelect,
            null);
    }

    /* get range of EventData records (does not return null) */
    public static EventData[] getRangeEvents(
        String acctId, 
        String devId,
        long timeStart, long timeEnd,
        int statCode[],
        boolean validGPS,
        EventData.LimitType limitType, long limit, boolean ascending,
        String addtnlSelect,
        DBRecordHandler rcdHandler)
        throws DBException
    {
        
        /* get record selector */
        DBSelect<EventData> dsel = EventData._createRangeEventSelector(
            acctId, devId, 
            timeStart, timeEnd,
            statCode,
            validGPS, 
            limitType, limit, ascending,
            addtnlSelect);

        /* invalid arguments? */
        if (dsel == null) {
            return EMPTY_ARRAY;
        }
        
        /* get events */
        EventData ed[] = null;
        try {
            DBProvider.lockTables(new String[] { TABLE_NAME() }, null);
            //ed = (EventData[])DBRecord.select(EventData.getFactory(), dsel.toString(false), rcdHandler);
            ed = DBRecord.select(dsel, rcdHandler); // select:DBSelect
            // 'ed' _may_ be empty if (rcdHandler != null)
        } finally {
            DBProvider.unlockTables();
        }
        if (ed == null) {
            // no records
            return EMPTY_ARRAY;
        } else
        if (dsel.isOrderAscending() == ascending) {
            // records are in requested order, return as-is
            return ed;
        } else {
            // records are in descending order
            // reorder to ascending order
            int lastNdx = ed.length - 1;
            for (int i = 0; i < ed.length / 2; i++) {
                EventData edrcd = ed[i];
                ed[i] = ed[lastNdx - i];
                ed[lastNdx - i] = edrcd;
            }
            return ed;
        }

    }

    /* return count in range of EventData records */
    public static long countRangeEvents(
        String acctId, String devId,
        long timeStart, long timeEnd,
        int statCode[],
        boolean validGPS,
        EventData.LimitType limitType, long limit,
        String addtnlSelect)
        throws DBException
    {
        
        /* get record selector */
        DBSelect<EventData> dsel = EventData._createRangeEventSelector(
            acctId, devId, 
            timeStart, timeEnd,
            statCode,
            validGPS, 
            limitType, limit, true,
            addtnlSelect);

        /* invalid arguements? */
        if (dsel == null) {
            return 0L;
        }

        /* count events */
        long recordCount = 0L;
        try {
            DBProvider.lockTables(new String[] { TABLE_NAME() }, null);
            recordCount = DBRecord.getRecordCount(dsel);
        } finally {
            DBProvider.unlockTables();
        }
        return recordCount;

    }

    public static long getRecordCount(
        String acctId, String devId,
        long timeStart, long timeEnd)
        throws DBException
    {
        StringBuffer wh = new StringBuffer();
        wh.append(EventData.getWhereClause(
            acctId, devId,
            timeStart, timeEnd,
            null  /*statCode[]*/ ,
            false /*gpsRequired*/,
            null  /*andSelect*/  ));
        return DBRecord.getRecordCount(EventData.getFactory(), wh);
    }

    // ------------------------------------------------------------------------

    private static class GPSDistanceAccumulator
        implements DBRecordHandler
    {
        private double accumKM = 0.0;
        private GeoPoint startingGP = null;
        private EventData lastEvent = null;
        public GPSDistanceAccumulator() {
        }
        public GPSDistanceAccumulator(GeoPoint startingGP, double startingOdomKM) {
            this();
            this.startingGP = startingGP;
            this.accumKM = startingOdomKM;
        }
        public int handleDBRecord(DBRecord rcd) throws DBException 
        {
            EventData ev = (EventData)rcd;
            if (this.lastEvent != null) {
                GeoPoint lastGP = this.lastEvent.getGeoPoint();
                GeoPoint thisGP = ev.getGeoPoint();
                double distKM = lastGP.kilometersToPoint(thisGP);
                this.accumKM += distKM;
            } else
            if (this.startingGP != null) {
                GeoPoint thisGP = ev.getGeoPoint();
                double distKM = this.startingGP.kilometersToPoint(thisGP);
                this.accumKM += distKM;
            }
            this.lastEvent = ev;
            return DBRH_SKIP;
        }
        public void clearGPSDistanceTraveled() {
            this.accumKM = 0.0;
        }
        public double getGPSDistanceTraveledKM() {
            return this.accumKM;
        }
    }
    
    public static double getGPSDistanceTraveledKM(String acctId, String devId,
        long timeStart, long timeEnd,
        GeoPoint startingGP, double startingOdomKM)
    {
        
        /* record handler */
        GPSDistanceAccumulator rcdHandler = new GPSDistanceAccumulator(startingGP, startingOdomKM);
        
        /* look through events */
        try {
            EventData.getRangeEvents(
                acctId, devId,
                timeStart, timeEnd,
                null/*StatusCodes*/,
                true/*validGPS*/,
                EventData.LimitType.LAST, -1L/*limit*/, true/*ascending*/,
                null/*addtnlSelect*/,
                rcdHandler);
        } catch (DBException dbe) {
            Print.logException("Calculating GPS distance traveled", dbe);
        }

        /* return distance */
        return rcdHandler.getGPSDistanceTraveledKM();
        
    }
 
    // ------------------------------------------------------------------------

    public static DateTime parseDate(String dateStr, TimeZone tz)
    {
        // Formats:
        //   YYYY/MM[/DD[/hh[:mm[:ss]]]]
        //   eeeeeeeeeee
        String dateFld[] = StringTools.parseString(dateStr, "/:");
        if ((dateFld == null) || (dateFld.length == 0)) {
            return null; // no date specified
        } else
        if (dateFld.length == 1) {
            // parse as 'Epoch' time
            long epoch = StringTools.parseLong(dateFld[0], -1L);
            return (epoch > 0L)? new DateTime(epoch,tz) : null;
        } else {
            // (dateFld.length >= 2)
            int YY = StringTools.parseInt(dateFld[0], -1); // 1900..2007+
            int MM = StringTools.parseInt(dateFld[1], -1); // 1..12
            if ((YY < 1900) || (MM < 1) || (MM > 12)) {
                return null;
            } else {
                int DD = 1;
                int hh = 0, mm = 0, ss = 0;    // default to beginning of day
                if (dateFld.length >= 3) {
                    // at least YYYY/MM/DD provided
                    DD = StringTools.parseInt(dateFld[2], -1);
                    if (DD < 1) {
                        DD = 1;
                    } else
                    if (DD > DateTime.getDaysInMonth(tz,MM,YY)) {
                        DD = DateTime.getDaysInMonth(tz,MM,YY);
                    } else {
                        if (dateFld.length >= 4) { hh = StringTools.parseInt(dateFld[3], 0); }
                        if (dateFld.length >= 5) { mm = StringTools.parseInt(dateFld[4], 0); }
                        if (dateFld.length >= 6) { ss = StringTools.parseInt(dateFld[5], 0); }
                    }
                } else {
                    // only YYYY/MM provided
                    DD = 1; // first day of month
                }
                return new DateTime(tz, YY, MM, DD, hh, mm, ss);
            }
        }
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // This section support a method for obtaining human readable information from the EventData
    // record for reporting, or email purposes. (currently this is used by the 'rules' engine
    // when generating notification emails).
    
    private static final String KEY_ACCOUNT[]       = new String[] { "account"          };                  // "opendmtp"
    private static final String KEY_DEVICE[]        = new String[] { "device"           };                  // "mobile"
    private static final String KEY_DATETIME[]      = new String[] { "dateTime"      , "date"           };  // "2007/08/09 03:02:51 GMT"
    private static final String KEY_DATE_YEAR[]     = new String[] { "dateYear"      , "year"           };  // "2007"
    private static final String KEY_DATE_MONTH[]    = new String[] { "dateMonth"     , "month"          };  // "January"
    private static final String KEY_DATE_DAY[]      = new String[] { "dateDay"       , "day"            };  // "23"
    private static final String KEY_DATE_DOW[]      = new String[] { "dateDow"       , "dayOfWeek"      };  // "Monday"
    private static final String KEY_TIME[]          = new String[] { "time"             };                  // "03:02:51"
    private static final String KEY_STATUSDESC[]    = new String[] { "status"           };                  // "Location"
    private static final String KEY_ENTITYID[]      = new String[] { "entityID"         };                  // "t1234"
    private static final String KEY_ENTITY[]        = new String[] { "entity"        , "entityDesc"     };  // "Trailer 1234"
    private static final String KEY_DEVENTITIES[]   = new String[] { "deviceEntities", "deviceTrailers" };  // "Trailer 1234, Trailer 4321"
    private static final String KEY_DRIVERID[]      = new String[] { "driverID"         };                  // "smith"
    private static final String KEY_DRIVER[]        = new String[] { "driver"        , "driverDesc"     };  // "Joe Smith"
    private static final String KEY_GEOPOINT[]      = new String[] { "geopoint"         };                  // "39.12345,-142.12345"
    private static final String KEY_LATITUDE[]      = new String[] { "latitude"         };                  // "39.12345"
    private static final String KEY_LONGITUDE[]     = new String[] { "longitude"        };                  // "-142.12345"
    private static final String KEY_SPEED[]         = new String[] { "speed"            };                  // "34.9 mph"
    private static final String KEY_SPEED_LIMIT[]   = new String[] { "speedLimit"       };                  // "45.0 mph"
    private static final String KEY_DIRECTION[]     = new String[] { "direction"     , "compass"        };  // "SE"
    private static final String KEY_HEADING[]       = new String[] { "heading"       , "bearing"        , "course" };  // "123.4"
    private static final String KEY_ODOMETER[]      = new String[] { "odometer"         };                  // "1234 Miles"
    private static final String KEY_DISTANCE[]      = new String[] { "distance"         };                  // "1234 Miles"
    private static final String KEY_ALTITUDE[]      = new String[] { "alt"           , "altitude"       };  // "12345 feet"
    private static final String KEY_J1708FAULT[]    = new String[] { "j1708Fault"       };                  // 
    private static final String KEY_J1708FTYPE[]    = new String[] { "j1708FType"       };                  // 
    private static final String KEY_J1708FDESC[]    = new String[] { "j1708FDesc"       };                  // 
    private static final String KEY_GEOZONEID[]     = new String[] { "geozoneID"        };                  // "home"
    private static final String KEY_GEOZONE[]       = new String[] { "geozone"          };                  // "Home Base"
    private static final String KEY_ADDRESS[]       = new String[] { "fullAddress"   , "address"        };  // "1234 Somewhere Lane, Smithsville, CA 99999"
    private static final String KEY_STREETADDR[]    = new String[] { "streetAddress" , "street"         };  // "1234 Somewhere Lane"
    private static final String KEY_CITY[]          = new String[] { "city"             };                  // "Smithsville"
    private static final String KEY_STATE[]         = new String[] { "state"         , "province"       };  // "CA"
    private static final String KEY_POSTALCODE[]    = new String[] { "postalCode"    , "zipCode"        };  // "98765"
    private static final String KEY_SUBDIVISION[]   = new String[] { "subdivision"   , "subdiv"         };  // "US/CA"

    private static boolean _keyMatch(String fn, String k[])
    {
        for (int i = 0; i < k.length; i++) {
            if (fn.equalsIgnoreCase(k[i])) {
                return true;
            }
        }
        return false;
    }

    public String getFieldValueString(String fn, BasicPrivateLabel bpl)
    {
        if (fn != null) {
            Locale locale = (bpl != null)? bpl.getLocale() : null;
            if (_keyMatch(fn,KEY_ACCOUNT)) {
                Account account = this.getAccount();
                return (account != null)? account.getDescription() : this.getAccountID();
            } else
            if (_keyMatch(fn,KEY_DEVICE)) {
                Device device = this.getDevice();
                return (device != null)? device.getDescription() : this.getDeviceID();
            } else
            if (_keyMatch(fn,KEY_DATETIME)) {
                return this.getTimestampString();
            } else
            if (_keyMatch(fn,KEY_DATE_YEAR)) {
                return this.getTimestampYear();
            } else
            if (_keyMatch(fn,KEY_DATE_MONTH)) {
                return this.getTimestampMonth(false, locale);
            } else
            if (_keyMatch(fn,KEY_DATE_DAY)) {
                return this.getTimestampDayOfMonth();
            } else
            if (_keyMatch(fn,KEY_DATE_DOW)) {
                return this.getTimestampDayOfWeek(false, locale);
            } else
            if (_keyMatch(fn,KEY_TIME)) {
                return this.getTimestampTime();
            } else
            if (_keyMatch(fn,KEY_STATUSDESC)) {
                return this.getStatusCodeDescription(bpl);
            } else
            if (_keyMatch(fn,KEY_ENTITYID)) {
                return this.getEntityID();
            } else
            if (_keyMatch(fn,KEY_ENTITY)) {
                String aid = this.getAccountID();
                String eid = this.getEntityID();
                return Device.getEntityDescription(aid, eid);
            } else
            if (_keyMatch(fn,KEY_DEVENTITIES)) {
                Device device = this.getDevice();
                if (device != null) {
                    String e[] = device.getAttachedEntityDescriptions();
                    if ((e != null) && (e.length > 0)) {
                        StringBuffer sb = new StringBuffer();
                        for (int i = 0; i < e.length; i++) { 
                            if (i > 0) { sb.append(","); }
                            sb.append(e[i]);
                        }
                        return sb.toString();
                    } else {
                        return "";
                    }
                } else {
                    return "";
                }
            } else
            if (_keyMatch(fn,KEY_DRIVERID)) {
                return this.getDriverID();
            } else
            if (_keyMatch(fn,KEY_DRIVER)) {
                String did = this.getEntityID();
                return did; // TODO: get driver description
            } else
            if (_keyMatch(fn,KEY_GEOPOINT)) {
                Account.LatLonFormat latlonFmt = Account.getLatLonFormat(this.getAccount());
                double lat = this.getLatitude();
                double lon = this.getLongitude();
                String fmt = latlonFmt.isDegMinSec()? "DMS" : latlonFmt.isDegMin()? "DM" : "5";
                String latStr = GeoPoint.formatLatitude( lat, fmt, locale);
                String lonStr = GeoPoint.formatLongitude(lon, fmt, locale);
                return latStr + GeoPoint.PointSeparator + lonStr;
            } else
            if (_keyMatch(fn,KEY_LATITUDE)) {
                Account.LatLonFormat latlonFmt = Account.getLatLonFormat(this.getAccount());
                double lat = this.getLatitude();
                String fmt = latlonFmt.isDegMinSec()? "DMS" : latlonFmt.isDegMin()? "DM" : "5";
                return GeoPoint.formatLatitude(lat, fmt, locale);
            } else
            if (_keyMatch(fn,KEY_LONGITUDE)) {
                Account.LatLonFormat latlonFmt = Account.getLatLonFormat(this.getAccount());
                double lon = this.getLongitude();
                String fmt = latlonFmt.isDegMinSec()? "DMS" : latlonFmt.isDegMin()? "DM" : "5";
                return GeoPoint.formatLongitude(lon, fmt, locale);
            } else
            if (_keyMatch(fn,KEY_SPEED)) {
                double kph = this.getSpeedKPH();
                Account account = this.getAccount();
                if (account != null) {
                    return account.getSpeedString(kph,true,locale);
                } else {
                    return StringTools.format(kph,"0") + " " + Account.SpeedUnits.KPH.toString(locale);
                }
            } else
            if (_keyMatch(fn,KEY_SPEED_LIMIT)) {
                double kph = this.getSpeedLimitKPH();
                Account account = this.getAccount();
                if (account != null) {
                    return account.getSpeedString(kph,true,locale);
                } else {
                    return StringTools.format(kph,"0") + " " + Account.SpeedUnits.KPH.toString(locale);
                }
            } else
            if (_keyMatch(fn,KEY_DIRECTION)) {
                return GeoPoint.GetHeadingString(this.getHeading(),locale);
            } else
            if (_keyMatch(fn,KEY_HEADING)) {
                return StringTools.format(this.getHeading(),"0.0");
            } else
            if (_keyMatch(fn,KEY_ODOMETER)) {
                double  odomKM  = this.getOdometerKM();
                Device  device  = this.getDevice();
                if (device != null) {
                    odomKM += device.getOdometerOffsetKM(); // ok
                }
                Account account = this.getAccount();
                if (account != null) {
                    return account.getDistanceString(odomKM, true, locale);
                } else {
                    return StringTools.format(odomKM,"0") + " " + Account.DistanceUnits.KM.toString(locale);
                }
            } else
            if (_keyMatch(fn,KEY_DISTANCE)) {
                double distKM = this.getDistanceKM();
                Account account = this.getAccount();
                if (account != null) {
                    return account.getDistanceString(distKM, true, locale);
                } else {
                    return StringTools.format(distKM,"0") + " " + Account.DistanceUnits.KM.toString(locale);
                }
            } else
            if (_keyMatch(fn,KEY_ALTITUDE)) {
                return this.getAltitudeString(true, locale);
            } else
            if (_keyMatch(fn,KEY_J1708FAULT)) {
                long j1708Fault = this.getFieldValue(EventData.FLD_j1708Fault, 0L);
                return DTJ1708Fault.GetJ1708FaultString(j1708Fault, locale);
            } else
            if (_keyMatch(fn,KEY_J1708FTYPE)) {
                long j1708Fault = this.getFieldValue(EventData.FLD_j1708Fault, 0L);
                return DTJ1708Fault.GetJ1708FaultType(j1708Fault);
            } else
            if (_keyMatch(fn,KEY_J1708FDESC)) {
                long j1708Fault = this.getFieldValue(EventData.FLD_j1708Fault, 0L);
                return DTJ1708Fault.GetJ1708FaultDescription(j1708Fault, locale);
            } else
            if (_keyMatch(fn,KEY_GEOZONEID)) {
                return this.getGeozoneID();
            } else
            if (_keyMatch(fn,KEY_GEOZONE)) {
                return this.getGeozoneDescription();
            } else
            if (_keyMatch(fn,KEY_ADDRESS)) {
                return this.getAddress();
            } else
            if (_keyMatch(fn,KEY_STREETADDR)) {
                return this.getStreetAddress();
            } else
            if (_keyMatch(fn,KEY_CITY)) {
                return this.getCity();
            } else
            if (_keyMatch(fn,KEY_STATE)) {
                return this.getStateProvince();
            } else
            if (_keyMatch(fn,KEY_POSTALCODE)) {
                return this.getPostalCode();
            } else
            if (_keyMatch(fn,KEY_SUBDIVISION)) {
                return this.getSubdivision();
            } else {
                // "statusCode", etc.
                String fldName = this.getFieldName(fn); // this gets the field name with proper case
                DBField dbFld = (fldName != null)? this.getRecordKey().getField(fldName) : null;
                if (dbFld != null) {
                    Object val = this.getFieldValue(fldName);
                    if (val != null) {
                        return dbFld.formatValue(val);
                    } else {
                        return dbFld.formatValue(dbFld.getDefaultValue());
                    }
                }
                // field not found
            }
        }
        return null;
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    
    /* singleton instance of DeviceDescriptionComparator */
    private static Comparator<EventData> devDescComparator = null;
    public static Comparator<EventData> getDeviceDescriptionComparator()
    {
        if (devDescComparator == null) {
            devDescComparator = new DeviceDescriptionComparator(); // ascending
        }
        return devDescComparator;
    }
    
    /* Comparator optimized for EventData device description */
    public static class DeviceDescriptionComparator
        implements Comparator<EventData>
    {
        private boolean ascending = true;
        public DeviceDescriptionComparator() {
            this(true);
        }
        public DeviceDescriptionComparator(boolean ascending) {
            this.ascending  = ascending;
        }
        public int compare(EventData o1, EventData o2) {
            // assume we are comparing EventData records
            String D1 = o1.getDeviceDescription(); // ev1.getDeviceID();
            String D2 = o2.getDeviceDescription(); // ev2.getDeviceID();
            return this.ascending? D1.compareTo(D2) : D2.compareTo(D1);
        }
        public boolean equals(Object other) {
            if (other instanceof DeviceDescriptionComparator) {
                DeviceDescriptionComparator ddc = (DeviceDescriptionComparator)other;
                return (this.ascending == ddc.ascending);
            }
            return false;
        }
    }

    /* generic field comparator */
    // Note: This comparator has not been tested yet
    public static class FieldComparator
        implements Comparator<EventData>
    {
        private boolean ascending = true;
        private String  fieldName = "";
        public FieldComparator(String fldName) {
            super();
            this.ascending = true;
            this.fieldName = (fldName != null)? fldName : "";
        }
        public int compare(EventData o1, EventData o2) {
            EventData ed1 = o1;
            EventData ed2 = o2;
            if (ed1 == ed2) {
                return 0;
            } else
            if (ed1 == null) {
                return this.ascending? -1 : 1;
            } else
            if (ed2 == null) {
                return this.ascending? 1 : -1;
            }
            Object v1 = ed1.getFieldValue(this.fieldName);
            Object v2 = ed2.getFieldValue(this.fieldName);
            if (v1 == v2) {
                return 0;
            } else
            if (v1 == null) {
                return this.ascending? -1 : 1;
            } else 
            if (v2 == null) {
                return this.ascending? 1 : -1;
            } else 
            if (v1.equals(v2)) {
                return 0;
            } else
            if ((v1 instanceof Number) && (v2 instanceof Number)) {
                double d = ((Number)v2).doubleValue() - ((Number)v1).doubleValue();
                if (d > 0.0) {
                    return this.ascending? 1 : -1;
                } else
                if (d < 0.0) {
                    return this.ascending? -1 : 1;
                } else {
                    return 0;
                }
            } else {
                String s1 = v1.toString();
                String s2 = v2.toString();
                return this.ascending? s1.compareTo(s2) : s2.compareTo(s1);
            }
        }
        public boolean equals(Object other) {
            if (other instanceof FieldComparator) {
                FieldComparator edc = (FieldComparator)other;
                if (this.ascending != edc.ascending) {
                    return false;
                } else
                if (!this.fieldName.equals(edc.fieldName)) {
                    return false;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

}
