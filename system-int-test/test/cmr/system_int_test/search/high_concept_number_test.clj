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

(deftest elasticsearch-indexes-handle-large-numbers
  ;; :concept-seq-id is derived from the serialization of the numeric portion or a :concept-id
  ;; original elasticsearch indexes stored the value as a signed integer, now it is stored as an unsigned_long
  (testing "whether concepts can be indexed with values larger than 2^31 - 1 as concept-seq-id"
    (let [preserved-values (atom [])]
      (are3
       [concept-id]
       (let [concept (d/ingest-umm-spec-collection "PROV1"
                                                   (data-umm-c/collection
                                                    {:EntryTitle (str "title_" concept-id)
                                                     :Version "1.0"
                                                     :ShortName (str "sn_" concept-id)
                                                     :concept-id concept-id}))]
         (swap! preserved-values conj concept)

         (index/wait-until-indexed)
         (is (= concept-id (:concept-id concept)))

            ;; TODO enable when searching is re-enabled
         #_(d/refs-match? [concept] (search/find-refs :collection {:concept-id concept-id})))

       "in-range integer value concept-seq-id"
       "C1200382534-PROV1"

       "max value concept-seq-id of 2^31 - 1"
       "C2147483647-PROV1"

       "concept-seq-id of 2^31"
       "C2147483648-PROV1"

       "concept-seq-id an order of magnitude larger than supported integer max"
       "C99999999999-PROV1")

      (clojure.pprint/pprint @preserved-values)

      (testing "whether existing searches will work with new field in place"
        (d/refs-match? @preserved-values
                       (search/find-refs :collection
                                         {:provider "PROV1"})))

      (testing "whether queries that utilize concept-seq-id continue to work"
        (d/refs-match? @preserved-values
                       (search/find-refs :collection
                                         {:provider "PROV1"
                                          :has_granules_or_cwic true}))))))
