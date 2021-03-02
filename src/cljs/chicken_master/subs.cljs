(ns chicken-master.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub ::name (fn [db] (:name db)))
(re-frame/reg-sub ::current-user (fn [db] (:current-user db)))
(re-frame/reg-sub ::settings (fn [db] (:settings db)))
(re-frame/reg-sub ::loading? (fn [db] (:loading? db)))

(re-frame/reg-sub ::available-products (fn [db] (:products db)))
(re-frame/reg-sub ::available-customers (fn [db] (:customers db)))
(re-frame/reg-sub ::orders (fn [db] (:orders db)))

(re-frame/reg-sub ::show-edit-modal (fn [db] (-> db :order-edit :show)))
(re-frame/reg-sub ::show-stock-modal (fn [db] (-> db :stock :show)))
(re-frame/reg-sub ::show-customers-modal (fn [db] (-> db :clients :show)))
(re-frame/reg-sub ::show-settings-modal (fn [db] (-> db :settings :show)))

(re-frame/reg-sub ::editted-order (fn [db] (:order-edit db)))

(re-frame/reg-sub ::current-days (fn [db] (:current-days db)))
