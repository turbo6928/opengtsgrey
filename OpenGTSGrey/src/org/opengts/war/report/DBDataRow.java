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
// ----------------------------------------------------------------------------
package org.opengts.war.report;

import java.io.*;

import org.opengts.war.tools.*;

public interface DBDataRow
    extends CSSRowClass
{
    
    // ------------------------------------------------------------------------

    public ReportData getReportData();
    
    public ReportColumn[] getReportColumns();
    public DataRowTemplate getDataRowTemplate();

    // ------------------------------------------------------------------------

    public Object getRowObject();

    public Object getDBValue(String fldName, int rowNdx, ReportColumn rptCol);

    // ------------------------------------------------------------------------

}
