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
// Other reporting engine options:
//  http://java-source.net/open-source/charting-and-reporting
// ----------------------------------------------------------------------------
// Change History:
//  2007/03/11  Martin D. Flynn
//     -Initial release
//  2007/03/25  Martin D. Flynn
//     -Updated to use 'DeviceList'
//  2007/06/13  Martin D. Flynn
//     -Renamed 'DeviceList' to 'ReportDeviceList'
//  2007/11/28  Martin D. Flynn
//     -Fixed replacement of literal '\n' in the header text (previously '\n' may 
//      not have been replaced if there were no 'key' replacements also in the text).
//  2008/02/17  Martin D. Flynn
//     -Default date/time format to 'private.xml', if not specified here
//  2009/07/01  Martin D. Flynn
//     -Moved default style to 'ReportDisplay.css'
//  2009/11/01  Martin D. Flynn
//     -Modified 'expandHeaderText' to support specified ${key=default} values
//     -Added ReportOption support
// ----------------------------------------------------------------------------
package org.opengts.war.report;

import java.util.*;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;

import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.war.tools.*;

public abstract class ReportLayout
{

    // ------------------------------------------------------------------------
    
    public static final  String CSS_CLASS_TABLE         = "rptTbl";
    public static final  String CSS_CLASS_TABLE_SORT    = "rptTbl_sortable"; // ReportPresentation.SORTTABLE_CSS_CLASS;

    public static final  String CSS_CLASS_ODD           = "rptBodyRowOdd";
    public static final  String CSS_CLASS_EVEN          = "rptBodyRowEven";
    public static final  String CSS_CLASS_BODY_TOTAL    = "rptBodyRowTotal";

    public static final  String CSS_CLASS_TOTAL         = "rptTotalRow";

    // ------------------------------------------------------------------------

    public  static final long   RPT_MULTI_DEVICES       = 0x00000001L;
    public  static final long   RPT_START_DATE          = 0x00000002L;
    public  static final long   RPT_END_DATE            = 0x00000004L;

    // ------------------------------------------------------------------------
    // Header text replacement keys
    // These keys can be placed in the report title and header text:
    //  ${speedUnits} will be replaced with the Account speed unit abbrev
    //  ${distanceUnits} will be replaced with the Account distance unit abbrev
    //  ${timezone} will be replaced with the Account timezone

    private static final char   KEY_START_ESC           = '\\';
    private static final String KEY_START               = "${";
    private static final String KEY_END                 = "}";

    public  static final String HEADER_SPEED_UNITS      = "speedUnits";     // mph, kph
    public  static final String HEADER_DISTANCE_UNITS   = "distanceUnits";  // km, miles
    public  static final String HEADER_ALTITUDE_UNITS   = "altitudeUnits";  // meters, feet
    public  static final String HEADER_ECONOMY_UNITS    = "economyUnits";   // mpg, kpl
    public  static final String HEADER_VOLUME_UNITS     = "volumeUnits";    // liters, gallons
    public  static final String HEADER_TIMEZONE         = "timezone";       // GMT, US/Pacific
    public  static final String HEADER_ACCOUNTID        = "accountId";
    public  static final String HEADER_ACCOUNTDESC      = "accountDesc";
    public  static final String HEADER_DEVICEID         = "deviceId";
    public  static final String HEADER_DEVICEDESC       = "deviceDesc";
    public  static final String HEADER_GROUPID          = "groupId";
    public  static final String HEADER_GROUPDESC        = "groupDesc";
    public  static final String HEADER_DATERANGE        = "dateRange";
    public  static final String HEADER_LIMIT            = "limit";

    private static HashMap<String,CustomHeaderValue> customLookupTable = null;

    // ------------------------------------------------------------------------
    // CSS files
        
    private static Collection<String> defaultCSSFiles = null;

    public static void setDefaultCSSFiles(Collection<String> cssFiles)
    {
        defaultCSSFiles = !ListTools.isEmpty(cssFiles)? cssFiles : null;
    }
    
    public static boolean hasDefaultCSSFiles()
    {
        return !ListTools.isEmpty(defaultCSSFiles);
    }
    
    public static Collection<String> getDefaultCSSFiles()
    {
        return defaultCSSFiles;
    }

    // ------------------------------------------------------------------------
    // Style sheet
        
    private static String defaultStyleSheet = "";

    /* set default style sheet */
    public static void setDefaultStyleSheet(String styleSheet)
    {
        if (StringTools.isBlank(styleSheet)) {
            defaultStyleSheet = "";
        } else {
            defaultStyleSheet = styleSheet;
            if (!defaultStyleSheet.endsWith("\n")) {
                defaultStyleSheet += "\n";
            }
        }
    }

    /* get default style sheet */
    public static boolean hasDefaultStyleSheet()
    {
        return !StringTools.isBlank(defaultStyleSheet);
    }

    /* get default style sheet */
    public static String getDefaultStyleSheet()
    {
        return defaultStyleSheet;
    }

    // ------------------------------------------------------------------------
    // Override ReportPresentation class
    
    private static final String DEFAULT_REPORT_PRESENTATION = DBConfig.PACKAGE_WAR_ + "report.presentation.ReportTable";
    
    private static Class<ReportPresentation> reportPresClass = null;

    @SuppressWarnings("unchecked")
    public static boolean setReportPresentationClassName(String className)
    {
        if (!StringTools.isBlank(className)) {
            try {
                Class presClass = Class.forName(className);
                if (!ReportPresentation.class.isAssignableFrom(presClass)) {
                    new RuntimeException("Must be a subclass of ReportPresentation");
                }
                Print.logInfo("Installing custom ReportPresentation class: " + StringTools.className(presClass));
                reportPresClass = (Class<ReportPresentation>)presClass; // "uncheck cast"
                return true;
            } catch (Throwable th) {
                Print.logException("Unable to install custom ReportPresentation: " + className, th);
                reportPresClass = null;
                return false;
            }
        } else {
            reportPresClass = null;
            return true;
        }
    }

    protected static ReportPresentation createReportPresentation()
    {

        /* make sure ReportPresentation class is initialized */
        if ((reportPresClass == null) && !setReportPresentationClassName(DEFAULT_REPORT_PRESENTATION)) {
            Print.logStackTrace("Unable to create default ReportPresentation: " + DEFAULT_REPORT_PRESENTATION);
            return null;
        }

        /* create/return new ReportPresentation */
        try {
            return reportPresClass.newInstance();
        } catch (Throwable th) {
            Print.logException("Unable to create custom ReportPresentation: " + StringTools.className(reportPresClass), th);
            return null;
        }

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* style sheet */
    private Collection<String>  cssFiles        = null;
    private String              styleSheet      = null;

    /* date/time format */
    private String              dateFormat      = null;
    private String              timeFormat      = null;
    
    /* report view */
    private ReportPresentation  reportTable     = null;
    
    /* report data row template */
    private DataRowTemplate     reportDataRow   = null;

    // ------------------------------------------------------------------------

    /* standard constructor */
    protected ReportLayout()
    {
        super();
        ReportLayout.InitCustomHeaderValueLookup();
    }

    // ------------------------------------------------------------------------
    // HTML report presentation

    protected ReportPresentation getReportPresentation()
    {
        if (this.reportTable == null) {
            this.reportTable = ReportLayout.createReportPresentation();
        }
        return this.reportTable;
    }

    // ------------------------------------------------------------------------
    // Date/Time format
    
    public void setDateTimeFormat(String dateFmt, String timeFmt)
    {
        this.dateFormat = ((dateFmt != null) && !dateFmt.equals(""))? dateFmt.trim() : null;
        this.timeFormat = ((timeFmt != null) && !timeFmt.equals(""))? timeFmt.trim() : null;
    }
    
    public String getDateFormat(PrivateLabel privLabel)
    {
        if (this.dateFormat == null) {
            if (privLabel != null) {
                return privLabel.getDateFormat();
            } else {
                return BasicPrivateLabel.getDefaultDateFormat();
            }
        }
        return this.dateFormat;
    }
    
    public String getTimeFormat(PrivateLabel privLabel)
    {
        if (this.timeFormat == null) {
            if (privLabel != null) {
                return privLabel.getTimeFormat();
            } else {
                return BasicPrivateLabel.getDefaultTimeFormat();
            }
        }
        return this.timeFormat;
    }
    
    public String getDateTimeFormat(PrivateLabel privLabel)
    {
        return this.getDateFormat(privLabel) + " " + this.getTimeFormat(privLabel);
    }

    // ------------------------------------------------------------------------
    // HTML style sheet
    
    public void setCSSFiles(Collection<String> cssFiles)
    {
        this.cssFiles = !ListTools.isEmpty(cssFiles)? cssFiles : null;
    }
    
    public boolean hasCSSFiles()
    {
        return !ListTools.isEmpty(this.cssFiles);
    }

    public Collection<String> getCSSFiles()
    {
        return this.cssFiles;
    }

    public Collection<String> getCSSFiles(boolean inclDefault)
    {
        if (!inclDefault) {
            return this.getCSSFiles();
        } else {
            if (!ReportLayout.hasDefaultCSSFiles()) {
                return this.getCSSFiles();
            } else
            if (!this.hasCSSFiles()) {
                return ReportLayout.getDefaultCSSFiles();
            } else {
                Collection<String> list = new Vector<String>();
                list.addAll(ReportLayout.getDefaultCSSFiles());
                list.addAll(this.getCSSFiles());
                return list;
            }
        }
    }

    // ------------------------------------------------------------------------

    public void setStyleSheet(String style)
    {
        this.styleSheet = style;
    }
    
    public boolean hasStyleSheet()
    {
        return !StringTools.isBlank(this.styleSheet);
    }

    public String getStyleSheet()
    {
        return (this.styleSheet != null)? this.styleSheet : "";
    }

    public String getStyleSheet(boolean inclDefault)
    {
        if (!inclDefault) {
            return this.getStyleSheet();
        } else {
            if (!ReportLayout.hasDefaultStyleSheet()) {
                return this.getStyleSheet();
            } else
            if (!this.hasStyleSheet()) {
                return ReportLayout.getDefaultStyleSheet();
            } else {
                StringBuffer style = new StringBuffer();
                style.append(ReportLayout.getDefaultStyleSheet());
                style.append(this.getStyleSheet());
                return style.toString();
            }
        }
    }

    public void writeReportStyle(String format, ReportData report, PrintWriter out, int indentLevel)
        throws ReportException
    {
        if (StringTools.isBlank(format) || format.equalsIgnoreCase(ReportPresentation.FORMAT_HTML)) {
            out.write("\n");

            /* default style */
            String dftStyle = ReportLayout.getDefaultStyleSheet();
            if (!StringTools.isBlank(dftStyle)) {
                out.write(dftStyle);
                out.write("\n");
            }

            /* custom report style */
            String cstStyle = this.getStyleSheet();
            if (!StringTools.isBlank(cstStyle)) {
                out.write(cstStyle);
                out.write("\n");
            }

        }
    }

    // ------------------------------------------------------------------------
    // write report
 
    /**
    *** @return The number of CSV records written
    **/
    public int writeReport(String format, ReportData rd, PrintWriter out, int indentLevel) 
        throws ReportException
    {
        ReportPresentation rp = this.getReportPresentation();
        if (rp != null) {
            return rp.writeReport(format, rd, out, indentLevel);
        } else {
            return 0;
        }
    }

    // ------------------------------------------------------------------------
    // DataRowTemplate

    public void setDataRowTemplate(DataRowTemplate rdp)
    {
        this.reportDataRow = rdp;
    }
    
    public DataRowTemplate getDataRowTemplate()
    {
        return this.reportDataRow;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* replace ${key} fields with the representative text */
    public static String expandHeaderText(String text, ReportData rd)
    {
        String keyStart = KEY_START;
        String keyEnd   = KEY_END;
        String argDelim = ":";
        String dftDelim = "=";
        
        /* first key location */
        int ks = text.indexOf(keyStart);
        
        /* ReportOption? */
        ReportOption ro = (rd != null)? rd.getReportOption() : null;

        /* start replacing keys */
        StringBuffer repText = new StringBuffer(text);
        for (;(ks >= 0);) {
            
            /* find end of key */
            int ke = repText.indexOf(keyEnd,ks);
            if (ke < 0) {
                // invalid key specification, stop here
                break;
            }

            /* adjusted indexes based on key delimiter lengths */
            int ksi = ((ks > 0) && (repText.charAt(ks-1) == KEY_START_ESC))? ks - 1 : ks;
            int ksx = ks + keyStart.length();
            int kex = ke + keyEnd.length();
            
            /* extract "key:arg=default" */
            String keyArgDft = repText.substring(ksx, ke);

            /* extract default */
            String dftStr;
            String keyArg;
            int d = keyArgDft.indexOf(dftDelim);
            if (d >= 0) {
                dftStr = keyArgDft.substring(d + dftDelim.length()); // leave default as-is (untrimmed)
                keyArg = keyArgDft.substring(0, d).trim();  // trim key
                //Print.logInfo("Found Default: " + keyArg + " ==> " + dftStr);
            } else {
                dftStr = "";
                keyArg = keyArgDft;
            }

            /* extract key/arg */
            String key;
            String arg;
            int a = keyArg.indexOf(argDelim);
            if (a >= 0) {
                arg = keyArg.substring(a + argDelim.length());
                key = keyArg.substring(0, a).trim();
            } else {
                arg = null;
                key = keyArg;
            }

            /* get value for key */
            String kv = null;
            if (ro != null) {
                if (key.equalsIgnoreCase("description")) {
                    kv = ro.getDescription(rd.getLocale());
                } else {
                    kv = ro.getValue(key);
                }
            }
            if (kv == null) {
                kv = lookupCustomHeaderText(key, arg, rd);
            }

            /* replace key with value */
            String fv = (kv != null)? kv : dftStr;
            repText.replace(ksi, kex, fv);
            
            /* find start of next key */
            ks = repText.indexOf(keyStart, ks);
            
        }
        
        /* replace literal "\n" with newline '\n' */
        int nl = repText.indexOf("\\n");
        for (;(nl >= 0);) {
            repText.replace(nl, nl+2, "\n");
            nl = repText.indexOf("\\n", nl);
        }
        
        /* return new text */
        return repText.toString().trim();
        
    }
    
    private static String lookupCustomHeaderText(String key, String arg, ReportData rd)
    {
        
        /* null key */
        if (key == null) {
            return "";
        }
        
        /* custom header key */
        CustomHeaderValue cv = customLookupTable.get(key);
        if (cv != null) {
            return cv.getValue(arg, rd);
        }
        
        /* request property keys */
        RequestProperties reqState = rd.getRequestProperties();
        String v = reqState.getKeyValue(key,arg);
        return (v != null)? v : key.toUpperCase();

    }
    
    private static void InitCustomHeaderValueLookup()
    {
        
        /* already initialized? */
        if (customLookupTable != null) {
            return;
        }
        
        /* init */
        customLookupTable = new HashMap<String,CustomHeaderValue>();
        customLookupTable.put(HEADER_SPEED_UNITS, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                Account a = (rd != null)? rd.getAccount() : null;
                return Account.getSpeedUnits(a).toString(rd.getLocale());
            }
        });
        customLookupTable.put(HEADER_DISTANCE_UNITS, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                Account a = (rd != null)? rd.getAccount() : null;
                return Account.getDistanceUnits(a).toString(rd.getLocale());
            }
        });
        customLookupTable.put(HEADER_ALTITUDE_UNITS, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                I18N i18n = rd.getPrivateLabel().getI18N(ReportLayout.class);
                if (Account.getDistanceUnits(rd.getAccount()).isMiles()) {
                    return i18n.getString("ReportLayout.feet","feet");
                } else {
                    return i18n.getString("ReportLayout.meters","meters");
                }
            }
        });
        customLookupTable.put(HEADER_ECONOMY_UNITS, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                Account a = (rd != null)? rd.getAccount() : null;
                return Account.getEconomyUnits(a).toString(rd.getLocale());
            }
        });
        customLookupTable.put(HEADER_VOLUME_UNITS, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                Account a = (rd != null)? rd.getAccount() : null;
                return Account.getVolumeUnits(a).toString(rd.getLocale());
            }
        });
        customLookupTable.put(HEADER_ACCOUNTID, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                return (rd != null)? rd.getAccountID() : "";
            }
        });
        customLookupTable.put(HEADER_ACCOUNTDESC, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                Account a = (rd != null)? rd.getAccount() : null;
                return (a != null)? a.getDescription() : "";
            }
        });
        customLookupTable.put(HEADER_DEVICEID, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                ReportDeviceList devList = rd.getReportDeviceList();
                int devSize = devList.size();
                if (devSize <= 0) {
                    return "";
                } else
                if (devSize == 1) {
                    return devList.getFirstDeviceID();
                } else {
                    I18N i18n = rd.getPrivateLabel().getI18N(ReportLayout.class);
                    return i18n.getString("ReportLayout.multipleDevices","(Multiple Devices)");
                }
            }
        });
        customLookupTable.put(HEADER_DEVICEDESC, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                ReportDeviceList devList = rd.getReportDeviceList();
                int devSize = devList.size();
                if (devSize <= 0) {
                    return "";
                } else
                if (devSize == 1) {
                    Device d = devList.getFirstDevice();
                    return (d != null)? d.getDescription() : "";
                } else {
                    I18N i18n = rd.getPrivateLabel().getI18N(ReportLayout.class);
                    return i18n.getString("ReportLayout.multipleDevices","(Multiple Devices)");
                }
            }
        });
        customLookupTable.put(HEADER_GROUPID, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                ReportDeviceList rdl = rd.getReportDeviceList();
                DeviceGroup devGrp = rdl.getDeviceGroup();
                if (devGrp != null) {
                    return devGrp.getGroupID();
                } else {
                    return DeviceGroup.DEVICE_GROUP_ALL;
                }
            }
        });
        customLookupTable.put(HEADER_GROUPDESC, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                ReportDeviceList rdl = rd.getReportDeviceList();
                DeviceGroup devGrp = rdl.getDeviceGroup();
                if (devGrp != null) {
                    String desc = devGrp.getDescription();
                    return !desc.equals("")? desc : devGrp.getGroupID();
                } else {
                    return DeviceGroup.GetDeviceGroupAll(rd.getLocale());
                }
            }
        });
        customLookupTable.put(HEADER_TIMEZONE, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                return rd.getTimeZoneString();
            }
        });
        customLookupTable.put(HEADER_DATERANGE, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                PrivateLabel privLabel = rd.getPrivateLabel();
                I18N i18n = privLabel.getI18N(ReportLayout.class);
                ReportLayout rl = rd.getReportLayout();
                ReportConstraints rc = rd.getReportConstraints();
                long timeStart = rc.getTimeStart();
                long timeEnd   = rc.getTimeEnd();
                String timeFmt = rl.getDateTimeFormat(privLabel);
                String dateFmt = rl.getDateFormat(privLabel);
                String tzName  = rd.getTimeZoneString();
                TimeZone tz    = rd.getTimeZone();
                DateTime dtStr = new DateTime(timeStart,tz);
                DateTime dtEnd = new DateTime(timeEnd,tz);
                String fmtStr  = ((timeStart > 0L) && (timeStart == dtStr.getDayStart(tz)))? dateFmt : timeFmt;
                String fmtEnd  = ((timeEnd   > 0L) && (timeEnd   == dtEnd.getDayEnd(tz)  ))? dateFmt : timeFmt;
                StringBuffer dt = new StringBuffer();
                if ((timeStart > 0L) && (timeEnd > 0L)) {
                    // both start/end defined
                    String ds = dtStr.format(fmtStr,tz);
                    String de = dtEnd.format(fmtEnd,tz);
                    dt.append(i18n.getString("ReportLayout.throughDate","''{0}'' through ''{1}''",ds,de));
                } else
                if (timeStart > 0L) {
                    // only start defined
                    String ds = dtStr.format(fmtStr,tz);
                    dt.append(i18n.getString("ReportLayout.throughPresent","''{0}'' through Present",ds));
                } else
                if (timeEnd > 0L) {
                    // only end defined
                    String de = dtEnd.format(fmtEnd,tz);
                    dt.append(i18n.getString("ReportLayout.asOfDate","As of ''{0}''",de));
                } else {
                    // neither start/end defined
                }
                if (tzName != null) {
                    // show timezone
                    dt.append(" [");
                    dt.append(tzName);
                    dt.append("]");
                }
                return dt.toString();
            }
        });
        customLookupTable.put(HEADER_LIMIT, new CustomHeaderValue() {
            public String getValue(String arg, ReportData rd) {
                PrivateLabel privLabel = rd.getPrivateLabel();
                I18N i18n = privLabel.getI18N(ReportLayout.class);
                ReportLayout rl = rd.getReportLayout();
                ReportConstraints rc = rd.getReportConstraints();
                long rptLimit   = rc.getReportLimit();
                StringBuffer dt = new StringBuffer();
                if (rptLimit > 0L) {
                    // show specified report limit
                    EventData.LimitType type = rc.getSelectionLimitType();
                    if (EventData.LimitType.FIRST.equals(type)) {
                        if (rptLimit == 1L) {
                            dt.append(i18n.getString("ReportLayout.firstRecord","(First record)"));
                        } else {
                            // should only display this if limit has been exceeded
                            dt.append(i18n.getString("ReportLayout.firstLimitRecords","(First {0} records)",new Long(rptLimit)));
                        }
                    } else
                    if (EventData.LimitType.LAST.equals(type)) {
                        dt.append(" ");
                        if (rptLimit == 1L) {
                            dt.append(i18n.getString("ReportLayout.lastRecord","(Last record)"));
                        } else {
                            // should only display this if limit has been exceeded
                            dt.append(i18n.getString("ReportLayout.lastLimitRecords","(Last {0} records)",new Long(rptLimit)));
                        }
                    } else {
                        // will not occur
                        dt.append(" ");
                        dt.append(i18n.getString("ReportLayout.unknownType","(Unknown type)"));
                    }
                }
                return dt.toString();
            }
        });

    }

    private static interface CustomHeaderValue
    {
        public String getValue(String arg, ReportData rc);
    }

    // ------------------------------------------------------------------------
    
}
