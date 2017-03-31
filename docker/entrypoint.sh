#!/usr/bin/env bash

configure_database() {
  echo "Configuring alibi::database"

  if [[ ! -e "${ALIBI_DATA_DIR}/alibi.db" ]]; then
    echo "Generating alibi::database in ${ALIBI_DATA_DIR}/alibi.db"

    mkdir -p ${ALIBI_DATA_DIR}
    lein run sqlite create-db :filename "${ALIBI_DATA_DIR}/alibi.db"
  fi

  if [[ ! -e "${ALIBI_DATA_DIR}/config.edn" ]]; then
    echo "Generatinig alibi::database configuration"

cat << EOF > ${ALIBI_DATA_DIR}/config.edn
{:cookie-encryption-key "`openssl rand -base64 12`"
 :selmer-caching? false

 :persistence :sqlite
 :persistence-strategies {}
 :sqlite {:subprotocol "sqlite" :subname "${ALIBI_DATA_DIR}/alibi.db"}

 :authentication :single-user
 :single-user {:username "me!"}}
EOF
  fi

  echo "Copying alibi::database configuration from ${ALIBI_DATA_DIR}/config.edn"
  cp "${ALIBI_DATA_DIR}/config.edn" "${ALIBI_INSTALL_DIR}/config/local/config.edn"
}


case ${1} in
  app:start|app:projects|app:tasks|app:create-overhead-task)

    configure_database

    case ${1} in
      app:start)
        lein with-profile local ring server-headless
        ;;
      app:projects)
	shift 1
        lein with-profile local run projects $@
        ;;
      app:tasks)
        shift 1
        lein with-profile local run tasks $@
        ;;
    esac
    ;;
  app:help)
    echo "Available options:"
    echo " app:start                - Starts the Alibi server (default)"
    echo " app:projects             - Run project action"
    echo " app:tasks                - Run task action"
    echo " app:help                 - Displays the help"
    echo " [command]                - Execute the specified command, eg. bash."
    ;;
  *)
    exec "$@"
    ;;
esac
