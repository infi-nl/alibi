#!/usr/bin/env bash

cd `dirname $0`/..

# MYSQL_HOST=${MYSQL_HOST:-"127.0.0.1"}
# MYSQL_PORT=${MYSQL_PORT:-"3306"}
# MYSQL_DATABASE=${MYSQL_DATABASE:-"alibi"}
# MYSQL_USERNAME=${MYSQL_USERNAME:-"root"}
# MYSQL_PASSWORD=${MYSQL_PASSWORD:-""}
# 
# echo "Creating config/test/config.edn"
# 
# mkdir -p config/test
# 
# cat << EOF > config/test/config.edn
# {:alpaca-mysql {:subprotocol "mysql"
#                  :subname "//${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?zeroDateTimeBehavior=convertToNull"
#                  :user "${MYSQL_USERNAME}"
#                  :password "${MYSQL_PASSWORD}"}}
# EOF
# echo "Created config/test/config.edn"

echo "Running tests"
LEIN_ROOT=1 lein with-profile -dev,+test do clean, test
echo "Ran tests"
