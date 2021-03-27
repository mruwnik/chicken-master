(ns chicken-master.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub ::name (fn [db] (:name db)))
(re-frame/reg-sub ::current-user (fn [db] (:current-user db)))
(re-frame/reg-sub ::settings (fn [db] (:settings db)))
(re-frame/reg-sub ::loading? (fn [db] (-> db :loading? pos?)))

(re-frame/reg-sub ::available-products (fn [db] (:products db)))
(re-frame/reg-sub ::available-customers (fn [db] (:customers db)))
(re-frame/reg-sub
 ::customer-prices
 (fn [db]
   (->> db :customers
        (reduce (fn [col {:keys [id prices]}]
                  (assoc col id (reduce-kv #(assoc %1 %2 (:price %3)) {} prices))) {}))))
(re-frame/reg-sub ::orders (fn [db] (:orders db)))

(defn- show-modal? [modal db] (and (-> modal db :show) (-> db :loading? zero?)))
(re-frame/reg-sub ::show-edit-modal (partial show-modal? :order-edit))
(re-frame/reg-sub ::show-stock-modal (partial show-modal? :stock))
(re-frame/reg-sub ::show-settings-modal (partial show-modal? :settings))
(re-frame/reg-sub ::show-customers-modal (fn [db] (-> db :clients :show)))

(re-frame/reg-sub ::editted-order (fn [db] (:order-edit db)))

(re-frame/reg-sub ::current-days (fn [db] (:current-days db)))
