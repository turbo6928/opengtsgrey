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
//     -Changes to facilitate easier rule checking and action execution.
//  2007/07/27  Martin D. Flynn
//     -Changed 'executeStatusCodeRules' to 'executeRules'
//     -Added 'executeSelector'
//  2009/10/02  Martin D. Flynn
//     -Added ACTION_SAVE_LAST action
//     -"executeSelector" and "executeRules" now return the executed action-mask,
//      instead of just true/false.
// ----------------------------------------------------------------------------
package org.opengts.db;

import java.lang.*;
import java.util.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;

import org.opengts.db.tables.*;

public interface RuleFactory
{

    // ------------------------------------------------------------------------

    public static final String PROP_rule_workHours_         = "rule.workHours.";

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Notification Action

    public static final int    ACTION_NONE                  = 0x000000; // no action

    public static final int    ACTION_NOTIFY_MASK           = 0x0000FF; // 
    public static final int    ACTION_NOTIFY_ACCOUNT        = 0x000001; // send email to Account 'notifyEmail'
    public static final int    ACTION_NOTIFY_DEVICE         = 0x000002; // send email to Device 'notifyEmail'
    public static final int    ACTION_NOTIFY_RULE           = 0x000004; // send email to Rule 'notifyEmail'
    
    public static final int    ACTION_VIA_MASK              = 0xFFFF00; // 
    public static final int    ACTION_VIA_EMAIL             = 0x000100; // notify via SendMail (default)
    public static final int    ACTION_VIA_QUEUE             = 0x000200; // notify via Notify Queue
    public static final int    ACTION_VIA_LISTENER          = 0x000400; // notify via callback listener

    public static final int    ACTION_SAVE_LAST             = 0x010000; // save last notification 

    public static final int    ACTION_NOTIFY_ALL =  // 0x0007
        ACTION_NOTIFY_ACCOUNT   |
        ACTION_NOTIFY_DEVICE    |
        ACTION_NOTIFY_RULE;

    public static final int    ACTION_EMAIL_ALL =   // 0x0107
        ACTION_VIA_EMAIL        |
        ACTION_NOTIFY_ALL;

    public static final int    ACTION_DEFAULT =     // 0x1507
        ACTION_EMAIL_ALL        |
        ACTION_VIA_LISTENER     |
        ACTION_SAVE_LAST;

    public enum NotifyAction implements EnumTools.BitMask, EnumTools.StringLocale {
        NONE         ((long)ACTION_NONE          ,I18N.getString(RuleFactory.class,"RuleFactory.notifyAction.none"         ,"None"      )),
        // ---
        NOTIFY_ACCT  ((long)ACTION_NOTIFY_ACCOUNT,I18N.getString(RuleFactory.class,"RuleFactory.notifyAction.notifyAccount","Account"   )),
        NOTIFY_DEV   ((long)ACTION_NOTIFY_DEVICE ,I18N.getString(RuleFactory.class,"RuleFactory.notifyAction.notifyDevice" ,"Device"    )),
        NOTIFY_RULE  ((long)ACTION_NOTIFY_RULE   ,I18N.getString(RuleFactory.class,"RuleFactory.notifyAction.notifyRule"   ,"Rule"      )),
        // ---
        VIA_EMAIL    ((long)ACTION_VIA_EMAIL     ,I18N.getString(RuleFactory.class,"RuleFactory.notifyAction.viaEMail"     ,"EMail"     )),
        VIA_QUEUE    ((long)ACTION_VIA_QUEUE     ,I18N.getString(RuleFactory.class,"RuleFactory.notifyAction.viaQueue"     ,"Queue"     )),
        VIA_LISTENER ((long)ACTION_VIA_LISTENER  ,I18N.getString(RuleFactory.class,"RuleFactory.notifyAction.viaListener"  ,"Listener"  )),
        // ---
        SAVE_LAST    ((long)ACTION_SAVE_LAST     ,I18N.getString(RuleFactory.class,"RuleFactory.notifyAction.saveLast"     ,"SaveLast"  ));
        // ---
        private long        vv = 0L;
        private I18N.Text   aa = null;
        NotifyAction(long v, I18N.Text a)           { vv=v; aa=a; }
        public long    getLongValue()               { return vv; }
        public String  toString()                   { return aa.toString(); }
        public String  toString(Locale loc)         { return aa.toString(loc); }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    public static final int     PRIORITY_UNDEFINED          = 0;
    public static final int     PRIORITY_HIGH               = 1;
    public static final int     PRIORITY_MEDIUM             = 5;
    public static final int     PRIORITY_LOW                = 9;

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* return this RuleFactory name */
    public String getName();
    
    /* return this RuleFactory version */
    public String getVersion();

    // ------------------------------------------------------------------------

    /* return an array of available identifiers */
    public java.util.List<String> getIdentifierNames();

    /* return the function description */
    public String getIdentifierDescription(String idName);

    // ------------------------------------------------------------------------

    /* return an array of available functions */
    public java.util.List<String> getFunctionNames();

    /* return the function 'usage' String */
    public String getFunctionUsage(String ftnName);

    /* return the function description */
    public String getFunctionDescription(String ftnName);

    // ------------------------------------------------------------------------

    /* return true if the specified selector is syntactically correct */
    public boolean checkSelectorSyntax(String selector); 

    /* return true if the specified 'event' matches the specified 'selector' */
    public boolean isSelectorMatch(String selector, EventData event);

    /* if the 'event' matches the 'selector', then execute the default rule action */
    public int executeSelector(String selector, EventData event);

    /* if the 'event' matches the 'selector', then execute the default rule action */
    public Object evaluateSelector(String selector, EventData event)
        throws RuleParseException;

    /* test and execute all matching rules for the specified 'event' */
    public int executeRules(EventData event);
    
    // ------------------------------------------------------------------------

}
