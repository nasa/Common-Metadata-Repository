(ns cmr.search.test.unit.data.metadata-retrieval.revision-format-map
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.data.metadata-retrieval.revision-format-map :as crfm]
   [cmr.common.mime-types :as mt]
   [cmr.common.time-keeper :as tk]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
   [cmr.search.test.unit.data.metadata-retrieval.test-metadata :as tm]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]))

(use-fixtures :each tk/freeze-resume-time-fixture)

(def all-metadata-formats
  #{:echo10 :iso19115 :dif :dif10 {:format :umm-json :version "1.3"}})

(defn- concept-match?
  "Verify the given expected and actual concept matches,
   for ISO19115 concept, we ignore the ids during comparison."
  [expected actual]
  (if (= mt/iso19115 (:format expected))
    (is (= (update expected :metadata expected-conversion/ignore-ids)
           (update actual :metadata expected-conversion/ignore-ids)))
    (is (= expected actual))))

(deftest revision-format-map-to-concept-test
  (let [revision-format-map (crfm/concept->revision-format-map nil
                                                               tm/dif10-concept
                                                               all-metadata-formats
                                                               metadata-transformer/transform-to-multiple-formats)]
    (testing "With Decompressed revision format maps"
      (doseq [metadata-format all-metadata-formats]
        (testing (str "To " metadata-format)
          (concept-match? (tm/concept-in-format metadata-format)
                          (crfm/revision-format-map->concept
                           metadata-format revision-format-map)))))
    (testing "With Compressed revision format map"
      (is (= (tm/concept-in-format :echo10)
             (crfm/revision-format-map->concept
              :echo10 (crfm/compress revision-format-map)))))
    (testing "With native target format"
      ;; DIF was the native format for sample revision format metadata
      (is (= tm/dif10-concept
             (crfm/revision-format-map->concept
              :native revision-format-map))))))

(deftest concept-to-revision-format-map-test
  (testing "Convert with native"
    (is (= {:concept-id (:concept-id tm/dif10-concept)
            :revision-id (:revision-id tm/dif10-concept)
            :native-format :dif10
            :dif10 (:metadata tm/dif10-concept)}
           (crfm/concept->revision-format-map nil
                                              tm/dif10-concept
                                              #{:native}
                                              metadata-transformer/transform-to-multiple-formats))))
  (testing "Convert with multiple formats"
    (is (= {:concept-id (:concept-id tm/dif10-concept)
            :revision-id (:revision-id tm/dif10-concept)
            :native-format :dif10
            :dif10 (:metadata tm/dif10-concept)
            :echo10 (:metadata (tm/concept-in-format :echo10))
            {:format :umm-json
             :version "1.3"} (:metadata tm/umm-json-1.3-concept)}
           (crfm/concept->revision-format-map
            nil tm/dif10-concept
            #{:echo10 {:format :umm-json
                       :version "1.3"}}
            metadata-transformer/transform-to-multiple-formats)))))
