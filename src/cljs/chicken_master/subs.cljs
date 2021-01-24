(ns chicken-master.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub ::name (fn [db] (:name db)))
(re-frame/reg-sub ::available-products (fn [db] (:products db)))

(re-frame/reg-sub ::show-edit-modal (fn [db] (-> db :order-edit :show)))
(re-frame/reg-sub ::show-stock-modal (fn [db] (-> db :stock :show)))

(re-frame/reg-sub ::order-edit-who (fn [db] (println (:order-edit db)) (-> db :order-edit :who)))
(re-frame/reg-sub ::order-edit-notes (fn [db] (-> db :order-edit :notes)))
(re-frame/reg-sub ::order-edit-products (fn [db] (-> db :order-edit :products)))

(re-frame/reg-sub ::current-days (fn [db] (:current-days db)))
