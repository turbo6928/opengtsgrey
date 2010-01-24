#!/bin/bash
# -----------------------------------------------------------------------------
# Project: OpenGTS - Open GPS Tracking System
# URL    : http://www.opengts.org
# File   : rgTest.sh
# -----------------------------------------------------------------------------
# Description:
#   This command-line utility allows testing of the ReverseGeocodeProvider for a
#   specific Account or PrivateLabel name.
# -----------------------------------------------------------------------------
if [ "${GTS_HOME}" = "" ]; then 
    echo "!!! ERROR: GTS_HOME not defined !!!"
    exit 99
fi
# -----------------------------------------------------------------------------

QUIET='-quiet'
${GTS_HOME}/bin/exeJava ${QUIET} org.opengts.geocoder.ReverseGeocodeProviderAdapter $*

# ---

