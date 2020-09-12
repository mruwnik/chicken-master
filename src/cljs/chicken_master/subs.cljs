(ns chicken-master.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))

(def settings {:first-day-offset 1
               :day-names ["Niedz" "Pon" "Wt" "Śr" "Czw" "Pt" "Sob"]
               :always-day-names true
               :show-order-time false})
