(ns chicken-master.calendar-test
  (:require
   [chicken-master.calendar :as cal]
   [cljs.test :refer-macros [deftest is testing]]))

(deftest format-raw-order-test
  (testing "no products"
    (is (= (cal/format-raw-order {}) {:who nil :notes nil :products {}}))
    (is (= (cal/format-raw-order {"who" "bla" "notes" "ble"})
           {:who "bla" :notes "ble" :products {}})))

  (testing "decent products"
    (is (= (cal/format-raw-order {"who" "bla" "notes" "ble"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-cows" "cows" "amount-cows" "22"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who "bla" :notes "ble" :products {:eggs 12 :cows 22 :milk 3.2}})))

  (testing "duplicate products"
    (is (= (cal/format-raw-order {"who" "bla" "notes" "ble"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-eggs1" "eggs" "amount-eggs1" "12"
                                  "product-cows1" "cows" "amount-cows1" "1"
                                  "product-cows2" "cows" "amount-cows2" "2"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who "bla" :notes "ble" :products {:eggs 24 :cows 3 :milk 3.2}})))

  (testing "unselected are ignored"
    (is (= (cal/format-raw-order {"who" "bla" "notes" "ble"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-bad1" "" "amount-bad1" "12"
                                  "product-bad2" "" "amount-bad2" "1"
                                  "product-milk" "milk" "amount-milk" "3.2"
                                  "product-bad3" "" "amount-bad3" "2"})
           {:who "bla" :notes "ble" :products {:eggs 12 :milk 3.2}})))

  (testing "items with 0 are removed"
    (is (= (cal/format-raw-order {"who" "bla" "notes" "ble"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-eggs1" "eggs" "amount-eggs1" "0"
                                  "product-cow" "cow" "amount-cow" "0"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who "bla" :notes "ble" :products {:eggs 12 :milk 3.2}}))))
