(ns chicken-master.time
  (:import [java.time Instant LocalDate ZoneOffset]
           [java.time.format DateTimeFormatter]
           [java.sql Timestamp]
           [org.dmfs.rfc5545.recur RecurrenceRule]
           [org.dmfs.rfc5545 DateTime]))

(defn parse-date [date]
  (if (= (count date) 10)
    (-> date (LocalDate/parse) (.atStartOfDay) (.toInstant ZoneOffset/UTC))
    (Instant/parse date)))

(defprotocol TimeHelpers
  (to-inst [d])
  (to-db-date [d])
  (format-date [date])
  (before [d1 d2])
  (after [d1 d2]))

(extend-type Instant
  TimeHelpers
  (to-inst [d] d)
  (to-db-date [d] (Timestamp/from d))
  (format-date [date]
    (-> DateTimeFormatter/ISO_LOCAL_DATE
        (.withZone ZoneOffset/UTC)
        (.format date)))
  (before [d1 d2] (.isBefore d1 d2))
  (after [d1 d2] (.isBefore d2 d1)))

(extend-type java.util.Date
  TimeHelpers
  (to-inst [d] (.toInstant d))
  (to-db-date [d] (-> d to-inst to-db-date))
  (format-date [date] (format-date (to-inst date)))
  (before [d1 d2] (< (.compareTo d1 d2) 0))
  (after [d1 d2] (> (.compareTo d1 d2) 0)))

(extend-type java.lang.String
  TimeHelpers
  (to-inst [d] (parse-date d))
  (to-db-date [d] (-> d to-inst to-db-date))
  (format-date [date] (format-date (to-inst date)))
  (before [d1 d2] (before (to-inst d1) (to-inst d2)))
  (after [d1 d2] (after (to-inst d1) (to-inst d2))))

(defn earliest [& ds] (->> ds (map to-inst) (sort before) first))
(defn latest [& ds] (->> ds (map to-inst) (sort after) first))
(defn between [d1 d2 d3] (and (not (before d2 d1)) (not (after d2 d3))))

(defn now [] (Instant/now))
(def min-date (parse-date "2020-01-01"))
(def max-date (.plusSeconds (now) (* 40 356 24 60 60))) ; 40 years from now - can't be bothered to do this properly...

(defn recurrence->dates [start rule]
  (let [iterator (.iterator (RecurrenceRule. rule)
                            (-> start (.toEpochMilli) (DateTime.)))]
    (take-while identity
                (repeatedly #(when (.hasNext iterator)
                               (-> iterator (.nextDateTime) (.getTimestamp) (Instant/ofEpochMilli)))))))

(defn last-date
  "Get the end date for the given rule"
  [start rule]
  (->> (recurrence->dates (to-inst start) rule)
       (take-while #(before % max-date))
       last))
