(ns alibi.application.screens.entry-screen
  (:require
    [alibi.domain.task.repository :refer [project-id-for-task-id]]
    [alibi.domain.entry-app-svc :as svc]
    [bouncer.core :refer [validate]]
    [bouncer.validators :as v :refer [defvalidator]]
    [clojure.set :refer [rename-keys]]
    [ring.util.response :as response]
    [clojure.walk :refer [keywordize-keys]]
    [alibi.types :refer [str->int]]
    [alibi.domain.query-handler :as queries]
    [alibi.infra.date-time :refer [format-date
                                                plus
                                                today
                                                local-date?
                                                str->local-date
                                                str->local-time]]
    [clojure.data.json :as json]))

(defn- entries-for-day
  [for-date user-id]
  {:entries (queries/handle :entry-screen/entries-for-day
                            :user-id user-id
                            :day for-date)
   :formatted-date (.toLowerCase
                     (format-date "d MMM yyyy" for-date))
   :for-date for-date})

(defn- find-first-monday
  [dt]
  (if (= (.getDayOfWeek dt) org.joda.time.DateTimeConstants/MONDAY)
    dt
    (recur (.plusDays dt -1))))

(defn- view-data-activity-graphic
  [for-date identity]
  (let [first-monday (find-first-monday for-date)
        first-monday-formatted (format-date first-monday)
        data (queries/handle :entry-screen/activity-graphic
                             :user-id (get identity :id)
                             :from first-monday)]
    {:from-date first-monday-formatted
     :data data}))

(defn- view-data-projects-tasks
  [projects-tasks]
  {:projects-by-id
   (->> projects-tasks
        (map (fn [{:keys [project-id project-name]}]
               [project-id project-name]))
        distinct
        (into {}))
   :tasks-by-id
   (->> projects-tasks
        (map (fn [{:keys [task-id task-name]}]
               [task-id task-name]))
        distinct
        (into {}))})

(defn- bookable-project-task->option
  [entry]
  (let [display-name (str (:project-name entry) " / "
                          (:task-name entry))]
    {:value (str (:project-id entry) "," (:task-id entry))
     :project-id (:project-id entry)
     :task-id (:task-id entry)
     :text display-name
     :billing-method (:billing-method entry)}))

(defn- view-data
  [{:keys [for-date
           selected-project-id
           selected-task-id
           identity
           start-time
           end-time
           comment
           billable?] :as client-state}]
  (let [date-formatted (format-date for-date)
        bookable-projects-and-tasks (queries/handle :entry-screen/list-all-bookable-projects-and-tasks)]
    {:entries-for-day (entries-for-day for-date (get identity :id))
     :view-data
     (pr-str
       {:projects-tasks (view-data-projects-tasks bookable-projects-and-tasks)
        :activity-graphic (view-data-activity-graphic for-date identity)
        :initial-state
        {:post-new-entry-bar {:options (map bookable-project-task->option
                                            bookable-projects-and-tasks)}
         :selected-item (when (and (integer? selected-project-id)
                                   (integer? selected-task-id))
                          {:projectId selected-project-id
                           :taskId selected-task-id})
         :selected-date date-formatted
         :post-entry-form {:startTime start-time
                           :endTime end-time
                           :isBillable billable?
                           :comment comment}}})}))

(defn- default-client-state
  [for-date identity selected-task-id]
  (cond-> {:identity identity
           :selected-project-id ""
           :selected-task-id  ""
           :comment ""
           :for-date for-date
           :start-time ""
           :end-time ""
           :billable? false}
    selected-task-id
    (assoc :selected-task-id selected-task-id
           :selected-project-id (project-id-for-task-id selected-task-id))))

(defn- build-client-state-from-post
  [request]
  (-> (:form-params request)
      keywordize-keys
      (select-keys [:selected-project-id :selected-task-id :comment :for-date
                    :start-time :end-time :billable? :entry-id
                    :delete-entry])
      (update :billable? boolean)
      (update :for-date str->local-date)
      (update :selected-task-id str->int)
      (update :selected-project-id str->int)
      (update :entry-id str->int)
      (assoc :identity (:identity request)
             :submitted true)))

(defn- client-state->new-entry-cmd
  [state]
  (-> state
      (update :end-time str->local-time)
      (update :start-time str->local-time)
      (assoc :user-id (get-in state [:identity :id]))
      (rename-keys {:selected-task-id :task-id
                    :selected-project-id :project-id})))

(defn- update-entry! [cs]
  (svc/update-entry!
    (get-in cs [:identity :id])
    {:entry-id (:entry-id cs)
     :start-time (str->local-time (:start-time cs))
     :end-time (str->local-time (:end-time cs))
     :for-date (:for-date cs)
     :task-id (:selected-task-id cs)
     :billable? (:billable? cs)
     :comment (:comment cs)})
  (response/redirect (str "/entry/" (:for-date cs))))

(defn- post-new-entry! [client-state]
  (svc/post-new-entry! (client-state->new-entry-cmd client-state))
  (response/redirect (str "/entry/"
                          (:for-date client-state)
                          "?selected-task-id="
                          (:selected-task-id client-state))))

(defn- delete-entry! [client-state]
  (svc/delete-entry! (get-in client-state [:identity :id])
                     {:entry-id (:entry-id client-state)})
  (response/redirect (str "/entry/" (:for-date client-state))))

(defn post
  [request]
  (let [client-state (build-client-state-from-post request)]
    (cond
      (and (:entry-id client-state) (find client-state :delete-entry))
      (delete-entry! client-state)

      (:entry-id client-state) (update-entry! client-state)

      :else (post-new-entry! client-state))))

(defn render
  [client-state]
  (response/response
    {:template-data
     (assoc (view-data client-state)
            :identity (:identity client-state))
     :selmer-template "entry.html"}))

(defn get-page
  [for-date {{selected-task-id "selected-task-id"} :params :as request}]
  (render
    (default-client-state for-date
      (:identity request)
      (str->int selected-task-id))))

(defn activity-graphic
  [{{from "from" :as params} :params :as request}]
  {:pre [(str->local-date from)]}
  (let [data (queries/handle :entry-screen/activity-graphic
                             :user-id (get-in request [:identity :id])
                             :from (str->local-date from))]
    (->
      data
      (json/write-str)
      (response/response)
      (response/content-type "application/json"))))

(defn day-entries-table
  [{{for-date "for-date"} :params identity :identity}]
  {:pre [(str->local-date for-date)]}
  (let [data (entries-for-day (str->local-date for-date) (get identity :id))]
    (-> {:selmer-template "day-entries-table.html"}
        (assoc :template-data {:entries-for-day data})
        (response/response))))

