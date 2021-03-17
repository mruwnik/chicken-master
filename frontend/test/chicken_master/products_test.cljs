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
