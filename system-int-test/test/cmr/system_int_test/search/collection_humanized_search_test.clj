(ns cmr.system-int-test.search.collection-humanized-search-test
  "Integration test for CMR collection search by humanized fields"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common-app.test.side-api :as side]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]
            [cmr.search.services.humanizers.humanizer-report-service :as hrs]
            [cmr.system-int-test.utils.humanizer-util :as hu]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       hu/grant-all-humanizers-fixture
                       hu/save-sample-humanizers-fixture]))

;; Trying out the humanizers report
;; 1. Run a test
;; 2. Refresh the metadata cache.
(comment
  (cmr.search.data.metadata-retrieval.metadata-cache/refresh-cache
    {:system (get-in user/system [:apps :search])}))
;;3. Retrieve the reporting
;;  curl http://localhost:3003/humanizers/report

(deftest humanizer-report
  (d/ingest "PROV1" (dc/collection
                      {:product {:short-name "A"
                                 :long-name "A"
                                 :version-id "V1"}
                       :platforms [(dc/platform {:short-name "TERRA"
                                                 :instruments
                                                 [(dc/instrument {:short-name "GPS RECEIVERS"})]})]}))
  (d/ingest "PROV1" (dc/collection
                      {:product {:short-name "B"
                                 :long-name "B"
                                 :version-id "V2"}
                       :platforms [(dc/platform {:short-name "AM-1"})]}))
  (d/ingest "PROV1" (dc/collection
                      {:product {:short-name "C"
                                 :long-name "C"
                                 :version-id "V3"}
                       :projects (dc/projects "USGS_SOFIA")
                       :science-keywords [{:category "Bioosphere"
                                           :topic "Topic1"
                                           :term "Term1"}
                                          {:category "Bio sphere"
                                           :topic "Topic2"
                                           :term "Term2"}]}))

  (index/wait-until-indexed)
  ;; Refresh the metadata cache
  (search/refresh-collection-metadata-cache)
  (testing "Humanizer report csv"
    (let [report (search/get-humanizers-report)]
      (is (= (str/split report #"\n")
             ["provider,concept_id,short_name,version,original_value,humanized_value"
              "PROV1,C1200000001-PROV1,A,V1,GPS RECEIVERS,GPS Receivers"
              "PROV1,C1200000002-PROV1,B,V2,AM-1,Terra"
              "PROV1,C1200000003-PROV1,C,V3,Bioosphere,Biosphere"
              "PROV1,C1200000003-PROV1,C,V3,USGS_SOFIA,USGS SOFIA"])))))

(deftest humanizer-report-batch
  (side/eval-form `(hrs/set-humanizer-report-collection-batch-size! 10))
  ;; Insert more entries than the batch size to test batches
  (doseq [n (range (inc (hrs/humanizer-report-collection-batch-size)))]
    (d/ingest "PROV1" (dc/collection
                        {:product {:short-name "B"
                                   :long-name "B"
                                   :version-id n}
                         :platforms [(dc/platform {:short-name "AM-1"})]})))
  (index/wait-until-indexed)
  ;; Refresh the metadata cache
  (search/refresh-collection-metadata-cache)
  (testing "Humanizer report batches"
    (let [report-lines (str/split (search/get-humanizers-report) #"\n")]
      (is (= (count report-lines) (+ 2 (hrs/humanizer-report-collection-batch-size))))
      (for [actual-line (rest report-lines)
            n (inc hrs/humanizer-report-collection-batch-size)]
        (is (= actual-line) (str "PROV1,C1200000001-PROV1,B,"n",AM-1,Terra"))))))

(deftest search-by-platform-humanized
  (let [coll1 (d/ingest "PROV1" (dc/collection {:platforms [(dc/platform {:short-name "TERRA"})]}))
        coll2 (d/ingest "PROV1" (dc/collection {:platforms [(dc/platform {:short-name "AM-1"})]}))
        coll3 (d/ingest "PROV1" (dc/collection {:platforms [(dc/platform {:short-name "Aqua"})]}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized platform"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:platform-h "Terra"}))))
    (testing "After humanizer is updated, collection search reflect the updates"
      (hu/save-humanizers
        [{:type "capitalize", :field "platform", :source_value "TERRA", :order 0}])
      (index/wait-until-indexed)
      (is (d/refs-match? [coll1]
                         (search/find-refs :collection {:platform-h "Terra"}))))))

(deftest search-by-instrument-humanized
  (let [i1 (dc/instrument {:short-name "GPS RECEIVERS"})
        i2 (dc/instrument {:short-name "GPS"})
        i3 (dc/instrument {:short-name "LIDAR"})

        p1 (dc/platform {:short-name "platform_1" :instruments [i1]})
        p2 (dc/platform {:short-name "platform_2" :instruments [i2]})
        p3 (dc/platform {:short-name "platform_3" :instruments [i3]})

        coll1 (d/ingest "PROV1" (dc/collection {:platforms [p1]}))
        coll2 (d/ingest "PROV1" (dc/collection {:platforms [p2]}))
        coll3 (d/ingest "PROV1" (dc/collection {:platforms [p3]}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized instrument"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:instrument-h "GPS Receivers"}))))))

(deftest search-by-project-humanized
  (let [coll1 (d/ingest "PROV1" (dc/collection {:projects (dc/projects "USGS SOFIA")}))
        coll2 (d/ingest "PROV1" (dc/collection {:projects (dc/projects "USGS_SOFIA")}))
        coll3 (d/ingest "PROV1" (dc/collection {:projects (dc/projects "OPENDAP")}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized project"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:project-h "USGS SOFIA"}))))))

(deftest search-by-data-center-humanized
  (let [coll1 (d/ingest "PROV1" (dc/collection {:organizations [(dc/org :archive-center "NSIDC")]}))
        coll2 (d/ingest "PROV1" (dc/collection {:organizations [(dc/org :archive-center "NASA/NSIDC_DAAC")]}))
        coll3 (d/ingest "PROV1" (dc/collection {:organizations [(dc/org :archive-center "ASF")]}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized organization"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:data-center-h "NSIDC"}))))))

(deftest search-by-processing-level-id-humanized
  (let [coll1 (d/ingest "PROV1" (dc/collection {:processing-level-id "1T"}))
        coll2 (d/ingest "PROV1" (dc/collection {:processing-level-id "L1T"}))
        coll3 (d/ingest "PROV1" (dc/collection {:processing-level-id "3"}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized processing-level-id"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:processing-level-id-h "1T"}))))))

(deftest search-by-science-keywords-humanized
  (let [sk1 (dc/science-keyword {:category "bioosphere"
                                 :topic "topic1"
                                 :term "term1"})
        sk2 (dc/science-keyword {:category "category1"
                                 :topic "bioosphere"
                                 :term "term1"})
        sk3 (dc/science-keyword {:category "biosphere"
                                 :topic "topic1"
                                 :term "term1"})
        sk4 (dc/science-keyword {:category "category1"
                                 :topic "biosphere"
                                 :term "term1"})
        sk5 (dc/science-keyword {:category "category1"
                                 :topic "topic1"
                                 :term "term1"})
        coll1 (d/ingest "PROV1" (dc/collection {:science-keywords [sk1]}))
        coll2 (d/ingest "PROV1" (dc/collection {:science-keywords [sk2]}))
        coll3 (d/ingest "PROV1" (dc/collection {:science-keywords [sk3]}))
        coll4 (d/ingest "PROV1" (dc/collection {:science-keywords [sk4]}))
        coll5 (d/ingest "PROV1" (dc/collection {:science-keywords [sk5]}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized science keyword"
      (is (d/refs-match? [coll1 coll3]
                         (search/find-refs
                           :collection
                           {:science-keywords-h {:0 {:category "biosphere"}}})))
      (is (d/refs-match? [coll2 coll4]
                         (search/find-refs
                           :collection
                           {:science-keywords-h {:0 {:topic "biosphere"}}})))
      (is (d/refs-match? [coll1 coll2 coll3 coll4]
                         (search/find-refs
                           :collection
                           {:science-keywords-h {:0 {:any "biosphere"}}}))))))
