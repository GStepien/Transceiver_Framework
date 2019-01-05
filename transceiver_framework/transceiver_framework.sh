#!/bin/bash

#    Copyright (c) 2018 Grzegorz Stepien
#
#    This file and its contents are provided under the BSD 3-clause license.
#    For more details, see './LICENSE.md'
#    (where '.' represents this program's root directory).

# Script expects the path to a JSON driver file as input. Configures and executes a 
# transceiver framework instance based on said file. 
#
# Let <name> be defined as follows:
# If a second argument is provided to the script, then <name> is set to that argument. Otherwise
# <name> is set to the name of the folder containing this script. 
#
# Compiles the java sources into a jar (via the command './install.sh "./<name>/pom.xml"') 
# if the latter is not already present at './<name>/<name>/<name>.jar' 
# (where '.' represents the project root folder).

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
SHELL=$(which bash)
if [ -z "$SHELL" ]; then
    SHELL=$(which sh)
fi
JAVA=$(which java)
SED=$(which sed)
HEAD=$(which head)
PRINTF=$(which printf)
TEE=$(which tee)
MKDIR=$(which mkdir)
BASENAME=$(which basename)

if [ -z "$DIRNAME" -o -z "$READLINK" -o -z "$SHELL" -o -z "$JAVA" -o -z "$SED" -o \
     -z "$HEAD" -o -z "$PRINTF" -o -z "$TEE" -o -z "$MKDIR" -o -z "$BASENAME"]; then
  error_msg "At least one required tool is missing. See \"Check tool availability\" paragraph of \"$0\" for more details."
  exit 1
fi

# Folder containing this script. Should be the child of transceiver framework's root folder - adapt this if you move this script somewhere else!
THIS_FOLDER=$("$DIRNAME" "$0")
THIS_FOLDER=$("$READLINK" -e "$THIS_FOLDER") 
THIS_FOLDER_NAME=$("$BASENAME" "$THIS_FOLDER")
ROOT_FOLDER=$("$READLINK" -e "$THIS_FOLDER/../")

# Folder containing the JSON driver file
DRIVER_CONFIG_FOLDER=$("$DIRNAME" "$1")
DRIVER_CONFIG_FOLDER=$("$READLINK" -e "$DRIVER_CONFIG_FOLDER")

# <name>
if [ $# -ge 2 ]; then
    NAME="$2"
else
    NAME="${THIS_FOLDER_NAME}"
fi

# Compile script
INSTALL_SCRIPT="${ROOT_FOLDER}/install.sh"
INSTALL_ARGS="\"${ROOT_FOLDER}/${NAME}/pom.xml\""

# Initial java memory pool size
JAVA_XMS='1g'
# Maximal java memory pool size
JAVA_XMX='2g'
# Resulting jar file name
JAR_FILE="${ROOT_FOLDER}/${NAME}/${NAME}/${NAME}.jar"

# VM arguments
JAVA_VM_ARGS="-Xms${JAVA_XMS} "\
"-Xmx${JAVA_XMX} "\
"-Djava.util.logging.manager=\"gs.tf.drivers.JSONClosedMultiTaskDriver\\\$ManualResetLogManager\" "\
"-jar \"${JAR_FILE}\""\

# Composed commands to execute
INSTALL_COMMAND="\"$SHELL\" \"$INSTALL_SCRIPT\" $INSTALL_ARGS"
RUN_COMMAND="\"$JAVA\" $JAVA_VM_ARGS \"$1\""

if [ -z "$1" ]; then
    error_msg "No JSON driver file provided as argument."
    exit 1
fi

# Compile first if necessary
if [ ! -f "$JAR_FILE" ]; then
    "$PRINTF" "Compiling java project via command: $INSTALL_COMMAND\n"
    eval "$INSTALL_COMMAND"

    if [ ! -f "$JAR_FILE" ]; then
        error_msg "Jar file not where expected after compilation."
        exit 1
    fi
fi

# Execute (from root directory) and log console output (stdout and stderr) to 'console_out.log' 
# in the folder containing the JSON driver file.
"$PRINTF" "Executing via command: $RUN_COMMAND\n"
"$MKDIR" -p "${DRIVER_CONFIG_FOLDER}/logs"
(cd "${ROOT_FOLDER}" && (eval "$RUN_COMMAND" 2>&1 | "$TEE" "${DRIVER_CONFIG_FOLDER}/logs/console_out.log"))
