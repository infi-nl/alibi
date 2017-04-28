(ns time.core
  (:require [clojure.string :as string]))

(def LocalDate (. js/JSJoda -LocalDate))
(def DayOfWeek (. js/JSJoda -DayOfWeek))

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

(def ^:private time-formatter (js/JSJoda.DateTimeFormatter.ofPattern "HH:mm"))

(defn str->unix [date-str time-str]
  (.. (js/JSJoda.LocalDate.parse  date-str)
      (atTime (js/JSJoda.LocalTime.parse  time-str time-formatter))
      (atZone (js/JSJoda.ZoneId.systemDefault ))
      (toInstant)
      (epochSecond)))

(defn unix->time-str [epoch]
  (.format (js/JSJoda.LocalTime.ofInstant (js/JSJoda.Instant.ofEpochSecond epoch))
           time-formatter))

(defn unix->date-str [epoch]
  (.toString (js/JSJoda.LocalDate.ofInstant (js/JSJoda.Instant.ofEpochSecond epoch))))

(defn try-parse-time [v]
  (when-not (= v "24:00") ; 24:00 is parsed to 0:00, everything above 24:00
                          ; fails to parse
    (try
      (.. js/JSJoda -LocalTime (from (. time-formatter parse v)))
      (catch js/Error e
        nil))))

(defn find-monday-before [for-date]
  (if (string? for-date) (recur (. LocalDate parse for-date))
    (if (= (. for-date dayOfWeek) (.-MONDAY DayOfWeek))
      for-date
      (recur (. for-date plusDays -1)))))
