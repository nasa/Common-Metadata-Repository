(ns cmr.system-int-test.ingest.subscription-time-frame-job-test
  "CMR subscription time-frame job tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-system]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each
  (join-fixtures
   [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                          {:grant-all-search? false
                           :grant-all-ingest? false})
    (dev-system/freeze-resume-time-fixture)]))

(defn- revision-date-range-query-with-response-status
  [query-params expected-status]
  (let [response (client/post (url/email-subscription-processing)
                              {:accept "application/json"
                               :query-params query-params
                               :connection-manager (system/conn-mgr)
                               :throw-exceptions false})]
    (is (= expected-status
           (:status response)))))

(deftest subscriptions-job-endpoint-test
  (let [admin-update-group (echo-util/get-or-create-group (system/context) "admin-update-group")
        admin-update-token (echo-util/login (system/context) "admin1" [admin-update-group])
        _ (echo-util/grant-group-admin (system/context) admin-update-group :update)
        non-admin-token (echo-util/login (system/context) "user1")]
    (testing "subscription-job-endpoint-test"
      (are3 [query-params expected-status]
        (revision-date-range-query-with-response-status query-params expected-status)

        "returns 200 with valid time range format"
        {:token admin-update-token :revision-date-range "2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"} 200

        "returns 422 with invalid time range format"
        {:token admin-update-token :revision-date-range "2000-01-01T10:00:Z,2010-03-10T12:00:00Z"} 422

        "returns 400 when the start time is after the end time"
        {:token admin-update-token :revision-date-range "2010-01-01T10:00:00Z,2000-03-10T12:00:00Z"} 400

        "returns 400 when the start time equals the end time"
        {:token admin-update-token :revision-date-range "2000-03-10T12:00:00Z,2000-03-10T12:00:00Z"} 400

        "returns 400 when only a start-date is provided"
        {:token admin-update-token :revision-date-range "2000-01-01T10:00:00Z,"} 400

        "returns 400 when only an end-date is provided"
        {:token admin-update-token :revision-date-range ",2010-03-10T12:00:00Z"} 400))))
