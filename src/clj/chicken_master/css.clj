(ns chicken-master.css
  (:require [garden.def :refer [defstyles]]
            [garden.stylesheet :refer [at-media]]))

(defstyles screen
  [:html {:height "100%"}
   [:body {:height "100%"}
    [:.full-height {:height "100%"}]

    [:.scroll-bar {:position :absolute
                   :right "10px"
                   :width "50px"}
     [:#scroll-down {:position :fixed
                     :right "0"
                     :bottom "0"}]]

    [:.popup {:position :fixed
              :height "100%"
              :width "100%"
              :overflow :auto
              :z-index 1
              :background-color "rgba(0,0,0,0.4)"}
     [:form {
             :background-color "#fefefe"
             :margin "15% auto"
             :padding "20px"
             :border "1px solid #888"
             :width "15%"
             }
      [:.input-item
       [:label {:min-width "60px"
                :display :inline-block}]]
      [:.form-buttons {:margin "10px"}
       [:* {:margin "20px"}]]]]

    [:.scroll-button {:display :none}]
    (at-media
     {:max-width "800px"}
     [:.scroll-bar {:display :none}]
     [:.menu-button {:width "100%"
                     :font-size "3em"
                     :display :inherit}]
     [:.popup
      [:form {
              :background-color "#fefefe"
              :margin "3% auto"
              :padding "20px"
              :border "1px solid #888"
              :width "60%"}
       [:.product-items-edit {:margin-top "1.5em"}
        [:.product-item-edit
         [:label {:display :none}]]]]]
     [:.calendar
      [:.day {:min-height "12em"}]
      ])
    (at-media
     {:min-width "800px"}
     [:.popup
      [:form {
              :background-color "#fefefe"
              :margin "15% auto"
              :padding "20px"
              :border "1px solid #888"
              :width "15%"
              }]]
     [:.calendar {:display :grid
                  :grid-template-columns "25% 25% 25% 25%"
                  :grid-template-rows "50% 50%"}])
    (at-media {:min-width "1200px"}
              [:.calendar
               {:display :grid
                :grid-template-columns "14% 14% 14% 14% 14% 14% 14%"
                :grid-template-rows "50% 50%"}])
    [:.calendar
     [:.day-header {:border "2px solid black"
                    :text-align :center
                    :font-size "2em"}]
     [:.day.today {:border "0.4em solid red"}]
     [:.day {:border "2px solid black"
             :overflow :auto}

      ; If each day has a header, then hide the rest of the border
      [:.day-header {:border "none"
                     :border-bottom "2px solid black"}]

      [:.orders {:padding-left "25px"}

       [:.actions {:display :none
                   :float :right}]
       [:.order:hover [:.actions {:display :inline}]]
       [:.order.pending {:color :grey}]
       [:.order.fulfilled {:color :red}]

       [:.who {:font-size "18px"
               :font-weight :bold
               :white-space :nowrap
               :overflow :hidden
               :text-overflow :ellipsis}]]
      [[:.products :.products-sum] {:padding-left "25px"}
       [:.product {:margin-bottom "5px"}
        [:.product-name {:width "5em"
                         :display :inline-block
                         :text-overflow :ellipsis
                         :white-space :nowrap
                         :overflow :hidden
                         :margin-right "10px"}]
        [:.product-amount {:width "40px"
                           :max-height "5px"}]

        ]]
      [:.summary {:margin-top "10px"}]]]


    [:.stock-modal
     [:.add-product {:float :right}]
     [:.stock-product {:margin "1em 0"}
      [:.product-name {:display :inline-block
                       :width "6em"}]
      [:.stock-product-amount {:display :inline-block}
       [:.input-item {:display :inline}
        [:input {:width "40px"}]
        [:label {:display :none}]]
       ]]
     ]
    ]]

  ; Chrome, Safari, Edge, Opera
  ["input::-webkit-outer-spin-button" {:-webkit-appearance :none :margin 0}]
  ["input::-webkit-inner-spin-button"{:-webkit-appearance :none :margin 0}]

  ; Firefox
  ["input[type=number]" {:-moz-appearance :textfield}]
)
