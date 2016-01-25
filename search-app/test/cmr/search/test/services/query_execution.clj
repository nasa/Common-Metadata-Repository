(ns cmr.search.test.services.query-execution
  "This tests the query-execution namespace. The main point of this is to make sure that specific
  queries are given the correct execution strategy."
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters.conversion :as pc]
            [cmr.search.services.query-execution :as qe]))


(defn params->query-execution-strategy
  "Converts parameters to a query and then returns the query execution strategy used on that."
  [concept-type params]
  (-> (pc/parse-parameter-query concept-type params)
      (#'qe/query->execution-strategy)))

;; Integration test that specific set of input parameters will result in query with the specified
;; execution strategy
(deftest parameters-to-query-execution-strategy
  (doseq [concept-type [:granule :collection]]
    (testing (str "Parameters for " concept-type)
      (are [params expected-strategy]
        (= expected-strategy (params->query-execution-strategy concept-type params))

        ;; Specific elastic items queries by format
        {:concept-id "G1-PROV1" :result-format :atom} :specific-elastic-items
        {:concept-id "G1-PROV1" :result-format :json} :specific-elastic-items
        {:concept-id "G1-PROV1" :result-format :csv} :specific-elastic-items
        {:concept-id "G1-PROV1" :result-format :opendata} :specific-elastic-items

        ;; Multiple are supported
        {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :opendata} :specific-elastic-items

        ;; Sorting is supported
        {:concept-id ["G1-PROV1" "G2-PROV1"]
         :result-format :opendata
         :sort-key ["concept-id"]}
        :specific-elastic-items

        ;; A page size less than the number of items forces elastic strategy
        {:concept-id ["G1-PROV1" "G2-PROV1"]
         :page-size 2
         :result-format :opendata}
        :specific-elastic-items

        {:concept-id ["G1-PROV1" "G2-PROV1"]
         :page-size 1
         :result-format :opendata}
        :elastic

        ;; A different page number forces elastic strategy
        {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :opendata :page-num 2} :elastic

        ;; All revisions uses elastic strategy
        {:concept-id "G1-PROV1" :result-format :opendata :all-revisions "true"} :elastic

        ;; XML References use elastic strategy
        {:concept-id "G1-PROV1" :result-format :xml} :elastic

        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        ;; Direct transfomer queries
        {:concept-id "G1-PROV1" :result-format :echo10} :direct-transformer
        {:concept-id "G1-PROV1" :result-format :dif} :direct-transformer
        {:concept-id "G1-PROV1" :result-format :dif10} :direct-transformer
        {:concept-id "G1-PROV1" :result-format :iso19115} :direct-transformer
        {:concept-id "G1-PROV1" :result-format :iso-smap} :direct-transformer

        ;; Sorting uses the elastic strategy
        {:concept-id ["G1-PROV1" "G2-PROV1"]
         :result-format :echo10
         :sort-key ["concept-id"]}
        :elastic

        ;; Multiple are supported
        {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :echo10} :direct-transformer

        ;; A page size less than the number of items forces elastic strategy
        {:concept-id ["G1-PROV1" "G2-PROV1"]
         :page-size 2
         :result-format :echo10}
        :direct-transformer

        {:concept-id ["G1-PROV1" "G2-PROV1"]
         :page-size 1
         :result-format :echo10}
        :elastic

        ;; A different page number forces elastic strategy
        {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :echo10 :page-num 2} :elastic

        ;; All revisions uses elastic strategy
        {:concept-id "G1-PROV1" :result-format :echo10 :all-revisions "true"} :elastic

        ;; Facets require elastic strategy
        {:concept-id "G1-PROV1" :result-format :echo10 :include-facets "true"} :elastic

        ;; XML References use elastic strategy
        {:concept-id "G1-PROV1" :result-format :xml} :elastic))))



