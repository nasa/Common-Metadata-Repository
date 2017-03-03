(ns cmr.search.test.data.metadata-retrieval.metadata-transformer
  (require
   [clojure.test :refer :all]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as util]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as mt]
   [cmr.search.test.data.metadata-retrieval.test-metadata :as tm]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]))

(use-fixtures :each tk/freeze-resume-time-fixture)

(def original-transform-strategy mt/transform-strategy)

(deftest transform-to-multiple-formats-test
  (testing "Skipping exceptions if failed"
    (let [num-calls (atom 0)
          bad-transform-strategy (fn [& args]
                                   (if (= (swap! num-calls inc) 2)
                                     :weird-transform-strategy
                                     (apply original-transform-strategy args)))]
      (with-bindings {#'mt/transform-strategy bad-transform-strategy}
        (let [actual (util/map-values expected-conversion/ignore-ids
                                (mt/transform-to-multiple-formats
                                 ;; The second transform fails so dif10 is excluded in the output
                                 {} tm/dif-concept [:echo10 :dif10 :iso19115] true))
              expected {:echo10 (:metadata (tm/concept-in-format :echo10))
                        :iso19115 (expected-conversion/ignore-ids (:metadata tm/iso19115-concept))}]
           (is (= expected actual))))))) 
