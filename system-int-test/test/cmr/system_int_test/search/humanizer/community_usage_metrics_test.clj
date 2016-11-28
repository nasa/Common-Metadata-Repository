(ns cmr.system-int-test.search.humanizer.community-usage-metrics-test
  "This tests the CMR Search API's community usage metric capabilities"
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
  "Product,Version,Hosts\nAMSR-L1A,3,4\nAG_VIRTUAL,3.2,6\nMAPSS_MOD04_L2,N/A,87")

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
        (hu/assert-humanizers-saved {:community-usage-metrics [{:short-name "AST_09XT" :version "3" :access-count 156}]}
                                    "admin" concept-id revision-id)))))

(deftest update-community-usage-no-permission-test
  (testing "Create without token"
    (is (= {:status 401
            :errors ["You do not have permission to perform that action."]}
           (hu/update-community-usage-metrics nil sample-usage-csv))))

  (testing "Create with unknown token"
    (is (= {:status 401
            :errors ["Token ABC does not exist"]}
           (hu/update-community-usage-metrics "ABC" sample-usage-csv))))

  (testing "Create without permission"
    (let [token (e/login (s/context) "user2")]
      (is (= {:status 401
              :errors ["You do not have permission to perform that action."]}
             (hu/update-community-usage-metrics token sample-usage-csv))))))

(deftest update-community-usage-metrics-validation-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)

  (let [admin-update-token (e/login (s/context) "admin" ["admin-update-group-guid"])]
    (testing "Create community usage with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/json] are not supported."]}
             (hu/update-community-usage-metrics admin-update-token sample-usage-csv {:http-options {:content-type :json}}))))

    (testing "Create community usage with nil body"
      (is (= {:status 422,
              :errors
              ["You posted empty content"]}
             (hu/update-community-usage-metrics admin-update-token nil))))

    (testing "Create humanizer with empty csv"
      (is (= {:status 422,
              :errors
              ["You posted empty content"]}
             (hu/update-community-usage-metrics admin-update-token ""))))

    (testing "Missing CSV Column"
      (are3 [column-name csv]
            (is (= {:status 422
                    :errors [(format "A '%s' column is required in community usage CSV data"
                                     column-name)]}
                   (hu/update-community-usage-metrics admin-update-token csv)))

            "Missing product (short-name)"
            "Product" "Version,Hosts\n3,4"

            "Missing hosts (access-count)"
            "Hosts" "Product,Version\nAMSR-L1A,4"))

    (testing "Empty Required CSV Column"
      (testing "Empty Product"
        (is (= {:status 422
                :errors ["Error parsing 'Product' CSV Data. Product may not be empty."]}
               (hu/update-community-usage-metrics admin-update-token
                                                  "Product,Version,Hosts\n,4,64"))))
      (testing "Empty Hosts"
        (is (= {:status 422
                :errors ["Error parsing 'Hosts' CSV Data for collection [AMSR-L1A], version [4]. Hosts may not be empty."]}
               (hu/update-community-usage-metrics admin-update-token
                                                  "Product,Version,Hosts\nAMSR-L1A,4,")))))

    (testing "Maximum product length validations"
      (let [long-value (apply str (repeat 86 "x"))]
        (is (= {:status 400
                :errors [(format
                          "/0/short-name string \"%s\" is too long (length: 86, maximum allowed: 85)"
                          long-value)]}
               (hu/update-community-usage-metrics
                admin-update-token (format "Product,Version,Hosts\n%s,3,4" long-value))))))

    (testing "Maximum version length validations"
      (let [long-value (apply str (repeat 21 "x"))]
        (is (= {:status 400
                :errors [(format
                          "/0/version string \"%s\" is too long (length: 21, maximum allowed: 20)"
                          long-value)]}
               (hu/update-community-usage-metrics
                admin-update-token (format "Product,Version,Hosts\nAST_09XT,%s,4" long-value))))))

    (testing "Non-integer value for hosts (access-count)"
      (is (= {:status 422
              :errors ["Error parsing 'Hosts' CSV Data for collection [AMSR-L1A], version [3]. Hosts must be an integer."]}
             (hu/update-community-usage-metrics admin-update-token "Product,Version,Hosts\nAMSR-L1A,3,x"))))))

(def sample-aggregation-csv
  "Sample CSV to test aggregation of access counts in different CSV entries with the same short-name
   and version"
  "Product,Version,Hosts\nAMSR-L1A,3,4\nAMSR-L1A,3,6\nAMSR-L1A,N/A,87\nAMSR-L1A,N/A,10\nMAPSS_MOD04_L2,4,12")

(def sample-aggregation-data
 "Expected sample aggregated data from sample-aggregation-csv"
 [{:short-name "AMSR-L1A"
   :version "3"
   :access-count 10}
  {:short-name "AMSR-L1A"
   :version "N/A"
   :access-count 97}
  {:short-name "MAPSS_MOD04_L2"
   :version "4"
   :access-count 12}])

;; Test that metrics are combined appropriately when short-name and version match for different
;; CSV entries.
(deftest aggregate-community-metrics-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)

  (testing "Successful community usage aggregation")
  (let [admin-update-token (e/login (s/context) "admin" ["admin-update-group-guid"])
        {:keys [status concept-id revision-id]} (hu/update-community-usage-metrics admin-update-token sample-aggregation-csv)]
    (is (= 201 status))
    (is concept-id)
    (is (= 1 revision-id))
    (hu/assert-humanizers-saved {:community-usage-metrics sample-aggregation-data} "admin" concept-id revision-id)))

(deftest commas-in-access-count-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)

  (testing "Successful community usage aggregation")
  (let [admin-update-token (e/login (s/context) "admin" ["admin-update-group-guid"])
        {:keys [status concept-id revision-id]}
        (hu/update-community-usage-metrics admin-update-token
                                           "\"Product\",\"Version\",\"Hosts\"\n\"AMSR-L1A\",\"3\",\"4,186\"")]
    (is (= 201 status))
    (hu/assert-humanizers-saved
     {:community-usage-metrics[{:short-name "AMSR-L1A"
                                :version "3"
                                :access-count 4186}]}
     "admin" concept-id revision-id)))
