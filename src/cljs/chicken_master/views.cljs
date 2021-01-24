(ns chicken-master.views
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.subs :as subs]
   [chicken-master.calendar :as cal]
   [chicken-master.events :as event]))


(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div {:class :full-height}
     [:button {:id :scroll-up-button :class :scroll-button :on-click #(re-frame/dispatch [::event/scroll-weeks -2])} "^"]
     [:div {:class :scroll-bar}
      [:button {:id :scroll-up :on-click #(re-frame/dispatch [::event/scroll-weeks -2])} "^"]
      [:button {:id :scroll-down :on-click #(re-frame/dispatch [::event/scroll-weeks 2])} "v"]]
     (when @(re-frame/subscribe [::subs/show-edit-modal])
       (cal/edit-order))
     (cal/calendar @(re-frame/subscribe [::subs/current-days]))
     [:button {:id :scroll-down-button :class :scroll-button :on-click #(re-frame/dispatch [::event/scroll-weeks 2])} "v"]
     ]))
