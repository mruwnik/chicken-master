(ns chicken-master.views
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.subs :as subs]
   [chicken-master.stock :as stock]
   [chicken-master.customers :as cust]
   [chicken-master.calendar :as cal]
   [chicken-master.events :as event]
   [chicken-master.html :as html]
   [chicken-master.config :refer [settings-options]]))

(defn show-settings []
  (html/modal :settings (settings-options)))

(defn login-screen []
  [:div
   (html/modal
    :login
    [:div
     [:div
      [:label {:for :name} "nazwa użytkownika"]
      [:br]
      [:input {:id :name :name :name}]]
     [:div
      [:label {:for :name} "hasło"]
      [:br]
      [:input {:id :password :name :password :type :password}]]]
    :on-submit #(re-frame/dispatch [::event/set-user %])
    :show-cancel nil)])

(defn app []
  [:div {:class :full-height}
   [:div {:class [:loader-container (if-not @(re-frame/subscribe [::subs/loading?]) :hidden)]}
    [:div {:class :loader}]]
   (cond
     @(re-frame/subscribe [::subs/show-stock-modal]) (stock/show-available)
     @(re-frame/subscribe [::subs/show-settings-modal]) (show-settings)
     @(re-frame/subscribe [::subs/show-customers-modal]) (cust/show-customers)
     @(re-frame/subscribe [::subs/show-edit-modal]) (cal/edit-order)
     @(re-frame/subscribe [::subs/show-order-type-modal]) (cal/choose-order-type))

   [:button {:id :show-stock-button :class :menu-button :on-click #(re-frame/dispatch [::event/show-stock])} "Magazyn"]
   [:button {:id :show-clients-button :class :menu-button :on-click #(re-frame/dispatch [::event/show-customers])} "Klienci"]
   [:button {:id :show-clients-button :class :menu-button :on-click #(re-frame/dispatch [::event/show-settings])} "Ustawienia"]
   ;; (re-frame/dispatch [::event/show-settings])

   [:button {:id :scroll-up-button :class [:menu-button :scroll-button] :on-click #(re-frame/dispatch [::event/scroll-weeks -2])} "^"]

   [:div {:class :scroll-bar}
    [:button {:id :scroll-up :on-click #(re-frame/dispatch [::event/scroll-weeks -2])} "^"]
    [:button {:id :scroll-down :on-click #(re-frame/dispatch [::event/scroll-weeks 2])} "v"]]

   (cal/calendar @(re-frame/subscribe [::subs/current-days]) @(re-frame/subscribe [::subs/settings]))
   [:button {:id :scroll-down-button :class [:menu-button :scroll-button] :on-click #(re-frame/dispatch [::event/scroll-weeks 2])} "v"]
   ])

(defn main-panel []
  (if @(re-frame/subscribe [::subs/current-user])
    (app)
    (login-screen)))
