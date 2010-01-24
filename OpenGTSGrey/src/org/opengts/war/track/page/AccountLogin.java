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
//  2007/06/03  Martin D. Flynn
//     -Added I18N support
//  2007/06/13  Martin D. Flynn
//     -Added support for browsers with disabled cookies
//  2007/07/27  Martin D. Flynn
//     -Added 'getNavigationTab(...)'
//  2007/12/13  Martin D. Flynn
//     -Changed form target to '_self' for "ContentOnly" display
//  2008/12/01  Martin D. Flynn
//     -Increased maxsize for account/user/password fields to match length specified
//      in their respective tables.
//  2009/01/01  Martin D. Flynn
//     -Added popup 'alert' for login errors
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

import org.opengts.war.tools.*;
import org.opengts.war.track.*;

public class AccountLogin
    extends WebPageAdaptor
    implements Constants
{

    // ------------------------------------------------------------------------
    // Tomcat conf/server.xml
    //   emptySessionPath="true"
    //   <SessionId cookiesFirst="true" noCookies="true"/>
    // HttpServletResponse.encodeURL() 
    // ------------------------------------------------------------------------

    private static       String FORM_LOGIN              = "Login";
    private static       String FORM_DEMO               = "Demo";

    // ------------------------------------------------------------------------

    public  static final String CSS_ACCOUNT_LOGIN[]     = new String[] { "accountLoginTable", "accountLoginCell" };

    // ------------------------------------------------------------------------
    // Properties

    public  static final String PROP_customLoginUrl     = "customLoginUrl";

    // ------------------------------------------------------------------------
    // WebPage interface
    
    public AccountLogin()
    {
        this.setBaseURI(Track.BASE_URI());
        super.setPageName(PAGE_LOGIN); // 'super' required here
        this.setPageNavigation(new String[] { PAGE_LOGIN });
        this.setLoginRequired(false);
    }

    // ------------------------------------------------------------------------

    public void setPageName(String pageName)
    {
        // ignore (changing the PAGE_LOGIN name is not allowed)
    }

    // ------------------------------------------------------------------------
    
    
    public URIArg getPageURI(String command, String cmdArg)
    {
        String loginURL = this.getProperties().getString(PROP_customLoginUrl,null);
        if (!StringTools.isBlank(loginURL)) {
            Print.logInfo("Login custom URL: " + loginURL);
            return new URIArg(loginURL);
        } else {
            return super.getPageURI(command, cmdArg);
        }
    }

    // ------------------------------------------------------------------------

    public String getMenuName(RequestProperties reqState)
    {
        return "";
    }

    public String getMenuDescription(RequestProperties reqState, String parentMenuName)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(AccountLogin.class);
        return super._getMenuDescription(reqState,i18n.getString("AccountLogin.menuDesc","Logout"));
    }

    public String getMenuHelp(RequestProperties reqState, String parentMenuName)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(AccountLogin.class);
        return super._getMenuHelp(reqState,i18n.getString("AccountLogin.menuHelp","Logout"));
    }

    // ------------------------------------------------------------------------

    public String getNavigationDescription(RequestProperties reqState)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(AccountLogin.class);
        if (reqState.isLoggedIn()) {
            return i18n.getString("AccountLogin.navDesc","Logout");
        } else
        if (privLabel.getBooleanProperty(PrivateLabel.PROP_AccountLogin_showLoginLink,true)) {
            return i18n.getString("AccountLogin.navDesc.login","Login");
        } else {
            return super._getNavigationDescription(reqState,"");
        }
    }

    public String getNavigationTab(RequestProperties reqState)
    {
        PrivateLabel privLabel = reqState.getPrivateLabel();
        I18N i18n = privLabel.getI18N(AccountLogin.class);
        return i18n.getString("AccountLogin.navTab","Logout");
    }

    // ------------------------------------------------------------------------

    public void writePage(
        final RequestProperties reqState,
        String pageMsg)
        throws IOException
    {
        final PrivateLabel privLabel = reqState.getPrivateLabel();
        final I18N i18n = privLabel.getI18N(AccountLogin.class);

        /* Style */
        HTMLOutput HTML_CSS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                String cssDir = AccountLogin.this.getCssDirectory(); 
                WebPageAdaptor.writeCssLink(out, reqState, "AccountLogin.css", cssDir);
            }
        };

        /* write frame */
        HTMLOutput HTML_CONTENT = new HTMLOutput(CSS_ACCOUNT_LOGIN, pageMsg) {
            public void write(PrintWriter out) throws IOException {
                // baseURL
                URIArg  baseURI    = MakeURL(Track.BASE_URI(),null,null,null);
                HttpServletRequest req = reqState.getHttpServletRequest();
                String rtpArg      = (req != null)? req.getParameter(AttributeTools.ATTR_RTP) : null;
                if (!StringTools.isBlank(rtpArg)) { baseURI.addArg(AttributeTools.ATTR_RTP,rtpArg); }
                String  baseURL    = EncodeURL(reqState, baseURI);
                String  accountID  = AttributeTools.getRequestString(req, Constants.PARM_ACCOUNT, "");
                String  userID     = AttributeTools.getRequestString(req, Constants.PARM_USER   , "");
                // other args
                String  newURL     = reqState.getPrivateLabel().hasWebPage(PAGE_ACCOUNT_NEW )? 
                    //EncodeMakeURL(reqState,Track.BASE_URI(),PAGE_ACCOUNT_NEW ) : null;
                    privLabel.getWebPageURL(reqState,PAGE_ACCOUNT_NEW) : null;
                String  forgotURL  = reqState.getPrivateLabel().hasWebPage(PAGE_PASSWD_EMAIL)? 
                    //EncodeMakeURL(reqState,Track.BASE_URI(),PAGE_PASSWD_EMAIL) : null;
                    privLabel.getWebPageURL(reqState,PAGE_PASSWD_EMAIL) : null;
                boolean acctLogin  = reqState.getPrivateLabel().getAccountLogin();
                boolean userLogin  = reqState.getPrivateLabel().getUserLogin();
                boolean emailLogin = reqState.getPrivateLabel().getAllowEmailLogin();
                boolean showDemo   = reqState.getEnableDemo();
                String  target     = reqState.getPageFrameContentOnly()? "_self" : "_top";
                boolean loginOK    = privLabel.getBooleanProperty(BasicPrivateLabelLoader.ATTR_allowLogin, true);
                String  ro         = loginOK? "" : "readonly";
                // ----------------------------------
                // Basic login input form:
                //  <form name="login" method="post" action="http://track.example.com:8080/track/Track" target="_top">
                //      Account:  <input name="account"  type="text"     size='20' maxlength='32'> <br>
                //      User:     <input name="user"     type="text"     size='20' maxlength='32'> <br>
                //      Password: <input name="password" type="password" size='20' maxlength='32'> <br>
                //      <input type="submit" name="submit" value="Login">
                //  </form>
                out.println("<form name='"+FORM_LOGIN+"' method='post' action='"+baseURL+"' target='"+target+"'>");
                out.println("  <span style='font-size:11pt'>"+i18n.getString("AccountLogin.enterLogin","Enter your Login ID and Password")+"</span>");
                out.println("  <hr>");
                out.println("  <center>"); // necessary because "text-align:center" doesn't center the following table
                out.println("  <table>");
                if (acctLogin) {
                    out.println("  <tr><td>"+i18n.getString("AccountLogin.account","Account:")+"</td><td><input class='"+CommonServlet.CSS_TEXT_INPUT+"' type='text' "+ro+" name='"+Constants.PARM_ACCOUNT+"' value='"+accountID+"' size='20' maxlength='32'></td></tr>");
                }
                if (userLogin && emailLogin) {
                    out.println("  <tr><td>"+i18n.getString("AccountLogin.userEmail","User/EMail:")+"</td><td><input class='"+CommonServlet.CSS_TEXT_INPUT+"' type='text' "+ro+" name='"+Constants.PARM_USER+"' value='"+userID+"' size='30' maxlength='40'></td></tr>");
                } else
                if (userLogin) {
                    out.println("  <tr><td>"+i18n.getString("AccountLogin.user","User:")+"</td><td><input class='"+CommonServlet.CSS_TEXT_INPUT+"' type='text' "+ro+" name='"+Constants.PARM_USER+"' value='"+userID+"' size='20' maxlength='32'></td></tr>");
                } else
                if (emailLogin) {
                    out.println("  <tr><td>"+i18n.getString("AccountLogin.email","EMail:")+"</td><td><input class='"+CommonServlet.CSS_TEXT_INPUT+"' type='text' "+ro+" name='"+Constants.PARM_USEREMAIL+"' value='"+userID+"' size='30' maxlength='40'></td></tr>");
                }
                out.println("  <tr><td>"+i18n.getString("AccountLogin.password","Password:")+"</td><td><input class='"+CommonServlet.CSS_TEXT_INPUT+"' type='password' "+ro+" name='"+Constants.PARM_PASSWORD+"' value='' size='20' maxlength='32'></td></tr>");
                out.println("  </table>");
                out.println("  </center>");
                out.println("  <input type='submit' name='submit' value='"+i18n.getString("AccountLogin.login","Login")+"'>");
                out.println("  <br/><span style='font-size:8pt'><i>"+i18n.getString("AccountLogin.cookiesJavaScript","(Cookies and JavaScript must be enabled)")+"</i></span>");
                out.println("</form>");
                if (forgotURL != null) {
                    out.println("<br/><span style='font-size:8pt'><i><a href='"+forgotURL+"'>"+i18n.getString("AccountLogin.forgotPassword","Forgot your password?")+"</a></i></span>");
                }
                if (showDemo) {
                    out.println("<hr/>");
                    out.println("<form name='"+FORM_DEMO+"' method='post' action='"+baseURL+"' target='"+target+"'>");
                    out.println("  <input type='hidden' name='"+Constants.PARM_ACCOUNT  +"' value='"+reqState.getDemoAccountID()+"'/>");
                    out.println("  <input type='hidden' name='"+Constants.PARM_USER     +"' value=''/>");
                    out.println("  <input type='hidden' name='"+Constants.PARM_PASSWORD +"' value=''/>");
                    out.println("  <span style='font-size:9pt'>"+i18n.getString("AccountLogin.freeDemo","Or click here for a free demonstration")+"</span><br>");
                    out.println("  <input type='submit' name='submit' value='"+i18n.getString("AccountLogin.demo","Demo")+"'>");
                    out.println("</form>");
                }
                if (newURL != null) {
                    out.println("<hr/><span style='font-size:8pt'><i><a href='"+newURL+"'>"+i18n.getString("AccountLogin.freeAccount","Sign up for a free account")+"</a></i></span>");
                }
            }
        };

        /* write frame */
        String onload = (!StringTools.isBlank(pageMsg) && reqState._isLoginErrorAlert())? JS_alert(true,pageMsg) : null;
        CommonServlet.writePageFrame(
            reqState,
            onload,null,                // onLoad/onUnload
            HTML_CSS,                   // Style sheets
            HTMLOutput.NOOP,            // JavaScript
            null,                       // Navigation
            HTML_CONTENT);              // Content

    }

    // ------------------------------------------------------------------------

}
