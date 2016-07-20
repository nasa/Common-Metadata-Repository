(ns cmr.search.test.data.metadata-retrieval.metadata-transformer
  (require [clojure.test :refer :all]
           [cmr.common.log :as l]
           [cmr.search.test.data.metadata-retrieval.test-metadata :as tm]
           [cmr.search.data.metadata-retrieval.metadata-transformer :as mt]))

(def original-transform-strategy mt/transform-strategy)

(deftest transform-to-multiple-formats-test
  (testing "Skipping exceptions if failed"
    (let [num-calls (atom 0)
          bad-transform-strategy (fn [& args]
                                   (if (= (swap! num-calls inc) 2)
                                     :weird-transform-strategy
                                     (apply original-transform-strategy args)))]
      (with-redefs [mt/transform-strategy bad-transform-strategy
                    ;; Ignore error message logging to avoid output in tests
                    l/error (fn [& args] nil)]
        (is (= {:echo10 (:metadata tm/echo10-concept)
                :iso19115 (:metadata tm/iso19115-concept)}
               (mt/transform-to-multiple-formats
                ;; The second transform fails so dif10 is excluded in the output
                {} tm/dif-concept [:echo10 :dif10 :iso19115] true)))))))

