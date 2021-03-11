(ns chicken-master.products
  (:require
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.html :as html]
   [chicken-master.subs :as subs]))

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
      [:select {:name (str "product-" id) :id :product :defaultValue (or (some-> what name) "-")
                :on-change #(let [prod (-> % .-target .-value keyword)]
                              (if-not (= prod :-)
                                (swap! state assoc prod (+ (@state prod) (@state what))))
                              (swap! state dissoc what))}
       (for [product (->> available (concat [what]) (remove nil?) sort vec)]
         [:option {:key (gensym) :value product} (name product)])
       [:option {:key (gensym) :value nil} "-"]]]
     (number-input (str "amount-" id) nil (@state what)
                   #(swap! state assoc what (-> % .-target .-value num-or-nil)))]))

(defn products-edit [state & {:keys [available-prods getter-fn]
                              :or {available-prods @(re-frame/subscribe [::subs/available-products])}}]
  (let [all-product-names (-> available-prods keys set)]
    (fn []
      (let [available (remove (partial get @state) all-product-names)
            product-names (if (seq available)
                            (conj (->> @state (map first) vec) nil)
                            (map first @state))
            products (->> product-names
                          (map (partial product-item available state))
                          (into [:div {:class :product-items-edit}]))]
        (if getter-fn
          (conj products
                [:button {:type :button
                          :on-click #(getter-fn (dissoc @state nil))} "ok"])
          products)))))

(defn format-product [settings [product amount]]
  [:div {:key (gensym) :class :product}
   [:span {:class :product-name} product]
   (if (settings :editable-number-inputs)
     (number-input (str "amount-" product) "" amount nil)
     [:span {:class :product-amount} amount])])

(defn item-adder [& {:keys [type value callback button class]
                     :or {type :text value "" button nil}}]
  (let [state (reagent/atom value)]
    (fn []
      [:div {:class class :on-click #(.stopPropagation %)}
       [:input {:type type :name :user-name :default-value value
                :on-change #(let [val (-> % .-target .-value)]
                              (reset! state val)
                              (if-not button (callback val)))}]
       (if button
         [:button {:class :add-product
                   :type :button
                   :disabled (= @state "")
                   :on-click (if callback #(-> state (reset-vals! value) first callback))} button])])))
