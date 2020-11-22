(ns chicken-master.html
  (:require [re-frame.core :as re-frame]
            [chicken-master.events :as event]))


(defn input
  ([id label] (input id label {}))
  ([id label options]
     [:div {:class :input-item}
      [:label {:for id} label]
      [:input (-> options
                  (assoc :defaultValue (:default options))
                  (dissoc :default)
                  (merge {:name id :id id}))]]))


(defn modal [content on-submit]
  [:div {:class :popup}
   [:form {:action "#"
           :on-submit (fn [e]
                        (.preventDefault e)
                        (when (on-submit)
                          (re-frame/dispatch [::event/hide-modal])))}
    content
    [:div {:class :form-buttons}
     [:button "add"]
     [:button {:type :button :on-click #(re-frame/dispatch [::event/hide-modal])} "cancel"]]]])
