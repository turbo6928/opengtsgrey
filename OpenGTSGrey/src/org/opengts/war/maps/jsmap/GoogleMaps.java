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
// Reverse-Geocoding possibilities:
//  - http://blog.programmableweb.com/2008/10/24/google-maps-api-gets-reverse-geocoding/
//  - http://gmaps-samples.googlecode.com/svn/trunk/geocoder/reverse.html
//  - http://groups.google.com/group/Google-Maps-API/web/resources-non-google-geocoders
//  - http://www.freereversegeo.com/
//  - http://mapperz.blogspot.com/2007/08/exclusive-reverse-geocoding-using.html
//  - http://nicogoeminne.googlepages.com/reversegeocode.html
// Dual Maps:
//  - http://www.mapchannels.com/dualmaps.aspx
// Register for Google Map keys:
//  - http://www.google.com/apis/maps/signup.html
// Usage examples:
//  - http://mapstraction.com/demo-filters.php
//  - http://econym.org.uk/gmap/
// Scale/Zoom/Meters-per-pixel
//  - http://slappy.cs.uiuc.edu/fall06/cs492/Group2/example.html
// Misc (many useful examples)
//  - http://www.bdcc.co.uk/Gmaps/BdccGmapBits.htm
//  - http://groups.google.com/group/Google-Maps-API/web/examples-tutorials-gpolygon-gpolyline
//  - http://code.nosvamosdetapas.com/googlemaps/test5.html
//  - http://maps.huge.info/examples.htm
//      - http://maps.huge.info/dragcircle2.htm
//      - http://maps.huge.info/dragpoly.htm
//  - http://wolfpil.googlepages.com/polygon.html 
//  - http://maps.forum.nu/gm_plot.html
//  - http://wolfpil.googlepages.com/switch-polies.html
// Google Pushpins:
//  - http://labs.google.com/ridefinder/images/mm_20_${color}.png
//  - http://labs.google.com/ridefinder/images/mm_20_shadow.png
//  - http://gmaps-utility-library.googlecode.com/svn/trunk/mapiconmaker/1.1/docs/examples.html
//  - http://www.powerhut.co.uk/googlemaps/custom_markers.php
//  - http://groups.google.com/group/google-maps-api/web/examples-tutorials-custom-icons-for-markers
//  - http://thydzik.com/dynamic-google-maps-markersicons-with-php/
// ----------------------------------------------------------------------------
// Change History:
//  2008/07/08  Martin D. Flynn
//     -Initial release
//  2008/08/08  Martin D. Flynn
//     -Added Geozone support
//  2009/08/07  Martin D. Flynn
//     -Added "google.mapcontrol" and "google.sensor" properties.
//  2009/12/16
//     -Added support for client-id (ie. "&client=gme-...")
// ----------------------------------------------------------------------------
package org.opengts.war.maps.jsmap;

import java.util.*;
import java.io.*;

import org.opengts.util.*;

import org.opengts.db.tables.Geozone;
import org.opengts.war.tools.*;
import org.opengts.war.maps.JSMap;

public class GoogleMaps
    extends JSMap
{

    // ------------------------------------------------------------------------

    private static final String PROP_mapcontrol     = "google.mapcontrol";
    private static final String PROP_sensor         = "google.sensor";
    
    private static final String PremierPrefix_      = "gme-";

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* GoogleMaps instance */ 
    public GoogleMaps(String name, String key) 
    {
        super(name, key);
        this.addSupportedFeature(FEATURE_LATLON_DISPLAY);
        this.addSupportedFeature(FEATURE_GEOZONES);
        this.addSupportedFeature(FEATURE_DETAIL_REPORT);
        this.addSupportedFeature(FEATURE_DETAIL_INFO_BOX);
        this.addSupportedFeature(FEATURE_REPLAY_POINTS);
        this.addSupportedFeature(FEATURE_CENTER_ON_LAST);
    }

    // ------------------------------------------------------------------------

    /* validate */
    public boolean validate()
    {
        String key = this.getAuthorization();
        if ((key == null) || key.startsWith("*")) {
            Print.logError("Invalid Google Map key specified");
            return false;
        } else
        if (key.startsWith(PremierPrefix_)) {
            return true;
        } else
        if (key.length() < 30) {
            Print.logError("Invalid Google Map key specified");
            return false;
        } else {
            return true;
        }
    }

    // ------------------------------------------------------------------------

    /* write mapping support JS to stream */ 
    protected void writeJSVariables(PrintWriter out, RequestProperties reqState) 
        throws IOException
    {
        super.writeJSVariables(out, reqState);
    }

    // ------------------------------------------------------------------------

    protected void writeJSIncludes(PrintWriter out, RequestProperties reqState)
        throws IOException 
    {

        /* map provider properties */
        MapProvider  mp   = reqState.getMapProvider();
        RTProperties mrtp = (mp != null)? mp.getProperties() : null;

        /* authorization key */
        String mapCtlURL = (mrtp != null)? mrtp.getString(PROP_mapcontrol,null) : null;
        if (StringTools.isBlank(mapCtlURL)) {
            StringBuffer sb = new StringBuffer();
            sb.append("http://maps.google.com/maps?file=api&v=2");
            // "key="
            String key = this.getAuthorization();
            if (StringTools.isBlank(key) || key.startsWith("*")) {
                Print.logError("Invalid Google Map key specified");
            } else
            if (key.startsWith(PremierPrefix_)) {
                sb.append("&client=").append(key);
            } else
            if (key.length() < 30) {
                Print.logError("Invalid Google Map key specified");
            } else {
                sb.append("&key=").append(key);
            }
            // "&sensor="
            String sensorVal = (mrtp != null)? mrtp.getString(PROP_sensor,"true") : "true";
            if (!StringTools.isBlank(sensorVal)) {
                sb.append("&sensor=").append(sensorVal);
            }
            // "&oe=" character encoding
            sb.append("&oe=").append("utf-8");
            // "&hl=" localization
            String localStr = reqState.getPrivateLabel().getLocaleString();
            if (!StringTools.isBlank(localStr)) {
                sb.append("&hl=").append(localStr);
            }
            // URL
            mapCtlURL = sb.toString();
        }

        /* display Javascript */
        super.writeJSIncludes(out, reqState, new String[] {
            JavaScriptTools.qualifyJSFileRef("maps/jsmap.js"),
            mapCtlURL,
            JavaScriptTools.qualifyJSFileRef("maps/GoogleMaps.js")
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
        I18N i18n = I18N.getI18N(GoogleMaps.class, loc);
        return new String[] { 
            i18n.getString("GoogleMaps.geozoneNotes.1", "Click to reset center."),
            i18n.getString("GoogleMaps.geozoneNotes.2", "Click-drag center to move."),
            i18n.getString("GoogleMaps.geozoneNotes.3", "Click-drag radius to resize."),
        };
    }

    // ------------------------------------------------------------------------

}
