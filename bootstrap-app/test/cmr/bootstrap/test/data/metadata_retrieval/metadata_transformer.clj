(ns cmr.bootstrap.test.data.metadata-retrieval.metadata-transformer
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [cmr.common.mime-types :as mt]
   [cmr.bootstrap.data.metadata-retrieval.metadata-transformer :as metadata-transformer]))

(def original-transform-strategy metadata-transformer/transform-strategy)

;; Define some test metadata
(def dif10-concept
  "A fake concept map with dif10 metadata"
  {:concept-id "C1-PROV1"
   :revision-id 1
   :metadata (slurp (io/resource "example-data/dif10/sample_collection.xml"))
   :format mt/dif10
   :concept-type :collection})

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
                      {} dif10-concept [:echo10 :dif :iso19115] true)
              actual-formats (set (keys actual))]
          ;; We only check the generated formats, not the actual metadata generated for simplicity reasons
          (is (= #{:echo10 :iso19115} actual-formats)))))))
