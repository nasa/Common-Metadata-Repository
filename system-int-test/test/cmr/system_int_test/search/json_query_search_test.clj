(ns cmr.system-int-test.search.json-query-search-test
  "Integration test for JSON Query specific search issues. General JSON query search tests will be
  included in other files by condition."
  (:require [clojure.test :refer :all]
            [cmr.search.services.messages.common-messages :as msg]
            [cmr.system-int-test.utils.search-util :as search]))

(deftest validation-test
  (testing "Invalid JSON condition names"
    (are [concept-type search-map error-message]
         (= {:status 400
             :errors error-message}
            (search/find-refs-with-json-query concept-type {} search-map))
         :collection {:foo "bar"}
         ["Invalid JSON condition name(s) [\"foo\"] for collection search."]

         :collection {:not {:or [{:provider "PROV1"} {:not-right {:another-bad-name "123"}}]}}
         ["Invalid JSON condition name(s) [\"not-right\" \"another-bad-name\"] for collection search."]

         ;; We do not support JSON Queries for granules yet
         :granule {:provider "PROV1"}
         ["Invalid JSON condition name(s) [\"provider\"] for granule search."]))

  (testing "Invalid science keyword parameters"
    (is (= {:status 400 :errors ["Invalid science keyword parameter(s) [\"not\" \"and\"]."]}
           (search/find-refs-with-json-query :collection {} {:science-keywords {:category "cat"
                                                                                :and "bad1"
                                                                                :not "bad2"}})))))