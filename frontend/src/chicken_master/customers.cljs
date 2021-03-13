(ns chicken-master.customers
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.products :as prod]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))

(defn order-adder [order who]
  (let [state (-> order
                  (update :products reagent/atom)
                  (assoc :group-products (:group-products who))
                  reagent/atom)]
    (fn []
      [:details {:class (or (:class order) :customer-block) :key (gensym) :open (:open @state)}
       [:summary {:on-click #(swap! state update :open not)}
        [prod/item-adder
         :type :date
         :value (:day @state)
         :class :order-date-picker
         :callback (fn [day] (swap! state #(assoc % :day day :open true)))]]
       (if (:day @state)
         [:div
          (when (:product-groups who)
            [prod/group-products state])
          [prod/products-edit (:products @state)
           :getter-fn #(re-frame/dispatch [::event/save-order (assoc @state :products %)])]])])))

(defn product-group-adder [who [name {:keys [id products]}]]
  (let [state (reagent/atom {:name name :id id :products (or products {})})]
    (fn []
      [:div {:class :customer-block}
       (if-not (:edit @state)
         [:div
          [:span {:class :customer-product-group-name} (:name @state)]
          [:button {:type :button :on-click #(swap! state assoc :edit true)}
           (if (:name @state) "e" "+")]]

         [:div {:class :customer-product-group-edit}
          (html/input :customer-product-group-name "nazwa"
                      {:default (:name @state)
                       :on-blur #(swap! state assoc :name (-> % .-target .-value))})
          [prod/products-edit (reagent/atom (or (:products @state) {}))
           :getter-fn #(do
                         (swap! state dissoc :edit)
                         (when (and (:name @state) (:products @state))
                           (re-frame/dispatch [::event/save-product-group (:id who) (assoc @state :products %)])))]])])))


(defn show-customers []
  (html/modal
   :clients
   [:div {:class :customers-modal}
    [:h2 "Clienci"]
    [prod/item-adder :callback #(re-frame/dispatch [::event/add-customer %]) :button "+"]
    (let [client-orders (->> @(re-frame/subscribe [::subs/orders])
                             vals
                             (group-by #(get-in % [:who :id])))]
      (for [{:keys [name id] :as who} @(re-frame/subscribe [::subs/available-customers])]
        [:details {:class "client" :key (gensym)}
         [:summary [:span name [:button {:on-click #(re-frame/dispatch
                                                     [::event/confirm-action
                                                      "na pewno usunąć?"
                                                      ::event/remove-customer id])} "-"]]]
         [:details {:class :customer}
          [:summary "Stałe zamówienia"]
          (for [group (:product-groups who)]
            [:div {:key (gensym)}
             [product-group-adder who group]])
          [product-group-adder who []]]

         [:details {:class :client-orders}
          [:summary "Zamówienia"]
          [order-adder who]
          (for [order (reverse (sort-by :day (client-orders id)))]
            [order-adder (assoc order :key (gensym)) who])]]))]
   :class :wide-popup))
