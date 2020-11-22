(ns chicken-master.calendar
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.config :refer [settings]]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.products :as prod]
   [chicken-master.events :as event]
   [chicken-master.time :as time]))


(defn edit-order []
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
   ;; On success
   (fn [] (re-frame/dispatch [::event/save-order]))))

(defn format-order [{:keys [id who day hour products state]}]
  [:li {:class [:order state] :key (gensym)}
   [:div {:class :actions}
    (condp = state
      :waiting   [:button {:on-click #(re-frame/dispatch [::event/fulfill-order id])} "✓"]
      :fulfilled [:button {:on-click #(re-frame/dispatch [::event/reset-order id])} "X"]
      :pending nil
      :default nil)
    [:button {:on-click #(re-frame/dispatch [::event/edit-order day id])} "E"]
    [:button {:on-click #(re-frame/dispatch [::event/remove-order id])} "-"]]
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
     (if (settings :hide-fulfilled-orders)
       (->> customers (remove (comp #{:fulfilled} :state)) (map format-order))
       (map format-order customers))
     [:button {:type :button
               :on-click #(re-frame/dispatch [::event/edit-order (time/iso-date date)])} "+"]]]])

(defn calendar-header []
  (->> (:day-names settings)
       cycle (drop (:first-day-offset settings))
       (take 7)
       (map (fn [day] [:div {:class :day-header} day]))
       (into [])))

(defn calendar [days]
  (->> days
       (map day)
       (concat (when (settings :calendar-heading) (calendar-header)))
       (into [:div {:class [:calendar :full-height]}])))
