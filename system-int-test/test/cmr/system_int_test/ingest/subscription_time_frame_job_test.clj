(ns cmr.system-int-test.ingest.subscription-time-frame-job-test
  "CMR subscription time-frame job tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer [deftest is  testing]]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.url-helper :as url]))

(defn- revision-date-range-query-with-response-status
  ([query-params expected-status]
   (revision-date-range-query-with-response-status query-params expected-status nil))
  ([query-params expected-status expected-msg]
   (let [response (client/post (url/email-subscription-processing)
                               {:accept "application/json"
                                :query-params (merge {:token "mock-echo-system-token"} query-params)
                                :connection-manager (system/conn-mgr)
                                :throw-exceptions false})
         errors (:errors (json/decode (:body response) true))]
     (is (= expected-status
            (:status response)))
     (when expected-msg
       (is (= [expected-msg] errors))))))

(deftest subscriptions-job-endpoint-test
  (testing "subscription-job-endpoint-test"
    (are3 [revision-date-range expected-status expected-msg]
      (revision-date-range-query-with-response-status 
        {:revision-date-range revision-date-range} expected-status expected-msg)

      "returns 400 when the start time is after the end time"
      "2010-01-01T10:00:00Z,2000-03-10T12:00:00Z"
      400 
      "The start date should occur before the end date."

      "returns 400 when the start time equals the end time"
      "2000-03-10T12:00:00Z,2000-03-10T12:00:00Z" 
      400 
      "The start date should occur before the end date."

      "returns 400 when only a start-date is provided"
      "2000-01-01T10:00:00Z," 
      400 
      "Missing end date in revision-date-range."

      "returns 400 when only an end-date is provided"
      ",2010-03-10T12:00:00Z" 
      400 
      "Missing start date in revision-date-range.")

    (are3 [revision-date-range expected-status]
      (revision-date-range-query-with-response-status 
        {:revision-date-range revision-date-range} expected-status)

      "returns 200 with valid time range format"
      "2000-01-01T10:00:00Z,2010-03-10T12:00:00Z" 
      200

      "returns 422 with invalid time range format"
      "2000-01-01T10:00:Z,2010-03-10T12:00:00Z" 
      422)))
