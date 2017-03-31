(ns alibi.core
  (:require
    [alibi.config :as config]
    [alibi.web :as web]
    [alibi.cli :refer [cli-main cli-clj]]
    [alibi.datasource.sqlite.bootstrap :refer [with-sqlite]]
    [alibi.application.openid-connect :as openid]
    [alibi.infra.jdbc-extensions]))

(def config (alibi.config/read-config))

(defn load-persistence-strategy [strategy middlewares-map]
  (let [[strategy-key strategy-config] strategy
        {:keys [requires bootstrap]} strategy-config]
    (when (seq requires)
      (apply require requires))
    (assert bootstrap (str strategy-key " should define a :bootstrap "
                           "function in config"))
    (let [bootstrap-fn (resolve bootstrap)]
      (assert bootstrap-fn (str "Couldn't resolve :bootstrap="
                                bootstrap " to a var, "
                                "are you sure the function exists?"))
      (assoc middlewares-map strategy-key (bootstrap-fn strategy-config)))))

(def persistence-middlewares
  (loop [strategies (:persistence-strategies config)
         middlewares {:sqlite (fn [f] (with-sqlite (:sqlite config) (f)))}]
    (if (seq strategies)
      (recur (rest strategies)
             (load-persistence-strategy (first strategies) middlewares))
      middlewares)))

(def persistence-middleware
  (let [persistence-key (:persistence config :sqlite)]
    (if-let [middleware (get persistence-middlewares persistence-key)]
      middleware
      (throw (Exception. (str "Persistence strategy " persistence-key
                              " not supported, try one of "
                              (keys persistence-middleware)))))))

(def auth-backends
  {:openid (fn [] (openid/backend (:openid config)))
   :single-user (fn [] (get-in config [:single-user :username]
                               "anonymous"))})

(def auth-backend
  (let [auth-backend-key (:authentication config :single-user)]
    (if-let [backend (get auth-backends auth-backend-key)]
      (backend)
      (throw (Exception. (str "Auth backend " auth-backend-key " not supported"
                              "try one of " (keys auth-backends)))))))

(defn wrap-persistence
  [handler]
  (fn [req]
    (persistence-middleware
      (fn [] (handler req)))))

(def app (web/app wrap-persistence auth-backend))

(defn -main [& args]
  (persistence-middleware (fn []  (apply cli-main args))))

(defmacro cli [& args]
  `(-main ~@(map str args)))
