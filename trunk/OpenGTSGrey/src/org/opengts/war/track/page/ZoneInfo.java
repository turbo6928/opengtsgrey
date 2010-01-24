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
//  2008/08/08  Martin D. Flynn
//     -Initial release
//  2008/08/17  Martin D. Flynn
//     -Added "Distance" title line (below "Cursor Location")
//     -Fix display of View/Edit buttons on creation of first user.
//  2008/09/01  Martin D. Flynn
//     -Added delete confirmation
//  2008/10/16  Martin D. Flynn
//     -Update with new ACL usage
//  2008/12/01  Martin D. Flynn
//     -Added ability to display multiple points
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
import org.opengts.db.tables.*;
import org.opengts.geocoder.GeocodeProvider;

import org.opengts.war.tools.*;
import org.opengts.war.track.*;

public class ZoneInfo
    extends WebPageAdaptor
    implements Constants
{

    // ------------------------------------------------------------------------
    // 'private.xml' properties

    // PrivateLabel.PROP_ZoneInfo_mapControlLocation
    private static final String CONTROLS_ON_LEFT[]              = new String[] { "left", "true" };

    // ------------------------------------------------------------------------

    private static final double MIN_RADIUS_METERS               = Geozone.MIN_RADIUS_METERS;
    private static final double MAX_RADIUS_METERS               = Geozone.MAX_RADIUS_METERS;
    
    private static final double DEFAULT_ZONE_RADIUS             = 20000.0;

    // ------------------------------------------------------------------------

    private static final String OVERLAP_PRIORITY[]              = new String[] { "0", "1", "2", "3", "4", "5" };

    // ------------------------------------------------------------------------
    // Parameters

    // forms 
    public  static final String FORM_ZONE_SELECT                = "ZoneInfoSelect";
    public  static final String FORM_ZONE_EDIT                  = "ZoneInfoEdit";
    public  static final String FORM_ZONE_NEW                   = "ZoneInfoNew";

    // commands
    public  static final String COMMAND_INFO_UPDATE             = "update";
    public  static final String COMMAND_INFO_SELECT             = "select";
    public  static final String COMMAND_INFO_NEW                = "new";

    // submit
    public  static final String PARM_SUBMIT_EDIT                = "z_subedit";
    public  static final String PARM_SUBMIT_VIEW                = "z_subview";
    public  static final String PARM_SUBMIT_CHG                 = "z_subchg";
    public  static final String PARM_SUBMIT_DEL                 = "z_subdel";
    public  static final String PARM_SUBMIT_NEW                 = "z_subnew";

    // buttons
    public  static final String PARM_BUTTON_CANCEL              = "u_btncan";
    public  static final String PARM_BUTTON_BACK                = "u_btnbak";

    // parameters
    public  static final String PARM_NEW_ID                     = "z_newid";
    public  static final String PARM_ZONE_SELECT                = "z_zone";
    public  static final String PARM_PRIORITY                   = "z_priority";
    public  static final String PARM_REV_GEOCODE                = "z_revgeo";
    public  static final String PARM_ARRIVE_NOTIFY              = "z_arrive";
    public  static final String PARM_DEPART_NOTIFY              = "z_depart";
    public  static final String PARM_CLIENT_UPLOAD              = "z_upload";
    public  static final String PARM_CLIENT_ID                  = "z_clntid";
    public  static final String PARM_ZONE_DESC                  = "z_desc";
    public  static final String PARM_ZONE_RADIUS                = "z_radius";
    public  static final String PARM_ZONE_INDEX                 = "z_index";

    public  static final String PARM_ZONE_LATITUDE_             = "z_lat";
    public static final String  PARM_ZONE_LATITUDE[]            = new String[] {
        PARM_ZONE_LATITUDE_ + "0",
        PARM_ZONE_LATITUDE_ + "1",
        PARM_ZONE_LATITUDE_ + "2",
        PARM_ZONE_LATITUDE_ + "3",
        PARM_ZONE_LATITUDE_ + "4",
        PARM_ZONE_LATITUDE_ + "5",
    };
    public  static final String PARM_ZONE_LONGITUDE_            = "z_lon";
    public static final String  PARM_ZONE_LONGITUDE[]           = new String[] {
        PARM_ZONE_LONGITUDE_ + "0",
        PARM_ZONE_LONGITUDE_ + "1",
        PARM_ZONE_LONGITUDE_ + "2",
        PARM_ZONE_LONGITUDE_ + "3",
        PARM_ZONE_LONGITUDE_ + "4",
        PARM_ZONE_LONGITUDE_ + "5",
    };

    // sort ID
    private static final int    DEFAULT_SORT_ID                 = 0;
    
    // point index
    private static final int    DEFAULT_POINT_INDEX             = 0;

    // ------------------------------------------------------------------------
    // WebPage interface
    
    public ZoneInfo()
    {
        this.setBaseURI(Track.BASE_URI());
        this.setPageName(PAGE_ZONE_INFO);
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
        I18N i18n = privLabel.getI18N(ZoneInfo.class);
        return super._getMenuDescription(reqState,i18n.getString("ZoneInfo.editMenuDesc","View/Edit Geozone Information"));
    }
   
    public String getMenuHelp(RequestProperties reqState, String parentMenuName)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(ZoneInfo.class);
        return super._getMenuHelp(reqState,i18n.getString("ZoneInfo.editMenuHelp","View and Edit Geozone information"));
    }

    // ------------------------------------------------------------------------

    public String getNavigationDescription(RequestProperties reqState)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(ZoneInfo.class);
        return super._getNavigationDescription(reqState,i18n.getString("ZoneInfo.navDesc","Geozone Admin"));
    }

    public String getNavigationTab(RequestProperties reqState)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(ZoneInfo.class);
        return super._getNavigationTab(reqState,i18n.getString("ZoneInfo.navTab","Geozone Admin"));
    }

    // ------------------------------------------------------------------------
    
    public void writePage(
        final RequestProperties reqState,
        String pageMsg)
        throws IOException
    {
        final HttpServletRequest request = reqState.getHttpServletRequest();
        final PrivateLabel privLabel = reqState.getPrivateLabel(); // never null
        final I18N    i18n     = privLabel.getI18N(ZoneInfo.class);
        final Locale  locale   = privLabel.getLocale();
        final Account currAcct = reqState.getCurrentAccount(); // never null
        final User    currUser = reqState.getCurrentUser(); // may be null
        final String  pageName = this.getPageName();
        final boolean showOverlapPriority  = Geozone.supportsPriority() && privLabel.getBooleanProperty(PrivateLabel.PROP_ZoneInfo_showOverlapPriority,false);
        final boolean showArriveDepartZone = Device.hasRuleFactory() || privLabel.getBooleanProperty(PrivateLabel.PROP_ZoneInfo_showArriveDepartZone,false);
        final boolean showClientUploadZone = privLabel.getBooleanProperty(PrivateLabel.PROP_ZoneInfo_showClientUploadZone,false);
        String  m = pageMsg;
        boolean error = false;

        /* argument zone-id */
        String zoneList[] = null;
        try {
            zoneList = Geozone.getGeozoneIDsForAccount(currAcct.getAccountID());
        } catch (DBException dbe) {
            zoneList = new String[0];
        }
        String selZoneID = AttributeTools.getRequestString(reqState.getHttpServletRequest(), PARM_ZONE_SELECT, "");
        if (StringTools.isBlank(selZoneID)) {
            if ((zoneList.length > 0) && (zoneList[0] != null)) {
                selZoneID = zoneList[0];
            } else {
                selZoneID = "";
            }
            //Print.logWarn("No Zone selected, choosing first zone: %s", selZoneID);
        }
        if (zoneList.length == 0) {
            zoneList = new String[] { selZoneID };
        }
        
        /* Geozone db */
        Geozone selZone = null;
        try {
            selZone = !selZoneID.equals("")? Geozone.getGeozone(currAcct,selZoneID,DEFAULT_SORT_ID,false) : null;
        } catch (DBException dbe) {
            // ignore
        }

        /* ACL */
        boolean allowNew    = privLabel.hasAllAccess(currUser, this.getAclName());
        boolean allowDelete = allowNew;
        boolean allowEdit   = allowNew || privLabel.hasWriteAccess(currUser, this.getAclName());
        boolean allowView   = allowEdit || privLabel.hasReadAccess(currUser, this.getAclName());

        /* command */
        String zoneCmd      = reqState.getCommandName();
        boolean listZones   = false;
        boolean updateZone  = zoneCmd.equals(COMMAND_INFO_UPDATE);
        boolean selectZone  = zoneCmd.equals(COMMAND_INFO_SELECT);
        boolean newZone     = zoneCmd.equals(COMMAND_INFO_NEW);
        boolean deleteZone  = false;
        boolean editZone    = false;
        boolean viewZone    = false;
        
        /* submit buttons */
        String submitEdit   = AttributeTools.getRequestString(request, PARM_SUBMIT_EDIT, "");
        String submitView   = AttributeTools.getRequestString(request, PARM_SUBMIT_VIEW, "");
        String submitChange = AttributeTools.getRequestString(request, PARM_SUBMIT_CHG , "");
        String submitNew    = AttributeTools.getRequestString(request, PARM_SUBMIT_NEW , "");
        String submitDelete = AttributeTools.getRequestString(request, PARM_SUBMIT_DEL , "");

        /* MapProvider support */
        final MapProvider mapProvider = reqState.getMapProvider();
        final boolean mapSupportsCursorLocation = ((mapProvider != null) && mapProvider.isFeatureSupported(MapProvider.FEATURE_LATLON_DISPLAY));
        final boolean mapSupportsDistanceRuler  = ((mapProvider != null) && mapProvider.isFeatureSupported(MapProvider.FEATURE_DISTANCE_RULER));
        final boolean mapSupportsGeozones       = ((mapProvider != null) && mapProvider.isFeatureSupported(MapProvider.FEATURE_GEOZONES));
        final int     mapGeozonePointCount      = (mapProvider != null)? mapProvider.getGeozoneSupportedPointCount(Geozone.GeozoneType.POINT_RADIUS.getIntValue()) : 0;

        /* sub-command */
        String newZoneID = null;
        if (newZone) {
            if (!allowNew) {
                // not authorized to create new Geozones
                Print.logInfo("Not authorized to create a new Geozone ...");
                newZone = false;
            } else {
                HttpServletRequest httpReq = reqState.getHttpServletRequest();
                newZoneID = AttributeTools.getRequestString(httpReq,PARM_NEW_ID,"").trim();
                newZoneID = newZoneID.toLowerCase();
                if (StringTools.isBlank(newZoneID)) {
                    m = i18n.getString("ZoneInfo.enterNewZone","Please enter a new Geozone name.");
                    error = true;
                    newZone = false;
                } else
                if (!WebPageAdaptor.isValidID(reqState, PrivateLabel.PROP_ZoneInfo_validateNewIDs, newZoneID)) {
                    m = i18n.getString("ZoneInfo.invalidIDChar","ID contains invalid characters");
                    error = true;
                    newZone = false;
                }
            }
        } else
        if (updateZone) {
            if (!allowEdit) {
                // not authorized to update Geozones
                updateZone = false;
            } else
            if (!SubmitMatch(submitChange,i18n.getString("ZoneInfo.change","Change"))) {
                updateZone = false;
            }
        } else
        if (selectZone) {
            if (SubmitMatch(submitDelete,i18n.getString("ZoneInfo.delete","Delete"))) {
                if (allowDelete) {
                    deleteZone = true;
                }
            } else
            if (SubmitMatch(submitEdit,i18n.getString("ZoneInfo.edit","Edit"))) {
                if (allowEdit) {
                    if (selZone == null) {
                        m = i18n.getString("ZoneInfo.pleaseSelectGeozone","Please select a Geozone");
                        error = true;
                        listZones = true;
                    } else {
                        editZone = true;
                        viewZone = true;
                    }
                }
            } else
            if (SubmitMatch(submitView,i18n.getString("ZoneInfo.view","View"))) {
                if (allowView) {
                    if (selZone == null) {
                        m = i18n.getString("ZoneInfo.pleaseSelectGeozone","Please select a Geozone");
                        error = true;
                        listZones = true;
                    } else {
                        viewZone = true;
                    }
                }
            } else {
                listZones = true;
            }
        } else {
            listZones = true;
        }

        /* delete Geozone? */
        if (deleteZone) {
            if (selZone == null) {
                m = i18n.getString("ZoneInfo.pleaseSelectGeozone","Please select a Geozone");
                error = true;
            } else {
                try {
                    Geozone.Key zoneKey = (Geozone.Key)selZone.getRecordKey();
                    Print.logWarn("Deleting Geozone: " + zoneKey);
                    zoneKey.delete(true); // will also delete dependencies
                    selZoneID = "";
                    selZone = null;
                    zoneList = Geozone.getGeozoneIDsForAccount(currAcct.getAccountID());
                    if ((zoneList != null) && (zoneList.length > 0)) {
                        selZoneID = zoneList[0];
                        try {
                            selZone = !selZoneID.equals("")?Geozone.getGeozone(currAcct,selZoneID,DEFAULT_SORT_ID,false):null;
                        } catch (DBException dbe) {
                            // ignore
                        }
                    }
                } catch (DBException dbe) {
                    Print.logException("Deleting Geozone", dbe);
                    m = i18n.getString("ZoneInfo.errorDelete","Internal error deleting Geozone");
                    error = true;
                }
            }
            listZones = true;
        }

        /* new Geozone? */
        if (newZone) {
            boolean createZoneOK = true;
            Print.logInfo("Creating new Geozone: %s", newZoneID);
            for (int u = 0; u < zoneList.length; u++) {
                if (newZoneID.equalsIgnoreCase(zoneList[u])) {
                    m = i18n.getString("ZoneInfo.alreadyExists","This Geozone already exists");
                    error = true;
                    createZoneOK = false;
                    break;
                }
            }
            if (createZoneOK) {
                try {
                    Geozone zone = Geozone.getGeozone(currAcct, newZoneID, DEFAULT_SORT_ID, true); // create
                    zone.save(); // needs to be saved to be created
                    zoneList = Geozone.getGeozoneIDsForAccount(currAcct.getAccountID());
                    selZone = zone;
                    selZoneID = selZone.getGeozoneID();
                    m = i18n.getString("ZoneInfo.createdZone","New Geozone has been created");
                } catch (DBException dbe) {
                    Print.logException("Creating Geozone", dbe);
                    m = i18n.getString("ZoneInfo.errorCreate","Internal error creating Geozone");
                    error = true;
                }
            }
            listZones = true;
        }

        /* change/update the Geozone info? */
        if (updateZone) {
            int     zonePriority   = StringTools.parseInt(AttributeTools.getRequestString(request,PARM_PRIORITY,null),0);
            boolean zoneRevGeocode = !StringTools.isBlank(AttributeTools.getRequestString(request,PARM_REV_GEOCODE,null));
            boolean zoneArrNotify  = !StringTools.isBlank(AttributeTools.getRequestString(request,PARM_ARRIVE_NOTIFY,null));
            boolean zoneDepNotify  = !StringTools.isBlank(AttributeTools.getRequestString(request,PARM_DEPART_NOTIFY,null));
            boolean zoneClientUpld = !StringTools.isBlank(AttributeTools.getRequestString(request,PARM_CLIENT_UPLOAD,null));
            long    zoneRadius     = StringTools.parseLong(AttributeTools.getRequestString(request,PARM_ZONE_RADIUS,null),100L);
            String  zoneDesc       = AttributeTools.getRequestString(request,PARM_ZONE_DESC,"");
            Print.logInfo("Updating Zone: %s - %s", selZoneID, zoneDesc);
            try {
                 if (selZone != null) {
                     boolean saveOK = true;
                     // Overlap priority
                     if (showOverlapPriority) {
                         selZone.setPriority(zonePriority);
                     }
                     // ReverseGeocode
                     selZone.setReverseGeocode(zoneRevGeocode);
                     // Arrive/Depart notification
                     if (showArriveDepartZone) {
                         selZone.setArrivalZone(zoneArrNotify);
                         selZone.setDepartureZone(zoneDepNotify);
                     }
                     // Client upload zone
                     if (showClientUploadZone) {
                         selZone.setClientUpload(zoneClientUpld);
                     }
                     // Radius (meters)
                     if (zoneRadius > 0L) {
                         selZone.setRadius((int)zoneRadius);
                     }
                     // GeoPoints
                     selZone.clearGeoPoints();
                     for (int z = 0, p = 0; z < mapGeozonePointCount; z++) {
                         double zoneLat = StringTools.parseDouble(AttributeTools.getRequestString(request,PARM_ZONE_LATITUDE [z],null),0.0);
                         double zoneLon = StringTools.parseDouble(AttributeTools.getRequestString(request,PARM_ZONE_LONGITUDE[z],null),0.0);
                         if (GeoPoint.isValid(zoneLat,zoneLon)) {
                             selZone.setGeoPoint(p++, zoneLat, zoneLon);
                         }
                     }
                     // description
                     if (!StringTools.isBlank(zoneDesc)) {
                         selZone.setDescription(zoneDesc);
                     }
                     // save
                     if (saveOK) {
                         selZone.save();
                         m = i18n.getString("ZoneInfo.zoneUpdated","Geozone information updated");
                     } else {
                         // error occurred, should stay on this page
                         editZone = true;
                     }
                 } else {
                     m = i18n.getString("ZoneInfo.noZones","There are currently no defined Geozones for this Account.");
                 }
            } catch (Throwable t) {
                Print.logException("Updating Geozone", t);
                m = i18n.getString("ZoneInfo.errorUpdate","Internal error updating Geozone");
                error = true;
            }
            listZones = true;
        }

        /* final vars */
        final String      _selZoneID   = selZoneID;
        final Geozone     _selZone     = selZone;
        final String      _zoneList[]  = zoneList;
        final boolean     _allowEdit   = allowEdit;
        final boolean     _allowView   = allowView;
        final boolean     _allowNew    = allowNew;
        final boolean     _allowDelete = allowDelete;
        final boolean     _editZone    = _allowEdit && editZone;
        final boolean     _viewZone    = _editZone || viewZone;
        final boolean     _listZones   = listZones || (!_editZone && !_viewZone);

        /* Style */
        HTMLOutput HTML_CSS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                String cssDir = ZoneInfo.this.getCssDirectory();
                WebPageAdaptor.writeCssLink(out, reqState, "ZoneInfo.css", cssDir);
            }
        };

        /* JavaScript */
        HTMLOutput HTML_JS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                MenuBar.writeJavaScript(out, pageName, reqState);
                JavaScriptTools.writeJSInclude(out, JavaScriptTools.qualifyJSFileRef(SORTTABLE_JS));
                if (!_listZones && mapSupportsGeozones) {

                    // MapProvider JavaScript
                    mapProvider.writeJavaScript(out, reqState);

                    /* start JavaScript */
                    JavaScriptTools.writeStartJavaScript(out);

                    // Geozone Javascript
                    double radiusMeters = DEFAULT_ZONE_RADIUS;
                    int zoneType = Geozone.GeozoneType.POINT_RADIUS.getIntValue();
                    if (_selZone != null) {
                        zoneType = _selZone.getZoneType();
                        radiusMeters = _selZone.getRadiusMeters(MIN_RADIUS_METERS,MAX_RADIUS_METERS);
                    }
                    MapDimension mapDim = mapProvider.getZoneDimension(); // new MapDimension(630, 490);
                    out.println("// Geozone vars");
                    out.println("jsvGeozoneMode = true;");
                    out.println("MAP_WIDTH  = " + mapDim.getWidth()  + ";");
                    out.println("MAP_HEIGHT = " + mapDim.getHeight() + ";");

                    JavaScriptTools.writeJSVar(out, "DEFAULT_ZONE_RADIUS", DEFAULT_ZONE_RADIUS);
                    JavaScriptTools.writeJSVar(out, "jsvZoneEditable"    , _editZone);
                    JavaScriptTools.writeJSVar(out, "jsvZoneType"        , zoneType);
                    JavaScriptTools.writeJSVar(out, "jsvZoneRadiusMeters", radiusMeters);

                    int zoneCount = mapGeozonePointCount;
                    out.write("// Geozone points\n");
                    JavaScriptTools.writeJSVar(out, "jsvZoneCount"       , zoneCount);
                    JavaScriptTools.writeJSVar(out, "jsvZoneIndex"       , DEFAULT_POINT_INDEX);
                    out.write("var jsvZoneList = new Array(\n"); // consistent with JSMapPoint
                    for (int z = 0; z < zoneCount; z++) {
                        GeoPoint gp = (_selZone != null)? _selZone.getGeoPoint(z) : null;
                        if (gp == null) { gp = GeoPoint.INVALID_GEOPOINT; }
                        out.write("    { lat:" + gp.getLatitude() + ", lon:" + gp.getLongitude() + " }");
                        if ((z+1) < zoneCount) { out.write(","); }
                        out.write("\n");
                    }
                    out.write("    );\n");
                    
                    /* end JavaScript */
                    JavaScriptTools.writeEndJavaScript(out);
                    
                    /* Geozone.js */
                    JavaScriptTools.writeJSInclude(out, JavaScriptTools.qualifyJSFileRef("Geozone.js"));

                }
            }
        };

        /* Content */
        final boolean mapControlsOnLeft = 
            ListTools.containsIgnoreCase(CONTROLS_ON_LEFT,privLabel.getStringProperty(PrivateLabel.PROP_ZoneInfo_mapControlLocation,""));
        HTMLOutput HTML_CONTENT = new HTMLOutput(CommonServlet.CSS_CONTENT_FRAME, m) {
            public void write(PrintWriter out) throws IOException {
                String pageName = ZoneInfo.this.getPageName();

                // frame header
              //String menuURL    = EncodeMakeURL(reqState,Track.BASE_URI(),PAGE_MENU_TOP);
                String menuURL    = privLabel.getWebPageURL(reqState, PAGE_MENU_TOP);
                String editURL    = ZoneInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                String selectURL  = ZoneInfo.this.encodePageURL(reqState);//,Track.BASE_URI());
                String newURL     = ZoneInfo.this.encodePageURL(reqState);//,Track.BASE_URI());

                if (_listZones) {
                    
                    // Geozone selection table (Select, Geozone ID, Zone Name)
                    String frameTitle = _allowEdit? 
                        i18n.getString("ZoneInfo.list.viewEditZone","View/Edit Geozone Information") : 
                        i18n.getString("ZoneInfo.list.viewZone","View Geozone Information");
                    out.write("<span class='"+CommonServlet.CSS_MENU_TITLE+"'>"+frameTitle+"</span><br/>\n");
                    out.write("<hr>\n");

                    // Geozone selection table (Select, Zone ID, Zone Name)
                    out.write("<h1 class='"+CommonServlet.CSS_ADMIN_SELECT_TITLE+"'>"+FilterText(i18n.getString("ZoneInfo.list.selectZone","Select a Geozone"))+":</h1>\n");
                    out.write("<div style='margin-left:25px;'>\n");
                    out.write("<form name='"+FORM_ZONE_SELECT+"' method='post' action='"+selectURL+"' target='_top'>");
                    out.write("<input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_SELECT+"'/>");
                    out.write("<table class='"+CommonServlet.CSS_ADMIN_SELECT_TABLE+"' cellspacing=0 cellpadding=0 border=0>\n");
                    out.write(" <thead>\n");
                    out.write("  <tr class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_ROW+"'>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL_SEL+"'>"+FilterText(i18n.getString("ZoneInfo.list.select","Select"))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"'>"+FilterText(i18n.getString("ZoneInfo.list.zoneID","Geozone ID"))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"'>"+FilterText(i18n.getString("ZoneInfo.list.description","Description\n(Address)"))+"</th>\n");
                    if (showOverlapPriority) {
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"'>"+FilterText(i18n.getString("ZoneInfo.list.overlapPriority","Overlap\nPriority"))+"</th>\n");
                    }
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"'>"+FilterText(i18n.getString("ZoneInfo.list.revGeocode","Reverse\nGeocode"))+"</th>\n");
                    if (showArriveDepartZone) {
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"'>"+FilterText(i18n.getString("ZoneInfo.list.arriveZone","Arrival\nZone"))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"'>"+FilterText(i18n.getString("ZoneInfo.list.departZone","Departure\nZone"))+"</th>\n");
                    }
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"'>"+FilterText(i18n.getString("ZoneInfo.list.radiusMeters","Radius\n(meters)"))+"</th>\n");
                    out.write("   <th class='"+CommonServlet.CSS_ADMIN_TABLE_HEADER_COL    +"'>"+FilterText(i18n.getString("ZoneInfo.list.centerPoint","Center\nLatitude/Longitude"))+"</th>\n");
                    out.write("  </tr>\n");
                    out.write(" </thead>\n");
                    out.write(" <tbody>\n");
                    int pointRadiusType = Geozone.GeozoneType.POINT_RADIUS.getIntValue();
                    for (int z = 0, r = 0; z < _zoneList.length; z++) {
                        try {
                            Geozone zone = Geozone.getGeozone(currAcct,_zoneList[z],DEFAULT_SORT_ID,false);
                            if ((zone != null) && (zone.getZoneType() == pointRadiusType)) {

                                String zoneID       = FilterText(zone.getGeozoneID());
                                String zoneDesc     = FilterText(zone.getDescription());
                                String zonePriority = FilterText(String.valueOf(zone.getPriority()));
                                String zoneRevGeo   = FilterText(ComboOption.getYesNoText(locale,zone.getReverseGeocode()));
                                String zoneArrNtfy  = FilterText(ComboOption.getYesNoText(locale,zone.getArrivalZone()));
                                String zoneDepNtfy  = FilterText(ComboOption.getYesNoText(locale,zone.getDepartureZone()));
                                String zoneRadius   = String.valueOf(zone.getRadius());
                                GeoPoint centerPt   = zone.getGeoPoint(DEFAULT_POINT_INDEX); // may be null if invalid
                                if (centerPt == null) { centerPt = new GeoPoint(0.0, 0.0); }
                                String zoneCenter   = centerPt.getLatitudeString("5",null) + " "+GeoPoint.PointSeparator+" " + centerPt.getLongitudeString("5",null);
                                String checked      = _selZoneID.equals(zone.getGeozoneID())? "checked" : "";
                                String styleClass = ((r++ & 1) == 0)? CommonServlet.CSS_ADMIN_TABLE_BODY_ROW_ODD : CommonServlet.CSS_ADMIN_TABLE_BODY_ROW_EVEN;

                                out.write("  <tr class='" + styleClass + "'>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL_SEL+"' "+SORTTABLE_SORTKEY+"='"+z+"'><input type='radio' name='"+PARM_ZONE_SELECT+"' id='"+zoneID+"' value='"+zoneID+"' "+checked+"></td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap><label for='"+zoneID+"'>"+zoneID+"</label></td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+zoneDesc+"</td>\n");
                                if (showOverlapPriority) {
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+zonePriority+"</td>\n");
                                }
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+zoneRevGeo+"</td>\n");
                                if (showArriveDepartZone) {
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+zoneArrNtfy+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+zoneDepNtfy+"</td>\n");
                                }
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+zoneRadius+"</td>\n");
                                out.write("   <td class='"+CommonServlet.CSS_ADMIN_TABLE_BODY_COL    +"' nowrap>"+zoneCenter+"</td>\n");
                                out.write("  </tr>\n");

                            }
                        } catch (DBException dbe) {
                            // 
                        }
                    }
                    out.write(" </tbody>\n");
                    out.write("</table>\n");
                    out.write("<table cellpadding='0' cellspacing='0' border='0' style='width:95%; margin-top:5px; margin-left:5px; margin-bottom:5px;'>\n");
                    out.write("<tr>\n");
                    if (_allowView  ) { 
                        out.write("<td style='padding-left:5px;'>");
                        out.write("<input type='submit' name='"+PARM_SUBMIT_VIEW+"' value='"+i18n.getString("ZoneInfo.list.view","View")+"'>");
                        out.write("</td>\n"); 
                    }
                    if (_allowEdit  ) { 
                        out.write("<td style='padding-left:5px;'>");
                        out.write("<input type='submit' name='"+PARM_SUBMIT_EDIT+"' value='"+i18n.getString("ZoneInfo.list.edit","Edit")+"'>");
                        out.write("</td>\n"); 
                    }
                    out.write("<td style='width:100%; text-align:right; padding-right:10px;'>");
                    if (_allowDelete) { 
                        out.write("<input type='submit' name='"+PARM_SUBMIT_DEL+"' value='"+i18n.getString("ZoneInfo.list.delete","Delete")+"' "+Onclick_ConfirmDelete(locale)+">");
                    } else {
                        out.write("&nbsp;"); 
                    }
                    out.write("</td>\n"); 
                    out.write("</tr>\n");
                    out.write("</table>\n");
                    out.write("</form>\n");
                    out.write("</div>\n");
                    out.write("<hr>\n");

                    /* new Geozone */
                    if (_allowNew) {
                    out.write("<h1 class='"+CommonServlet.CSS_ADMIN_SELECT_TITLE+"'>"+FilterText(i18n.getString("ZoneInfo.list.createNewZone","Create a new Geozone"))+":</h1>\n");
                    out.write("<div style='margin-top:5px; margin-left:5px; margin-bottom:5px;'>\n");
                    out.write("<form name='"+FORM_ZONE_NEW+"' method='post' action='"+newURL+"' target='_top'>");
                    out.write(" <input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_NEW+"'/>");
                    out.write(FilterText(i18n.getString("ZoneInfo.list.zoneID","Geozone ID"))+": <input type='text' name='"+PARM_NEW_ID+"' value='' size='32' maxlength='32'><br>\n");
                    out.write(" <input type='submit' name='"+PARM_SUBMIT_NEW+"' value='"+i18n.getString("ZoneInfo.list.new","New")+"' style='margin-top:5px; margin-left:10px;'>\n");
                    out.write("</form>\n");
                    out.write("</div>\n");
                    out.write("<hr>\n");
                    }

                } else {
                    
                    out.println("<form name='"+FORM_ZONE_EDIT+"' method='post' action='"+editURL+"' target='_top'>");
                    
                    // Geozone view/edit form
                    out.write("<table cellspacing='0' cellpadding='0' border='0'><tr>\n");
                    out.write("<td nowrap>");
                    String frameTitle = _editZone? 
                        i18n.getString("ZoneInfo.map.editZone","Edit Geozone") : 
                        i18n.getString("ZoneInfo.map.viewZone","View Geozone");
                    out.print  ("<span style='font-size:9pt; font-weight:bold;'>"+frameTitle+" &nbsp;</span>");
                    out.print  (Form_TextField(PARM_ZONE_SELECT, false, _selZoneID, 16, 20));
                    out.write("</td>");
                    out.write("<td nowrap style=\"width:100%; text-align:right;\">");
                    //out.println("<span style='width:100%;'>&nbsp;</span>");  <-- causes IE to NOT display the following description
                    String i18nAddressTooltip = i18n.getString("ZoneInfo.map.description.tooltip", "This description is used for custom reverse-geocoding");
                    out.print  ("<span class='zoneDescription' style='width:100%;' title=\""+i18nAddressTooltip+"\">");
                    out.print  ("<b>"+i18n.getString("ZoneInfo.map.description","Description (Address)")+"</b>:&nbsp;");
                    out.print  (Form_TextField(PARM_ZONE_DESC, _editZone, (_selZone!=null)?_selZone.getDescription():"", 30, 64));
                    out.println("</span>");
                    out.write("</td>");
                    out.write("</tr></table>");

                    //out.println("<br/>");
                    out.println("<input type='hidden' name='"+PARM_COMMAND+"' value='"+COMMAND_INFO_UPDATE+"'/>");

                    out.println("<table border='0' cellpadding='0' cellspacing='0' style='padding-top:3px'>"); // {
                    out.println("<tr>");

                    /* map (controls on right) */
                    MapDimension mapDim = mapProvider.getZoneDimension(); // new MapDimension(630, 490);
                    if (!mapControlsOnLeft) {
                        if (mapSupportsGeozones) {
                            out.println("<td style='width:"+mapDim.getWidth()+"px; height:"+mapDim.getHeight()+"px; padding-right:5px;'>");
                            out.println("<!-- Begin Map -->");
                            mapProvider.writeMapCell(out, reqState, mapDim);
                            out.println("<!-- End Map -->");
                            out.println("</td>");
                        } else {
                            out.println("<td style='width:"+mapDim.getWidth()+"px; height:"+mapDim.getHeight()+"px; padding-right:5px; border: 1px solid black;'>");
                            out.println("<!-- Geozones not yet supported for this MapProvider -->");
                            out.println("<center>");
                            out.println("<span style='font-size:12pt;'>");
                            out.println(i18n.getString("ZoneInfo.map.notSupported","Geozone map not yet supported for this MapProvider"));
                            out.println("&nbsp;</span>");
                            out.println("</center>");
                            out.println("</td>");
                        }
                    }

                    /* Geozone fields */
                    out.println("<td valign='top' style='border-top: solid #CCCCCC 1px;'>");

                    if (showOverlapPriority) {
                        String i18nPriorityTooltip = i18n.getString("ZoneInfo.map.overlapPriority.tooltip", "Priority used when multiple Geozones overlap");
                        out.println("<div class='zonePrioritySelect' title=\""+i18nPriorityTooltip+"\">");
                        int pri = (_selZone != null)? _selZone.getPriority() : 0;
                        if (pri < 0) {
                            pri = 0;
                        } else
                        if (pri >= OVERLAP_PRIORITY.length) {
                            pri = OVERLAP_PRIORITY.length - 1;
                        }
                        ComboMap priCombo = new ComboMap(OVERLAP_PRIORITY);
                        String priSel = OVERLAP_PRIORITY[pri];
                        out.println("<b><label for='"+PARM_PRIORITY+"'>"+i18n.getString("ZoneInfo.map.overlapPriority","Overlap Priority")+": </label></b>");
                        out.println(Form_ComboBox(PARM_PRIORITY, PARM_PRIORITY, _editZone, priCombo, priSel, null, -1));
                        out.println("</div>");
                    }
                    
                    String i18nRevGeoTooltip = i18n.getString("ZoneInfo.map.reverseGeocode.tooltip", "Select to use this zone for custom reverse-geocoding");
                    out.println("<div class='zoneCheckSelect' title=\""+i18nRevGeoTooltip+"\">");
                    out.println(Form_CheckBox(PARM_REV_GEOCODE, PARM_REV_GEOCODE, _editZone, ((_selZone!=null) && _selZone.getReverseGeocode()),null,null));
                    out.println("<b><label for='"+PARM_REV_GEOCODE+"'>"+i18n.getString("ZoneInfo.map.reverseGeocode","Reverse Geocode")+"</label></b>");
                    out.println("</div>");

                    if (showArriveDepartZone) {
                        String i18nArriveTooltip = i18n.getString("ZoneInfo.map.arrivalZone.tooltip", "Select to use this zone for 'Arrival' checking");
                        out.println("<div class='zoneCheckSelect' title=\""+i18nArriveTooltip+"\">");
                        out.println(Form_CheckBox(PARM_ARRIVE_NOTIFY, PARM_ARRIVE_NOTIFY, _editZone, ((_selZone!=null) && _selZone.getArrivalZone()),null,null));
                        out.println("<b><label for='"+PARM_ARRIVE_NOTIFY+"'>"+i18n.getString("ZoneInfo.map.arrivalZone","Arrival Zone")+"</label></b>");
                        out.println("</div>");
                    }

                    if (showArriveDepartZone) {
                        String i18nDepartTooltip = i18n.getString("ZoneInfo.map.departureZone.tooltip", "Select to use this zone for 'Departure' checking");
                        out.println("<div class='zoneCheckSelect' title=\""+i18nDepartTooltip+"\">");
                        out.println(Form_CheckBox(PARM_DEPART_NOTIFY, PARM_DEPART_NOTIFY, _editZone, ((_selZone!=null) && _selZone.getDepartureZone()),null,null));
                        out.println("<b><label for='"+PARM_DEPART_NOTIFY+"'>"+i18n.getString("ZoneInfo.map.departureZone","Departure Zone")+"</label></b>");
                        out.println("</div>");
                    }

                    if (showClientUploadZone) {
                        String i18nUploadTooltip = i18n.getString("ZoneInfo.map.clientUpload.tooltip", "Select to use for client-side geofence");
                        out.println("<div class='zoneCheckSelect' title=\""+i18nUploadTooltip+"\">");
                        out.println(Form_CheckBox(PARM_CLIENT_UPLOAD, PARM_CLIENT_UPLOAD, _editZone, ((_selZone!=null) && _selZone.getClientUpload()),null,null));
                        out.println("<b><label for='"+PARM_CLIENT_UPLOAD+"'>"+i18n.getString("ZoneInfo.map.clientUpload","Client Upload")+":</label></b>&nbsp;");
                        out.println(Form_TextField(PARM_CLIENT_ID, PARM_CLIENT_ID, _editZone, (_selZone!=null)?String.valueOf(_selZone.getClientID()):"", 4, 4));
                        out.println("</div>");
                    }
                    
                    out.println("<hr>");
                    if (_editZone && mapSupportsGeozones) {
                    out.println("<div class='zoneNotesBasic'>");
                    out.println("<i>"+i18n.getString("ZoneInfo.map.notes.basic", "The Geozone loc/size may be changed here, click 'RESET' to update.")+"</i>");
                    out.println("</div>");
                    }

                    String i18nRadiusTooltip = i18n.getString("ZoneInfo.map.radius.tooltip", "Radius may be between {0} and {1} meters", 
                        String.valueOf((long)MIN_RADIUS_METERS), String.valueOf((long)MAX_RADIUS_METERS));
                    out.println("<div class='zoneRadius' title=\""+i18nRadiusTooltip+"\">");
                    out.print  ("<b>"+i18n.getString("ZoneInfo.map.radiusMeters","Radius (meters)")+":</b>&nbsp;");
                    out.println(Form_TextField(MapProvider.ID_ZONE_RADIUS_M, PARM_ZONE_RADIUS, _editZone, (_selZone!=null)?String.valueOf(_selZone.getRadius()):"", 7, 7));
                    out.println("</div>");

                    out.println("<div class='zoneLatLon'>");
                    out.println("<b>"+i18n.getString("ZoneInfo.map.latLon","Lat/Lon")+"</b>:&nbsp;&nbsp;");
                    if (_editZone && mapSupportsGeozones) {
                        String i18nResetBtn = i18n.getString("ZoneInfo.map.reset","Reset Map");
                        String i18nResetTooltip = i18n.getString("ZoneInfo.map.reset.tooltip", "Click to update the map with the specified radius/latitude/longitude");
                        out.print("<input class='formButton' type='button' name='reset' value='"+i18nResetBtn+"' title=\""+i18nResetTooltip+"\" onclick=\"javascript:_zoneReset();\">");
                    }
                    out.println("<br>");
                    //if (mapGeozonePointCount > 1) {
                    //    int z = -1;
                    //    String chk = " checked";
                    //    out.print("<input type='radio' id='"+PARM_ZONE_INDEX+"' name='"+PARM_ZONE_INDEX+"' value='" + z + "' "+chk+" onchange=\"javascript:_zonePointSelectionChanged("+z+")\"/>&nbsp;All");
                    //    out.println("<br>\n");
                    //}
                    int pointCount = mapGeozonePointCount;
                    for (int z = 0; z < pointCount; z++) {
                        String latStr = (_selZone != null)? String.valueOf(_selZone.getLatitude(z) ) : "";
                        String lonStr = (_selZone != null)? String.valueOf(_selZone.getLongitude(z)) : "";
                        // id='"+PARM_ZONE_INDEX+"'
                        if (mapGeozonePointCount > 1) {
                            String chk = (z == 0)? " checked" : "";
                            out.println("<input type='radio'  name='"+PARM_ZONE_INDEX+"' value='" + z + "' "+chk+" onchange=\"javascript:_zonePointSelectionChanged("+z+")\"/>&nbsp;");
                        } else {
                            out.println("<input type='hidden' name='"+PARM_ZONE_INDEX+"' value='" + z + "'/>");
                        }
                        out.println(Form_TextField(MapProvider.ID_ZONE_LATITUDE [z], PARM_ZONE_LATITUDE [z], _editZone, latStr,  9,  9));
                        out.println(Form_TextField(MapProvider.ID_ZONE_LONGITUDE[z], PARM_ZONE_LONGITUDE[z], _editZone, lonStr, 10, 10));
                        if ((z+1) < pointCount) { out.println("<br>"); }
                    }
                    if (_editZone && mapSupportsGeozones) {
                        // "ZipCode" button
                        if (privLabel.getBooleanProperty(PrivateLabel.PROP_ZoneInfo_enableGeocode,false)) {
                            GeocodeProvider gcp = privLabel.getGeocodeProvider();
                            String i18nZipBtn = "";
                            if ((gcp == null) || gcp.getName().startsWith("geonames")) {
                                i18nZipBtn = i18n.getString("ZoneInfo.map.geocodeZip","Center On City/ZipCode");
                            } else {
                                i18nZipBtn = i18n.getString("ZoneInfo.map.geocodeAddress","Center On Address", gcp.getName());
                            }
                            String i18nZipTooltip = i18n.getString("ZoneInfo.map.geocode.tooltip", "Click to reset Geozone to spcified Address/ZipCode");
                            String rgZipCode_text = "rgZipCode";
                            out.print("<hr>\n");
                          //out.print("<br>");
                            out.print("<input class='formButton' type='button' name='tozip' value='"+i18nZipBtn+"' title=\""+i18nZipTooltip+"\" onclick=\"javascript:_zoneGotoAddr(jsmGetIDValue('"+rgZipCode_text+"'),'US');\">");
                            out.print("<br>");
                            out.println(Form_TextField(rgZipCode_text, rgZipCode_text, _editZone, "",  27, 60));
                        }
                    }
                    out.println("</div>");

                    out.println("<hr>");
                    out.println("<div class='zoneInstructions'>");
                    out.println("<b>"+i18n.getString("ZoneInfo.map.notes.header","Geozone Notes/Instructions")+":</b><br>");
                    if (_editZone && mapSupportsGeozones) {
                        String instr[] = mapProvider.getGeozoneInstructions(locale);
                        if ((instr != null) && (instr.length > 0)) {
                            for (int i = 0; i < instr.length; i++) {
                                if (!StringTools.isBlank(instr[i])) { 
                                    out.println("- " + FilterText(instr[i]) + "<br>"); 
                                }
                            }
                        }
                    }
                    out.println("- " + i18n.getString("ZoneInfo.map.notes.lengthInMeters", "Distances are always in meters.") + "<br>");

                    out.println("<hr>");
                    if (mapSupportsCursorLocation || mapSupportsDistanceRuler) {
                        if (mapSupportsCursorLocation) {
                            out.println("<b>"+i18n.getString("ZoneInfo.map.cursorLoc","Cursor")+"</b>:");
                            out.println("<span id='"+MapProvider.ID_LAT_LON_DISPLAY +"' style='margin-left:6px; margin-bottom:3px;'>0.00000, 0.00000</span>");
                        }
                        if (mapSupportsDistanceRuler) {
                            out.println("<b>"+i18n.getString("ZoneInfo.map.distanceRuler","Distance")+"</b>:");
                            out.println("<span id='"+MapProvider.ID_DISTANCE_DISPLAY+"' style='margin-left:6px;'>0 "+GeoPoint.DistanceUnits.METERS.toString(locale)+"</span>");
                        }
                        out.println("<hr>");
                    }

                    out.println("</div>");

                    out.write("<div width='100%'>\n");
                    out.write("<span style='padding-left:10px'>&nbsp;</span>\n");
                    if (_editZone) {
                        out.write("<input type='submit' name='"+PARM_SUBMIT_CHG+"' value='"+i18n.getString("ZoneInfo.map.change","Change")+"'>\n");
                        out.write("<span style='padding-left:10px'>&nbsp;</span>\n");
                        out.write("<input type='button' name='"+PARM_BUTTON_CANCEL+"' value='"+i18n.getString("ZoneInfo.map.cancel","Cancel")+"' onclick=\"javascript:openURL('"+editURL+"','_top');\">\n");
                    } else {
                        out.write("<input type='button' name='"+PARM_BUTTON_BACK+"' value='"+i18n.getString("ZoneInfo.map.back","Back")+"' onclick=\"javascript:openURL('"+editURL+"','_top');\">\n");
                    }
                    out.write("</div>\n");

                    out.println("<div width='100%' height='100%'>");
                    out.println("&nbsp;");
                    out.println("</div>");

                    out.println("</td>");

                    /* map (controls on left) */
                    if (mapControlsOnLeft) {
                        if (mapSupportsGeozones) {
                            out.println("<td style='width:"+mapDim.getWidth()+"px; height:"+mapDim.getHeight()+"px; padding-left:5px;'>");
                            out.println("<!-- Begin Map -->");
                            mapProvider.writeMapCell(out, reqState, mapDim);
                            out.println("<!-- End Map -->");
                            out.println("</td>");
                        } else {
                            out.println("<td style='width:"+mapDim.getWidth()+"px; height:"+mapDim.getHeight()+"px; padding-left:5px; border: 1px solid black;'>");
                            out.println("<!-- Geozones not yet supported for this MapProvider -->");
                            out.println("<center>");
                            out.println("<span style='font-size:12pt;'>");
                            out.println(i18n.getString("ZoneInfo.map.notSupported","Geozone map not yet supported for this MapProvider"));
                            out.println("&nbsp;</span>");
                            out.println("</center>");
                            out.println("</td>");
                        }
                    }

                    /* end of form */
                    out.println("</tr>");
                    out.println("</table>"); // }
                    out.println("</form>");

                }

            }
        };
        
        /* map load? */
        String mapOnLoad   = _listZones? "" : "javascript:_zoneMapOnLoad();";
        String mapOnUnload = _listZones? "" : "javascript:_zoneMapOnUnload();";

        /* write frame */
        String onload = error? (mapOnLoad + JS_alert(false,m)) : mapOnLoad;
        CommonServlet.writePageFrame(
            reqState,
            onload,mapOnUnload,         // onLoad/onUnload
            HTML_CSS,                   // Style sheets
            HTML_JS,                    // Javascript
            null,                       // Navigation
            HTML_CONTENT);              // Content

    }
    
    // ------------------------------------------------------------------------
}
