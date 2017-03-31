(ns alibi.web
  (:require [selmer.parser :as selmer]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :as response]
            [alibi.application.screens.entry-screen
             :as entry-screen]
            [ring.util.mime-type :as mime]
            [ring.middleware.params :as params]
            [ring.middleware.session :as session]
            [alibi.application.authn-authz
             :refer [wrap-authn-and-authz]]
            [alibi.application.alibi-identity
             :refer [wrap-augment-identity]]
            [ring.middleware.session.cookie :as cookie-session]
            [alibi.infra.date-time
             :refer [today format-date str->local-date]]
            [alibi.config :refer [read-config]]))

(def config (read-config))

(def cookie-encryption-key (:cookie-encryption-key config))

(if (get config :selmer-caching? true)
  (selmer/cache-on!)
  (selmer/cache-off!))

(defroutes compojure-routes
  (GET "/"
       []
       (response/redirect "/entry"))
  (GET "/activity-graphic"
       request
       (entry-screen/activity-graphic request))
  (GET "/entry/day-entries"
       request
       (entry-screen/day-entries-table request))
  (GET "/entry"
       request
       (response/redirect (str "/entry/" (format-date (today)))))
  (GET "/entry/:for-date"
       [for-date :<< str->local-date :as request]
       (entry-screen/get-page for-date request))
  (POST "/entry/:for-date"
        request
        (entry-screen/post request))
  (GET "/*" {{resource-path :*} :route-params}
       (fn [req]
         (let [resp (some->
                      (response/resource-response (str "public/" resource-path))
                      ((fn [resp]
                         (if-let [mime-type (mime/ext-mime-type resource-path)]
                           (response/content-type resp mime-type)
                           resp))))]
           (if resp
             resp
             (response/not-found (str "Could not find " resource-path)))))))

(defn wrap-no-cache
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (response/header "Expires" "Thu, 01 Dec 1994 16:00:00 GMT")
          (response/header "Cache-control" "max-age=0")))))

(defn wrap-selmer [handler]
  (fn [request]
    (let [response (handler request)]
      (if-let [selmer-template (:selmer-template (:body response))]
        (assoc response :body
               (selmer/render-file selmer-template
                                   (get-in response [:body :template-data])))
        response))))

(defn wrap-default-responses [handler]
  (fn [request]
    (let [response (handler request)]
      (if (response/response? response)
        (->
          response
          (response/content-type (get-in response [:headers "Content-Type"] "text/html"))
          (response/charset "utf-8"))
        response))))

(defn app [wrap-persistence auth-backend]
  (-> compojure-routes
      (wrap-augment-identity)
      (wrap-authn-and-authz auth-backend)
      (session/wrap-session {:store (cookie-session/cookie-store {:key cookie-encryption-key })})
      (params/wrap-params)
      (wrap-no-cache)
      (wrap-default-responses)
      (wrap-selmer)
      (wrap-persistence)))
