(ns alibi.test-helpers
  (:require [clojure.data :refer [diff]]
            [clojure.pprint :refer [pprint]]
            [clojure.test :as test :refer [is]]))

(defn- pprint-str [o]
  (with-out-str (pprint o)))

(defn map-difference [actual expected]
  (let [delta (vec (diff actual expected))]
    (str "only in actual:\n" (pprint-str (delta 0))
         "only in expected:\n" (pprint-str (delta 1))
         "in both:\n" (pprint-str (delta 2)))))

(defmacro is-equal
  ([actual expected]
   `(test/is (= ~actual ~expected) (map-difference ~actual ~expected)))
  ([actual expected msg]
   `(test/is (= ~actual ~expected) (str ~msg "\n" (map-difference ~actual ~expected)))))

(defn- all-tests [for-ns]
  (->> (ns-publics for-ns)
       (vals)
       (map meta)
       (filter ::test?)
       (map :name)
       (map (partial ns-resolve for-ns))))

(defmacro deftest [name & body]
  `(def ~(vary-meta name assoc ::test? true) (fn [] ~@body)))

(defn copy-tests [from-ns fixture]
  `(do
     ~@(let [tests (all-tests from-ns)]
         (for [test tests]
           (let [test-name (-> test meta :name)]
             `(clojure.test/deftest ~test-name
                (~fixture @~test)))))))
