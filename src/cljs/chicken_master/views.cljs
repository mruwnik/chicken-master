(ns chicken-master.views
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.subs :as subs]
   [chicken-master.calendar :as cal]
   [chicken-master.time :as time]))


(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div {:class :full-height}
     (when @(re-frame/subscribe [::subs/show-edit-modal])
       (cal/edit-order))
     (cal/calendar @(re-frame/subscribe [::subs/current-days]))
     ]))
