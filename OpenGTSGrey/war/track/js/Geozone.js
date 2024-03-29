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
//  2009/09/23  Martin D. Flynn
//     -Moved from ZoneInfo.java
//  2009/12/16  Martin D. Flynn
//     -Added support for a generic "<geocode>" address Geocode provider
// ----------------------------------------------------------------------------

var PAGE_ZONEGEOCODE = "ZONEGEOCODE";

/* parse geozones onLoad */
function _zoneMapOnLoad() {
    mapProviderParseZones(jsvZoneList);
};

/* unload */
function _zoneMapOnUnload() {
    mapProviderUnload();
};

// ----------------------------------------------------------------------------

/* "Reset" was clicked */
function _zoneReset() 
{
    jsvZoneRadiusMeters = zoneMapGetRadius(true);
    for (var z = 0; z < jsvZoneCount; z++) {
        if (document.getElementById(ID_ZONE_LATITUDE_ + z)) {
           var lat = numParseFloat(jsmGetIDValue(ID_ZONE_LATITUDE_  + z), 0.0);
           var lon = numParseFloat(jsmGetIDValue(ID_ZONE_LONGITUDE_ + z), 0.0);
           jsvZoneList[z].lat = lat;
           jsvZoneList[z].lon = lon;
        }
    }
    //jsvZoneIndex = 0;
    mapProviderParseZones(jsvZoneList);
};

/* radio button selection changed */
function _zonePointSelectionChanged(ndx) 
{
    jsvZoneIndex = ndx;
    mapProviderParseZones(jsvZoneList);
};

/* return the current point-radius index */
function zoneMapSetIndex(ndx)
{
    if (ndx != jsvZoneIndex) {
        var radioObj = document.ZoneInfoEdit.z_index; //  document.getElementByName("z_index");
        if (setCheckedRadioValue(radioObj, ndx)) {
            jsvZoneIndex = ndx;
            mapProviderParseZones(jsvZoneList);
        }
    }
};

/* return the current point-radius index */
function zoneMapGetIndex()
{
    return jsvZoneIndex;
};

/* get the zone radius */
function zoneMapGetRadius(updateFromID)
{
    
    if (updateFromID) { 
        jsvZoneRadiusMeters = jsmGetIDValue(ID_ZONE_RADIUS_M); 
    }
    
    if (jsvZoneRadiusMeters <= 0.0              ) { jsvZoneRadiusMeters = DEFAULT_ZONE_RADIUS; }
    if (jsvZoneRadiusMeters >  MAX_ZONE_RADIUS_M) { jsvZoneRadiusMeters = MAX_ZONE_RADIUS_M;   }
    if (jsvZoneRadiusMeters <  MIN_ZONE_RADIUS_M) { jsvZoneRadiusMeters = MIN_ZONE_RADIUS_M;   }
    
    return jsvZoneRadiusMeters;
};

// ----------------------------------------------------------------------------
// get lat/lon from GeoNames postalCode geocoder

var TAG_geocode         = "geocode";
var TAG_geonames        = "geonames";
var TAG_code            = "code";
var TAG_geoname         = "geoname";
var TAG_lat             = "lat";
var TAG_lng             = "lng";
var TAG_lon             = "lon";

/* get the lat/lon for the specified zip code, and recenter map */
// The browser may not allow requests to a non-originating server
function _zoneGotoAddr(addr, ct) 
{

    /* get the latitude/longitude for the zip */
    //var url = "http://ws.geonames.org/postalCodeSearch?postalcode="+zip+"&country="+ct+"&style=long&maxRows=5";
    var url = "./Track?page=" + PAGE_ZONEGEOCODE + "&addr=" + addr + "&country=" + ct + "&_uniq=" + Math.random();
    //alert("URL " + url);
    try {
        var req = jsmGetXMLHttpRequest();
        if (req) {
            req.open("GET", url, true);
            //req.setRequestHeader("CACHE-CONTROL", "NO-CACHE");
            //req.setRequestHeader("PRAGMA", "NO-CACHE");
            //req.setRequestHeader("If-Modified-Since", "Sat, 1 Jan 2000 00:00:00 GMT");
            req.onreadystatechange = function() {
                if (req.readyState == 4) {
                    var lat = 0.0;
                    var lon = 0.0;
                    for (;;) {
                        
                        /* get xml */
                        var xmlStr = req.responseText;
                        if (!xmlStr || (xmlStr == "")) {
                            break;
                        }
                        
                        /* get XML doc */
                        var xmlDoc = createXMLDocument(xmlStr);
                        if (xmlDoc == null) {
                            break;
                        }

                        /* try parsing as "geocode" encasulated XML */
                        var geocode = xmlDoc.getElementsByTagName(TAG_geocode);
                        if ((geocode != null) && (geocode.length > 0)) {
                            //alert("geocode: " + xmlStr);
                            var geocodeElem = geocode[0];
                            if (geocodeElem != null) {
                                var latn = geocodeElem.getElementsByTagName(TAG_lat);
                                var lonn = geocodeElem.getElementsByTagName(TAG_lng);
                                if (!lonn || (lonn.length == 0)) { lonn = geocodeElem.getElementsByTagName(TAG_lon); }
                                if ((latn.length > 0) && (lonn.length > 0)) {
                                    lat = numParseFloat(latn[0].childNodes[0].nodeValue,0.0);
                                    lon = numParseFloat(lonn[0].childNodes[0].nodeValue,0.0);
                                    break;
                                }
                            }
                            break;
                        }

                        /* try parsing as forwarded XML from Geonames */
                        var geonames = xmlDoc.getElementsByTagName(TAG_geonames);
                        if ((geonames != null) && (geonames.length > 0)) {
                            //alert("geonames: " + xmlStr);
                            // returned XML was forwarded as-is from Geonames
                            var geonamesElem = geonames[0];
                            var codeList = null;
                            if (geonamesElem != null) {
                                codeList = geonamesElem.getElementsByTagName(TAG_code);
                                if (!codeList || (codeList.length == 0)) {
                                    codeList = geonamesElem.getElementsByTagName(TAG_geoname);
                                }
                            }
                            if (codeList != null) {
                                for (var i = 0; i < codeList.length; i++) {
                                    var code = codeList[i];
                                    var latn = code.getElementsByTagName(TAG_lat);
                                    var lonn = code.getElementsByTagName(TAG_lng);
                                    if ((latn.length > 0) && (lonn.length > 0)) {
                                        lat = numParseFloat(latn[0].childNodes[0].nodeValue,0.0);
                                        lon = numParseFloat(lonn[0].childNodes[0].nodeValue,0.0);
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                        
                        /* break */
                        //alert("unknown: " + xmlStr);
                        break;
                        
                    }
                    
                    /* set lat/lon */
                    if ((lat != 0.0) || (lon != 0.0)) {
                        var radiusM = MAX_ZONE_RADIUS_M / 10;
                        jsvZoneIndex = 0;
                        _jsmSetPointZoneValue(0, lat, lon, radiusM);
                        for (var z = 1; z < jsvZoneCount; z++) {
                            _jsmSetPointZoneValue(z, 0.0, 0.0, radiusM);
                        }
                        _zoneReset();
                    }
                    
                } else
                if (req.readyState == 1) {
                    // alert('Loading GeoNames from URL: [' + req.readyState + ']\n' + url);
                } else {
                    // alert('Problem loading URL? [' + req.readyState + ']\n' + url);
                }
            }
            req.send(null);
        } else {
            alert("Error [_zoneCenterOnZip]:\n" + url);
        }
    } catch (e) {
        alert("Error [_zoneCenterOnZip]:\n" + e);
    }

};
