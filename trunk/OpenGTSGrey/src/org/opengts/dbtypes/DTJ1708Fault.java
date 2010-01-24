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
//  2007/02/21  Martin D. Flynn
//      Initial release
// ----------------------------------------------------------------------------
package org.opengts.dbtypes;

import java.lang.*;
import java.util.*;
import java.math.*;
import java.io.*;
import java.sql.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.db.DBConfig;

public class DTJ1708Fault
    extends DBFieldType
{
    
    // ------------------------------------------------------------------------
    
    public static final String NAME_MID         = "MID";
    public static final String NAME_PID         = "PID";
    public static final String NAME_SID         = "SID";
    public static final String NAME_FMI         = "FMI";

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // 0xMMPPPPFF
    //   MM       = 0x80
    //     PPPP   = PID/SID (0x8000 if SID)
    //         FF = FMI

    /* encode J1708 MID,PID/SID,FMI into long value */
    public static long EncodeJ1708Fault(int mid, boolean isSid, int pidSid, int fmi)
    {
        long faultCode = 0L;
        faultCode |= ((mid & 0x00FFL) << 24);
        if (isSid) { faultCode |= 0x00800000L; }
        faultCode |= ((pidSid & 0x0FFFL) << 8);
        faultCode |= (fmi & 0xFFL);
        return faultCode;
    }

    /* decode MID from long value */
    public static int DecodeJ1708FaultMid(long fault)
    {
        return (int)((fault >> 24) & 0xFFL);
    }
    
    /* return true if long value represents a SID */
    public static boolean IsJ1708FaultSid(long fault)
    {
        return ((fault & 0x00800000L) != 0L);
    }

    /* decode PID/SID component from long value */
    public static int DecodeJ1708FaultPidSid(long fault)
    {
        return (int)((fault >> 8) & 0x0FFFL);
    }

    /* decode PID component from long value */
    public static int DecodeJ1708FaultPid(long fault)
    {
        return DTJ1708Fault.IsJ1708FaultSid(fault)? -1 : DecodeJ1708FaultPidSid(fault);
    }

    /* decode SID component from long value */
    public static int DecodeJ1708FaultSid(long fault)
    {
        return DTJ1708Fault.IsJ1708FaultSid(fault)? DecodeJ1708FaultPidSid(fault) : -1;
    }

    /* decode FMI component from long value */
    public static int DecodeJ1708FaultFmi(long fault)
    {
        return (int)(fault & 0xFFL);
    }

    /* return string representation of fault code */
    public static String GetJ1708FaultString(long fault, Locale locale)
    {
        // SID: "128/s123/1"
        // PID: "128/123/1"
        if (fault == 0L) {
            return "---";
        } else {
            StringBuffer sb = new StringBuffer();
            sb.append(DTJ1708Fault.DecodeJ1708FaultMid(fault));                     // MID
            sb.append("/");
            if (DTJ1708Fault.IsJ1708FaultSid(fault)) {
                sb.append("s").append(DTJ1708Fault.DecodeJ1708FaultPidSid(fault));  // SID "128/s123/1"
            } else {
                sb.append(DTJ1708Fault.DecodeJ1708FaultPidSid(fault));              // PID "128/123/1"
            }
            sb.append("/");
            sb.append(DTJ1708Fault.DecodeJ1708FaultFmi(fault));                     // FMI
            return sb.toString();
        }
    }

    /* return J1708 fault type */
    public static String GetJ1708FaultType(long fault)
    {
        if (fault == 0L) {
            return "";
        } else
        if (DTJ1708Fault.IsJ1708FaultSid(fault)) {
            return NAME_MID + "/" + NAME_SID + "/" + NAME_FMI;
        } else {
            return NAME_MID + "/" + NAME_PID + "/" + NAME_FMI;
        }
    }

    // ------------------------------------------------------------------------

    private static boolean                  j1587DidInit = false;
    private static J1587DescriptionProvider j1587Descr   = null;

    public interface J1587DescriptionProvider
    {
        public Properties getJ1587Descriptions(long fault);
    }

    public static boolean InitJ1587DescriptionProvider()
    {
        if (!j1587DidInit) {
            j1587DidInit = true;
            try {
                MethodAction ma = new MethodAction(DBConfig.PACKAGE_OPT_DB_ + "J1587","GetJ1587DescriptionProvider");
                j1587Descr = (J1587DescriptionProvider)ma.invoke();
                Print.logDebug("J1587 Description Provider installed ...");
            } catch (Throwable th) { // ClassNotFoundException
                //Print.logException("J1587 Description Provider NOT installed!", th);
            }
        }
        return (j1587Descr != null);
    }

    public static String GetJ1708FaultDescription(long fault, Locale locale)
    {
        if (fault == 0L) {
            return "";
        } else {
            String fmt = "000";
            StringBuffer sb = new StringBuffer();
            // get J1587 descriptions
            int     mid    = DTJ1708Fault.DecodeJ1708FaultMid(fault);     // MID
            boolean isSid  = DTJ1708Fault.IsJ1708FaultSid(fault);
            int     pidSid = DTJ1708Fault.DecodeJ1708FaultPidSid(fault);  // PID|SID "128/[s]123/1"
            int     fmi    = DTJ1708Fault.DecodeJ1708FaultFmi(fault);     // FMI
            Properties p   = (j1587Descr != null)? j1587Descr.getJ1587Descriptions(fault) : new Properties();
            // MID
            sb.append(NAME_MID + " [" + StringTools.format(mid,fmt) + "] : " + p.getProperty(NAME_MID,"") + "\n");
            // PID/SID
            if (isSid) {
                sb.append(NAME_SID + " [" + StringTools.format(pidSid,fmt) + "] : " + p.getProperty(NAME_SID,"") + "\n");
            } else {
                sb.append(NAME_PID + " [" + StringTools.format(pidSid,fmt) + "] : " + p.getProperty(NAME_PID,"") + "\n");
            }
            // FMI
            sb.append(NAME_FMI + " [" + StringTools.format(fmi,fmt) + "] : " + p.getProperty(NAME_FMI,"") + "\n");
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private long    faultCode   = 0L;

    public DTJ1708Fault(int mid, boolean isSid, int pidSid, int fmi)
    {
        this.faultCode = DTJ1708Fault.EncodeJ1708Fault(mid, isSid, pidSid, fmi);
    }

    public DTJ1708Fault(long faultCode)
    {
        this.faultCode = faultCode;
    }

    public DTJ1708Fault(ResultSet rs, String fldName)
        throws SQLException
    {
        super(rs, fldName);
        // set to default value if 'rs' is null
        this.faultCode = (rs != null)? rs.getLong(fldName) : 0L;
    }

    // ------------------------------------------------------------------------

    /* return fault code */
    public long getFaultCode()
    {
        return this.faultCode;
    }

    /* return multi-line description */
    public String getDescription()
    {
        return DTJ1708Fault.GetJ1708FaultDescription(this.getFaultCode(),null);
    }
    
    // ------------------------------------------------------------------------

    /* return MID */
    public int getMid()
    {
        return DecodeJ1708FaultMid(this.getFaultCode());
    }

    /* return true if SID */
    public boolean isSid()
    {
        return IsJ1708FaultSid(this.getFaultCode());
    }

    /* return PID/SID */
    public int getPidSid()
    {
        return DecodeJ1708FaultPidSid(this.getFaultCode());
    }

    /* return FMI */
    public int getFmi()
    {
        return DecodeJ1708FaultFmi(this.getFaultCode());
    }

    // ------------------------------------------------------------------------

    public Object getObject()
    {
        return new Long(this.getFaultCode());
    }

    public String toString()
    {
        return "0x" + StringTools.toHexString(this.getFaultCode());
    }

    public boolean equals(Object other)
    {
        if (other instanceof DTJ1708Fault) {
            DTJ1708Fault jf = (DTJ1708Fault)other;
            return (this.getFaultCode() == jf.getFaultCode());
        } else {
            return false;
        }
    }
    
    // ------------------------------------------------------------------------

}
