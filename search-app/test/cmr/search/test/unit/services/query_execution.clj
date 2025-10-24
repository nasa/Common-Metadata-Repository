(ns cmr.search.test.unit.services.query-execution
  "This tests the query-execution namespace. The main point of this is to make sure that specific
  queries are given the correct execution strategy."
  (:require
   [clojure.test :refer :all]
   [cmr.common.hash-cache :as hc]
   [cmr.elastic-utils.search.es-params-converter :as pc]
   [cmr.elastic-utils.search.query-execution :as qe]
   [cmr.metadata-db.data.concepts :as c]
   [cmr.search.services.query-execution :as search-qe]))

(def test-context
  {:system
   {:caches
    {:provider-cache
     ;; minimal fake hash cache that satisfies the protocol
     (reify hc/CmrHashCache
       (get-map [_ _] {"PROV1" {:provider-id "PROV1" :small true}})
       (key-exists [_ _] true)
       (get-keys [_ _] ["PROV1"])
       (get-value [_ _ field] (when (= field "PROV1")
                                {:provider-id "PROV1" :small true}))
       (get-values [_ _ fields]
         (map #(when (= % "PROV1")
                 {:provider-id "PROV1" :small true})
              fields))
       (reset [_] nil)
       (reset [_ _] nil)
       (set-value [_ _ _ _] nil)
       (set-values [_ _ _] nil)
       (remove-value [_ _ _] nil)
       (cache-size [_ _] 0))}}})

(defn- params->query-execution-strategy
  "Converts parameters to a query and then returns the query execution strategy used on that."
  [concept-type params]
  (-> (pc/parse-parameter-query test-context concept-type params)
      (#'qe/query->execution-strategy)))

;; Integration test that specific set of input parameters will result in query with the specified
;; execution strategy
(deftest parameters-to-query-execution-strategy
  ;; stub get-collection-concept-ids to always return ["C1-PROV1"]
  (with-redefs [c/get-collection-concept-ids
                (fn [_db _provider _granule-ids]
                  ;; Stubbed return value for all tests
                  ["C1-PROV1"])]
    (testing "Collection and Granule Common"
      (doseq [concept-type [:granule :collection]]
        (testing (str "Parameters for " concept-type)
          (are [params expected-strategy]
               (is (= expected-strategy
                      (params->query-execution-strategy concept-type params)))

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
            :elasticsearch

            ;; A different page number forces elastic strategy
            {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :opendata :page-num 2} :elasticsearch

            ;; XML References use elastic strategy
            {:concept-id "G1-PROV1" :result-format :xml} :elasticsearch

            ;; Sorting uses the elastic strategy
            {:concept-id ["G1-PROV1" "G2-PROV1"]
             :result-format :echo10
             :sort-key ["concept-id"]}
            :elasticsearch

            {:concept-id ["G1-PROV1" "G2-PROV1"]
             :page-size 1
             :result-format :echo10}
            :elasticsearch

            ;; A different page number forces elastic strategy
            {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :echo10 :page-num 2} :elasticsearch))))

    (testing "Collection Specific"
      (are [params expected-strategy]
           (is (= expected-strategy (params->query-execution-strategy :collection params)))

        ;; Facets are supported
        {:concept-id "G1-PROV1" :result-format :opendata :include-facets "true"} :specific-elastic-items

        ;; Facets require elastic strategy
        {:concept-id "G1-PROV1" :result-format :echo10 :include-facets "true"} :elasticsearch

        ;; All revisions uses elastic strategy
        {:concept-id "C1-PROV1" :result-format :opendata :all-revisions "true"} :elasticsearch
        {:concept-id "C1-PROV1" :result-format :echo10 :all-revisions "true"} :elasticsearch

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        ;; Metadata  queries
        {:concept-id "C1-PROV1" :result-format :echo10} :elasticsearch
        {:concept-id "C1-PROV1" :result-format :dif} :elasticsearch
        {:concept-id "C1-PROV1" :result-format :dif10} :elasticsearch
        {:concept-id "C1-PROV1" :result-format :iso19115} :elasticsearch
        {:concept-id "C1-PROV1" :result-format :iso-smap} :elasticsearch))

    (testing "Granule Specific"
      (are [params expected-strategy]
           (is (= expected-strategy (params->query-execution-strategy :granule params)))

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        ;; Direct db queries
        {:concept-id "G1-PROV1" :result-format :echo10} :direct-db
        {:concept-id "G1-PROV1" :result-format :dif} :direct-db
        {:concept-id "G1-PROV1" :result-format :dif10} :direct-db
        {:concept-id "G1-PROV1" :result-format :iso19115} :direct-db
        {:concept-id "G1-PROV1" :result-format :iso-smap} :direct-db

        ;; Multiple are supported
        {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :echo10} :direct-db

        ;; A page size less than the number of items forces elastic strategy
        {:concept-id ["G1-PROV1" "G2-PROV1"]
         :page-size 2
         :result-format :echo10}
        :direct-db))))

