(ns chicken-master.calendar
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
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
  (some->> customers
       (filter (comp #{who} :name))
       first
       :product-groups
       (reduce #(assoc %1 (:name %2) (:products %2)) {})))

(defn group-products [state]
  [:div {:class :input-item}
   [:label {:for :order-group-products} "stałe"]
   [:select {:class :order-group-products :id :order-group-products
             :value "-" :on-change #(some->> % .-target .-value
                                             (get (:group-products @state))
                                             (reset! (:products @state)))}
    [:option "-"]
    (for [[group _] (:group-products @state)]
      [:option {:key (gensym)} group])]])

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
                                     :on-blur #(->> % .-target .-value
                                                    (get-group-products customers)
                                                    (swap! state assoc :group-products))})
             (into [:datalist {:id :customers}]
                   (for [cust customers] [:option {:value (:name cust) :id (:id cust)}]))
             [:input {:id :who-id :name :who-id :type :hidden :value (or (:id who) "")}]]))

        (when (:day fields)
          (html/input :day "dzień" {:type :date :required true :default (:day order)}))
        (when (and (:group-products fields) (-> @state :group-products seq))
          [group-products state])
        (when (:notes fields)
          (html/input :notes "notka"
                      {:default (:notes @state)}))
        (when (:products fields)
          [prod/products-edit (:products @state) :available-prods available-prods])]))))

(defn edit-order []
  (html/modal
   :order-edit
   [order-form @(re-frame/subscribe [::subs/editted-order])]
   ;; On success
   :on-submit (fn [form] (re-frame/dispatch [::event/save-order (format-raw-order form)]))))

(defn format-order [settings {:keys [id who day hour notes products state]}]
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
        (map (partial prod/format-product settings))
        (into [:div {:class :products}]))])

(defn day [settings [date orders]]
  [:div {:class [:day (when (-> date time/parse-date time/today?) :today)]
         :on-drag-over #(.preventDefault %)
         :on-drop #(let [id (-> % .-dataTransfer (.getData "text") prod/num-or-nil)]
                     (.preventDefault %)
                     (re-frame/dispatch [::event/move-order id date]))}
   [:div {:class :day-header} (-> date time/parse-date time/format-date)]
   [:div
    [:div {:class :orders}
     (if (settings :hide-fulfilled-orders)
       (->> orders (remove (comp #{:fulfilled} :state)) (map (partial format-order settings)))
       (map (partial format-order settings) orders))
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
             (map (partial prod/format-product settings))
             (into [:div {:class :products-sum}]))])]]])

(defn calendar [days settings]
  (->> days
       (map (partial day settings))
       (into [:div {:class [:calendar :full-height]}])))
