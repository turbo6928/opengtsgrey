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
// References:
//  - http://wiki.openstreetmap.org/index.php/OpenLayers_Simple_Example
//  - http://wiki.openstreetmap.org/index.php/Kosmos
//  - http://www.openlayers.org/
//  - http://www.openlayers.org/dev/examples/
//  - http://cfis.savagexi.com/articles/2007/09/29/can-open-source-dethrone-google-maps
//  - http://www.developpez.net/forums/showthread.php?t=533436
// Polygons:
//  - http://dev.openlayers.org/sandbox/tschaub/feature/examples/regular-polygons.html
// Dragging a feature:
//  - http://dev.openlayers.org/sandbox/tschaub/feature/examples/drag-marker.html
//  - http://dev.openlayers.org/sandbox/tschaub/feature/examples/drag-feature.html
// Resizing a feature:
//  - http://dev.openlayers.org/sandbox/tschaub/feature/examples/resize-features.html
// Using with Virtual Earth:
//  - http://www.mp2kmag.com/a147--open.layers.mappoint.html
// Zooming
//  - http://www.cartogrammar.com/blog/map-panning-and-zooming-methods/
// ----------------------------------------------------------------------------
// Change History:
//  2008/08/08  Martin D. Flynn
//     -Initial release
//     -Includes Geozone support
//  2008/10/16  Martin D. Flynn
//     -Initial support for GeoServer
// ----------------------------------------------------------------------------
package org.opengts.war.maps.jsmap;

import java.util.*;
import java.io.*;

import org.opengts.util.*;

import org.opengts.db.tables.Geozone;
import org.opengts.war.tools.*;
import org.opengts.war.maps.JSMap;

public class OpenLayers
    extends JSMap
{

    // ------------------------------------------------------------------------

    public  static final String PROP_GEOSERVER_enable[]         = new String[] { "geoServer.enable" };
    public  static final String PROP_GEOSERVER_url[]            = new String[] { "geoServer.url" };
    public  static final String PROP_GEOSERVER_maxResolution[]  = new String[] { "geoServer.maxResolution" };
    public  static final String PROP_GEOSERVER_size[]           = new String[] { "geoServer.size" };
    public  static final String PROP_GEOSERVER_projection[]     = new String[] { "geoServer.projection" };
    public  static final String PROP_GEOSERVER_layers[]         = new String[] { "geoServer.layers" };
    public  static final String PROP_GEOSERVER_bounds[]         = new String[] { "geoServer.bounds" };

    // ------------------------------------------------------------------------
    
    private static final int   DEFAULT_ZOOM             = 4;

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* OpenLayers instance */ 
    public OpenLayers(String name, String key) 
    {
        super(name, key); 
        this.addSupportedFeature(FEATURE_LATLON_DISPLAY);
        this.addSupportedFeature(FEATURE_DISTANCE_RULER);
        this.addSupportedFeature(FEATURE_GEOZONES);
        this.addSupportedFeature(FEATURE_DETAIL_REPORT);
        this.addSupportedFeature(FEATURE_DETAIL_INFO_BOX);
        this.addSupportedFeature(FEATURE_REPLAY_POINTS);
        this.addSupportedFeature(FEATURE_CENTER_ON_LAST);
    }

    // ------------------------------------------------------------------------

    /* write mapping support JS to stream */ 
    protected void writeJSVariables(PrintWriter out, RequestProperties reqState) 
        throws IOException
    {
        super.writeJSVariables(out, reqState);
        out.write("// OpenLayers custom vars\n");
        RTProperties rtp = this.getProperties();
        boolean geoServer = rtp.getBoolean(PROP_GEOSERVER_enable, false); 
        JavaScriptTools.writeJSVar(out, "GEOSERVER_enable", geoServer);
        if (geoServer) {
            JavaScriptTools.writeJSVar(out, "GEOSERVER_url"          , rtp.getString(PROP_GEOSERVER_url, ""));
            JavaScriptTools.writeJSVar(out, "GEOSERVER_maxResolution", rtp.getDouble(PROP_GEOSERVER_maxResolution, 0.0));
            JavaScriptTools.writeJSVar(out, "GEOSERVER_size"         , rtp.getString(PROP_GEOSERVER_size, "{ width:0, height:0 }"),false);
            JavaScriptTools.writeJSVar(out, "GEOSERVER_projection"   , rtp.getString(PROP_GEOSERVER_projection, ""));
            JavaScriptTools.writeJSVar(out, "GEOSERVER_layers"       , rtp.getString(PROP_GEOSERVER_layers, ""));
            JavaScriptTools.writeJSVar(out, "GEOSERVER_bounds"       , rtp.getString(PROP_GEOSERVER_bounds, "{ top:0, left:0, bottom:0, right:0 }"),false);
        }
    }

    // ------------------------------------------------------------------------

    protected void writeJSIncludes(PrintWriter out, RequestProperties reqState)
        throws IOException 
    {
        super.writeJSIncludes(out, reqState, new String[] {
            JavaScriptTools.qualifyJSFileRef("maps/jsmap.js"),
            "http://openlayers.org/api/OpenLayers.js",
            JavaScriptTools.qualifyJSFileRef("maps/OpenLayers.js")
        });
    }

    // ------------------------------------------------------------------------

    /**
    *** Returns the number of supported points for the specified Geozone type
    *** @param type  The Geozone type
    *** @return The number of supported points for the specified Geozone type
    **/
    public int getGeozoneSupportedPointCount(int type)
    {
        RTProperties rtp = this.getProperties();
        if (type == Geozone.GeozoneType.POINT_RADIUS.getIntValue()) {
            return rtp.getBoolean(PROP_zone_map_multipoint,false)? Geozone.GetGeoPointCount() : 1;
        } else {
            return 0;
        }
    }

    public String[] getGeozoneInstructions(Locale loc)
    {
        I18N i18n = I18N.getI18N(OpenLayers.class, loc);
        return new String[] {
            i18n.getString("OpenLayers.geozoneNotes.1", "Click to reset center."),
            i18n.getString("OpenLayers.geozoneNotes.2", "Click-drag Geozone to move."),
            i18n.getString("OpenLayers.geozoneNotes.3", "Shift-click-drag to resize."),
            i18n.getString("OpenLayers.geozoneNotes.4", "Ctrl-click-drag for distance.")
        };
    }

    // ------------------------------------------------------------------------

}
