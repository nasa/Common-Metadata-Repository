(ns cmr.system-int-test.search.collection-humanized-search-test
  "Integration test for CMR collection search by humanized fields"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.services.humanizers.humanizer-report-service :as hrs]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url]))

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

(defn- get-cached-report
  "Pull the report data from its cache."
  []
  (let [full-url (str (url/search-read-caches-url)
                      "/"
                      (name hrs/report-cache-key)
                      "/"
                      (name hrs/csv-report-cache-key))
        admin-read-group-concept-id (e/get-or-create-group (s/context)
                                                           "admin-read-group")
        admin-read-token (e/login (s/context)
                                  "admin"
                                  [admin-read-group-concept-id])
        response (client/request {:url full-url
                                  :method :get
                                  :query-params {:token admin-read-token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]
    ;; Make sure the status returned success
    (when-not (= status 200)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(deftest humanizer-report
  (let [coll1 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection
                                             1
                                             {:ShortName "A"
                                              :Version "V1"
                                              :Platforms [(data-umm-cmn/platform
                                                           {:ShortName "TERRA"
                                                            :Instruments
                                                            [(data-umm-cmn/instrument {:ShortName "GPS RECEIVERS"})]})]}))
        coll2 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection
                                             2
                                             {:ShortName "B"
                                              :Version "V2"
                                              :Platforms [(data-umm-cmn/platform {:ShortName "AM-1"})]}))
        coll3 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection
                                             3
                                             {:ShortName "C"
                                              :Version "V3"
                                              :Projects (data-umm-cmn/projects "USGS_SOFIA")
                                              :ScienceKeywords [{:Category "Bioosphere"
                                                                 :Topic "Topic1"
                                                                 :Term "Term1"}
                                                                {:Category "Bio sphere"
                                                                 :Topic "Topic2"
                                                                 :Term "Term2"}]}))]

    (index/wait-until-indexed)
    ;; Refresh the metadata cache
    (search/refresh-collection-metadata-cache)
    (testing "Humanizer report csv"
      (let [report (search/get-humanizers-report)]
        (is (= ["provider,concept_id,short_name,version,original_value,humanized_value"
                (format "PROV1,%s,A,V1,GPS RECEIVERS,GPS Receivers" (:concept-id coll1))
                (format "PROV1,%s,B,V2,AM-1,Terra" (:concept-id coll2))
                (format "PROV1,%s,C,V3,Bioosphere,Biosphere" (:concept-id coll3))
                (format "PROV1,%s,C,V3,USGS_SOFIA,USGS SOFIA" (:concept-id coll3))]
               (str/split report #"\n")))
        (testing "Ensure that the returned report is the same as the cached one"
          (is (= report (get-cached-report))))))))

(defn- get-humanizer-report-batch-size
  "Returns the currently configured value for the humanizer report batch size."
  []
  (-> (side/eval-form `(hrs/humanizer-report-collection-batch-size))
      :body
      Integer/parseInt))

(deftest humanizer-report-batch
  (side/eval-form `(hrs/set-humanizer-report-collection-batch-size! 10))
  (let [updated-batch-size (get-humanizer-report-batch-size)]
    ;; Insert more entries than the batch size to test batches
    (doseq [n (range (inc updated-batch-size))]
      (d/ingest-umm-spec-collection
       "PROV1"
       (data-umm-c/collection
        n
        {:ShortName "B"
         :Version n
         :Platforms [(data-umm-cmn/platform {:ShortName "AM-1"})]})))
    (index/wait-until-indexed)
    ;; Refresh the metadata cache
    (search/refresh-collection-metadata-cache)
    (testing "Humanizer report batches"
      (let [report-lines (str/split (search/get-humanizers-report) #"\n")]
        (is (= (count report-lines) (+ 2 updated-batch-size)))
        (doall
         (map-indexed (fn [n actual-line]
                        (let [coll-normalized (+ n 7) ;; First collection concept ID ends with 7
                              coll-suffix (if (< coll-normalized 10)
                                            (str "0" coll-normalized)
                                            (str coll-normalized))]
                          (is (-> (format "PROV1,C12000000\\d+-PROV1,B,%d,AM-1,Terra" n)
                                  re-pattern
                                  (re-find actual-line)
                                  nil?
                                  false?))))
                      (rest report-lines)))))
    (side/eval-form `(hrs/set-humanizer-report-collection-batch-size! 500))))

(deftest search-by-platform-humanized
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:Platforms [(data-umm-cmn/platform {:ShortName "TERRA"})]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:Platforms [(data-umm-cmn/platform {:ShortName "AM-1"})]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:Platforms [(data-umm-cmn/platform {:ShortName "Aqua"})]}))]
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
  (let [i1 (data-umm-cmn/instrument {:ShortName "GPS RECEIVERS"})
        i2 (data-umm-cmn/instrument {:ShortName "GPS"})
        i3 (data-umm-cmn/instrument {:ShortName "LIDAR"})

        p1 (data-umm-cmn/platform {:ShortName "platform_1" :Instruments [i1]})
        p2 (data-umm-cmn/platform {:ShortName "platform_2" :Instruments [i2]})
        p3 (data-umm-cmn/platform {:ShortName "platform_3" :Instruments [i3]})

        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:Platforms [p1]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:Platforms [p2]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:Platforms [p3]}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized instrument"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:instrument-h "GPS Receivers"}))))))

(deftest search-by-project-humanized
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:Projects (data-umm-cmn/projects "USGS SOFIA")}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:Projects (data-umm-cmn/projects "USGS_SOFIA")}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:Projects (data-umm-cmn/projects "OPENDAP")}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized project"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:project-h "USGS SOFIA"}))))))

(deftest search-by-data-center-humanized
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1
                                                     {:DataCenters [(data-umm-cmn/data-center
                                                                     {:Roles ["ARCHIVER"]
                                                                      :ShortName "NSIDC"})]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2
                                                     {:DataCenters [(data-umm-cmn/data-center
                                                                     {:Roles ["ARCHIVER"]
                                                                      :ShortName "NASA/NSIDC_DAAC"})]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3
                                                     {:DataCenters [(data-umm-cmn/data-center
                                                                     {:Roles ["ARCHIVER"]
                                                                      :ShortName "ASF"})]}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized organization"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:data-center-h "NSIDC"}))))))

(deftest search-by-processing-level-id-humanized
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:ProcessingLevel {:Id "1T"}}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:ProcessingLevel {:Id "L1T"}}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:ProcessingLevel {:Id "3"}}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized processing-level-id"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {:processing-level-id-h "1T"}))))))

(deftest search-by-science-keywords-humanized
  (let [sk1 (data-umm-cmn/science-keyword {:Category "bioosphere"
                                           :Topic "topic1"
                                           :Term "term1"})
        sk2 (data-umm-cmn/science-keyword {:Category "category1"
                                           :Topic "bioosphere"
                                           :Term "term1"})
        sk3 (data-umm-cmn/science-keyword {:Category "biosphere"
                                           :Topic "topic1"
                                           :Term "term1"})
        sk4 (data-umm-cmn/science-keyword {:Category "category1"
                                           :Topic "biosphere"
                                           :Term "term1"})
        sk5 (data-umm-cmn/science-keyword {:Category "category1"
                                           :Topic "topic1"
                                           :Term "term1"})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:ScienceKeywords [sk1]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:ScienceKeywords [sk2]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:ScienceKeywords [sk3]}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:ScienceKeywords [sk4]}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 5 {:ScienceKeywords [sk5]}))]
    (index/wait-until-indexed)
    (testing "search collections by humanized science keyword"
      (is (d/refs-match? [coll1 coll3]
                         (search/find-refs
                           :collection
                           {:science-keywords-h {:0 {:Category "biosphere"}}})))
      (is (d/refs-match? [coll2 coll4]
                         (search/find-refs
                           :collection
                           {:science-keywords-h {:0 {:Topic "biosphere"}}})))
      (is (d/refs-match? [coll1 coll2 coll3 coll4]
                         (search/find-refs
                           :collection
                           {:science-keywords-h {:0 {:any "biosphere"}}}))))))

(deftest search-by-granule-data-format-humanized
  (let [aadi1 {:ArchiveAndDistributionInformation
               {:FileDistributionInformation
                [{:FormatType "Binary"
                  :AverageFileSize 50
                  :AverageFileSizeUnit "MB"
                  :Fees "None currently"
                  :Format "NetCDF-3"}]}}
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection aadi1))]
    (index/wait-until-indexed)

    (testing "search collections by humanized granule data format"
      (is (d/refs-match? [coll1]
                         (search/find-refs :collection {:granule-data-format-h "NetCDF"})))
      (is (d/refs-match? [coll1]
                         (search/find-refs :collection {:granule-data-format "NetCDF-3"})))
      (is (d/refs-match? [coll1]
                         (search/find-refs :collection {:granule-data-format "netcdf-3"})))
      (is (d/refs-match? [coll1]
                         (search/find-refs :collection {"granule-data-format-h[]" "NetCDF"}))))

    (testing "CMR-7112: search by humanized field and include_granule_counts does not result in exception"
      (is (d/refs-match? [coll1]
                         (search/find-refs :collection {"granule-data-format-h[]" "NetCDF"
                                                        :include_granule_counts true}))))))
