(ns chicken-master.views
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.subs :as subs]
   [chicken-master.calendar :as cal]
   [chicken-master.time :as time]
   ))

(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div {:class :full-height}
     (cal/add-order (new js/Date))
     (-> (new js/Date)
         time/start-of-week
         (time/days-range 14)
         cal/calendar)
     ]))
