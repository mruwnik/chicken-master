(ns chicken-master.stock
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.products :as prod]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))

(defn stock-form [stock]
  (let [state (reagent/atom stock)]
    (fn []
      [:div
       (for [[product amount] @state]
         [:div {:key (gensym) :class :stock-product}
          [:span {:class :product-name} product]
          [:div {:class :stock-product-amount}
           [:button {:type :button :on-click #(swap! state update product dec)} "-"]
           (prod/number-input (name product) "" (or amount 0)
                              #(swap! state assoc product (-> % .-target .-value prod/num-or-nil)))
           [:button {:type :button :on-click #(swap! state update product inc)} "+"]
           [:button {:type :button :on-click #(swap! state dissoc product)} "x"]]])
       [prod/item-adder :callback #(swap! state assoc (keyword %) 0) :button "+"]])))

(defn show-available []
  (html/modal
   :stock
   [:div {:class :stock-modal}
    [:h2 "Magazyn"]
    [stock-form @(re-frame/subscribe [::subs/available-products])]]
   ;; On success
   :on-submit (fn [form]
                (->> form
                     (reduce-kv #(if-let [val (prod/num-or-nil %3)]
                                   (assoc %1 (keyword %2) val)
                                   %1)
                                {})
                     (conj [::event/save-stock])
                     re-frame/dispatch))))
