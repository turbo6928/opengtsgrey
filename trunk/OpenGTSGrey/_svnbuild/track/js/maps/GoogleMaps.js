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
// Required funtions defined by this module:
//   new JSMap(String mapID)
//   JSClearLayers()
//   JSSetCenter(JSMapPoint center [, int zoom])
//   JSDrawPushpins(JSMapPushpin pushPin[], int recenterMode, int replay)
//   JSDrawPOI(JSMapPushpin pushPin[])
//   JSDrawRoute(JSMapPoint points[], String color)
//   JSDrawShape(String type, double radius, JSMapPoint points[], color, zoomTo)
//   JSDrawGeozone(int type, double radius, JSMapPoint points[], int primaryIndex)
//   JSShowPushpin(JSMapPushpin pushPin, boolean center)
//   JSPauseReplay(int replay)
//   JSUnload() 
// ----------------------------------------------------------------------------
// Change History:
//  2008/07/08  Martin D. Flynn
//     -Initial release
//  2008/08/08  Martin D. Flynn
//     -Added support for Geozones
//  2008/09/01  Martin D. Flynn
//     -Added replay and geozone recenter support
//  2009/08/23  Martin D. Flynn
//     -Added color argument to JSDrawRoute
//     -Added option for drawing multiple points per device on fleet map
//  2009/09/23  Martin D. Flynn
//     -Added support for displaying multipoint geozones (single point at a time)
//  2009/11/01  Juan Carlos Argueta
//     -Added route-arrows
// ----------------------------------------------------------------------------

var googleMap2              = null;

var DRAG_NONE               = 0;
var DRAG_RULER              = 1;
var DRAG_GEOZONE_CENTER     = 2;
var DRAG_GEOZONE_RADIUS     = 3;

var USE_DEFAULT_CONTROLS    = true;

// [Juan Carlos Argueta] Los basico de la información de la flecha (Info of the arrow icon)
var arrowIcon = new GIcon();
    arrowIcon.iconSize = new GSize(14,14);
    arrowIcon.shadowSize = new GSize(1,1);
    arrowIcon.iconAnchor = new GPoint(7,7); // Mitad de iconSize
    arrowIcon.infoWindowAnchor = new GPoint(0,0);
var degreesPerRadian = 180.0 / Math.PI;
// ----- End Info of the arrow icon

/**
*** JSMap constructor
**/
function JSMap(element)
{
    //if (navigator.platform.match(/linux|bsd/i)) { _mSvgEnabled = _mSvgForced = true; }

    /* map */
    var mapStyle = { 
        draggableCursor: "auto", 
        draggingCursor: "move" 
    };
    googleMap2 = new GMap2(element, mapStyle);
    this.gmapGoogleMap = googleMap2;
    if (USE_DEFAULT_CONTROLS) {
        this.gmapGoogleMap.setUIToDefault();
        //var customUI = this.gmapGoogleMap.getDefaultUI();
        ////customUI.controls.scalecontrol = false;
        //this.gmapGoogleMap.setUI(customUI);
    } else {
        //this.gmapGoogleMap.addMapType(G_PHYSICAL_MAP);
        //this.gmapGoogleMap.addMapType(G_SATELLITE_3D_MAP); // provided by "Harold Julian M"
        //var hierarchy = new GHierarchicalMapTypeControl();
        //hierarchy.addRelationship(G_SATELLITE_MAP, G_HYBRID_MAP, "Labels", true);
        //this.gmapGoogleMap.addControl(hierarchy);
        this.gmapGoogleMap.addControl(new GMapTypeControl(1));
        this.gmapGoogleMap.addControl(new GSmallMapControl());
    }
    
    /* scroll wheel zoom */
    this.gmapGoogleMap.disableDoubleClickZoom();
    if (SCROLL_WHEEL_ZOOM) { 
        this.gmapGoogleMap.enableScrollWheelZoom(); 
    }
    
    element.style.cursor = "crosshair"; // may not be effective
    var self = this;
    
    /* misc vars */
    this.visiblePopupInfoBox = null;

    /* replay vars */
    this.replayTimer = null;
    this.replayIndex = 0;
    this.replayInterval = (REPLAY_INTERVAL < 100)? 100 : REPLAY_INTERVAL;
    this.replayInProgress = false;
    this.replayPushpins = [];

    /* zone vars */
    this.geozoneCenter = null;  // JSMapPoint

    /* drawn shapes */
    this.drawShapes = [];

    /* 'mousemove' to update latitude/longitude */
    var locDisp = document.getElementById(ID_LAT_LON_DISPLAY);
    if (locDisp != null) {
        GEvent.addListener(this.gmapGoogleMap, "mousemove", function (point) {
            jsmSetLatLonDisplay(point.lat(),point.lng());
            jsmapElem.style.cursor = "crosshair";
        });
        jsmSetLatLonDisplay(0,0);
    }
    
    /* "click" */
    GEvent.addListener(this.gmapGoogleMap, "click", function (overlay, point) {
        if (point) {
            var LL = new JSMapPoint(point.lat(), point.lng());
            if (jsvGeozoneMode && jsvZoneEditable) {
                // recenter geozone
                var CC = (this.geozoneCenter != null)? this.geozoneCenter : new JSMapPoint(0.0,0.0);
                if (jsvZoneRadiusMeters <= 0.0              ) { jsvZoneRadiusMeters = DEFAULT_ZONE_RADIUS; }
                if (jsvZoneRadiusMeters >  MAX_ZONE_RADIUS_M) { jsvZoneRadiusMeters = MAX_ZONE_RADIUS_M;   }
                if (jsvZoneRadiusMeters <  MIN_ZONE_RADIUS_M) { jsvZoneRadiusMeters = MIN_ZONE_RADIUS_M;   }
                if (geoDistanceMeters(CC.lat, CC.lon, LL.lat, LL.lon) > jsvZoneRadiusMeters) {
                    jsmSetPointZoneValue(LL.lat, LL.lon, jsvZoneRadiusMeters);
                    mapProviderParseZones(jsvZoneList);
                }
            }
        }
    });

    /* right-click-drag to display 'ruler' */
    this.dragRulerLatLon = null;
    this.rulerOverlay = null;
    var distDisp = document.getElementById(ID_DISTANCE_DISPLAY);
    if (distDisp != null) {
        /*
        GEvent.addListener(this.gmapGoogleMap, 'mousedown', function (e) { // "dragstart", "dragend"
            // how do I tell that the control-key has been pressed?
            if (e.ctrlKey) {
                if (self.rulerOverLay != null) {
                    self.gmapGoogleMap.removeOverlay(self.rulerOverlay);
                    self.rulerOverlay = null;
                }
                jsmSetDistanceDisplay(0);
                this.dragRulerLatLon = new JSMapPoint(point.lat(),point.lng());
            }
        });
        GEvent.addListener(this.gmapGoogleMap, 'mousemove', function (point) {
            if (self.rulerOverLay != null) {
                self.gmapGoogleMap.removeOverlay(self.rulerOverlay);
                self.rulerOverlay = null;
            }
            var ruler = [];
            ruler.push(new GLatLng(this.dragRulerLatLon.lat,this.dragRulerLatLon.lon));
            ruler.push(new GLatLng(point.lat(),point.lng()));
            self.rulerOverlay = new GPolyline(latlon, '#FF6422', 2);
            self.gmapGoogleMap.addOverlay(new GPolyline(latlon, '#FF2222', 2));
        });
        GEvent.addListener(this.gmapGoogleMap, 'mouseup', function (e) {
            self.dragRulerLatLon = null;
        });
        */
    }

};

// ----------------------------------------------------------------------------

/**
*** Unload/release resources
**/
JSMap.prototype.JSUnload = function()
{
    GUnload();
};

// ----------------------------------------------------------------------------

/**
*** Clear all pushpins and drawn lines
**/
JSMap.prototype.JSClearLayers = function()
{

    /* clear all overlays */
    try { this.gmapGoogleMap.clearOverlays(); } catch (e) {}

    /* reset state */
    this._clearReplay();
    this.centerBounds = new GLatLngBounds();

    /* redraw shapes? */
    if (this.drawShapes) {
        for (var s = 0; s < this.drawShapes.length; s++) {
            this.gmapGoogleMap.addOverlay(this.drawShapes[s]);
        }
    }

};

// ----------------------------------------------------------------------------

/**
*** Pause/Resume replay
**/
JSMap.prototype.JSPauseReplay = function(replay)
{
    /* stop replay? */
    if (!replay || (replay <= 0) || !this.replayInProgress) {
        // stopping replay
        this._clearReplay();
        return REPLAY_STOPPED;
    } else {
        // replay currently in progress
        if (this.replayTimer == null) {
            // replay is "PAUSED" ... resuming replay
            this._hidePushpinPopup(this.visiblePopupInfoBox);
            jsmHighlightDetailRow(-1, false);
            this._startReplayTimer(replay, 100);
            return REPLAY_RUNNING;
        } else {
            // replaying "RUNNING" ... pausing replay
            this._stopReplayTimer();
            return REPLAY_PAUSED;
        }
    }
}

/**
*** Start the replay timer
**/
JSMap.prototype._startReplayTimer = function(replay, interval)
{
    if (this.replayInProgress) {
        this.replayTimer = setTimeout("jsmap._replayPushpins("+replay+")", interval);
    }
    jsmSetReplayState(REPLAY_RUNNING);
}

/**
*** Stop the current replay timer
**/
JSMap.prototype._stopReplayTimer = function()
{
    if (this.replayTimer != null) { 
        clearTimeout(this.replayTimer); 
        this.replayTimer = null;
    }
    jsmSetReplayState(this.replayInProgress? REPLAY_PAUSED : REPLAY_STOPPED);
}

/**
*** Clear any current replay in process
**/
JSMap.prototype._clearReplay = function()
{
    this.replayPushpins = [];
    this.replayInProgress = false;
    this._stopReplayTimer();
    this.replayIndex = 0;
    jsmHighlightDetailRow(-1, false);
}

/**
*** Gets the current replay state
**/
JSMap.prototype._getReplayState = function()
{
    if (this.replayInProgress) {
        if (this.replayTimer == null) {
            return REPLAY_PAUSED;
        } else {
            return REPLAY_RUNNING;
        }
    } else {
        return REPLAY_STOPPED;
    }
}

// ----------------------------------------------------------------------------

/**
*** Sets the center of the map
**/
JSMap.prototype.JSSetCenter = function(center, zoom)
{
    if (zoom) {
        this.gmapGoogleMap.setCenter(new GLatLng(center.lat, center.lon), zoom);
    } else {
        this.gmapGoogleMap.setCenter(new GLatLng(center.lat, center.lon));
    }
};

/**
*** Draw the specified pushpins on the map
*** @param pushPins  An array of JSMapPushpin objects
*** @param recenter  True to cause the map to re-center on the drawn pushpins
**/
JSMap.prototype.JSDrawPushpins = function(pushPins, recenterMode, replay)
{

    /* clear replay (may be redundant, but repeated just to make sure) */
    this._clearReplay();
    
    /* drawn pushpins */
    var drawPushpins = [];

    /* recenter map on points */
    var pointCount = 0;
    if ((pushPins != null) && (pushPins.length > 0)) {
        for (var i = 0; i < pushPins.length; i++) {
            var pp = pushPins[i]; // JSMapPushpin
            if ((pp.lat != 0.0) || (pp.lon != 0.0)) {
                pointCount++;
                this.centerBounds.extend(new GLatLng(pp.lat, pp.lon));
                drawPushpins.push(pp);
            }
        }
    }
    if (recenterMode > 0) {
        try {
            if (pointCount <= 0) {
                var centerPt   = new GLatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lon);
                var zoomFactor = DEFAULT_ZOOM;
                this.gmapGoogleMap.setCenter(centerPt, zoomFactor);
            } else 
            if (recenterMode == 1) { // center on last point
                var pp         = drawPushpins[drawPushpins.length - 1];
                var centerPt   = new GLatLng(pp.lat, pp.lon);
                this.gmapGoogleMap.setCenter(centerPt);
            } else {
                var centerPt   = this.centerBounds.getCenter();
                var zoomFactor = this.gmapGoogleMap.getBoundsZoomLevel(this.centerBounds);
                this.gmapGoogleMap.setCenter(centerPt, zoomFactor);
            }
        } catch (e) {
            //alert("Error: [JSDrawPushpins] " + e);
            return;
        }
    }
    if (pointCount <= 0) {
        return;
    }

    /* replay pushpins? */
    if (replay && (replay >= 1)) {
        this.replayIndex = 0;
        this.replayInProgress = true;
        this.replayPushpins = drawPushpins;
        this._startReplayTimer(replay, 100);
        return;
    }

    /* draw pushpins now */
    var pushpinErr = null;
    for (var i = 0; i < drawPushpins.length; i++) {
        var pp = drawPushpins[i];
        try {
            this._addPushpin(pp);
        } catch (e) {
            if (pushpinErr == null) { pushpinErr = e; }
        }
    }
    if (pushpinErr != null) {
        alert("Error: adding pushpins:\n" + pushpinErr);
    }

};

/**
*** Draw the specified PointsOfInterest pushpins on the map
*** @param pushPins  An array of JSMapPushpin objects
**/
JSMap.prototype.JSDrawPOI = function(pushPins)
{

    /* draw pushpins now */
    if ((pushPins != null) && (pushPins.length > 0)) {
        var pushpinErr = null;
        for (var i = 0; i < pushPins.length; i++) {
            var pp = pushPins[i];
            if ((pp.lat == 0.0) && (pp.lon == 0.0)) {
                continue;
            }
            try {
                this._addPushpin(pp);
            } catch (e) {
                if (pushpinErr == null) { pushpinErr = e; }
            }
        }
        if (pushpinErr != null) {
            alert("Error: adding pushpins:\n" + pushpinErr);
        }
    }

}

/**
*** Adds a single pushpin to the map
*** @param pp  The JSMapPushpin object to add to the map
**/
JSMap.prototype._addPushpin = function(pp)
{
    try {

        /* pushpin icon */
        var gmapPushpin = new GIcon();
        gmapPushpin.image = pp.iconUrl;
        if (pp.iconSize)   { gmapPushpin.iconSize   = new GSize(pp.iconSize[0],pp.iconSize[1]); }
        if (pp.iconOffset) { gmapPushpin.iconAnchor = new GPoint(pp.iconOffset[0],pp.iconOffset[1]); }
        gmapPushpin.shadow = pp.shadownUrl;
        if (pp.shadowSize) { gmapPushpin.shadowSize = new GSize(pp.shadowSize[0],pp.shadowSize[1]); }
        gmapPushpin.infoWindowAnchor = new GPoint(5, 1);

        /* marker */
        var pt = new GLatLng(pp.lat, pp.lon);
        var marker = new GMarker(pt, gmapPushpin);
        GEvent.addListener(marker, 'click', function() { marker.openInfoWindowHtml(pp.html); });
        this.gmapGoogleMap.addOverlay(marker);
        pp.marker = marker;

    } catch(e) {
        //
    }
};

/**
*** Replays the list of pushpins on the map
*** @param pp  The JSMapPushpin object to add to the map
**/
JSMap.prototype._replayPushpins = function(replay)
{

    /* advance to next valid point */
    while (true) {
        if (this.replayIndex >= this.replayPushpins.length) {
            this._clearReplay();
            jsmHighlightDetailRow(-1, false);
            return; // stop
        }
        var pp = this.replayPushpins[this.replayIndex]; // JSMapPushpin
        if ((pp.lat != 0.0) || (pp.lon != 0.0)) {
            break; // valid point
        }
        this.replayIndex++;
    }

    /* add pushpin */
    try {
        var pp = this.replayPushpins[this.replayIndex++]; // JSMapPushpin
        pp.hoverPopup = true;
        this._addPushpin(pp);
        if (replay && (replay >= 2)) {
            this._showPushpinPopup(pp);
        } else {
            jsmHighlightDetailRow(pp.rcdNdx, true);
        }
        this._startReplayTimer(replay, this.replayInterval);
    } catch (e) {
        alert("Replay error: " + e);
    }

}

// ----------------------------------------------------------------------------

/**
*** This method should cause the info-bubble popup for the specified pushpin to display
*** @param pushPin   The JSMapPushpin object which popup its info-bubble
**/
JSMap.prototype.JSShowPushpin = function(pp, center)
{
    if (pp) {
        if (center) {
            this.JSSetCenter(new JSMapPoint(pp.lat, pp.lon));
        }
        this._showPushpinPopup(pp);
    }
};

JSMap.prototype._showPushpinPopup = function(pp)
{
    this._hidePushpinPopup(this.visiblePopupInfoBox);
    if (pp) {
        try {
            GEvent.trigger(pp.marker,"click");
        } catch (e) {
            // ignore
        }
        this.visiblePopupInfoBox = pp;
        jsmHighlightDetailRow(pp.rcdNdx, true);
    }
}

JSMap.prototype._hidePushpinPopup = function(pp)
{
    //GEvent.trigger(pp.marker,"click");
    if (pp) {
        jsmHighlightDetailRow(pp.rcdNdx, false);
    }
    if (this.visiblePopupInfoBox) {
        jsmHighlightDetailRow(this.visiblePopupInfoBox.rcdNdx, false);
        this.visiblePopupInfoBox = null;
    }
}

// ----------------------------------------------------------------------------

/**
*** Draws a line between the specified points on the map.
*** @param points   An array of JSMapPoint objects
**/
JSMap.prototype.JSDrawRoute = function(points, color)
{
    var latlon = [];
    for (var i = 0; i < points.length; i++) {
        latlon.push(new GLatLng(points[i].lat,points[i].lon));
    }
    this.gmapGoogleMap.addOverlay(new GPolyline(latlon, color, 2, 1.0)); // "#003399"
    if (ROUTE_LINE_ARROWS) {
        this.midArrows(latlon);
    }
};

//  [Juan Carlos Argueta] retReturns the bearing in degrees between two points.
JSMap.prototype.bearing = function(from, to) {
    // ----- Returns the bearing in degrees between two points. -----
    // ----- North = 0, East = 90, South = 180, West = 270.
    // ----- var degreesPerRadian = 180.0 / Math.PI;

    // ----- Convert to radians.
    var lat1 = from.latRadians();
    var lon1 = from.lngRadians();
    var lat2 = to.latRadians();
    var lon2 = to.lngRadians();

    // -----Compute the angle.
    var angle = - Math.atan2( Math.sin( lon1 - lon2 ) * Math.cos( lat2 ), Math.cos( lat1 ) * Math.sin( lat2 ) - Math.sin( lat1 ) * Math.cos( lat2 ) * Math.cos( lon1 - lon2 ) );
    if (angle < 0.0) { angle  += Math.PI * 2.0; }

    // ----- And convert result to degrees.
    angle = angle * degreesPerRadian;
    angle = angle.toFixed(1);

    return angle;
}
       
// [Juan Carlos Argueta]  A function to create the arrow head at the end of the polyline ===
JSMap.prototype.arrowHead = function(points) {	  
    // ----- obtain the bearing between the last two points
    if (!points || (points.length < 2)) { return; }
    var p1 = points[points.length-1];
    var p2 = points[points.length-2];
    var dir = this.bearing(p2,p1);
    // ----- round it to a multiple of 3 and cast out 120s
    dir = Math.round(dir/3) * 3;
    while (dir >= 120) { dir -= 120; }
    // ----- use the corresponding triangle marker 
    arrowIcon.image = "http://www.google.com/intl/en_ALL/mapfiles/dir_"+dir+".png";
    // map.addOverlay(new GMarker(p1, arrowIcon));
    this.gmapGoogleMap.addOverlay(new GMarker(p1, arrowIcon));
}
      
// [Juan Carlos Argueta]  A function to put arrow heads at intermediate points
JSMap.prototype.midArrows = function(points) {		  
    for (var i = 1; i < points.length - 1; i++) {  
        var p1 = points[i-1];
        var p2 = points[i+1];
        // var dir = bearing(p1,p2);
        var dir = this.bearing(p1,p2);
        // ----- round it to a multiple of 3 and cast out 120s
        dir = Math.round(dir/3) * 3;
        while (dir >= 120) { dir -= 120; }
        // ----- use the corresponding triangle marker 
        arrowIcon.image = "http://www.google.com/intl/en_ALL/mapfiles/dir_"+dir+".png";
        // map.addOverlay(new GMarker(points[i], arrowIcon));
        this.gmapGoogleMap.addOverlay(new GMarker(points[i], arrowIcon));
    }
}

// ----------------------------------------------------------------------------

/**
*** Remove previously drawn shapes 
**/
JSMap.prototype._removeShapes = function()
{
    if (this.drawShapes) {
        for (var s = 0; s < this.drawShapes.length; s++) {
            this.gmapGoogleMap.removeOverlay(this.drawShapes[s]);
        }
    }
    this.drawShapes = [];
}

/**
*** Draws a Shape on the map at the specified location
*** @param type     The Geozone shape type ("line", "circle", "rectangle", "polygon", "center")
*** @param radiusM  The circle radius, in meters
*** @param points   An array of points (JSMapPoint[])
*** @param zoomTo   rue to zoom to drawn shape
*** @return True if shape was drawn, false otherwise
**/
JSMap.prototype.JSDrawShape = function(type, radiusM, verticePts, color, zoomTo)
{

    /* no type? */
    if (!type || (type == "") || (type == "!")) {
        this._removeShapes();
        return false;
    }

    /* clear existing shapes? */
    if (type.startsWith("!")) { 
        this._removeShapes();
        type = type.substr(1); 
    }

    /* no geopoints? */
    if (!verticePts || (verticePts.length == 0)) {
        return false;
    }

    /* color */
    if (!color || (color == "")) {
        color = "#0000FF";
    }

    /* zoom bounds */
    var mapBounds = zoomTo? new GLatLngBounds() : null;

    /* draw shape */
    var didDrawShape = false;
    if (type == "circle") { // ZONE_POINT_RADIUS

        for (var p = 0; p < verticePts.length; p++) {
            var jsPt = verticePts[p]; // JSMapPoint
            
            /* calc circle points */
            var crPts = [];
            var crLat = geoRadians(jsPt.lat);  // radians
            var crLon = geoRadians(jsPt.lon);  // radians
            var d     = radiusM / EARTH_RADIUS_METERS;
            for (x = 0; x <= 360; x += 6) {         // 6 degrees (saves memory, & it still looks like a circle)
                var xrad  = geoRadians(x);          // radians
                var rrLat = Math.asin(Math.sin(crLat) * Math.cos(d) + Math.cos(crLat) * Math.sin(d) * Math.cos(xrad));
                var rrLon = crLon + Math.atan2(Math.sin(xrad) * Math.sin(d) * Math.cos(crLat), Math.cos(d)-Math.sin(crLat) * Math.sin(rrLat));
                var gPt   = new GLatLng(geoDegrees(rrLat),geoDegrees(rrLon));
                crPts.push(gPt);
                if (mapBounds) { mapBounds.extend(gPt); } // TODO: could stand to be optimized
            }
    
            /* draw circle */
            var crPoly = new GPolygon(crPts, color, 1, 0.9, color, 0.1);
            this.gmapGoogleMap.addOverlay(crPoly);
            this.drawShapes.push(crPoly);
            didDrawShape = true;
            
        }

    } else
    if (type == "rectangle") { // ZONE_BOUNDED_RECT
        
        if (verticePts.length >= 2) {

            /* create rectangle */
            var vp0   = verticePts[0];
            var vp1   = verticePts[1];
            var TL    = new GLatLng(((vp0.lat>vp1.lat)?vp0.lat:vp1.lat),((vp0.lon<vp1.lon)?vp0.lon:vp1.lon));
            var TR    = new GLatLng(((vp0.lat>vp1.lat)?vp0.lat:vp1.lat),((vp0.lon>vp1.lon)?vp0.lon:vp1.lon));
            var BL    = new GLatLng(((vp0.lat<vp1.lat)?vp0.lat:vp1.lat),((vp0.lon<vp1.lon)?vp0.lon:vp1.lon));
            var BR    = new GLatLng(((vp0.lat<vp1.lat)?vp0.lat:vp1.lat),((vp0.lon>vp1.lon)?vp0.lon:vp1.lon));
            var crPts = [ TL, TR, BR, BL, TL ];
            if (mapBounds) { for (var b = 0; b < crPts.length; b++) { mapBounds.extend(crPts[b]); } }
    
            /* draw rectangle */
            var crPoly = new GPolygon(crPts, color, 1, 0.9, color, 0.1);
            this.gmapGoogleMap.addOverlay(crPoly);
            this.drawShapes.push(crPoly);
            didDrawShape = true;

        }

    } else
    if (type == "polygon") { // ZONE_POLYGON
        
        if (verticePts.length >= 3) {

            /* accumulate polygon vertices */
            var crPts = [];
            for (var p = 0; p < verticePts.length; p++) {
                var gPt = new GLatLng(verticePts[p].lat, verticePts[p].lon);
                crPts.push(gPt);
                if (mapBounds) { mapBounds.extend(gPt); }
            }
            crPts.push(crPts[0]); // close polygon

            /* draw polygon */
            var crPoly = new GPolygon(crPts, color, 1, 0.9, color, 0.1);
            this.gmapGoogleMap.addOverlay(crPoly);
            this.drawShapes.push(crPoly);
            didDrawShape = true;

        }
        
    } else
    if (type == "center") {

        if (mapBounds) {
            for (var p = 0; p < verticePts.length; p++) {
                var gPt = new GLatLng(verticePts[p].lat, verticePts[p].lon);
                mapBounds.extend(gPt);
            }
            didDrawShape = true;
        }

    }

    /* center on shape */
    if (didDrawShape && zoomTo && mapBounds) {
        var centerPt   = mapBounds.getCenter(); // GLatLng
        var zoomFactor = this.gmapGoogleMap.getBoundsZoomLevel(mapBounds);
        this.gmapGoogleMap.setCenter(centerPt, zoomFactor);
    }

    /* shape not supported */
    return didDrawShape;

}

// ----------------------------------------------------------------------------

var geozonePoints = [];

/**
*** Draws a Geozone on the map at the specified location
*** @param type     The Geozone type
*** @param radiusM  The circle radius, in meters
*** @param points   An array of points (JSMapPoint[])
*** @param primNdx  Index of point on which to center
*** @return An object representing the Circle.
**/
JSMap.prototype.JSDrawGeozone = function(type, radiusM, points, primNdx)
{
    // jsvZoneType:
    //   0 - ZONE_POINT_RADIUS
    //   1 - ZONE_BOUNDED_RECT
    //   2 - ZONE_SWEPT_POINT_RADIUS
    //   3 - ZONE_POLYGON
    // (only type ZONE_POINT_RADIUS is currently supported)

    /* Geozone mode */
    jsvGeozoneMode = true;

    /* remove old geozone */
    for (var i = 0; i < geozonePoints.length; i++) {
        geozonePoints[i].remove();
    }
    geozonePoints = [];
    this.geozoneCenter = null;

    /* draw geozone */
    if (type == ZONE_POINT_RADIUS) {

        if ((points != null) && (points.length > 0)) {
            var zoneNdx = ((primNdx >= 0) && (primNdx < points.length))? primNdx : 0;
            var zoneCenter = points[zoneNdx]; // JSMapPoint
            if (radiusM > MAX_ZONE_RADIUS_M) { radiusM = MAX_ZONE_RADIUS_M; }
            if (radiusM < MIN_ZONE_RADIUS_M) { radiusM = MIN_ZONE_RADIUS_M; }
            jsvZoneRadiusMeters = radiusM;
            this.geozoneCenter = zoneCenter;

            /* draw points */
            var mapBounds = new GLatLngBounds();
            var prg = new PointRadiusGeozone(this.gmapGoogleMap, zoneCenter.lat, zoneCenter.lon, radiusM, jsvZoneEditable);
            mapBounds.extend(new GLatLng(zoneCenter.lat,zoneCenter.lon));
            mapBounds.extend(prg.calcRadiusPoint(0.0));
            mapBounds.extend(prg.calcRadiusPoint(180.0));
            geozonePoints.push(prg);

            /* center on geozone */
            var centerPt   = mapBounds.getCenter(); // GLatLng
            var zoomFactor = this.gmapGoogleMap.getBoundsZoomLevel(mapBounds);
            this.gmapGoogleMap.setCenter(centerPt, zoomFactor);
    
        }

    } else {
        
        alert("Geozone type not supported: " + type);
        
    }
    
    return null;
};

// ----------------------------------------------------------------------------
// ----------------------------------------------------------------------------

function PointRadiusGeozone(gMap, lat, lon, radiusM, editable)
{
    var self = this;

    /* circle attributes */
    this.googleMap        = gMap;
    this.radiusMeters     = (radiusM <= MAX_ZONE_RADIUS_M)? Math.round(radiusM) : MAX_ZONE_RADIUS_M;
    this.centerPoint      = new GLatLng(lat, lon);
    this.radiusPoint      = null;
    this.centerMarker     = null;
    this.radiusMarker     = null;
    this.circlePolygon    = null;

    /* center Icon/marker */
    var centerIcon        = new GIcon();
    centerIcon.image      = "http://labs.google.com/ridefinder/images/mm_20_blue.png";
    centerIcon.shadow     = "http://labs.google.com/ridefinder/images/mm_20_shadow.png";
    centerIcon.iconSize   = new GSize(12,20);
    centerIcon.iconAnchor = new GPoint(6,20);
    centerIcon.shadowSize = new GSize(22,20);
    this.centerMarker = new GMarker(this.centerPoint, { icon: centerIcon, draggable: editable });
    this.googleMap.addOverlay(this.centerMarker);

    /* editable? */
    if (editable) {

        /* center marker dragging */
        this.centerMarker.enableDragging();
        GEvent.addListener(this.centerMarker, "dragend", function() {
            var oldCP = self.centerPoint;
            var oldRP = self.radiusMarker.getPoint();
            var newRP = self.calcRadiusPoint(geoHeading(oldCP.lat(),oldCP.lng(),oldRP.lat(),oldRP.lng()));
            self.centerPoint = self.centerMarker.getPoint();
            self.radiusMarker.setPoint(newRP);
            self.drawCircle(); 
        });
    
        /* radius Icon/Marker */
        var radiusIcon        = new GIcon();
        radiusIcon.image      = "http://labs.google.com/ridefinder/images/mm_20_gray.png";
        radiusIcon.shadow     = "http://labs.google.com/ridefinder/images/mm_20_shadow.png";
        radiusIcon.iconSize   = new GSize(12,20);
        radiusIcon.iconAnchor = new GPoint(6,20);
        radiusIcon.shadowSize = new GSize(22,20);
        this.radiusPoint      = this.calcRadiusPoint(60.0);
        this.radiusMarker     = new GMarker(this.radiusPoint, { icon: radiusIcon, draggable: true });
        this.googleMap.addOverlay(this.radiusMarker);

        /* radius marker dragging */
        this.radiusMarker.enableDragging();
        GEvent.addListener(this.radiusMarker, "dragend", function() {
            var oldCP = self.centerMarker.getPoint();
            var newRP = self.radiusMarker.getPoint();
            var radM  = Math.round(geoDistanceMeters(oldCP.lat(),oldCP.lng(),newRP.lat(),newRP.lng()));
            self.radiusMeters = radM;
            if (self.radiusMeters < MIN_ZONE_RADIUS_M) {
                self.radiusMeters = MIN_ZONE_RADIUS_M;
                newRP = self.calcRadiusPoint(geoHeading(oldCP.lat(),oldCP.lng(),newRP.lat(),newRP.lng()));
                self.radiusMarker.setPoint(newRP);
            } else
            if (self.radiusMeters > MAX_ZONE_RADIUS_M) {
                self.radiusMeters = MAX_ZONE_RADIUS_M;
                newRP = self.calcRadiusPoint(geoHeading(oldCP.lat(),oldCP.lng(),newRP.lat(),newRP.lng()));
                self.radiusMarker.setPoint(newRP);
            }
            jsvZoneRadiusMeters = self.radiusMeters;
            self.drawCircle(); 
        });

    }

    /* draw circle */
    this.drawCircle();

};

PointRadiusGeozone.prototype.calcRadiusPoint = function(heading)
{

    /* calculate new 'radius' point */
    var cpt   = this.centerMarker.getPoint();   // GLatLng [MUST be 'centerMarker.getPoint()' NOT 'centerPoint']
    var crLat = geoRadians(cpt.lat());          // radians
    var crLon = geoRadians(cpt.lng());          // radians
    var d     = this.radiusMeters / EARTH_RADIUS_METERS;
    var xrad  = geoRadians(heading);            // radians
    var rrLat = Math.asin(Math.sin(crLat) * Math.cos(d) + Math.cos(crLat) * Math.sin(d) * Math.cos(xrad));
    var rrLon = crLon + Math.atan2(Math.sin(xrad) * Math.sin(d) * Math.cos(crLat), Math.cos(d)-Math.sin(crLat) * Math.sin(rrLat));
    return new GLatLng(geoDegrees(rrLat), geoDegrees(rrLon));

};

PointRadiusGeozone.prototype.drawCircle = function()
{

    /* calc circle points */
    var points = [];
    var crLat  = geoRadians(this.centerPoint.lat());  // radians
    var crLon  = geoRadians(this.centerPoint.lng());  // radians
    var d      = this.radiusMeters / EARTH_RADIUS_METERS;
    for (x = 0; x <= 360; x += 6) {         // 6 degrees (saves memory, & it still looks like a circle)
        var xrad  = geoRadians(x);          // radians
        var rrLat = Math.asin(Math.sin(crLat) * Math.cos(d) + Math.cos(crLat) * Math.sin(d) * Math.cos(xrad));
        var rrLon = crLon + Math.atan2(Math.sin(xrad) * Math.sin(d) * Math.cos(crLat), Math.cos(d)-Math.sin(crLat) * Math.sin(rrLat));
        var pt    = new GLatLng(geoDegrees(rrLat),geoDegrees(rrLon));
        points.push(pt);
    }
    
    /* remove old circle */
    if (this.circlePolygon != null) {
        this.googleMap.removeOverlay(this.circlePolygon);
    }
    
    /* draw circle */
    //this.circlePolygon = new GPolyline(points, "#0000FF", 2, 0.9);
    this.circlePolygon = new GPolygon(points, "#0000FF", 1, 0.9, "#0000FF", 0.1);
    this.googleMap.addOverlay(this.circlePolygon);
    
    /* set Geozone elements */
    jsmSetPointZoneValue(this.centerPoint.lat(), this.centerPoint.lng(), this.radiusMeters);

};

PointRadiusGeozone.prototype.getCenter = function()
{
    return this.centerPoint; // GLatLng
};

PointRadiusGeozone.prototype.getRadiusMeters = function()
{
    return this.radiusMeters;
};

PointRadiusGeozone.prototype.remove = function()
{
    if (this.radiusMarker != null) {
        this.googleMap.removeOverlay(this.radiusMarker);
    }
    if (this.centerMarker != null) {
        this.googleMap.removeOverlay(this.centerMarker);
    }
    this.googleMap.removeOverlay(this.circlePolygon);
};

// ----------------------------------------------------------------------------
// ----------------------------------------------------------------------------
