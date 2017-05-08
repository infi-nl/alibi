(ns alibi.domain.task
  (:refer-clojure :exclude [get])
  (:require
    [alibi.domain.billing-method :refer [billing-method?]]
    [clojure.set :refer [rename-keys]]))

(defrecord Task [task-id billing-method name project-id])

(defn hydrate-task
  [{:keys [task-id billing-method] :as cmd}]
  {:pre [(integer? task-id)
         (billing-method? billing-method)]}
  (map->Task cmd))

(defn task? [o] (instance? Task o))

(defn new-task
  [{:keys [for-project-id task-name billing-method]}]
  {:pre [(and (string? task-name) (seq task-name))
         (integer? for-project-id)]}
  (hydrate-task {:task-id 0
                 :name task-name
                 :project-id for-project-id
                 :billing-method billing-method}))

(defprotocol TaskRepository
  (-task-exists? [this task-id])
  (-get [this task-id])
  (-project-id-for-task-id [this task-id])
  (-add! [this task]))

(def ^:private ^:dynamic *repository-impl*)

(defmacro with-impl [impl & body]
  `(binding [*repository-impl* ~impl]
     ~@body))

(defn task-exists? [task-id]
  (-task-exists? *repository-impl* task-id))

(defn get [task-id]
  (when task-id
    (-get *repository-impl* task-id)))

(defn add! [task]
  (-add! *repository-impl* task))

(defn project-id-for-task-id [task-id]
  (-project-id-for-task-id *repository-impl* task-id))
