(ns chicken-master.stock-test
  (:require
   [chicken-master.stock :as sut]
   [cljs.test :refer-macros [deftest is testing]]))


(deftest process-form-test
  (testing "no values"
    (is (= (sut/process-form {}) {})))

  (testing "non numeric values are removed"
    (is (= (sut/process-form {"bla" "dew"}) {})))

  (testing "price and amount are extracted"
    (is (= (sut/process-form {"bla" "dew" "ble-amount" "123" "ble-price" "4.32"})
           {:ble {:amount 123 :price 432}})))

  (testing "multiple values are handled"
    (is (= (sut/process-form {"cheese-price" "0.12" "user-name" "" "carrots-amount" "-1"
                              "eggs-amount" "8" "cows-amount" "15" "carrots-price" "31.3"
                              "eggs-price" "0" "cows-price" "0" "cheese-amount" "4"})
           {:cheese {:price 12, :amount 4}
            :carrots {:amount -1, :price 3130}
            :eggs {:amount 8, :price 0}
            :cows {:amount 15, :price 0}}))))
