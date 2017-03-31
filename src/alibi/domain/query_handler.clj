(ns alibi.domain.query-handler)

(def ^:dynamic *handler* nil)

(defmacro with-handler [handler & body]
  `(binding [*handler* ~handler]
     ~@body))

(defn handle [type & args]
  (let [handler (*handler* type)]
    (assert handler (str "handler not found for " type))
    (handler args)))
