(ns alibi.infra.jdbc-extensions
  (:require [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [clojure.java.jdbc :as jdbc])
  (:import [org.joda.time DateTime DateTimeZone]))

; adapted from clj-time.jdbc

(defn- get-jvm-timezone [] (System/getProperty "user.timezone"))

; we can't use DateTimeZone/getDefault because we change that later and there 
; is no method to get the system timezone
(def mysql-timezone (DateTimeZone/forID (get-jvm-timezone)))
(def db-timezone (time/time-zone-for-id "Europe/Amsterdam"))

(DateTimeZone/setDefault db-timezone)

; the idea here is that the db stores times in db-timezone, which
; means we need to do some conversion before and after storing
(defn- make-time-from-sql-value
  [v]
  (let [millis (.getTime v)]
    (. (DateTime. millis ^DateTimeZone mysql-timezone)
       (withZoneRetainFields db-timezone))))

(defn- make-sql-time
  [obj]
  (if-let [dt (tc/to-date-time obj)]
    (java.sql.Timestamp. (.. dt
                             (withZoneRetainFields mysql-timezone)
                             (getMillis)))))

; http://clojure.github.io/java.jdbc/#clojure.java.jdbc/IResultSetReadColumn
(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Timestamp
  (result-set-read-column [v _2 _3]
    (make-time-from-sql-value v))
  java.sql.Date
  (result-set-read-column [v _2 _3]
    (tc/from-sql-date v))
  java.sql.Time
  (result-set-read-column [v _2 _3]
    (org.joda.time.DateTime. v)))

(extend-protocol jdbc/ISQLValue
  org.joda.time.DateTime
  (sql-value [v]
    (make-sql-time v)))
