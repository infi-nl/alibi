(ns alibi.application.alibi-identity
  (:require
    [buddy.auth :refer [authenticated?]]))

(def ^:dynamic ^:private *impl* nil)

(defmacro with-impl [impl & body]
  `(binding [*impl* ~impl]
     ~@body))

(defprotocol Identity
  (-user-id-for-username [_ username]))

(defn- user-id-for-username [username]
  (-user-id-for-username *impl* username))

(defn wrap-augment-identity [handler]
  (fn [request]
    (handler
      (if (authenticated? request)
        (let [username (:identity request)]
          (assoc request
                 :identity {:username username
                            :id (user-id-for-username username)}))
        request))))

