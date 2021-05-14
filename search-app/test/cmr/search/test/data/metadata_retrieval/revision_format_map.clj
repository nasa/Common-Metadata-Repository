(ns cmr.search.test.data.metadata-retrieval.revision-format-map
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as util]
   [cmr.search.data.metadata-retrieval.revision-format-map :as r]
   [cmr.search.test.data.metadata-retrieval.test-metadata :as tm]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]
   [digest :as digest]))

(use-fixtures :each tk/freeze-resume-time-fixture)

(def all-metadata-formats
  #{:echo10 :iso19115 :dif :dif10 {:format :umm-json :version "1.3"}})

(defn lz4-compressed?
  [lz4-info]
  (and (map? lz4-info)
       (= (set (keys lz4-info))
          #{:compressed :decompressed-length})))

(deftest compression-test
  (let [uncompressed (r/concept->revision-format-map nil tm/dif10-concept all-metadata-formats)
        compressed (r/compress uncompressed)
        decompressed (r/decompress compressed)]
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

(defn- concept-match?
  "Verify the given expected and actual concept matches,
   for ISO19115 concept, we ignore the ids during comparison."
  [expected actual]
  (if (= mt/iso19115 (:format expected))
    (is (= (update expected :metadata expected-conversion/ignore-ids)
           (update actual :metadata expected-conversion/ignore-ids)))
    (is (= expected actual))))

(deftest revision-format-map-to-concept-test
  (let [revision-format-map (r/concept->revision-format-map nil tm/dif10-concept all-metadata-formats)]
    (testing "With Decompressed revision format maps"
      (doseq [metadata-format all-metadata-formats]
        (testing (str "To " metadata-format)
          (concept-match? (tm/concept-in-format metadata-format)
                          (r/revision-format-map->concept
                           metadata-format revision-format-map)))))
    (testing "With Compressed revision format map"
      (is (= (tm/concept-in-format :echo10)
             (r/revision-format-map->concept
              :echo10 (r/compress revision-format-map)))))
    (testing "With native target format"
      ;; DIF was the native format for sample revision format metadata
      (is (= tm/dif10-concept
             (r/revision-format-map->concept
              :native revision-format-map))))))

(deftest concept-to-revision-format-map
  (testing "Convert with native"
    (is (= {:concept-id (:concept-id tm/dif10-concept)
            :revision-id (:revision-id tm/dif10-concept)
            :native-format :dif10
            :dif10 (:metadata tm/dif10-concept)}
           (r/concept->revision-format-map nil tm/dif10-concept #{:native}))))
  (testing "Convert with multiple formats"
    (is (= {:concept-id (:concept-id tm/dif10-concept)
            :revision-id (:revision-id tm/dif10-concept)
            :native-format :dif10
            :dif10 (:metadata tm/dif10-concept)
            :echo10 (:metadata (tm/concept-in-format :echo10))
            {:format :umm-json
             :version "1.3"} (:metadata tm/umm-json-1.3-concept)}
           (r/concept->revision-format-map
            nil tm/dif10-concept
            #{:echo10 {:format :umm-json
                       :version "1.3"}})))))

(deftest ^:kaocha/pending add-additional-format-test
  (testing "Decompressed"
    (let [rfm (r/concept->revision-format-map nil tm/dif10-concept #{:native})]
      (is (= (assoc rfm :echo10 (:metadata tm/echo10-concept))
             (r/add-additional-format nil :echo10 rfm)))))
  (testing "Compressed"
    (let [rfm (r/compress
                (r/concept->revision-format-map nil tm/dif10-concept #{:native}))
          expected (assoc rfm :echo10 (util/string->lz4-bytes (:metadata tm/echo10-concept)))
          actual (r/add-additional-format nil :echo10 rfm)]

      ; A test like (is(= expected actual)) does not work for everyone locally
      ; as there seams to be some problem with testing byte arrays.
      ; To get around this, a new test was come up with which drops the
      ; compressed segment (byte array) which fails and tests that data sepratly
      ; by doing a comparison on the md5 values

      (is (= (util/dissoc-in expected [:echo10 :compressed])
             (util/dissoc-in actual [:echo10 :compressed])))
      (is (= (digest/md5 (String. (get-in expected [:echo10 :compressed]) "UTF-8"))
             (digest/md5 (String. (get-in actual [:echo10 :compressed] )"UTF-8")))))))

(defn test-rfm
  "Creates a revision format map with the specified formats."
  [concept-id revision-id formats]
  (-> (r/concept->revision-format-map nil tm/dif10-concept all-metadata-formats)
      (select-keys (concat formats [:native-format]))
      (assoc :concept-id concept-id
             :revision-id revision-id)))

(deftest merge-into-cache-map-test
  (testing "Not yet in cache map is added to cache"
    (let [existing-rfm (test-rfm "C1-PROV1" 1 [:echo10])
          existing-cache-map {"C1-PROV1" existing-rfm}
          rfm (test-rfm "C2-PROV1" 1 [:echo10])]
      (is (= {"C1-PROV1" existing-rfm
              "C2-PROV1" rfm}
             (r/merge-into-cache-map existing-cache-map rfm)))))
  (testing "Newer revision replaces older revision"
    (let [rfm1 (test-rfm "C1-PROV1" 1 [:echo10])
          rfm2 (test-rfm "C2-PROV1" 1 [:echo10])
          existing-cache-map {"C1-PROV1" rfm1
                              "C2-PROV1" rfm2}
          new-rfm (test-rfm "C2-PROV1" 2 [:echo10])]
      (is (= {"C1-PROV1" rfm1
              "C2-PROV1" new-rfm}
             (r/merge-into-cache-map existing-cache-map new-rfm)))))
  (testing "Older revisions are ignored"
    (let [rfm1 (test-rfm "C1-PROV1" 1 [:echo10])
          rfm2 (test-rfm "C2-PROV1" 2 [:echo10])
          existing-cache-map {"C1-PROV1" rfm1
                              "C2-PROV1" rfm2}
          new-rfm (test-rfm "C2-PROV1" 1 [:echo10])]
      (is (= existing-cache-map
             (r/merge-into-cache-map existing-cache-map new-rfm)))))
  (testing "Additional formats are merged in"
    (let [rfm1 (test-rfm "C1-PROV1" 1 [:echo10])
          rfm2 (test-rfm "C2-PROV1" 2 [:echo10])
          existing-cache-map {"C1-PROV1" rfm1
                              "C2-PROV1" rfm2}
          new-rfm (test-rfm "C2-PROV1" 2 [:dif10 {:format :umm-json :version "1.3"}])
          merged-rfm (test-rfm "C2-PROV1" 2 [:dif10 {:format :umm-json :version "1.3"} :echo10])]
      (is (= {"C1-PROV1" rfm1
              "C2-PROV1" merged-rfm}
             (r/merge-into-cache-map existing-cache-map new-rfm))))))
