(ns cmr.ingest.test.api.generic-documents
  "This tests functions in generic documents."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as u :refer [are3]]
   [cmr.ingest.api.generic-documents :as gendoc]))

(comment deftest required-query-parameters-test
  "Tests the required query parameters functionality works."
  (are3 [expected request required-query-parameters]
        (try
          (let [actual (gendoc/validate-any-required-query-parameters request required-query-parameters)]
            (is (= expected (first actual))))
          (catch Exception e
            (is (= expected (str (.getMessage e))))))

        "Test that the required query parameter exists"
        nil
        {:params {:provider "PROV2"}}
        {:provider cmr.ingest.services.messages/provider-does-not-exist}

        "Test that the required query parameter doesn't exist"
        (cmr.ingest.services.messages/provider-does-not-exist)
        {:params {:provider1 "PROV2"}}
        {:provider cmr.ingest.services.messages/provider-does-not-exist}

        "Test that more than 1 required query parameter works."
        nil
        {:params {:provider "PROV2"
                  :provider2 "PROV3"}}
        {:provider cmr.ingest.services.messages/provider-does-not-exist
         :provider2 cmr.ingest.services.messages/provider-does-not-exist}

        "Test when more than 1 required query parameter exists and 1 fails."
        (cmr.ingest.services.messages/provider-does-not-exist)
        {:params {:provider "PROV2"
                  :provider3 "PROV3"}}
        {:provider cmr.ingest.services.messages/provider-does-not-exist
         :provider2 cmr.ingest.services.messages/provider-does-not-exist}))
