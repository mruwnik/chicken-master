(ns chicken-master.time-test
  (:require
   [chicken-master.time :as sut]
   [cljs.test :refer-macros [deftest is testing]])
  (:import [goog.date Date]))

(deftest parse-date-test
  (testing "dates get parsed correctly"
    (is (.equals (sut/parse-date (new Date 2020 10 10)) (new Date 2020 10 10)))
    (is (.equals (sut/parse-date "2010-10-11") (new Date 2010 9 11)))
    (is (.equals (sut/parse-date nil) (new Date 1970 0 1)))))

(deftest date-offset-test
  (testing "date offsets work"
    (is (.equals (sut/date-offset (new Date 2020 2 2) 2)
                 (new Date 2020 2 4))))

  (testing "negative offsets work"
    (is (.equals (sut/date-offset (new Date 2020 2 2) -12)
                 (new Date 2020 1 19)))))


(deftest test-start-of-week
  (testing "no extra offset"
    (with-redefs [sut/settings (atom {:first-day-offset 0})]
      (is (= (->> (sut/parse-date "2020-11-22") sut/start-of-week sut/iso-date) "2020-11-22"))

      (doseq [day ["2020-11-22" "2020-11-23" "2020-11-24" "2020-11-25" "2020-11-26" "2020-11-27" "2020-11-28"]]
        (is #(= (-> (sut/parse-date %) sut/start-of-week sut/iso-date) "2020-11-22")))))

  (testing "extra offset"
    (with-redefs [sut/settings (atom {:first-day-offset 1})]
      (is (= (->> (sut/parse-date "2020-11-22") sut/start-of-week sut/iso-date) "2020-11-16"))

      (doseq [day ["2020-11-23" "2020-11-24" "2020-11-25" "2020-11-26" "2020-11-27" "2020-11-28" "2020-11-29"]]
        (is #(= (-> (sut/parse-date %) sut/start-of-week sut/iso-date) "2020-11-23"))))))


(deftest days-range-test
  (testing "getting a days range works"
    (is (= (map sut/iso-date (sut/days-range 12 (new Date 2020 1 1)))
           ["2020-02-01" "2020-02-02" "2020-02-03" "2020-02-04" "2020-02-05" "2020-02-06"
            "2020-02-07" "2020-02-08" "2020-02-09" "2020-02-10" "2020-02-11" "2020-02-12"])))

  (testing "negative ranges are handled"
    (is (= (sut/days-range -12 (new Date 2020 1 1)) []))))

(deftest date-comparison-tests
  (testing "before"
    (is (sut/before? (new Date 2020 1 2) (new Date 2020 1 4)))
    (is (not (sut/before? (new Date 2020 1 2) (new Date 2020 1 2)))))

  (testing "after"
    (is (sut/after? (new Date 2020 1 5) (new Date 2020 1 4)))
    (is (not (sut/before? (new Date 2020 1 2) (new Date 2020 1 2)))))

  (testing "same day"
    (is (sut/same-day? (new Date 2020 1 5) (new Date 2020 1 5)))
    (is (not (sut/same-day? (new Date 2020 1 12) (new Date 2020 1 2))))))

(deftest test-format-date
  (testing "don't show date"
    (with-redefs [sut/settings (atom {:date-format ""})]
      (is (= (sut/format-date (new Date 2020 01 01)) ""))))

  (testing "without name"
    (with-redefs [sut/settings (atom {:date-format "%m/%d"})]
      (is (= (sut/format-date (new Date 2020 01 01)) "2/1"))))

  (testing "with name"
    (with-redefs [sut/settings (atom {:date-format "%D %m/%d"
                                      :day-names ["Niedz" "Pon" "Wt" "Śr" "Czw" "Pt" "Sob"]})]
      (is (= (sut/format-date (new Date 2020 01 01)) "Sob 2/1")))))
