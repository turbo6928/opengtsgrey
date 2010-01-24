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
// Note:
//  This class holds a polygon based on GeoPoints, and will perform polygon
//  inclusion testing as if the points were in a flat 2D plane.  The accuracy 
//  of 2D calculations based on GeoPoints will descrease and the distance 
//  between the points increases.
// References:
//  http://softsurfer.com/Archive/algorithm_0103/algorithm_0103.htm
// ----------------------------------------------------------------------------
// Change History:
//  2007/07/27  Martin D. Flynn
//     -Initial release
//  2009/09/23  Martin D. Flynn
//     -Fixed 'isPointInside' to close the polygon before performing a point
//      inclusion test.
// ----------------------------------------------------------------------------
package org.opengts.util;

import java.util.*;

import org.opengts.util.*;

/**
*** A container for a polygon composed of GeoPoints
**/

public class GeoPolygon
    implements Cloneable
{

    // ------------------------------------------------------------------------

    private String      name = null; // optional
    private GeoPoint    points[] = null;

    /**
    *** Empty constructor
    **/
    private GeoPolygon()
    {
        super();
    }
    
    /**
    *** Point constructor
    *** @param gp A list of GeoPoints
    **/
    public GeoPolygon(GeoPoint... gp)
    {
        super();
        this.points = gp;
    }

    /**
    *** Point constructor
    *** @param gp An array of lattitude/longitude pairs
    **/
    public GeoPolygon(float gp[][])
    {
        super();
        this.points = new GeoPoint[gp.length];
        for (int i = 0; i < gp.length; i++) {
            this.points[i] = new GeoPoint((double)gp[i][0],(double)gp[i][1]);
        }
    }

    /**
    *** Point constructor
    *** @param gp An array of lattitude/longitude pairs
    **/
    public GeoPolygon(double gp[][])
    {
        super();
        this.points = new GeoPoint[gp.length];
        for (int i = 0; i < gp.length; i++) {
            this.points[i] = new GeoPoint(gp[i][0],gp[i][1]);
        }
    }

    /**
    *** Name/point constructor
    *** @param name The name of the polygon
    *** @param gp A list of GeoPoints
    **/
    public GeoPolygon(String name, GeoPoint... gp)
    {
        this(gp);
        this.name = name;
        // closed
    }

    /**
    *** Name/point constructor
    *** @param name The name of the polygon
    *** @param gp An array of lattitude/longitude pairs
    **/
    public GeoPolygon(String name, float gp[][])
    {
        this(gp);
        this.name = name;
        // closed
    }

    /**
    *** Name/point constructor
    *** @param name The name of the polygon
    *** @param gp An array of lattitude/longitude pairs
    **/
    public GeoPolygon(String name, double gp[][])
    {
        this(gp);
        this.name = name;
        // closed
    }

    /**
    *** Copy constructor
    **/
    public GeoPolygon(GeoPolygon other)
    {
        super();
        if (other != null) {
            this.name     = other.getName();
            this.points   = other.getGeoPoints();
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Gets the name of the polygon
    *** @return The name of the polygon
    **/
    public String getName()
    {
        return this.name;
    }
    
    /**
    *** Sets the name of the polygon
    *** @param name The name of the polygon
    **/
    public void setName(String name)
    {
        this.name = name;
    }
    
    // ------------------------------------------------------------------------

    /**
    *** Return true if this polygon is valid 
    *** @return True if the polygon is valid
    **/
    public boolean isValid()
    {
        if (this.points == null) {
            return false;
        } else
        if (GeoPolygon.isClosed(this.points)) {
            return (this.points.length >= 4);
        } else {
            return (this.points.length >= 3);
        }
    }
    
    // ------------------------------------------------------------------------

    /**
    *** Return the array of points that make up the polygon
    *** @return The array of points that make up the polygon
    **/
    public GeoPoint[] getGeoPoints()
    {
        return this.points;
    }
    
    // ------------------------------------------------------------------------

    /**
    *** Insert point into polygon
    *** @param gp The point to insert into the polygon
    *** @param ndx The index to insert the point at
    **/
    public void insertGeoPoint(GeoPoint gp, int ndx)
    {
        this.points = ListTools.insert(this.points, gp, ndx);
    }
    
    // ------------------------------------------------------------------------

    /**
    *** Returns true if the specified points represent a closed polygon
    *** @param gp The set of points representing a polygon
    *** @return True if the represented polygon is closed
    **/
    public static boolean isClosed(GeoPoint gp[])
    {
        if (gp == null) {
            // no points
            return false;
        } else
        if (gp.length < 3) {
            // minimum points for a closed polygon
            return false;
        } else {
            // first point equals last point?
            GeoPoint gp0 = gp[0];
            GeoPoint gpN = gp[gp.length - 1];
            return gp0.equals(gpN);
        }
    }

    /**
    *** Returns true if this polygon is closed
    *** @return True if this polygon is closed
    **/
    public boolean isClosed()
    {
        return GeoPolygon.isClosed(this.points);
    }

    /**
    *** Closes the polygon represented by the list of points
    *** @return A closed polygon
    **/
    public static GeoPoint[] closePolygon(GeoPoint gp[])
    {
        if (ListTools.isEmpty(gp)) {
            // null/empty, return as-is
            return gp;
        } else
        if (gp.length < 3) {
            // invalid number of points, return as-is
            return gp;
        } else {
            GeoPoint gp0 = gp[0];
            GeoPoint gpN = gp[gp.length - 1];
            if (gp0.equals(gpN)) {
                // already closed
                return gp;
            } else {
                // close and return new array
                return ListTools.add(gp, gp0);
            }
        }
    }

    /**
    *** Closes this GeoPolygon (making sure last point is equal to first point)
    *** @return True if able to close GeoPolygon, false otherwise
    **/
    public boolean closePolygon()
    {
        if ((this.points != null) && (this.points.length >= 3)) {
            this.points = GeoPolygon.closePolygon(this.points);
            return true;
        } else {
            return false;
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** Return the number of points in the polygon
    *** @return The number of points in the polygon
    **/
    public int getSize()
    {
        return (this.points != null)? this.points.length : 0;
    }

    // ------------------------------------------------------------------------

    /**
    *** Returns true if the specified point is inside the polygon, 
    *** same as <code>isPointInside()</code>
    *** @param gp The point to check if is inside the polygon
    *** @return True if the specified point is inside the polygon
    *** @see #isPointInside(GeoPoint gp)
    **/
    public boolean containsPoint(GeoPoint gp)
    {
        return GeoPolygon.isPointInside(gp, this.getGeoPoints());
    }

    /**
    *** Returns true if the specified point is inside the polygon
    *** @param gp The point to check if is inside the polygon
    *** @return True if the specified point is inside the polygon
    **/
    public boolean isPointInside(GeoPoint gp)
    {
        return GeoPolygon.isPointInside(gp, this.getGeoPoints());
    }

    /**
    *** Returns true if the specified point is inside the polygon formed by
    *** a specified list of points, same as <code>isPointInside()</code>  <br>
    *** NOTE: The list of points MUST represent a <strong>closed</strong> polygon.
    *** @param gp The point to check if is inside the polygon
    *** @return True if the specified point is inside the polygon
    *** @see #isPointInside(GeoPoint gp, GeoPoint... pp)
    **/
    public static boolean containsPoint(GeoPoint gp, GeoPoint... pp)
    {
        return GeoPolygon.isPointInside(gp, pp);
    }

    /**
    *** Returns true if the specified point is inside the polygon formed by
    *** a specified list of points <br>
    *** NOTE: The list of points MUST represent a <strong>closed</strong> polygon.
    *** @param gp The point to check if is inside the polygon
    *** @return True if the specified point is inside the polygon
    **/
    public static boolean isPointInside(GeoPoint gp, GeoPoint... pp)
    {

        /* quick argument validation */
        if ((gp == null) || (pp == null)) {
            return false;
        }

        /* close polygon */
        pp = GeoPolygon.closePolygon(pp);

        // Uses "Winding Number" algorithm
        // Notes: 
        //  - This is a very simple algorithm that compares the number of downward vectors
        //    with the number of upward vectors surrounding a specified point.  
        //  - This algorithm was designed for a 2D plane and will fail for a curved surface
        //    where the distance between points is great.
        // Observations:
        //  - It appears that the state borders may have been defined by simple X/Y cooridinated
        //    based on latitude/longitude values.  The simple cases are states bordered by 
        //    constant longitudes or latitudes.
        int wn = 0;                                             // the winding number counter
        for (int i = 0; i < pp.length - 1; i++) {               // edge from V[i] to V[i+1]
            if (pp[i].getY() <= gp.getY()) {                    // start y <= P.y
                if (pp[i+1].getY() > gp.getY()) {               // an upward crossing
                    if (GeoPolygon._isLeft(pp[i],pp[i+1],gp) > 0.0) {  // P left of edge
                        ++wn;                                   // have a valid up intersect
                    }
                }
            } else {                                            // start y > P.y (no test needed)
                if (pp[i+1].getY() <= gp.getY()) {              // a downward crossing
                    if (GeoPolygon._isLeft(pp[i],pp[i+1],gp) < 0.0) {  // P right of edge
                        --wn;                                   // have a valid down intersect
                    }
                }
            }
        }
        return (wn == 0)? false : true; // wn==0 if point is OUTSIDE

    }
    
    /** 
    *** Tests if the point, <code>gpC</code>, is Left|On|Right of an infinite line 
    *** formed by <code>gp0</code> and <code>gp1</code>
    *** @param gp0 First point forming the line
    *** @param gp1 Second point forming the line
    *** @return <ul>
    ****        <li> >0 for gpC left of the line through gp0 and P1</li>
    ***         <li> =0 for gpC on the line</li>
    ***         <li> <0 for gpC right of the line</li>
    ****        </ul>
    *** @see "The January 2001 Algorithm 'Area of 2D and 3D Triangles and Polygons'"
    **/
    private static double _isLeft(GeoPoint gp0, GeoPoint gp1, GeoPoint gpC)
    {
        double val = (gp1.getX() - gp0.getX()) * (gpC.getY() - gp0.getY()) -
                     (gpC.getX() - gp0.getX()) * (gp1.getY() - gp0.getY());
        return val;
    }

    // ------------------------------------------------------------------------

    /**
    *** Shallow copy
    **/
    public Object clone()
    {
        return new GeoPolygon(this);
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private static GeozoneChecker geozoneCheck = null;

    /**
    *** Gets a GeozoneChecker implemtation
    *** @return A GeozoneChecker implementation
    **/
    public static GeozoneChecker getGeozoneChecker()
    {
        if (geozoneCheck == null) {
            geozoneCheck = new GeozoneChecker() {
                public boolean containsPoint(GeoPoint gpTest, GeoPoint gpList[], double radiusKM) {
                    return GeoPolygon.isPointInside(gpTest, gpList);
                }
            };
        }
        return geozoneCheck;
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

}
