#!/bin/bash

#    Copyright (c) 2018 Grzegorz Stepien
#
#    This file and its contents are provided under the BSD 3-clause license.
#    For more details, see './LICENSE.md'
#    (where '.' represents this program's root directory).

# Configures and executes a transceiver framework instance based on
# the JSON driver file at './driver_config.json' where '.' represents the folder
# containing this script.

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
PRINTF=$(which printf)
SED=$(which sed)
HEAD=$(which head)

if [ -z "$DIRNAME" -o -z "$READLINK" -o -z "$SHELL" -o -z "$PRINTF" -o -z "$SED" -o -z "$HEAD" ]; then
  error_msg "At least one required tool is missing. See \"Check tool availability\" paragraph of \"$0\" for more details."
  exit 1
fi

# Folder containing this script
DRIVER_CONFIG_FOLDER=$("$DIRNAME" "$0")
DRIVER_CONFIG_FOLDER=$("$READLINK" -e "$DRIVER_CONFIG_FOLDER")

# Transceiver framework's root folder - adapt this if you move this script somewhere else!
ROOT_FOLDER=$("$READLINK" -e "${DRIVER_CONFIG_FOLDER}/../../..") 

# Driver config
DRIVER_CONFIG="${DRIVER_CONFIG_FOLDER}/driver_config.json"

# Starter script
RUN_SCRIPT="${ROOT_FOLDER}/transceiver_framework/transceiver_framework.sh"

# Composed commands to execute
EXEC_COMMAND="\"$SHELL\" \"$RUN_SCRIPT\" \"$DRIVER_CONFIG\""

# Execute commands
eval "$EXEC_COMMAND"
