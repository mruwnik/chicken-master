(ns chicken-master.stock
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.config :refer [settings]]
   [chicken-master.products :as prod]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))


(defn show-available []
  (html/modal
   :stock
   [:div {:class :stock-modal}
    [:h2 "Magazyn"]
    (for [[product amount] @(re-frame/subscribe [::subs/available-products])]
      [:div {:key (gensym) :class :stock-product}
       [:span {:class :product-name} product]
       [:div {:class :stock-product-amount}
        [:button {:type :button :on-click #(re-frame/dispatch [::event/update-product-stock product -1])} "-"]
        (prod/number-input (name product) "" (or amount 0)
                      #(re-frame/dispatch [::event/set-stock-amount product (-> % .-target .-value prod/num-or-nil)]))
        [:button {:type :button :on-click #(re-frame/dispatch [::event/update-product-stock product 1])} "+"]
        [:button {:type :button :on-click #(re-frame/dispatch [::event/delete-product product])} "x"]
        ]])
    [prod/item-adder :callback #(re-frame/dispatch [::event/set-stock-amount % 0]) :button "+"]
    ]
   ;; On success
   (fn [form]
     (->> form
          (reduce-kv #(if-let [val (prod/num-or-nil %3)]
                        (assoc %1 (keyword %2) val)
                        %1)
                     {})
          (conj [::event/save-stock])
          re-frame/dispatch))))
