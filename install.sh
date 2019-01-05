#!/bin/bash

#    Copyright (c) 2018 Grzegorz Stepien
#
#    This file and its contents are provided under the BSD 3-clause license.
#    For more details, see './LICENSE.md'
#    (where '.' represents this program's root directory).

# Script pulls newest version of this repository (via './git_pull_recursive.sh') and
# executes Maven's 'clean' and 'install' command. The latter is done using
# the pom.xml file the path of which is either provided via argument to this script or, if no
# argument is provided, is assumed to be located at './transceiver_framework/pom.xml'.
#
# If a pom.xml location is provided via argument, it must match the pattern './<name>/pom.xml', where
# '<name>' is some subfolder in the project root folder (represented by '.'). 
#
# Afterwards, a fat jar containing the project (as defined via the aforementioned pom.xml)
# and all its dependencies should be present at './<name>/<name>/<name>.jar'.

# Helper method for error printing
error_msg()
{
    TITLE="Unsuccessful execution of this script via: \"$0 $CONSOLE_PARAMS\""
    "$PRINTF" "ERROR: $TITLE\n\tMessage: $1\n"
}
CONSOLE_PARAMS=$@

# Check tool availability
DIRNAME=$(which dirname)
READLINK=$(which readlink)
BASENAME=$(which basename)
SHELL=$(which bash)
if [ -z "$SHELL" ]; then
    SHELL=$(which sh)
fi
MVN=$(which mvn)
PRINTF=$(which printf)

if [ -z "$DIRNAME" -o -z "$READLINK" -o -z "$SHELL" -o -z "$MVN" -o -z "$PRINTF" -o -z "$BASENAME" ]; then
  error_msg "At least one required tool is missing. See \"Check tool availability\" paragraph of \"$0\" for more details."
  exit 1
fi

# Project root folder
ROOT_FOLDER=$("$DIRNAME" "$0")
ROOT_FOLDER=$("$READLINK" -e "$ROOT_FOLDER")

# Full pom.xml path:
if [ $# -ge 1 ]; then
    POM_PATH=$("$READLINK" -e "$1")
else
    POM_PATH=$("$READLINK" -e "${ROOT_FOLDER}/transceiver_framework/pom.xml")
fi

# Check if pom.xml path matches pattern './<name>/pom.xml'
POM_FOLDER=$("$DIRNAME" "$POM_PATH")
NAME=$("$BASENAME" "$POM_FOLDER")
if [ $("$BASENAME" "$POM_PATH") != "pom.xml" -o \
     $("$DIRNAME" "$POM_FOLDER") != "$ROOT_FOLDER" -o \
     -z "$NAME" ]; then
    error_msg "Inconsistent folder structure."
    exit 1
fi

MVN_ARGS="-f \"${POM_PATH}\"" 

# Actual git pull script.
PULL_SCRIPT="${ROOT_FOLDER}/git_pull_recursive.sh"

# Composed commands to execute
PULL_COMMAND="\"$SHELL\" \"$PULL_SCRIPT\""
MVN_CLEAN="\"$MVN\" $MVN_ARGS clean"
MVN_INSTALL="\"$MVN\" $MVN_ARGS install"

# Execute commands
eval "$PULL_COMMAND"
eval "$MVN_CLEAN"
eval "$MVN_INSTALL"

JAR_FILE="${POM_FOLDER}/${NAME}/${NAME}.jar"
if [ ! -f "$JAR_FILE" ]; then
    error_msg "Jar file not where expected after compilation: $JAR_FILE"
    exit 1
fi
