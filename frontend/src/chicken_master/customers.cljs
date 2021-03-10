(ns chicken-master.customers
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.products :as prod]
   [chicken-master.subs :as subs]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))

(defn order-adder [order]
  (let [state (reagent/atom order)]
    (fn []
      [:details {:class (or (:class order) :customer-order) :key (gensym) :open (:open @state)}
       [:summary {:on-click #(swap! state update :open not)}
        [prod/item-adder
         :type :date
         :value (:day @state)
         :class :order-date-picker
         :callback (fn [day] (swap! state #(assoc % :day day :open true)))]]
       (if (:day @state)
         [prod/products-edit (:products @state)
          :getter-fn #(re-frame/dispatch [::event/save-order (assoc @state :products %)])])])))

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
         [order-adder {:who who}]
         (for [order (reverse (sort-by :day (client-orders id)))]
            [order-adder (assoc order :key (gensym))])
         ]))]
     ))
