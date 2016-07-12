(ns cmr.search.test.data.metadata-retrieval.revision-format-map
  (require [clojure.test :refer :all]
           [cmr.search.data.metadata-retrieval.revision-format-map :as r]
           [cmr.search.test.data.metadata-retrieval.test-metadata :as test-metadata]))

(def all-metadata-formats
  #{:echo10 :iso19115 :dif :dif10 {:format :umm-json :version "1.3"}})

(def revision-format-map-with-all-formats
  (r/concept->revision-format-map nil test-metadata/dif-concept all-metadata-formats))


(defn lz4-compressed?
  [lz4-info]
  (and (map? lz4-info)
       (= (set (keys lz4-info))
          #{:compressed :decompressed-length})))

(deftest compression-test
  (let [uncompressed revision-format-map-with-all-formats
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
        (is (= (select-keys revision-format-map-with-all-formats
                            [:concept-id :revision-id :native-format])
               (select-keys compressed
                            [:concept-id :revision-id :native-format])))))
    (testing "Decompress"
      (is (not (:compressed? decompressed)))
      (is (not (contains? decompressed :size)))
      (testing "Metadata fields are decompressed"
        (is (= revision-format-map-with-all-formats
               (dissoc decompressed :compressed?)))))))

(deftest revision-format-map-to-concept-test
  (testing "With Decompressed revision format maps"
    (doseq [metadata-format all-metadata-formats]
      (testing (str "To " metadata-format)
        (is (= (test-metadata/concept-in-format metadata-format)
               (r/revision-format-map->concept
                metadata-format revision-format-map-with-all-formats))))))
  (testing "With Compressed revision format map"
    (is (= test-metadata/echo10-concept
           (r/revision-format-map->concept
            :echo10 (r/compress revision-format-map-with-all-formats)))))
  (testing "With native target format"
    ;; DIF was the native format for sample revision format metadata
    (is (= test-metadata/dif-concept
           (r/revision-format-map->concept
            :native revision-format-map-with-all-formats)))))

(deftest concept-to-revision-format-map
  ;; TODO
  ())
