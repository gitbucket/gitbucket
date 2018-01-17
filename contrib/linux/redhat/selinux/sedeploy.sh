#!/bin/sh

set -e

MODULE=${1}

# this will create a .mod file
checkmodule -M -m -o ${MODULE}.mod ${MODULE}.te

# this will create a compiled semodule
semodule_package -m ${MODULE}.mod -o ${MODULE}.pp

# this will install the module
semodule -i ${MODULE}.pp
