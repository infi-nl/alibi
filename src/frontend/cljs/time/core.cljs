(ns time.core
  (:require [clojure.string :as string]))

(defn parse-float [v]
  (js/parseFloat v))

(defn expand-time [time-val]
  (let [match-table
        [;1 => 01:00
         [#"[0-9]{1}" (constantly true) #(str "0" % ":00")]

         ;13 => 13:00
         [#"([0-9]{2})" (fn [_ hours] (< (parse-float hours) 24))
          #(str % ":00")]

         ;130 -> 1:30
         [#"([0-9]{1})([0-9]{2})" (fn [_ hrs mins] (< (parse-float mins) 60))
          (fn [_ hrs mins] (str "0" hrs ":" mins))]

         ;1300 -> 13:00
         [#"([0-9]{2})([0-9]{2})" (fn [_ hrs mins]
                                    (and (< (parse-float hrs) 24)
                                         (< (parse-float mins) 60)))
          (fn [_ hrs mins] (str hrs ":" mins))]

         ;13: -> 13:00
         [#"([0-9]{2}):" (fn[_ hrs] (< (parse-float hrs) 24))
          #(str % "00")]

         ;13:1 => 13:10
         [#"([0-9]{2}):([0-9]{1})" (fn [_ hrs mins]
                                     (and (< (parse-float hrs) 24)
                                          (< (parse-float mins) 6)))
          #(str % "0")]

         ;1:30 => 01:30
         [#"[0-9]{1}:([0-9]{2})" (fn [_ mins]
                                   (< (parse-float mins) 60))
          #(str "0" %)]]
        match-entry (fn [v [regex pred? make-value]]
                      (let [match (re-matches regex v)]
                        (when (and match (apply pred? match))
                          (apply make-value match))))]
  (cond
    (nil? time-val) time-val
    (string/index-of time-val ";") (expand-time
                                     (string/replace time-val ";" ":"))
    :else (if-let [match (some #(match-entry time-val %) match-table)]
            match
            time-val))))

(defn str->unix [date-str time-str]
  (let [time-formatter (js/JSJoda.DateTimeFormatter.ofPattern "HH:mm")]
    (.. (js/JSJoda.LocalDate.parse  date-str)
        (atTime (js/JSJoda.LocalTime.parse  time-str time-formatter))
        (atZone (js/JSJoda.ZoneId.systemDefault ))
        (toInstant)
        (epochSecond))))
