#!/bin/bash

TAG=${1:-latest}

FULL_TAG=infi/alibi:$TAG

echo Building $FULL_TAG

docker build -t $FULL_TAG .
