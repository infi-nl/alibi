(ns alibi.domain.task.repository
  (:refer-clojure :exclude [get]))

(defprotocol TaskRepository
  (-task-exists? [this task-id])
  (-get [this task-id])
  (-project-id-for-task-id [this task-id])
  (-add! [this task]))

(def ^:private ^:dynamic *impl*)

(defmacro with-impl [impl & body]
  `(binding [*impl* ~impl]
     ~@body))

(defn task-exists? [task-id]
  (-task-exists? *impl* task-id))

(defn get [task-id]
  (when task-id
    (-get *impl* task-id)))

(defn add! [task]
  (-add! *impl* task))

(defn project-id-for-task-id
  [task-id]
  (-project-id-for-task-id *impl* task-id))
