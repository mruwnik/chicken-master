(ns chicken-master.calendar
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.subs :as subs]
   [chicken-master.time :as time]
   ))

(defn get-day [date]
  {:date date
   :customers [{:who "mr.blobby (649 234 234)" :when date :products {:eggs 2 :milk 3}}
               {:who "da police (0118 999 881 999 119 725 123123 12 3123 123 )" :when date :products {:eggs 12}}
               {:who "johnny" :when date :products {:eggs 5}}]})


(defn modal [content on-submit]
  [:div {:class :popup}
   [:form {:action "#"}
    content
    [:div {:class :form-buttons}
     [:input {:type :submit :value "add"}]
     [:button {:type :button} "cancel"]]]])

(defn add-product [date order-id]
  (modal
   [:div
    [:div {:class :input-item}
     [:label {:for :product} "co"]
     [:input {:type :text :name :product :id :product}]]
    [:div {:class :input-item}
     [:label {:for :amount} "ile"]
     [:input {:type :number :name :amount :id :amount}]]]
   js/console.log))

(defn add-order [date]
  (modal
   [:div
    [:div {:class :input-item}
     [:label {:for :who} "kto"]
     [:input {:type :text :name :who :id :who :required true :step "60"}]]
    [:div {:class :input-item}
     [:label {:for :when} "kiedy"]
     [:input {:type :time :name :when :id :when}]]
    [:div {}
     [:label "co"]
    ]]
   js/console.log))

(defn format-product [[product amount]]
  [:li {:class :product}
   [:input {:class :product-amount :type :number :min 0 :defaultValue amount}]
   [:span {:class :product-name} product]])

(defn format-order [{:keys [who when products]}]
  [:li {:class :order}
   [:div {:class :actions}
    [:button "+"] [:button "O"] [:button "-"]]
   [:div {:class :who} who]
   (if (subs/settings :show-order-time)
     [:div {:class :when} (str (.getHours when) ":" (.getMinutes when))])
   (->> products
        (map format-product)
        (into [:ul {:class :products}]))])

(defn day [{:keys [date customers]}]
  [:div {:class [:day (when (time/today? date) :today)]}
   [:div {:class :day-header} (time/format-date date)]
   [:div
    [:ul {:class :orders}
     (map format-order customers)
     [:button {:type :button} "+"]]]])

(defn calendar-header []
  (->> (subs/settings :day-names)
       (map (fn [day] [:div {:class :day-header} day]))
       (into [])))

(defn calendar [days]
   (->> days
        (map (comp day get-day))
        (concat (when-not (subs/settings :always-day-names) (calendar-header)))
        (into [:div {:class [:calendar :full-height]}])))
