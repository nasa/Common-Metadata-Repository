(ns cmr.search.test.unit.services.acls.collections-cache
  (:require
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
   [cmr.search.services.acls.collections-cache :as search-coll-for-gran-acls-cache]
   [cmr.common-app.data.search.collection-for-gran-acls-caches :as cmn-coll-for-gran-acls-caches]
   [cmr.redis-utils.test.test-util :as test-util]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.util :refer [are3]]))

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
                     search-coll-for-gran-acls-cache/time-strs->clj-times
                     search-coll-for-gran-acls-cache/clj-times->time-strs)]
      (is (= some-text (:a-field actual)) "field should not change")
      (is (= expected-date (str (:point-of-time actual))) "Date should exist"))))

(use-fixtures :each test-util/embedded-redis-server-fixture)

(def get-collection-for-gran-acls #'search-coll-for-gran-acls-cache/get-collection-for-gran-acls)

(defn create-collection-for-gran-acls-test-entry
 [provider-id entry-title coll-concept-id]
 {:concept-type :collection,
  :provider-id provider-id,
  :EntryTitle entry-title,
  :AccessConstraints {:Value 1},
  ;; this is specific timestamp for tests, do not change without changing converted timestamps in below unit tests
  :TemporalExtents [{:RangeDateTimes [{:BeginningDateTime "1984-05-01T00:00:00.000Z", :EndingDateTime nil}]}],
  :concept-id coll-concept-id})

(deftest get-collection-gran-acls-by-concept-id-test
 (let [coll-by-concept-id-cache-key cmn-coll-for-gran-acls-caches/coll-by-concept-id-cache-key
       coll-by-concept-id-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-concept-id-cache-key]})
       _ (hash-cache/reset coll-by-concept-id-cache coll-by-concept-id-cache-key)
       context {:system {:caches {coll-by-concept-id-cache-key coll-by-concept-id-cache}}}
       test-coll1 (create-collection-for-gran-acls-test-entry "TEST_PROV1" "EntryTitle1" "C123-TEST_PROV1")]
  ;; populate the cache
  (hash-cache/set-value coll-by-concept-id-cache coll-by-concept-id-cache-key "C123-TEST_PROV1" test-coll1)
  (are3 [expected coll-concept-id]
        (is (= expected (get-collection-for-gran-acls context coll-concept-id)))

        "Collection found in cache -> return expected cache"
        {:concept-type :collection,
         :provider-id "TEST_PROV1",
         :EntryTitle "EntryTitle1",
         :AccessConstraints {:Value 1},
         :TemporalExtents [{:RangeDateTimes [{:BeginningDateTime #=(cmr.common.joda-time/date-time 452217600000 "UTC"), :EndingDateTime nil}]}],
         :concept-id "C123-TEST_PROV1"}
        "C123-TEST_PROV1")))

(deftest get-collection-gran-acls-by-concept-id-no-collection-test
 (let [coll-by-concept-id-cache-key cmn-coll-for-gran-acls-caches/coll-by-concept-id-cache-key
       coll-by-concept-id-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-concept-id-cache-key]})
       _ (hash-cache/reset coll-by-concept-id-cache coll-by-concept-id-cache-key)
       context {:system {:caches {coll-by-concept-id-cache-key coll-by-concept-id-cache}}}
       test-coll2 (create-collection-for-gran-acls-test-entry "TEST_PROV1" "EntryTitle8" "C888-TEST_PROV1")
       converted-test-coll2 {:concept-type :collection,
                             :provider-id "TEST_PROV1",
                             :EntryTitle "EntryTitle8",
                             :AccessConstraints {:Value 1},
                             :TemporalExtents [{:RangeDateTimes [{:BeginningDateTime #=(cmr.common.joda-time/date-time 452217600000 "UTC"), :EndingDateTime nil}]}],
                             :concept-id "C888-TEST_PROV1"}]
  ;; populate the cache
  (hash-cache/set-value coll-by-concept-id-cache coll-by-concept-id-cache-key "C456-TEST_PROV1" {})

  (testing "Testing when collection doesn't exist in cache or elastic -> Then returns nil collection"
   ;; mock the set-cache func
   (with-redefs-fn {#'cmn-coll-for-gran-acls-caches/set-caches (fn [context coll-concept-id] nil)}
    #(is (= nil (get-collection-for-gran-acls context "C000-NON_EXISTENT")))))

  (testing "Testing when collection cache has collection, but it is empty map in cache and elastic -> Then should find the collection in elastic, if still empty then returns empty coll"
   (with-redefs-fn {#'cmn-coll-for-gran-acls-caches/set-caches (fn [context coll-concept-id] {})}
    #(is (= {} (get-collection-for-gran-acls context "C456-TEST_PROV1")))))

  (testing "Testing when collection is not in cache, but exists in elastic -> Then should find the collection in elastic and add to cache"
   (with-redefs-fn {#'cmn-coll-for-gran-acls-caches/set-caches (fn [context coll-concept-id] test-coll2)}
    #(is (= converted-test-coll2 (get-collection-for-gran-acls context "C888-TEST_PROV1")))))
  ))

(deftest get-collection-gran-acls-by-provider-id-and-entry-title-test
 (let [coll-by-provider-id-entry-title-cache-key cmn-coll-for-gran-acls-caches/coll-by-provider-id-and-entry-title-cache-key
       coll-by-provider-id-entry-title-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-provider-id-entry-title-cache-key]})
       _ (hash-cache/reset coll-by-provider-id-entry-title-cache coll-by-provider-id-entry-title-cache-key)
       context {:system {:caches {coll-by-provider-id-entry-title-cache-key coll-by-provider-id-entry-title-cache}}}
       test-coll1 (create-collection-for-gran-acls-test-entry "TEST_PROV2" "EntryTitle2" "C123-TEST_PROV2")]
  ;; populate the cache
  (hash-cache/set-value coll-by-provider-id-entry-title-cache coll-by-provider-id-entry-title-cache-key "C123-TEST_PROV2EntryTitle2" test-coll1)
  (hash-cache/set-value coll-by-provider-id-entry-title-cache coll-by-provider-id-entry-title-cache-key "C456-TEST_PROV2EntryTitle2" {})

  (are3 [expected provider-id entry-title]
        (is (= expected (get-collection-for-gran-acls context provider-id entry-title)))

        "Collection found in cache"
        {:concept-type :collection,
         :provider-id "TEST_PROV2",
         :EntryTitle "EntryTitle2",
         :AccessConstraints {:Value 1},
         :TemporalExtents [{:RangeDateTimes [{:BeginningDateTime #=(cmr.common.joda-time/date-time 452217600000 "UTC"), :EndingDateTime nil}]}],
         :concept-id "C123-TEST_PROV2"}
        "C123-TEST_PROV2" "EntryTitle2")))

(deftest get-collection-gran-acls-by-provider-id-and-entry-title-no-collection-test
 (let [coll-by-provider-id-and-entry-title-cache-key cmn-coll-for-gran-acls-caches/coll-by-provider-id-and-entry-title-cache-key
       coll-by-provider-id-and-entry-title-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-provider-id-and-entry-title-cache-key]})
       _ (hash-cache/reset coll-by-provider-id-and-entry-title-cache coll-by-provider-id-and-entry-title-cache-key)
       context {:system {:caches {coll-by-provider-id-and-entry-title-cache coll-by-provider-id-and-entry-title-cache}}}
       test-coll2 (create-collection-for-gran-acls-test-entry "TEST_PROV1" "EntryTitle2" "C888-TEST_PROV1")
       converted-test-coll2 {:concept-type :collection,
                             :provider-id "TEST_PROV1",
                             :EntryTitle "EntryTitle2",
                             :AccessConstraints {:Value 1},
                             :TemporalExtents [{:RangeDateTimes [{:BeginningDateTime #=(cmr.common.joda-time/date-time 452217600000 "UTC"), :EndingDateTime nil}]}],
                             :concept-id "C888-TEST_PROV1"}
       _ (hash-cache/set-value coll-by-provider-id-and-entry-title-cache coll-by-provider-id-and-entry-title-cache-key "SOME_PROVEntryTitleRandom" {})
       coll-added (hash-cache/get-value coll-by-provider-id-and-entry-title-cache coll-by-provider-id-and-entry-title-cache-key "SOME_PROVEntryTitleRandom")
       _ (println "coll-added = " (pr-str coll-added))]
  ;; populate the cache
  ;(hash-cache/set-value coll-by-provider-id-and-entry-title-cache coll-by-provider-id-and-entry-title-cache-key "SOME_PROVEntryTitleRandom" {})
  ;(hash-cache/get-value coll-by-provider-id-and-entry-title-cache coll-by-provider-id-and-entry-title-cache-key "SOME_PROVEntryTitleRandom")


  (testing "Testing when collection doesn't exist in cache or elastic -> Then returns nil collection"
   ;; mock the set-cache func
   (is (= nil (get-collection-for-gran-acls context "NON_EXISTENT" "EntryTitle000")))
   ;(with-redefs-fn {#'cmn-coll-for-gran-acls-caches/set-caches (fn [context provider-id entry-title] nil)}
   ; #(is (= nil (get-collection-for-gran-acls context "NON_EXISTENT" "EntryTitle000"))))

   )

  ;(testing "Testing when collection cache has collection, but it is empty map in cache and elastic -> Then should find the collection in elastic, if still empty then returns empty coll"
  ; (with-redefs-fn {#'cmn-coll-for-gran-acls-caches/set-caches (fn [context provider-id entry-title] {})}
  ;  #(is (= {} (get-collection-for-gran-acls context "TEST_PROV1" "EntryTitle1")))))
  ;
  ;(testing "Testing when collection is not in cache, but exists in elastic -> Then should find the collection in elastic and add to cache"
  ; (with-redefs-fn {#'cmn-coll-for-gran-acls-caches/set-caches (fn [context provider-id entry-title] test-coll2)}
  ;  #(is (= converted-test-coll2 (get-collection-for-gran-acls context "TEST_PROV1" "EntryTitle2")))))

  ))
