(ns alibi.domain.project
  (:refer-clojure :exclude [get])
  (:require
    [alibi.domain.billing-method :refer [billing-method?]]
    [clojure.set :refer [rename-keys]]))

(defrecord Project [project-id project-name billing-method])

(defn project? [p] (instance? Project p))

(defn hydrate-project
  [{:keys [project-id billing-method] :as project}]
  {:pre [(billing-method? billing-method)
         (integer? project-id)]}
  (map->Project project))

(defn new-project
  [{:keys [project-name billing-method] :as cmd}]
  {:pre [(and (string? project-name) (seq project-name))]}
  (hydrate-project {:project-id 0
                    :billing-method billing-method
                    :project-name project-name}))

(def ^:private ^:dynamic *repo-implementation*)

(defmacro with-repo-impl [impl & body]
  `(binding [*repo-implementation* ~impl]
     ~@body))

(defprotocol ProjectRepository
  (-get [this project-id])
  (-add! [this project])
  (-exists? [this project-id]))

(defn get [project-id]
  (-get *repo-implementation* project-id))

(defn add! [project]
  (-add! *repo-implementation* project))

(defn exists? [project-id]
  (-exists? *repo-implementation* project-id))
