#!/bin/bash

#    Copyright (c) 2018 Grzegorz Stepien
#
#    This file and its contents are provided under the BSD 3-clause license.
#    For more details, see './LICENSE.md'
#    (where '.' represents this program's root directory).

# Pull root project, pull already cloned submodules 
# (recusively, i.e. including submodules of those submodules and so on) 
# and clone missing submodules (again, recursively).
# Always pulls the version of each submodule associated with its superproject
# (which is not necessarily the newest one).
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

if [ -z "$GIT" -o -z "$PRINTF" ]; then
  error_msg "At least one required tool is missing. See \"Check tool availability\" paragraph of \"$0\" for more details."
  exit 1
fi

"$GIT" fetch && "$GIT" fetch --tags && "$GIT" merge FETCH_HEAD &&
  "$GIT" submodule update --init --recursive &&
  "$GIT" submodule update --recursive
