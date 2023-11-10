(ns cmr.common-app.test.data.metadata-retrieval.revision-format-map-test
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.data.metadata-retrieval.collection-metadata-cache :as cmn-coll-metadata-cache]
   [cmr.common-app.data.metadata-retrieval.revision-format-map :as crfm]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.mime-types :as mt]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as util]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
   [cmr.redis-utils.test.test-util :as test-util]
   [digest :as digest]))

(use-fixtures :each tk/freeze-resume-time-fixture)
(use-fixtures :each test-util/embedded-redis-server-fixture)

(def all-metadata-formats
  #{:echo10 :iso19115 :dif :dif10 {:format :umm-json :version "1.17.3"}})

(defn lz4-compressed?
  [lz4-info]
  (and (map? lz4-info)
       (= (set (keys lz4-info))
          #{:compressed :decompressed-length})))

(def revision-format-map-data
  {"C1200000022-PROV1"
   {:concept-id "C1200000022-PROV1",
    :revision-id 9,
    :native-format {:format :umm-json, :version "1.17.3"},
    :echo10 "<Collection><ShortName>Mapping...",
    :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
    :umm-json_1.17.3 "{      \"DataLanguage\" : \"Engli..."},
   "C1200000023-PROV1"
   {:concept-id "C1200000023-PROV1",
    :revision-id 9,
    :native-format {:format :umm-json, :version "1.17.3"},
    :echo10 "<Collection><ShortName>SWOT_L2...",
    :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
    :umm-json_1.17.3 "{  \"AdditionalAttributes\" : [ ..."}
   ;; this one is not correct and will get filtered out.
   :concept-id {:concept-id "C1200000023-PROV1"}})

(def dif10-concept
  "A fake concept map with dif10 metadata"
  {:concept-id "C1-PROV1"
   :revision-id 1
   :metadata (str "Some fake metadata stuff - doesn't test the transform "
                  "just the concept->revision-format-map without transforming.")
   :format mt/dif10
   :concept-type :collection})

(def echo10-concept
  "A fake concept map with echo10 metadata"
  {:concept-id "C1-PROV1"
   :revision-id 1
   :metadata "<Collection><ShortName>SWOT_L2..."
   :format mt/echo10
   :concept-type :collection})

(def dif10-concept->revision-format-map
  {:concept-id "C1-PROV1"
   :revision-id 1
   :native-format :dif10
   :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n..."
   :echo10 "<Collection><ShortName>SWOT_L2..."
   :dif "<DIF xmlns=\"http://gcmd.gsfc.n..."
   :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt..."
   {:format :umm-json, :version "1.17.3"}
   "{  \"AdditionalAttributes\" : [ ..."})

(defn transform-multiple
  "moch test function to pass back an example of what the transform function returns."
  [context concept target-formats ignore-exceptions?]
  {:echo10 "<Collection><ShortName>SWOT_L2..."
   :dif "<DIF xmlns=\"http://gcmd.gsfc.n..."
   :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n..."
   :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt..."
   {:format :umm-json, :version "1.17.3"} "{  \"AdditionalAttributes\" : [ ..."})

(defn transform-one
  "moch test function to pass back an example of what the transform function returns."
  [context concept target-format]
  "<Collection><ShortName>SWOT_L2...")

(defn transform-multiple-return-none
  "moch test function to pass back an example of what the transform function returns."
  [context concept target-formats ignore-exceptions?]
  nil)

(deftest cached-formats-test
  (testing "Testing the revision-format-map cached-format function"
    ;; hash-set produces #{"C1200000023-PROV1" "C1200000022-PROV1"}
    (is (= (hash-set "C1200000023-PROV1" "C1200000022-PROV1") (crfm/cached-formats revision-format-map-data)))))

(deftest compression-test
  (let [uncompressed (crfm/concept->revision-format-map nil dif10-concept all-metadata-formats transform-multiple)
        compressed (crfm/compress uncompressed)
        decompressed (crfm/decompress compressed)]
    (testing "testing the format of the revision-format-map"
      (is (= dif10-concept->revision-format-map
             (crfm/concept->revision-format-map nil dif10-concept all-metadata-formats transform-multiple))))
    (testing "Compress"
      (is (:compressed? compressed))
      (is (> (:size compressed) 0))
      (testing "All metadata is compressed"
        (doseq [metadata-format all-metadata-formats]
          (testing (str metadata-format)
            (is (lz4-compressed? (get compressed metadata-format))))))
      (testing "Non metadata fields are not compressed"
        (is (= (select-keys uncompressed
                            [:concept-id :revision-id :native-format])
               (select-keys compressed
                            [:concept-id :revision-id :native-format])))))
    (testing "Decompress"
      (is (not (:compressed? decompressed)))
      (is (not (contains? decompressed :size)))
      (testing "Metadata fields are decompressed"
        (is (= uncompressed
               (dissoc decompressed :compressed?)))))))

(defn test-rfm
  "Creates a revision format map with the specified formats."
  [concept-id revision-id formats]
  (-> (crfm/concept->revision-format-map nil dif10-concept all-metadata-formats transform-multiple)
      (select-keys (concat formats [:native-format]))
      (assoc :concept-id concept-id
             :revision-id revision-id)))

(deftest ^:kaocha/pending add-additional-format-test
  (testing "Decompressed"
    (let [rfm (crfm/concept->revision-format-map nil dif10-concept #{:native} transform-multiple)]
      (is (= (assoc rfm :echo10 (:metadata echo10-concept))
             (crfm/add-additional-format nil :echo10 rfm transform-one)))))
  (testing "Compressed"
    (let [rfm (crfm/compress
               (crfm/concept->revision-format-map nil dif10-concept #{:native} transform-multiple))
          expected (assoc rfm :echo10 (util/string->lz4-bytes (:metadata echo10-concept)))
          actual (crfm/add-additional-format nil :echo10 rfm transform-one)]

      ; A test like (is(= expected actual)) does not work for everyone locally
      ; as there seams to be some problem with testing byte arrays.
      ; To get around this, a new test was come up with which drops the
      ; compressed segment (byte array) which fails and tests that data sepratly
      ; by doing a comparison on the md5 values

      (is (= (util/dissoc-in expected [:echo10 :compressed])
             (util/dissoc-in actual [:echo10 :compressed])))
      (is (= (digest/md5 (String. (get-in expected [:echo10 :compressed]) "UTF-8"))
             (digest/md5 (String. (get-in actual [:echo10 :compressed]) "UTF-8")))))))

(deftest merge-into-cache-map-test
  (let [cache-key cmn-coll-metadata-cache/cache-key
        rhcache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [cache-key]})]
    (hash-cache/reset rhcache cache-key)
    (testing "Not yet in cache map is added to cache"
      (let [existing-rfm (test-rfm "C1-PROV1" 1 [:echo10])
            rfm (test-rfm "C2-PROV1" 1 [:echo10])]
        (hash-cache/set-value rhcache cache-key "C1-PROV1" existing-rfm)
        (crfm/merge-into-cache-map rhcache cache-key rfm)
        (is (= {"C1-PROV1" existing-rfm
                "C2-PROV1" rfm}
               (hash-cache/get-map rhcache cache-key)))))
    (testing "Newer revision replaces older revision"
      (let [rfm1 (test-rfm "C1-PROV1" 1 [:echo10])
            rfm2 (test-rfm "C2-PROV1" 1 [:echo10])
            new-rfm (test-rfm "C2-PROV1" 2 [:echo10])]
        (hash-cache/set-value rhcache cache-key "C1-PROV1" rfm1)
        (hash-cache/set-value rhcache cache-key "C2-PROV1" rfm2)
        (crfm/merge-into-cache-map rhcache cache-key new-rfm)
        (is (= {"C1-PROV1" rfm1
                "C2-PROV1" new-rfm}
               (hash-cache/get-map rhcache cache-key)))))
    (testing "Older revisions are ignored"
      (let [rfm1 (test-rfm "C1-PROV1" 1 [:echo10])
            rfm2 (test-rfm "C2-PROV1" 2 [:echo10])
            existing-cache-map {"C1-PROV1" rfm1
                                "C2-PROV1" rfm2}
            new-rfm (test-rfm "C2-PROV1" 1 [:echo10])]
        (hash-cache/set-value rhcache cache-key "C1-PROV1" rfm1)
        (hash-cache/set-value rhcache cache-key "C2-PROV1" rfm2)
        (crfm/merge-into-cache-map rhcache cache-key new-rfm)
        (is (= existing-cache-map
               (hash-cache/get-map rhcache cache-key)))))
    (testing "Additional formats are merged in"
      (let [rfm1 (test-rfm "C1-PROV1" 1 [:echo10])
            rfm2 (test-rfm "C2-PROV1" 2 [:echo10])
            new-rfm (test-rfm "C2-PROV1" 2 [:dif10 {:format :umm-json :version "1.3"}])
            merged-rfm (test-rfm "C2-PROV1" 2 [:dif10 {:format :umm-json :version "1.3"} :echo10])]
        (hash-cache/set-value rhcache cache-key "C1-PROV1" rfm1)
        (hash-cache/set-value rhcache cache-key "C2-PROV1" rfm2)
        (crfm/merge-into-cache-map rhcache cache-key new-rfm)
        (is (= {"C1-PROV1" rfm1
                "C2-PROV1" merged-rfm}
               (hash-cache/get-map rhcache cache-key)))))))
