(ns chicken-master.stock
  (:require
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.config :as config]
   [chicken-master.products :as prod]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))

(defn stock-form [stock]
  (let [state (reagent/atom stock)]
    (fn []
      [:div
       (doall
        (for [[product {:keys [amount price]}] @state]
          [:div {:key (gensym) :class :stock-product}
           [:span {:class :product-name} product]
           [:div {:class :stock-product-amount}
            [:button {:type :button :on-click #(swap! state update-in [product :amount] dec)} "-"]
            (prod/number-input (str (name product) "-amount") "" (or amount 0)
                               #(swap! state assoc-in [product :amount] (-> % .-target .-value prod/num-or-nil)))
            [:button {:type :button :on-click #(swap! state update-in [product :amount] inc)} "+"]
            [:button {:type :button :on-click #(swap! state dissoc product)} "x"]]
           (when (config/settings :prices)
             [:div {:class :stock-product-price}
              (prod/number-input (str (name product) "-price") "cena" (prod/format-price price)
                                 #(swap! state assoc-in
                                         [product :price]
                                         (some-> % .-target .-value prod/num-or-nil prod/normalise-price)))])]))
       [prod/item-adder :callback #(swap! state assoc (keyword %) 0) :button "+"]])))

(defn process-form [form]
  (->> form
       (filter (comp prod/num-or-nil second))
       (map (fn [[k v]] [(str/split k #"-") (prod/num-or-nil v)]))
       (group-by ffirst)
       (map (fn [[k vals]] [(keyword k) (reduce #(assoc %1 (-> %2 first second keyword) (second %2)) {} vals)]))
       (map (fn [[k vals]] [k (update vals :price prod/normalise-price)]))
       (into {})))

(defn show-available []
  (html/modal
   :stock
   [:div {:class :stock-modal}
    [:h2 "Magazyn"]
    [stock-form @(re-frame/subscribe [::subs/available-products])]]
   ;; On success
   :on-submit (fn [form] (re-frame/dispatch [::event/save-stock (process-form form)]))))
