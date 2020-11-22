(ns chicken-master.products
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.config :refer [settings]]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))

(defn product-item [what amount available product-no]
  [:div {:key (gensym)}
   [:div {:class :input-item}
    [:label {:for :product} "co"]
    [:select {:name :product :id :product :defaultValue what
              :on-change #(re-frame/dispatch [::event/selected-product (-> % .-target .-value) product-no])}
     [:option {:value nil} "-"]
     (for [product available]
       [:option {:key (gensym) :value product} (name product)])]]
   (html/input :amount "ile"
               {:type :number :default amount :min 0
                ;; :on-blur #(re-frame/dispatch [::event/changed-amount (-> % .-target .-value) product-no])
                :on-input #(re-frame/dispatch [::event/changed-amount (-> % .-target .-value) product-no])
                })])

(defn format-product [[product amount]]
  [:li {:key (gensym) :class :product}
   (if (settings :editable-number-inputs)
      [:input {:class :product-amount :type :number :min 0 :defaultValue amount}]
      [:span {:class :product-amount} amount])
   [:span {:class :product-name} product]])
