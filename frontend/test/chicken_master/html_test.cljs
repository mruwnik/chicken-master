(ns chicken-master.html-test
  (:require
   [chicken-master.html :as sut]
   [cljs.test :refer-macros [deftest is testing]]))

(defn make-select [name selected items]
  (let [select (new js/Set (map #(js-obj "selected" (= % selected) "value" %) items))]
    (set! (.-tagName select) "SELECT")
    (set! (.-name select) name)
    select))

(deftest test-extract-input
  (testing "unknown types return nil"
    (is (nil? (sut/extract-input (clj->js {:tagName "BLA bla" :name "bla" :checked true})))))

  (testing "no input type is handled"
    (is (= (sut/extract-input (clj->js {:tagName "INPUT" :name "bla" :value "asd"}))
           ["bla" "asd"])))

  (testing "checkboxes work"
    (is (= (sut/extract-input (clj->js {:tagName "CHECKBOX" :name "bla" :checked true})) ["bla" true]))
    (is (= (sut/extract-input (clj->js {:tagName "CHECKBOX" :name "bla" :checked false :value "bla"}))
           ["bla" false]))
    (is (= (sut/extract-input (clj->js {:tagName "CHECKBOX" :name "bla" :value "asd"})) ["bla" nil])))

  (testing "input checkboxes work"
    (is (= (sut/extract-input (clj->js {:tagName "INPUT" :name "bla" :type "checkbox" :checked true}))
           ["bla" true]))
    (is (= (sut/extract-input (clj->js {:tagName "INPUT" :name "bla" :type "cHEckBOx" :checked true}))
           ["bla" true])))

  (testing "basic inputs work"
    (is (= (sut/extract-input (clj->js {:tagName "INPUT" :name "bla" :type "text" :value true}))
           ["bla" true]))
    (is (= (sut/extract-input (clj->js {:tagName "INPUT" :name "bla" :type "text" :value "ble ble"}))
           ["bla" "ble ble"])))

  (testing "selects work"
    (is (= (sut/extract-input (make-select "bla" nil [:a :b :c :d])) ["bla" nil]))
    (is (= (sut/extract-input (make-select "bla" :missing-item [:a :b :c :d])) ["bla" nil]))))

(deftest test-form-values
  (testing "extraction works"
    (is (= (sut/form-values
            (clj->js {:elements
                      [(clj->js {:tagName "CHECKBOX" :name "bla" :checked true})
                       (clj->js {:tagName "CHECKBOX" :name "ble" :checked nil})
                       (clj->js {:tagName "INPUT" :type "text" :name "name" :value "mr blobby"})
                       (clj->js {:tagName "INPUT" :type "text" :name "flies" :value 12})
                       (clj->js {:tagName "INPUT" :type "text" :name "flies" :value 12})
                       (clj->js {:tagName "BAD INPUT" :name "asda" :value 12})
                       (clj->js {:tagName "UNPUT" :name "afe" :value 12})
                       (make-select "selected" :a [:a :b :c :d])
                       (make-select "not-selected" nil [:a :b :c :d])]}))
           {"bla" true, "ble" nil, "name" "mr blobby", "flies" 12, "selected" :a, "not-selected" nil}))))
