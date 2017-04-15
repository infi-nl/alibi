(ns alibi.logging)

(def log-count (atom 0))

(defn log [first & more]
  (swap! log-count inc)
  (apply (.-log js/console) (str @log-count " " first) more))

(defn log-cljs [& more]
  (apply log (map clj->js more)))

