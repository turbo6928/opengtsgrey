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
// Description:
//  Report definition based on EventData table
// ----------------------------------------------------------------------------
// Change History:
//  2007/03/11  Martin D. Flynn
//     -Initial release
//  2007/01/10  Martin D. Flynn
//     -Added fields 'checkinDateTime', 'checkinAge'
//  2008/02/04  Martin D. Flynn
//     -Added fields 'engineRpm', 'fuelUsed'
//  2008/03/12  Martin D. Flynn
//     -Added additional decimal point options to various fields
//  2008/04/11  Martin D. Flynn
//     -Added color indicator to lat/lon when gps age is over a given threshold
//     -Added field 'gpsAge'
//  2008/05/14  Martin D. Flynn
//     -Added City/State/Country/Subdivision fields
//  2008/10/16  Martin D. Flynn
//     -Added battery level field.
//     -Added input mask field.
//  2009/01/01  Martin D. Flynn
//     -Added arguments to "heading" to allow displaying in degrees
// ----------------------------------------------------------------------------
package org.opengts.war.report.event;

import java.util.*;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;

import org.opengts.dbtypes.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.war.tools.RequestProperties;
import org.opengts.war.tools.WebPageAdaptor;

import org.opengts.war.report.*;

public class EventDataLayout
    extends ReportLayout
{

    // ------------------------------------------------------------------------

    // TODO: these colors/thresholds should be configurable at runtime
    private static final long   GPS_AGE_THRESHOLD_1     = DateTime.MinuteSeconds(60);
    private static final String GPS_AGE_COLOR_1         = "#BB0000";  // lighter red
    private static final long   GPS_AGE_THRESHOLD_2     = DateTime.MinuteSeconds(20);
    private static final String GPS_AGE_COLOR_2         = "#550000";  // darker red

    // ------------------------------------------------------------------------

    private static final long  MINIMUM_REASONABLE_TIMESTAMP = (new DateTime(null,2000,1,1)).getTimeSec();

    // ------------------------------------------------------------------------
    // Data keys
    // - These define what data is available (see 'EventDataRow') and what columns will be 
    //   displayed in the table.
    // - Column names must only contain <alpha>/<numeric>/'_' characters
    
    public static final String  DATA_INDEX              = "index";
    public static final String  DATA_DEVICE_ID          = "deviceId";
    public static final String  DATA_DEVICE_DESC        = "deviceDesc";
    public static final String  DATA_GROUP_ID           = "groupId";
    public static final String  DATA_STATUS_CODE        = "statusCode";
    public static final String  DATA_STATUS_DESC        = "statusDesc";
    public static final String  DATA_GPS_AGE            = "gpsAge";
    public static final String  DATA_LATITUDE           = "latitude";
    public static final String  DATA_LONGITUDE          = "longitude";
    public static final String  DATA_GEOPOINT           = "geoPoint";
    public static final String  DATA_ALTITUDE           = "altitude";
    public static final String  DATA_SATELLITES         = "satellites";
    public static final String  DATA_BATTERY_LEVEL      = "battery";
    public static final String  DATA_SPEED_LIMIT        = "speedLimit";
    public static final String  DATA_SPEED              = "speed";
    public static final String  DATA_SPEED_HEADING      = "speedH";
    public static final String  DATA_SPEED_UNITS        = "speedU";
    public static final String  DATA_HEADING            = "heading";
    public static final String  DATA_DISTANCE           = "distance";
    public static final String  DATA_ODOMETER           = "odometer";

    public static final String  DATA_DATE               = "date";
    public static final String  DATA_TIME               = "time";
    public static final String  DATA_DATETIME           = "dateTime";
    public static final String  DATA_TIMESTAMP          = "timestamp";

    public static final String  DATA_CREATE_DATE        = "createDate";
    public static final String  DATA_CREATE_TIME        = "createTime";
    public static final String  DATA_CREATE_DATETIME    = "createDateTime";
    public static final String  DATA_CREATE_TIMESTAMP   = "createTimestamp";

    public static final String  DATA_INPUT_STATE        = "inputState";
    
    public static final String  DATA_GEOZONE_ID         = "geozoneId";
    public static final String  DATA_GEOZONE_DESC       = "geozoneDesc";

    public static final String  DATA_ADDRESS            = "address";
    public static final String  DATA_CITY               = "city";
    public static final String  DATA_STATE              = "state";
    public static final String  DATA_COUNTRY            = "country";
    public static final String  DATA_SUBDIVISION        = "subdivision";

    public static final String  DATA_ENTITY_ID          = "entityId";
    public static final String  DATA_ENTITY_DESC        = "entityDesc";
    public static final String  DATA_DRIVER_ID          = "driverId";
    public static final String  DATA_DRIVER_DESC        = "driverDesc";
    public static final String  DATA_DRIVER_MESSAGE     = "driverMessage";
    public static final String  DATA_JOB_NUMBER         = "jobNumber";

    public static final String  DATA_THERMO_1           = "thermo1";
    public static final String  DATA_THERMO_2           = "thermo2";
    public static final String  DATA_THERMO_3           = "thermo3";
    public static final String  DATA_THERMO_4           = "thermo4";
    public static final String  DATA_THERMO_5           = "thermo5";
    public static final String  DATA_THERMO_6           = "thermo6";
    public static final String  DATA_THERMO_7           = "thermo7";
    public static final String  DATA_THERMO_8           = "thermo8";

    public static final String  DATA_FUEL_LEVEL         = "fuelLevel";
    public static final String  DATA_FUEL_ECONOMY       = "fuelEconomy";
    public static final String  DATA_FUEL_TOTAL         = "fuelTotal";
    public static final String  DATA_FUEL_IDLE          = "fuelIdle";
    public static final String  DATA_J1708_FAULT        = "j1708Fault";
    public static final String  DATA_ENGINERPM          = "engineRpm";
    public static final String  DATA_BRAKE_G_FORCE      = "brakeGForce";
    
    public static final String  DATA_CHECKIN_DATETIME   = "checkinDateTime";    // Device record
    public static final String  DATA_CHECKIN_AGE        = "checkinAge";         // Device record
    public static final String  DATA_CUSTOM_FIELD       = "customField";        // Device record

    public static final String  DATA_RAW_DATA           = "rawData";

    // ------------------------------------------------------------------------
    // EventDataLayout is a singleton

    private static EventDataLayout reportDef = null;

    /**
    *** Gets the EventDataLayout singleton instance
    *** @return The EventDataLayout singleton instance
    **/
    public static ReportLayout getReportLayout()
    {
        if (reportDef == null) {
            reportDef = new EventDataLayout();
        }
        return reportDef;
    }
    
    /**
    *** Standard singleton constructor
    **/
    private EventDataLayout()
    {
        super();
        this.setDataRowTemplate(new EventDataRow());
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    
    /* format double value */
    protected static String formatDouble(double value, String arg, String dftArg)
    {
        String fmt = dftArg;
        if (!StringTools.isBlank(arg)) {
            switch (arg.charAt(0)) {
                case '0': fmt = "0"        ; break;
                case '1': fmt = "0.0"      ; break;
                case '2': fmt = "0.00"     ; break;
                case '3': fmt = "0.000"    ; break;
                case '4': fmt = "0.0000"   ; break;
                case '5': fmt = "0.00000"  ; break;
                case '6': fmt = "0.000000" ; break;
                case '7': fmt = "0.0000000"; break;
            }
        }
        return StringTools.format(value, fmt);
    }
    
    // ------------------------------------------------------------------------

    /* format temperatures */
    protected static String formatTemperature(double thermoC, String arg, ReportData rd)
    {
        if (EventData.isValidTemperature(thermoC)) {
            Account a       = rd.getAccount();
            double thermo   = Account.getTemperatureUnits(a).convertFromC(thermoC);
            String unitAbbr = Account.getTemperatureUnits(a).toString(rd.getLocale());
            return formatDouble(thermo, arg, "0.0") + unitAbbr;
        } else {
            return "n/a";
        }
    }

    /* format kilometer distance */
    protected static String formatKM(double dist, String arg, ReportData rd)
    {
        if (dist > 0.0) {
            dist = Account.getDistanceUnits(rd.getAccount()).convertFromKM(dist);
            return formatDouble(dist, arg, "0");
        } else {
            return "";
        }
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    protected static class EventDataRow
        extends DataRowTemplate
    {
        public EventDataRow() {
            super();
            
            // Index
            this.addColumnTemplate(new DataColumnTemplate(DATA_INDEX) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    int ofs = 1;
                    if ((arg != null) && (arg.length() > 0) && (arg.charAt(0) == '0')) {
                        ofs = 0;
                    }
                    return String.valueOf(rowNdx + ofs);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    return "#";
                }
            });

            // Device-ID
            this.addColumnTemplate(new DataColumnTemplate(DATA_DEVICE_ID) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getDeviceID();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.deviceID","Device-ID");
                }
            });
            
            // Device Description
            this.addColumnTemplate(new DataColumnTemplate(DATA_DEVICE_DESC) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Device dev = ed.getDevice();
                    return (dev != null)? dev.getDescription() : "";
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.deviceDescription","Device\nDescription");
                }
            });

            // Group-ID
            this.addColumnTemplate(new DataColumnTemplate(DATA_GROUP_ID) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Device dev = ed.getDevice();
                    return (dev != null)? dev.getGroupID() : "";
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.groupID","Group-ID");
                }
            });

            // Event timestamp Date/Time
            this.addColumnTemplate(new DataColumnTemplate(DATA_DATE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long ts = ed.getTimestamp();
                    if (ts > 0L) {
                        ReportLayout rl = rd.getReportLayout();
                        //Account a = rd.getAccount();
                        //TimeZone tz = (a != null)? TimeZone.getTimeZone(a.getTimeZone()) : null;
                        TimeZone tz = rd.getTimeZone();
                        DateTime dt = new DateTime(ts);
                        String dtFmt = dt.format(rl.getDateFormat(rd.getPrivateLabel()), tz);
                        return new ColumnValue(dtFmt).setSortKey(ts);
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.date","Date");
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_TIME) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long ts = ed.getTimestamp();
                    if (ts > 0L) {
                        ReportLayout rl = rd.getReportLayout();
                        //Account a = rd.getAccount();
                        //TimeZone tz = (a != null)? TimeZone.getTimeZone(a.getTimeZone()) : null;
                        TimeZone tz = rd.getTimeZone();
                        DateTime dt = new DateTime(ts);
                        return dt.format(rl.getTimeFormat(rd.getPrivateLabel()), tz);
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.time","Time");
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_DATETIME) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long ts = ed.getTimestamp();
                    if (ts > 0L) {
                        ReportLayout rl = rd.getReportLayout();
                        //Account a = rd.getAccount();
                        //TimeZone tz = (a != null)? TimeZone.getTimeZone(a.getTimeZone()) : null;
                        TimeZone tz  = rd.getTimeZone();
                        DateTime dt  = new DateTime(ts);
                        String dtFmt = dt.format(rl.getDateTimeFormat(rd.getPrivateLabel()), tz);
                        return new ColumnValue(dtFmt).setSortKey(ts);
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.dateTime","Date/Time") + "\n${timezone}";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_TIMESTAMP) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long ts = ed.getTimestamp();
                    return String.valueOf(ts);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.timestamp","Timestamp") + "\n(Epoch)";
                }
            });

            // Event creation Date/Time
            this.addColumnTemplate(new DataColumnTemplate(DATA_CREATE_DATE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long ts = ed.getCreationTime();
                    if (ts > 0L) {
                        ReportLayout rl = rd.getReportLayout();
                        //Account a = rd.getAccount();
                        //TimeZone tz = (a != null)? TimeZone.getTimeZone(a.getTimeZone()) : null;
                        TimeZone tz  = rd.getTimeZone();
                        DateTime dt  = new DateTime(ts);
                        String dtFmt = dt.format(rl.getDateFormat(rd.getPrivateLabel()), tz);
                        return new ColumnValue(dtFmt).setSortKey(ts);
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.createDate","Insert\nDate");
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_CREATE_TIME) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long ts = ed.getCreationTime();
                    if (ts > 0L) {
                        ReportLayout rl = rd.getReportLayout();
                        //Account a = rd.getAccount();
                        //TimeZone tz = (a != null)? TimeZone.getTimeZone(a.getTimeZone()) : null;
                        TimeZone tz = rd.getTimeZone();
                        DateTime dt = new DateTime(ts);
                        return dt.format(rl.getTimeFormat(rd.getPrivateLabel()), tz);
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.createTime","insert\nTime");
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_CREATE_DATETIME) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long ts = ed.getCreationTime();
                    if (ts > 0L) {
                        ReportLayout rl = rd.getReportLayout();
                        //Account a = rd.getAccount();
                        //TimeZone tz = (a != null)? TimeZone.getTimeZone(a.getTimeZone()) : null;
                        TimeZone tz  = rd.getTimeZone();
                        DateTime dt  = new DateTime(ts);
                        String dtFmt = dt.format(rl.getDateTimeFormat(rd.getPrivateLabel()), tz);
                        return new ColumnValue(dtFmt).setSortKey(ts);
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.createDateTime","Insert\nDate/Time") + "\n${timezone}";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_CREATE_TIMESTAMP) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long ts = ed.getCreationTime();
                    return String.valueOf(ts);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.createTimestamp","Insert\nTimestamp") + "\n(Epoch)";
                }
            });

            // Status Code/Description
            this.addColumnTemplate(new DataColumnTemplate(DATA_STATUS_CODE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return "0x" + StringTools.toHexString((long)ed.getStatusCode(),16);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.statusCode","Status#");
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_STATUS_DESC) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getStatusCodeDescription(rd.getPrivateLabel());
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.statusDescription","Status");
                }
            });

            // Entity ID
            this.addColumnTemplate(new DataColumnTemplate(DATA_ENTITY_ID) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getEntityID();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.entityID","Entity-ID");
                }
            });

            // Entity Description
            this.addColumnTemplate(new DataColumnTemplate(DATA_ENTITY_DESC) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    String aid = ed.getAccountID();
                    String eid = ed.getEntityID();
                    return Device.getEntityDescription(aid, eid);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.entityDescription","Entity\nDescription");
                }
            });

            // Driver ID
            this.addColumnTemplate(new DataColumnTemplate(DATA_DRIVER_ID) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getDriverID();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.driverID","Driver-ID");
                }
            });

            // Driver Description (currently same as DriverID)
            this.addColumnTemplate(new DataColumnTemplate(DATA_DRIVER_DESC) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    String did = ed.getDriverID();
                    return did;
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.driverDescription","Driver\nDescription");
                }
            });

            // Driver Message
            this.addColumnTemplate(new DataColumnTemplate(DATA_DRIVER_MESSAGE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getDriverMessage(); // may be blank
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.driverMessage","Driver\nMessage");
                }
            });

            // JobNumber
            this.addColumnTemplate(new DataColumnTemplate(DATA_JOB_NUMBER) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getJobNumber(); // may be blank
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.jobNumber","Job\nNumber");
                }
            });

            // GPS Age
            this.addColumnTemplate(new DataColumnTemplate(DATA_GPS_AGE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long gpsAge = ed.getGpsAge();
                    if (gpsAge >= GPS_AGE_THRESHOLD_1) {
                        return (new ColumnValue(gpsAge)).setForegroundColor(GPS_AGE_COLOR_1).setFontStyleItalic();
                    } else
                    if (gpsAge >= GPS_AGE_THRESHOLD_2) {
                        return (new ColumnValue(gpsAge)).setForegroundColor(GPS_AGE_COLOR_2);
                    } else {
                        return String.valueOf(gpsAge);
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.gpsAge","GPS\nAge");
                }
            });

            // Latitude
            this.addColumnTemplate(new DataColumnTemplate(DATA_LATITUDE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Locale locale = rd.getLocale();
                    double lat = ed.getLatitude();
                    arg = StringTools.trim(arg);
                    String valStr = "";
                    Account.LatLonFormat latlonFmt = Account.getLatLonFormat(rd.getAccount());
                    if (arg.equalsIgnoreCase("dms") || (StringTools.isBlank(arg) && latlonFmt.isDegMinSec())) {
                        valStr = GeoPoint.formatLatitude(lat, "DMS", locale);
                    } else
                    if (arg.equalsIgnoreCase("dm")  || (StringTools.isBlank(arg) && latlonFmt.isDegMin())) {
                        valStr = GeoPoint.formatLatitude(lat, "DM" , locale);
                    } else {
                        String fmt = StringTools.isBlank(arg)? "4" : arg;
                        valStr = GeoPoint.formatLatitude(lat, fmt  , locale);
                    }
                    long gpsAge = ed.getGpsAge();
                    if (gpsAge >= GPS_AGE_THRESHOLD_1) {
                        return (new ColumnValue(valStr)).setForegroundColor(GPS_AGE_COLOR_1).setFontStyleItalic();
                    } else
                    if (gpsAge >= GPS_AGE_THRESHOLD_2) {
                        return (new ColumnValue(valStr)).setForegroundColor(GPS_AGE_COLOR_2);
                    } else {
                        return valStr;
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.lat","Lat");
                }
            });
            
            // Longitude
            this.addColumnTemplate(new DataColumnTemplate(DATA_LONGITUDE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Locale locale = rd.getLocale();
                    double lon = ed.getLongitude();
                    arg = StringTools.trim(arg);
                    String valStr = "";
                    Account.LatLonFormat latlonFmt = Account.getLatLonFormat(rd.getAccount());
                    if (arg.equalsIgnoreCase("dms") || (StringTools.isBlank(arg) && latlonFmt.isDegMinSec())) {
                        valStr = GeoPoint.formatLongitude(lon, "DMS", locale);
                    } else
                    if (arg.equalsIgnoreCase("dm")  || (StringTools.isBlank(arg) && latlonFmt.isDegMin())) {
                        valStr = GeoPoint.formatLongitude(lon, "DM" , locale);
                    } else {
                        String fmt = StringTools.isBlank(arg)? "4" : arg;
                        valStr = GeoPoint.formatLongitude(lon, fmt  , locale);
                    }
                    long gpsAge = ed.getGpsAge();
                    if (gpsAge >= GPS_AGE_THRESHOLD_1) {
                        return (new ColumnValue(valStr)).setForegroundColor(GPS_AGE_COLOR_1).setFontStyleItalic();
                    } else
                    if (gpsAge >= GPS_AGE_THRESHOLD_2) {
                        return (new ColumnValue(valStr)).setForegroundColor(GPS_AGE_COLOR_2);
                    } else {
                        return valStr;
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.lon","Lon");
                }
            });
            
            // Latitude/Longitude
            this.addColumnTemplate(new DataColumnTemplate(DATA_GEOPOINT) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Locale locale = rd.getLocale();
                    double lat = ed.getLatitude();
                    double lon = ed.getLongitude();
                    if (GeoPoint.isValid(lat,lon)) {
                        arg = StringTools.trim(arg);
                        String valStr = "";
                        Account.LatLonFormat latlonFmt = Account.getLatLonFormat(rd.getAccount());
                        if (arg.equalsIgnoreCase("dms") || (StringTools.isBlank(arg) && latlonFmt.isDegMinSec())) {
                            String latStr = GeoPoint.formatLatitude( lat, "DMS", locale);
                            String lonStr = GeoPoint.formatLongitude(lon, "DMS", locale);
                            valStr = latStr + GeoPoint.PointSeparator + lonStr;
                        } else
                        if (arg.equalsIgnoreCase("dm") || (StringTools.isBlank(arg) && latlonFmt.isDegMin())) {
                            String latStr = GeoPoint.formatLatitude( lat, "DM" , locale);
                            String lonStr = GeoPoint.formatLongitude(lon, "DM" , locale);
                            valStr = latStr + GeoPoint.PointSeparator + lonStr;
                        } else {
                            String fmt    = StringTools.isBlank(arg)? "4" : arg;
                            String latStr = GeoPoint.formatLatitude( lat, fmt  , locale);
                            String lonStr = GeoPoint.formatLongitude(lon, fmt  , locale);
                            valStr = latStr + GeoPoint.PointSeparator + lonStr;
                        }
                        long gpsAge = ed.getGpsAge();
                        if (gpsAge >= GPS_AGE_THRESHOLD_1) {
                            return (new ColumnValue(valStr)).setForegroundColor(GPS_AGE_COLOR_1).setFontStyleItalic();
                        } else
                        if (gpsAge >= GPS_AGE_THRESHOLD_2) {
                            return (new ColumnValue(valStr)).setForegroundColor(GPS_AGE_COLOR_2);
                        } else {
                            return valStr;
                        }
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.latLon","Lat/Lon");
                }
            });

            // Altitude
            this.addColumnTemplate(new DataColumnTemplate(DATA_ALTITUDE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double alt = ed.getAltitude(); // meters
                    if (Account.getDistanceUnits(rd.getAccount()).isMiles()) {
                        alt *= GeoPoint.FEET_PER_METER; // convert to feet
                    }
                    return formatDouble(alt, arg, "0");
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.altitude","Altitude") + "\n${altitudeUnits}";
                }
            });

            // Speed limit (posted speed)
            this.addColumnTemplate(new DataColumnTemplate(DATA_SPEED_LIMIT) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double kph = ed.getSpeedLimitKPH(); // KPH
                    if (kph <= 0.0) {
                        return "n/a ";
                    } else {
                        Account a = rd.getAccount();
                        return formatDouble(Account.getSpeedUnits(a).convertFromKPH(kph), arg, "0");
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.speedLimit","Speed Limit") + "\n${speedUnits}";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_SPEED) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double kph = ed.getSpeedKPH(); // KPH
                    if (kph <= 0.0) {
                        return "0   ";
                    } else {
                        Account a = rd.getAccount();
                        return formatDouble(Account.getSpeedUnits(a).convertFromKPH(kph), arg, "0");
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.speed","Speed") + "\n${speedUnits}";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_SPEED_HEADING) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double kph = ed.getSpeedKPH(); // KPH
                    if (kph <= 0.0) {
                        return "0   ";
                    } else {
                        Account a = rd.getAccount();
                        String speedStr = formatDouble(Account.getSpeedUnits(a).convertFromKPH(kph), arg, "0");
                        String headStr  = GeoPoint.GetHeadingString(ed.getHeading(),rd.getLocale()).toUpperCase();
                        if (headStr.length() == 1) {
                            headStr += " ";
                        }
                        return speedStr + " " + headStr;
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.speed","Speed") + "\n${speedUnits}";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_SPEED_UNITS) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double kph = ed.getSpeedKPH(); // KPH
                    if (kph <= 0.0) {
                        return "0    ";
                    } else {
                        Account a = rd.getAccount();
                        String unitAbbr = Account.getSpeedUnits(a).toString(rd.getLocale());
                        String speedStr = formatDouble(Account.getSpeedUnits(a).convertFromKPH(kph), arg, "0");
                        String headStr  = GeoPoint.GetHeadingString(ed.getHeading(),rd.getLocale()).toUpperCase();
                        if (headStr.length() == 1) {
                            headStr += " ";
                        }
                        return speedStr + unitAbbr + " " + headStr;
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.speed","Speed");
                }
            });

            // Heading
            this.addColumnTemplate(new DataColumnTemplate(DATA_HEADING) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double speed = ed.getSpeedKPH(); // KPH
                    if (speed > 0.0) {
                        if (!StringTools.isBlank(arg)) {
                            return formatDouble(ed.getHeading(), arg, "0");
                        } else {
                            return GeoPoint.GetHeadingString(ed.getHeading(),rd.getLocale()).toUpperCase();
                        }
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    return GeoPoint.GetHeadingTitle(rd.getLocale());
                }
            });

            // #Satellites
            this.addColumnTemplate(new DataColumnTemplate(DATA_SATELLITES) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    int satCount = ed.getSatelliteCount();
                    if (satCount > 0) {
                        return String.valueOf(satCount);
                    } else {
                        return "n/a";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.satelliteCount","Sat\nCount");
                }
            });

            // Distance
            this.addColumnTemplate(new DataColumnTemplate(DATA_DISTANCE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double dist = ed.getDistanceKM(); // kilometers
                    return EventDataLayout.formatKM(dist, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.distance","Distance") + "\n${distanceUnits}";
                }
            });

            // Odometer
            this.addColumnTemplate(new DataColumnTemplate(DATA_ODOMETER) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Device   dev = ed.getDevice();
                    double odom = ed.getOdometerKM(); // kilometers
                    if (odom <= 0.0) { odom = ed.getDistanceKM(); }
                    //Print.logInfo("Event Odometer: %f [%f]", odom, dev.getOdometerOffsetKM());
                    odom += dev.getOdometerOffsetKM(); // ok
                    return EventDataLayout.formatKM(odom, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.odometer","Odometer") + "\n${distanceUnits}";
                }
            });

            // Input Mask
            this.addColumnTemplate(new DataColumnTemplate(DATA_INPUT_STATE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    int input = (int)ed.getInputMask(); // bit mask
                    String s = StringTools.toBinaryString(input);
                    int slen = s.length();
                    int blen = StringTools.parseInt(arg,8);
                    return s.substring(slen - blen, slen);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.inputBitMask","Inputs\n(BitMask)");
                }
            });

            // Geozone-ID
            this.addColumnTemplate(new DataColumnTemplate(DATA_GEOZONE_ID) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getGeozoneID();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.geozoneID","Geozone-ID");
                }
            });

            // Geozone Description
            this.addColumnTemplate(new DataColumnTemplate(DATA_GEOZONE_DESC) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getGeozoneDescription();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.geozoneDescription","Geozone\nDescription");
                }
            });

            // Address
            this.addColumnTemplate(new DataColumnTemplate(DATA_ADDRESS) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getAddress();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.address","Address");
                }
            });
            
            // City
            this.addColumnTemplate(new DataColumnTemplate(DATA_CITY) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getCity();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.city","City");
                }
            });
            
            // State/Province
            this.addColumnTemplate(new DataColumnTemplate(DATA_STATE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getStateProvince();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.stateProvince","State\nProvince");
                }
            });

            // Country
            this.addColumnTemplate(new DataColumnTemplate(DATA_COUNTRY) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getCountry();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.country","Country");
                }
            });

            // Subdivision
            this.addColumnTemplate(new DataColumnTemplate(DATA_SUBDIVISION) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getSubdivision();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.subdivision","Subdivision");
                }
            });

            // Temperature (report index starts at '1')
            this.addColumnTemplate(new DataColumnTemplate(DATA_THERMO_1) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double thermo = ed.getThermoAverage(0); // degrees 'C'
                    return EventDataLayout.formatTemperature(thermo, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.temperature","Temp") + "\n#1";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_THERMO_2) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double thermo = ed.getThermoAverage(1); // degrees 'C'
                    return EventDataLayout.formatTemperature(thermo, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.temperature","Temp") + "\n#2";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_THERMO_3) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double thermo = ed.getThermoAverage(2); // degrees 'C'
                    return EventDataLayout.formatTemperature(thermo, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.temperature","Temp") + "\n#3";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_THERMO_4) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double thermo = ed.getThermoAverage(3); // degrees 'C'
                    return EventDataLayout.formatTemperature(thermo, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.temperature","Temp") + "\n#4";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_THERMO_5) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double thermo = ed.getThermoAverage(4); // degrees 'C'
                    return EventDataLayout.formatTemperature(thermo, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.temperature","Temp") + "\n#5";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_THERMO_6) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double thermo = ed.getThermoAverage(5); // degrees 'C'
                    return EventDataLayout.formatTemperature(thermo, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.temperature","Temp") + "\n#6";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_THERMO_7) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double thermo = ed.getThermoAverage(6); // degrees 'C'
                    return EventDataLayout.formatTemperature(thermo, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.temperature","Temp") + "\n#7";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_THERMO_8) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double thermo = ed.getThermoAverage(7); // degrees 'C'
                    return EventDataLayout.formatTemperature(thermo, arg, rd);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.temperature","Temp") + "\n#8";
                }
            });

            // Battery level
            this.addColumnTemplate(new DataColumnTemplate(DATA_BATTERY_LEVEL) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double battery = ed.getBatteryLevel();
                    if (battery > 0.0) {
                        return formatDouble(battery, arg, "0");
                    } else {
                        return "n/a";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.BatteryLevel","Battery\nLevel");
                }
            });

            // Fuel level
            this.addColumnTemplate(new DataColumnTemplate(DATA_FUEL_LEVEL) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double fuelLevel = ed.getFieldValue(EventData.FLD_fuelLevel, 0.0);
                    long percent = Math.round(fuelLevel * 100.0);
                    return percent + "%";
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.fuelPercent","Fuel%");
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_FUEL_TOTAL) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double vol = ed.getFieldValue(EventData.FLD_fuelTotal, 0.0); // liters
                    if (vol > 0.0) {
                        vol = Account.getVolumeUnits(rd.getAccount()).convertFromLiters(vol);
                        return StringTools.format(vol, "#0.0");
                    } else {
                        return "---";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.fuelTotal","Total Fuel") + "\n${volumeUnits}";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_FUEL_IDLE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double vol = ed.getFieldValue(EventData.FLD_fuelIdle, 0.0); // liters
                    if (vol > 0.0) {
                        vol = Account.getVolumeUnits(rd.getAccount()).convertFromLiters(vol);
                        return StringTools.format(vol, "#0.0");
                    } else {
                        return "---";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.fuelIdle","Idle Fuel") + "\n${volumeUnits}";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_FUEL_ECONOMY) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double econ = ed.getFieldValue(EventData.FLD_fuelEconomy, 0.0); // kilometers per liter
                    if (econ > 0.0) {
                        econ = Account.getEconomyUnits(rd.getAccount()).convertFromKPL(econ);
                        return StringTools.format(econ, "#0.0");
                    } else {
                        return "---";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.fuelEcon","Fuel Econ") + "\n${economyUnits}";
                }
            });

            // OBC/J1708
            this.addColumnTemplate(new DataColumnTemplate(DATA_J1708_FAULT) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long fault = ed.getFieldValue(EventData.FLD_j1708Fault, 0L);
                    if (fault == 0L) {
                        return "---";
                    } else {
                        Locale locale = rd.getLocale();
                        ColumnValue cv = new ColumnValue(DTJ1708Fault.GetJ1708FaultString(fault,locale));
                        //RequestProperties reqState = rd.getRequestProperties();
                        //URIArg j1587URL = WebPageAdaptor.MakeURL(reqState.getBaseURI(),"j1587.show"); // Constants.PAGE_J1587_SHOW);
                        //j1587URL.addArg("mid" , DTJ1708Fault.DecodeJ1708FaultMid(fault));
                        //j1587URL.addArg("pid" , DTJ1708Fault.DecodeJ1708FaultPid(fault));
                        //j1587URL.addArg("sid" , DTJ1708Fault.DecodeJ1708FaultSid(fault));
                        //j1587URL.addArg("fmi" , DTJ1708Fault.DecodeJ1708FaultFmi(fault));
                        //cv.setLinkURL("javascript:openResizableWindow('"+j1587URL+"','J1587Desc',320,100);",null);
                        return cv;
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.j1708Fault","J1708 Fault") + "\nMID/PID/FMI";
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_ENGINERPM) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    long rpm = ed.getFieldValue(EventData.FLD_engineRpm, 0L);
                    return String.valueOf(rpm);
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.engineRpm","Engine\nRPM");
                }
            });

            // Accelerometer
            this.addColumnTemplate(new DataColumnTemplate(DATA_BRAKE_G_FORCE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    double bgf = ed.getFieldValue(EventData.FLD_brakeGForce, 0.0);
                    return StringTools.format(bgf,"#0.0");
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.brakeGForce","Braking\nG-force");
                }
            });

            // last connect/checkin date/time (Device record)
            this.addColumnTemplate(new DataColumnTemplate(DATA_CHECKIN_DATETIME) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Device dev = ed.getDevice();
                    long ts = dev.getLastTotalConnectTime();
                    if (ts <= 0L) {
                        try {
                            EventData lastEv = dev.getLastEvent(-1L, false);
                            if (lastEv != null) {
                                ts = lastEv.getTimestamp();
                            }
                        } catch (DBException dbe) {
                            // error retrieving event record
                        }
                    }
                    if (ts > MINIMUM_REASONABLE_TIMESTAMP) {
                        ReportLayout rl = rd.getReportLayout();
                        //Account a = rd.getAccount();
                        //TimeZone tz = (a != null)? TimeZone.getTimeZone(a.getTimeZone()) : null;
                        TimeZone tz = rd.getTimeZone();
                        DateTime dt = new DateTime(ts);
                        String dtFmt = dt.format(rl.getDateTimeFormat(rd.getPrivateLabel()), tz);
                        ColumnValue cv = new ColumnValue(dtFmt).setSortKey(ts);
                        long ageSec = DateTime.getCurrentTimeSec() - ts;
                        if (ageSec >= DateTime.HourSeconds(24)) {
                            cv.setForegroundColor("red");
                        }
                        return cv;
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.lastCheckinTime","Last Check-In\nTime");
                }
            });
            this.addColumnTemplate(new DataColumnTemplate(DATA_CHECKIN_AGE) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Device dev = ed.getDevice();
                    long ts = dev.getLastTotalConnectTime();
                    if (ts <= 0L) {
                        try {
                            EventData lastEv = dev.getLastEvent(-1L, false);
                            if (lastEv != null) {
                                ts = lastEv.getTimestamp();
                            }
                        } catch (DBException dbe) {
                            // error retrieving event record
                        }
                    }
                    if (ts > MINIMUM_REASONABLE_TIMESTAMP) {
                        long ageSec = DateTime.getCurrentTimeSec() - ts;
                        long days   = (ageSec / DateTime.DaySeconds(1));
                        long hours  = (ageSec % DateTime.DaySeconds(1)) / DateTime.HourSeconds(1);
                        long min    = (ageSec % DateTime.HourSeconds(1)) / DateTime.MinuteSeconds(1);
                        StringBuffer sb = new StringBuffer();
                        sb.append(days ).append("d ");
                        if (hours < 10) { sb.append("0"); }
                        sb.append(hours).append("h ");
                        if (min   < 10) { sb.append("0"); }
                        sb.append(min  ).append("m");
                        ColumnValue cv = new ColumnValue(sb.toString()).setSortKey(ageSec);
                        if (ageSec >= DateTime.HourSeconds(24)) {
                            cv.setForegroundColor("red");
                        }
                        return cv;
                    } else {
                        return "";
                    }
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.lastCheckinAge","Since Last\nCheck-In");
                }
            });

            // custom field value (Device record)
            this.addColumnTemplate(new DataColumnTemplate(DATA_CUSTOM_FIELD) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    Device dev = ed.getDevice();
                    String value = dev.getCustomAttribute(arg);
                    return !StringTools.isBlank(value)? value : "";
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    String arg = rc.getArg();
                    String desc = rd.getPrivateLabel().getStringProperty(BasicPrivateLabel.PROP_DeviceInfo_custom_ + arg, null);
                    if (!StringTools.isBlank(desc)) {
                        if (desc.length() > 12) {
                            int p = desc.lastIndexOf(" ");
                            if (p > 0) {
                                desc = desc.substring(0,p) + "\n" + desc.substring(p+1);
                            }
                        }
                        return desc;
                    } else {
                        I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                        return i18n.getString("EventDataLayout.customAttribute","Custom\nAttribute");
                    }
                }
            });

            // Raw data
            this.addColumnTemplate(new DataColumnTemplate(DATA_RAW_DATA) {
                public Object getColumnValue(int rowNdx, ReportData rd, ReportColumn rc, Object obj) {
                    String arg = rc.getArg();
                    EventData ed = (EventData)obj;
                    return ed.getRawData();
                }
                public String getTitle(ReportData rd, ReportColumn rc) {
                    I18N i18n = rd.getPrivateLabel().getI18N(EventDataLayout.class);
                    return i18n.getString("EventDataLayout.rawData","Raw Data");
                }
            });

        }
    }
   
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

}
