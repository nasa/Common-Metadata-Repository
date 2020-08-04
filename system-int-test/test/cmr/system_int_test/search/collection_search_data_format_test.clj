(ns cmr.system-int-test.search.collection-search-data-format-test
  "This tests ingesting and searching for collections in different formats."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.umm-spec-collection :as umm-spec-collection]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"
                                              "provguid2" "PROV2"})
                       hu/grant-all-humanizers-fixture
                       hu/save-sample-humanizers-fixture]))

(def sample-data-format-collection-test
  {:ArchiveAndDistributionInformation
    {:FileArchiveInformation
      [{:Format "Binary"
        :FormatType "Native"
        :FormatDescription "Use the something app to open the binary file."}
       {:Format "netCDF-4"
        :FormatType "Supported"
        :FormatDescription "An acsii file also exists."}]
     :FileDistributionInformation
      [{:Format "NetCDF-4"
        :FormatType "Supported"
        :FormatDescription "An acsii file also exists."}
       {:Format "NetCDF-5"
        :FormatType "Supported"
        :FormatDescription "An acsii file also exists."}
       {:Format "NetCDF-6 and NetCDF-7 or NetCDF-8, and NetCDF-9"
        :FormatType "Supported"
        :FormatDescription "An acsii file also exists."}]}})

(deftest collection-with-file-formats-ingest-test
  "Test the ingest and indexing behavior by searching on data format of a collection that was
   ingested. Also test to make sure one of the data formats was parsed correctly and humanized."

  (testing "ingest and search by data format of a collection with data formats."
    (let [concept (umm-spec-collection/collection-concept sample-data-format-collection-test)
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (index/wait-until-indexed)
      (is (= 1
             (-> (search/make-raw-search-query :collection ".umm_json?granule_data_format=NetCDF-9")
                 (:body)
                 (json/decode true)
                 (:hits))))
      ;; The humanizer fixture contains a Humanizer with NetCDF-4 assigned NetCDF.
      ;; Check to see that the humanizer exits for the data format.
      (is (string/includes? (search/get-humanizers-report) "NetCDF-4,NetCDF")))))
