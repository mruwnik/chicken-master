(ns chicken-master.products-test
  (:require
   [chicken-master.products :as sut]
   [cljs.test :refer-macros [deftest is testing]]))

(deftest test-num-or-nil
  (testing "valid integers"
    (is (= (sut/num-or-nil "1") 1))
    (is (= (sut/num-or-nil "-10") -10)))

  (testing "valid floats"
    (is (= (sut/num-or-nil "1.23456") 1.23456)))

  (testing "strange numbers"
    (is (= (sut/num-or-nil "     1") 1))
    (is (= (sut/num-or-nil "1asd") 1)))

  (testing "invalid numbers"
    (is (nil? (sut/num-or-nil "")))
    (is (nil? (sut/num-or-nil "asd")))
    (is (nil? (sut/num-or-nil "asd23")))
    (is (nil? (sut/num-or-nil nil)))))

(deftest test-round
  (testing "rounding works"
    (is (= (sut/round 1.234567 0) 1))
    (is (= (sut/round 1.234567 1) 1.2))
    (is (= (sut/round 1.234567 2) 1.23))
    (is (= (sut/round 1.234567 3) 1.235))))

(deftest test-prices
  (testing "prices are formatted"
    (is (= (sut/format-price 0) 0))
    (is (= (sut/format-price 10) 0.1))
    (is (= (sut/format-price 1234567890) 12345678.9)))

  (testing "prices get normalised"
    (is (= (sut/normalise-price 0) 0))
    (is (= (sut/normalise-price 10) 1000))
    (is (= (sut/normalise-price 12.34) 1234))
    (is (= (sut/normalise-price 12.345678) 1235))
    (is (= (sut/normalise-price 12.325678) 1233)))

  (testing "nil prices are handled"
    (is (nil? (sut/format-price nil)))
    (is (nil? (sut/normalise-price nil)))))

(deftest collect-products-test
  (testing "no values"
    (is (= (sut/collect-products []) {})))

  (testing "non product fields are ignored"
    (is (= (sut/collect-products [["day" "2021-03-23"] ["who-id" ""]]) {})))

  (testing "items with 0 are ignored"
    (is (= (sut/collect-products [["amount-G__125" "0"] ["product-G__125" "-"]]) {})))

  (testing "products get extracted"
    (is (= (sut/collect-products
            [["amount-G__122" "33"] ["amount-G__119" "23"]
             ["product-G__119" "cheese"] ["product-G__122" "eggs"]])
           {:eggs {:amount 33} :cheese {:amount 23}})))

  (testing "prices are handled"
    (is (= (sut/collect-products
            [["amount-G__122" "33"] ["amount-G__119" "23"] ["price-G__119" "51"]
             ["product-G__119" "cheese"] ["product-G__122" "eggs"]])
           {:eggs {:amount 33} :cheese {:amount 23 :price 5100}})))

  (testing "multiple items of the same type get summed"
    (is (= (sut/collect-products
            [["amount-G__122" "33"] ["amount-G__119" "23"]
             ["product-G__119" "cheese"] ["product-G__122" "cheese"]])
           {:cheese {:amount 56}})))

  (testing "all together"
    (is (= (sut/collect-products
            [["price-G__122" "19.99"] ["amount-G__122" "33"] ["amount-G__125" "0"]
             ["product-G__125" "-"] ["amount-G__119" "23"]
             ["product-G__119" "cheese"] ["product-G__122" "eggs"]
             ["day" "2021-03-23"]
             ["who-id" ""]])
           {:eggs {:amount 33 :price 1999} :cheese {:amount 23}}))))
