(ns alibi.actions
  (:require
    [alibi.activity-graphic-data-source :as ag-ds]
    [time.core :as time]))

(defn receive-entries-data [for-date data]
  {:action :receive-activity-graphic-data
   :for-date for-date
   :data data})

(defn load-entries-data [new-date]
  (fn [dispatch!]
    (let [date-str (.toString new-date)]
      (.then
        (ag-ds/get-data (.toString (time/find-monday-before date-str)))
        (fn [data]
          (dispatch! (receive-entries-data date-str data)))))))

(def change-entry-page-date load-entries-data)
