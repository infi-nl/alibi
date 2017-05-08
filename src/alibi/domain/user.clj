(ns alibi.domain.user)

(def ^:private ^:dynamic *implementation*)

(defmacro with-impl [impl & body]
  `(binding [*implementation* ~impl]
     ~@body))

(defprotocol UserRepository
  (-user-exists? [this user-id]))

(defn user-exists? [user-id]
  (-user-exists? *implementation* user-id))
