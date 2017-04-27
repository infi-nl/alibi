(ns alibi.domain.entry.repository)

(defprotocol EntryRepository
  (-add-entry! [this entry])
  (-find-entry [this entry-id])
  (-save-entry! [this entry])
  (-delete-entry! [this entry-id]))

(def ^:private ^:dynamic *impl*)

(defmacro with-impl [impl & body]
  `(binding [*impl* ~impl]
     ~@body))

(defn add-entry! [entry]
  (-add-entry! *impl* entry))

(defn find-entry [entry-id]
  (-find-entry *impl* entry-id))

(defn delete-entry! [entry]
  (-delete-entry! *impl* (:entry-id entry)))

(defn save-entry!
  [entry]
  (-save-entry! *impl* entry))
