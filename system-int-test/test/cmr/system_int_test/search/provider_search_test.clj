(ns cmr.system-int-test.search.provider-search-test
  "Integration tests for provider search"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.search.api.providers :as providers]
   [cmr.system-int-test.utils.provider-util :as prov-util]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"}
                                          {:grant-all-search? false}))
(def error-msg {:errors ["Provider with provider-id [PROV-FAKE] does not exist."]})

;; todo should this check all of the fields are the same as expected?
(deftest get-all-providers
   (testing "Returns the three providers that we have created"
     (let [providers (map #(get % "ProviderId") (:results (search/find-providers)))
           provider-list (list "PROV1" "PROV2" "PROV3")]
       (is (= provider-list providers))))
  (testing "Returns a specific providers metadata that we have created"
    (let [provider (get (:results (search/find-providers "PROV1")) "ProviderId")]
      (is (= "PROV1" provider))))
  ;; todo because this is returning in json it is not getting this right
  (testing "Get a provider that is not in CMR"
    (let [error-response (:body (search/find-providers "PROV-FAKE"))]
      (is (= (json/generate-string error-msg) error-response)))))
