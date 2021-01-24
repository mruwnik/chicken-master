(ns chicken-master.calendar
  (:require
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [chicken-master.config :refer [settings]]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.products :as prod]
   [chicken-master.events :as event]
   [chicken-master.time :as time]))

(defn format-raw-order [{:strs [who notes] :as raw-values}]
  {:who who
   :notes notes
   :products (->> raw-values
                  (remove (comp #{"who" "notes"} first))
                  (map (fn [[k v]] [(str/split k "-") v]))
                  (group-by (comp last first))
                  (map #(sort-by first (second %)))
                  (map (fn [[[_ amount] [_ product]]] [(keyword product) (js/parseInt amount)]))
                  (group-by first)
                  (map (fn [[product items]] [product (->> items (map last) (reduce +))]))
                  (into {}))})

(defn edit-order []
  (html/modal
   [:div
    (html/input :who "kto"
           {:required true
            :default @(re-frame/subscribe [::subs/order-edit-who])})
    (html/input :notes "notka"
           {:default @(re-frame/subscribe [::subs/order-edit-notes])})
    (let [available-prods @(re-frame/subscribe [::subs/available-products])
          selected-prods  @(re-frame/subscribe [::subs/order-edit-products])]
      [:div {:class :product-items-edit}
       (for [[i {product :prod amount :amount}] (map-indexed vector selected-prods)]
         (prod/product-item product amount available-prods i))])
    [:button {:type :button :on-click #(re-frame/dispatch [::event/add-product])} "+"]]
   ;; On success
   (fn [form] (re-frame/dispatch [::event/save-order (format-raw-order form)]))))

(defn format-order [{:keys [id who day hour notes products state]}]
  [:div {:class [:order state] :key (gensym)}
   [:div {:class :actions}
    (condp = state
      :waiting   [:button {:on-click #(re-frame/dispatch [::event/fulfill-order id])} "✓"]
      :fulfilled [:button {:on-click #(re-frame/dispatch [::event/reset-order id])} "X"]
      :pending nil
      nil nil)
    [:button {:on-click #(re-frame/dispatch [::event/edit-order day id])} "E"]
    [:button {:on-click #(re-frame/dispatch
                          [::event/confirm-action
                           "na pewno usunąć?"
                           ::event/remove-order id])} "-"]]
   [:div {:class :who} who]
   (if (settings :show-order-time)
     [:div {:class :when} hour])
   (if (and (settings :show-order-notes) notes)
     [:div {:class :notes} notes])
   (->> products
        (map prod/format-product)
        (into [:div {:class :products}]))])

(defn day [{:keys [date customers]}]
  [:div {:class [:day (when (time/today? date) :today)]}
   [:div {:class :day-header} (time/format-date date)]
   [:div
    [:div {:class :orders}
     (if (settings :hide-fulfilled-orders)
       (->> customers (remove (comp #{:fulfilled} :state)) (map format-order))
       (map format-order customers))
     [:button {:type :button
               :on-click #(re-frame/dispatch [::event/edit-order (time/iso-date date)])} "+"]
     (when (seq (map :products customers))
       [:div {:class :summary}
        [:div {:class :header} "w sumie:"]
        (->> customers
             (map :products)
             (apply merge-with +)
             (sort-by first)
             (map prod/format-product)
             (into [:div {:class :products-sum}]))])]]])

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
