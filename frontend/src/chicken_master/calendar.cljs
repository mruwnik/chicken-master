(ns chicken-master.calendar
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.config :as config]
   [chicken-master.products :as prod]
   [chicken-master.events :as event]
   [chicken-master.time :as time]))

(defn format-raw-order [{:strs [day who who-id notes] :as raw-values}]
  {:who {:name who
         :id (if (prod/num-or-nil who-id)
               (prod/num-or-nil who-id)
               ;; seeing as there's an autocomplete thingy, assume that if the name is the same,
               ;; then so is the user
               (some->> @(re-frame/subscribe [::subs/available-customers])
                    (filter (comp #{who} :name))
                    first :id))}
   :day day
   :notes notes
   :products (prod/collect-products (remove (comp #{"who" "notes"} first) raw-values))})

(defn get-group-products [customers who]
  (some->> customers (filter (comp #{who} :name))
           first :product-groups
           (reduce-kv #(assoc %1 %2 (:products %3)) {})))

(defn calc-order-prices [{:keys [who products] :as order}]
  (->> products
       (reduce-kv (fn [coll prod {:keys [amount price]}]
                    (assoc-in coll [prod :final-price]
                              (prod/calc-price (:id who) prod price amount))) products)
       (assoc order :products)))

(defn merge-product-values [& products]
  (apply merge-with
         (fn [& values] (some->> values (remove nil?) seq (reduce +)))
         products))

(defn order-form
  ([order] (order-form order #{:who :day :notes :products :group-products}))
  ([order fields]
   (let [customers @(re-frame/subscribe [::subs/available-customers])
         available-prods @(re-frame/subscribe [::subs/available-products])
         state (-> (or order {})
                   (update :products reagent/atom)
                   (assoc :group-products
                          (get-group-products customers (-> order :who :name)))
                   reagent/atom)]
     (fn []
       [:div
        (when (:who fields)
          (let [who (:who @state)]
            [:div
             (html/input :who "kto" {:required true
                                     :default (:name who)
                                     :list :customers
                                     :on-change (fn [e]
                                                  (if-let [products (->> e .-target .-value (get-group-products customers))]
                                                    (swap! state assoc :group-products products)))})
             (into [:datalist {:id :customers}]
                   (for [cust customers] [:option {:value (:name cust) :id (:id cust)}]))
             [:input {:id :who-id :name :who-id :type :hidden :value (or (:id who) "")}]]))

        (when (:day fields)
          (html/input :day "dzień" {:type :date :required true :default (:day order)}))
        (when (and (:group-products fields) (-> @state :group-products seq))
          [prod/group-products state])
        (when (:notes fields)
          (html/input :notes "notka"
                      {:default (:notes @state)}))
        (when (:products fields)
          [prod/products-edit (:products @state)
           :available-prods available-prods
           :fields (if (config/settings :prices) #{:amount :price} #{:amount})])]))))

(defn edit-order []
  (html/modal
   :order-edit
   [order-form @(re-frame/subscribe [::subs/editted-order])]
   ;; On success
   :on-submit (fn [form] (re-frame/dispatch [::event/save-order (format-raw-order form)]))))

(defn format-order [settings {:keys [id who day hour notes state products]}]
  [:div {:class [:order state] :key (gensym)
         :draggable true
         :on-drag-start (fn [e]
                          (-> e .-dataTransfer (.setData "order-day" day))
                          (-> e .-dataTransfer (.setData "order-id" id)))}
   [:div {:class :actions}
    (condp = state
      :waiting   [:button {:on-click #(re-frame/dispatch [::event/fulfill-order id day])} "✓"]
      :fulfilled [:button {:on-click #(re-frame/dispatch [::event/reset-order id day])} "X"]
      :pending nil
      nil nil)
    [:button {:on-click #(re-frame/dispatch [::event/edit-order day id])} "E"]
    [:button {:on-click #(re-frame/dispatch
                          [::event/confirm-action
                           "na pewno usunąć?"
                           ::event/remove-order id day])} "-"]]
   [:div {:class :who} (:name who)]
   (if (settings :show-order-time)
     [:div {:class :when} hour])
   (if (and (settings :show-order-notes) notes)
     [:div {:class :notes} notes])
   (->> products
        (map (partial prod/format-product settings))
        (into [:div {:class :products}]))])

(defn day [settings [date orders]]
  (let [orders (map calc-order-prices orders)]
    [:div {:class [:day (when (-> date time/parse-date time/today?) :today)]
           :on-drag-over #(.preventDefault %)
           :on-drop #(let [id  (-> % .-dataTransfer (.getData "order-id") prod/num-or-nil)
                           from (-> % .-dataTransfer (.getData "order-day"))]
                       (.preventDefault %)
                       (re-frame/dispatch [::event/move-order id from date]))}
     [:div {:class :day-header} (-> date time/parse-date time/format-date)]
     [:div
      [:div {:class :orders}
       (->> (if (settings :hide-fulfilled-orders)
              (remove (comp #{:fulfilled} :state) orders)
              orders)
            (remove (comp #{:canceled} :state))
            (map (partial format-order settings))
            doall)
       (when (settings :show-day-add-order)
         [:button {:type :button
                   :on-click #(re-frame/dispatch [::event/edit-order date])} "+"])
       (when (seq (map :products orders))
         [:div {:class :summary}
          [:hr {:class :day-seperator}]
          [:div {:class :header} "w sumie:"]
          (->> orders
               (map :products)
               (apply merge-with merge-product-values)
               (sort-by first)
               (map (partial prod/format-product settings))
               (into [:div {:class :products-sum}]))])]]]))

(defn calendar [days settings]
  (->> days
       (map (partial day settings))
       (into [:div {:class [:calendar :full-height]}])))
