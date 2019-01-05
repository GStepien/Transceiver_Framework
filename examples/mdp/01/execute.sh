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
RSCRIPT=$(which Rscript)

if [ -z "$DIRNAME" -o -z "$READLINK" -o -z "$SHELL" -o -z "$PRINTF" -o -z "$SED" -o -z "$HEAD" -o -z "$RSCRIPT" ]; then
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
RUN_SCRIPT="${ROOT_FOLDER}/mdp/mdp.sh"

# Evaluation script
EVALUATION_SCRIPT="${DRIVER_CONFIG_FOLDER}/evaluate.R"

# Composed commands to execute
EXEC_COMMAND="\"$SHELL\" \"$RUN_SCRIPT\" \"$DRIVER_CONFIG\""
EVALUATE_COMMAND="\"$RSCRIPT\" \"$EVALUATION_SCRIPT\""

# Execute commands
eval "$EXEC_COMMAND" && eval "$EVALUATE_COMMAND"
