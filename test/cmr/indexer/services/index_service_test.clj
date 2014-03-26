(ns ^{:doc "Integration test for elasticsearch data service"}
  cmr.indexer.services.index-service-test
  (:require [clojure.test :refer :all]
            [cmr.indexer.services.index-service :as svc]))

(deftest concept->elastic-doc-test
  (let [short-name "MINIMAL"
        version-id "1"
        dataset-id "A minimal valid collection V 1"
        provider-id "PROV1"
        concept-id "C1234-PROV1"
        concept {"concept-id" concept-id
                 "provider-id" provider-id}
        umm-concept {:entry-id "MINIMAL_1"
                     :entry-title dataset-id
                     :product {:short-name short-name
                               :version-id version-id}}
        expected {:concept-id concept-id
                  :entry-title dataset-id
                  :entry-title.lowercase "a minimal valid collection v 1"
                  :provider-id provider-id
                  :provider-id.lowercase "prov1"
                  :short-name short-name
                  :short-name.lowercase "minimal"
                  :version-id version-id
                  :version-id.lowercase "1"}
        actual (svc/concept->elastic-doc concept umm-concept)]
    (is (= expected actual))))
