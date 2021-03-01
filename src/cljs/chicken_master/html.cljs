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
   [:div {:class :popup :on-click #(re-frame/dispatch [::event/hide-modal modal-id])}
    [:div {:class :popup-content :on-click #(.stopPropagation %)}
     content
     [:div {:class :form-buttons}
      [:button {:type :button :on-click #(re-frame/dispatch [::event/hide-modal modal-id])} "ok"]]]])
  ([modal-id content & {:keys [on-submit submit-text]
                         :or {submit-text "ok"}}]
   [:div {:class :popup :on-click #(re-frame/dispatch [::event/hide-modal modal-id])}
    [:form {:action "#"
            :class :popup-content
            :on-click #(.stopPropagation %)
            :on-submit (fn [e]
                         (.preventDefault e)
                         (when (-> e .-target form-values on-submit)
                           (re-frame/dispatch [::event/hide-modal modal-id])))}
     content
     [:div {:class :form-buttons}
      [:button submit-text]
      [:button {:type :button :on-click #(re-frame/dispatch [::event/hide-modal modal-id])} "anuluj"]]]]))


(comment
  (->> (.getElementsByTagName js/document "form")
       first
       .-elements
       (map extract-input)))
