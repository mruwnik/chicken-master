(ns chicken-master.config)

(def debug?
  ^boolean goog.DEBUG)

(def settings {:first-day-offset 1
               :day-names ["Niedz" "Pon" "Wt" "Śr" "Czw" "Pt" "Sob"]
               :always-day-names true
               :show-order-time false})
