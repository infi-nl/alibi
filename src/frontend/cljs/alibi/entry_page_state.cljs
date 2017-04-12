(ns alibi.entry-page-state
  (:require
   [cljs.reader]
   [alibi.activity-graphic-data-source :as ag-ds]
   [om.core :as om]
   [alibi.logging :refer [log]]
   [time.core :refer [str->unix]]))

(let [view-data-input (js/document.getElementById  "view-data")
      view-data (cljs.reader/read-string
                           (.-value view-data-input))]
  (def task-name (get-in view-data [:projects-tasks :tasks-by-id]))
  (def project-name (get-in view-data [:projects-tasks :projects-by-id]))

  (ag-ds/load! (:activity-graphic view-data))

  (def initial-state
    (let [options (get-in (:initial-state view-data)
                          [:post-new-entry-bar :options])
          options-by-id (into {} (for [{:keys [value] :as opt} options]
                                   [value opt]))]

      (assoc-in (:initial-state view-data)
                [:post-new-entry-bar :options-by-id] options-by-id))))

(defn input-entry->data-entry
  [entry]
  (when entry
    ;(log "entry %o" entry)
    (let [from (str->unix (:selected-date entry) (:startTime entry))
          till (str->unix (:selected-date entry) (:endTime entry))
          duration (- till from)]
      {:task-id (get-in entry [:selected-item :task-id])
       :project-id (get-in entry [:selected-item :project-id])
       :billable? (:isBillable entry)
       :comment (:comment entry)
       :user-id 0
       :from from
       :till till
       :duration duration
       :task (task-name (get-in entry [:selected-item :task-id]))
       :project (project-name (get-in entry [:selected-item :project-id]))
       :entry-id (:entry-id entry)})))

(defn input-entry [entry-screen-form]
  (-> (:post-entry-form entry-screen-form)
      (assoc :selected-date (get-in entry-screen-form [:selected-date :date])
             :selected-item (get-in entry-screen-form [:selected-task]))))

(defonce state (atom (merge {:activity-graphic-data []
                             :activity-graphic-mouse-over-entry {}}
                            initial-state)))

(defn selected-date []
  (om/ref-cursor (get-in (om/root-cursor state) [:form :selected-date])))
(defn selected-task []
  (om/ref-cursor (get-in (om/root-cursor state) [:form :selected-task])))
(defn post-entry-form []
  (om/ref-cursor (get-in (om/root-cursor state) [:form :post-entry-form])))
(defn entry-screen-form []
  (om/ref-cursor (:form (om/root-cursor state))))

(defn entries []
  (om/ref-cursor (:activity-graphic-data (om/root-cursor state))))
(defn mouse-over-entry []
  (om/ref-cursor (:activity-graphic-mouse-over-entry (om/root-cursor state))))
(defn post-new-entry-bar []
  (om/ref-cursor (get-in (om/root-cursor state) [:post-new-entry-bar])))
