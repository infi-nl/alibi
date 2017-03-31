(ns alibi.datasource.sqlite.queries
  (:require
    [clojure.java.jdbc :as db]
    [clojure.set :refer [rename-keys]]
    [alibi.domain.billing-method :refer [billing-method?]]
    [alibi.infra.date-time
     :refer [tomorrow format-time-period int->seconds local-date? ->unix
             format-date ->local-date ->local-time seconds-between ->datetime]]
    [clojure.string :refer [capitalize]]))

(defn- list-all-bookable-projects-and-tasks [db-spec & _]
  (db/query
    db-spec
    "select p.name 'project-name', t.name 'task-name',
    p.id 'project-id', t.id 'task-id',
    t.billing_type billing_type
    from projects p join tasks t on t.project_id=p.id
    order by p.name asc, t.name asc"
    {:row-fn (fn [row]
               (-> row
                   (assoc :billing-method (capitalize (:billing_type row)))))}))

(defn entries-for-day [db-spec {:keys [day user-id] :as args}]
  (let [row-fn (fn [r]
                 (-> r
                     (update :billable? = 1)
                     (assoc :duration (-> (seconds-between
                                            (->local-time (:start-time r))
                                            (->local-time (:end-time r)))
                                          .getSeconds))))

        records (db/query
                  db-spec
                  ["select e.start_time 'start-time', e.end_time 'end-time',
                   e.comment, p.name project, t.name task,
                   e.is_billable 'billable?'
                   from entries e join tasks t on t.id=e.task_id
                   join projects p on p.id=t.project_id
                   where e.for_date>=? and e.for_date<?
                   and e.user_id=?
                   order by e.for_date asc"
                   day (tomorrow day) user-id]
                  {:row-fn row-fn})]

    {:entries (vec (map #(update % :duration
                                 (comp format-time-period int->seconds))
                        records))
     :aggregates {:total-duration (->> records
                                       (map :duration)
                                       (reduce +)
                                       int->seconds
                                       format-time-period)}}))

(defn activity-graphic [db-spec {:keys [from user-id]}]
  {:pre [(local-date? from)
         (integer? user-id)]}
  (let [till (. from plusDays 7)]
    (db/query
      db-spec
      ["select e.task_id 'task-id', e.is_billable 'billable?',
       e.comment, e.start_time 'start-time', e.end_time 'end-time',
       e.for_date 'date', t.project_id 'project-id', t.name task,
       p.name 'project', e.id 'entry-id'
       from entries e join tasks t on t.id=e.task_id
       join projects p on p.id=t.project_id
       where e.for_date>=? and e.for_date<? and e.user_id=?"
       (str from) (str till) user-id]
      {:row-fn (fn [{:keys [date start-time end-time] :as row}]
                 (let [from (->datetime date start-time)
                       till (->datetime date end-time)]
                   (-> row
                       (update :billable? = 1)
                       (assoc :from (->unix from)
                              :till (->unix till)
                              :duration (.getSeconds (seconds-between from till)))
                       (dissoc :date :end-time :start-time))))})))


(defn handler [db-spec]
  {:entry-screen/list-all-bookable-projects-and-tasks
   (partial list-all-bookable-projects-and-tasks db-spec)

   :entry-screen/entries-for-day
   (partial entries-for-day db-spec)

   :entry-screen/activity-graphic
   (partial activity-graphic db-spec)})
