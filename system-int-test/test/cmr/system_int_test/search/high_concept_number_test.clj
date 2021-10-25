(ns cmr.system-int-test.search.high-concept-number-test
  "Tests that check how the system handles concepts with high concept numbers."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [cmr.common.util :refer [are3]]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
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
         (is (= 201 (:status concept)))

         (d/refs-match? [concept] (search/find-refs :collection {:concept-id concept-id})))

       "in-range integer value concept-seq-id"
       "C1200382534-PROV1"

       "max value concept-seq-id of 2^31 - 1"
       "C2147483647-PROV1"

       "concept-seq-id of 2^31"
       "C2147483648-PROV1"

       "concept-seq-id an order of magnitude larger than supported integer max"
       "C99999999999-PROV1")

      (testing "basic search still returns values"
        (d/refs-match? @preserved-values
                       (search/find-refs :collection {:provider "PROV1"})))))

  (testing "whether queries that utilize concept-seq-id continue to work"
    (let [c1 (d/ingest-umm-spec-collection "PROV1"
                                           (data-umm-c/collection
                                            {:EntryTitle (str "lucky_sevens")
                                             :Version "1.0"
                                             :ShortName (str "sn_lucky")
                                             :concept-id "C1200382535-PROV1"}))
          c2 (d/ingest-umm-spec-collection "PROV1"
                                           (data-umm-c/collection
                                            {:EntryTitle (str "lonely_eights")
                                             :Version "1.0"
                                             :ShortName (str "sn_lonely")
                                             :concept-id "C8888888888-PROV1"}))
          _g1_c1 (d/ingest "PROV1"
                           (dg/granule-with-umm-spec-collection
                            c1
                            (:concept-id c1)
                            {:granule-ur "Granule1_1"}))

          _g1_c2 (d/ingest "PROV1"
                           (dg/granule-with-umm-spec-collection
                            c2
                            (:concept-id c2)
                            {:granule-ur "Granule1_2"}))]
      (index/wait-until-indexed)
      (testing "a 200 status and some results are returned"
        (is (pos? (count (search/find-refs :collection
                                           {:provider "PROV1"
                                            :has_granules_or_cwic true}))))))))
