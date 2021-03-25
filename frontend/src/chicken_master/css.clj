(ns chicken-master.css
  (:require [garden.def :refer [defstyles]]
            [garden.core :refer [css]]
            [garden.stylesheet :refer [at-media at-keyframes]]
            [clojure-watch.core :refer [start-watch]]))

(defstyles screen
  (at-keyframes "spin"
                [:0% {:transform "rotate(0deg)"}]
                [:100% {:transform "rotate(360deg)"}])

  [:html {:height "100%"}
   [:body {:height "100%"}
    [:.hidden {:display :none}]
    [:.loader-container {:position :absolute
                         :width "100%"
                         :height "100%"
                         :z-index 1000
                         :background-color "rgba(0,0,0,0.4)"
                         }
     [:.loader {:margin :auto
                :position :relative
                :top "40%"
                :border "5px solid #f3f3f3"
                :border-top "5px solid #8bd81e"
                :border-radius "50%"
                :width "30px"
                :height "30px"
                :animation "spin 1s linear infinite"}]]

    [:.full-height {:height "100%"}]

    [:.scroll-bar {:position :absolute
                   :right "10px"
                   :width "50px"}
     [:#scroll-down {:position :fixed
                     :right "0"
                     :bottom "0"}]]

    [:.popup
      {:position :fixed
       :height "100%"
       :width "100%"
       :overflow :auto
       :z-index 1
       :background-color "rgba(0,0,0,0.4)"}
     [:.popup-content {
             :background-color "#fefefe"
             :margin "15% auto"
             :padding "20px"
             :border "1px solid #888"
             :width "15%"
             }
      [:.input-item
       [:label {:min-width "60px"
                :display :inline-block}]]
      [:..popup-form-buttons {:margin "10px"}
       [:* {:margin "20px"}]]]]
    [:.wide-popup [:.popup-content {:width "45%"}]]

    [:.scroll-button {:display :none}]
    (at-media
     {:max-width "800px"}
     [:.scroll-bar {:display :none}]
     [:.menu-button {:width "100%"
                     :font-size "1.5em"
                     :font-variant "small-caps"
                     :display :inherit}]
     [:.popup
      [:.popup-content {
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
      [:.popup-content {
              :background-color "#fefefe"
              :margin "15% auto"
              :padding "20px"
              :border "1px solid #888"
              :width "15%"
              }]]
     [:.wide-popup [:.popup-content {:width "45%"}]]
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
                    :font-size "1.5em"
                    :font-variant "small-caps"}
      [:.day-name {:color :red}]
      [:.date {:color :purple}]]
     [:.day.today {:border "0.2em solid #6cb802"}]
     [:.day {:border "2px solid black"
             :overflow :auto}

      ; If each day has a header, then hide the rest of the border
      [:.day-header {:border "none"
                     :border-bottom "2px solid black"}]

      [:.orders {:padding-left "25px"}

       [:.actions {:display :none
                   :float :right}]
       [:.order:hover [:.actions {:display :inline}]]
       [:.order.pending {:color "rgb(247, 176, 176)"}]
       [:.order.fulfilled {:color "rgb(114, 114, 114)"}]

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

    [:.customers-modal
     [:details {:padding "0.5em"}]
     [:.customer-block {:margin-left "1em" :padding "0.5em"}
      [:.order-date-picker {:display :inline-block :width "75%" :cursor :pointer}]
      [:.product-item-edit {:margin-left "1em"}]]]
    [:.customer-product-group-edit {:margin-left "1.2em" :padding "0.5em"}]
    ]]

  ; Chrome, Safari, Edge, Opera
  ["input::-webkit-outer-spin-button" {:-webkit-appearance :none :margin 0}]
  ["input::-webkit-inner-spin-button"{:-webkit-appearance :none :margin 0}]

  ; Firefox
  ["input[type=number]" {:-moz-appearance :textfield}]
)




(defn -main
  ([command] (-main command "resources/public/css/screen.css"))
  ([command output]
   (condp = command
     "compile" (css {:output-to output} screen)
     "watch" (start-watch [{:path "src/chicken_master"
                            :event-types [:modify]
                            :bootstrap (fn [path] (println "Starting to watch " path))
                            :callback (fn [_ filename]
                                        (when (#{"src/chicken_master/css.clj"} filename)
                                          (css {:output-to output} screen)))
                            :options {:recursive true}}])
     (println "Unsuported command - use either compile or watch"))))
