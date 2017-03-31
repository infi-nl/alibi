#!/bin/bash

if [ -z "$1" ]; then
        echo "Usage: init-demo-db.sh <database-filename>"
        exit 1
fi

SQLITE_DBFILE="$1"
export SQLITE_DBFILE

echo "creating db $SQLITE_DBFILE"
lein with-profile demo run sqlite create-db :filename "$1" || exit 1


echo "creating project"
lein with-profile demo run projects create :name "CTU" :billing-method :overhead || exit 1

echo "creating tasks"
lein with-profile demo run tasks create :name "Chase terrorists" :for-project 1 :billing-method :hourly || exit 1
lein with-profile demo run tasks create :name "Find mole" :for-project 1 :billing-method :fixed-price || exit 1
