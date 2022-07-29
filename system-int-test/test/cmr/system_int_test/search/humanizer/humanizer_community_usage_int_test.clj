(ns cmr.system-int-test.search.humanizer.humanizer-community-usage-int-test
  "This tests the CMR Search API's humanizer and community usage metric capabilities. The purpose
   is to test the two types of data being saved in the same metadata file. The humanizer and
   community usage APIs are tested individually in other test files in this folder."
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def sample-usage-csv
  "Product,ProductVersion,Hosts\nAMSR-L1A,3,4\nAG_VIRTUAL,3.2,6\nMAPSS_MOD04_L2,N/A,87")

(def sample-usage-data
  [{:short-name "AMSR-L1A"
    :version "3"
    :access-count 4}
   {:short-name "AG_VIRTUAL"
    :version "3.2"
    :access-count 6}
   {:short-name "MAPSS_MOD04_L2"
    :version "N/A"
    :access-count 87}])

(deftest update-humanizers-test
  (let [admin-update-group-concept-id (e/get-or-create-group (s/context) "admin-update-group")
        _  (e/grant-group-admin (s/context) admin-update-group-concept-id :update)
        token (e/login (s/context) "admin" [admin-update-group-concept-id])
        humanizers (hu/make-humanizers)
        {:keys [status concept-id revision-id]} (hu/update-humanizers token humanizers)]

    (testing "update community usage metrics"
      (let [existing-concept-id concept-id
            {:keys [status concept-id revision-id]} (hu/update-community-usage-metrics token sample-usage-csv)]
        (is (= 200 status))
        (is (= existing-concept-id concept-id))
        (is (= 2 revision-id))
        ;; Check that the humanizers have not changed after making changes to the community usage metrics
        (hu/assert-humanizers-saved {:humanizers humanizers
                                     :community-usage-metrics sample-usage-data}
                                    "admin" concept-id revision-id)))

    (testing "update humanizers"
      (let [existing-concept-id concept-id
            updated-humanizers [(second humanizers)]
            {:keys [status concept-id revision-id]} (hu/update-humanizers token updated-humanizers)]
        (is (= 200 status))
        (is (= existing-concept-id concept-id))
        (is (= 3 revision-id))
        ;; Test that the community usage metrics
        (hu/assert-humanizers-saved {:humanizers updated-humanizers
                                     :community-usage-metrics sample-usage-data}
                                    "admin" concept-id revision-id)))))
