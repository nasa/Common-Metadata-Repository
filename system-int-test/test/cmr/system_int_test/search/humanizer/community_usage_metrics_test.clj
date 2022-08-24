(ns cmr.system-int-test.search.humanizer.community-usage-metrics-test
  "This tests the CMR Search API's community usage metric capabilities"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.humanizer-util :as humanizer-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

;; Intentional space before version and empty CSV line for testing
(def sample-usage-csv
  "Product, ProductVersion,Hosts\r\nAMSR-L1A,3,4\nAG_VIRTUAL,3.2,6\nMAPSS_MOD04_L2,N/A,87\n")

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
  (testing "Successful community usage creation")
  (let [admin-update-group-concept-id (echo-util/get-or-create-group
                                       (system/context)
                                       "admin-update-group")
        _  (echo-util/grant-group-admin (system/context)
                                        admin-update-group-concept-id
                                        :update)
        admin-update-token (echo-util/login (system/context)
                                            "admin"
                                            [admin-update-group-concept-id])
        {:keys [status concept-id revision-id]} (humanizer-util/update-community-usage-metrics
                                                 admin-update-token
                                                 sample-usage-csv)]
    (is (= 201 status))
    (is concept-id)
    (is (= 1 revision-id))
    (humanizer-util/assert-humanizers-saved
     {:community-usage-metrics sample-usage-data}
     "admin"
     concept-id
     revision-id)

    (testing "Get community usage metrics"
      (let [{:keys [status body]} (humanizer-util/get-community-usage-metrics)]
        (is (= 200 status))
        (is (= sample-usage-data body))))

    (testing "Successful community usage update"
      (let [existing-concept-id concept-id
            {:keys [status concept-id revision-id]}
            (humanizer-util/update-community-usage-metrics
             admin-update-token
             "Product,ProductVersion,Hosts\nAST_09XT,3,156")]
        (is (= 200 status))
        (is (= existing-concept-id concept-id))
        (is (= 2 revision-id))
        (humanizer-util/assert-humanizers-saved
         {:community-usage-metrics [{:short-name "AST_09XT" :version "3" :access-count 156}]}
         "admin"
         concept-id
         revision-id)))))

(deftest update-community-usage-no-permission-test
  (testing "Create without token"
    (is (= {:status 401
            :errors ["You do not have permission to perform that action."]}
           (humanizer-util/update-community-usage-metrics nil sample-usage-csv))))

  (testing "Create with unknown token"
    (is (= {:status 401
            :errors ["Token does not exist"]}
           (humanizer-util/update-community-usage-metrics "ABC" sample-usage-csv))))

  (testing "Create without permission"
    (let [token (echo-util/login (system/context) "user2")]
      (is (= {:status 401
              :errors ["You do not have permission to perform that action."]}
             (humanizer-util/update-community-usage-metrics token sample-usage-csv))))))

(def comprehensive-usage-test-csv
  "Provider,Product,ProductVersion,Hosts
  PROV1,SHORTNAME1,N/A,100
  PROV1,ENTRYTITLE2,N/A,100
  PROV1,ENTRYTITLE2:1,N/A,100
  PROV1,ENTRY:TITLE3:1,N/A,100
  PROV1,SHORTNAME2,N/A,10
  PROV1,NONEXISTENTCOL1,N/A,1
  PROV1,NONEXISTENTCOL1:1,N/A,1
  PROV2,SHORTNAME1,N/A,100
  PROV2,ENTRYTITLE2,N/A,100
  PROV2,ENTRYTITLE2:1,N/A,100
  PROV2,ENTRY:TITLE3:1,N/A,100
  PROV2,SHORTNAME2,N/A,10
  PROV2,NONEXISTENTCOL2,N/A,1
  PROV2,NONEXISTENTCOL2:1,N/A,1")

(def comprehensive-usage-test-result
  [{:short-name "SHORTNAME1" :version "N/A" :access-count 800}
   {:short-name "SHORTNAME2" :version "N/A" :access-count 20}
   {:short-name "NONEXISTENTCOL1" :version "N/A" :access-count 1}
   {:short-name "NONEXISTENTCOL1:1" :version "N/A" :access-count 1}
   {:short-name "NONEXISTENTCOL2" :version "N/A" :access-count 1}
   {:short-name "NONEXISTENTCOL2:1" :version "N/A" :access-count 1}])

(deftest update-community-metrics-test-with-comprehensive
  (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                         {:ShortName "SHORTNAME1"
                                          :EntryTitle "ENTRYTITLE1"})
                                {:token "mock-echo-system-token"})
  (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                         2
                                         {:ShortName "SHORTNAME1"
                                          :EntryTitle "ENTRYTITLE2"})
                                {:token "mock-echo-system-token"})
  (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                         3
                                         {:ShortName "SHORTNAME1"
                                          :EntryTitle "ENTRY:TITLE3"})
                                {:token "mock-echo-system-token"})
  (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                         {:ShortName "SHORTNAME2"
                                          :EntryTitle "ENTRYTITLE4"})
                                {:token "mock-echo-system-token"})
  (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection
                                         {:ShortName "SHORTNAME1"
                                          :EntryTitle "ENTRYTITLE1"})
                                {:token "mock-echo-system-token"})
  (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection
                                         4
                                         {:ShortName "SHORTNAME1"
                                          :EntryTitle "ENTRYTITLE2"})
                                {:token "mock-echo-system-token"})
  (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection
                                         5
                                         {:ShortName "SHORTNAME1"
                                          :EntryTitle "ENTRY:TITLE3"})
                                {:token "mock-echo-system-token"})
  (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection
                                         {:ShortName "SHORTNAME2"
                                          :EntryTitle "ENTRYTITLE5"})
                                {:token "mock-echo-system-token"})
  (index/wait-until-indexed)
  (testing "Create community usage with invalid comprehensive param"
    (let [response (humanizer-util/update-community-usage-metrics
                    "mock-echo-system-token"
                    comprehensive-usage-test-csv
                    {:http-options {:query-params {:comprehensive "wrong"}}})]
      (is (= {:status 400
              :errors
              ["Parameter comprehensive must take value of true or false but was [wrong]"]}
             response))))

  (testing "Create community usage with comprehensive is true"
    (let [response (humanizer-util/update-community-usage-metrics
                    "mock-echo-system-token"
                    comprehensive-usage-test-csv
                    {:http-options {:query-params {:comprehensive "true"}}})]
      (humanizer-util/assert-humanizers-saved
       {:community-usage-metrics comprehensive-usage-test-result}
       "ECHO_SYS"
       (:concept-id response)
       (:revision-id response))))

  (testing "Create community usage with comprehensive is false"
    (let [response (humanizer-util/update-community-usage-metrics
                    "mock-echo-system-token"
                    comprehensive-usage-test-csv
                    {:http-options {:query-params {:comprehensive "false"}}})]
      (humanizer-util/assert-humanizers-saved
       {:community-usage-metrics comprehensive-usage-test-result}
       "ECHO_SYS"
       (:concept-id response)
       (:revision-id response))))

  (testing "Create community usage with comprehensive is not included"
    (let [response (humanizer-util/update-community-usage-metrics
                    "mock-echo-system-token"
                    comprehensive-usage-test-csv)]
      (humanizer-util/assert-humanizers-saved
       {:community-usage-metrics comprehensive-usage-test-result}
       "ECHO_SYS"
       (:concept-id response)
       (:revision-id response)))))

(deftest update-community-usage-metrics-validation-test
  (let [admin-update-group-concept-id (echo-util/get-or-create-group (system/context) "admin-update-group")
        _  (echo-util/grant-group-admin (system/context) admin-update-group-concept-id :update)
        admin-update-token (echo-util/login (system/context) "admin" [admin-update-group-concept-id])]
    (testing "Create community usage with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/json] are not supported."]}
             (humanizer-util/update-community-usage-metrics
              admin-update-token
              sample-usage-csv
              {:http-options {:content-type :json}}))))

    (testing "Create community usage with nil body"
      (is (= {:status 422,
              :errors
              ["You posted empty content"]}
             (humanizer-util/update-community-usage-metrics admin-update-token nil))))

    (testing "Create humanizer with empty csv"
      (is (= {:status 422,
              :errors
              ["You posted empty content"]}
             (humanizer-util/update-community-usage-metrics admin-update-token ""))))

    (testing "Missing CSV Column"
      (are3 [column-name csv]
        (is (= {:status 422
                :errors [(format "A '%s' column is required in community usage CSV data"
                                 column-name)]}
               (humanizer-util/update-community-usage-metrics admin-update-token csv)))

        "Missing product (short-name)"
        "Product" "ProductVersion,Hosts\n3,4"

        "Missing hosts (access-count)"
        "Hosts" "Product,ProductVersion\nAMSR-L1A,4"))

    (testing "Empty Required CSV Column"
      (testing "Empty Product"
        (is (= {:status 422
                :errors ["Error parsing 'Product' CSV Data. Product may not be empty."]}
               (humanizer-util/update-community-usage-metrics
                admin-update-token
                "Product,ProductVersion,Hosts\n,4,64"))))
      (testing "Empty Hosts"
        (is (= {:status 422
                :errors ["Error parsing 'Hosts' CSV Data. Hosts may not be empty. CSV line entry: [\"AMSR-L1A\" \"4\" \"\"]"]}
               (humanizer-util/update-community-usage-metrics
                admin-update-token
                "Product,ProductVersion,Hosts\nAMSR-L1A,4,")))))

    (testing "Maximum product length validations"
      (let [long-value (apply str (repeat 86 "x"))]
        (is (= {:status 400
                :errors ["#/0/short-name: expected maxLength: 85, actual: 86"]}
               (humanizer-util/update-community-usage-metrics
                admin-update-token
                (format "Product,ProductVersion,Hosts\n%s,3,4" long-value))))))

    (testing "Version is removed if length exceeds 20 characters"
      (let [long-value (apply str (repeat 21 "x"))
            {:keys [status concept-id revision-id]} (humanizer-util/update-community-usage-metrics
                                                     admin-update-token
                                                     (format "Product,ProductVersion,Hosts\nAST_09XT,%s,4" long-value))
            {:keys [status body]} (humanizer-util/get-community-usage-metrics)]
        (is (= status 200))
        (is (= body [{:short-name "AST_09XT" :access-count 4}]))))

    (testing "Non-integer value for hosts (access-count)"
      (is (= {:status 422
              :errors ["Error parsing 'Hosts' CSV Data. Hosts must be an integer. CSV line entry: [\"AMSR-L1A\" \"3\" \"x\"]"]}
             (humanizer-util/update-community-usage-metrics
              admin-update-token
              "Product,ProductVersion,Hosts\nAMSR-L1A,3,x"))))
    (testing "large integer value for hosts (access-count)"
      (is (= {:concept-id "H1200000011-CMR", :revision-id 2, :status 200}
             (humanizer-util/update-community-usage-metrics
              admin-update-token
              "Product,ProductVersion,Hosts\nAMSR-L1A,3,2147483648"))))))

(def sample-aggregation-csv
  "Sample CSV to test aggregation of access counts in different CSV entries with the same short-name
   and version"
  "Product,ProductVersion,Hosts\nAMSR-L1A,3,4\nAMSR-L1A,3,6\nAMSR-L1A,N/A,87\nAMSR-L1A,N/A,10\nMAPSS_MOD04_L2,4,12")

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
  (testing "Successful community usage aggregation"
    (let [admin-update-group-concept-id (echo-util/get-or-create-group (system/context) "admin-update-group")
          _  (echo-util/grant-group-admin (system/context) admin-update-group-concept-id :update)
          admin-update-token (echo-util/login (system/context) "admin" [admin-update-group-concept-id])
          {:keys [status concept-id revision-id]} (humanizer-util/update-community-usage-metrics
                                                   admin-update-token
                                                   sample-aggregation-csv)]
      (is (= 201 status))
      (is concept-id)
      (is (= 1 revision-id))
      (humanizer-util/assert-humanizers-saved
       {:community-usage-metrics sample-aggregation-data}
       "admin"
       concept-id
       revision-id))))

(deftest commas-in-access-count-test
  (testing "Successful community usage aggregation"
    (let [admin-update-group-concept-id (echo-util/get-or-create-group (system/context) "admin-update-group")
          _  (echo-util/grant-group-admin (system/context) admin-update-group-concept-id :update)
          admin-update-token (echo-util/login (system/context) "admin" [admin-update-group-concept-id])
          {:keys [status concept-id revision-id]}
          (humanizer-util/update-community-usage-metrics
           admin-update-token
           "\"Product\",\"ProductVersion\",\"Hosts\"\n\"AMSR-L1A\",\"3\",\"4,186\"")]
      (is (= 201 status))
      (humanizer-util/assert-humanizers-saved
       {:community-usage-metrics [{:short-name "AMSR-L1A"
                                   :version "3"
                                   :access-count 4186}]}
       "admin" concept-id revision-id))))
