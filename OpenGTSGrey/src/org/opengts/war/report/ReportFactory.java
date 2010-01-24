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
// - ReportFactory
//    - ReportLayout (singleton instantiated by ReportFactory)
//       - ReportTable (report format template)
//          - ReportHeader
//             - HeaderRowTemplate
//          - ReportBody
//             - BodyRowTemplate
//       - DataRowTemplate
//          - DataColumnTemplate
//             - HeaderColumnTemplate
//             - BodyColumnTemplate
//    - ReportData (intantiated at the time of a new report)
//       - ReportConstraints
// ----------------------------------------------------------------------------
// Change History:
//  2007/03/11  Martin D. Flynn
//     -Initial release
//  2007/03/25  Martin D. Flynn
//     -Updated to use 'DeviceList'
//  2007/06/13  Martin D. Flynn
//     -Renamed 'DeviceList' to 'ReportDeviceList'
//  2007/11/28  Martin D. Flynn
//     -Integrated use of 'ReportColumn'
//  2008/02/04  Martin D. Flynn
//     -Update to support localizing text found in 'reports.xml'
//  2008/09/19  Martin D. Flynn
//     -Removed obsolete 'Limit' tag (replaced long ago by 'SelectionLimit')
//  2008/12/01  Martin D. Flynn
//     -Added support for report properties
//  2009/04/02  Martin D. Flynn
//     -Added "ruleFactoryName" attribute to "MapIconSelector" and "RuleSelector" tags.
//  2009/05/24  Martin D. Flynn
//     -Added "optional" attribute to "Report" and "ReportLayout" tags.
//  2009/10/02  Martin D. Flynn
//     -Added 'sortable' attribute to report "Column" tag.
//  2009/11/01  Martin D. Flynn
//     -Added ReportOption support
// ----------------------------------------------------------------------------
package org.opengts.war.report;

import java.util.*;
import java.io.*;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.war.tools.*;

public class ReportFactory
{
    
    // ------------------------------------------------------------------------

    private static      boolean IGNORE_MISSING_REPORTS      = true;
    public static void setIgnoreMissingReports(boolean ignMissing)
    {
        IGNORE_MISSING_REPORTS = ignMissing;
    }

    // ------------------------------------------------------------------------

    public static final String REPORT_TYPE_DEVICE_DETAIL    = "device.detail";
    public static final String REPORT_TYPE_DEVICE_SUMMARY   = "device.summary";
    public static final String REPORT_TYPE_FLEET_SUMMARY    = "fleet.summary";
    public static final String REPORT_TYPE_DRIVER_PERFORM   = "driver.performance";
    public static final String REPORT_TYPE_IFTA_DETAIL      = "ifta.detail";
    public static final String REPORT_TYPE_SYSADMIN_SUMMARY = "sysadmin.summary";

    public static final String REPORT_TYPES[]               = new String[] {
        REPORT_TYPE_DEVICE_DETAIL,
        REPORT_TYPE_DEVICE_SUMMARY,
        REPORT_TYPE_FLEET_SUMMARY,
        REPORT_TYPE_DRIVER_PERFORM,
        REPORT_TYPE_IFTA_DETAIL,
        REPORT_TYPE_SYSADMIN_SUMMARY
    };
    
    public static String getReportTypeDescription(PrivateLabel privLabel, String rptType)
    {
        
        /* get 'report.xml' description */
        String desc = ReportFactory.getReportTypeDescription(rptType,privLabel.getLocale());
        if ((desc != null) && !desc.equals("")) {
            return desc;
        }
        
        /* return default descriptions */
        final I18N i18n = privLabel.getI18N(ReportFactory.class);
        if (rptType.equalsIgnoreCase(REPORT_TYPE_DEVICE_DETAIL)) {
            return i18n.getString("ReportFactory.deviceDetailReports","Device Detail Reports:");
        } else
        if (rptType.equalsIgnoreCase(REPORT_TYPE_DEVICE_SUMMARY)) {
            return i18n.getString("ReportFactory.deviceSummaryReports","Device Summary Reports:");
        } else
        if (rptType.equalsIgnoreCase(REPORT_TYPE_FLEET_SUMMARY)) {
            return i18n.getString("ReportFactory.fleetSummaryReports","Fleet Summary Reports:");
        } else
        if (rptType.equalsIgnoreCase(REPORT_TYPE_DRIVER_PERFORM)) {
            return i18n.getString("ReportFactory.driverPerformanceReports","Driver Performance Reports:");
        } else
        if (rptType.equalsIgnoreCase(REPORT_TYPE_IFTA_DETAIL)) {
            return i18n.getString("ReportFactory.iftaReports","I.F.T.A. Reports:");
        } else
        if (rptType.equalsIgnoreCase(REPORT_TYPE_SYSADMIN_SUMMARY)) {
            return i18n.getString("ReportFactory.sysadminReports","System Administrator Reports:");
        } else {
            return "";
        }
        
    }

    // ------------------------------------------------------------------------
    
    public  static final String TAG_ReportDefinition        = "ReportDefinition";
    public  static final String TAG_DefaultStyle            = "DefaultStyle";

    public  static final String TAG_ReportLayout            = "ReportLayout";
    public  static final String TAG_DateFormat              = "DateFormat";
    public  static final String TAG_TimeFormat              = "TimeFormat";
    public  static final String TAG_LayoutStyle             = "LayoutStyle";
    
    public  static final String TAG_ReportTypes             = "ReportTypes";
    public  static final String TAG_ReportType              = "ReportType";         // i18n

    public  static final String TAG_Report                  = "Report";
    public  static final String TAG_MenuDescription         = "MenuDescription";    // i18n
    public  static final String TAG_Title                   = "Title";              // i18n
    public  static final String TAG_Subtitle                = "Subtitle";           // i18n
    public  static final String TAG_SimpleColumns           = "SimpleColumns";
    public  static final String TAG_Columns                 = "Columns";
    public  static final String TAG_Column                  = "Column";             // i18n
    public  static final String TAG_MapIconSelector         = "MapIconSelector";
    public  static final String TAG_Properties              = "Properties";
    public  static final String TAG_Property                = "Property";
    public  static final String TAG_Options                 = "Options";
    public  static final String TAG_Option                  = "Option";
    public  static final String TAG_Description             = "Description";

    public  static final String TAG_Constraints             = "Constraints";
    public  static final String TAG_TimeStart               = "TimeStart";
    public  static final String TAG_TimeEnd                 = "TimeEnd";
    public  static final String TAG_ValidGPSRequired        = "ValidGPSRequired";
    public  static final String TAG_SelectionLimit          = "SelectionLimit";
    public  static final String TAG_ReportLimit             = "ReportLimit";
    public  static final String TAG_OrderAscending          = "OrderAscending";
    public  static final String TAG_OrderDescending         = "OrderDescending";
    public  static final String TAG_Where                   = "Where";
    public  static final String TAG_RuleSelector            = "RuleSelector";

    public  static final String ATTR_i18nPackage            = "i18nPackage";
    public  static final String ATTR_name                   = "name";
    public  static final String ATTR_class                  = "class";
    public  static final String ATTR_optional               = "optional";
    public  static final String ATTR_type                   = "type";
    public  static final String ATTR_i18n                   = "i18n";
    public  static final String ATTR_key                    = "key";
    public  static final String ATTR_arg                    = "arg";
    public  static final String ATTR_isGroup                = "isGroup";
    public  static final String ATTR_ruleFactoryName        = "ruleFactoryName";
    public  static final String ATTR_sysAdminOnly           = "sysAdminOnly";
    public  static final String ATTR_sortable               = "sortable";
    public  static final String ATTR_cssFile                = "cssFile";
    public  static final String ATTR_ifTrue                 = "ifTrue";
    public  static final String ATTR_ifFalse                = "ifFalse";

    // ------------------------------------------------------------------------

    /* used for global property definitions */
    private static final String PROP_ReportDefinition_      = TAG_ReportDefinition + ".";

    // ------------------------------------------------------------------------

    private static boolean hasParsingErrors = false;

    /* return true if errors were encounted loading 'reports.xml' */
    public static boolean hasParsingErrors()
    {
        return ReportFactory.hasParsingErrors;
    }
    
    protected static void _setHasParsingErrors(File xmlFile)
    {
        ReportFactory.hasParsingErrors = true;
    }

    // ------------------------------------------------------------------------

    private static boolean hasParsingWarnings = false;

    /* return true if errors were encounted loading 'reports.xml' */
    public static boolean hasParsingWarnings()
    {
        return ReportFactory.hasParsingWarnings;
    }
    
    protected static void _setHasParsingWarnings(File xmlFile)
    {
        ReportFactory.hasParsingWarnings = true;
    }

    // ------------------------------------------------------------------------

    public  static final String REPORT_FACTORY_XML          = "reports.xml";

    public static File _getReportXMLFile()
    {
        File cfgFile = RTConfig.getLoadedConfigFile();
        if (cfgFile != null) {
            return new File(cfgFile.getParentFile(), REPORT_FACTORY_XML);
        } else {
            return null;
        }
    }

    /* return an XML Document for the 'reports.xml' config file */
    private static Document _getDocument(File xmlFile)
    {

        /* valid file specified? */
        if (xmlFile == null) {
            Print.logError("ReportFactory XML file not specified: " + xmlFile);
            return null;
        } else
        if (!xmlFile.exists()) {
            Print.logError("ReportFactory XML file does not exist: " + xmlFile);
            return null;
        }

        /* create XML document */
        Document doc = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(xmlFile);
        } catch (ParserConfigurationException pce) {
            Print.logException("Parse error: ", pce);
        } catch (SAXException se) {
            Print.logException("Parse error: ", se);
        } catch (IOException ioe) {
            Print.logException("Parse error: ", ioe);
        }
        
        /* return */
        return doc;
        
    }

    /* return the value of the XML text node */
    private static String getNodeText(Node root, String repNewline)
    {
        StringBuffer text = new StringBuffer();
        
        /* extract String */
        if (root != null) {
            NodeList list = root.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node n = list.item(i);
                if (n.getNodeType() == Node.CDATA_SECTION_NODE) { // CDATA Section
                    text.append(n.getNodeValue());
                } else
                if (n.getNodeType() == Node.TEXT_NODE) {
                    text.append(n.getNodeValue());
                } else {
                    //Print.logWarn("Unrecognized node type: " + n.getNodeType());
                }
            }
        }

        /* remove CR, and handle NL */
        if (repNewline != null) {
            // 'repNewline' contains text which is used to replace detected '\n' charaters
            StringBuffer sb = new StringBuffer();
            String s[] = StringTools.parseString(text.toString(),"\n\r");
            for (int i = 0; i < s.length; i++) {
                String line = s[i].trim();
                if (!line.equals("")) {
                    if (sb.length() > 0) {
                        sb.append(repNewline);
                    }
                    sb.append(line);
                }
            }
            text = sb;
        }

        /* return String */
        return text.toString().trim();

    }

    /* load the 'private.xml' file */
    public static int loadReportDefinitionXML()
    {
        try {
            // load default file
            return ReportFactory._loadReportDefinitionXML(null);
        } catch (Throwable t) {
            Print.logException("Unable to load ReportFactory XML", t);
            ReportFactory._setHasParsingErrors(null);
            return 0;
        }
    }

    /* load the 'reports.xml' file */
    public static int loadReportDefinitionXML(File xmlFile)
    {
        try {
            return ReportFactory._loadReportDefinitionXML(xmlFile);
        } catch (Throwable t) {
            Print.logException("Unable to load ReportFactory XML", t);
            ReportFactory._setHasParsingErrors(xmlFile);
            return 0;
        }
    }

    /* load the 'private.xml' file */
    private static int _loadReportDefinitionXML(File xmlFile)
    {
        int count = 0;
        boolean isReload = (ReportFactoryMap != null);

        /* reset reports */
        ReportFactory.ReportFactoryMap = null;
        ReportFactory.hasParsingErrors = false;

        /* xml file specified? */
        if (xmlFile == null) {
            // get default file
            xmlFile = ReportFactory._getReportXMLFile(); 
            // 'xmlFile' may still be null
        }

        /* get XML document */
        Document xmlDoc = ReportFactory._getDocument(xmlFile);
        if (xmlDoc == null) {
            //Print.logError("Unable to create XML 'Document'");
            ReportFactory._setHasParsingErrors(xmlFile);
            return count;
        }

        /* get top-level tag */
        Element reportDef = xmlDoc.getDocumentElement();
        if (!reportDef.getTagName().equalsIgnoreCase(TAG_ReportDefinition)) {
            Print.logError("["+xmlFile+"] Invalid root tag ID: " + reportDef.getTagName());
            ReportFactory._setHasParsingErrors(xmlFile);
            return count;
        }

        /* I18N package name */
        String i18nPkgName = reportDef.getAttribute(ATTR_i18nPackage);
        if ((i18nPkgName == null) || i18nPkgName.equals("")) {
            i18nPkgName = ReportFactory.class.getPackage().getName();
        }

        /* parse Properties */
        RTProperties rptDefProps = new RTProperties();
        {
            // load Properties defined in XML
            NodeList propertiesNodes = reportDef.getElementsByTagName(TAG_Properties);
            for (int pn = 0; pn < propertiesNodes.getLength(); pn++) {
                Element propsTag = (Element)propertiesNodes.item(pn);
                NodeList propNodes = propsTag.getElementsByTagName(TAG_Property);
                for (int p = 0; p < propNodes.getLength(); p++) {
                    Element propTag = (Element)propNodes.item(p);
                    String propKey = XMLTools.getAttribute(propTag, ATTR_key, null);
                    if (!StringTools.isBlank(propKey)) {
                        String propVal = ReportFactory.getNodeText(propTag,"\\n");
                        rptDefProps.setString(propKey, propVal);
                    } else {
                        Print.logError("["+xmlFile+"] Report Property 'key' is blank");
                        ReportFactory._setHasParsingErrors(xmlFile);
                    }
                }
            }
            // override with properties defined in the runtime config files
            RTProperties globalProps = RTConfig.getProperties(PROP_ReportDefinition_,false);
            for (Object gk : globalProps.getPropertyKeys()) {
                Object gv = globalProps.getProperty(gk, null);
                if ((gk instanceof String) && ((String)gk).startsWith(PROP_ReportDefinition_) && (gv instanceof String)) {
                    String k = ((String)gk).substring(PROP_ReportDefinition_.length());
                    rptDefProps.setString(k,(String)gv);
                }
            }
        }

        /* parse DefaultStyle */
        Collection<String> dftCssFiles = new Vector<String>();
        StringBuffer       dftStyle    = new StringBuffer();
        NodeList     defaultStyleNodes = reportDef.getElementsByTagName(TAG_DefaultStyle);
        for (int dsl = 0; dsl < defaultStyleNodes.getLength(); dsl++) {
            Element dsTag = (Element)defaultStyleNodes.item(dsl);
            // CSS file
            String cssFile = XMLTools.getAttribute(dsTag, ATTR_cssFile, null);
            if (!StringTools.isBlank(cssFile)) {
                dftCssFiles.add(cssFile);
            }
            // custom style
            String style = ReportFactory.getNodeText(dsTag,null);
            if (!StringTools.isBlank(style)) {
                dftStyle.append(style);
                dftStyle.append("\n");
            }
        }
        ReportLayout.setDefaultCSSFiles(dftCssFiles);
        ReportLayout.setDefaultStyleSheet(ReportFactory.reformatStyle(dftStyle));
        //Print.logInfo("DefaultStyle: \n" + ReportLayout.getDefaultStyleSheet());

        /* parse ReportLayout */
        NodeList layoutList = reportDef.getElementsByTagName(TAG_ReportLayout);
        for (int rl = 0; rl < layoutList.getLength(); rl++) {
            Element reportLayout = (Element)layoutList.item(rl);
            String layoutClass   = reportLayout.getAttribute(ATTR_class);
            Boolean isOptional   = StringTools.parseBoolean(reportLayout.getAttribute(ATTR_optional),false);

            /* parse DateFormat */
            String dateFmt = null;
            NodeList dateFmtNodes = reportLayout.getElementsByTagName(TAG_DateFormat);
            for (int fmt = 0; fmt < dateFmtNodes.getLength(); fmt++) {
                Element fmtTag = (Element)dateFmtNodes.item(fmt);
                dateFmt = ReportFactory.getNodeText(fmtTag,"");
                break; // take only the first definition
            }
            
            /* parse TimeFormat */
            String timeFmt = null;
            NodeList timeFmtNodes = reportLayout.getElementsByTagName(TAG_TimeFormat);
            for (int fmt = 0; fmt < timeFmtNodes.getLength(); fmt++) {
                Element fmtTag = (Element)timeFmtNodes.item(fmt);
                timeFmt = ReportFactory.getNodeText(fmtTag,"");
                break; // take only the first definition
            }

            /* parse LayoutStyle */
            Collection<String> cssFiles = new Vector<String>();
            StringBuffer layoutStyle    = new StringBuffer();
            NodeList layoutStyleNodes   = reportLayout.getElementsByTagName(TAG_LayoutStyle);
            for (int dsl = 0; dsl < layoutStyleNodes.getLength(); dsl++) {
                Element lsTag = (Element)layoutStyleNodes.item(dsl);
                // CSS file
                String cssFile = XMLTools.getAttribute(lsTag, ATTR_cssFile, null);
                if (!StringTools.isBlank(cssFile)) {
                    cssFiles.add(cssFile);
                }
                // custom style
                String style = ReportFactory.getNodeText(lsTag,null);
                if (!StringTools.isBlank(style)) {
                    layoutStyle.append(style);
                    layoutStyle.append("\n");
                }
            }
            
            /* set layout style */
            boolean foundLayout = false;
            try {
                // invoke static method "getReportLayout", this returns the layout singleton instance
                MethodAction ma = new MethodAction(layoutClass, "getReportLayout");
                ReportLayout rptLayout = (ReportLayout)ma.invoke(); // may throw ClassNotFoundException, etc
                rptLayout.setDateTimeFormat(dateFmt,timeFmt);
                rptLayout.setCSSFiles(cssFiles);
                rptLayout.setStyleSheet(ReportFactory.reformatStyle(layoutStyle));
            } catch (ClassNotFoundException cnfe) {
                if (!IGNORE_MISSING_REPORTS && !isOptional) {
                    Print.logError("["+xmlFile+"] ReportLayout class not found: " + layoutClass);
                    ReportFactory._setHasParsingErrors(xmlFile);
                } else
                if (RTConfig.isDebugMode()) {
                    Print.logWarn("Optional ReportLayout class not found: " + layoutClass);
                    ReportFactory._setHasParsingWarnings(xmlFile);
                }
            } catch (NoSuchMethodException nsme) {
                Print.logError("["+xmlFile+"] ReportLayout static method not found: " + layoutClass + ".getReportLayout()");
                ReportFactory._setHasParsingErrors(xmlFile);
            } catch (Throwable t) {
                Print.logException("["+xmlFile+"] Exception while initializing ReportLayout: " + layoutClass, t);
                ReportFactory._setHasParsingErrors(xmlFile);
            }

        } // report layouts

        /* parse <ReportTypes> */
        NodeList rptTypesList = reportDef.getElementsByTagName(TAG_ReportTypes);
        for (int ty = 0; ty < rptTypesList.getLength(); ty++) {
            Element rptTypes = (Element)rptTypesList.item(ty);
            NodeList typeList = rptTypes.getElementsByTagName(TAG_ReportType);
            for (int c = 0; c < typeList.getLength(); c++) {
                Element type = (Element)typeList.item(c);
                String  typeName  = type.getAttribute(ATTR_name);
                boolean typeGroup = StringTools.parseBoolean(type.getAttribute(ATTR_isGroup),false);
                String i18nKey = type.getAttribute(ATTR_i18n);
                String typeDescDft = ReportFactory.getNodeText(type," ");
                I18N.Text typeDesc = ReportFactory.parseI18N(i18nPkgName,i18nKey,typeDescDft);
                ReportFactory.addReportType(typeName, typeGroup, typeDesc);
            }
        }

        /* parse <Report> */
        NodeList reportList = reportDef.getElementsByTagName(TAG_Report);
        for (int r = 0; r < reportList.getLength(); r++) {
            Element report            = (Element)reportList.item(r);
            String  rptName           = report.getAttribute(ATTR_name);
            String  rptType           = report.getAttribute(ATTR_type);
            String  rptClass          = report.getAttribute(ATTR_class);
            boolean rptOptional       = XMLTools.getAttributeBoolean(report, ATTR_optional    , false);
            boolean rptSysAdminOnly   = XMLTools.getAttributeBoolean(report, ATTR_sysAdminOnly, false);
            boolean rptTableSortable  = XMLTools.getAttributeBoolean(report, ATTR_sortable    , false);
            I18N.Text rptMenu         = null;
            I18N.Text rptTitle        = null;
            I18N.Text rptSubt         = null;
            ReportColumn rptCols[]    = null;
            java.util.List rptColList = null;
            ReportConstraints rptRC   = null;
            String rptIconSel         = null;
            RTProperties rptProps     = new RTProperties();
            OrderedMap<String,ReportOption> rptOptMap = null;

            /* report nodes */
            NodeList attrList         = report.getChildNodes();
            for (int c = 0; c < attrList.getLength(); c++) {

                /* get Node (only interested in 'Element's) */
                Node attrNode = attrList.item(c);
                if (!(attrNode instanceof Element)) {
                    continue;
                }

                /* parse node */
                String attrName = attrNode.getNodeName();
                Element attrElem = (Element)attrNode;
                if (attrName.equalsIgnoreCase(TAG_MenuDescription)) {
                    String i18nKey = attrElem.getAttribute(ATTR_i18n);
                    String textDft = ReportFactory.getNodeText(attrElem,"\\n");
                    rptMenu = ReportFactory.parseI18N(i18nPkgName,i18nKey,textDft);
                    //Print.logInfo("  MenuDescription: " + rptMenu);
                } else
                if (attrName.equalsIgnoreCase(TAG_Title)) {
                    String i18nKey = attrElem.getAttribute(ATTR_i18n);
                    String textDft = ReportFactory.getNodeText(attrElem,"\\n");
                    rptTitle = ReportFactory.parseI18N(i18nPkgName,i18nKey,textDft);
                    //Print.logInfo("  Title: " + rptTitle);
                } else
                if (attrName.equalsIgnoreCase(TAG_Subtitle)) {
                    String i18nKey = attrElem.getAttribute(ATTR_i18n);
                    String textDft = ReportFactory.getNodeText(attrElem,"\\n");
                    rptSubt = ReportFactory.parseI18N(i18nPkgName,i18nKey,textDft);
                    //Print.logInfo("  Subtitle: " + rptSubt);
                } else
                if ((rptCols == null) && attrName.equalsIgnoreCase(TAG_SimpleColumns)) {
                    String columns = ReportFactory.getNodeText(attrElem,null);
                    String cols[]  = StringTools.parseString(columns, ", \t\r\n");
                    java.util.List<ReportColumn> colList = new Vector<ReportColumn>();
                    for (int i = 0; i < cols.length; i++) {
                        String colName = cols[i].trim();
                        if (!colName.equals("")) {
                            String colKey = colName;
                            int ka = colKey.indexOf(':');
                            String colArg = null;
                            if (ka >= 0) {
                                colArg = colKey.substring(ka+1);
                                colKey = colKey.substring(0,ka);
                            }
                            ReportColumn rc = new ReportColumn(colKey, colArg, null);
                            rc.setSortable(rptTableSortable);
                            colList.add(rc);
                        }
                    }
                    rptCols = colList.toArray(new ReportColumn[colList.size()]);
                } else
                if ((rptCols == null) && attrName.equalsIgnoreCase(TAG_Columns)) {
                    java.util.List<ReportColumn> colList = new Vector<ReportColumn>();
                    NodeList columnList = attrElem.getElementsByTagName(TAG_Column);
                    for (int z = 0; z < columnList.getLength(); z++) {
                        Element   column   = (Element)columnList.item(z);
                        String    colName  = XMLTools.getAttribute(column, ATTR_name, column.getAttribute(ATTR_key));
                        String    ifTrue   = XMLTools.getAttribute(column, ATTR_ifTrue , null);
                        String    ifFalse  = XMLTools.getAttribute(column, ATTR_ifFalse, null);
                        if (!StringTools.isBlank(ifTrue ) && (rptDefProps.getBoolean(ifTrue ,true) != true )) {
                            // property is explicitly set to 'false' ... ignore column
                            Print.logDebug("Ignoring column ["+ifTrue +"==true]: " + rptName + "." + colName);
                        } else
                        if (!StringTools.isBlank(ifFalse) && (rptDefProps.getBoolean(ifFalse,true) != false)) {
                            // property is 'true' (ie. not 'false') ... ignore column
                            Print.logDebug("Ignoring column ["+ifFalse+"==false]: " + rptName + "." + colName);
                        } else {
                            String    colArg   = XMLTools.getAttribute(column, ATTR_arg , "");
                            boolean   colSort  = XMLTools.getAttributeBoolean(column, ATTR_sortable, true);
                            String    titlsStr = ReportFactory.getNodeText(column, "\\n");
                            I18N.Text colTitle = null;
                            if (!StringTools.isBlank(titlsStr)) {
                                String i18nKey = XMLTools.getAttribute(column, ATTR_i18n, null);
                                colTitle = ReportFactory.parseI18N(i18nPkgName, i18nKey, titlsStr);
                            }
                            ReportColumn rc = new ReportColumn(colName, colArg, colTitle);
                            rc.setSortable(rptTableSortable && colSort);
                            colList.add(rc);
                        }
                    }
                    rptCols = colList.toArray(new ReportColumn[colList.size()]);
                } else
                if ((rptOptMap == null) && attrName.equalsIgnoreCase(TAG_Options)) {
                    rptOptMap = new OrderedMap<String,ReportOption>();
                    NodeList optionList = attrElem.getElementsByTagName(TAG_Option);
                    for (int z = 0; z < optionList.getLength(); z++) {
                        Element option  = (Element)optionList.item(z);
                        String  optName = XMLTools.getAttribute(option, ATTR_name, "");
                        String  ifTrue  = XMLTools.getAttribute(option, ATTR_ifTrue , null);
                        String  ifFalse = XMLTools.getAttribute(option, ATTR_ifFalse, null);
                        if (StringTools.isBlank(optName)) {
                            Print.logError("["+xmlFile+"] Missing Option name");
                            ReportFactory._setHasParsingErrors(xmlFile);
                        } else
                        if (!StringTools.isBlank(ifTrue ) && (rptDefProps.getBoolean(ifTrue ,true) != true )) {
                            // property is explicitly set to 'false' ... ignore option
                            Print.logDebug("Ignoring option ["+ifTrue +"==true]: " + rptName + "." + optName);
                        } else
                        if (!StringTools.isBlank(ifFalse) && (rptDefProps.getBoolean(ifFalse,true) != false)) {
                            // property is 'true' (ie. not 'false') ... ignore option
                            Print.logDebug("Ignoring option ["+ifFalse+"==false]: " + rptName + "." + optName);
                        } else {
                            if (rptOptMap.containsKey(optName)) {
                                Print.logError("["+xmlFile+"] Option already defined: " + optName);
                                ReportFactory._setHasParsingErrors(xmlFile);
                                continue;
                            }
                            ReportOption rptOpt = new ReportOption(optName);
                            rptOptMap.put(optName,rptOpt);
                            NodeList optChildList = option.getChildNodes();
                            for (int zz = 0; zz < optChildList.getLength(); zz++) {
                                Node optChildNode = optChildList.item(zz);
                                if (!(optChildNode instanceof Element)) { continue; }
                                String optChildName  = optChildNode.getNodeName();
                                Element optChildElem = (Element)optChildNode;
                                if (optChildName.equalsIgnoreCase(TAG_Description)) {
                                    String i18nKey = optChildElem.getAttribute(ATTR_i18n);
                                    String textDft = ReportFactory.getNodeText(optChildElem,"\\n");
                                    rptOpt.setDescription(ReportFactory.parseI18N(i18nPkgName,i18nKey,textDft));
                                } else 
                                if (optChildName.equalsIgnoreCase(TAG_Property)) {
                                    String propKey = optChildElem.getAttribute(ATTR_key);
                                    if (!StringTools.isBlank(propKey)) {
                                        String propVal = ReportFactory.getNodeText(optChildElem,"\\n");
                                        rptOpt.setValue(propKey, propVal);
                                    } else {
                                        Print.logError("["+xmlFile+"] Option Property 'key' is blank: " + optName);
                                        ReportFactory._setHasParsingErrors(xmlFile);
                                    }
                                } else {
                                    Print.logError("["+xmlFile+"] Unrecognized TAG: " + optChildName);
                                    ReportFactory._setHasParsingErrors(xmlFile);
                                }
                            }
                        }
                    }
                } else
                if ((rptRC == null) && attrName.equalsIgnoreCase(TAG_Constraints)) {
                    rptRC = parseReportConstraintsXML(xmlFile, attrElem);
                    //Print.logInfo("ReportConstraints: ...");
                } else
                if ((rptIconSel == null) && attrName.equalsIgnoreCase(TAG_MapIconSelector)) {
                    String rfName = attrElem.getAttribute(ATTR_ruleFactoryName);
                    RuleFactory ruleFact = Device.getRuleFactory();
                    if (ruleFact == null) {
                        // no Device RuleFactory installed
                    } else
                    if (StringTools.isBlank(rfName) || rfName.equalsIgnoreCase(ruleFact.getName())) {
                        rptIconSel = ReportFactory.getNodeText(attrElem," "); // 'rptIconSel' is saved later
                        if (!ruleFact.checkSelectorSyntax(rptIconSel)) {
                            Print.logError("["+xmlFile+"] Invalid MapIconSelector syntax: " + rptIconSel + " [" + ruleFact.getName() + "]");
                            ReportFactory._setHasParsingErrors(xmlFile);
                        } else {
                            //Print.logInfo("MapIconSelector: " + rptIconSel);
                        }
                    } else {
                        //Print.logWarn("[" +xmlFile + "] Ignoring MapIconSelector for RuleFactory '"+rfName+"'");
                    }
                } else
                if (attrName.equalsIgnoreCase(TAG_Property)) {
                    String propKey = attrElem.getAttribute(ATTR_key);
                    if (!StringTools.isBlank(propKey)) {
                        String propVal = ReportFactory.getNodeText(attrElem,"\\n");
                        rptProps.setString(propKey, propVal);
                        //Print.logInfo("Report '%s' property: %s ==> %s", rptName, propKey, propVal);
                    } else {
                        Print.logError("["+xmlFile+"] Report Property 'key' is blank: " + rptName);
                        ReportFactory._setHasParsingErrors(xmlFile);
                    }
                } else {
                    Print.logError("["+xmlFile+"] Unrecognized tag name: " + attrName);
                    ReportFactory._setHasParsingErrors(xmlFile);
                }

            }

            /* create/add ReportFactory */
            try {

                /* initialize ReportFactory */
                ReportFactory rf = new ReportFactory();
                rf.setReportName(rptName);
                rf.setReportType(rptType);
                rf.setReportClassName(rptClass);
                rf.setMenuDescription(rptMenu);
                rf.setReportTitle(rptTitle);
                rf.setReportSubtitle(rptSubt);
                rf.setReportColumns(rptCols);
                rf.setReportConstraints(rptRC);
                rf.setMapIconSelector(rptIconSel);
                rf.setProperties(rptProps);
                rf.setSysAdminOnly(rptSysAdminOnly);
                rf.setTableSortable(rptTableSortable);
                rf.setReportOptionMap(rptOptMap);

                /* check report class */
                rf.getReportClass(); // may throw ReportException

                /* add */
                ReportFactory.addReportFactory(rf);
                count++;

            } catch (ReportException re) {
                if (!IGNORE_MISSING_REPORTS && !rptOptional) {
                    Print.logError("["+xmlFile+"] Report '" + rptName + "' [" + re.getMessage() + "]");
                    ReportFactory._setHasParsingErrors(xmlFile);
                } else
                if (RTConfig.isDebugMode()) {
                    Print.logWarn("Optional Report '" + rptName + "' [" + re.getMessage() + "]");
                    ReportFactory._setHasParsingWarnings(xmlFile);
                }
            }

        } // reports

        /* return number of reports loaded */
        if (isReload) {
            //Print.logInfo("Reloaded: " + xmlFile);
        } else {
            Print.logDebug("Loaded: " + xmlFile);
        }
        return count;
        
    }
    
    private static String reformatStyle(StringBuffer style)
    {
        String styleLines[] = StringTools.parseString(style.toString(), "\r\n");
        for (int i = 0; i < styleLines.length; i++) {
            styleLines[i] = styleLines[i].trim();
        }
        return StringTools.join(styleLines,'\n');
    }
    
    /* parse the TAG_Constraints element */
    private static ReportConstraints parseReportConstraintsXML(File xmlFile, Element dftConst)
    {
        ReportConstraints rc = new ReportConstraints();
        NodeList attrList = dftConst.getChildNodes();
        for (int c = 0; c < attrList.getLength(); c++) {

            /* get Node (only interested in 'Element's) */
            Node attrNode = attrList.item(c);
            if (!(attrNode instanceof Element)) {
                continue;
            }
                
            /* parse node */
            String attrName = attrNode.getNodeName();
            Element attrElem = (Element)attrNode;
            if (attrName.equalsIgnoreCase(TAG_TimeStart)) {
                rc.setTimeStart(StringTools.parseLong(ReportFactory.getNodeText(attrElem," "),-1L));
                //Print.logInfo("TimeStart: " + rc.getTimeStart());
            } else
            if (attrName.equalsIgnoreCase(TAG_TimeEnd)) {
                rc.setTimeEnd(StringTools.parseLong(ReportFactory.getNodeText(attrElem," "),-1L));
                //Print.logInfo("TimeEnd: " + rc.getTimeEnd());
            } else
            if (attrName.equalsIgnoreCase(TAG_ValidGPSRequired)) {
                rc.setValidGPSRequired(StringTools.parseBoolean(ReportFactory.getNodeText(attrElem," "),false));
                //Print.logInfo("ValidGPSRequired: " + rc.getValidGPSRequired());
            } else
            if (attrName.equalsIgnoreCase(TAG_OrderAscending)) {
                rc.setOrderAscending(StringTools.parseBoolean(ReportFactory.getNodeText(attrElem," "),true));
                //Print.logInfo("OrderAscending: " + rc.getOrderAscending());
            } else
            if (attrName.equalsIgnoreCase(TAG_OrderDescending)) {
                rc.setOrderAscending(!StringTools.parseBoolean(ReportFactory.getNodeText(attrElem," "),false));
                //Print.logInfo("OrderAscending: " + rc.getOrderAscending());
            } else
            if (attrName.equalsIgnoreCase(TAG_SelectionLimit)) {
                long limit = StringTools.parseLong(ReportFactory.getNodeText(attrElem," "),-1L);
                String typeStr = attrElem.getAttribute(ATTR_type);
                if (typeStr == null) { typeStr = ""; }
                EventData.LimitType type = EventData.LimitType.FIRST;
                if (typeStr.equalsIgnoreCase("first")) {
                    type = EventData.LimitType.FIRST;
                } else
                if (typeStr.equalsIgnoreCase("last")) {
                    type = EventData.LimitType.LAST;
                } else {
                    type = (limit > 0L)? EventData.LimitType.LAST : EventData.LimitType.FIRST;
                }
                rc.setSelectionLimit(type, limit);
                //Print.logInfo("Limit: (type=" + type + ") " + limit);
            } else
            if (attrName.equalsIgnoreCase(TAG_ReportLimit)) {
                long limit = StringTools.parseLong(ReportFactory.getNodeText(attrElem," "),-1L);
                rc.setReportLimit(limit);
            } else
            if (attrName.equalsIgnoreCase(TAG_Where)) {
                String typeStr = attrElem.getAttribute(ATTR_type); // <-- currently ignored
                rc.setWhere(ReportFactory.getNodeText(attrElem," "));
                //Print.logInfo("Where: " + rc.getWhere());
            } else
            if (attrName.equalsIgnoreCase(TAG_RuleSelector)) {
                String rfName = attrElem.getAttribute(ATTR_ruleFactoryName);
                RuleFactory ruleFact = Device.getRuleFactory();
                if (ruleFact == null) {
                    // no Device RuleFactory installed
                } else
                if ((StringTools.isBlank(rfName) || rfName.equalsIgnoreCase(ruleFact.getName()))) {
                    String ruleSel = ReportFactory.getNodeText(attrElem, " ");
                    if (!ruleFact.checkSelectorSyntax(ruleSel)) {
                        Print.logWarn("[" +xmlFile + "] Invalid RuleSelector syntax: " + ruleSel);
                    } else {
                        //Print.logInfo("RuleSelector: " + ruleSel);
                    }
                    rc.setRuleSelector(ruleSel);
                } else {
                    //Print.logWarn("[" +xmlFile + "] Ignoring RuleSelector for RuleFactory '"+rfName+"'");
                }
            } else {
                Print.logError("[" +xmlFile + "] Unrecognized tag: " + attrName);
                ReportFactory._setHasParsingErrors(xmlFile);
            }

        }
        return rc;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private static HashMap<String,ReportType> ReportTypeMap = null;
    
    private static class ReportType
    {
        private String    type = null;
        private boolean   isGroup = false;
        private I18N.Text desc = null;
        public ReportType(String type, boolean isGroup, I18N.Text desc) {
            this.type    = (type != null)? type : "";
            this.isGroup = isGroup;
            this.desc    = (desc != null)? desc : new I18N.Text();
        }
        public String getType() {
            return this.type;
        }
        public boolean isGroup() {
            return this.isGroup;
        }
        public String getDescription(Locale loc) {
            return this.desc.toString(loc);
        }
    }
    
    protected static ReportType _getReportType(String type)
    {
        return ((ReportTypeMap != null) && (type != null))? ReportTypeMap.get(type) : null;
    }
    
    public static boolean hasReportType(String type)
    {
        return (ReportFactory._getReportType(type) != null);
    }
    
    public static boolean getReportTypeIsGroup(String type)
    {
        ReportType rt = ReportFactory._getReportType(type);
        return (rt != null)? rt.isGroup() : false;
    }
    
    public static String getReportTypeDescription(String type, Locale loc)
    {
        ReportType rt = ReportFactory._getReportType(type);
        return (rt != null)? rt.getDescription(loc) : "";
    }

    /* add a PrivateLabel instance to the host map */
    public static void addReportType(String type, boolean isGroup, I18N.Text desc)
    {
        if ((type != null) && !type.equals("")) {
            if (ReportTypeMap == null) { ReportTypeMap = new HashMap<String,ReportType>(); }
            ReportTypeMap.put(type, new ReportType(type, isGroup, desc));
        }
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private static HashMap<String,ReportFactory> ReportFactoryMap = null;

    /* return a Collection of current ReportFactory instances */
    public static Collection<ReportFactory> getReportFactories() 
    {
        return (ReportFactoryMap != null)? ReportFactoryMap.values() : null;
    }

    /* return the PrivateLabel instance for the specified hostName */
    public static ReportFactory getReportFactory(String rptName, boolean isOptional) 
        throws ReportException
    {
        ReportFactory rptFact = ReportFactory._getReportFactory(rptName);
        if (rptFact == null) {
            if (isOptional) {
                return null;
            } else {
                throw new ReportException("Report name not found: " + rptName);
            }
        }
        return rptFact;
    }
    
    /* return the PrivateLabel instance for the specified hostName */
    protected static ReportFactory _getReportFactory(String rptName) 
    {
        // get report factory based on report name 
        if ((rptName != null) && (ReportFactoryMap != null)) {
            ReportFactory rptFact = ReportFactoryMap.get(rptName);
            if (rptFact != null) {
                return rptFact;
            }
        }
        return null;
    }
    
    /* return true if the named report exists */
    public static boolean hasReportFactory(String rptName)
    {
        return (ReportFactory._getReportFactory(rptName) != null);
    }

    /* add a PrivateLabel instance to the host map */
    public static void addReportFactory(ReportFactory rptFact)
        throws ReportException
    {
        if (rptFact != null) {
            
            /* get hash key name */
            String name = rptFact.getReportName();
            if ((name == null) || name.equals("")) {
                throw new ReportException("Report name not specified");
            } 

            /* already present? */
            if (ReportFactory._getReportFactory(name) != null) {
                throw new ReportException("Report name already exists: " + name);
            }

            /* return map */
            if (ReportFactoryMap == null) { 
                ReportFactoryMap = new HashMap<String,ReportFactory>(); 
            }
            
            /* add report */
            ReportFactoryMap.put(name, rptFact);
            
        }
    }

    /* validate all report classes */
    /*
    public static boolean validateReports()
    {
        if (ReportFactoryMap != null) {
            int errCount = 0;
            for (ReportFactory rf : ReportFactoryMap.values()) {
                try {
                    Class rptClass = rf.getReportClass();
                } catch (ReportException re) {
                    Print.logWarn("Report '%s': %s", rf.getReportName(), re.getMessage());
                    errCount++;
                }
            }
            return (errCount == 0);
        } else {
            return true;
        }
    }
    */

    // ------------------------------------------------------------------------

    public static boolean           SAVE_I18N_STRINGS = false;
    public static Set<I18N.Text>    I18N_STRINGS      = null;

    /* parse I18N key/text */
    protected static I18N.Text parseI18N(String pkgName, String i18nKey, String dftStr)
    {
        // pkgName - the location of the "LocalStrings_XX.properties" file
        // i18nKey - the key used to look up the localized string
        // dftStr  - the default value to return if the key is not found
        
        /* no key/value? */
        if (((i18nKey == null) || i18nKey.equals("")) && 
            ((dftStr  == null) || dftStr.equals("") )   ) {
            return null;
        }
        
        /* create/return I18N text */
        I18N.Text text = I18N.parseText(pkgName, i18nKey, dftStr);
        if (SAVE_I18N_STRINGS) {
            if (I18N_STRINGS == null) { I18N_STRINGS = new OrderedSet<I18N.Text>(); }
            I18N_STRINGS.add(text);
        }
        return text;
        
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private String                          className           = null;
    private Class                           classObj            = null;
    
    private ReportColumn                    reportColumns[]     = null;
    private String                          reportName          = "report";
    private I18N.Text                       reportTitle         = null;
    private I18N.Text                       reportSubtitle      = null;
    private String                          reportType          = REPORT_TYPE_DEVICE_DETAIL;
    private I18N.Text                       menuDescription     = null;

    private ReportConstraints               dftConstraints      = null;

    private String                          mapIconSelector     = null;

    private RTProperties                    reportProperties    = null;
    
    private OrderedMap<String,ReportOption> reportOptions       = null;
    
    private boolean                         isSysAdminOnly      = false;
    
    private boolean                         isTableSortable     = false;

    // ------------------------------------------------------------------------

    private ReportFactory()
    {
        super();
    }

    // ------------------------------------------------------------------------

    /* set the report name */
    private void setReportName(String rn)
        throws ReportException
    {
        if ((rn == null) || rn.equals("")) {
            throw new ReportException("Report name not specified");
        } 
        this.reportName = rn;
    }

    /* return report name */
    public String getReportName()
    {
        return (this.reportName != null)? this.reportName : "report";
    }

    // ------------------------------------------------------------------------

    /* set the report type */
    private void setReportType(String rt)
        throws ReportException
    {
        if ((rt == null) || rt.equals("")) {
            throw new ReportException("Report type not specified");
        } else
        if (!ReportFactory.hasReportType(rt)) {
            throw new ReportException("Report type not defined: " + rt);
        }
        this.reportType = rt;
    }

    /* return report name */
    public String getReportType()
    {
        return (this.reportType != null)? this.reportType : REPORT_TYPE_DEVICE_DETAIL;
    }

    /* return true if this report is based on a 'group' of devices */
    public boolean getReportTypeIsGroup()
    {
        return ReportFactory.getReportTypeIsGroup(this.getReportType());
    }
    
    // ------------------------------------------------------------------------

    public void setSysAdminOnly(boolean sysAdmin)
    {
        this.isSysAdminOnly = sysAdmin;
    }

    public boolean getSysAdminOnly()
    {
        return this.isSysAdminOnly;
    }

    public boolean isSysAdminOnly()
    {
        return this.isSysAdminOnly;
    }
    
    // ------------------------------------------------------------------------

    public void setTableSortable(boolean sortable)
    {
        this.isTableSortable = sortable;
    }

    public boolean getTableSortable()
    {
        return this.isTableSortable;
    }

    public boolean isTableSortable()
    {
        return this.isTableSortable;
    }

    // ------------------------------------------------------------------------

    /* set the report class name */
    private void setReportClassName(String cn)
        throws ReportException
    {
        if (StringTools.isBlank(cn)) {
            throw new ReportException("Report class name not specified");
        } 
        this.className = cn;
    }
    
    /* return the report class name */
    public String getReportClassName()
    {
        return this.className;
    }
    
    public Class getReportClass()
        throws ReportException
    {
        if (this.classObj == null) {
            
            /* report class name */
            String cn = this.getReportClassName();
            if (StringTools.isBlank(cn)) {
                throw new ReportException("Report class name not specified");
            }
            
            /* get report class */
            try {
                Class rptClass = Class.forName(cn);
                if (!ReportData.class.isAssignableFrom(rptClass)) {
                    throw new ReportException(cn + " does not implement interface ReportData");
                }
                this.classObj = rptClass;
            } catch (ClassNotFoundException cnfe) {
                throw new ReportException("Class not found: " + cn);
            } catch (Throwable t) {
                throw new ReportException("Unable to load class: " + cn, t);
            }
            
        }
        return this.classObj;
    }

    // ------------------------------------------------------------------------
    
    public ReportData createReport(
        ReportEntry reportEntry, String reportOptionID, 
        RequestProperties reqState, 
        Device device)
        throws ReportException
    {
        String rptClassName = StringTools.className(this.getReportClass());
        if (device == null) {
            //Print.logInfo("Creating Group 'All' Report: " + rptClassName);
            return this._createReport(reportEntry, reportOptionID, reqState, (ReportDeviceList)null);
        } else {
            //Print.logInfo("Creating Device '"+device+"' Report: " + rptClassName);
            Account account = reqState.getCurrentAccount();
            User    user    = reqState.getCurrentUser();
            return this._createReport(reportEntry, reportOptionID, reqState, new ReportDeviceList(account,user,device));
        }
    }

    public ReportData createReport(
        ReportEntry reportEntry, String reportOptionID, 
        RequestProperties reqState, 
        DeviceGroup group)
        throws ReportException
    {
        String rptClassName = StringTools.className(this.getReportClass());
        if (group == null) {
            //Print.logInfo("Creating Group 'All' Report: " + rptClassName);
            return this._createReport(reportEntry, reportOptionID, reqState, (ReportDeviceList)null);
        } else {
            //Print.logInfo("Creating Group '"+group+"' Report: " + rptClassName);
            Account account = reqState.getCurrentAccount();
            User    user    = reqState.getCurrentUser();
            return this._createReport(reportEntry, reportOptionID, reqState, new ReportDeviceList(account,user,group));
        }
    }
 
    public ReportData createReport(
        ReportEntry reportEntry, String reportOptionID, 
        RequestProperties reqState, 
        ReportDeviceList deviceList)
        throws ReportException
    {
        //Print.logStackTrace("Creating Report: " + deviceList);
        return this._createReport(reportEntry, reportOptionID, reqState, deviceList);
    }

    protected ReportData _createReport(
        ReportEntry reportEntry, String reportOptionID, 
        RequestProperties reqState, 
        ReportDeviceList deviceList)
        throws ReportException
    {
        Object reportInstance = null;
        Class rptClass = this.getReportClass();
        
        /* ReportEntry matches this ReportFactory? */
        if ((reportEntry != null) && (reportEntry.getReportFactory() != this)) {
            throw new ReportException("Invalid ReportEntry: " + this.getReportName());
        }

        /* create report instance */
        try {
            if (reportEntry != null) {
                // new 'ReportEntry' generation
                Class argTypes[] = new Class[] { 
                    ReportEntry.class, RequestProperties.class, ReportDeviceList.class
                };
                MethodAction rc = new MethodAction(rptClass, MethodAction.CONSTRUCTOR, argTypes);
                reportInstance = rc.invoke(new Object[] { reportEntry, reqState, deviceList });
            } else {
                Print.logWarn("ReportEntry not specified ...");
                throw new Throwable("No ReportEntry ... try again using ReportFactory");
            }
        } catch (Throwable reTh) {
            try {
                // legacy 'ReportFactory' generation
                Class argTypes[] = new Class[] { 
                    ReportFactory.class, RequestProperties.class, Account.class, User.class, ReportDeviceList.class
                };
                MethodAction rc = new MethodAction(rptClass, MethodAction.CONSTRUCTOR, argTypes);
                Account account = reqState.getCurrentAccount();
                User    user    = reqState.getCurrentUser();
                reportInstance = rc.invoke(new Object[] { this, reqState, account, user, deviceList });
                Print.logInfo("Report not yet converted to new constructor: " + this.getReportName());
            } catch (ReportException re) {
                throw re; // re-throw
            } catch (Throwable rfTh) {
                throw new ReportException("Unable to create report: " + this.getReportClassName(), rfTh);
            }
        }
        
        /* invalid instance? */
        if (!(reportInstance instanceof ReportData)) {
            throw new ReportException("Report class is not a subclass of ReportData");
        }

        /* init/return report */
        Locale locale = reqState.getLocale();
        ReportData report = (ReportData)reportInstance;
        report.setReportName(this.getReportName());
        report.setReportTitle(this.getReportTitle(locale));
        report.setReportSubtitle(this.getReportSubtitle(locale));
        report.setReportColumns(this.getReportColumns());
        report.setReportConstraints(this.getReportConstraints());
        report.setMapIconSelector(this.getMapIconSelector());
        
        /* report option */
        report.setReportOptionID(reportOptionID);
        
        /* return report */
        return report;

    }

    // ------------------------------------------------------------------------

    /* set menu description */
    private void setMenuDescription(I18N.Text text)
    {
        this.menuDescription = text;
    }
    
    /* return I18N text menu description */
    public I18N.Text getMenuDescription()
    {
        return this.menuDescription;
    }

    /* return menu description */
    public String getMenuDescription(Locale loc, String dft)
    {
        return (this.menuDescription != null)? this.menuDescription.toString(loc) : dft;
    }

    /* return menu description */
    public String getMenuDescription(Locale loc)
    {
        return this.getMenuDescription(loc,"Menu Item");
    }

    // ------------------------------------------------------------------------

    /* set report title */
    private void setReportTitle(I18N.Text rt)
    {
        this.reportTitle = rt;
    }
    
    /* return I18N text report title */
    public I18N.Text getReportTitle()
    {
        return this.reportTitle;
    }

    /* return report title */
    public String getReportTitle(Locale loc, String dft)
    {
        return (this.reportTitle != null)? this.reportTitle.toString(loc) : dft;
    }

    /* return report title */
    public String getReportTitle(Locale loc)
    {
        return this.getReportTitle(loc,"A Report");
    }

    // ------------------------------------------------------------------------

    /* set report subtitle */
    private void setReportSubtitle(I18N.Text st)
    {
        this.reportSubtitle = st;
    }

    /* return report subtitle */
    public I18N.Text getReportSubtitle()
    {
        return this.reportSubtitle;
    }

    /* return report subtitle */
    public String getReportSubtitle(Locale loc, String dft)
    {
        return (this.reportSubtitle != null)? this.reportSubtitle.toString(loc) : dft;
    }

    /* return report subtitle */
    public String getReportSubtitle(Locale loc)
    {
        return this.getReportSubtitle(loc,"${deviceDesc} [${deviceId}]\n${dateRange}");
    }

    // ------------------------------------------------------------------------

    /* return report columns */
    private void setReportColumns(ReportColumn rc[])
    {
        this.reportColumns = rc;
    }

    /* return report columns */
    public ReportColumn[] getReportColumns()
    {
        return (this.reportColumns != null)? this.reportColumns : new ReportColumn[0];
    }

    // ------------------------------------------------------------------------

    /* return report constraints */
    private void setReportConstraints(ReportConstraints rc)
    {
        this.dftConstraints = rc;
    }
    
    /* return true if this report factory has default constraints */
    public boolean hasReportConstraints()
    {
        return (this.dftConstraints != null);
    }

    /* return a clone of the ReportConstraints */
    public ReportConstraints getReportConstraints()
    {
        if (this.dftConstraints == null) {
            return null;
        } else {
            return (ReportConstraints)this.dftConstraints.clone();
        }
    }

    // ------------------------------------------------------------------------
    // Map icon selector

    /* set icon selector */
    public void setMapIconSelector(String iconSel)
    {
        this.mapIconSelector = ((iconSel != null) && !iconSel.equals(""))? iconSel : null;
    }

    /* return icon selector (may return null) */
    public String getMapIconSelector()
    {
        return this.mapIconSelector;
    }

    // ------------------------------------------------------------------------
    // Properties
    
    /* set properties */
    public void setProperties(RTProperties props)
    {
        this.reportProperties = props;
    }
    
    /* get properties */
    public RTProperties getProperties()
    {
        if (this.reportProperties == null) { this.reportProperties = new RTProperties(); }
        return this.reportProperties;
    }

    // ------------------------------------------------------------------------
    // ReportOptions

    public boolean hasReportOptions()
    {
        return !ListTools.isEmpty(this.reportOptions);
    }

    public int getReportOptionCount()
    {
        return !ListTools.isEmpty(this.reportOptions)? this.reportOptions.size() : 0;
    }

    public void setReportOptionMap(OrderedMap<String,ReportOption> rptOptMap)
    {
        this.reportOptions = rptOptMap;
    }
    
    public OrderedMap<String,ReportOption> getReportOptionMap()
    {
        return this.reportOptions;
    }
    
    public ReportOption getReportOption(String name)
    {
        if ((this.reportOptions == null) || (name == null)) {
            return null;
        } else {
            return this.reportOptions.get(name);
        }
    }
    
    public OrderedMap<String,String> getReportOptionDescriptionMap(RequestProperties reqState)
    {
        if (ListTools.isEmpty(this.reportOptions)) {
            return null;
        } else {
            Locale locale = reqState.getLocale();
            OrderedMap<String,String> descMap = new OrderedMap<String,String>();
            for (ReportOption rptOpt : this.reportOptions.values()) {
                String key = rptOpt.getName();
                String val = StringTools.replaceKeys(rptOpt.getDescription(locale),reqState);
                descMap.put(key,val);
            }
            return descMap;
        }
    }

    // ------------------------------------------------------------------------
    // ReportTextInput

    public boolean hasReportTextInput()
    {
        return false;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    
    private static final String ARG_XML[]       = new String[] { "xml"      };
    private static final String ARG_OUT[]       = new String[] { "out"      };
    private static final String ARG_REPORT[]    = new String[] { "report"   };
    private static final String ARG_INFO[]      = new String[] { "info"     };
    private static final String ARG_ACCOUNT[]   = new String[] { "account"  };
    private static final String ARG_DEVICE[]    = new String[] { "device"   };
    private static final String ARG_FORMAT[]    = new String[] { "format"   };

    // Debug: report testing ...
    public static void main(String argv[])
    {
        DBConfig.cmdLineInit(argv,true);  // main
        File   reportXML = RTConfig.getFile(ARG_XML, new File("./" + REPORT_FACTORY_XML));
        File   output    = RTConfig.getFile(ARG_OUT, null);
        String rptName   = RTConfig.getString(ARG_REPORT, "event.detail"); // debug
        String rptInfo   = RTConfig.getString(ARG_INFO, null);
        String accountID = RTConfig.getString(ARG_ACCOUNT, null);
        String deviceID  = RTConfig.getString(ARG_DEVICE, null);
        String format    = RTConfig.getString(ARG_FORMAT, ReportPresentation.FORMAT_HTML);
        if (rptInfo != null) { rptName = rptInfo; }

        /* load 'reports.xml' */
        if (reportXML == null) {
            Print.logError("'"+REPORT_FACTORY_XML+"' file not specified");
            System.exit(1);
        }
        int rptCount = loadReportDefinitionXML(reportXML);
        if (rptCount <= 0) {
            Print.logError("No reports found");
            System.exit(1);
        }
        
        /* Account/Device specified? */
        if ((accountID == null) || accountID.equals("")) {
            Print.logWarn("Missing Account ...");
            System.exit(0);
        } else
        if ((deviceID == null) || deviceID.equals("")) {
            Print.logWarn("Missing Device ...");
            System.exit(0);
        }

        /* report constraints */
        Print.logInfo("Attempting to load Account/Device: " + accountID + "/" + deviceID);
        Account acct = null;
        Device  dev  = null;
        try {
            acct = Account.getAccount(accountID);
            if (acct != null) {
                dev = Device.getDevice(acct, deviceID);
                if (dev == null) {
                    Print.logError("Device not found: " + accountID + "/" + deviceID);
                    System.exit(1);
                }
            } else {
                Print.logError("Account not found: " + accountID);
                System.exit(1);
            }
        } catch (Throwable t) {
            Print.logException("Error getting Account/Device", t);
            System.exit(1);
        }
        
        /* open output */
        PrintWriter out = null;
        if (output != null) {
            try {
                out = new PrintWriter(new FileOutputStream(output));
            } catch (IOException ioe) {
                Print.logError("Unable to open output: " + output);
                out = null;
            }
        }

        //Print.logInfo("Creating report ...");
        try {
            RequestProperties reqState = new RequestProperties();
            reqState.setCurrentAccount(acct);
            ReportFactory rf = ReportFactory.getReportFactory(rptName, false);
            ReportEntry re = new ReportEntry(rf, "");
            ReportData rpt = rf.createReport(re, null, reqState, dev);
            TimeZone tz = TimeZone.getTimeZone(acct.getTimeZone());
            ReportConstraints rc = rpt.getReportConstraints();
            rc.setTimeStart(-1L);
            rc.setTimeEnd(new DateTime(tz).getDayEnd(tz));
            Print.logInfo("Generating report: " + rpt.getClass().getName());
            PrintWriter pw = (out != null)? out : new PrintWriter(System.out);
            if (rptInfo == null) {
                pw.print("<html>\n"); 
                pw.print("<head>\n"); 
                rpt.getReportLayout().writeReportStyle(format, rpt, pw, 1);
                pw.print("</head>\n"); 
                pw.print("<body>\n"); 
                rpt.getReportLayout().writeReport(format, rpt, pw, 1);
                pw.print("</body>\n"); 
                pw.print("</html>\n"); 
            } else {
                Print.logInfo("ReoprtFactory constraints: " + rf.getReportConstraints());
                Print.logInfo("ReoprtData constraints   : " + rpt.getReportConstraints());
            }
        } catch (ReportException re) {
            Print.logException("Error generating report", re);
            System.exit(1);
        } catch (Throwable t) {
            Print.logException("Error generating report", t);
            System.exit(1);
        }
        
        /* close output */
        if (out != null) {
            try { out.close(); } catch (Throwable t) {/*ignore*/}
            out = null;
        }
        
    }

}
