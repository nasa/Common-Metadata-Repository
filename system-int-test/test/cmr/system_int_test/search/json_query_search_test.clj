(ns cmr.system-int-test.search.json-query-search-test
  "Integration test for JSON Query specific search issues. General JSON query search tests will be
  included in other files by condition."
  (:require [clojure.test :refer :all]
            [cmr.search.services.messages.common-messages :as msg]
            [cmr.system-int-test.utils.search-util :as search]))

(deftest validation-test
  (testing "invalide JSON condition names"
    (are [concept-type invalid-keys json-search]
         (= {:status 400
             :errors [(format "Invalid JSON condition name(s) %s for %s search."
                              (mapv name invalid-keys)
                              (name concept-type))]}
            (search/find-refs-with-json-query concept-type {} json-search))
         :collection [:foo] {:foo "bar"}
         :collection [:not-right] {:not {:or [{:provider "PROV1"}
                                              {:not-right "123"}]}}
         ;; We do not support JSON Queries for granules yet
         :granule [:provider] {:provider "PROV1"})))