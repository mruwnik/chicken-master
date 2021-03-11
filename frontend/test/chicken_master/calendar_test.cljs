(ns chicken-master.calendar-test
  (:require
   [chicken-master.calendar :as sut]
   [cljs.test :refer-macros [deftest is testing]]))

(deftest format-raw-order-test
  (testing "no products"
    (is (= (sut/format-raw-order {}) {:who {:name nil :id nil} :day nil :notes nil :products {}}))
    (is (= (sut/format-raw-order {"who" "bla" "notes" "ble"})
           {:who {:name "bla" :id nil} :day nil :notes "ble" :products {}}))
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble" "day" "2020-10-10"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble" :products {}})))

  (testing "decent products"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble"
                                  "day" "2020-10-10"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-cows" "cows" "amount-cows" "22"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble"
            :products {:eggs 12 :cows 22 :milk 3.2}})))

  (testing "duplicate products"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-eggs1" "eggs" "amount-eggs1" "12"
                                  "product-cows1" "cows" "amount-cows1" "1"
                                  "product-cows2" "cows" "amount-cows2" "2"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who {:name "bla" :id 123} :day nil :notes "ble" :products {:eggs 24 :cows 3 :milk 3.2}})))

  (testing "unselected are ignored"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble" "day" "2020-10-10"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-bad1" "" "amount-bad1" "12"
                                  "product-bad2" "" "amount-bad2" "1"
                                  "product-milk" "milk" "amount-milk" "3.2"
                                  "product-bad3" "" "amount-bad3" "2"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble" :products {:eggs 12 :milk 3.2}})))

  (testing "items with 0 are removed"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble" "day" "2020-10-10"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-eggs1" "eggs" "amount-eggs1" "0"
                                  "product-cow" "cow" "amount-cow" "0"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble" :products {:eggs 12 :milk 3.2}}))))

(def customers
  [{:id 1 :name "mr blobby" :product-groups [{:name "group 1" :products {:eggs 1 :carrots 2}}
                                             {:name "group 2" :products {:eggs 11 :carrots 2}}
                                             {:name "group 3" :products {:milk 2 :eggs 12}}]}
   {:id 2 :name "johnny D" :product-groups [{:name "group 4" :products {:eggs 2}}
                                            {:name "group 5" :products {:milk 2}}]}
   {:id 3 :name "joe" :product-groups []}
   {:id 4 :name "mark"}])

(deftest get-group-products-test
  (testing "products get returned if the customer has them"
    (is (= (sut/get-group-products customers "mr blobby")
           {"group 1" {:eggs 1, :carrots 2}
            "group 2" {:eggs 11, :carrots 2}
            "group 3" {:milk 2, :eggs 12}})))

  (testing "no products are returned if the customer has none"
    (is (= (sut/get-group-products customers "joe") {})))

  (testing "missing products are handled"
    (is (nil? (sut/get-group-products customers "mark"))))

  (testing "missing customers are handled"
    (is (nil? (sut/get-group-products customers "bla bla bla"))))

  (testing "nil customers are handled"
    (is (nil? (sut/get-group-products customers nil)))))
