(ns cmr.system-int-test.ingest.job-management-test
  "CMR subscription processing tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.system-int-test.utils.dev-system-util :as dev-system]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each
  (join-fixtures
   [(dev-system/freeze-resume-time-fixture)]))

(defn- get-email-subscription-processing-job-state
  []
  (client/get
   (url/email-subscription-processing-job-state)
   {:accept "application/json"
    :query-params {:token "mock-echo-system-token"}
    :connection-manager (system/conn-mgr)
    :throw-exceptions false}))

(defn- disable-email-subscription-processing-job
  []
  (client/post
   (url/disable-email-subscription-processing)
   {:accept "application/json"
    :query-params {:token "mock-echo-system-token"}
    :connection-manager (system/conn-mgr)
    :throw-exceptions false}))

(defn- enable-email-subscription-processing-job
  []
  (client/post
   (url/enable-email-subscription-processing)
   {:accept "application/json"
    :query-params {:token "mock-echo-system-token"}
    :connection-manager (system/conn-mgr)
    :throw-exceptions false}))

(deftest subscription-email-processing-job-management-test
  (testing "email-subscription-processing state is PAUSED after calling disable-endpoint"
    (let [response (disable-email-subscription-processing-job)
          response-status (:status response)
          state-response (get-email-subscription-processing-job-state)
          actual-job-state (:state (json/decode (:body state-response) true))]
      (is (= 200 response-status))
      (is (= "PAUSED" actual-job-state))))
  (testing "email-subscription-processing state is NORMAL after calling enable-endpoint from
            a paused state."
    (let [response (enable-email-subscription-processing-job)
          response-status (:status response)
          state-response (get-email-subscription-processing-job-state)
          actual-job-state (:state (json/decode (:body state-response) true))]
      (is (= 200 response-status))
      (is (= "NORMAL" actual-job-state)))))

