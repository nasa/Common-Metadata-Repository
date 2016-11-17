(ns cmr.system-int-test.search.collection-usage-relevancy
  "This tests the CMR Search API's community usage relevancy scoring and ranking
  capabilities"
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def sample-usage-csv
  (str "Product,Version,Hosts\n"
       "AMSR-L1A,3,10\n"
       "AG_VIRTUAL,1,100\n"
       "AG_MAPSS,2,30\n"
       "AST_05,B,8\n"))

(defn- ingest-community-usage-metrics
 "Ingest sample metrics to use in tests"
 ([]
  (ingest-community-usage-metrics sample-usage-csv))
 ([csv-data]
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)
  (let [admin-update-token (e/login (s/context) "admin" ["admin-update-group-guid"])]
    (hu/update-community-usage-metrics admin-update-token csv-data)
    (index/wait-until-indexed))))

(deftest community-usage-relevancy-scoring
  (ingest-community-usage-metrics)
  (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A"
                                    :entry-title "Relevancy 1"
                                    :version-id "3"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_VIRTUAL"
                                    :entry-title "Relevancy 2"
                                    :version-id "1"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS"
                                    :entry-title "Relevancy 3"
                                    :version-id "2"}))
  (index/wait-until-indexed)

  (testing "Equal keyword relevancy, order by usage"
    (let [results (:refs (search/find-refs :collection {:keyword "Relevancy"}))]
      (is (= (map :name results) ["Relevancy 2" "Relevancy 3" "Relevancy 1"]))))

  (testing "Collection missing from metrics file"
    (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS"
                                      :entry-title "Relevancy 4"
                                      :version-id "5"}))
    (index/wait-until-indexed)

    (let [results (:refs (search/find-refs :collection {:keyword "Relevancy"}))]
      (is (= "Relevancy 4" (:name (last results)))))))

(deftest keyword-relevancy-takes-precedence
  (ingest-community-usage-metrics)
  (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A"
                                    :entry-title "Relevancy 1"
                                    :version-id "3"
                                    :platforms [(dc/platform {:short-name "Relevancy"})]}))
  (d/ingest "PROV1" (dc/collection {:short-name "AST_05"
                                    :entry-id "Relevancy"
                                    :entry-title "AST_05"
                                    :version-id "B"
                                    :projects (dc/projects ["Relevancy"])
                                    :platforms [(dc/platform {:short-name "Relevancy"})]}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_VIRTUAL"
                                    :entry-title "Relevancy 2"
                                    :version-id "1"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS"
                                    :entry-title "Relevancy 3"
                                    :version-id "2"}))
  (index/wait-until-indexed)
  (let [results (:refs (search/find-refs :collection {:keyword "Relevancy"}))]
    (is (= ["AST_05" "Relevancy 1" "Relevancy 2" "Relevancy 3"] (map :name results)))))

(deftest ingest-metrics-after-collections
  (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A"
                                    :entry-title "Relevancy 1"
                                    :version-id "3"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_VIRTUAL"
                                    :entry-title "Relevancy 2"
                                    :version-id "1"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS"
                                    :entry-title "Relevancy 3"
                                    :version-id "2"}))
  (index/wait-until-indexed)
  (ingest-community-usage-metrics)

  (let [results (:refs (search/find-refs :collection {:keyword "Relevancy"}))]
    (is (= (map :name results) ["Relevancy 2" "Relevancy 3" "Relevancy 1"]))))

(deftest change-metrics
  (ingest-community-usage-metrics)

  (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A"
                                    :entry-title "Relevancy 1"
                                    :version-id "3"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_VIRTUAL"
                                    :entry-title "Relevancy 2"
                                    :version-id "1"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS"
                                    :entry-title "Relevancy 3"
                                    :version-id "2"}))
  (index/wait-until-indexed)

  ;; Ingest new community usage metrics and check that results change
  (ingest-community-usage-metrics (str "Product,Version,Hosts\n"
                                       "AMSR-L1A,3,40\n"
                                       "AG_VIRTUAL,1,12\n"
                                       "AG_MAPSS,2,58\n"))

  (let [results (:refs (search/find-refs :collection {:keyword "Relevancy"}))]
    (is (= (map :name results) ["Relevancy 3" "Relevancy 1" "Relevancy 2"]))))

;; Outside of keyword search, allow the user to sort by community usage
(deftest sort-by-community-usage
  (ingest-community-usage-metrics)
  (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A" ;10
                                    :entry-title "Relevancy 1"
                                    :version-id "3"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_VIRTUAL" ; 100
                                    :entry-title "Relevancy 2"
                                    :version-id "1"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS" ;30
                                    :entry-title "Relevancy 3"
                                    :version-id "2"
                                    :platforms [(dc/platform {:short-name "Relevancy"})]}))
  (index/wait-until-indexed)
  (testing "Sort by usage ascending"
    (let [results (:refs (search/find-refs :collection {:sort-key "usage-score"}))]
      (is (= (map :name results) ["Relevancy 1" "Relevancy 3" "Relevancy 2"]))))
  (testing "Sort by usage descending"
    (let [results (:refs (search/find-refs :collection {:sort-key "-usage-score"}))]
      (is (= (map :name results) ["Relevancy 2" "Relevancy 3" "Relevancy 1"])))))
