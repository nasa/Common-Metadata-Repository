(ns cmr.common-app.test.services.kms-fetcher
  "Tests for kms fetcher get-full-hierarchy* functions"
  (:require 
    [clojure.test :refer :all]
    [cmr.common-app.services.kms-fetcher :as kf]))

;; There are a handful of get-full-hierarchy functions. Most of them use the exact same mechanism.
;; So just pick a few popular and different ones to test.
(def sample-keyword-map
  {:spatial-keywords 
    { "some_guid1" {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :uuid "f2ffbe58-8792-413b-805b-3e1c8de1c6ff"}
      "some_guid2" {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CHAD", :uuid "9b328d2c-07c9-4fd8-945d-f8d4d12e0bb3"}}
   :science-keywords 
    { "some_guid1" {:category "category1" :topic "topic1" :term "term1" :variable-level-1 "v11" :uuid "1f2c3b1f-acae-4af0-a759-f0d57ccfc83f"} 
      "some-guid2" {:category "category2" :topic "topic2" :term "term2" :variable-level-1 "v12" :variable-level-2 "v21" :uuid "2f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}}
   :providers 
    {"a-short-name" {:level-0 "l01"  :short-name "A-SHORT-NAME" :long-name "test-long-name-1" :uuid "3f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}}})

(deftest test-shortname-lookup
  (testing "Looking up an existing short-name returns the right result."
   (let [expected {:level-0 "l01"  :short-name "A-SHORT-NAME" :long-name "test-long-name-1" :uuid "3f2c3b1f-acae-4af0-a759-f0d57ccfc83f"} 
         actual (kf/get-full-hierarchy-for-short-name sample-keyword-map :providers "a-ShORT-NAME")]
     (is (= expected actual))))

  (testing "Looking up an non-existing short-name returns nil."
   (let [expected nil 
         actual (kf/get-full-hierarchy-for-short-name sample-keyword-map :providers "UNKNOWN")]
     (is (= expected actual)))))

(deftest test-location-keyword-map-lookup
  (testing "Looking up a root location keyword-map returns the top hierarchy result."
    (let [keyword-map {:category "continent", :type "africa", :subregion-1 "central africa"}
          expected {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :uuid "f2ffbe58-8792-413b-805b-3e1c8de1c6ff"}
          actual (kf/get-full-hierarchy-for-location-keywords sample-keyword-map keyword-map)]
      (is (= expected actual))))

  (testing "Looking up a root un-matching location keyword-map returns nil"
    (let [keyword-map {:category "CONTINENT", :type "AFRICA"}
          expected nil 
          actual (kf/get-full-hierarchy-for-location-keywords sample-keyword-map keyword-map)]
      (is (= expected actual)))))

(deftest test-science-keyword-map-lookup
  (testing "Looking up a root science keyword-map returns the top hierarchy result."
    (let [keyword-map {:category "Category1" :topic "Topic1" :term "Term1" :variable-level-1 "V11"}
          expected {:category "category1" :topic "topic1" :term "term1" :variable-level-1 "v11" :uuid "1f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}
          actual (kf/get-full-hierarchy-for-science-keyword sample-keyword-map keyword-map)]
      (is (= expected actual))))

  (testing "Looking up a root un-matching science keyword-map returns nil"
    (let [keyword-map {:category "category3", :type "type3"}
          expected nil
          actual (kf/get-full-hierarchy-for-science-keyword sample-keyword-map keyword-map)]
      (is (= expected actual)))))
