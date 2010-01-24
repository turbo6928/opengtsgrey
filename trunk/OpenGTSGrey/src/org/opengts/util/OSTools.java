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
// Description:
//  General OS specific tools
// ----------------------------------------------------------------------------
// Change History:
//  2006/03/26  Martin D. Flynn
//     -Initial release
//  2006/06/30  Martin D. Flynn
//     -Repackaged
//  2008/06/20  Martin D. Flynn
//     -Added method 'getProcessID()'
// ----------------------------------------------------------------------------
package org.opengts.util;

import java.util.*;
import java.io.*;

public class OSTools
{

    // ------------------------------------------------------------------------
    // OS and JVM specific tools
    // ------------------------------------------------------------------------

    private static final int OS_INITIALIZE          = -1;
    public  static final int OS_TYPE_MASK           = 0x00FF00;
    public  static final int OS_SUBTYPE_MASK        = 0x0000FF;

    public  static final int OS_UNKNOWN             = 0x000000;
    public  static final int OS_LINUX               = 0x000100;
    public  static final int OS_MACOSX              = 0x000200;
    public  static final int OS_WINDOWS             = 0x000300;
    public  static final int OS_WINDOWS_XP          = 0x000001;
    public  static final int OS_WINDOWS_9X          = 0x000002;
    public  static final int OS_WINDOWS_CYGWIN      = 0x000010;

    private static       int OSType                 = OS_INITIALIZE;

    /**
    *** Returns the known OS type as an integer bitmask
    *** @return The OS type
    **/
    public static int getOSType()
    {
        if (OSType == OS_INITIALIZE) {
            String osName = System.getProperty("os.name").toLowerCase();
            //Print.logInfo("OS: " + osName);
            if (osName.startsWith("windows")) {
                OSType = OS_WINDOWS;
                if (osName.startsWith("windows xp")) {
                    OSType |= OS_WINDOWS_XP;
                } else
                if (osName.startsWith("windows 9") || osName.startsWith("windows m")) {
                    OSType |= OS_WINDOWS_9X;
                }
            } else
            if (osName.startsWith("mac")) {
                // "Max OS X"
                OSType = OS_MACOSX;
            } else
            if (File.separatorChar == '/') {
                // "Linux"
                OSType = OS_LINUX;
            } else {
                OSType = OS_UNKNOWN;
            }
        }
        return OSType;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Returns true if the OS is unknown
    *** @return True if the OS is unknown
    **/
    public static boolean isUnknown()
    {
        return (getOSType() == OS_UNKNOWN);
    }

    /**
    *** Returns true if the OS is the specified type
    *** @return True if the OS is the specified type
    **/
    public static boolean isOSType(int type)
    {
        int osType = getOSType();
        return ((osType & OS_TYPE_MASK) == type);
    }

    /**
    *** Returns true if the OS is the specified type
    *** @return True if the OS is the specified type
    **/
    public static boolean isOSType(int type, int subType)
    {
        int osType = getOSType();
        if ((osType & OS_TYPE_MASK) != type) {
            return false;
        } else {
            return ((osType & OS_SUBTYPE_MASK & subType) != 0);
        }
    }

    /**
    *** Returns true if the OS is a flavor of Windows
    *** @return True if the OS is a flavor of Windows
    **/
    public static boolean isWindows()
    {
        return isOSType(OS_WINDOWS);
    }

    /**
    *** Returns true if the OS is Windows XP
    *** @return True if the OS is Windows XP
    **/
    public static boolean isWindowsXP()
    {
        return isOSType(OS_WINDOWS, OS_WINDOWS_XP);
    }

    /**
    *** Returns true if the OS is Windows 95/98
    *** @return True if the OS is Windows 95/98
    **/
    public static boolean isWindows9X()
    {
        return isOSType(OS_WINDOWS, OS_WINDOWS_9X);
    }

    /**
    *** Returns true if the OS is Unix/Linux
    *** @return True if the OS is Unix/Linux
    **/
    public static boolean isLinux()
    {
        return isOSType(OS_LINUX);
    }

    /**
    *** Returns true if the OS is Apple Mac OS X
    *** @return True if the OS is Apple Mac OS X
    **/
    public static boolean isMacOSX()
    {
        return isOSType(OS_MACOSX);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Returns true if this implementation has a broken 'toFront' Swing implementation.<br>
    *** (may only be applicable on Java v1.4.2)
    *** @return True if this implementation has a broken 'toFront' Swing implementation.
    **/
    public static boolean isBrokenToFront()
    {
        return isWindows();
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    public static final String PROPERTY_JAVA_HOME                   = "java.home";
    public static final String PROPERTY_JAVA_VENDOR                 = "java.vendor";
    public static final String PROPERTY_JAVA_SPECIFICATION_VERSION  = "java.specification.version";

    /**
    *** Returns true if executed from a Sun Microsystems JVM.
    *** @return True is executed from a Sun Microsystems JVM.
    **/
    public static boolean isSunJava()
    {
        String propVal = System.getProperty(PROPERTY_JAVA_VENDOR); // "Sun Microsystems Inc."
        if ((propVal == null) || (propVal.indexOf("Sun Microsystems") < 0)) {
            return false;
        } else {
            return true;
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Gets the class of the caller at the specified frame index
    *** @param frame The frame index
    *** @return The calling class
    **/
    @SuppressWarnings("proprietary")  // <-- does not work to supress the "Sun proprietary API" warning
    private static Class _getCallerClass(int frame)
        throws Throwable
    {
        return sun.reflect.Reflection.getCallerClass(frame + 1); // <== ignore any warnings
    }

    /**
    *** Gets the class of the caller at the specified frame index
    *** @param frame The frame index
    *** @return The calling class
    **/
    public static Class getCallerClass(int frame)
    {
        try {
            // sun.reflect.Reflection.getCallerClass(0) == sun.reflect.Reflection
            // sun.reflect.Reflection.getCallerClass(1) == OSTools
            Class clz = OSTools._getCallerClass(frame + 1);
            //Print._println("" + (frame + 1) + "] class " + StringTools.className(clz));
            return clz;
        } catch (Throwable th) { // ClassNotFoundException
            // This can occur when the code has been compiled with the Sun Microsystems version
            // of Java, but is executed with the GNU version of Java (or other non-Sun version).
            Print.logException("Sun Microsystems version of Java is not in use", th);
            return null;
        }
    }

    /**
    *** Returns true if 'sun.reflect.Reflection' is present in the runtime libraries.<br>
    *** (will return true when running with the Sun Microsystems version of Java)
    *** @return True if 'getCallerClass' is available.
    **/
    public static boolean hasGetCallerClass()
    {
        try {
            OSTools._getCallerClass(0);
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    /**
    *** Prints the class of the caller (debug purposes only)
    **/
    public static void printCallerClasses()
    {
        try {
            for (int i = 0;; i++) {
                Class clz = OSTools._getCallerClass(i);
                Print.logInfo("" + i + "] class " + StringTools.className(clz));
                if (clz == null) { break; }
            }
        } catch (Throwable th) { // ClassNotFoundException
            // This can occur when the code has been compiled with the Sun Microsystems version
            // of Java, but is executed with the GNU version of Java.
            Print.logException("Sun Microsystems version of Java is not in use", th);
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Gets the Process-ID of this JVM invocation.<br>
    *** IMPORTANT: This implementation relies on a "convention", rather that a documented method
    *** of obtaining the process-id of this JVM within the OS.  <b>Caveat Emptor!</b><br>
    *** (On Windows, this returns the 'WINPID' which is probably useless anyway)
    *** @return The Process-ID
    **/
    public static int getProcessID()
    {
        // References:
        //  - http://blog.igorminar.com/2007/03/how-java-application-can-discover-its.html
        if (OSTools.isSunJava()) {
            try {
                // by convention, returns "<PID>@<host>" (until something changes, and it doesn't)
                String n = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
                int pid = StringTools.parseInt(n,-1); // parse PID
                return pid;
            } catch (Throwable th) {
                Print.logException("Unable to obtain Process ID", th);
                return -1;
            }
        } else {
            return -1;
        }
    }

    /* this does not work on Windows (and seems to return the wrong parent PID on Linux) */
    private static int _getProcessID()
    {
        try {
            String cmd[] = new String[] { "bash", "-c", "echo $PPID" };
            Process ppidExec = Runtime.getRuntime().exec(cmd);
            BufferedReader ppidReader = new BufferedReader(new InputStreamReader(ppidExec.getInputStream()));
            StringBuffer sb = new StringBuffer();
            for (;;) {
                String line = ppidReader.readLine();
                if (line == null) { break; }
                sb.append(StringTools.trim(line));
            }
            int pid = StringTools.parseInt(sb.toString(),-1);
            int exitVal = ppidExec.waitFor();
            Print.logInfo("Exit value: %d [%s]", exitVal, sb.toString());
            ppidReader.close();
            return pid;
        } catch (Throwable th) {
            Print.logException("Unable to obtain PID", th);
            return -1;
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Returns a Java command set up to be executed by Runtime.getRuntime().exec(...)
    *** @param classpath The classpath 
    *** @param className The main Java class name
    *** @param args The command line arguments
    *** @return A command to call and it's arguments
    **/
    public static String[] createJavaCommand(String classpath[], String className, String args[])
    {
        java.util.List<String> execCmd = new Vector<String>();
        execCmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        execCmd.add("-classpath");
        if (ListTools.isEmpty(classpath)) {
            execCmd.add(System.getProperty("java.class.path"));
        } else {
            StringBuffer sb = new StringBuffer();
            for (String p : classpath) {
                if (sb.length() > 0) { sb.append(File.pathSeparator); }
                sb.append(p);
            }
            execCmd.add(sb.toString());
        }
        execCmd.add(className);
        if (!ListTools.isEmpty(args)) {
            for (String a : args) {
                execCmd.add(a);
            }
        }
        return execCmd.toArray(new String[execCmd.size()]);
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Main entry point for testing/debugging
    *** @param argv Comand-line arguments
    **/
    public static void main(String argv[])
    {
        RTConfig.setCommandLineArgs(argv);
        System.out.println("OS Type ...");
        Print.sysPrintln("Is Windows  : " + isWindows());
        Print.sysPrintln("Is Windows9X: " + isWindows9X());
        Print.sysPrintln("Is WindowsXP: " + isWindowsXP());
        Print.sysPrintln("Is Linux    : " + isLinux());
        Print.sysPrintln("Is MacOSX   : " + isMacOSX());
        Print.sysPrintln("PID #1      : " + getProcessID());
        Print.sysPrintln("PID #2      : " + _getProcessID());
        Runtime rt = Runtime.getRuntime();
        Print.sysPrintln("Total Mem   : " + rt.totalMemory()/(1024.0*1024.0));
        Print.sysPrintln("Max Mem     : " + rt.maxMemory()/(1024.0*1024.0));
        Print.sysPrintln("Free Mem    : " + rt.freeMemory()/(1024.0*1024.0));
    }

}
