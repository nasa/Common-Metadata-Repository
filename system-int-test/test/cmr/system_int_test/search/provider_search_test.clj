(ns cmr.system-int-test.search.provider-search-test
  "Integration tests for providers search"
  (:require
   [clojure.walk :as walk]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"}
                                          {:grant-all-search? false}))
(def error-msg {"errors" ["Provider with provider-id [PROV-FAKE] does not exist."]})

(deftest get-all-providers
   
  (testing "Returns the three providers that we have created"
     (let [providers-result (walk/keywordize-keys (:results (search/find-providers)))
           prov-id-list (map #(:ProviderId %) (:items providers-result))
           prov-count (:hits providers-result)
           provider-list (list "PROV1" "PROV2" "PROV3")]
       (is (= provider-list prov-id-list))
       (is (= 3 prov-count))))
  
  (testing "Returns a specific providers metadata that we have created"
    (let [provider (:ProviderId (first (:items (walk/keywordize-keys (:results (search/find-providers "PROV1"))))))]
      (is (= "PROV1" provider))))
  
  (testing "Get a provider that is not in CMR"
    (let [error-response (:results (search/find-providers "PROV-FAKE"))]
      (is (= error-msg error-response)))))
