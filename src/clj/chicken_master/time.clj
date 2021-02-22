(ns chicken-master.time
  (:import [java.time Instant LocalDate ZoneOffset]
           [java.sql Timestamp]))


(defn parse-date [date]
  (-> date (LocalDate/parse) (.atStartOfDay) (.toInstant ZoneOffset/UTC)))

(defn inst->timestamp [inst] (Timestamp/from inst))

(defn now [] (Instant/now))
