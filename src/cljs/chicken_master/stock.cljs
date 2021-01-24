(ns chicken-master.stock
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.config :refer [settings]]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))


(defn num-or-nil [val]
  (let [i (js/parseInt val)]
    (when-not (js/isNaN i) i)))


(defn show-available []
  (html/modal
   :stock
   [:div {:class :stock-modal}
    [:button {:class :add-product :type :button :on-click #(re-frame/dispatch [::event/add-stock-product])} "dodaj"]
    [:h2 "Magazyn"]
    (for [[product amount] @(re-frame/subscribe [::subs/available-products])]
      [:div {:key (gensym) :class :stock-product}
       [:span {:class :product-name} product]
       [:div {:class :stock-product-amount}
        [:button {:type :button :on-click #(re-frame/dispatch [::event/update-product-stock product -1])} "-"]
        (html/input (name product) ""
                    {:type :number :default (or amount 0) :min 0
                     :on-blur #(re-frame/dispatch [::event/set-stock-amount product (-> % .-target .-value js/parseInt)])
                     })
        [:button {:type :button :on-click #(re-frame/dispatch [::event/update-product-stock product 1])} "+"]
        ]])]
   ;; On success
   (fn [form]
     (->> form
          (reduce-kv #(assoc %1 (keyword %2) (num-or-nil %3)) {})
          (conj [::event/save-stock])
          re-frame/dispatch))))
