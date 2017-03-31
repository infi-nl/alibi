(ns alibi.activity-graphic-data-source
  (:require [alibi.logging :refer [log log-cljs]]))

(def fetched-data (atom {}))

(defn load!
  [{:keys [from-date data] :as ag-data}]
  (when ag-data
    (let [deferred (.Deferred js/$)]
      (.resolve deferred data)
      (swap! fetched-data assoc from-date deferred))))


(defn get-data [for-date]
  (if (@fetched-data for-date)
    (@fetched-data for-date)
    (do
      (log "fetching %o" for-date)
      (let [deferred
            (.. js/$
                (ajax #js
                      {:url (str "/activity-graphic?from="
                                 for-date)
                       :dataType "json"
                       :method "get"})
                (then (fn [project-data]
                        (js->clj project-data :keywordize-keys true)))
                (catch (fn [err]
                         (log "Failed fetching data %o" err))))]
        (swap! fetched-data assoc for-date deferred)
        deferred))))
