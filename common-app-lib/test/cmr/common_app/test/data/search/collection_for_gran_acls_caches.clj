(ns cmr.common-app.test.data.search.collection-for-gran-acls-caches
 (:require
  [clojure.test :refer :all]
  [clojure.test.check.generators :as gen]
  [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
  [cmr.redis-utils.test.test-util :as test-util]
  [cmr.common-app.data.search.collection-for-gran-acls-caches :as cmn-coll-for-gran-acls-caches]
  [cmr.common.hash-cache :as hash-cache]
  [cmr.common.joda-time :as joda-time]))

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
                   cmn-coll-for-gran-acls-caches/time-strs->clj-times
                   cmn-coll-for-gran-acls-caches/clj-times->time-strs)]
   (is (= some-text (:a-field actual)) "field should not change")
   (is (= expected-date (str (:point-of-time actual))) "Date should exist"))))

;(use-fixtures :each test-util/embedded-redis-server-fixture)

;(def refresh-entire-cache #'cmn-coll-for-gran-acls-caches/refresh-entire-cache)
;(def set-caches #'cmn-coll-for-gran-acls-caches/set-caches)

;(defn create-collection-for-gran-acls-test-clj-entry
; [provider-id entry-title coll-concept-id]
; {:concept-type :collection,
;  :provider-id provider-id,
;  :EntryTitle entry-title,
;  :AccessConstraints {:Value 1},
;  :TemporalExtents [{:RangeDateTimes [{:BeginningDateTime #=(joda-time/date-time 452217600000 "UTC"), :EndingDateTime nil}]}],
;  :concept-id coll-concept-id})

;(deftest refresh-entire-cache-test
; (let [coll-by-concept-id-cache-key cmn-coll-for-gran-acls-caches/coll-by-concept-id-cache-key
;       coll-by-concept-id-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-concept-id-cache-key]})
;       coll-by-provider-id-and-entry-title-cache-key cmn-coll-for-gran-acls-caches/coll-by-provider-id-and-entry-title-cache-key
;       coll-by-provider-id-and-entry-title-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-provider-id-and-entry-title-cache-key]})
;       _ (hash-cache/reset coll-by-concept-id-cache coll-by-concept-id-cache-key)
;       context {:system {:caches {coll-by-concept-id-cache-key coll-by-concept-id-cache}}}
;       test_collection (create-collection-for-gran-acls-test-clj-entry "PROV_TEST" "EntryTitleA" "C1234-PROV_TEST")
;       collections-found [test_collection]
;       _   (hash-cache/set-value coll-by-concept-id-cache coll-by-concept-id-cache-key "C0-DIFF_PROV" {})]
;  (testing "Testing when multiple collections are found in elastic"
;   (with-redefs-fn {#'cmn-coll-for-gran-acls-caches/fetch-collections (fn [context] collections-found)}
;    #(is (= nil (refresh-entire-cache context))))
;   ;; check redis cache
;   (is (= test_collection (hash-cache/get-value
;                           coll-by-provider-id-and-entry-title-cache
;                           coll-by-provider-id-and-entry-title-cache-key
;                           (str "PROV_TEST" "EntryTitleA"))))
;   (is (= test_collection (hash-cache/get-value
;                           coll-by-concept-id-cache
;                           coll-by-concept-id-cache-key
;                           "C1234-PROV_TEST"))))))

;(deftest set-caches-test
; (let [test_collection (create-collection-for-gran-acls-test-clj-entry "PROV_TESTER" "EntryTitleB" "C1234-PROV_TESTER")
;       coll-by-concept-id-cache-key cmn-coll-for-gran-acls-caches/coll-by-concept-id-cache-key
;       coll-by-concept-id-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-concept-id-cache-key]})
;       coll-by-provider-id-and-entry-title-cache-key cmn-coll-for-gran-acls-caches/coll-by-provider-id-and-entry-title-cache-key
;       coll-by-provider-id-and-entry-title-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-provider-id-and-entry-title-cache-key]})
;       _ (hash-cache/reset coll-by-concept-id-cache coll-by-concept-id-cache-key)
;       context {:system {:caches {coll-by-concept-id-cache-key coll-by-concept-id-cache}}}]
;  (testing "Testing when multiple collections are found in elastic"
;   (with-redefs-fn {#'cmn-coll-for-gran-acls-caches/fetch-collections (fn [context collection-concept-id] test_collection)}
;    #(is (= test_collection (set-caches context "C1234-PROV_TESTER"))))
;   ;; check redis cache
;   (is (= test_collection (hash-cache/get-value
;                           coll-by-provider-id-and-entry-title-cache
;                           coll-by-provider-id-and-entry-title-cache-key
;                           (str "PROV_TESTER" "EntryTitleB"))))
;   (is (= test_collection (hash-cache/get-value
;                           coll-by-concept-id-cache
;                           coll-by-concept-id-cache-key
;                           "C1234-PROV_TESTER"))))))
