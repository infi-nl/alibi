(ns alibi.application.openid-connect
  (:require
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [buddy.core.keys :as keys]
    [clojure.java.io :refer [resource]]
    [buddy.sign.jwt :as jwt]
    [alibi.infra.date-time :as date-time]
    [ring.util.response :refer [response response? redirect status] :as response]
    [crypto.random :refer [base64]]
    [ring.util.codec :refer [form-encode]]
    [buddy.auth.backends :as backends]
    ))

(def time-tolerance [60 :seconds])

(defn backend
  [{:keys [authorization-endpoint token-endpoint client-id client-secret
           public-key-filename issuer] :as oauth-config}]
  {:pre [authorization-endpoint token-endpoint client-id client-secret
         public-key-filename issuer]}
  (letfn
    [(fetch-token [authorization-code]
       (let [result (client/post token-endpoint
                                 {:basic-auth [client-id client-secret]
                                  :form-params {"grant_type" "authorization_code"
                                                "code" authorization-code}
                                  :throw-exceptions false})]
         result))

     (parse-oauth-response [response-body]
       (json/read-str response-body :key-fn keyword))

     (validate-id-token [id-token]
       {:pre [id-token]}
       (let [public-key (keys/public-key (resource public-key-filename))]
         (-> id-token
             (jwt/unsign public-key
                         {:alg :rs256
                          :aud client-id
                          :iss issuer
                          :now (-> (date-time/now)
                                   (date-time/plus time-tolerance)
                                   (date-time/->unix))})
             :sub)))


     (request-openid-token [authorization-code]
       (let [{:keys [body] fetch-token-status :status}
             (fetch-token authorization-code)]
         (if (not= fetch-token-status 200)
           (-> (response body)
               (status 400))
           (let [parsed (parse-oauth-response body)]
             (validate-id-token (:id_token parsed))))))

     (verify-oauth-authorization-response
       [{{:keys [oauth-state]} :session
         {request-state "state" authorization-code "code"} :params
         :as request}]
       {:pre [oauth-state request-state authorization-code]}
       (if (not= request-state oauth-state)
         (-> (response "invalid state token")
             (status 400))
         (request-openid-token authorization-code)))

     (redirect-to-openid []
       (let [csrf-token (base64 32)
             query-params {"client_id" client-id
                           "state" csrf-token
                           "response_type" "code"
                           "scope" "openid"}]
         (->
           (redirect
             (str authorization-endpoint "?" (form-encode query-params)))
           (assoc-in [:session :oauth-state] csrf-token))))

     (login-and-redirect-to-home [{:keys [session]} user-id]
       (-> (redirect "/")
           (assoc :session (assoc session :identity user-id))))

     (handle-unauthorized [request & _]
       (if (= (:uri request) "/authorize")
         (let [verify-result (verify-oauth-authorization-response request)]
           (if (response? verify-result)
             verify-result
             (login-and-redirect-to-home request verify-result)))
         (redirect-to-openid)))]
    (backends/session
      {:unauthorized-handler handle-unauthorized})))
