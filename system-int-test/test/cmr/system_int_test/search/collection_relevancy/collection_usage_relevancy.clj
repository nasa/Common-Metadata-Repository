(ns cmr.system-int-test.search.collection-relevancy.collection-usage-relevancy
  "This tests the CMR Search API's community usage relevancy scoring and ranking
  capabilities"
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.config :as config]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.data.query-to-elastic :as query-to-elastic]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
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
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-use-relevancy-score! true))
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
      (is (= ["Relevancy 2" "Relevancy 3" "Relevancy 1"] (map :name results)))))

  (testing "Collection missing from metrics file"
    (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS"
                                      :entry-title "Relevancy 4"
                                      :version-id "5"}))
    (index/wait-until-indexed)

    (let [results (:refs (search/find-refs :collection {:keyword "Relevancy"}))]
      (is (= "Relevancy 4" (:name (last results))))))

  (testing "Turn off using relevancy score"
    (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-use-relevancy-score! false))
    (let [results (:refs (search/find-refs :collection {:keyword "Relevancy"}))]
      (is (= ["Relevancy 4" "Relevancy 3" "Relevancy 2" "Relevancy 1"] (map :name results))))))

(deftest keyword-relevancy-takes-precedence
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-use-relevancy-score! true))
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
    (is (= ["AST_05" "Relevancy 1" "Relevancy 2" "Relevancy 3"] (map :name results))))

  (testing "Usage sort takes precedence over keyword Relevancy"
    (let [results (:refs (search/find-refs :collection {:keyword "Relevancy" :sort-key "-usage-score"}))]
      (is (= ["Relevancy 2" "Relevancy 3" "Relevancy 1" "AST_05"]
             (map :name results))))))

(deftest ingest-metrics-after-collections
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-use-relevancy-score! true))
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
    (is (= ["Relevancy 2" "Relevancy 3" "Relevancy 1"] (map :name results)))))

(deftest change-metrics
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-use-relevancy-score! true))
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
    (is (= ["Relevancy 3" "Relevancy 1" "Relevancy 2"] (map :name results)))))

;; Outside of keyword search, allow the user to sort by community usage
(deftest sort-by-community-usage
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-use-relevancy-score! true))
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
      (is (= ["Relevancy 1" "Relevancy 3" "Relevancy 2"]
             (map :name results)))))
  (testing "Sort by usage descending"
    (let [results (:refs (search/find-refs :collection {:sort-key "-usage-score"}))]
      (is (= ["Relevancy 2" "Relevancy 3" "Relevancy 1"]
             (map :name results))))))

;; More complicated example/test with entries with version N/A - N/A version entries are
;; currently added to all collection versions. See CMR-3594.
(def sample-csv-not-provided-versions
  (str "Product,Version,Hosts\n"
       "AMSR-L1A,3,10\n"
       "AMSR-L1A,N/A,50\n"
       "AG_VIRTUAL,1,100\n"
       "AG_MAPSS,2,30\n"))

(deftest community-usage-not-provided-versions
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-use-relevancy-score! true))
  (ingest-community-usage-metrics sample-csv-not-provided-versions)
  (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A"
                                    :entry-title "AMSR-L1A V3 Relevancy"
                                    :version-id "3"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A"
                                    :entry-title "AMSR-L1A V2 Relevancy"
                                    :version-id "2"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_VIRTUAL"
                                    :entry-title "AG_VIRTUAL Relevancy"
                                    :version-id "1"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS"
                                    :entry-title "AG_MAPSS Relevancy"
                                    :version-id "2"}))
  (index/wait-until-indexed)
  (let [results (:refs (search/find-refs :collection {:keyword "Relevancy"}))]
    (is (= ["AG_VIRTUAL Relevancy" "AMSR-L1A V3 Relevancy" "AMSR-L1A V2 Relevancy" "AG_MAPSS Relevancy"]
           (map :name results)))))
