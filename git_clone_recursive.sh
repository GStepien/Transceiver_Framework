#!/bin/bash

#    Copyright (c) 2018 Grzegorz Stepien
#
#    This file and its contents are provided under the BSD 3-clause license.
#    For more details, see './LICENSE.md'
#    (where '.' represents this program's root directory).

# A simple script for cloning and configuring this repository and its submodules.
# The repository is cloned into a new folder './<repository_name>' where '.' 
# is the folder where this script is executed and '<repository_name>' is the name
# of this repository (i.e., the value assigned to the 'REPOSITORY_NAME' variable below, 
# which, in turn, is the basename of this repository without the '.git' extension).
#
# Submodules are pulled with detached HEAD.

# Helper method for error printing
error_msg()
{
    TITLE="Unsuccessful execution of this script via: \"$0 $CONSOLE_PARAMS\""
    "$PRINTF" "ERROR: $TITLE\n\tMessage: $1\n"
}
CONSOLE_PARAMS=$@

# Check tool availability
GIT=$(which git)
PRINTF=$(which printf)
BASENAME=$(which basename)

if [ -z "$GIT" -o -z "$PRINTF" -o -z "$BASENAME" ]; then
  error_msg "At least one required tool is missing. See \"Check tool availability\" paragraph of \"$0\" for more details."
  exit 1
fi

REPOSITORY="https://github.com/GStepien/Transceiver_Framework.git"
REPOSITORY_NAME=$("$BASENAME" "${REPOSITORY%.*}")

# Clone repository, recursively initialize and clone submodules
"$GIT" clone --recurse-submodules $REPOSITORY

cd "$REPOSITORY_NAME"
# Fetch tags
"$GIT" fetch --tags && "$GIT" merge FETCH_HEAD
# Display status of submodules when executing 'git status'
"$GIT" config --local status.submoduleSummary true
cd "$OLDPWD"
