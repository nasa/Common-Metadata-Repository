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
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variables]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"}))

(deftest elasticsearch-indexes-handle-large-numbers
  ;; :concept-seq-id is derived from the serialization of the numeric portion of a :concept-id
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
    (let [c1 (d/ingest-umm-spec-collection "PROV2"
                                           (data-umm-c/collection
                                            {:EntryTitle (str "lucky_sevens")
                                             :Version "1.0"
                                             :ShortName (str "sn_lucky")
                                             :concept-id "C77777777777-PROV2"}))
          c2 (d/ingest-umm-spec-collection "PROV2"
                                           (data-umm-c/collection
                                            {:EntryTitle (str "lonely_eights")
                                             :Version "1.0"
                                             :ShortName (str "sn_lonely")
                                             :concept-id "C88888888888-PROV2"}))
          _g1_c1 (d/ingest "PROV2"
                           (dg/granule-with-umm-spec-collection
                            c1
                            (:concept-id c1)
                            {:granule-ur "Granule1_1"}))

          _g1_c2 (d/ingest "PROV2"
                           (dg/granule-with-umm-spec-collection
                            c2
                            (:concept-id c2)
                            {:granule-ur "Granule1_2"}))]
      (index/wait-until-indexed)
      (testing "refs are returned as expected"
        (d/refs-match? [c2 c1]
                       (search/find-refs :collection
                                         {:provider "PROV2"
                                          :has_granules_or_cwic true}))))))

(deftest granules-with-high-concept-ids-test
  (testing "granules with high concept-ids are searchable and indexed"
    (let [coll (d/ingest-umm-spec-collection "PROV1"
                                             (data-umm-c/collection
                                              {:EntryTitle (str "gran")
                                               :Version "1.0"
                                               :ShortName (str "sn_gran")}))
          g1_c1 (d/ingest "PROV1"
                          (dg/granule-with-umm-spec-collection
                           coll
                           (:concept-id coll)
                           {:granule-ur "Granule1_1"
                            :concept-id "G55555555555555555-PROV1"}))]
      (index/wait-until-indexed)
      (d/refs-match? [g1_c1] (search/find-refs :granule {:concept-id (:concept-id g1_c1)})))))

(deftest variable-with-high-concept-ids-test
  (testing "variables with high concept-ids are searchable and indexed"
    (let [coll (d/ingest-umm-spec-collection "PROV1"
                                             (data-umm-c/collection
                                              {:EntryTitle (str "normal")
                                               :Version "1.0"
                                               :ShortName (str "sn_normal")}))
          _ (index/wait-until-indexed)
          var-concept (variables/make-variable-concept
                       {:Name "Variable-with-long-id"}
                       {:native-id "var1"
                        :provider-id "PROV1"
                        :concept-id "V222222222222222222-PROV1"
                        :coll-concept-id (:concept-id coll)})
          variable (variables/ingest-variable-with-association var-concept)]
      (index/wait-until-indexed)
      (variables/assert-variable-search-order [variable] (variables/search {:provider "PROV1"}))))
  
  (testing "variable associations with high concept-id searches return as expected"
    (let [coll (d/ingest-umm-spec-collection "PROV3"
                                             (data-umm-c/collection
                                              {:EntryTitle (str "nov_nines")
                                               :Version "1.0"
                                               :ShortName (str "sn_nines")
                                               :concept-id "C999444999999999-PROV3"}))
          _ (index/wait-until-indexed)
          var-concept (variables/make-variable-concept
                       {:Name "Variable2"}
                       {:native-id "var2"
                        :provider-id "PROV3"
                        :coll-concept-id (:concept-id coll)})
          variable (variables/ingest-variable-with-association var-concept)]
      (index/wait-until-indexed)
      (variables/assert-variable-search-order [variable] (variables/search {:provider "PROV3"})))))
