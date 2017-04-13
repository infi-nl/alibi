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
    [time.core :refer [unix->time-str unix->date-str]]
    [alibi.entry-page-state :refer [state task-name project-name
                                    input-entry->data-entry
                                    data-entry->input-entry
                                    find-entry]]))

(enable-console-print!)
(defn parse-float [v] (js/parseFloat v))

(defn entry-form-reducer
  [prev-state {:keys [action] :as payload} next-state]
  (let [empty-state {:startTime ""
                     :endTime ""
                     :isBillable false
                     :comment ""
                     :entry-id "new"}]
    (case action
      :change-comment
      (assoc prev-state :comment (:comment payload))

      :change-start-time
      (assoc prev-state :startTime (:start-time payload))

      :change-end-time
      (assoc prev-state :endTime (:end-time payload))

      :change-billable?
      (assoc prev-state :isBillable (:billable? payload))

      :cancel-entry empty-state

      :edit-entry
      (merge empty-state
             (select-keys (:selected-entry next-state)
                          [:comment :startTime :endTime :isBillable :entry-id]))

      prev-state)))

(defn reducer
  [prev-state {:keys [action] :as payload}]
  ;(log "reducer %o" payload)
  (let [next-state
        (case action
          :select-task
          (assoc-in prev-state [:form :selected-task] (:task payload))

          ;:change-date
          ;(assoc-in prev-state [:post-entry-form :selectedDate] (:date payload))

          :receive-activity-graphic-data
          (-> prev-state
              (assoc-in [:form :selected-date :date] (:for-date payload))
              (assoc :activity-graphic-data (vec (:data payload))))

          :mouse-over-entry
          (assoc prev-state :activity-graphic-mouse-over-entry (:entry payload))

          :mouse-leave-entry
          (assoc prev-state :activity-graphic-mouse-over-entry {})

          :edit-entry
          (let [entry (data-entry->input-entry
                        (find-entry prev-state (:entry-id payload)))]

            (-> prev-state
                (assoc-in [:form :selected-date :date] (:selected-date entry))
                (assoc-in [:form :selected-task] (:selected-item entry))
                (assoc :activity-graphic-mouse-over-entry {})
                (assoc :selected-entry entry)))

          :cancel-entry
          (-> prev-state
              (update :form assoc
                      :submitted? false
                      :form-at-submit-time nil)
              (assoc-in [:form :selected-task] {}))

          :entry-form-show-errors
          ; insert :form
          (update prev-state :form assoc
                  :submitted? true
                  :form-at-submit-time (:form payload))

          prev-state)]
    (update-in next-state [:form :post-entry-form]
               entry-form-reducer payload next-state)))

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
    (fetch-ag-data! (get-in current-state [:form :selected-date :date]))))

; if you wonder why we introduce an itermediate IRender here: it seems Om
; ref-cursors only work if there is at least one om/root that binds to the root
; atom, so we do that here even though it is not passed on to om/build
; see also https://github.com/omcljs/om/issues/864
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
  post-entry-form/om-component
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
  (let [new-date (get-in for-state [:form :selected-date :date])]
    (day-entry-table/render "day-entry-table" new-date)))

(add-watch
  state :renderer
  (fn [_ _ _ new-state]
    ;(log "new-state %o" new-state)
    (render-day-entry-table! new-state)))

(reset! state @state)
(js/console.log @state)
