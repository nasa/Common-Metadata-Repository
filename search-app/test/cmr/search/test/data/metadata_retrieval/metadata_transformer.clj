(ns cmr.search.test.data.metadata-retrieval.metadata-transformer
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
   [cmr.search.test.data.metadata-retrieval.test-metadata :as tm]))

(def original-transform-strategy metadata-transformer/transform-strategy)

(deftest transform-to-multiple-formats-test
  (testing "Skipping exceptions if failed"
    (let [num-calls (atom 0)
          bad-transform-strategy (fn [& args]
                                   (if (= (swap! num-calls inc) 2)
                                     :weird-transform-strategy
                                     (apply original-transform-strategy args)))]
      (with-bindings {#'metadata-transformer/transform-strategy bad-transform-strategy}
        (let [actual (metadata-transformer/transform-to-multiple-formats
                      ;; The second transform fails so dif10 is excluded in the output
                      {} tm/dif10-concept [:echo10 :dif :iso19115] true)
              actual-formats (set (keys actual))]
          ;; We only check the generated formats, not the actual metadata generated for simplicity reasons
          (is (= #{:echo10 :iso19115} actual-formats)))))))
