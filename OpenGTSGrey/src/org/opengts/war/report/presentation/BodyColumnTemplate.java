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
//  2007/03/11  Martin D. Flynn
//     -Initial release
//  2008/01/10  Martin D. Flynn
//     -Added support for ColumnValue attributes
//  2008/04/11  Martin D. Flynn
//     -Added support for ColumnValue "font-style"
//  2009/07/01  Martin D. Flynn
//     -Added ability to use a custom CSS class
// ----------------------------------------------------------------------------
package org.opengts.war.report.presentation;

import java.io.*;

import org.opengts.util.*;

import org.opengts.war.report.*;

public class BodyColumnTemplate
{

    // ------------------------------------------------------------------------

    public static final String BODY_COLUMN          = "rptBodyCol";
    
    public static final String TOTAL_COLUMN         = "rptTotalCol";

    // ------------------------------------------------------------------------
    
    private String  fieldName       = "";

    // ------------------------------------------------------------------------

    public BodyColumnTemplate(String fieldName)
    {
        this.fieldName = (fieldName != null)? fieldName : "";
    }

    // ------------------------------------------------------------------------

    public String getFieldName()
    {
        return this.fieldName;
    }

    // ------------------------------------------------------------------------

    public void writeHTML(PrintWriter out, int level, int rowIndex, String custCssClass, int colSpan, Object valObj) 
    {
        ColumnValue cv = (valObj instanceof ColumnValue)? (ColumnValue)valObj : null;

        /* column CSS class */
        String cssClass = null;
        if ((cv != null) && cv.hasCssClass()) {
            cssClass = cv.getCssClass();
        } else
        if (!StringTools.isBlank(custCssClass)) {
            cssClass = custCssClass;
        } else
        if (rowIndex < 0) {
            cssClass = TOTAL_COLUMN;
        } else {
            cssClass = BODY_COLUMN;
        }

        /* begin table cell */
        out.print(StringTools.replicateString(" ", level * ReportTable.INDENT));
        out.print("<td");
        out.print(" id=\"" + this.getFieldName() + "\"");
        out.print(" class=\"" + cssClass + "\"");
        out.print(" nowrap");
        if ((cv != null) && cv.hasStyle()) {
            int styleCnt = 0;
            out.print(" style=\"");
            if (cv.hasForegroundColor()) {
                if (styleCnt++ > 0) { out.print(" "); }
                out.print("color: " + cv.getForegroundColor() + ";");
            }
            if (cv.hasBackgroundColor()) {
                if (styleCnt++ > 0) { out.print(" "); }
                out.print("background-color: " + cv.getBackgroundColor() + ";");
            }
            if (cv.hasTextDecoration()) {
                if (styleCnt++ > 0) { out.print(" "); }
                out.print("text-decoration: " + cv.getTextDecoration() + ";");
            }
            if (cv.hasFontStyle()) {
                if (styleCnt++ > 0) { out.print(" "); }
                out.print("font-style: " + cv.getFontStyle() + ";");
            }
            if (cv.hasFontWeight()) {
                if (styleCnt++ > 0) { out.print(" "); }
                out.print("font-weight: " + cv.getFontWeight() + ";");
            }
            out.print("\"");
        }
        if ((cv != null) && cv.hasSortKey()) {
            // used by 'sorttable.js'
            out.print(" "+ReportPresentation.SORTTABLE_SORTKEY+"=\"" + cv.getSortKey() + "\"");
        }
        if (colSpan > 1) {
            out.print(" colspan=\"" + colSpan + "\"");
        }
        out.print(">");

        /* value String */
        String value = (valObj != null)? valObj.toString() : "";
        String cellValue = ReportTable.FilterText(((value!=null)?value:""));
        if ((cv != null) && cv.hasLinkURL()) {
            out.print("<a href=\"" + cv.getLinkURL() + "\"");
            if (cv.hasLinkTarget()) {
                out.print(" target=\"" + cv.getLinkTarget() + "\"");
            }
            out.print(" style=\"text-decoration: none;\">");
            out.print(cellValue);
            out.print("</a>");
        } else {
            out.print(cellValue);
        }

        /* end table cell */
        out.print("</td>\n");

    }

    // ------------------------------------------------------------------------

    public void writeXML(PrintWriter out, int level, int rowIndex, String custCssClass, int colSpan, Object valObj,
        boolean isSoapRequest) 
    {
        ColumnValue cv = (valObj instanceof ColumnValue)? (ColumnValue)valObj : null;
        String PFX1 = XMLTools.PREFIX(isSoapRequest, level * ReportTable.INDENT);

        /* column CSS class */
        String cssClass = null;
        if ((cv != null) && cv.hasCssClass()) {
            cssClass = cv.getCssClass();
        } else
        if (!StringTools.isBlank(custCssClass)) {
            cssClass = custCssClass;
        } else
        if (rowIndex < 0) {
            cssClass = TOTAL_COLUMN;
        } else {
            cssClass = BODY_COLUMN;
        }
        
        /* style */
        StringBuffer style = null;
        if ((cv != null) && cv.hasStyle()) {
            style = new StringBuffer();
            if (cv.hasForegroundColor()) {
                style.append("color:" + cv.getForegroundColor() + ";");
            }
            if (cv.hasBackgroundColor()) {
                style.append("background-color:" + cv.getBackgroundColor() + ";");
            }
            if (cv.hasTextDecoration()) {
                style.append("text-decoration:" + cv.getTextDecoration() + ";");
            }
            if (cv.hasFontStyle()) {
                style.append("font-style:" + cv.getFontStyle() + ";");
            }
            if (cv.hasFontWeight()) {
                style.append("font-weight:" + cv.getFontWeight() + ";");
            }
        }

        /* begin BodyColumn */
        out.print(PFX1);
        out.print(XMLTools.startTAG(isSoapRequest,"BodyColumn",
            XMLTools.ATTR("id",this.getFieldName()) +
            XMLTools.ATTR("class",cssClass) +
            (((style!=null)&&(style.length()>0))?XMLTools.ATTR("style",style):"") +
            ((colSpan>1)?XMLTools.ATTR("colspan",colSpan):""),
            false,false));

        /* value String */
        String cellValue = (valObj != null)? valObj.toString() : "";
        if (!StringTools.isBlank(cellValue)) {
            if ((valObj instanceof Number) || (valObj instanceof Boolean)) {
                out.print(cellValue);
            } else {
                out.print(ReportTable.XmlFilter(isSoapRequest,cellValue));
            }
        }

        /* end BodyColumn */
        out.print(XMLTools.endTAG(isSoapRequest,"BodyColumn",true));

    }

    // ------------------------------------------------------------------------

    public void writeCSV(PrintWriter out, int level, String value) 
    {
        String v = (value != null)? value.trim() : "";
        String csvValue = ReportTable.csvFilter(v);
        out.print(csvValue);
    }

    // ------------------------------------------------------------------------

}
