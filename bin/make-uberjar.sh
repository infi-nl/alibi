#!/bin/bash

PROFILE=${1:-prod}

echo Building uberjar for profile $PROFILE

echo "Cleaning"
bin/clean.sh

echo "Building clojurescript"
lein cljsbuild once min

echo "Building uberjar"
lein with-profile $PROFILE ring uberjar
