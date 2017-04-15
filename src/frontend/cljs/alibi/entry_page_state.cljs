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
      view-data (cljs.reader/read-string (.-value view-data-input))]
  (def task-name (get-in view-data [:projects-tasks :tasks-by-id]))
  (def project-name (get-in view-data [:projects-tasks :projects-by-id]))

  (ag-ds/load! (:activity-graphic view-data))

  (def initial-state
    (merge {:activity-graphic-data []
            :activity-graphic-mouse-over-entry {}}
           (let [options (get-in (:initial-state view-data)
                                 [:post-new-entry-bar :options])
                 options-by-id (into {} (for [{:keys [value] :as opt} options]
                                          [value opt]))]

             (assoc-in (:initial-state view-data)
                       [:post-new-entry-bar :options-by-id] options-by-id)))))


(def form-selected-date #(get-in % [:selected-date :date]))
(def form-selected-task #(get-in % [:selected-task]))
(def form-selected-task-id #(get-in % [:selected-task :task-id]))
(def form-selected-project-id #(get-in % [:selected-task :project-id]))
(def form-start-time #(get-in % [:post-entry-form :start-time]))
(def form-end-time #(get-in % [:post-entry-form :end-time]))
(def form-entry-id #(get-in % [:post-entry-form :entry-id]))
(def form-comment #(get-in % [:post-entry-form :comment]))
(def form-billable? #(get-in % [:post-entry-form :billable?]))
(def form-submitted? #(get-in % [:submitted?]))
(def form-form-at-submit-time #(get-in % [:form-at-submit-time]))

(defn form-data-entry->form [entry]
  (when entry
    {:selected-task {:task-id (:task-id entry)
                     :project-id (:project-id entry)}
     :selected-date {:date (unix->date-str (:from entry))}
     :post-entry-form {:billable? (:billable? entry)
                       :comment (:comment entry)
                       :start-time (unix->time-str (:from entry))
                       :end-time (unix->time-str (:till entry))
                       :entry-id (:entry-id entry)}}))

(defn form->data-entry [form]
  (let [from (str->unix (form-selected-date form) (form-start-time form))
        till (str->unix (form-selected-date form) (form-end-time form))
        duration (- till from)]
    {:task-id (form-selected-task-id form)
     :project-id (form-selected-project-id form)
     :billable? (form-billable? form) :comment (form-comment form)
     :user-id 0 :from from :till till :duration duration
     :task (task-name (form-selected-task-id form))
     :project (project-name (form-selected-project-id form))
     :entry-id (form-entry-id form)}))

(defn form-validate-form [form]
  (let [validate
        (fn [f field-name msg errs]
          (if (f form)
            errs
            (conj errs [field-name msg])))
        errors
        (->> []
             (validate (comp seq form-selected-task) "SelectedItem" "Task not selected")
             (validate (comp try-parse-time form-start-time) "Start time",
                       "Please enter a valid time value (e.g. 13:37)")
             (validate (comp try-parse-time form-end-time) "End time",
                       "Please enter a valid time value (e.g. 13:37)"))
        has-field-error? (into {} errors)]
    (if (or (has-field-error? "Start time") (has-field-error? "End time"))
      errors
      (cond-> errors
        (not (neg? (.compareTo (try-parse-time (form-start-time form))
                               (try-parse-time (form-end-time form)))))
        (conj ["End time" "End time should come after start time"])))))

(defn form-get-editing-entry [form]
  (let [form (-> form
                 (update-in [:post-entry-form :start-time] expand-time)
                 (update-in [:post-entry-form :end-time] expand-time))]
    (when-not (seq (form-validate-form form))
      (form->data-entry form))))

(defn form-get-editing-entry-id [form]
  (:entry-id (form-get-editing-entry form)))

(defn entries-add-form-entry [entries form]
  (if-let [form-entry (form-get-editing-entry form)]
    (conj (vec (remove #(= (:entry-id form-entry) (:entry-id %)) entries))
          form-entry)
    entries))

(defn entries-find-entry [entries entry-id]
  {:pre [(integer? entry-id)]}
  (->> entries
       (filter #(= (:entry-id %) entry-id))
       first))

(defn selected-task-cursor [state]
  (om/ref-cursor (get-in (om/root-cursor state) [:form :selected-task])))
(defn entry-screen-form-cursor [state]
  (om/ref-cursor (:form (om/root-cursor state))))

(defn entries-cursor [state]
  (om/ref-cursor (:activity-graphic-data (om/root-cursor state))))
(defn mouse-over-entry-cursor [state]
  (om/ref-cursor (:activity-graphic-mouse-over-entry (om/root-cursor state))))
(defn post-new-entry-bar-cursor [state]
  (om/ref-cursor (get-in (om/root-cursor state) [:post-new-entry-bar])))

(def entries :activity-graphic-data)
(def entry-screen-form :form)
(def selected-date (comp form-selected-date entry-screen-form))

(defn reducer
  [prev-state {:keys [action] :as payload}]
  ;(log "reducer %o" payload)
  (case action
    :receive-activity-graphic-data
    (-> prev-state
        (assoc-in [:form :selected-date :date] (:for-date payload))
        (assoc :activity-graphic-data (vec (:data payload))))

    :mouse-over-entry
    (assoc prev-state :activity-graphic-mouse-over-entry (:entry payload))

    :mouse-leave-entry
    (assoc prev-state :activity-graphic-mouse-over-entry {})

    :edit-entry
    (let [form-entry (-> (entries prev-state)
                         (entries-find-entry (:entry-id payload))
                         (form-data-entry->form))]
      (-> prev-state
          (assoc :form form-entry)
          (assoc :activity-graphic-mouse-over-entry {})))

    :select-task
    (assoc-in prev-state [:form :selected-task] (:task payload))


    :cancel-entry (update prev-state :form assoc
                          :submitted? false
                          :form-at-submit-time nil
                          :selected-task {}
                          :post-entry-form {:start-time ""
                                            :end-time ""
                                            :billable? false
                                            :comment ""
                                            :entry-id "new"})

    :entry-form-show-errors (update prev-state :form assoc
                                    :submitted? true
                                    :form-at-submit-time (:form payload))

    :change-comment (assoc-in prev-state [:form :post-entry-form :comment]
                              (:comment payload))

    :change-start-time (assoc-in prev-state [:form :post-entry-form :start-time]
                                 (:start-time payload))

    :change-end-time (assoc-in prev-state [:form :post-entry-form :end-time]
                               (:end-time payload))

    :change-billable? (assoc-in prev-state [:form :post-entry-form :billable?]
                                (:billable? payload))

    prev-state))
