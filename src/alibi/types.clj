(ns alibi.types)

(defn str->int [val]
  (when (string? val)
    (try
      (Integer/parseInt val)
      (catch NumberFormatException e nil))))
