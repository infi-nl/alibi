(ns alibi.entry-page-state
  (:require
   [cljs.reader]
   [alibi.activity-graphic-data-source :as ag-ds]
   [om.core :as om]
   [alibi.logging :refer [log]]))

(def DateTimeFormatter (.-DateTimeFormatter js/JSJoda))
(def LocalTime (.-LocalTime js/JSJoda))
(def LocalDate (.-LocalDate js/JSJoda))
(def ChronoUnit (.-ChronoUnit js/JSJoda))
(def ZoneId (.-ZoneId js/JSJoda))
(def Instant js/JSJoda.Instant)

(def time-formatter (.ofPattern DateTimeFormatter "HH:mm"))

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


(defonce state (atom (merge {:activity-graphic-data []
                             :activity-graphic-mouse-over-entry {}} initial-state)))

(defn input-entry->data-entry
  [entry]
  (when entry
    ;(log "entry %o" entry)
    (let [start-time (.parse LocalTime (:startTime entry) time-formatter)
          end-time (.parse LocalTime (:endTime entry) time-formatter)
          duration-secs (.until start-time end-time (.-SECONDS ChronoUnit))
          date (.parse LocalDate (:selected-date entry))
          start (.. date
                    (atTime start-time)
                    (atZone (.systemDefault ZoneId))
                    (toInstant)
                    (epochSecond))
          end (.. date
                  (atTime end-time)
                  (atZone (.systemDefault ZoneId))
                  (toInstant)
                  (epochSecond))]
      {:task-id (get-in entry [:selected-item :taskId])
       :project-id (get-in entry [:selected-item :projectId])
       :billable? (:isBillable entry)
       :comment (:comment entry)
       :user-id 0
       :from start
       :till end
       :duration duration-secs
       :task (task-name (get-in entry [:selected-item :taskId]))
       :project (project-name (get-in entry [:selected-item :projectId]))
       :entry-id (:entry-id entry)})))

(defn input-entry [post-entry-form selected-date selected-item]
  (-> post-entry-form
      (assoc :selected-date selected-date
             :selected-item selected-item)))

(defn selected-date' [] (om/ref-cursor (get-in (om/root-cursor state) [:form :selected-date])))
(defn entries [] (om/ref-cursor (:activity-graphic-data (om/root-cursor state))))
(defn mouse-over-entry []
  (om/ref-cursor (:activity-graphic-mouse-over-entry (om/root-cursor state))))
(defn post-entry-form []
  (om/ref-cursor (:post-entry-form (om/root-cursor state))))
(defn selected-item []
  (om/ref-cursor (:selected-item (om/root-cursor state))))
(defn post-new-entry-bar []
  (om/ref-cursor (get-in (om/root-cursor state) [:post-new-entry-bar])))
