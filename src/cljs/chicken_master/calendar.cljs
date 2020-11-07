(ns chicken-master.calendar
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.config :refer [settings]]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.products :as prod]
   [chicken-master.events :as event]
   [chicken-master.time :as time]))


(defn add-order [date]
  (html/modal
   [:div
    (html/input :who "kto"
           {:required true
            :default @(re-frame/subscribe [::subs/order-edit-who])})
    (html/input :when "kiedy"
           {:type :time :step 60
            :default @(re-frame/subscribe [::subs/order-edit-when])})
    (let [available-prods @(re-frame/subscribe [::subs/available-products])
          selected-prods  @(re-frame/subscribe [::subs/order-edit-products])]
      [:div {}
       [:label "co"]
       (for [[i {product :prod amount :amount}] (map-indexed vector selected-prods)]
         (prod/product-item product amount available-prods i))])
    [:button {:type :button :on-click #(re-frame/dispatch [::event/add-product])} "+"]]
   js/console.log))

(defn format-order [{:keys [id who day hour products]}]
  [:li {:class :order :key (gensym)}
   [:div {:class :actions}
    [:button "O"]
    [:button {:on-click #(re-frame/dispatch [::event/edit-order day id])} "E"]
    [:button "-"]]
   [:div {:class :who} who]
   (if (settings :show-order-time)
     [:div {:class :when} hour])
   (->> products
        (map prod/format-product)
        (into [:ul {:class :products}]))])

(defn day [{:keys [date customers]}]
  [:div {:class [:day (when (time/today? date) :today)]}
   [:div {:class :day-header} (time/format-date date)]
   [:div
    [:ul {:class :orders}
     (map format-order customers)
     [:button {:type :button
               :on-click #(re-frame/dispatch [::event/edit-order date])} "+"]]]])

(defn calendar-header []
  (->> (settings :day-names)
       (map (fn [day] [:div {:class :day-header} day]))
       (into [])))

(defn calendar [days]
  (->> days
       (map day)
       (concat (when-not (settings :always-day-names) (calendar-header)))
       (into [:div {:class [:calendar :full-height]}])))
