(ns cmr.search.test.unit.services.acls.collections-cache
  (:require
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.search.services.acls.collections-cache :as coll-cache]))

(defn- random-text
  "Create a random string by combining all the values from gen/string-alphanumeric"
  []
  (apply str (vec (gen/sample gen/string-alphanumeric))))

(deftest make-dates-safe-for-serialize-test
  "Confirm that an object can be serialized to text and then back"
  (testing "round trip"
    (let [some-text (random-text)
          some-date "2024-12-31T4:3:2"
          supplied-data {:point-of-time some-date :a-field some-text}
          expected-date "2024-12-31T04:03:02.000Z"
          actual (-> supplied-data
                     coll-cache/time-strs->clj-times
                     coll-cache/clj-times->time-strs)]
      (is (= some-text (:a-field actual)) "field should not change")
      (is (= expected-date (str (:point-of-time actual))) "Date should exist"))))

;; TODO attempt to mock out and redef tests to check if something works or not
;; https://clojuredocs.org/clojure.core/with-redefs-fn

;(deftest get-collection-gran-acls-test
; "Test that collection is retrieved from acls."
; (testing "collection is found"
;  (let [context {:system {:caches [cmr.common-app.data.search.collection-for-gran-acls-caches/coll-by-concept-id-cache-key]}}
;        coll-concept-id "C1234-PROV1"
;        actual (cmr.search.services.acls.collections-cache/get-collection-gran-acls context coll-concept-id)
;        ]
;   )
;  ))
;
;(deftest is-a-fn
; (with-redefs-fn {#'http/post (fn [url] {:body "Hello world again"})}
;  #(is (= {:body "Hello world again"} (http/post "http://service.com/greet")))))
