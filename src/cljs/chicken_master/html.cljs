(ns chicken-master.html
  (:require [re-frame.core :as re-frame]
            [chicken-master.events :as event]))

(defn extract-input [elem]
  (condp = (.-tagName elem)
    "CHECKBOX" [elem (.-checked elem)]
    "INPUT" [(.-name elem) (if (-> (.-type elem) clojure.string/lower-case #{"checkbox"})
                             (.-checked elem)
                             (.-value elem))]
    "SELECT" [(.-name elem) (some->> elem
                                     (filter #(.-selected %))
                                     first
                                     .-value)]
    nil))

(defn form-values [form]
  (some->> form
           .-elements
           (map extract-input)
           (remove nil?)
           (into {})))

(defn input
  ([id label] (input id label {}))
  ([id label options]
     [:div {:class :input-item}
      (if label [:label {:for id} label])
      [:input (-> options
                  (assoc :defaultValue (:default options))
                  (dissoc :default)
                  (merge {:name id :id id}))]]))


(defn modal
  ([modal-id content]
   [:div {:class :popup}
    [:div {:class :popup-content}
     content
     [:div {:class :form-buttons}
      [:button {:type :button :on-click #(re-frame/dispatch [::event/hide-modal modal-id])} "ok"]]]])
  ([modal-id content on-submit]
   [:div {:class :popup}
    [:form {:action "#"
            :class :popup-content
            :on-submit (fn [e]
                         (.preventDefault e)
                         (when (-> e .-target form-values on-submit)
                           (re-frame/dispatch [::event/hide-modal modal-id])))}
     content
     [:div {:class :form-buttons}
      [:button "ok"]
      [:button {:type :button :on-click #(re-frame/dispatch [::event/hide-modal modal-id])} "anuluj"]]]]))


(comment
  (->> (.getElementsByTagName js/document "form")
       first
       .-elements
       (map extract-input)))
