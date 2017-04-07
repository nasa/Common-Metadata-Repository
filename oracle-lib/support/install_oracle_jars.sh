#!/bin/sh
# Installs Oracle JDBC drivers into the local maven repository. See README.md for more invormation.

# This must match the version of oracle in project.clj
VERSION=11.2.0.4

PROJECT_DIR=oracle-lib
SUPPORT_DIR=support
PWD=$(pwd)
BASE_DIR=$(basename $PWD)

if [ "$BASE_DIR" = "$PROJECT_DIR" ] ; then
    PREFIX=./${SUPPORT_DIR}/
elif [ "$BASE_DIR" = "support" ] ; then
    PREFIX="./"
fi

echo "PREFIX: $PREFIX"

# Check that jars are available
if ! [ -e "${PREFIX}ojdbc6.jar" ] ; then
  echo "ojdbc6.jar was not found on current path. Download Oracle JDBC Driver Jars and put on current path" >&2
  exit 1
fi
if ! [ -e "${PREFIX}ons.jar" ] ; then
  echo "ons.jar was not found on current path. Download Oracle JDBC Driver Jars and put on current path" >&2
  exit 1
fi
if ! [ -e "${PREFIX}ucp.jar" ] ; then
  echo "ucp.jar was not found on current path. Download Oracle JDBC Driver Jars and put on current path" >&2
  exit 1
fi

echo "Installing oracle jars into local maven repository" >&2

mvn install:install-file -Dfile=${PREFIX}ojdbc6.jar -DartifactId=ojdbc6 -Dversion=${VERSION} -DgroupId=com.oracle -Dpackaging=jar -DcreateChecksum=true
mvn install:install-file -Dfile=${PREFIX}ons.jar -DartifactId=ons -Dversion=${VERSION} -DgroupId=com.oracle -Dpackaging=jar -DcreateChecksum=true
mvn install:install-file -Dfile=${PREFIX}ucp.jar -DartifactId=ucp -Dversion=${VERSION} -DgroupId=com.oracle -Dpackaging=jar -DcreateChecksum=true
