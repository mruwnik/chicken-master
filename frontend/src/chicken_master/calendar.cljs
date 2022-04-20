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

(defn int-or-nil [val]
  (let [i (js/parseInt val)]
    (when-not (js/isNaN i) i)))

(defn parse-recurrence [{:strs [recurrence-till recurrence-times recurrence-unit recurrence-every]}]
  (when (or (int-or-nil recurrence-times) (seq recurrence-till))
    {:times (int-or-nil recurrence-times)
     :until (when (seq recurrence-till) recurrence-till)
     :unit recurrence-unit
     :every (or (int-or-nil recurrence-every) 1)}))

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
   :recurrence (parse-recurrence raw-values)
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
  ([order] (order-form order #{:who :day :notes :products :group-products :recurrence}))
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
        (when (:recurrence fields)
          [:details {:class :recurrence-details}
           [:summary "powtarzanie"]
           [:div {:class :recurrence}
            (html/input :recurrence-times "ile razy" {:type :number :default (-> order :recurrence :times)})
            (html/input :recurrence-till "do kiedy" {:type :date :default (-> order :recurrence :until)})
            [:div {:class :recurrence-freq}
             (html/input :recurrence-every "co" {:type :number :default (-> order :recurrence :every)})
             [:select {:name :recurrence-unit :id :recurrence-unit :defaultValue (-> order :recurrence :unit)}
              [:option {:key :day :value "day"} "dni"]
              [:option {:key :week :value "week"} "tygodni"]
              [:option {:key :month :value "month"} "miesięcy"]]]]])
        (when (:products fields)
          [prod/products-edit (:products @state)
           :available-prods available-prods
           :fields (if (config/settings :prices) #{:amount :price} #{:amount})])]))))

(defn edit-order []
  (html/modal
   :order-edit
   [order-form @(re-frame/subscribe [::subs/editted-order])]
   ;; On success
   :on-submit (fn [form]
                (let [order @(re-frame/subscribe [::subs/editted-order])
                      event [::event/save-order (format-raw-order form)]]
                  (re-frame/dispatch [::event/change-order-type (:id order) event])
                  :close-modal))))

(defn choose-order-type []
  (let [{:keys [event]} @(re-frame/subscribe [::subs/order-type-edit])]
    (html/modal
     :order-type-edit
     [:div
      (html/input :single "tylko to" {:type :radio :name :type-choose :defaultChecked true})
      ;; (html/input :from-here "od tego" {:type :radio :name :type-choose})
      (html/input :all "wszystkie" {:type :radio :name :type-choose})]
     ;; On success
     :on-submit (fn [form] (re-frame/dispatch (conj event (form "type-choose" "single"))) :close-modal))))

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
                           ::event/change-order-type id [::event/remove-order id day]])} "-"]]
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
