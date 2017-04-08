(ns alibi.entry-page
  (:require
    [alibi.logging :refer [log log-cljs]]
    [alibi.post-new-entry-bar :as post-new-entry-bar]
    [alibi.post-entry-form :as post-entry-form]
    [alibi.activity-graphic :as activity-graphic]
    [alibi.activity-graphic-data-source :as ag-ds]
    [alibi.day-entry-table :as day-entry-table]
    [clojure.string :refer [split]]
    [time.core :refer [expand-time]]
    [cljs.reader]
    [om.core :as om]
    [om.dom :as dom]
    [alibi.entry-page-state :refer [state task-name project-name
                                    input-entry->data-entry]]))

(enable-console-print!)
(defn parse-float [v] (js/parseFloat v))

(def DateTimeFormatter (.-DateTimeFormatter js/JSJoda))
(def LocalTime (.-LocalTime js/JSJoda))
(def LocalDate (.-LocalDate js/JSJoda))
(def ChronoUnit (.-ChronoUnit js/JSJoda))
(def ZoneId (.-ZoneId js/JSJoda))
(def Instant js/JSJoda.Instant)

(def time-formatter (.ofPattern DateTimeFormatter "HH:mm"))

(defn epoch->time-str [epoch]
  (.format (LocalTime.ofInstant (Instant.ofEpochSecond epoch))
           time-formatter))

(defn epoch->date-str [epoch]
  (.toString (LocalDate.ofInstant (Instant.ofEpochSecond epoch))))

(defn data-entry->input-entry [entry]
  (when entry
    {:selected-item {:taskId (:task-id entry)
                     :projectId (:project-id entry)}
     :selected-date (epoch->date-str (:from entry))
     :isBillable (:billable? entry)
     :comment (:comment entry)
     :startTime (epoch->time-str (:from entry))
     :endTime (epoch->time-str (:till entry))
     :entry-id (:entry-id entry)}))

(defn get-entry [state entry-id]
  {:pre [(integer? entry-id)]}
  (let [ag-data (:activity-graphic-data state)]
    (->> ag-data
         (filter #(= (:entry-id %) entry-id))
         first)))

(defn reducer
  [prev-state {:keys [action] :as payload}]
  ;(log "reducer %o" payload)
  (let [next-state
        (case action
          :select-task
          (assoc prev-state :selected-item (:task payload))

          ;:change-date
          ;(assoc-in prev-state [:post-entry-form :selectedDate] (:date payload))

          :receive-activity-graphic-data
          (-> prev-state
              (assoc :selected-date (:for-date payload))
              (assoc :activity-graphic-data (vec (:data payload))))

          :mouse-over-entry
          (assoc prev-state :activity-graphic-mouse-over-entry (:entry payload))

          :mouse-leave-entry
          (assoc prev-state :activity-graphic-mouse-over-entry {})

          :edit-entry
          (let [entry (data-entry->input-entry
                        (get-entry prev-state (:entry-id payload)))]

            (-> prev-state
                (assoc :selected-item (:selected-item entry)
                       :selected-date (:selected-date entry)
                       :selected-entry entry)))

          :cancel-entry
          (assoc prev-state :selected-item {})

          prev-state)]
    (update next-state :post-entry-form post-entry-form/reducer payload next-state)))

(defn dispatch!
  [state-atom action]
  (if (fn? action)
    (action (partial dispatch! state-atom) @state-atom)
    (swap! state-atom reducer action)))

(defn fetch-ag-data! [for-date]
  (.then
    (ag-ds/get-data (.toString (activity-graphic/find-first-monday for-date)))
    (fn [data]
      ;(log-cljs "received" for-date)
      (dispatch! state {:action :receive-activity-graphic-data
                        :for-date (.toString for-date)
                        :data data}))))

(let [current-state @state]
  (when-not (seq (:activity-graphic-data current-state))
    (log "fetching initial ag data")
    (fetch-ag-data! (get current-state :selected-date))))

; if you wonder why we introduce an itermediate IRender here: it seems Om
; ref-cursors only work if there is at least one om/root that binds to the root
; atom, so we do that here even though it is not passed on to om/build
(om/root
  (let [dispatch! (partial dispatch! state)] ; make sure entry-bar-form gets a constant state
    (fn [_ owner]
      (reify
        om/IRender
        (render [_]
          (om/build post-new-entry-bar/entry-bar-form
                    {:dispatch! dispatch!})))))
  state
  {:target (js/document.getElementById "post-new-entry-bar-container")})

(om/root
  post-entry-form/react-component
  {:dispatch! (partial dispatch! state)}
  {:target (js/document.getElementById "entry-form-react-container")})

(om/root
  activity-graphic/render-html
  {:dispatch! (partial dispatch! state)}
  {:target (js/document.getElementById "activity-graphic")})

(om/root
  activity-graphic/render-tooltip
  {:dispatch! (partial dispatch! state)}
  {:target (js/document.getElementById "activity-graphic-tooltip-container")})

(defn render-day-entry-table!
  [for-state]
  (let [new-date (get for-state :selected-date)]
    (day-entry-table/render "day-entry-table" new-date)))

(add-watch
  state :renderer
  (fn [_ _ _ new-state]
    ;(log "new-state %o" new-state)
    (render-day-entry-table! new-state)))

(reset! state @state)
