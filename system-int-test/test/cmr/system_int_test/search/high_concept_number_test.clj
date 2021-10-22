(ns cmr.system-int-test.search.high-concept-number-test
  "Tests that check how the system handles concepts with high concept numbers."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [cmr.common.util :refer [are3]]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest elasticsearch-indexes
  (testing "whether concepts can be indexed with values larger than 2^31 as concept-seq-id"
    (are3 [concept-id]
          (let [concept (d/ingest-umm-spec-collection "PROV1"
                                                      (data-umm-c/collection
                                                       {:EntryTitle (str "title_" concept-id)
                                                        :Version "1.0"
                                                        :ShortName (str "sn_" concept-id)
                                                        :concept-id concept-id}))]

            (index/wait-until-indexed)
            (is (= concept-id (:concept-id concept)))
            (d/refs-match? [concept] (search/find-refs :collection {:concept-id concept-id})))
          "2^31 integer max for ES"
          "C2147483648-PROV1"

          "2^34 sized concept-id"
          "C17179869184-PROV1")))
