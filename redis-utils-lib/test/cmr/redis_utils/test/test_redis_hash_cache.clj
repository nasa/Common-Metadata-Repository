(ns cmr.redis-utils.test.test-redis-hash-cache
  "Namespace to test redis cache."
  (:require
    [clojure.test :refer :all]
    [cmr.common.hash-cache :as h-cache]
    [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
    [cmr.redis-utils.test.test-util :as test-util]))

(use-fixtures :once test-util/embedded-redis-server-fixture)
(def field-value-map
  {"C1200000001-PROV1"
   {:concept-id "C1200000001-PROV1",
    :revision-id 4,
    :native-format {:format :umm-json, :version "1.17.3"},
    :echo10 "<Collection><ShortName>Mapping...",
    :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
    :umm-json_1.17.3 "{      \"DataLanguage\" : \"Engli..."}
   "C1200000002-PROV1"
   {:concept-id "C1200000002-PROV1",
    :revision-id 4,
    :native-format {:format :umm-json, :version "1.17.3"},
    :echo10 "<Collection><ShortName>SWOT_L2...",
    :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
    :umm-json_1.17.3 "{  \"AdditionalAttributes\" : [ ..."}})

(def single-field-value-map
  {"C1200000003-PROV1" {:concept-id "C1200000003-PROV1",
                        :revision-id 4,
                        :native-format {:format :umm-json, :version "1.17.3"},
                        :echo10 "<Collection><ShortName>SWOT_L2...",
                        :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
                        :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
                        :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
                        :umm-json_1.17.3 "{  \"AdditionalAttributes\" : [ ..."}})
(deftest redis-hash-cache-test
  (let [cache-key :test-hash-cache
        rhcache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [cache-key]})]
    (testing "Clearing the cache with cache-key"
      (h-cache/set-value rhcache cache-key "C1200000003-PROV1" (get single-field-value-map "C1200000003-PROV1"))
      (h-cache/reset rhcache cache-key)
      (is (= nil (h-cache/get-map rhcache cache-key))))

    (testing "Clearing the cache with out cache-key - its already defined when cache is created."
      (h-cache/set-value rhcache cache-key "C1200000003-PROV1" (get single-field-value-map "C1200000003-PROV1"))
      (h-cache/reset rhcache)
      (is (= nil (h-cache/get-map rhcache cache-key))))

    (testing "Testing getting an empty hashmap back"
      (is (= nil (h-cache/get-map rhcache cache-key))))

    (testing "The cache is empty because it hasn't been populated"
      (is (= false (h-cache/key-exists rhcache cache-key))))

    (h-cache/set-value rhcache cache-key "C1200000003-PROV1" {:concept-id "C1200000003-PROV1",:revision-id 4,
                                                              :native-format {:format :umm-json, :version "1.17.3"},
                                                              :echo10 "<Collection><ShortName>SWOT_L2...",
                                                              :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
                                                              :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
                                                              :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
                                                              :umm-json_1.17.3 "{  \"AdditionalAttributes\" : [ ..."})
    (testing "The cache key exists in redis."
      (is (= true (h-cache/key-exists rhcache cache-key))))

    (testing "Testing getting the entire hashmap back"
      (is (= single-field-value-map (h-cache/get-map rhcache cache-key))))

    (testing "Testing getting the fields of the hashmap back"
      (is (= ["C1200000003-PROV1"] (h-cache/get-keys rhcache cache-key))))

    (testing "Testing getting a value back"
      (is (= (get single-field-value-map "C1200000003-PROV1")
            (h-cache/get-value rhcache cache-key "C1200000003-PROV1"))))

    (testing "Testing getting getting multiple values back from a list."
      (is (= (list (get single-field-value-map "C1200000003-PROV1"))
             (h-cache/get-values rhcache cache-key '("C1200000003-PROV1")))))

    (testing "Testing ingesting a field value map and pulling out the hash map"
      (h-cache/set-values rhcache cache-key field-value-map)
      (is (= (merge field-value-map single-field-value-map)
             (h-cache/get-map rhcache cache-key))))

    (testing "Testing getting all of the fields from the hash cache."
      (is (= (set ["C1200000001-PROV1" "C1200000002-PROV1" "C1200000003-PROV1"])
             (set (h-cache/get-keys rhcache cache-key)))))

    (testing "Testing getting a value back from a bigger hash map."
      (is (= (get single-field-value-map "C1200000003-PROV1")
            h-cache/get-value rhcache cache-key "C1200000003-PROV1")))

    (testing "Testing getting multiple values back."
      (is (= (let [full-map (merge field-value-map single-field-value-map)]
              (set [(get full-map "C1200000003-PROV1") (get full-map "C1200000002-PROV1")]))
            (set (h-cache/get-values rhcache cache-key '("C1200000003-PROV1" "C1200000002-PROV1"))))))

    (testing "Testing getting size back from redis."
      (is (= java.lang.Long (type (h-cache/cache-size rhcache cache-key)))))))
