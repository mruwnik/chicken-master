(ns chicken-master.calendar
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.config :refer [settings]]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.products :as prod]
   [chicken-master.events :as event]
   [chicken-master.time :as time]))

(defn format-raw-order [{:strs [who who-id notes] :as raw-values}]
  {:who {:name who :id (prod/num-or-nil who-id)}
   :notes notes
   :products (prod/collect-products (remove (comp #{"who" "notes"} first) raw-values))})

(defn edit-order []
  (html/modal
   :order-edit
   [:div
    (let [who @(re-frame/subscribe [::subs/order-edit-who])]
      [:div
       (html/input :who "kto" {:required true :default (:name who)})
       [:input {:id :who-id :name :who-id :type :hidden :value (or (:id who) "")}]])
    (html/input :notes "notka"
           {:default @(re-frame/subscribe [::subs/order-edit-notes])})
    [prod/products-edit @(re-frame/subscribe [::subs/order-edit-products])]]
   ;; On success
   (fn [form] (re-frame/dispatch [::event/save-order (format-raw-order form)]))))

(defn format-order [{:keys [id who day hour notes products state]}]
  [:div {:class [:order state] :key (gensym)
         :draggable true
         :on-drag-start #(-> % .-dataTransfer (.setData "text" id))}
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
   [:div {:class :who} (:name who)]
   (if (settings :show-order-time)
     [:div {:class :when} hour])
   (if (and (settings :show-order-notes) notes)
     [:div {:class :notes} notes])
   (->> products
        (map prod/format-product)
        (into [:div {:class :products}]))])

(defn day [[date orders]]
  [:div {:class [:day (when (-> date time/parse-date time/today?) :today)]
         :on-drag-over #(.preventDefault %)
         :on-drop #(let [id (-> % .-dataTransfer (.getData "text") prod/num-or-nil)]
                     (.preventDefault %)
                     (re-frame/dispatch [::event/move-order id date]))}
   [:div {:class :day-header} (-> date time/parse-date time/format-date)]
   [:div
    [:div {:class :orders}
     (if (settings :hide-fulfilled-orders)
       (->> orders (remove (comp #{:fulfilled} :state)) (map format-order))
       (map format-order orders))
     (when (settings :show-day-add-order)
       [:button {:type :button
                 :on-click #(re-frame/dispatch [::event/edit-order date])} "+"])
     (when (seq (map :products orders))
       [:div {:class :summary}
        [:hr {:class :day-seperator}]
        [:div {:class :header} "w sumie:"]
        (->> orders
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
