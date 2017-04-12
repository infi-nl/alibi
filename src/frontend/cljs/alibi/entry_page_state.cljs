(ns alibi.entry-page-state
  (:require
   [cljs.reader]
   [clojure.data]
   [alibi.activity-graphic-data-source :as ag-ds]
   [om.core :as om]
   [alibi.logging :refer [log]]
   [time.core :refer [str->unix unix->date-str unix->time-str try-parse-time
                      expand-time]]))

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

(defn data-entry->input-entry [entry]
  (when entry
    {:selected-item {:task-id (:task-id entry)
                     :project-id (:project-id entry)}
     :selected-date (unix->date-str (:from entry))
     :isBillable (:billable? entry)
     :comment (:comment entry)
     :startTime (unix->time-str (:from entry))
     :endTime (unix->time-str (:till entry))
     :entry-id (:entry-id entry)}))

(defn validate-form [{:keys [selected-item endTime startTime] :as form-state}]
  (let [validate
        (fn [f field-name msg errs]
          (if (f form-state)
            errs
            (conj errs [field-name msg])))
        errors
        (->> []
             (validate (constantly selected-item) "SelectedItem" "Task not selected")
             (validate #(try-parse-time startTime)
                       "Start time",
                       "Please enter a valid time value (e.g. 13:37)")
             (validate #(try-parse-time endTime)
                       "End time",
                       "Please enter a valid time value (e.g. 13:37)"))
        has-field-error? (into {} errors)]
    (if (or (has-field-error? "Start time") (has-field-error? "End time"))
      errors
      (cond-> errors
        (>= (. (try-parse-time startTime)
              (compareTo (try-parse-time endTime))) 0)
        (conj ["End time" "End time should come after start time"])))))

(defn additional-entry [input-entry]
  (let [input-entry' (-> input-entry
                       (update :startTime expand-time)
                       (update :endTime expand-time))]
    (if-not (seq (validate-form input-entry'))
      input-entry'
      nil)))

(defn form->input-entry [entry-screen-form]
  (-> (:post-entry-form entry-screen-form)
      (assoc :selected-date (get-in entry-screen-form [:selected-date :date])
             :selected-item (get-in entry-screen-form [:selected-task]))))

(defn form->input-entry' [form]
  (log "pre" form)
  (let [res (form->input-entry form)]
    (log "post" res)
    (log "diff" (clojure.data/diff form res))
    res))


(defonce state (atom (merge {:activity-graphic-data []
                             :activity-graphic-mouse-over-entry {}}
                            initial-state)))

(defn find-entry [state entry-id]
  {:pre [(integer? entry-id)]}
  (let [ag-data (:activity-graphic-data state)]
    (->> ag-data
         (filter #(= (:entry-id %) entry-id))
         first)))

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
