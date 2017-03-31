(ns alibi.logging)

(defn log [& more]
  (apply (.-log js/console) more))

(defn log-cljs [& more]
  (apply log (map clj->js more)))

