(ns ^{:doc "Integration test for elasticsearch data service"}
  cmr.indexer.services.index-service-test
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
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
                               :version-id version-id}
                     :temporal-coverage {:range-date-times [{:beginning-date-time (t/date-time 1996 2 24 22 20 41)
                                                             :ending-date-time (t/date-time 1997 3 25 23 23 43 123)}]}}
        expected {:concept-id concept-id
                  :entry-title dataset-id
                  :entry-title.lowercase "a minimal valid collection v 1"
                  :provider-id provider-id
                  :provider-id.lowercase "prov1"
                  :short-name short-name
                  :short-name.lowercase "minimal"
                  :version-id version-id
                  :version-id.lowercase "1"
                  :start-date "1996-02-24T22:20:41.000Z"
                  :end-date "1997-03-25T23:23:43.123Z"}
        actual (svc/concept->elastic-doc concept umm-concept)]
    (is (= expected actual))))
