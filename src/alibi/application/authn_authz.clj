(ns alibi.application.authn-authz
  (:require
            [buddy.auth.middleware
             :refer [wrap-authentication wrap-authorization]]
            [ring.util.response :as response]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [buddy.auth.protocols]
            [alibi.infra.date-time :as date-time]))

(defn as-user-backend [username]
  (reify
    buddy.auth.protocols/IAuthentication
    (-parse [_ _] true)
    (-authenticate [_ _ _] username)
    buddy.auth.protocols/IAuthorization
    (-handle-unauthorized [_ _ _]
      (->
        (response/response  "Permission denied")
        (response/status 403)))))


(defn wrap-authorize-all [handler]
  (fn [{:keys [uri] :as request}]
    (if (not (authenticated? request))
      (throw-unauthorized {:message "not authorized"})
      (handler request))))

(defn wrap-authn-and-authz [handler backend-or-username]
  (let [backend (if (string? backend-or-username)
                  (as-user-backend backend-or-username)
                  backend-or-username)]
    (-> handler
        (wrap-authorize-all)
        (wrap-authorization backend)
        (wrap-authentication backend))))
