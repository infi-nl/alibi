#!/bin/bash

VIRTUAL_HOST_NAME=${1:-alibi}
HOST_IP=${2:-172.17.0.1}

echo Running server, vhost=$VIRTUAL_HOST_NAME, host-ip=$HOST_IP

docker run -e VIRTUAL_HOST=$VIRTUAL_HOST_NAME -d --restart=always --add-host docker-host:$HOST_IP -p 127.0.0.1::3500 infi/alibi
