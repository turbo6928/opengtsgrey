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
//  2008/07/21  Martin D. Flynn
//     -Initial release
//  2009/05/01  Martin D. Flynn
//     -Fixed bug that unecessarily processed non-matching tag blocks
//  2009/12/16  Martin D. Flynn
//     -Added compare types "gt", "ge", "lt", "le", "in", "ni"
// ----------------------------------------------------------------------------
package org.opengts.war.track.taglib;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.sql.*;

import javax.servlet.*;
import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import org.opengts.util.*;
import org.opengts.war.tools.*;
import org.opengts.war.track.*;

public class RTConfigTag 
    extends BodyTagSupport
    implements Constants, StringTools.KeyValueMap
{

    // ------------------------------------------------------------------------

  //private static final String KEY_START               = "@@{";
    private static final String KEY_START               = "${";
    private static final String KEY_END                 = "}";
    
    private static final String COMPARE_EQ              = "eq";     // ==
    private static final String COMPARE_NE              = "ne";     // !=
    private static final String COMPARE_GT              = "gt";     // >
    private static final String COMPARE_GE              = "ge";     // >=
    private static final String COMPARE_LT              = "lt";     // <
    private static final String COMPARE_LE              = "le";     // <=
    private static final String COMPARE_INSET           = "inset";  // in set
    private static final String COMPARE_NOTINSET        = "!inset"; // not in set

    private static final String BOOLEAN_TRUE            = "true";
    private static final String BOOLEAN_FALSE           = "false";

    // ------------------------------------------------------------------------
    // <%@ taglib uri="./Track" prefix="gts" %>
    // <gts:var ifKey="key" [compare="eq"] [value="false"]>Some html</gts:var>
    // ------------------------------------------------------------------------
    // <jsp:forward page="xxxxx"/>
    // ------------------------------------------------------------------------

    private String ifKey = null;

    /**
    *** Gets the "ifKey" attribute
    *** @return The "ifKey" attribute
    **/
    public String getIfKey()
    {
        return this.ifKey;
    }
    
    /**
    *** Sets the "ifKey" attribute
    *** @param k  The "ifKey" attribute value
    **/
    public void setIfKey(String k)
    {
        this.ifKey = k;
        this.compareValue = null;
    }
    
    /**
    *** Gets the Session attribute for the specified key
    *** @param key  The attribute key
    *** @param dft  The default value
    *** @return The value for the specified key
    **/
    public String getAttributeValue(String key, String dft)
    {
        if (!StringTools.isBlank(key)) {
            ServletRequest request = super.pageContext.getRequest();
            RequestProperties rp = (RequestProperties)request.getAttribute(PARM_REQSTATE);
            if (rp != null) {
                String v = rp._getKeyValue(key, null);
                if (v != null) {
                    return v;
                }
            }
        }
        return dft;

    }

    // "StringTools.KeyValueMap" interface
    public String getKeyValue(String key, String arg)
    {
        return this.getAttributeValue(key, null);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private String compareType = COMPARE_EQ;

    /**
    *** Gets the "compare" type
    *** @return The "compare" type
    **/
    public String getCompare(String dft)
    {
        return !StringTools.isBlank(this.compareType)? this.compareType : dft;
    }

    /**
    *** Gets the "compare" type
    *** @return The "compare" type
    **/
    public String getCompare()
    {
        return this.compareType;
    }
    
    /**
    *** Sets the "compare" type
    *** @param comp  The "compare" type
    **/
    public void setCompare(String comp)
    {
        this.compareType = comp;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private String compareValue = null;

    /**
    *** Gets the comparison value
    *** @param dft  The default returned value if the comparison value has not been defined
    *** @return The comparison value
    **/
    public String getValue(String dft)
    {
        return !StringTools.isBlank(this.compareValue)? this.compareValue : dft;
    }

    /**
    *** Gets the comparison value
    *** @return The comparison value
    **/
    public String getValue()
    {
        return (this.compareValue != null)? this.compareValue : "";
    }
    
    /**
    *** Sets the comparison value
    *** @param val  The comparison value
    **/
    public void setValue(String val)
    {
        this.compareValue = val;
    }
    
    /**
    *** Returns true if the attribute key matches the current comparison value, based on the
    *** comparison type.
    **/
    public boolean isMatch()
    {

        /* key 'ifKey' */
        String k = this.getIfKey();
        if (StringTools.isBlank(k)) {
            // key not defined (always true)
            return true;
        }

        /* get config value */
        String ct = this.getCompare(COMPARE_EQ).toLowerCase();
        String cv = this.getValue(BOOLEAN_TRUE);        // constant (not null)
        String kv = this.getAttributeValue(k, null);    // variable (may be null)
        if (ct.equals(COMPARE_EQ)) {
            // compare equals
            return (kv != null)?  kv.equalsIgnoreCase(cv) : false;
        } else
        if (ct.equals(COMPARE_NE)) {
            // compare not equals
            return (kv != null)? !kv.equalsIgnoreCase(cv) : true;
        } else
        if (kv == null) {
            return false;
        } else
        if (ct.equals(COMPARE_GT)) {
            // compare greater-than
            return (StringTools.parseDouble(kv,0.0) >  StringTools.parseDouble(cv,0.0));
        } else
        if (ct.equals(COMPARE_GE)) {
            // compare greater-than-or-equals-to
            return (StringTools.parseDouble(kv,0.0) >= StringTools.parseDouble(cv,0.0));
        } else
        if (ct.equals(COMPARE_LT)) {
            // compare less-than
            return (StringTools.parseDouble(kv,0.0) <  StringTools.parseDouble(cv,0.0));
        } else
        if (ct.equals(COMPARE_LE)) {
            // compare less-than-or-equals-to
            return (StringTools.parseDouble(kv,0.0) <= StringTools.parseDouble(cv,0.0));
        } else
        if (ct.equals(COMPARE_INSET)) {
            // compare "in" set
            return ListTools.contains(StringTools.split(cv,','),kv);
        } else
        if (ct.equals(COMPARE_NOTINSET)) {
            // compare "not in" set
            return !ListTools.contains(StringTools.split(cv,','),kv);
        } else {
            // false
            return false;
        }
        
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private String bodyContent = null;
    
    private void setSavedBodyContent(String body)
    {
        this.bodyContent = body;
        /*
        Print.logInfo("BodyContent:\n"+this.bodyContent);
        if (!StringTools.isBlank(this.bodyContent) && Character.isLetter(this.bodyContent.charAt(0))) {
            int e = this.bodyContent.indexOf(KEY_END);
            if (e >= 0) {
                int s = this.bodyContent.indexOf(KEY_START);
                if ((s <= -1) || (s > e)) {
                    this.bodyContent = KEY_START + this.bodyContent;
                    Print.logInfo("Repaired broken Taglib BodyContent: " + this.bodyContent);
                }
            }
        }
        */
    }
    
    private String getSavedBodyContent()
    {
        return (this.bodyContent != null)? this.bodyContent : "";
    }
    
    // ------------------------------------------------------------------------
    
    /**
    *** Tag handler override
    *** May return:
    ***     EVAL_BODY_INCLUDE (only if BodyTag interface NOT implemented)
    ***     EVAL_BODY_TAG (only if BodyTag interface IS implemented)
    ***     EVAL_BODY_BUFFERED
    ***     SKIP_BODY 
    **/
    public int doStartTag()
        throws JspTagException
    {
        if (this.isMatch()) {
            return EVAL_BODY_BUFFERED;
        } else {
            // no-match, do not process this tag-block
            return SKIP_BODY;
        }
    }
    
    /**
    *** Tag handler override
    *** May return:
    ***     EVAL_PAGE
    ***     SKIP_PAGE
    **/
    public int doEndTag()
        throws JspTagException
    {

        try {
            if (this.isMatch()) {
                String body = StringTools.replaceKeys(this.getSavedBodyContent(), this, null,
                    KEY_START, KEY_END);
                super.pageContext.getOut().write(body);
            }
        } catch (Throwable t) {
            if (t instanceof JspTagException) {
                throw (JspTagException)t;
            } else {
                throw new JspTagException(t);
            }
        }
        
        return EVAL_PAGE;
    }

    // ------------------------------------------------------------------------
        
    public void setBodyContent(BodyContent body)
    {
        super.setBodyContent(body);
    }
    
    /**
    *** Invoked before the body of the tag is evaluated but after body content is set
    **/
    public void doInitBody()
        throws JspException
    {
        // invoked after 'setBodyContent'
        super.doInitBody();
    }
    
    /**
    *** Invoked after body content is evaluated
    **/
    public int doAfterBody()
        throws JspException
    {
        // invoked after 'doInitBody'        
        this.setSavedBodyContent(this.getBodyContent().getString());
        return SKIP_BODY; // EVAL_BODY_TAG loops
    }

    // ------------------------------------------------------------------------

    /**
    *** Release resources
    **/
    public void release()
    {
        this.ifKey = null;
        this.compareType  = null;
        this.compareValue = null;
        this.bodyContent  = null;
    }
    
    // ------------------------------------------------------------------------

}
