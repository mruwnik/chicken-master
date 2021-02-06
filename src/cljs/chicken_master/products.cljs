(ns chicken-master.products
  (:require
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.config :refer [settings]]
   [chicken-master.html :as html]
   [chicken-master.subs :as subs]
   [chicken-master.events :as event]))

(defn num-or-nil [val]
  (let [i (js/parseFloat val)]
    (when-not (js/isNaN i) i)))

(defn round [num digits]
  (let [div (js/Math.pow 10 digits)]
    (/ (js/Math.round (* num div)) div)))

(defn number-input [id label amount on-blur]
  (html/input id label
              {:type :number
               :default (round amount 3)
               :step :any
               :on-blur on-blur}))

(defn collect-products [raw-values]
  (->> raw-values
       (remove (comp str/blank? second))
       (map (fn [[k v]] [(str/split k "-") v]))
       (group-by (comp last first))
       (map #(sort-by first (second %)))
       (map (fn [[[_ amount] [_ product]]] [(keyword product) (num-or-nil amount)]))
       (remove (comp nil? first))
       (remove (comp zero? second))
       (group-by first)
       (map (fn [[product items]] [product (->> items (map last) (reduce +))]))
       (into {})))

(defn product-item [available state what]
  (let [id (gensym)]
    [:div {:class :product-item-edit :key (gensym)}
     [:div {:class :input-item}
      ;; [:label {:for :product} "co"]
      [:select {:name (str "product-" id) :id :product :defaultValue what
                :on-change #(let [prod (-> % .-target .-value keyword)]
                              (swap! state assoc prod (+ (@state prod) (@state what)))
                              (swap! state dissoc what)
                              (if (not (contains? @state nil))
                                (swap! state assoc nil 0))
                              )}
       [:option {:value ""} "-"]
       (for [[product _] available]
         [:option {:key (gensym) :value product} (name product)])]
      ]
     (number-input (str "amount-" id) nil (@state what)
                   #(do (swap! state assoc what (-> % .-target .-value num-or-nil))
                        (prn @state)))
     ]))

(defn products-edit [selected-prods & {:keys [available-prods getter-fn]
                                       :or {available-prods @(re-frame/subscribe [::subs/available-products])}}]
  (let [state (reagent/atom (assoc selected-prods nil 0))]
    (fn []
      (let [products (->> @state
                          keys
                          (map (partial product-item available-prods state))
                          (into [:div {:class :product-items-edit}]))]
        (if getter-fn
          (conj products
                [:button {:type :button
                          :on-click #(getter-fn (dissoc @state nil))} "ok"])
          products
          )))))

(defn format-product [[product amount]]
  [:div {:key (gensym) :class :product}
   [:span {:class :product-name} product]
   (if (settings :editable-number-inputs)
     (number-input (str "amount-" product) "" amount nil)
     [:span {:class :product-amount} amount])])
