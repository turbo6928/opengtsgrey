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
//  2007/03/30  Martin D. Flynn
//     -Added support for 'User' login (in addition to 'Account' login)
//  2007/05/20  Martin D. Flynn
//     -Added support for "admin" user
//     -Added check for login to authorized host domain
//  2007/06/03  Martin D. Flynn
//     -Added I18N support
//  2007/06/13  Martin D. Flynn
//     -Added support for browsers with disabled cookies
//  2007/07/27 Martin D. Flynn
//     -Now set per-thread runtime properties for locale and 'From' email address.
//  2007/01/10 Martin D. Flynn
//     -Added customizable 'BASE_URI()' method
//  2008/02/27 Martin D. Flynn
//     -Added methods '_resolveFile' and '_writeFile' to resolve Image/JavaScript files.
//  2008/05/14 Martin D. Flynn
//     -Added runtime property for enabling cookie support
//  2008/09/01 Martin D. Flynn
//     -Added support for User 'getFirstLoginPageID()'
//  2009/02/20 Martin D. Flynn
//     -"RELOAD:XML" now also reloads the runtime config file.
//  2009/09/23 Martin D. Flynn
//     -An encoded hash of the password is now saved as a session attribute
//  2009/10/02  Martin D. Flynn
//     -Added support for creating pushpin markers with embedded text.
//  2009/12/16  Martin D. Flynn
//     -Added support to "ZONEGEOCODE" handler to allow using the GeocodeProvider
//      defined in the 'private.xml' file.
// ----------------------------------------------------------------------------
package org.opengts.war.track;

import java.util.*;
import java.io.*;
import java.net.*;
import java.math.*;
import java.security.*;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.RenderedImage;
import javax.imageio.ImageIO;

import javax.servlet.*;
import javax.servlet.http.*;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;

import org.opengts.geocoder.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.war.tools.*;
import org.opengts.war.track.page.TrackMap;

public class Track
    extends CommonServlet
    implements Constants
{

    // ------------------------------------------------------------------------
    // TODO: 
    //  - HttpSessionBindingListener (interface) // Only applicable to HttpSession setAttribute/removeAttribute
    //      void valueBound(HttpSessionBindingEvent event)
    //      void valueUnbound(HttpSessionBindingEvent event)
    //  - HttpSessionListener (interface)
    //      void sessionCreated(HttpSessionEvent se)
    //      void sessionDestroyed(HttpSessionEvent se)

    private static class TrackSessionListener
        implements HttpSessionListener
    {
        private int sessionCount = 0;
        public TrackSessionListener() {
            this.sessionCount = 0;
        }
        public void sessionCreated(HttpSessionEvent se) {
            HttpSession session = se.getSession();
            synchronized (this) {
                this.sessionCount++;
            }
        }
        public void sessionDestroyed(HttpSessionEvent se) {
            HttpSession session = se.getSession();
            synchronized (this) {
                this.sessionCount--;
            }
        }
        public int getSessionCount() {
            int count = 0;
            synchronized (this) {
                count = this.sessionCount;
            }
            return count;
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* Debug: display incoming request */
    private static final boolean DISPLAY_REQUEST            = false;

    // ------------------------------------------------------------------------

    /* these must match the data response values in "jsmap.js" */
    public  static final String DATA_RESPONSE_LOGOUT        = "LOGOUT";
    public  static final String DATA_RESPONSE_ERROR         = "ERROR";
    public  static final String DATA_RESPONSE_PING_OK       = "PING:OK";
    public  static final String DATA_RESPONSE_PING_ERROR    = "PING:ERROR";

    // ------------------------------------------------------------------------

    /* global enable cookies */
    private static boolean REQUIRE_COOKIES                  = true;

    // ------------------------------------------------------------------------
    // Commands

    public static final String COMMAND_DEVICE_LIST          = "devlist";

    // ------------------------------------------------------------------------
    // custom page requests

    public static final String PAGE_RELOAD                  = "RELOAD:XML";
    public static final String PAGE_COPYRIGHT               = "COPYRIGHT";
    public static final String PAGE_STATISTICS              = "STATISTICS";
    public static final String PAGE_REVERSEGEOCODE          = "REVERSEGEOCODE";
    public static final String PAGE_ZONEGEOCODE             = "ZONEGEOCODE";
    public static final String PAGE_AUTHENTICATE            = "AUTHENTICATE";

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private static long InitializedTime = 0L;

    /* get initialized time */
    public static long GetInitializedTime()
    {
        return InitializedTime;
    }

    /* static initializer */
    static {

        /* initialize DBFactories */
        // should already have been called by 'RTConfigContextListener'
        DBConfig.servletInit(null);

        /* pre-init Base URI var */
        Track.BASE_URI();

        /* enable cookies? */
        if (RTConfig.hasProperty(DBConfig.TRACK_REQUIRE_COOKIES)) {
            REQUIRE_COOKIES = RTConfig.getBoolean(DBConfig.TRACK_REQUIRE_COOKIES,true);
        } else
        if (RTConfig.hasProperty(DBConfig.TRACK_ENABLE_COOKIES)) {
            // obsolete
            REQUIRE_COOKIES = RTConfig.getBoolean(DBConfig.TRACK_ENABLE_COOKIES,true);
        }

        /* initialized */
        InitializedTime = DateTime.getCurrentTimeSec();

    };

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* return base uri */
    private static String TRACK_BASE_URI = null;
    public static String BASE_URI()
    {
        if (TRACK_BASE_URI == null) {
            String uri = RTConfig.getString(DBConfig.TRACK_BASE_URI, null);
            if (uri == null) { uri = Constants.DEFAULT_BASE_URI; }
            if (uri.equals(".")) {
                TRACK_BASE_URI = "./";
            } else
            if (uri.startsWith("./")) {
                TRACK_BASE_URI = uri;
            } else
            if (uri.startsWith("/")) {
                TRACK_BASE_URI = "." + uri;
            } else {
                TRACK_BASE_URI = "./" + uri;
            }
        }
        return TRACK_BASE_URI;
    }
    
    public static String GetBaseURL(RequestProperties reqState)
    {
        return WebPageAdaptor.EncodeMakeURL(reqState, Track.BASE_URI());
    }
    
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    
    private static final String DIRECTORY_CSS           = "/css/";
    private static final String DIRECTORY_JS            = "/js/";
    private static final String DIRECTORY_IMAGES        = "/images/";
    private static final String DIRECTORY_OPT           = "/opt/";

    private static boolean  trackRootDirInit = false;
    private static File     trackRootDir = null;
    
    /* resolve the specified file relative to the configFile directory */
    protected static File _resolveFile(String fileName)
    {

        /* initialize trackRootDir */
        if (!trackRootDirInit) {
            trackRootDirInit = true;
            trackRootDir = RTConfig.getServletContextPath(); // may return null
        }

        /* have a directory? */
        if (trackRootDir == null) {
            Print.logWarn("No Servlet Context Path!");
            return null;
        }

        /* have a file? */
        if (StringTools.isBlank(fileName)) {
            Print.logWarn("Specified file name is blank/null");
            return null;
        }

        /* remove prefixing '/' from filename */
        if (fileName.startsWith("/")) { 
            fileName = fileName.substring(1); 
        }

        /* assemble file path */
        File filePath = new File(trackRootDir, fileName);
        if (!filePath.isFile()) {
            Print.logInfo("File not found: " + filePath);
            return null;
        }

        /* return resolved file */
        return filePath;

    }
    
    /* write the specified file to the output stream */
    protected static void _writeFile(HttpServletResponse response, File file)
        throws IOException
    {
        //Print.logInfo("Found file: " + file);

        /* content mime type */
        CommonServlet.setResponseContentType(response, HTMLTools.getMimeTypeFromExtension(FileTools.getExtension(file)));

        /* copy file to output */
        OutputStream output = response.getOutputStream();
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            FileTools.copyStreams(input, output);
        } catch (IOException ioe) {
            Print.logError("Error writing file: " + file + " " + ioe);
        } finally {
            if (input != null) { try{input.close();}catch(IOException err){/*ignore*/} }
        }
        output.close();
        
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // GET/POST entry point

    /* GET request */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        this._doWork_wrapper(false, request, response);
    }

    /* POST request */
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        this._doWork_wrapper(true, request, response);
    }

    /* handle POST/GET request */
    private void _doWork_wrapper(boolean isPost, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        AttributeTools.decodeRTP(request);
        PrivateLabel privLabel = this._getPrivateLabel(request);
        if (privLabel != null) {
            try {
                privLabel.pushRTProperties();
                this._doWork(isPost, request, response, privLabel);
            } finally {
                //privLabel.popRTProperties();
                RTConfig.popAllTemporaryProperties();
            }
        } else {
            CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_HTML); // CONTENT_TYPE_PLAIN);
            PrintWriter out = response.getWriter();
            String pageName = AttributeTools.getRequestString(request, CommonServlet.PARM_PAGE, "");
            out.println("<HTML>");
            if (pageName.equals(PAGE_RELOAD)) {
                RTConfig.reload();
                PrivateLabelLoader.loadPrivateLabelXML();
                out.println("RELOADED ...");
            } else
            if (BasicPrivateLabelLoader.hasParsingErrors()) {
                out.println("ERROR encountered:<br>");
                out.println("'private.xml' contains syntax/parsing errors<br>");
                out.println("(see log files for additional information)<br>");
                out.println("<br>");
                out.println("Please contact the System Administrator<br>");
            } else
            if (!BasicPrivateLabelLoader.hasDefaultPrivateLabel()) {
                out.println("The specified URL is not allowed on this server:<br>");
                out.println("(Unrecognized URL and no default Domain has been defined)<br>");
            } else {
                // should not occur
                out.println("ERROR encountered:<br>");
                out.println("Invalid 'private.xml' configuration<br>");
                out.println("<br>");
                out.println("Please contact the System Administrator<br>");
            }
            out.println("</HTML>");
            out.close();
        }
    }

    /* return the PrivateLabel for this URL */
    private PrivateLabel _getPrivateLabel(HttpServletRequest request)
    {
        PrivateLabel privLabel = null;
        String reqURLStr = request.getRequestURL().toString();
        try {
            URL reqURL = new URL(reqURLStr);
            privLabel = (PrivateLabel)PrivateLabelLoader.getPrivateLabel(reqURL);
        } catch (MalformedURLException mfue) {
            // invalid URL?
            Print.logWarn("Invalid URL? " + reqURLStr);
            privLabel = (PrivateLabel)PrivateLabelLoader.getPrivateLabel((String)null);
        }
        if (privLabel == null) {
            Print.logError("Default PrivateLabel not defined, or not found!");
        }
        return privLabel;
    }

    /* handle POST/GET request */
    private void _doWork(boolean isPost, HttpServletRequest request, HttpServletResponse response, PrivateLabel privLabel)
        throws ServletException, IOException
    {

        /* response character set */
        ////response.setCharacterEncoding("UTF-8");         

        /* action request */
        String pageName     = AttributeTools.getRequestString(request, CommonServlet.PARM_PAGE    , ""); // query only
        String cmdName      = AttributeTools.getRequestString(request, CommonServlet.PARM_COMMAND , ""); // query only ("page_cmd")
        String cmdArg       = AttributeTools.getRequestString(request, CommonServlet.PARM_ARGUMENT, ""); // query only

        /* adjust page request */
        if (cmdName.equals(Constants.COMMAND_LOGOUT) || pageName.equals(PAGE_LOGIN)) {
            AttributeTools.clearSessionAttributes(request); // start with a clean slate
            pageName = PAGE_LOGIN;
        }

        /* currently logged-in account/user */
        String loginAcctID  = (String)AttributeTools.getSessionAttribute(request, Constants.PARM_ACCOUNT  , ""); // session only
        String loginUserID  = (String)AttributeTools.getSessionAttribute(request, Constants.PARM_USER     , ""); // session only

        /* account/user */
        String userEmail    = (String)AttributeTools.getRequestAttribute(request, Constants.PARM_USEREMAIL, ""); // session or query
        String accountID    = (String)AttributeTools.getRequestAttribute(request, Constants.PARM_ACCOUNT  , ""); // session or query
        String userID       = (String)AttributeTools.getRequestAttribute(request, Constants.PARM_USER     , ""); // session or query
        String password     =         AttributeTools.getRequestString   (request, Constants.PARM_PASSWORD , null); // query only
        String encpass      =         AttributeTools.getRequestString   (request, Constants.PARM_ENCPASS  , null); // query only
        //Print.logInfo("Account/User/Password: %s/%s/%s", accountID, userID, password);
        
        /* encoded password */
        String encodedPassword = null;
        if (!StringTools.isBlank(encpass)) {
            encodedPassword = encpass; // already encoded password
            password = null;
        } else {
            encodedPassword = Account.encodePassword(password); // may be null, if password is null
            if (encodedPassword == null) { encodedPassword = ""; }
            encpass = null;
        }

        /* session cached device/group */
        String deviceID     = (String)AttributeTools.getRequestAttribute(request, Constants.PARM_DEVICE   , "");
        String groupID      = (String)AttributeTools.getRequestAttribute(request, Constants.PARM_GROUP    , "");

        /* log-in ip address */
        String ipAddr       = request.getRemoteAddr();

        // Options for looking up an AccountID/UserID
        //  1) AccountID/UserID explicitly supplied.
        //  2) AccountID supplied, UserID implied (blank)
        //  3) UserEMail supplied, from which AccountID/UserID is implied

        /* debug info */
        String reqestURL = StringTools.trim(request.getRequestURL().toString());
        if (DISPLAY_REQUEST) {
            try {
                URL url = new URL(reqestURL);
                Print.logInfo("URL Path: " + url.getPath());
                Print.logInfo("URL File: " + url.getFile());
            } catch (MalformedURLException mfue) {
                //
            }
            Print.logInfo("Context Path: " + request.getContextPath());
            Print.logInfo("[" + ipAddr + "] URL: " + reqestURL + " " + request.getQueryString());
            for (Enumeration e = request.getParameterNames(); e.hasMoreElements();) {
                String key = (String)e.nextElement();
                String val = request.getParameter(key);
                Print.logInfo("Request Key:" + key + ", Value:" + val);
            }
        }

        /* check for file resource requests */
        String servletPath = request.getServletPath();
        if ((servletPath != null) && (
             servletPath.startsWith(DIRECTORY_CSS)          || 
             servletPath.startsWith(DIRECTORY_JS)           || 
             servletPath.startsWith(DIRECTORY_IMAGES)       ||
             servletPath.startsWith(DIRECTORY_OPT)   
             )) {
            // If this occurs, it means that the servlet context redirected a request for a file 
            // resource to this servlet.  We attempt here to find the requested file, and write 
            // it to the output stream.
            File filePath = Track._resolveFile(servletPath);
            if (filePath != null) {
                // file was found, write it to the output stream
                Print.logInfo("Serving file: " + filePath);
                Track._writeFile(response, filePath);
            } else {
                // the file was not found
                CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN);
                PrintWriter out = response.getWriter();
                out.println("Invalid file specification");
                out.close();
            }
            return;
        }

        // --- initialize the RequestProperties 
        RequestProperties reqState = new RequestProperties();
        reqState.setBaseURI(Track.BASE_URI());
        reqState.setHttpServletResponse(response);
        reqState.setHttpServletRequest(request);
        reqState.setIPAddress(ipAddr);
        reqState.setCommandName(cmdName);
        reqState.setCommandArg(cmdArg);
        reqState.setCookiesRequired(REQUIRE_COOKIES);  /* enable cookies? */

        /* store the RequestProperties instance in the current request to allow access from TagLibs */
        request.setAttribute(PARM_REQSTATE, reqState);

        /* content only? (obsolete?) */
        int contentOnly = 0;
        if (AttributeTools.hasRequestAttribute(request,PARM_CONTENT)) {
            // key explicitly specified in the request (obsolete)
            contentOnly = AttributeTools.getRequestInt(request,PARM_CONTENT, 0);
            AttributeTools.setSessionInt(request,PARM_CONTENT,contentOnly); // save in session
        } else {
            // get last value stored in the session
            contentOnly = AttributeTools.getSessionInt(request,PARM_CONTENT, 0);
        }
        reqState.setPageFrameContentOnly(contentOnly != 0);

        /* invalid url (no private label defined for this URL host) */
        if (privLabel == null) {
            // This would be an abnormal situation, English should be acceptable here
            if (pageName.equals(PAGE_AUTHENTICATE)) {
                Track._writeAuthenticateXML(response, false);
            } else {
                String invalidURL = "Invalid URL";
                Track.writeErrorResponse(reqState, invalidURL);
            }
            return;
        }
        reqState.setPrivateLabel(privLabel);
        I18N i18n = privLabel.getI18N(Track.class);

        /* authentication request */
        boolean authRequest = (pageName.equals(PAGE_AUTHENTICATE) && 
            privLabel.getBooleanProperty(PrivateLabel.PROP_Track_enableAuthenticationService,false));

        /* display Copyright info */
        if (pageName.equals(PAGE_COPYRIGHT)) {
            CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN);
            PrintWriter out = response.getWriter();
            out.println(org.opengts.Version.getInfo());
            out.close();
            return;
        }

        /* reload PrivateLabel XML */
        // This allow making changes directly within the "$CATALINA_HOME/webapps/track/" to 
        // various 'private.xml' or runtime config files (ie. custom.conf, etc), then force a
        // reload of the changes without having to start/stop Tomcat. Any currently logged-in
        // users will still remain logged-in.
        if (pageName.equals(PAGE_RELOAD)) {
            RTConfig.reload();
            PrivateLabelLoader.loadPrivateLabelXML();
            //ReportFactory.loadReportDefinitionXML(); <-- now invoked by PrivateLabelLoader
            //Print.logInfo(CommonServlet.PARM_COMMAND+"="+cmdName);
            if (cmdName.equalsIgnoreCase("short")) {
                CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN);
                PrintWriter out = response.getWriter();
                out.println("RELOADED");
                out.close();
            } else {
                Track.writeMessageResponse(reqState, "PrivateLabel XML reloaded"); // English only
            }
            return;
        }

        /* custom text marker */
        // EXPERIMENTAL - subject to change
        // Example:
        //   Marker?icon=/images/pp/label47_fill.png&fr=3,2,42,13,8&text=Demo2&rc=red&fc=yellow
        // Options:
        //   icon=<PathToImageIcon>                                 - icon URI (relative to "/track/")
        //   frame=<XOffset>,<YOffset>,<Width>,<Height>,<FontPt>    - text frame definition
        //   fill=<FillColor>                                       - text frame background color
        //   border=<BorderColor>                                   - text frame border color
        //   text=<ShortText>                                       - text
        if (reqestURL.endsWith("/Marker") && !loginAcctID.equals("")) { // make sure someone is logged-in
            // box28_white.png  : 3,2,21,16,8
            // label47_fill.png : 3,2,42,13,9
            String icon  = StringTools.blankDefault(AttributeTools.getRequestString(request,PushpinIcon.MARKER_ARG_ICON,""),"/images/pp/label47_fill.png");
            String frStr = AttributeTools.getRequestString(request, PushpinIcon.MARKER_ARG_TEXT_FRAME, "");
            int    fr[]  = StringTools.parseInt(StringTools.split(frStr,','),0); 
            Color  fillC = ColorTools.parseColor(AttributeTools.getRequestString(request,PushpinIcon.MARKER_ARG_FILL_COLOR  ,""),(Color)null);
            Color  bordC = ColorTools.parseColor(AttributeTools.getRequestString(request,PushpinIcon.MARKER_ARG_BORDER_COLOR,""),(Color)null);
            Color  textC = ColorTools.parseColor(AttributeTools.getRequestString(request,PushpinIcon.MARKER_ARG_TEXT_COLOR  ,""),(Color)null);
            int    X     = ((fr != null) && (fr.length > 0) && (fr[0] > 0))? fr[0] :  3; // X-Offset [ 1] 
            int    Y     = ((fr != null) && (fr.length > 1) && (fr[1] > 0))? fr[1] :  2; // Y-Offset [14]
            int    W     = ((fr != null) && (fr.length > 2) && (fr[2] > 0))? fr[2] : 42; // Width    [29]
            int    H     = ((fr != null) && (fr.length > 3) && (fr[3] > 0))? fr[3] : 13; // Height   [ 9]
            int    F     = ((fr != null) && (fr.length > 4) && (fr[4] > 0))? fr[4] :  9; // FontSize [10]
            String text  = AttributeTools.getRequestString(request, PushpinIcon.MARKER_ARG_TEXT, "");
            File iconPath = Track._resolveFile(icon);
            if (iconPath != null) {
                Print.logInfo("Loading Marker Icon: " + iconPath);
                PushpinIcon.TextIcon ti = new PushpinIcon.TextIcon(iconPath, X, Y, W, H, F);
                if (fillC != null) { ti.setFillColor(fillC); }
                if (bordC != null) { ti.setBorderColor(bordC); }
                if (textC != null) { ti.setTextColor(textC); }
                RenderedImage image = ti.createTextImage(text);
                if (image != null) {
                    response.setContentType(HTMLTools.CONTENT_TYPE_PNG);
                    OutputStream out = response.getOutputStream();
                    ImageIO.write(image, "png", out);
                    out.close();
                    return;
                } else {
                    // error?
                }
            }
            // the file was not found
            CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN);
            PrintWriter out = response.getWriter();
            out.println("Invalid file: " + icon);
            out.close();
            return;
        }

        /* statistics */
        if (pageName.equals(PAGE_STATISTICS) && !cmdName.equals("") && !cmdArg.equals("")) {
            try{
                MethodAction ma = new MethodAction(DBConfig.PACKAGE_OPT_DB_ + "DBStatistics", "getDBStats", String.class, String.class);
                String stats = (String)ma.invoke(cmdName, cmdArg);
                if (stats != null) {
                    CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN);
                    PrintWriter out = response.getWriter();
                    out.println(stats);
                    out.close();
                    return;
                }
            } catch (Throwable th) {
                // ignore
            }
        }

        /* offline? */
        String glbOflMsg  = PrivateLabel.GetGlobalOfflineMessage();
        String ctxOflMsg  = PrivateLabel.GetContextOfflineMessage();
        boolean isOffline = (glbOflMsg != null) || ((ctxOflMsg != null) && !AccountRecord.isSystemAdminAccountID(accountID));
        if (isOffline || pageName.equals(PAGE_OFFLINE)) {
            if (isOffline) {
                Print.logWarn("Domain Offline: " + request.getRequestURL());
            }
            AttributeTools.clearSessionAttributes(request);
            String offlineMsg = (ctxOflMsg != null)? ctxOflMsg : glbOflMsg;
            pageName = PAGE_OFFLINE;
            WebPage trackPage = privLabel.getWebPage(pageName); // may return null
            if (trackPage != null) {
                reqState.setPageName(pageName);
                reqState.setPageNavigationHTML(trackPage.getPageNavigationHTML(reqState));
                trackPage.writePage(reqState, offlineMsg);
            } else {
                if (StringTools.isBlank(offlineMsg)) {
                    offlineMsg = i18n.getString("Track.offline","The system is currently offline for maintenance\nPlease check back later.");
                }
                Track.writeMessageResponse(reqState, offlineMsg);
            }
            return;
        }

        /* explicit logout? */
        if (pageName.equals(PAGE_LOGIN)) {
            AttributeTools.clearSessionAttributes(request); // invalidate
            if (!loginAcctID.equals("")) { 
                Print.logInfo("Logout: " + loginAcctID); 
            }
            String pleaseLogin = i18n.getString("Track.pleaseLogin","Please Login");
            Track._displayLogin(reqState, pleaseLogin, false);
            return;
        }

        /* get requested page */
        WebPage trackPage = privLabel.getWebPage(pageName); // may return null
        if (trackPage != null) {
            reqState.setPageName(pageName);
        }

        /* check for page that does not require a login */
        if ((trackPage != null) && !trackPage.isLoginRequired()) {
            reqState.setPageNavigationHTML(trackPage.getPageNavigationHTML(reqState));
            trackPage.writePage(reqState, "");
            return;
        }

        // --- verify that user is logged in
        // If not logged-in, the login page will be displayed
        
        /* email id */
        String emailID = null;
        if (privLabel.getAllowEmailLogin()) {
            String email = !StringTools.isBlank(userEmail)? userEmail : userID;
            if (email.indexOf("@") > 0) {
                emailID = email;
            }
        }

        /* account-id not specified */
        if (StringTools.isBlank(accountID)) {
            String dftAcctID = privLabel.getDefaultLoginAccount(); // may be blank
            if (!StringTools.isBlank(dftAcctID)) {
                accountID = dftAcctID;
            } else
            if (!privLabel.getAccountLogin() || !StringTools.isBlank(emailID)) {
                // attempt to look up account/user from contact email address
                try {
                    User user = User.getUserForContactEmail(null,emailID);
                    if (user != null) {
                        emailID   = null;
                        accountID = user.getAccountID();
                        userID    = user.getUserID();
                        // account/user revalidated below ...
                    }
                } catch (Throwable th) { // DBException
                    // ignore
                }
            }
            if (StringTools.isBlank(accountID)) { // still blank?
                Print.logInfo("[%s] Account/User was not logged in ...", privLabel.getName());
                AttributeTools.clearSessionAttributes(request);
                if (authRequest) {
                    Track._writeAuthenticateXML(response, false);
                } else
                if ((trackPage instanceof TrackMap)                 &&
                    (cmdName.equals(TrackMap.COMMAND_MAP_UPDATE) || 
                     cmdName.equals(TrackMap.COMMAND_DEVICE_PING)  )  ) {
                    // A map has requested an update, and the user is not logged-in
                    PrintWriter out = response.getWriter();
                    CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN);
                    out.println(DATA_RESPONSE_LOGOUT);
                } else {
                    String enterAcctPass = i18n.getString("Track.enterAccount","Please enter Account/User/Password");
                    Track._displayLogin(reqState, enterAcctPass, false);
                }
                return;
            }
        } else
        if (!StringTools.isBlank(emailID)) {
            // attempt to look up account/user from contact email address
            try {
                User user = User.getUserForContactEmail(accountID,emailID);
                if (user != null) {
                    emailID   = null;
                    //accountID = user.getAccountID();
                    userID    = user.getUserID();
                    // account/user revalidated below ...
                }
            } catch (Throwable th) { // DBException
                // ignore
            }
        }

        /* load account */
        Account account = null;
        try {

            account = Account.getAccount(accountID);
            if (account == null) {
                Print.logInfo("Account does not exist: " + accountID);
                AttributeTools.clearSessionAttributes(request);
                if (authRequest) {
                    Track._writeAuthenticateXML(response, false);
                } else {
                    Track._displayLogin(reqState, i18n.getString("Track.invalidLogin","Invalid Login/Password"), true);
                }
                return;
            } else
            if (!account.isActive()) {
                Print.logInfo("Account is inactive: " + accountID);
                AttributeTools.clearSessionAttributes(request);
                if (authRequest) {
                    Track._writeAuthenticateXML(response, false);
                } else {
                    Track._displayLogin(reqState, i18n.getString("Track.inactiveAccount","Inactive Account"), true);
                }
                return;
            } else
            if (account.isExpired()) {
                Print.logInfo("Account has expired: " + accountID);
                AttributeTools.clearSessionAttributes(request);
                if (authRequest) {
                    Track._writeAuthenticateXML(response, false);
                } else {
                    Track._displayLogin(reqState, i18n.getString("Track.expiredAccount","Expired Account"), true);
                }
                return;
            }

            // check that the Account is authorized for this host domain
            if (privLabel.isRestricted()) {
                String acctDomain = account.getPrivateLabelName();
                String aliasName  = privLabel.getAliasName(); // may be null
                String hostName   = privLabel.getHostName();  // never null (may be PrivateLabel.DEFAULT_HOST)
                for (;;) {
                    if (!StringTools.isBlank(acctDomain)) {
                        if ((aliasName != null) && acctDomain.equals(aliasName)) {
                            break; // account domain matches alias name
                        } else
                        if ((hostName != null) && acctDomain.equals(hostName)) {
                            break; // account domain matches host name
                        } else
                        if (acctDomain.equals(BasicPrivateLabel.ALL_HOSTS)) {
                            break; // account domain explicitly states it is valid in all domains
                        }
                    }
                    // if we get here, we've failed the above tests
                    Print.logInfo("Account not authorized for this host: " + accountID + " ==> " + hostName);
                    if (authRequest) {
                        Track._writeAuthenticateXML(response, false);
                    } else {
                        Track._displayLogin(reqState, i18n.getString("Track.invalidHost","Invalid Login Host"), true);
                    }
                    return;
                }
            }

        } catch (DBException dbe) {

            // Internal error
            AttributeTools.clearSessionAttributes(request);
            Print.logException("Error reading Account: " + accountID, dbe);
            if (authRequest) {
                Track._writeAuthenticateXML(response, false);
            } else {
                Track.writeErrorResponse(reqState, i18n.getString("Track.errorAccount","Error reading Account"));
            }
            return;

        }

        /* default to 'admin' user */
        if (StringTools.isBlank(userID)) {
            userID = account.getDefaultUser();
            if (StringTools.isBlank(userID)) { 
                userID = (privLabel != null)? privLabel.getDefaultLoginUser() : User.getAdminUserID(); 
            }
        }

        /* different account (or not yet logged in) */
        boolean wasLoggedIn = (loginAcctID.equals(accountID) && loginUserID.equals(userID));
        if (!wasLoggedIn) {
            // clear old session variables and continue
            AttributeTools.clearSessionAttributes(request);
        }

        /* validate account/user/password */
        User user = null;
        try {

            /* lookup specified UserID */
            boolean loginOK = true;
            user = User.getUser(account, userID);
            if (user != null) {
                // we found a valid user
            } else
            if (User.isAdminUser(userID)) {
                // logging in as the 'admin' user, and we don't have an explicit user record
            } else {
                Print.logInfo("Invalid User: " + accountID + "/" + userID);
                account = null;
                loginOK = false;
            }

            // if we found the user, check the password (if not already logged in)
            if (loginOK && !wasLoggedIn) {
                if (user != null) {
                    // standard password check
                    if (!user.checkPassword(encodedPassword)) {
                        // user password is invalid (invalidate account/user)
                        Print.logInfo("Invalid Password for User: " + accountID + "/" + userID);
                        account = null;
                        user = null;
                        loginOK = false;
                    }
                } else {
                    // standard password check
                    if (!account.checkPassword(encodedPassword)) {
                        // account password is invalid (invalidate account)
                        Print.logInfo("Invalid Password for Account: " + accountID);
                        account = null;
                        loginOK = false;
                    }
                }
            }

            /* invalid account, user, or password, displays the same error */
            if (!loginOK) {
                AttributeTools.clearSessionAttributes(request);
                if (authRequest) {
                    Track._writeAuthenticateXML(response, false);
                } else
                if (AccountRecord.isSystemAdminAccountID(accountID) && StringTools.isBlank(encodedPassword)) {
                    String enterAcctPass = i18n.getString("Track.enterAccount","Please enter Account/User/Password");
                    Track._displayLogin(reqState, enterAcctPass, false);
                } else
                if (request.getParameter(AttributeTools.ATTR_RTP) != null) {
                    String enterAcctPass = i18n.getString("Track.enterAccount","Please enter Account/User/Password");
                    Track._displayLogin(reqState, enterAcctPass, false);
                } else {
                    String invalidLogin = i18n.getString("Track.invalidLogin","Invalid Login/Password");
                    Track._displayLogin(reqState, invalidLogin, true);
                }
                return;
            } 

            /* inactive/expired user */
            if ((user != null) && !user.isActive()) {
                Print.logInfo("User is inactive: " + accountID + "/" + userID);
                account = null;
                user = null;
                if (authRequest) {
                    Track._writeAuthenticateXML(response, false);
                } else {
                    String inactiveUser = i18n.getString("Track.inactiveUser","Inactive User");
                    Track._displayLogin(reqState, inactiveUser, true);
                }
                return;
            }

            /* log login message */
            if (!wasLoggedIn) {
                // Account/User was not previously logged-in
                if (account.isSystemAdmin()) {
                    Print.logInfo("Login SysAdmin: " + accountID + "/" + userID + " [From " + ipAddr + "]");
                } else {
                    Print.logInfo("Login Account/User: " + accountID + "/" + userID + " [From " + ipAddr + "]");
                }
            }

        } catch (DBException dbe) {

            // Internal error
            AttributeTools.clearSessionAttributes(request);
            Print.logException("Error reading Account/User: " + accountID + "/" + userID, dbe);
            if (authRequest) {
                Track._writeAuthenticateXML(response, false);
            } else {
                String errorAccount = i18n.getString("Track.errorUser","Error reading Account/User");
                Track.writeErrorResponse(reqState, errorAccount);
            }
            return;

        }
        reqState.setCurrentAccount(account);
        reqState.setCurrentUser(user); // may be null
        // login successful

        /* Authenticate test */
        // experimental
        if (authRequest) {
            Track._writeAuthenticateXML(response, true);
            return;
        }
        
        /* Account temporary properties? */
        boolean tempPropsActive = false;
        try {
            Resource resource = Resource.getResource(account, Resource.RESID_TemporaryProperties);
            if (resource != null) {
                RTProperties rtp = resource.getRTProperties();
                Print.logInfo("Pushing '"+Resource.RESID_TemporaryProperties+"': " + rtp);
                RTConfig.pushTemporaryProperties(rtp);
                tempPropsActive = true;
            }
        } catch (DBException dbe) {
            Print.logError("Error reading Account resource: " + dbe);
        }

        /* Reverse Geocode test */
        if (pageName.equals(PAGE_REVERSEGEOCODE) && !reqState.isDemoAccount() && 
            RTConfig.getBoolean("enableReverseGeocodeTest",false)) {
            ReverseGeocodeProvider rgp = privLabel.getReverseGeocodeProvider();
            if (rgp != null) {
                String gpStr = AttributeTools.getRequestString(request, "gp", "0/0"); // from query only
                GeoPoint gp = new GeoPoint(gpStr);
                ReverseGeocode rg = rgp.getReverseGeocode(gp);
                String charSet = StringTools.getCharacterEncoding();
                Print.logInfo("ReverseGeocode: ["+charSet+"]\n"+rg);
                Track.writeMessageResponse(reqState, "ReverseGeocode: ["+charSet+"]\n"+rg); // English only
            } else {
                Track.writeMessageResponse(reqState, "ReverseGeocodeProvider not available"); // English only
            }
            return;
        }

        /* PostalCode Geocode test */
        if (pageName.equals(PAGE_ZONEGEOCODE)) {
            //Print.logInfo("Found 'ZONEGEOCODE' request ...");
            String gpXML = null;
            if (privLabel.getBooleanProperty(PrivateLabel.PROP_ZoneInfo_enableGeocode,false)) {
                String addr    = AttributeTools.getRequestString(request, "addr", ""); // zip/address
                String country = AttributeTools.getRequestString(request, "country", "");
                GeocodeProvider geocodeProv = privLabel.getGeocodeProvider();
                if (geocodeProv != null) {
                    GeoPoint gp = geocodeProv.getGeocode(addr, country);
                    //Print.logInfo("GeocodeProvider ["+geocodeProv.getName()+"] "+addr+" ==> " + gp);
                    if ((gp != null) && gp.isValid()) {
                        StringBuffer sb = new StringBuffer();
                        sb.append("<geocode>\n");
                        sb.append(" <lat>").append(gp.getLatitude() ).append("</lat>\n");
                        sb.append(" <lng>").append(gp.getLongitude()).append("</lng>\n");
                        sb.append("</geocode>\n");
                        gpXML = sb.toString();
                    } else {
                        // no GeoPoint found
                        gpXML = "";
                    }
                } else
                if (StringTools.isBlank(addr)) {
                    // zip/address not specified
                    gpXML = "";
                } else
                if (StringTools.isNumeric(addr)) {
                    // all numeric, US zip code only
                    int timeoutMS = 5000;
                    String zip = addr;
                    gpXML = org.opengts.geocoder.geonames.GeoNames.getPostalCodeLocation_xml(zip, country, timeoutMS);
                } else {
                    // city, state
                    int timeoutMS = 5000;
                    String a[] = StringTools.split(addr,',');
                    if (ListTools.isEmpty(a)) {
                        gpXML = "";
                    } else
                    if (a.length >= 2) {
                        String state = a[a.length - 1];
                        String city  = a[a.length - 2];
                        gpXML = org.opengts.geocoder.geonames.GeoNames.getCityLocation_xml(city, state, country, timeoutMS);
                    } else {
                        String state = "";
                        String city  = a[0];
                        gpXML = org.opengts.geocoder.geonames.GeoNames.getCityLocation_xml(city, state, country, timeoutMS);
                    }
                }
            }
            CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_XML);
            PrintWriter out = response.getWriter();
            out.println(!StringTools.isBlank(gpXML)?gpXML:"<geonames></geonames>");
            out.close();
            return;
        }

        /* do we have a 'trackPage'? */
        if (trackPage == null) {
            // occurs when 'pageName' is either blank or invalid

            /* check user preference for first page after login */
            if (!wasLoggedIn && (user != null) && user.hasFirstLoginPageID()) {
                pageName = user.getFirstLoginPageID();
            } else {
                pageName = PAGE_MENU_TOP;
            }

            /* get page */
            trackPage = privLabel.getWebPage(pageName); // should not be null
            if (trackPage == null) {
                if (pageName.equals(PAGE_MENU_TOP)) {
                    Print.logError("[CRITICAL] Missing required page specification: '%s'", pageName);
                    String invalidPage = i18n.getString("Track.invalidPage","Unrecognized page requested: {0}",pageName);
                    Track.writeErrorResponse(reqState, invalidPage);
                    return;
                } else {
                    Print.logError("Invalid page requested [%s], defaulting to '%s'", pageName, PAGE_MENU_TOP);
                    pageName = PAGE_MENU_TOP;
                    trackPage = privLabel.getWebPage(pageName);
                    if (trackPage == null) {
                        Print.logError("[CRITICAL] Missing required page specification: '%s'", pageName);
                        String invalidPage = i18n.getString("Track.invalidPage","Unrecognized page requested: {0}",pageName);
                        Track.writeErrorResponse(reqState, invalidPage);
                        return;
                    }
                }
            }
            reqState.setPageName(pageName);

        }

        /* invalid page for ACL? */
        if (!privLabel.hasReadAccess(user,trackPage.getAclName())) {
            String userName = (user != null)? user.getUserID() : (User.getAdminUserID() + "?");
            String aclName = trackPage.getAclName();
            //Print.logWarn("User=%s, ACL=%s, Access=%s", userName, aclName, reqState.getAccessLevel(aclName));
            String unauthPage = i18n.getString("Track.unauthPage","Unauthorized page requested: {0}",pageName);
            Track.writeErrorResponse(reqState, unauthPage);
            return;
        }

        /* default selected device group (if any) */
        //if (StringTools.isBlank(groupID)) {
        //    groupID = DeviceGroup.DEVICE_GROUP_ALL;
        //}

        /* default selected device (if any) */
        if ((user != null) && !StringTools.isBlank(deviceID)) {
            try {
                if (!user.isAuthorizedDevice(deviceID)) {
                    deviceID = null;
                }
            } catch (DBException dbe) {
                deviceID = null;
            }
        }
        if (!StringTools.isBlank(deviceID)) {
            reqState.setSelectedDeviceID(deviceID, true);
        } else {
            try {
                if (user != null) {
                    deviceID = user.getDefaultDeviceID(); // already authorized
                } else {
                    OrderedSet<String> d = Device.getDeviceIDsForAccount(accountID, null, 1);
                    deviceID = !ListTools.isEmpty(d)? d.get(0) : null;
                }
            } catch (DBException dbe) {
                deviceID = null;
            }
            reqState.setSelectedDeviceID(deviceID, false);
        }

        /* default selected group (if any) */
        if (User.isAdminUser(user)) {
            // admin user
            OrderedSet<String> g = reqState.getDeviceGroupList(true/*include'ALL'*/);
            if (StringTools.isBlank(groupID)) {
                groupID = !ListTools.isEmpty(g)? g.get(0) : DeviceGroup.DEVICE_GROUP_ALL;
            } else
            if (!ListTools.contains(g,groupID)) {
                Print.logWarn("DeviceGroup list does not contain ID '%s'", groupID);
                groupID = !ListTools.isEmpty(g)? g.get(0) : DeviceGroup.DEVICE_GROUP_ALL;
            } else {
                // groupID is fine as-is
            }
        } else {
            // specific user
            OrderedSet<String> g = reqState.getDeviceGroupList(true/*include'ALL'*/);
            if (StringTools.isBlank(groupID)) {
                groupID = !ListTools.isEmpty(g)? g.get(0) : null;
            } else
            if (!ListTools.contains(g,groupID)) {
                Print.logWarn("DeviceGroup list does not contain ID '%s'", groupID);
                groupID = !ListTools.isEmpty(g)? g.get(0) : null;
            } else {
                // groupID is fine as-is
            }
        }
        reqState.setSelectedDeviceGroupID(groupID);

        /* Account/User checks out, marked as logged in */
        AttributeTools.setSessionAttribute(request, Constants.PARM_ACCOUNT, accountID);
        AttributeTools.setSessionAttribute(request, Constants.PARM_USER   , userID);
        AttributeTools.setSessionAttribute(request, Constants.PARM_GROUP  , groupID);
        AttributeTools.setSessionAttribute(request, Constants.PARM_DEVICE , deviceID);
        AttributeTools.setSessionAttribute(request, Constants.PARM_ENCPASS, encodedPassword);

        /* set login times */
        if (!wasLoggedIn) {
            // Last User login time
            if (user != null) {
                try {
                    user.setLastLoginTime(DateTime.getCurrentTimeSec());
                    user.save();
                } catch (DBException dbe) {
                    Print.logException("Error saving LastLoginTime for user: " + accountID + "/" + userID, dbe);
                    // continue
                }
            }
            // Last Account login time
            try {
                account.setLastLoginTime(DateTime.getCurrentTimeSec());
                account.save();
            } catch (DBException dbe) {
                Print.logException("Error saving LastLoginTime for account: " + accountID, dbe);
                // continue
            }
        }
        //Print.logInfo("Login OK: " + accountID + "/" + userID);
        
        /* dispatch to page */
        reqState.setPageNavigationHTML(trackPage.getPageNavigationHTML(reqState));
        trackPage.writePage(reqState, "");
        
    }

    /* display authentication state */
    private static void _writeAuthenticateXML(
        HttpServletResponse response,
        boolean state)
        throws IOException
    {
        CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_XML);
        PrintWriter out = response.getWriter();
        out.print("<authenticate>");
        out.print(state?"true":"false");
        out.print("</authenticate>");
        out.close();
    }

    /* display login */
    private static void _displayLogin(
        RequestProperties reqState,
        String msg,
        boolean alert)
        throws IOException
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        WebPage loginPage = privLabel.getWebPage(PAGE_LOGIN);
        if (loginPage != null) {
            reqState.setPageNavigationHTML(loginPage.getPageNavigationHTML(reqState));
            if (alert) {
                reqState._setLoginErrorAlert();
            }
            loginPage.writePage(reqState, msg);
        } else {
            I18N i18n = privLabel.getI18N(Track.class);
            String noLogin = i18n.getString("Track.noLogin","Login not available");
            Track.writeErrorResponse(reqState, noLogin);
        }
    }
    
    // ------------------------------------------------------------------------
    // Simple error response

    /* write message to stream */
    public static void writeMessageResponse(
        final RequestProperties reqState,
        final String msg)
        throws IOException
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        WebPage loginPage = privLabel.getWebPage(PAGE_LOGIN);
        if (loginPage != null) {
            reqState.setPageNavigationHTML(loginPage.getPageNavigationHTML(reqState));
        }
        HTMLOutput HTML_CONTENT = new HTMLOutput(CommonServlet.CSS_MESSAGE, "") {  // Content
            public void write(PrintWriter out) throws IOException {
                PrivateLabel privLabel = reqState.getPrivateLabel();
                I18N i18n = privLabel.getI18N(Track.class);
                String baseURL = WebPageAdaptor.EncodeURL(reqState,new URIArg(Track.BASE_URI()));
                out.println(StringTools.replace(msg,"\n","<BR>"));
                out.println("<hr>");
                out.println("<a href=\"" + baseURL + "\">" + i18n.getString("Track.back","Back") + "</a>");
            }
        };
        CommonServlet.writePageFrame(
            reqState,
            null,null,          // onLoad/onUnload
            HTMLOutput.NOOP,    // Style sheets
            HTMLOutput.NOOP,    // JavaScript
            null,               // Navigation
            HTML_CONTENT);      // Content
    }

    /* write error response to stream */
    public static void writeErrorResponse(
        RequestProperties reqState,
        String errMsg)
        throws IOException
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(Track.class);
        String error = i18n.getString("Track.error","ERROR:");
        Track.writeMessageResponse(reqState, error + "\n" + errMsg);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* debug: test page */
    public static void main(String argv[])
        throws IOException
    {
        //RTConfig.setCommandLineArgs(argv);
        
        TimeZone tz = null;
        DateTime fr = new DateTime(tz, 2008, RTConfig.getInt("m",6), 1);
        DateTime to = new DateTime(tz, 2008, RTConfig.getInt("m",7), 1);
        String acctId = "opendmtp";
        String devId  = "mobile";
        
        // OutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        
        //writePage_Map(
        //  out, 
        //  privLabel, 
        //  acctId, 
        //  devId, new String[] { devId }, 
        //  fr, to, 
        //  Account.DEFAULT_TIMEZONE,
        //  lastEvent);
        
        out.close(); // close (flush PrintWriter)
        String s = new String(baos.toByteArray());
        System.err.print(s);

    }

}
