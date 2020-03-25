#!/usr/bin/env bash

cd `dirname $0`/..

echo "Running tests"
LEIN_ROOT=1 lein with-profile -dev,+test do clean, test
echo "Ran tests"
