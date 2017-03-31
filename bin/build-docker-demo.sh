#!/bin/bash

TAG=${1:-infi/alibi-demo}

echo "Cleaning"
bin/clean.sh

echo "Building uberjar"
bin/make-uberjar.sh demo || exit 1

echo "Initing demo db"
bin/init-demo-db.sh target/alibi.db || exit 1

echo "Making docker image"
docker build -f Dockerfile.demo -t "$TAG" . || exit 1
