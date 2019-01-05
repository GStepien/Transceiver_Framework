#!/bin/bash

# MDP: A motif detector and predictor.
#
#    Copyright (c) 2018 Grzegorz Stepien
#
#    This program is free software: you can redistribute it and/or modify
#    it under the terms of the GNU General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU General Public License for more details.
#
#    You should have received a copy of the GNU General Public License
#    along with this program.  If not, see <https://www.gnu.org/licenses/>.
# 
#    For more details, see './LICENSE.md'
#    (where '.' represents this program's root directory).

# Script expects the path to a JSON driver file as input. Starts a Rserve instance,
# executes command './transceiver_framework/transceiver_framework.sh <path_to_json_driver_file> "mdp"'.
# After the latter's execution finishes, this script terminates the previously started 
# Rserve instance.

# Helper method for error printing
error_msg()
{
    TITLE="Unsuccessful execution of this script via: \"$0 $CONSOLE_PARAMS\""
    "$PRINTF" "ERROR: $TITLE\n\tMessage: $1\n"
}
CONSOLE_PARAMS=$@

# Always execute the shutdown hook when exiting
shutdown_hook()
{
    if [ -n "$ROOT_FOLDER" ]; then
        # Shutdown Rserve server
        "$NOHUP" "$RSCRIPT" "${ROOT_FOLDER}/mdp/mdp/src/main/r/rserve_terminator.R" $PORT >> "${DRIVER_CONFIG_FOLDER}/logs/R_out.log"
    fi

    if [ -n "$DRIVER_CONFIG_FOLDER" ]; then
        # Delete temp folder
        "$RM" -r -f "${DRIVER_CONFIG_FOLDER}/tmp"
    fi
}
trap shutdown_hook EXIT

# Helper method for determining a free TCP port on the local machine
get_free_tcp_port()
{
    read PORT_LOWER PORT_UPPER < /proc/sys/net/ipv4/ip_local_port_range
    
    PORT=$("$COMM" -23 \
            <("$SEQ" $PORT_LOWER $PORT_UPPER | "$SORT") \
                <("$SS" -ant | "$AWK" 'NR>=2{print $5}' | "$REV" | "$CUT" -d ':' -f 1 | "$REV" | "$SORT" -u) | \
            "$SHUF" | "$HEAD" -n 1)

    if [ -z "$PORT" ]; then
        error_msg "No free TCP port could be found."
        exit 1
    fi
}

# Check tool availability
SHUF=$(which shuf)
SS=$(which ss)
GREP=$(which grep)

DIRNAME=$(which dirname)
READLINK=$(which readlink)
SHELL=$(which bash)
if [ -z "$SHELL" ]; then
    SHELL=$(which sh)
fi
MKDIR=$(which mkdir)
RM=$(which rm)
RSCRIPT=$(which Rscript)
SED=$(which sed)
HEAD=$(which head)
PRINTF=$(which printf)
NOHUP=$(which nohup)
COMM=$(which comm)
SEQ=$(which seq)
AWK=$(which awk)
REV=$(which rev)
CUT=$(which cut)
SORT=$(which sort)

if [ -z "$SHUF" -o -z "$SS" -o -z "$GREP" -o -z "$DIRNAME" -o -z "$READLINK" -o \
     -z "$SHELL" -o -z "$MKDIR" -o -z "$RM" -o -z "$RSCRIPT" -o -z "$SED" -o \
     -z "$HEAD" -o -z "$PRINTF" -o -z "$NOHUP" -o -z "$COMM" -o -z "$SEQ" -o \
     -z "$AWK" -o -z "$REV" -o -z "$CUT" -o -z "$SORT" ]; then
  error_msg "At least one required tool is missing. See \"Check tool availability\" paragraph of \"$0\" for more details."
  exit 1
fi

# Folder containing this script. Should be the child of transceiver framework's root folder - adapt this if you move this script somewhere else!
ROOT_FOLDER=$("$DIRNAME" "$0")
ROOT_FOLDER=$("$READLINK" -e "$ROOT_FOLDER/..") 

# Folder containing the JSON driver file
DRIVER_CONFIG_FOLDER=$("$DIRNAME" "$1")
DRIVER_CONFIG_FOLDER=$("$READLINK" -e "$DRIVER_CONFIG_FOLDER")

# Transceiver framework execution script
TF_SCRIPT="${ROOT_FOLDER}/transceiver_framework/transceiver_framework.sh"

# Get free port and store it in a temp folder inside the folder containing the provided JSON driver file
get_free_tcp_port
"$MKDIR" -p "${DRIVER_CONFIG_FOLDER}/tmp"
"$PRINTF" "$PORT" > "${DRIVER_CONFIG_FOLDER}/tmp/port"

# Start Rserve server and log R related output to 'R_out.log' 
# in a newly created 'logs' folder in the folder containing the JSON driver file.
"$MKDIR" -p "${DRIVER_CONFIG_FOLDER}/logs"
"$NOHUP" "$RSCRIPT" "${ROOT_FOLDER}/mdp/mdp/src/main/r/rserve_starter.R" $PORT > "${DRIVER_CONFIG_FOLDER}/logs/R_out.log"

# Composed commands to execute
RUN_COMMAND="\"$SHELL\" \"$TF_SCRIPT\" \"$1\" \"mdp\""

# Execute
eval "$RUN_COMMAND"
