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
    (is (= (map :name results) ["AST_O5" "Relevancy 1" "Relevancy 2" "Relevancy 3"]))))

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

(deftest community-usage-relevancy-scoring-2
  (ingest-community-usage-metrics)
  (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A"
                                    :entry-title "Relevancy 1"
                                    :version-id "3"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_VIRTUAL"
                                    :entry-title "Relevancy"
                                    :version-id "1"}))
  (d/ingest "PROV1" (dc/collection {:short-name "AG_MAPSS"
                                    :entry-title "Relevancy 3"
                                    :version-id "2"
                                    :platforms [(dc/platform {:short-name "Relevancy"})]}))
  (index/wait-until-indexed)

  (def res (search/find-refs :collection {:keyword "Relevancy"})))

;; Field value score off:
;; Rel 3 = 1.3
;; Rel 2 = 1
;; Rel = 1

;; Field value score on, no metrics
;; Rel 2 = 0

;; Field value score on, with metrics
;; Rel 3 = 1.6
;; Rel 2 = 1 --should be 2?
;; Rel = 1.1

(comment
 ;; no metrics
 {:track_scores true, :query {:function_score {:score_mode :sum, :functions [{:weight 1.4, :filter {:or [{:and ({:regexp {:long-name.lowercase ".*relevancy.*"}})} {:or ({:regexp {:short-name.lowercase "relevancy"}})}]}} {:weight 1.4, :filter {:regexp {:entry-id.lowercase "relevancy"}}} {:weight 1.3, :filter {:or [{:and ({:regexp {:project-ln.lowercase ".*relevancy.*"}})} {:or ({:regexp {:project-sn2.lowercase "relevancy"}})}]}} {:weight 1.3, :filter {:or [{:and ({:regexp {:platform-ln.lowercase ".*relevancy.*"}})} {:or ({:regexp {:platform-sn.lowercase "relevancy"}})}]}} {:weight 1.2, :filter {:or [{:and ({:regexp {:instrument-ln.lowercase ".*relevancy.*"}})} {:or ({:regexp {:instrument-sn.lowercase "relevancy"}})}]}} {:weight 1.2, :filter {:or [{:and ({:regexp {:sensor-ln.lowercase ".*relevancy.*"}})} {:or ({:regexp {:sensor-sn.lowercase "relevancy"}})}]}} {:weight 1.2, :filter {:nested {:path :science-keywords, :filter {:or ({:regexp {:science-keywords.category.lowercase "relevancy"}} {:regexp {:science-keywords.topic.lowercase "relevancy"}} {:regexp {:science-keywords.term.lowercase "relevancy"}} {:regexp {:science-keywords.variable-level-1.lowercase "relevancy"}} {:regexp {:science-keywords.variable-level-2.lowercase "relevancy"}} {:regexp {:science-keywords.variable-level-3.lowercase "relevancy"}})}}}} {:weight 1.1, :filter {:regexp {:spatial-keyword.lowercase "relevancy"}}} {:weight 1.1, :filter {:regexp {:temporal-keyword.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:version-id.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:entry-title.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:provider-id.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:two-d-coord-name.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:processing-level-id.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:data-center.lowercase "relevancy"}}}], :query {:filtered {:query {:match_all {}}, :filter {:and {:filters ({:term {:permitted-group-ids "guest"}} {:query {:query_string {:query "relevancy", :analyzer :whitespace, :default_field :keyword, :default_operator :and}}})}}}}}}}

;; metrics
 {:track_scores true, :query {:function_score {:score_mode :sum, :functions [{:weight 1.4, :filter {:or [{:and ({:regexp {:long-name.lowercase ".*relevancy.*"}})} {:or ({:regexp {:short-name.lowercase "relevancy"}})}]}} {:weight 1.4, :filter {:regexp {:entry-id.lowercase "relevancy"}}} {:weight 1.3, :filter {:or [{:and ({:regexp {:project-ln.lowercase ".*relevancy.*"}})} {:or ({:regexp {:project-sn2.lowercase "relevancy"}})}]}} {:weight 1.3, :filter {:or [{:and ({:regexp {:platform-ln.lowercase ".*relevancy.*"}})} {:or ({:regexp {:platform-sn.lowercase "relevancy"}})}]}} {:weight 1.2, :filter {:or [{:and ({:regexp {:instrument-ln.lowercase ".*relevancy.*"}})} {:or ({:regexp {:instrument-sn.lowercase "relevancy"}})}]}} {:weight 1.2, :filter {:or [{:and ({:regexp {:sensor-ln.lowercase ".*relevancy.*"}})} {:or ({:regexp {:sensor-sn.lowercase "relevancy"}})}]}} {:weight 1.2, :filter {:nested {:path :science-keywords, :filter {:or ({:regexp {:science-keywords.category.lowercase "relevancy"}} {:regexp {:science-keywords.topic.lowercase "relevancy"}} {:regexp {:science-keywords.term.lowercase "relevancy"}} {:regexp {:science-keywords.variable-level-1.lowercase "relevancy"}} {:regexp {:science-keywords.variable-level-2.lowercase "relevancy"}} {:regexp {:science-keywords.variable-level-3.lowercase "relevancy"}})}}}} {:weight 1.1, :filter {:regexp {:spatial-keyword.lowercase "relevancy"}}} {:weight 1.1, :filter {:regexp {:temporal-keyword.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:version-id.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:entry-title.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:provider-id.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:two-d-coord-name.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:processing-level-id.lowercase "relevancy"}}} {:weight 1.0, :filter {:regexp {:data-center.lowercase "relevancy"}}} {:field_value_factor {:field "usage-relevancy-score", :missing 0.0}}], :query {:filtered {:query {:match_all {}}, :filter {:and {:filters ({:term {:permitted-group-ids "guest"}} {:query {:query_string {:query "relevancy", :analyzer :whitespace, :default_field :keyword, :default_operator :and}}})}}}}}}})
