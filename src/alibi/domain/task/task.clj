(ns alibi.domain.task.task
  (:require
    [alibi.domain.billing-method :refer [billing-method?]]
    [clojure.set :refer [rename-keys]]))

(defrecord Task [task-id billing-method name project-id])

(defn hydrate-task
  [{:keys [task-id billing-method] :as cmd}]
  {:pre [(integer? task-id)
         (billing-method? billing-method)]}
  (map->Task cmd))

(defn task?
  [o]
  (instance? Task o))

(defn new-task
  [{:keys [for-project-id task-name billing-method]}]
  {:pre [(and (string? task-name) (seq task-name))
         (integer? for-project-id)]}
  (hydrate-task {:task-id 0
                 :name task-name
                 :project-id for-project-id
                 :billing-method billing-method}))
