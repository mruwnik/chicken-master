(ns chicken-master.db)

(def default-db
  {:name "re-frame"
   :order-edit {:show nil
                :who "mr. blobby"
                :when "12:32"
                :products [{:prod :eggs :amount 2}
                           {:prod :milk :amount 5}
                           {}]}
   :customers {1 {:who "mr.blobby (649 234 234)" :day "2020-10-10" :hour "02:12" :products {:eggs 2 :milk 3}}
               2 {:who "da police (0118 999 881 999 119 725 123123 12 3123 123 )" :day "2020-10-10" :hour "02:12" :products {:eggs 12}}
               3 {:who "johnny" :day "2020-10-10" :hour "02:12" :products {:eggs 5}}}
   :days {"2020-09-05" [1 2 3]
          "2020-09-06" [1 2 3]
          "2020-09-07" [1 2 3]
          "2020-09-08" [1 2 3]
          "2020-09-09" [1 2 3]
          "2020-09-10" [1 2 3]
          "2020-09-11" [1 2 3]
          "2020-09-12" [1 2 3]
          "2020-09-13" [1 2 3]
          "2020-09-14" [1 2 3]
          "2020-09-15" [1 2 3]
          "2020-09-16" [1 2 3]
          "2020-09-17" [1 2 3]
          "2020-09-18" [1 2 3]}
   :products {:eggs {}
              :milk {}
              :cabbage {}
              :carrots {}}})
