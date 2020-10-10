(ns chicken-master.events
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.db :as db]
   [chicken-master.time :as time]))

(re-frame/reg-event-db ::initialize-db (fn [_ _] db/default-db))

(re-frame/reg-event-db ::hide-modal (fn [db _] (assoc db :order-edit {})))
(re-frame/reg-event-db
 ::edit-order
 (fn [{customers :customers :as db} [_ date id]]
   (assoc db :order-edit (merge (get customers id)
                                {:show true :day date}))))

(re-frame/reg-event-db ::add-product (fn [db _] (update-in db [:order-edit :products] conj {})))


(defn get-day [{:keys [days customers]} date]
  {:date date
   :customers (->> (.toIsoString ^js/goog.date.Date date true)
                   (get days)
                   (map customers))})


(re-frame/reg-event-db
 ::show-from-date
 (fn [db [_ date]]
   (->> date
        time/start-of-week
        (time/days-range 14)
        (map (partial get-day db))
        (assoc db :current-days))))
