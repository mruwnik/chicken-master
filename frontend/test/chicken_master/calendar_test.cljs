(ns chicken-master.calendar-test
  (:require
   [chicken-master.calendar :as sut]
   [day8.re-frame.test :as rf-test]
   [re-frame.core :as rf]
   [cljs.test :refer-macros [deftest is testing]]))

(defn set-db [updates]
  (rf/reg-event-db
   ::merge-db
   (fn [db [_ incoming]] (merge db incoming)))
  (rf/dispatch [::merge-db updates]))

(deftest calc-order-prices-test
  (let [order {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble"}]
    (testing "no products"
      (is (= (sut/calc-order-prices order) (assoc order :products nil))))

    (testing "prices set in order"
      (is (= (sut/calc-order-prices (assoc order :products {:eggs {:amount 12 :price 2}}))
             (assoc order :products {:eggs {:amount 12 :price 2 :final-price 24}}))))

  (testing "prices set per customer"
    (rf-test/run-test-sync
     (set-db {:customers [{:id 123 :prices {:eggs {:price 3}}}]})
     (is (= (sut/calc-order-prices (assoc order :products {:eggs {:amount 12}}))
            (assoc order :products {:eggs {:amount 12 :final-price 36}})))))

  (testing "prices set globally"
    (rf-test/run-test-sync
     (set-db {:products {:eggs {:price 4}}})
     (is (= (sut/calc-order-prices (assoc order :products {:eggs {:amount 12}}))
            (assoc order :products {:eggs {:amount 12 :final-price 48}})))))

  (testing "no price set"
    (rf-test/run-test-sync
     (set-db {:products {}})
     (is (= (sut/calc-order-prices (assoc order :products {:eggs {:amount 12}}))
            (assoc order :products {:eggs {:amount 12 :final-price nil}})))))

  (testing "all together"
    (rf-test/run-test-sync
     (set-db {:products {:eggs {:price 4}}
              :customers [{:id 123 :prices {:cows {:price 3}}}]})
     (is (= (sut/calc-order-prices
             (assoc order :products {:eggs {:amount 12}
                                     :cows {:amount 2}
                                     :milk {:amount 3 :price 5}
                                     :carrots {:amount 6}}))
            (assoc order :products {:eggs {:amount 12 :final-price 48}
                                    :cows {:amount 2 :final-price 6}
                                    :milk {:amount 3 :price 5 :final-price 15}
                                    :carrots {:amount 6 :final-price nil}})))))))

(deftest merge-product-values-test
  (testing "single item"
    (is (= (sut/merge-product-values {:amount 23, :price 32, :final-price 1})
           {:amount 23, :price 32, :final-price 1})))

  (testing "items with price"
    (is (= (sut/merge-product-values {:amount 23, :price 2, :final-price 3}
                                     {:amount 45, :price 1, :final-price 4})
           {:amount 68, :price 3, :final-price 7})))

  (testing "items without prices"
    (is (= (sut/merge-product-values {:amount 23, :price nil, :final-price nil}
                                     {:amount 45, :price nil, :final-price nil})
           {:amount 68, :price nil, :final-price nil})))

  (testing "items with mixed prices"
    (is (= (sut/merge-product-values {:amount 23, :price 2, :final-price 3}
                                     {:amount 45, :price nil, :final-price nil})
           {:amount 68, :price 2, :final-price 3})))

  (testing "multiple items"
    (is (= (sut/merge-product-values {:amount 6, :price 7, :final-price 3}
                                     {:amount 5, :price 2, :final-price 4}
                                     {:amount 4, :price nil, :final-price 5}
                                     {:amount 3, :price 4, :final-price nil}
                                     {:amount 2, :price 5, :final-price 7}
                                     {:amount 1})
           {:amount 21, :price 18, :final-price 19}))))

(deftest format-raw-order-test
  (testing "no products"
    (is (= (sut/format-raw-order {}) {:who {:name nil :id nil} :day nil :notes nil :products {} :recurrence nil}))
    (is (= (sut/format-raw-order {"who" "bla" "notes" "ble"})
           {:who {:name "bla" :id nil} :day nil :notes "ble" :products {} :recurrence nil}))
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble" "day" "2020-10-10"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble" :products {} :recurrence nil})))

  (testing "decent products"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble"
                                  "day" "2020-10-10"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-cows" "cows" "amount-cows" "22" "price-cows" "2.32"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble" :recurrence nil
            :products {:eggs {:amount 12} :cows {:amount 22 :price 232} :milk {:amount 3.2}}})))

  (testing "duplicate products"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-eggs1" "eggs" "amount-eggs1" "12"
                                  "product-cows1" "cows" "amount-cows1" "1"
                                  "product-cows2" "cows" "amount-cows2" "2"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who {:name "bla" :id 123} :day nil :notes "ble" :recurrence nil
            :products {:eggs {:amount 24} :cows {:amount 3} :milk {:amount 3.2}}})))

  (testing "unselected are ignored"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble" "day" "2020-10-10"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-bad1" "" "amount-bad1" "12"
                                  "product-bad2" "" "amount-bad2" "1"
                                  "product-milk" "milk" "amount-milk" "3.2"
                                  "product-bad3" "" "amount-bad3" "2"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble" :recurrence nil
            :products {:eggs {:amount 12} :milk {:amount 3.2}}})))

  (testing "prices are handled"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble" "day" "2020-10-10"
                                  "product-eggs" "eggs" "amount-eggs" "12" "price-eggs" "4.31"
                                  "product-eggs1" "eggs" "amount-eggs1" "0" "price-eggs1" "1.0"
                                  "product-cow" "cow" "amount-cow" "0"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble" :recurrence nil
            :products {:eggs {:amount 12 :price 431} :milk {:amount 3.2}}})))

  (testing "items with 0 are removed"
    (is (= (sut/format-raw-order {"who" "bla" "who-id" "123" "notes" "ble" "day" "2020-10-10"
                                  "product-eggs" "eggs" "amount-eggs" "12"
                                  "product-eggs1" "eggs" "amount-eggs1" "0"
                                  "product-cow" "cow" "amount-cow" "0"
                                  "product-milk" "milk" "amount-milk" "3.2"})
           {:who {:name "bla" :id 123} :day "2020-10-10" :notes "ble" :recurrence nil
            :products {:eggs {:amount 12} :milk {:amount 3.2}}})))

  (testing "recurrence object is not created when empty vals provided"
    (is (= (sut/format-raw-order {"recurrence-till" ""
                                  "recurrence-times" ""
                                  "recurrence-unit" ""
                                  "recurrence-every" ""})
           {:who {:name nil :id nil} :day nil :notes nil :products {} :recurrence nil})))

  (testing "recurrence object is created when times provided"
    (is (= (sut/format-raw-order {"recurrence-till" ""
                                  "recurrence-times" "6"
                                  "recurrence-unit" "week"
                                  "recurrence-every" "3"})
           {:who {:name nil :id nil} :day nil :notes nil :products {} :recurrence {:times 6, :until nil, :unit "week", :every 3}})))

  (testing "recurrence object is created when till provided"
    (is (= (sut/format-raw-order {"recurrence-till" "2020-01-01"
                                  "recurrence-times" ""
                                  "recurrence-unit" "week"
                                  "recurrence-every" "3"})
           {:who {:name nil :id nil} :day nil :notes nil :products {}
            :recurrence {:times nil, :until "2020-01-01", :unit "week", :every 3}}))))

(def customers
  [{:id 1 :name "mr blobby" :product-groups {"group 1" {:products {:eggs 1 :carrots 2}}
                                             "group 2" {:products {:eggs 11 :carrots 2}}
                                             "group 3" {:products {:milk 2 :eggs 12}}}}
   {:id 2 :name "johnny D" :product-groups {"group 4" {:products {:eggs 2}}
                                            "group 5" {:products {:milk 2}}}}
   {:id 3 :name "joe" :product-groups {}}
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
