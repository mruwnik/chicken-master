(ns chicken-master.time
  (:import [java.time Instant LocalDate ZoneOffset]
           [java.time.format DateTimeFormatter]
           [java.sql Timestamp]))


(defn parse-date [date]
  (-> date (LocalDate/parse) (.atStartOfDay) (.toInstant ZoneOffset/UTC)))

(defn format-date [date]
  (-> DateTimeFormatter/ISO_LOCAL_DATE
      (.withZone ZoneOffset/UTC)
      (.format date)))

(defn inst->timestamp [inst] (Timestamp/from inst))

(defn now [] (Instant/now))
