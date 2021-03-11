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
