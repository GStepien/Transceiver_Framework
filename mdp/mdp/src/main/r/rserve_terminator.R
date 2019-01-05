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

# Terminates an RServe instance. The instance's port number is assumed to either be
# the first argument provided to this script or, if no argument was provided,
# to be 51234.

packages <- c("RSclient")
for(pkg in packages){
  if(!require(pkg, character.only = TRUE)){
    install.packages(pkg, dependencies = TRUE)
    library(pkg, character.only = TRUE)
  }
}
rm(list = c("packages"))

cmd_args = commandArgs(trailingOnly = TRUE)
if(length(cmd_args) == 0) {
    cmd_args = 51234
}

stopifnot(length(cmd_args) == 1)

port = as.integer(cmd_args[1])
stopifnot(!is.na(port))

connection <- RSconnect(port = port)

RSshutdown(connection)
RSclose(connection)
rm(list = c("connection"))
