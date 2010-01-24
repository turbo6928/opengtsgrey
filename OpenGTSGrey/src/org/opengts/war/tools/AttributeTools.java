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
//  2007/01/25  Martin D. Flynn
//     -Initial release
//  2007/07/14  Martin D. Flynn
//     -Added method 'getMatchingKeys(...)'
// ----------------------------------------------------------------------------
package org.opengts.war.tools;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.awt.*;
import java.net.*;
import java.math.BigInteger;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;

public class AttributeTools
{

    // ------------------------------------------------------------------------

    /* ignore property key case */
    private static final boolean    IGNORE_CASE             = true;

    // ------------------------------------------------------------------------

    /* the RTProperties attribute key */
    public  static final String     ATTR_RTP                = "rtp_"; // ?rtp_=dfsdfsdf

    // ------------------------------------------------------------------------

    /** 
    *** Returns the HttpSession from the specified ServletRequest
    *** @param req The ServletRequest
    *** @return The HttpSession extracted from the specified ServletRequest
    **/
    public static HttpSession getSession(ServletRequest req)
    {
        return (req instanceof HttpServletRequest)?
            ((HttpServletRequest)req).getSession(true) :
            null;
    }

    /* return the current HttpSessionContext */
    // deprecated: returns null/empty
    //public static HttpSessionContext getSessionContext(ServletRequest req)
    //{
    //    HttpSession session = AttributeTools.getSession(req);
    //    return (session != null)? session.getSessionContext() : null;
    //}

    // ------------------------------------------------------------------------

    /**
    *** Looks for and decodes request argument "rtp" and adds any contained
    *** properties to the request attributes.
    *** @param req  The ServletRequest
    *** @return A copy of the decoded RTProperties
    **/
    public static RTProperties decodeRTP(ServletRequest req)
    {
        if (req != null) {
            String args = req.getParameter(ATTR_RTP);
            if (!StringTools.isBlank(args)) {
                RTProperties rtp = URIArg.decodeRTP(args);
                if (rtp != null) {
                    rtp.setIgnoreKeyCase(IGNORE_CASE);
                    req.setAttribute(ATTR_RTP, rtp);
                    return rtp;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------
    
    /** 
    *** Returns an array of keys from the list of session request parameters that
    *** match the specified partial key.
    *** @param key_  The parameter partial key
    *** @return An array of matching parameter keys
    **/
    public static String[] getMatchingKeys(ServletRequest req, String key_)
    {
        if (req == null)  {
            return null;
        } else {
            boolean allKeys = (key_ == null) || key_.equals("");
            java.util.List<String> keyList = new Vector<String>();
            for (Enumeration e = req.getParameterNames(); e.hasMoreElements();) {
                String k = (String)e.nextElement();
                if (allKeys) {
                    keyList.add(k);
                } else
                if (IGNORE_CASE? StringTools.startsWithIgnoreCase(k,key_) : k.startsWith(key_)) {
                    keyList.add(k);
                }
            }
            if (keyList.size() > 0) {
                // at least one entry was found
                return keyList.toArray(new String[keyList.size()]);
            } else {
                // no entries found
                return null;
            }
        }
    }

    // ------------------------------------------------------------------------
    // Search for the specified key in the following location(s):
    //  1) The URL Query string

    /**
    *** Returns true if the specified key is defined in the parameter list for
    *** the specified ServletRequest.
    *** @param req  The ServletRequest
    *** @param key  The key to test
    *** @return True if the specified key is defined.
    **/
    public static boolean hasRequestAttribute(ServletRequest req, String key)
    {
        String v = getRequestString(req,key,null);
        return (v != null);
    }

    /**
    *** Returns true if the specified key is defined in the parameter list for
    *** the specified ServletRequest.
    *** @param req  The ServletRequest
    *** @param key  An array of keys to test
    *** @return True if the specified key is defined.
    **/
    public static boolean hasRequestAttribute(ServletRequest req, String key[])
    {
        String v = getRequestString(req,key,null);
        return (v != null);
    }

    /**
    *** Returns the String value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  The key to test
    *** @param dft  The default value to return if the key is not defined
    *** @return The String value of the specified key
    **/
    public static String getRequestString(ServletRequest req, String key, String dft)
    {

        /* nothing to lookup? */
        if ((req == null) || StringTools.isBlank(key)) {
            return dft;
        }

        /* standard parameters */
        if (IGNORE_CASE) {
            for (Enumeration e = req.getParameterNames(); e.hasMoreElements();) {
                String n = (String)e.nextElement();
                if (n.equalsIgnoreCase(key)) {
                    String val = req.getParameter(n);
                    if (val != null) {
                        return val;
                    }
                }
            }
        } else {
            String val = req.getParameter(key);
            if (val != null) {
                return val;
            }
        }

        /* RTProperties? */
        RTProperties rtp = (RTProperties)req.getAttribute(ATTR_RTP);
        if (rtp != null) {
            String val = rtp.getString(key,null);
            if (val != null) {
                return val;
            }
        }

        /* default */
        return dft;

    }

    /**
    *** Returns the String value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  An array of keys to test
    *** @param dft  The default value to return if the key is not defined
    *** @return The String value of the specified key
    **/
    public static String getRequestString(ServletRequest req, String key[], String dft)
    {
        if ((req == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            for (int i = 0; i < key.length; i++) {
                String val = getRequestString(req,key[i],null);
                if (val != null) {
                    return val;
                }
            }
            return dft;
        }
    }

    /**
    *** Returns the Double value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  The key to test
    *** @param dft  The default value to return if the key is not defined, 
    ***             or cannot be converted to a Double.
    *** @return The Double value of the specified key
    **/
    public static double getRequestDouble(ServletRequest req, String key, double dft)
    {
        return StringTools.parseDouble(getRequestString(req,key,null), dft);
    }

    /**
    *** Returns the Double value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  An array of keys to test
    *** @param dft  The default value to return if the key is not defined, 
    ***             or cannot be converted to a Double.
    *** @return The Double value of the specified key
    **/
    public static double getRequestDouble(ServletRequest req, String key[], double dft)
    {
        return StringTools.parseDouble(getRequestString(req,key,null), dft);
    }

    /**
    *** Returns the Long value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  The key to test
    *** @param dft  The default value to return if the key is not defined,
    ***             or cannot be converted to a Long.
    *** @return The Long value of the specified key
    **/
    public static long getRequestLong(ServletRequest req, String key, long dft)
    {
        return StringTools.parseLong(getRequestString(req,key,null), dft);
    }

    /**
    *** Returns the Long value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  An array of keys to test
    *** @param dft  The default value to return if the key is not defined,
    ***             or cannot be converted to a Long.
    *** @return The Long value of the specified key
    **/
    public static long getRequestLong(ServletRequest req, String key[], long dft)
    {
        return StringTools.parseLong(getRequestString(req,key,null), dft);
    }

    /**
    *** Returns the Int value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  The key to test
    *** @param dft  The default value to return if the key is not defined,
    ***             or cannot be converted to a Int.
    *** @return The Int value of the specified key
    **/
    public static int getRequestInt(ServletRequest req, String key, int dft)
    {
        return StringTools.parseInt(getRequestString(req,key,null), dft);
    }

    /**
    *** Returns the Int value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  An array of keys to test
    *** @param dft  The default value to return if the key is not defined,
    ***             or cannot be converted to a Int.
    *** @return The Int value of the specified key
    **/
    public static int getRequestInt(ServletRequest req, String key[], int dft)
    {
        return StringTools.parseInt(getRequestString(req,key,null), dft);
    }

    /**
    *** Returns the Boolean value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  The key to test
    *** @param dft  The default value to return if the key is not defined,
    ***             or cannot be converted to a Boolean.
    *** @return The Boolean value of the specified key
    **/
    public static boolean getRequestBoolean(ServletRequest req, String key, boolean dft)
    {
        return StringTools.parseBoolean(getRequestString(req,key,null), dft);
    }

    /**
    *** Returns the Boolean value of the specified key from the parameter list in
    *** the specified ServletRequest
    *** @param req  The ServletRequest
    *** @param key  An array of keys to test
    *** @param dft  The default value to return if the key is not defined,
    ***             or cannot be converted to a Boolean.
    *** @return The Boolean value of the specified key
    **/
    public static boolean getRequestBoolean(ServletRequest req, String key[], boolean dft)
    {
        return StringTools.parseBoolean(getRequestString(req,key,null), dft);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Search for the specified key in the following location(s):
    //  1) The Session attributes

    /**
    *** Returns true if the specified attribute key is defined in the specified HttpSession
    *** @param key  The attribute key to test
    *** @return True if the attribute key is defined
    **/
    public static boolean hasSessionAttribute(HttpSession sess, String key)
    {
        Object val = getSessionAttribute(sess,key,null);
        return (val != null);
    }

    /**
    *** Returns true if the specified attribute key is defined in the specified HttpSession
    *** @param key  The attribute key to test
    *** @return True if the attribute key is defined
    **/
    public static boolean hasSessionAttribute(HttpSession sess, String key[])
    {
        Object val = getSessionAttribute(sess,key,null);
        return (val != null);
    }

    /**
    *** Returns true if the specified attribute key is defined in the specified ServletRequest
    *** @param key  The attribute key to test
    *** @return True if the attribute key is defined
    **/
    public static boolean hasSessionAttribute(ServletRequest req, String key)
    {
        return hasSessionAttribute(getSession(req), key);
    }

    /**
    *** Returns true if the specified attribute key is defined in the specified ServletRequest
    *** @param key  The attribute key to test
    *** @return True if the attribute key is defined
    **/
    public static boolean hasSessionAttribute(ServletRequest req, String key[])
    {
        return hasSessionAttribute(getSession(req), key);
    }

    // --------------------------------

    /**
    *** Gets the value for the specified attribute key from the specified HttpSession
    *** @param sess  The HttpSession
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined.
    *** @return The value of the specified attribute key
    **/
    public static Object getSessionAttribute(HttpSession sess, String key, Object dft)
    {
        Object val = (sess != null)? sess.getAttribute(key) : null;
        return (val != null)? val : dft;
    }

    /**
    *** Gets the value for the specified attribute key from the specified HttpSession
    *** @param sess  The HttpSession
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined.
    *** @return The value of the specified attribute key
    **/
    public static Object getSessionAttribute(HttpSession sess, String key[], Object dft)
    {
        if ((sess == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            for (int i = 0; i < key.length; i++) {
                Object val = getSessionAttribute(sess,key[i],null);
                if (val != null) {
                    return val;
                }
            }
            return dft;
        }
    }

    /**
    *** Gets the value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined.
    *** @return The value of the specified attribute key
    **/
    public static Object getSessionAttribute(ServletRequest req, String key, Object dft)
    {
        Object val = getSessionAttribute(getSession(req), key, null);
        return (val != null)? val : dft;
    }

    /**
    *** Gets the value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined.
    *** @return The value of the specified attribute key
    **/
    public static Object getSessionAttribute(ServletRequest req, String key[], Object dft)
    {
        if ((req == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            for (int i = 0; i < key.length; i++) {
                Object val = getSessionAttribute(req,key[i],null);
                if (val != null) {
                    return val;
                }
            }
            return dft;
        }
    }

    // --------------------------------

    /**
    *** Gets the String value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined.
    *** @return The String value of the specified attribute key
    **/
    public static String getSessionString(ServletRequest req, String key, String dft)
    {
        Object val = getSessionAttribute(getSession(req), key, null);
        return (val != null)? val.toString() : dft;
    }

    /**
    *** Gets the String value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined.
    *** @return The String value of the specified attribute key
    **/
    public static String getSessionString(ServletRequest req, String key[], String dft)
    {
        if ((req == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            for (int i = 0; i < key.length; i++) {
                String val = getSessionString(req,key[i],null);
                if (val != null) {
                    return val;
                }
            }
            return dft;
        }
    }

    // --------------------------------

    /**
    *** Gets the Double value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined,
    ***              or cannot be converted to a Double.
    *** @return The Double value of the specified attribute key
    **/
    public static double getSessionDouble(ServletRequest req, String key, double dft)
    {
        Object val = getSessionAttribute(getSession(req), key, null);
        return StringTools.parseDouble(val, dft);
    }

    /**
    *** Gets the Double value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined,
    ***              or cannot be converted to a Double.
    *** @return The Double value of the specified attribute key
    **/
    public static double getSessionDouble(ServletRequest req, String key[], double dft)
    {
        if ((req == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            for (int i = 0; i < key.length; i++) {
                Object val = getSessionAttribute(req,key[i],null);
                if (val != null) {
                    return StringTools.parseDouble(val, dft);
                }
            }
            return dft;
        }
    }

    // --------------------------------

    /**
    *** Gets the Long value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined,
    ***              or cannot be converted to a Long.
    *** @return The Long value of the specified attribute key
    **/
    public static long getSessionLong(ServletRequest req, String key, long dft)
    {
        Object val = getSessionAttribute(getSession(req), key, null);
        return StringTools.parseLong(val, dft);
    }

    /**
    *** Gets the Long value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined,
    ***              or cannot be converted to a Long.
    *** @return The Long value of the specified attribute key
    **/
    public static long getSessionLong(ServletRequest req, String key[], long dft)
    {
        if ((req == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            for (int i = 0; i < key.length; i++) {
                Object val = getSessionAttribute(req,key[i],null);
                if (val != null) {
                    return StringTools.parseLong(val, dft);
                }
            }
            return dft;
        }
    }

    // --------------------------------

    /**
    *** Gets the Int value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined,
    ***              or cannot be converted to a Int.
    *** @return The Int value of the specified attribute key
    **/
    public static int getSessionInt(ServletRequest req, String key, int dft)
    {
        Object val = getSessionAttribute(getSession(req), key, null);
        return StringTools.parseInt(val, dft);
    }

    /**
    *** Gets the Int value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined,
    ***              or cannot be converted to a Int.
    *** @return The Int value of the specified attribute key
    **/
    public static int getSessionInt(ServletRequest req, String key[], int dft)
    {
        if ((req == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            for (int i = 0; i < key.length; i++) {
                Object val = getSessionAttribute(req,key[i],null);
                if (val != null) {
                    return StringTools.parseInt(val, dft);
                }
            }
            return dft;
        }
    }

    // --------------------------------

    /**
    *** Gets the Boolean value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined,
    ***              or cannot be converted to a Boolean.
    *** @return The Boolean value of the specified attribute key
    **/
    public static boolean getSessionBoolean(ServletRequest req, String key, boolean dft)
    {
        Object val = getSessionAttribute(getSession(req), key, null);
        return StringTools.parseBoolean(val, dft);
    }
    
    /**
    *** Gets the Boolean value for the specified attribute key from the specified ServletRequest
    *** @param req   The ServletRequest
    *** @param key   The key for which the attribute value will be returned
    *** @param dft   The default value returns if the key is not defined,
    ***              or cannot be converted to a Boolean.
    *** @return The Boolean value of the specified attribute key
    **/
    public static boolean getSessionBoolean(ServletRequest req, String key[], boolean dft)
    {
        if ((req == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            for (int i = 0; i < key.length; i++) {
                Object val = getSessionAttribute(req,key[i],null);
                if (val != null) {
                    return StringTools.parseBoolean(val, dft);
                }
            }
            return dft;
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Search for the specified key in the following location(s):
    //  1) The URL Query string
    //  2) The Session attributes

    /**
    *** Returns the value for the specified key from the ServletRequest.  The query
    *** string is first searched for the key/value.  The session attributes are then
    *** search if the key/value is not found in the query string.
    *** @param req  The ServletRequest
    *** @param key  The key for which the value is to be returned
    *** @param dft  The default value return if the key is not defined
    *** @return The value of the speciied key
    **/
    public static Object getRequestAttribute(ServletRequest req, String key, Object dft)
    {
        // first try the query string
        Object val = getRequestString(req, key, null);
        // then try the session attributes
        if (val == null) { val = getSessionAttribute(req, key, null); }
        // all else fails, return the default
        return (val != null)? val : dft;
    }

    /**
    *** Returns the value for the specified key from the ServletRequest.  The query
    *** string is first searched for the key/value.  The session attributes are then
    *** search if the key/value is not found in the query string.
    *** @param req  The ServletRequest
    *** @param key  The key for which the value is to be returned
    *** @param dft  The default value return if the key is not defined
    *** @return The value of the speciied key
    **/
    public static Object getRequestAttribute(ServletRequest req, String key[], Object dft)
    {
        if ((req == null) || ListTools.isEmpty(key)) {
            return dft;
        } else {
            // first try the query string
            Object val = getRequestString(req, key, null);
            // then try the session attributes
            if (val == null) { val = getSessionAttribute(req, key, null); }
            // all else fails, return the default
            return (val != null)? val : dft;
        }
    }
    
    // ------------------------------------------------------------------------
    
    /**
    *** Sets the HttpSession attribute value for the specified key
    *** @param sess  The HttpSession
    *** @param key   The attribute key to set
    *** @param val   The value to set for the specified key
    **/
    public static void setSessionAttribute(HttpSession sess, String key, Object val)
    {
        if (sess != null) {
            try {
                if (val != null) {
                    sess.setAttribute(key, val);
                } else {
                    sess.removeAttribute(key);
                }
            } catch (Throwable th) { // IllegalStateException
                Print.logError("Error setting HttpSession attribute: " + th);
            }
        }
    }
    
    /**
    *** Sets the ServletRequest attribute value for the specified key
    *** @param req   The ServletRequest
    *** @param key   The attribute key to set
    *** @param val   The value to set for the specified key
    **/
    public static void setSessionAttribute(ServletRequest req, String key, Object val)
    {
        setSessionAttribute(getSession(req), key, val);
    }
    
    /**
    *** Sets the ServletRequest attribute Double value for the specified key
    *** @param req   The ServletRequest
    *** @param key   The attribute key to set
    *** @param val   The Double value to set for the specified key
    **/
    public static void setSessionDouble(ServletRequest req, String key, double val)
    {
        setSessionAttribute(getSession(req), key, new Double(val));
    }
    
    /**
    *** Sets the ServletRequest attribute Long value for the specified key
    *** @param req   The ServletRequest
    *** @param key   The attribute key to set
    *** @param val   The Long value to set for the specified key
    **/
    public static void setSessionLong(ServletRequest req, String key, long val)
    {
        setSessionAttribute(getSession(req), key, new Long(val));
    }
    
    /**
    *** Sets the ServletRequest attribute Int value for the specified key
    *** @param req   The ServletRequest
    *** @param key   The attribute key to set
    *** @param val   The Int value to set for the specified key
    **/
    public static void setSessionInt(ServletRequest req, String key, int val)
    {
        setSessionAttribute(getSession(req), key, new Integer(val));
    }
    
    /**
    *** Sets the ServletRequest attribute Boolean value for the specified key
    *** @param req   The ServletRequest
    *** @param key   The attribute key to set
    *** @param val   The Boolean value to set for the specified key
    **/
    public static void setSessionBoolean(ServletRequest req, String key, boolean val)
    {
        setSessionAttribute(getSession(req), key, new Boolean(val));
    }

    // ------------------------------------------------------------------------

    /**
    *** Clears all HttpSession attributes
    *** @param sess  The HttpSession 
    **/
    public static void clearSessionAttributes(HttpSession sess)
    {
        if (sess != null) {
            sess.invalidate();
        }
    }

    /**
    *** Clears all ServletRequest attributes
    *** @param req  The ServletRequest 
    **/
    public static void clearSessionAttributes(ServletRequest req)
    {
        clearSessionAttributes(getSession(req));
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
   
    private static final String ARG_DECODE[]    = new String[] { "decode" , "d"  };
    private static final String ARG_ENCODE[]    = new String[] { "encode" , "e"  };
    private static final String ARG_URL[]       = new String[] { "url"    , "u"  };

    private static void usage()
    {
        Print.logInfo("Usage:");
        Print.logInfo("  java ... " + AttributeTools.class.getName() + " {options}");
        Print.logInfo("Options:");
        Print.logInfo("  -encode=<ASCII>    Encode ASCII string to URL argument string");
        Print.logInfo("  -decode=<args>     Decode URL argument string to ASCII");
        System.exit(1);
    }

    public static void main(String argv[])
    {
        RTConfig.setCommandLineArgs(argv);

        /* encode rtp URL */
        if (RTConfig.hasProperty(ARG_URL)) {
            URIArg u = new URIArg(RTConfig.getString(ARG_URL,""));
            String r = URIArg.encodeRTP(u.getArgProperties());
            Print.sysPrintln("==> " + u.getURI() + "?" + ATTR_RTP + "=" + r);
            System.exit(0);
        }

        /* no options */
        usage();
        
    }

}
