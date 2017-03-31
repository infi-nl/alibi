(ns alibi.domain.project.repository
  (:refer-clojure :exclude [get]))

(def ^:private ^:dynamic *implementation*)

(defmacro with-impl [impl & body]
  `(binding [*implementation* ~impl]
     ~@body))

(defprotocol ProjectRepository
  (-get [this project-id])
  (-add! [this project])
  (-exists? [this project-id]))

(defn get [project-id]
  (-get *implementation* project-id))

(def get-project get)

(defn add! [project]
  (-add! *implementation* project))

(defn exists? [project-id]
  (-exists? *implementation* project-id))
