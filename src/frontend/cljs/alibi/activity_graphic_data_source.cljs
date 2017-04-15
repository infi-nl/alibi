(ns alibi.activity-graphic-data-source
  (:require
    [alibi.logging :refer [log log-cljs]]))

(def ^:private outstanding-promises (atom {}))

(defn resolved-promise [val]
  (let [deferred (js/$.Deferred)]
    (.resolve deferred val)
    deferred))

(defn load-in-cache
  [{:keys [from-date data] :as entries-data} cache]
  (if-not entries-data
    cache
    (assoc cache from-date data)))

(defn- fetch-data! [for-date]
  (log "fetching %o" for-date)
  (let [prom (.. js/$
                 (ajax #js
                       {:url (str "/activity-graphic?from="
                                  for-date)
                        :dataType "json"
                        :method "get"})
                 (then (fn [project-data]
                         (js->clj project-data :keywordize-keys true)))
                 (catch (fn [err]
                          (log "Failed fetching data %o" err))))]
    (swap! outstanding-promises assoc for-date prom)
    prom))

(defn- get-outstanding-promise! [for-date]
  (or (get @outstanding-promises for-date)
      (fetch-data! for-date)))

(defn fetch-data [for-date cache {:keys [on-fetching on-fetched]}]
  (let [cache-value (get cache for-date)]
    (cond
      (nil? cache-value)
      (let [prom (fetch-data! for-date)]
        (on-fetching)
        (.then prom on-fetched))

      (= :fetching cache-value)
      (let [prom (get-outstanding-promise! for-date)]
        (.then prom on-fetched))

      :else (on-fetched cache-value))))
