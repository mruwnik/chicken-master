(ns chicken-master.customers
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [clojure.string :as str]
   [chicken-master.products :as prod]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.config :as config]
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
       (when (:day @state)
         [:div
          (when (:product-groups who)
            [prod/group-products state])
          [prod/products-edit (:products @state)
           :fields (if (config/settings :prices) #{:amount :price} #{:amount})
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
          (html/input :customer-product-group-name nil
                      {:default (:name @state)
                       :on-blur #(swap! state assoc :name (-> % .-target .-value))})
          [prod/products-edit (reagent/atom (or (:products @state) {}))
           :fields (if (config/settings :prices) #{:amount :price} #{:amount})
           :getter-fn #(do
                         (swap! state dissoc :edit)
                         (when (and (:name @state) (:products @state))
                           (re-frame/dispatch [::event/save-product-group (:id who) (assoc @state :products %)])))]])])))

(defn price-adder [who]
  (let [state (reagent/atom (:prices who))]
    (fn []
      [:details {:class :customer-prices}
       [:summary "Ceny"]
       [prod/products-edit state
        :fields #{:price}
        :getter-fn #(when (seq @state)
                      (re-frame/dispatch [::event/save-customer-prices (:id who) @state]))]])))

(defn show-customers []
  (html/modal
   :clients
   [:div {:class :customers-modal}
    [:h2 "Klienci"]
    [(fn []
       (let [name-state (reagent/atom "")]
         (fn []
           (let [add! (fn [pickup]
                        (when-let [n (not-empty @name-state)]
                          (re-frame/dispatch [::event/add-customer n pickup])
                          (reset! name-state "")))]
             [:div {:class :customer-add
                    :on-click #(.stopPropagation %)}
              [:input {:type :text :name :customer-name :value @name-state
                       :on-change #(reset! name-state (-> % .-target .-value))}]
              [:button {:type :button :class :add-product :disabled (= "" @name-state)
                        :on-click #(add! :morning)} "+ rano"]
              [:button {:type :button :class :add-product :disabled (= "" @name-state)
                        :on-click #(add! :evening)} "+ wieczorem"]]))))]
    (let [client-orders (->> @(re-frame/subscribe [::subs/orders])
                             vals
                             (group-by #(get-in % [:who :id])))]
      (doall
       (for [{:keys [name id notes pickup-time] :as who} (sort-by #(some-> % :name str/lower-case) @(re-frame/subscribe [::subs/available-customers]))]
         [:details {:class :client :key (gensym)}
          [:summary [:span name [:button {:on-click #(re-frame/dispatch
                                                      [::event/confirm-action
                                                       "na pewno usunąć?"
                                                       ::event/remove-customer id])} "-"]]]
          [:div {:class :customer-pickup-time}
           [:label {:for (str "pickup-time-" id)} "odbiór: "]
           [:select {:id (str "pickup-time-" id)
                     :value (or (some-> pickup-time clojure.core/name) "morning")
                     :on-change #(re-frame/dispatch [::event/save-customer-pickup-time id (-> % .-target .-value)])}
            [:option {:value "morning"} "rano"]
            [:option {:value "evening"} "wieczorem"]]]
          [:div {:class :customer-notes}
           (html/input (str "notes-" id) "notatka"
                       {:default (or notes "")
                        :placeholder "telefon, preferencje, godziny odbioru..."
                        :on-blur #(let [v (-> % .-target .-value)]
                                    (when (not= v (or notes ""))
                                      (re-frame/dispatch [::event/save-customer-notes id v])))})]
          (when (config/settings :prices)
            [price-adder who])

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
             [order-adder (assoc order :key (gensym)) who])]])))]
   :class :wide-popup))
