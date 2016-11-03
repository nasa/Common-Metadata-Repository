(ns cmr.system-int-test.search.humanizer.community-usage-metrics-test
  "This tests the CMR Search API's humanizers capabilities"
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.access-control.test.util :as u]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def sample-usage-csv
  "Product,Version,Hosts\nAMSR-L1A,3,4\nAG_VIRTUAL,4,6\nMAPSS_MOD04_L2,N/A,87")

(def sample-usage-data
  [{:short-name "AMSR-L1A"
    :version 3
    :access-count 4}
   {:short-name "AG_VIRTUAL"
     :version 4
     :access-count 6}
   {:short-name "MAPSS_MOD04_L2"
     :access-count 87}])

(deftest update-community-metrics-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)

  (testing "Successful community usage creation")
  (let [admin-update-token (e/login (s/context) "admin" ["admin-update-group-guid"])
        {:keys [status concept-id revision-id]} (hu/update-community-usage-metrics admin-update-token sample-usage-csv)]
    (is (= 201 status))
    (is concept-id)
    (is (= 1 revision-id))
    (hu/assert-humanizers-saved {:community-usage-metrics sample-usage-data} "admin" concept-id revision-id)

    (testing "Get community usage metrics"
      (let [{:keys [status body]} (hu/get-community-usage-metrics)]
        (is (= 200 status))
        (is (= sample-usage-data body))))

    (testing "Successful community usage update"
      (let [existing-concept-id concept-id
            {:keys [status concept-id revision-id]}
            (hu/update-community-usage-metrics admin-update-token "Product,Version,Hosts\nAST_09XT,3,156")]
        (is (= 200 status))
        (is (= existing-concept-id concept-id))
        (is (= 2 revision-id))
        (hu/assert-humanizers-saved {:community-usage-metrics [{:short-name "AST_09XT" :version 3 :access-count 156}]}
                                    "admin" concept-id revision-id)))))
