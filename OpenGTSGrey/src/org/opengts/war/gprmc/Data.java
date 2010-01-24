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
// This module provides generic support for a vide variety of HTTP-based device 
// communication protocols.   This includes devices that send the NMEA-0183 $GPRMC 
// record in the requres URL.
//
// Here are the configurable properties that may be set in 'webapp.conf' to customize
// for a specific device type:
//    gprmc.logName           - Name used in logging output [default "gprmc"]
//    gprmc.uniquePrefix      - Prefix used on uniqueID when lookup up Device [defaults to 'gprmc.logName']
//    gprmc.defaultAccountID  - Default account id [default "gprmc"]
//    gprmc.minimumSpeedKPH   - Minimum acceptable speed
//    gprmc.dateFormat        - Date format for 'date' parameter (NONE|EPOCH|YMD|DMY|MDY) [default "YMD"]
//    gprmc.response.ok       - Response on successful data [default ""]
//    gprmc.response.error    - Response on error data [default ""]
//    gprmc.parm.unique       - Unique-ID parameter key [default "id"]
//    gprmc.parm.account      - Account-ID parameter key [default "acct"]
//    gprmc.parm.device       - Device-ID parameter key [default "dev"]
//    gprmc.parm.auth         - Auth/Password parameter key (not used)
//    gprmc.parm.status       - StatusCode parameter key [default "code"]
//    gprmc.parm.gprmc        - $GPRMC parameter key [default "gprmc"]
//    gprmc.parm.date         - Date parameter key (ignored if 'gprmc' is used) [default "date"]
//    gprmc.parm.time         - Time parameter key (ignored if 'gprmc' is used) [default "time"]
//    gprmc.parm.latitude     - Latitude parameter key (ignored if 'gprmc' is used) [default "lat"]
//    gprmc.parm.longitude    - Longitude parameter key (ignored if 'gprmc' is used) [default "lon"]
//    gprmc.parm.speed"       - Speed(kph) parameter key (ignored if 'gprmc' is used) [default "speed"]
//    gprmc.parm.heading      - Heading(degrees) parameter key (ignored if 'gprmc' is used) [default "head"]
//    gprmc.parm.altitude     - Altitude(meters) parameter key [default "alt"]
//    gprmc.parm.address      - Reverse-Geocode parameter key [default "addr"]
//
// Note: Do not rely on the property defaults always remaining the same as they are
// currently in this module.  This module is still under development and is subject to
// change, which includes the default values.
//
// Default sample Data:
//   http://track.example.com/gprmc/Data?
//      acct=myaccount&
//      dev=mydevice&
//      gprmc=$GPRMC,065954,V,3244.2749,N,14209.9369,W,21.6,0.0,211202,11.8,E,S*07
//   'webapp.conf' properties:
//      gprmc.defaultAccountID=gprmc
//      gprmc.minimumSpeedKPH=4.0
//      gprmc.parm.account=acct
//      gprmc.parm.device=dev
//      gprmc.parm.gprmc=gprmc
//
// NetGPS configuration: [http://www.gpsvehiclenavigation.com/GPS/netgps.php]
//   http://track.example.com/gprmc/Data?
//      un=deviceid&
//      cds=$GPRMC,140159.435,V,3244.2749,N,14209.9369,W,,,200807,,*13&
//      pw=anypass
//   'webapp.conf' properties:
//      gprmc.logName=netgps
//      gprmc.defaultAccountID=netgps
//      gprmc.minimumSpeedKPH=4.0
//      gprmc.parm.account=acct
//      gprmc.parm.device=un
//      gprmc.parm.auth=pw
//      gprmc.parm.gprmc=cds
//      gprmc.response.ok=GPSOK
//      gprmc.response.error=GPSERROR:
//
// GC-101 configuration:
//   http://track.example.com/gprmc/Data?
//      imei=471923002250245&
//      rmc=$GPRMC,023000.000,A,3130.0577,N,14271.7421,W,0.53,208.37,210507,,*19,AUTO
//   'webapp.conf' properties:
//      gprmc.logName=gc101
//      gprmc.uniquePrefix=gc101
//      gprmc.minimumSpeedKPH=4.0
//      gprmc.dateFormat=NONE
//      gprmc.parm.unique=imei
//      gprmc.parm.gprmc=rmc
//
// Mologogo configuration:
//   http://track.example.com/gprmc/data?
//      id=dad&
//      lat=39.251811&
//      lon=-137.132341&
//      accuracy=35949&
//      direction=-1&
//      speed=0&
//      speedUncertainty=0&
//      altitude=519&
//      altitudeUncertainty=49390&
//      pointType=GPS
//   'webapp.conf' properties:
//      gprmc.defaultAccountID=mologogo
//      gprmc.minimumSpeedKPH=4.0
//      gprmc.dateFormat=NONE
//      gprmc.parm.account=acct
//      gprmc.parm.device=id
//      gprmc.parm.gprmc=
//      gprmc.parm.latitude=lat
//      gprmc.parm.longitude=lon
//      gprmc.parm.speed=speed
//      gprmc.parm.heading=direction
//      gprmc.parm.status=pointType
//
// Another example configuration:
//   http://track.example.com/gprmc/Data?
//      acct=myacct&
//      dev=mydev&
//      lon=32.1234&
//      lat=-142.1234&
//      date=20070819&
//      time=225446&
//      speed=45.4&
//      code=1
//   'webapp.conf' properties:
//      gprmc.defaultAccountID=undefined
//      gprmc.minimumSpeedKPH=4.0
//      gprmc.dateFormat=YMD
//      gprmc.parm.account=acct
//      gprmc.parm.device=dev
//      gprmc.parm.gprmc=
//      gprmc.parm.latitude=lat
//      gprmc.parm.longitude=lon
//      gprmc.parm.date=date
//      gprmc.parm.time=time
//      gprmc.parm.speed=speed
//      gprmc.parm.heading=heading
//      gprmc.parm.status=code
//
// ----------------------------------------------------------------------------
// Change History:
//  2007/08/09  Martin D. Flynn
//     -Initial release. 
//     -Note: this module is new for this release and has not yet been fully tested.
//  2007/09/16  Martin D. Flynn
//     -Additional optional parameters to allow for more flexibility in defining data
//      format types.  This module should now be able to be configured for a wide variety
//      of HTTP base communication protocols from various types of remote devices.
//     -Note: this module has still not yet been fully tested.
//  2007/11/28  Martin D. Flynn
//     -Added 'gprmc.uniquePrefix' property
//  2008/02/10  Martin D. Flynn
//     -Added additional logging messages when lat/lon is invalid
//  2008/05/14  Martin D. Flynn
//     -Integrated Device DataTransport interface
//  2008/08/15  Martin D. Flynn
//     -Make sure 'isValid' is set for non-GPRMC parsed records.
// ----------------------------------------------------------------------------
package org.opengts.war.gprmc;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.sql.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.dbtypes.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.war.tools.*;

public class Data 
    extends CommonServlet
{

    // ------------------------------------------------------------------------

    /* name used to tag logged messaged */
    public static String LOG_NAME                       = "gprmc";

    // ------------------------------------------------------------------------

    /* unique id prefix */
    public static String UniqueIDPrefix                 = LOG_NAME;

    /* default account id */
    // The URL variable "?un=<id>" is used to allow this module the ability to uniquely
    // identify a reporting phone.  The reported "<id>" provided by the phone is used
    // as the "DeviceID" for the following AccountID.  This value can be overridden in
    // the Servlet 'webapp.conf' file.
    public static String  DefaultAccountID              = "gprmc";

    /* minimum required speed */
    // GPS receivers have a tendency to appear 'moving', even when sitting stationary
    // on your desk.  This filter is a 'speed' threshold which is used to force the 
    // reported speed to '0' if it falls below this value.  This value can be overridden
    // in the Servlet 'webapp.conf' file.
    public static double  MinimumReqSpeedKPH            = 4.0;

    /* Default time zone */
    private static TimeZone gmtTimeZone                 = DateTime.getGMTTimeZone();

    // ------------------------------------------------------------------------

    /* date format constants */
    public static final int DATE_FORMAT_NONE            = 0; // Current time will be used
    public static final int DATE_FORMAT_EPOCH           = 1; // <epochTime>
    public static final int DATE_FORMAT_YMD             = 2; // "YYYYMMDD" or "YYMMDD"
    public static final int DATE_FORMAT_MDY             = 3; // "MMDDYYYY" or "MMDDYY"
    public static final int DATE_FORMAT_DMY             = 4; // "DDMMYYYY" or "DDMMYY"
    
    /* date format */
    // The date format must be specified here
    public static int     DateFormat                    = DATE_FORMAT_YMD;

    public static String GetDateFormatString()
    {
        switch (DateFormat) {
            case DATE_FORMAT_NONE :  return "NONE";
            case DATE_FORMAT_EPOCH:  return "EPOCH";
            case DATE_FORMAT_YMD  :  return "YMD";
            case DATE_FORMAT_MDY  :  return "MDY";
            case DATE_FORMAT_DMY  :  return "DMY";
            default               :  return "???";
        }
    }
    
    // ------------------------------------------------------------------------
    
    // common parameter keys (lookups are case insensitive) */
    private static String PARM_UNIQUE                   = "id";         // UniqueID
    private static String PARM_ACCOUNT                  = "acct";       // AccountID
    private static String PARM_DEVICE                   = "dev";        // DeviceID
    private static String PARM_AUTH                     = "pass";       // authorization/password
    private static String PARM_STATUS                   = "code";       // status code
    private static String PARM_ALTITUDE                 = "alt";        // altitude (meters)
    private static String PARM_ADDRESS                  = "addr";       // reverse-geocoded address

    // $GPRMC field key
    private static String PARM_GPRMC                    = "gprmc";      // $GPRMC data

    // these are ignored if PARM_GPRMC is defined
    private static String PARM_DATE                     = "date";       // date (YYYYMMDD)
    private static String PARM_TIME                     = "time";       // time (HHMMSS)
    private static String PARM_LATITUDE                 = "lat";        // latitude
    private static String PARM_LONGITUDE                = "lon";        // longitude
    private static String PARM_SPEED                    = "speed";      // speed (kph)
    private static String PARM_HEADING                  = "head";       // heading (degrees)

    /* returned response */
    private static String RESPONSE_OK                   = "";
    private static String RESPONSE_ERROR                = "";

    // ------------------------------------------------------------------------

    /* configuration name (TODO: update based on specific servlet configuration) */
    public static final String  CONFIG_NAME             = "gprmc";
    
    /* runtime config */
    public static final String  CONFIG_LOG_NAME         = CONFIG_NAME + ".logName";
    public static final String  CONFIG_UNIQUE_PREFIX    = CONFIG_NAME + ".uniquePrefix";
    public static final String  CONFIG_DFT_ACCOUNT      = CONFIG_NAME + ".defaultAccountID";
    public static final String  CONFIG_MIN_SPEED        = CONFIG_NAME + ".minimumSpeedKPH";
    public static final String  CONFIG_DATE_FORMAT      = CONFIG_NAME + ".dateFormat";       // "YMD", "DMY", "MDY"
    public static final String  CONFIG_RESPONSE_OK      = CONFIG_NAME + ".response.ok";
    public static final String  CONFIG_RESPONSE_ERROR   = CONFIG_NAME + ".response.error";

    public static final String  CONFIG_PARM_UNIQUE      = CONFIG_NAME + ".parm.unique";
    public static final String  CONFIG_PARM_ACCOUNT     = CONFIG_NAME + ".parm.account";
    public static final String  CONFIG_PARM_DEVICE      = CONFIG_NAME + ".parm.device";
    public static final String  CONFIG_PARM_AUTH        = CONFIG_NAME + ".parm.auth";
    public static final String  CONFIG_PARM_STATUS      = CONFIG_NAME + ".parm.status";
    
    public static final String  CONFIG_PARM_GPRMC       = CONFIG_NAME + ".parm.gprmc";       // $GPRMC
    
    public static final String  CONFIG_PARM_DATE        = CONFIG_NAME + ".parm.date";        // epoch, YYYYMMDD, DDMMYYYY, MMDDYYYY
    public static final String  CONFIG_PARM_TIME        = CONFIG_NAME + ".parm.time";        // HHMMSS
    public static final String  CONFIG_PARM_LATITUDE    = CONFIG_NAME + ".parm.latitude";
    public static final String  CONFIG_PARM_LONGITUDE   = CONFIG_NAME + ".parm.longitude";
    public static final String  CONFIG_PARM_SPEED       = CONFIG_NAME + ".parm.speed";       // kph
    public static final String  CONFIG_PARM_HEADING     = CONFIG_NAME + ".parm.heading";     // degrees
    public static final String  CONFIG_PARM_ALTITUDE    = CONFIG_NAME + ".parm.altitude";    // meters
    public static final String  CONFIG_PARM_ADDRESS     = CONFIG_NAME + ".parm.address";     // reverse-geocode

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* static initializer */
    // Only initialized once (per JVM)
    static {

        /* initialize DBFactories */
        // should already have been called by 'RTConfigContextListener'
        DBConfig.servletInit(null);

        /* set configuration */
        LOG_NAME           = RTConfig.getString(CONFIG_LOG_NAME     , LOG_NAME);
        UniqueIDPrefix     = RTConfig.getString(CONFIG_UNIQUE_PREFIX, LOG_NAME);
        DefaultAccountID   = RTConfig.getString(CONFIG_DFT_ACCOUNT  , DefaultAccountID);
        MinimumReqSpeedKPH = RTConfig.getDouble(CONFIG_MIN_SPEED    , MinimumReqSpeedKPH);
        String dateFmt     = RTConfig.getString(CONFIG_DATE_FORMAT  , "");
        if (dateFmt.equals("") || dateFmt.equalsIgnoreCase("NONE")) {
            DateFormat = DATE_FORMAT_NONE;
        } else
        if (dateFmt.equalsIgnoreCase("EPOCH")) {
            DateFormat = DATE_FORMAT_EPOCH;
        } else
        if (dateFmt.equalsIgnoreCase("YMD")) {
            DateFormat = DATE_FORMAT_YMD;
        } else
        if (dateFmt.equalsIgnoreCase("MDY")) {
            DateFormat = DATE_FORMAT_MDY;
        } else
        if (dateFmt.equalsIgnoreCase("DMY")) {
            DateFormat = DATE_FORMAT_DMY;
        } else {
            DateFormat = DATE_FORMAT_YMD;
            Data.logError(null, "Invalid date format: " + dateFmt);
        }

        /* parameters */
        PARM_UNIQUE        = RTConfig.getString(CONFIG_PARM_UNIQUE   , PARM_UNIQUE)     .trim();
        PARM_ACCOUNT       = RTConfig.getString(CONFIG_PARM_ACCOUNT  , PARM_ACCOUNT)    .trim();
        PARM_DEVICE        = RTConfig.getString(CONFIG_PARM_DEVICE   , PARM_DEVICE)     .trim();
        PARM_AUTH          = RTConfig.getString(CONFIG_PARM_AUTH     , PARM_AUTH)       .trim();
        PARM_STATUS        = RTConfig.getString(CONFIG_PARM_STATUS   , PARM_STATUS)     .trim();
        PARM_GPRMC         = RTConfig.getString(CONFIG_PARM_GPRMC    , PARM_GPRMC)      .trim();
        PARM_DATE          = RTConfig.getString(CONFIG_PARM_DATE     , PARM_DATE)       .trim();
        PARM_TIME          = RTConfig.getString(CONFIG_PARM_TIME     , PARM_TIME)       .trim();
        PARM_LATITUDE      = RTConfig.getString(CONFIG_PARM_LATITUDE , PARM_LATITUDE)   .trim();
        PARM_LONGITUDE     = RTConfig.getString(CONFIG_PARM_LONGITUDE, PARM_LONGITUDE)  .trim();
        PARM_SPEED         = RTConfig.getString(CONFIG_PARM_SPEED    , PARM_SPEED)      .trim();
        PARM_HEADING       = RTConfig.getString(CONFIG_PARM_HEADING  , PARM_HEADING)    .trim();
        PARM_ALTITUDE      = RTConfig.getString(CONFIG_PARM_ALTITUDE , PARM_ALTITUDE)   .trim();
        PARM_ADDRESS       = RTConfig.getString(CONFIG_PARM_ADDRESS  , PARM_ADDRESS)    .trim();
        
        /* return errors */
        RESPONSE_OK        = RTConfig.getString(CONFIG_RESPONSE_OK   , RESPONSE_OK);
        RESPONSE_ERROR     = RTConfig.getString(CONFIG_RESPONSE_ERROR, RESPONSE_ERROR);

        /* header */
        Data.logInfo("Default AccountID  : " + DefaultAccountID);
        Data.logInfo("Minimum speed      : " + MinimumReqSpeedKPH + " kph");
        Data.logInfo("Date Format        : " + GetDateFormatString());
        Data.logInfo("UniqueID parameter : &" + PARM_UNIQUE + "=");
        Data.logInfo("Account parameter  : &" + PARM_ACCOUNT + "=");
        Data.logInfo("Device parameter   : &" + PARM_DEVICE + "=");
        Data.logInfo("Status parameter   : &" + PARM_STATUS + "=");
        if (!StringTools.isBlank(PARM_GPRMC)) {
        Data.logInfo("$GPRMC parameter   : &" + PARM_GPRMC + "=");
        } else {
        Data.logInfo("Date parameter     : &" + PARM_DATE + "=");
        Data.logInfo("Time parameter     : &" + PARM_TIME + "=");
        Data.logInfo("Latitude parameter : &" + PARM_LATITUDE + "=");
        Data.logInfo("Longitude parameter: &" + PARM_LONGITUDE + "=");
        Data.logInfo("SpeedKPH parameter : &" + PARM_SPEED + "=");
        Data.logInfo("Heading parameter  : &" + PARM_HEADING + "=");
        }
        Data.logInfo("Altitude parameter : &" + PARM_ALTITUDE + "=");
        Data.logInfo("Address parameter  : &" + PARM_ADDRESS + "=");

    };

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* translate status code from device */
    private static int TranslateStatusCode(String statusCodeStr)
    {
        // TODO: Status code translations are device dependent, thus this section will 
        //       needs to be customized to the specific device using this server.
        //
        // For instance, the Mologogo would use the following code translation:
        //    "GPS"    => StatusCodes.STATUS_LOCATION
        //    "CELL"   => StatusCodes.STATUS_LOCATION
        //    "MANUAL" => StatusCodes.STATUS_WAYMARK_0
        //
        // For now, just return the generic StatusCodes.STATUS_LOCATION
        return StatusCodes.STATUS_LOCATION;
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        this.doGet(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        String ipAddr     = request.getRemoteAddr();
        String uniqueID   = AttributeTools.getRequestString(request, PARM_UNIQUE    , null);
        String accountID  = AttributeTools.getRequestString(request, PARM_ACCOUNT   , null);
        String deviceID   = AttributeTools.getRequestString(request, PARM_DEVICE    , "");
        String authCode   = AttributeTools.getRequestString(request, PARM_AUTH      , "");  // not currently used
        String statusStr  = AttributeTools.getRequestString(request, PARM_STATUS    , "");
        double altitudeM  = AttributeTools.getRequestDouble(request, PARM_ALTITUDE  , 0.0);  // meters
        String address    = AttributeTools.getRequestString(request, PARM_ADDRESS   , "");
        Device device     = null;

        /* status code */
        int statusCode = Data.TranslateStatusCode(statusStr);

        /* URL */
        String URL = "[" + ipAddr + "] URL: " + request.getRequestURL() + " " + request.getQueryString();
        
        /* unique id? */
        if (!StringTools.isBlank(uniqueID)) {
            
            /* get Device by UniqueID */
            String uid = UniqueIDPrefix + "_" + uniqueID; // ie: "gprmc_123456789012345"
            try {
                device = Transport.loadDeviceByUniqueID(uid);
                if (device == null) {
                    Data.logWarn("Unique ID not found!: " + uid); // <== display main key
                    return;
                }
            } catch (DBException dbe) {
                Data.logError(URL, "Exception getting Device: " + uniqueID + " [" + dbe + "]");
                return;
            }
            
        } else {

            /* account id? */
            if (StringTools.isBlank(accountID)) {
                accountID = DefaultAccountID;
                if ((accountID == null) || accountID.equals("")) {
                    Data.logError(URL, "Unable to identify Account");
                    Data.logError(null, "(has '" + PARM_ACCOUNT + "' been properly configured in 'webapp.conf'?)");
                    this.plainTextResponse(response, RESPONSE_ERROR);
                    return;
                }
            }

            /* device id? */
            if (StringTools.isBlank(deviceID)) {
                Data.logError(URL, "Unable to identify Device");
                Data.logError(null, "(has '" + PARM_DEVICE + "' been properly configured in 'webapp.conf'?)");
                this.plainTextResponse(response, RESPONSE_ERROR);
                return;
            }

            /* read the device */
            try {
                device = Transport.loadDeviceByTransportID(Account.getAccount(accountID), deviceID);
                if (device == null) {
                    // Device was not found
                    Data.logError(URL, "Device not found AccountID/DeviceID: " + accountID + "/" + deviceID);
                    this.plainTextResponse(response, RESPONSE_ERROR);
                    return;
                }
            } catch (DBException dbe) {
                // Error while reading Device
                Data.logException(URL, "Error reading Device", dbe);
                this.plainTextResponse(response, RESPONSE_ERROR);
                return;
            }
            
        }

        /* update actual device/account ids */
        deviceID  = device.getDeviceID();
        accountID = device.getAccountID();

        /* validate source IP address */
        // This may be used to prevent rogue hackers from spoofing data coming from the phone
        if (!device.getDataTransport().isValidIPAddress(ipAddr)) {
            // 'ipAddr' does not match allowable device IP addresses
            Data.logError(URL, "Invalid IP Address for device");
            this.plainTextResponse(response, RESPONSE_ERROR);
            return;
        }
        
        /* display URL (debug) */
        Data.logInfo(URL);

        /* GPRMC? */
        boolean isValid     = false;
        long    fixtime     = 0L;
        double  latitude    = 0.0;
        double  longitude   = 0.0;
        double  speedKPH    = 0.0;    // kph
        double  headingDeg  = 0.0;    // degrees
        if (!StringTools.isBlank(PARM_GPRMC)) {
            String gprmcStr = AttributeTools.getRequestString(request, PARM_GPRMC, "");
            if ((gprmcStr == null) || !gprmcStr.startsWith("$GPRMC")) {
                Data.logError(URL, "Missing $GPRMC: " + gprmcStr);
                Data.logError(null, "(has '" + PARM_GPRMC + "' been properly configured in 'webapp.conf'?)");
                this.plainTextResponse(response, RESPONSE_ERROR);
                return;
            }
            Nmea0183 gprmc = new Nmea0183(gprmcStr);
            fixtime    = gprmc.getFixtime();
            isValid    = gprmc.isValidGPS();
            latitude   = isValid? gprmc.getLatitude()  : 0.0;
            longitude  = isValid? gprmc.getLongitude() : 0.0;
            speedKPH   = isValid? gprmc.getSpeedKPH()  : 0.0;
            headingDeg = isValid? gprmc.getHeading()   : 0.0;
            if (!isValid) {
                Data.logInfo("Invalid latitude/longitude");
            }
        } else {
            fixtime    = this._parseFixtime(request);
            latitude   = AttributeTools.getRequestDouble(request, PARM_LATITUDE , -999.0);
            longitude  = AttributeTools.getRequestDouble(request, PARM_LONGITUDE, -999.0);
            if ((latitude == -999.0) && (longitude == -999.0)) {
                Data.logError(URL, "Missing latitude/longitude");
                Data.logError(null, "(has '" + PARM_LATITUDE  + "' been properly configured in 'webapp.conf'?)");
                Data.logError(null, "(has '" + PARM_LONGITUDE + "' been properly configured in 'webapp.conf'?)");
                isValid   = false;
                latitude  = 0.0;
                longitude = 0.0;
            } else
            if ((latitude  >=  90.0) || (latitude  <=  -90.0) ||
                (longitude >= 180.0) || (longitude <= -180.0) ||
                ((latitude == 0.0) && (longitude == 0.0)    )   ) {
                Data.logInfo("Invalid latitude/longitude: " + latitude + "/" + longitude);
                isValid   = false;
                latitude  = 0.0;
                longitude = 0.0;
            } else {
                isValid   = true;
            }
            speedKPH   = isValid? AttributeTools.getRequestDouble(request, PARM_SPEED   , 0.0) : 0.0;  // kph
            headingDeg = isValid? AttributeTools.getRequestDouble(request, PARM_HEADING , 0.0) : 0.0;  // degrees
        }

        /* reject invalid GPS fixes? */
        if (!isValid && (statusCode == StatusCodes.STATUS_LOCATION)) {
            // ignore invalid GPS fixes that have a simple 'STATUS_LOCATION' status code
            Data.logWarn("Ignoring event with invalid latitude/longitude");
            this.plainTextResponse(response, "");
            return;
        }
        
        /* adjustments to received values */
        if (speedKPH < MinimumReqSpeedKPH) {
            // Say we're not moving if the value is <= our desired threshold
            speedKPH = 0.0;
        }
        if ((speedKPH <= 0.0) || (headingDeg < 0.0)) {
            // We're either not moving, or the GPS receiver doesn't know the heading
            headingDeg = 0.0; // to be consistent, set the heading to North
        }

        /* create new event record */
        EventData.Key evKey = new EventData.Key(accountID, deviceID, fixtime, statusCode);
        EventData evdb = evKey.getDBRecord();
        evdb.setLatitude(latitude);
        evdb.setLongitude(longitude);
        evdb.setSpeedKPH(speedKPH);
        evdb.setHeading(headingDeg);
        if (altitudeM != 0.0) {
            evdb.setAltitude(altitudeM);
        }
        if (!address.equals("")) {
            evdb.setAddress(address);
        }

        /* insert event */
        // this will display an error if it was unable to store the event
        if (device.insertEventData(evdb)) {
            Data.logInfo("Event inserted: "+device.getAccountID()+"/"+device.getDeviceID() +
                " - "+evdb.getGeoPoint());
        }

        /* write success response */
        this.plainTextResponse(response, RESPONSE_OK);

    }

    private long _parseFixtime(HttpServletRequest request)
    {
        // Examples:
        // 0) if (DateFormat == DATE_FORMAT_NONE):
        //      return current time
        // 1) if (DateFormat == DATE_FORMAT_EPOCH):
        //      &date=1187809084
        // 2) if (DateFormat == DATE_FORMAT_YMD):
        //      &date=2007/08/21&time=17:59:23
        //      &date=20070821&time=175923
        //      &date=070821&time=175923
        // 3) if (DateFormat == DATE_FORMAT_MDY):
        //      &date=08/21/2007&time=17:59:23
        //      &date=08212007&time=175923
        //      &date=082107&time=175923
        // 4) if (DateFormat == DATE_FORMAT_DMY):
        //      &date=21/08/2007&time=17:59:23
        //      &date=21082007&time=175923
        //      &date=210807&time=175923
        
        /* no date/time specification? */
        if (DateFormat == DATE_FORMAT_NONE) {
            return DateTime.getCurrentTimeSec();
        }

        /* extract date/time fields */
        String dateStr = AttributeTools.getRequestString(request,PARM_DATE,"");
        String timeStr = AttributeTools.getRequestString(request,PARM_TIME,"");

        /* unix 'Epoch' time? */
        if (DateFormat == DATE_FORMAT_EPOCH) {
            String epochStr = !dateStr.equals("")? dateStr : timeStr;
            long timestamp = StringTools.parseLong(epochStr, 0L);
            return (timestamp > 0L)? timestamp : DateTime.getCurrentTimeSec();
        }

        /* time */
        if (timeStr.indexOf(":") >= 0) {
            // Convert "HH:MM:SS" to "HHMMSS"
            timeStr = StringTools.stripChars(timeStr,':');
        }
        if (timeStr.length() != 6) {
            // invalid time length
            return DateTime.getCurrentTimeSec();
        }

        /* date */
        if (dateStr.indexOf("/") >= 0) {
            // Convert "YYYY/MM/DD" to "YYYYMMDD"
            dateStr = StringTools.stripChars(dateStr,'/');
        }
        int dateLen = dateStr.length();
        if ((dateLen != 8) && (dateLen != 6)) {
            // invalid date length
            return DateTime.getCurrentTimeSec();
        }

        /* parse date */
        int YYYY = 0;
        int MM   = 0;
        int DD   = 0;
        if (DateFormat == DATE_FORMAT_YMD) {
            if (dateLen == 8) {
                YYYY = StringTools.parseInt(dateStr.substring(0,4), 0);
                MM   = StringTools.parseInt(dateStr.substring(4,6), 0);
                DD   = StringTools.parseInt(dateStr.substring(6,8), 0);
            } else { // datalen == 6
                YYYY = StringTools.parseInt(dateStr.substring(0,2), 0) + 2000;
                MM   = StringTools.parseInt(dateStr.substring(2,4), 0);
                DD   = StringTools.parseInt(dateStr.substring(4,5), 0);
            }
        } else
        if (DateFormat == DATE_FORMAT_MDY) {
            if (dateLen == 8) {
                MM   = StringTools.parseInt(dateStr.substring(0,2), 0);
                DD   = StringTools.parseInt(dateStr.substring(2,4), 0);
                YYYY = StringTools.parseInt(dateStr.substring(4,8), 0);
            } else { // datalen == 6
                MM   = StringTools.parseInt(dateStr.substring(0,2), 0);
                DD   = StringTools.parseInt(dateStr.substring(2,4), 0);
                YYYY = StringTools.parseInt(dateStr.substring(4,6), 0) + 2000;
            }
        } else
        if (DateFormat == DATE_FORMAT_DMY) {
            if (dateLen == 8) {
                DD   = StringTools.parseInt(dateStr.substring(0,2), 0);
                MM   = StringTools.parseInt(dateStr.substring(2,4), 0);
                YYYY = StringTools.parseInt(dateStr.substring(4,8), 0);
            } else { // datalen == 6
                DD   = StringTools.parseInt(dateStr.substring(0,2), 0);
                MM   = StringTools.parseInt(dateStr.substring(2,4), 0);
                YYYY = StringTools.parseInt(dateStr.substring(4,6), 0) + 2000;
            }
        } else {
            // invalid date format specification
            return DateTime.getCurrentTimeSec();
        }

        /* parse time */
        int hh = StringTools.parseInt(timeStr.substring(0,2), 0);
        int mm = StringTools.parseInt(timeStr.substring(2,4), 0);
        int ss = StringTools.parseInt(timeStr.substring(4,6), 0);
        
        /* return epoch time */
        DateTime dt = new DateTime(gmtTimeZone, YYYY, MM, DD, hh, mm, ss);
        return dt.getTimeSec();

    }
    
    // ------------------------------------------------------------------------

    /* send plain text response */
    private void plainTextResponse(HttpServletResponse response, String errMsg)
        throws ServletException, IOException
    {
        CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN);
        PrintWriter out = response.getWriter();
        out.println(errMsg);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // message logging
    
    private static void logInfo(String msg)
    {
        Print.logInfo(LOG_NAME + ": " + msg);
    }

    private static void logWarn(String msg)
    {
        Print.logWarn(LOG_NAME + ": " + msg);
    }

    private static void logError(String URL, String msg)
    {
        if (URL != null) {
            Print.logError(LOG_NAME + ": " + URL);
        }
        Print.logError(LOG_NAME + ": " + msg);
    }

    private static void logException(String URL, String msg, Throwable th)
    {
        if (URL != null) {
            Print.logError(LOG_NAME + ": " + URL);
        }
        Print.logException(LOG_NAME + ": " + msg, th);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

}
