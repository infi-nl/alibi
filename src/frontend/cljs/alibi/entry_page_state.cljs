(ns alibi.entry-page-state
  (:require
   [cljs.reader]
   [alibi.activity-graphic-data-source :as ag-ds]
   [om.core :as om]
   [alibi.logging :refer [log]]))

(def view-data
  (let [view-data-input (.getElementById js/document "view-data")
        view-data-parsed (cljs.reader/read-string
                           (.-value view-data-input))]
    view-data-parsed))

(def task-name (get-in view-data [:projects-tasks :tasks-by-id]))
(def project-name (get-in view-data [:projects-tasks :projects-by-id]))

(ag-ds/load! (:activity-graphic view-data))

(def initial-state
  (let [is (:initial-state view-data)
        options (get-in is [:post-new-entry-bar :options])
        options-by-id (into {} (for [{:keys [value] :as opt} options]
                                 [value opt]))]

    (assoc-in is [:post-new-entry-bar :options-by-id] options-by-id)))

;(log "initial state %o" initial-state)


(defonce state (atom (merge {:activity-graphic-data []} initial-state)))

(defn selected-date [] (om/ref-cursor (om/root-cursor state)))
(defn entries [] (om/ref-cursor (:activity-graphic-data (om/root-cursor state))))
