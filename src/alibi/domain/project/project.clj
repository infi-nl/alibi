(ns alibi.domain.project.project
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
