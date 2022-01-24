(ns cmr.system-int-test.ingest.subscription-time-frame-job-test
  "CMR subscription time-frame job tests."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-system]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [clj-http.client :as client]
   [ring.util.codec :as codec]
   [cmr.common.mime-types :as mt]
   [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each
  (join-fixtures
   [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                          {:grant-all-search? false
                           :grant-all-ingest? false})
    (dev-system/freeze-resume-time-fixture)]))

(deftest subscription-job-endpoint-test
  (let [admin-update-group (echo-util/get-or-create-group (system/context) "admin-update-group")
        admin-update-token (echo-util/login (system/context) "admin1" [admin-update-group])
        _ (echo-util/grant-group-admin (system/context) admin-update-group :update)
        non-admin-token (echo-util/login (system/context) "user1")]
    (testing "returns 401 unauthorized when no auth token is provided"
      (let [response (client/post (url/email-subscription-processing)
                                  {:accept "application/json"
                                   :content-type mt/form-url-encoded
                                   :body (codec/form-encode {:revision-date "2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"})
                                   :connection-manager (system/conn-mgr)
                                   :throw-exceptions false})
            errors (:errors (json/decode (:body response) true))]
        (is (= 401 (:status response)))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "returns 401 unauthorized when regular user token is provided"
      (let [response (client/post (url/email-subscription-processing)
                                  {:accept "application/json"
                                   :content-type mt/form-url-encoded
                                   :body (codec/form-encode {:revision-date "2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"})
                                   :query-params {:token non-admin-token}
                                   :connection-manager (system/conn-mgr)
                                   :throw-exceptions false})
            errors (:errors (json/decode (:body response) true))]
        (is (= 401 (:status response)))
        (is (= ["You do not have permission to perform that action."] errors))))

    (testing "returns 200 with valid time range format"
      (let [response (client/post (url/email-subscription-processing)
                                  {:accept "application/json"
                                   :content-type mt/form-url-encoded
                                   :body (codec/form-encode {:revision-date "2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"})
                                   :query-params {:token admin-update-token}
                                   :connection-manager (system/conn-mgr)
                                   :throw-exceptions false})]
        (is (= 200
               (:status response)))))

    (testing "returns 422 with invalid time range format"
      (let [response (client/post (url/email-subscription-processing)
                                  {:accept "application/json"
                                   :content-type mt/form-url-encoded
                                   :body (codec/form-encode {:revision-date "2000-01-01T10:00:00Z,2010-03-10T12:00:"})
                                   :query-params {:token admin-update-token}
                                   :connection-manager (system/conn-mgr)
                                   :throw-exceptions false})]
        (is (= 422
               (:status response)))))

    (testing "returns 400 when only a start-date is provided"
      (let [response (client/post (url/email-subscription-processing)
                                  {:accept "application/json"
                                   :content-type mt/form-url-encoded
                                   :body (codec/form-encode {:revision-date "2000-01-01T10:00:00Z,"})
                                   :query-params {:token admin-update-token}
                                   :connection-manager (system/conn-mgr)
                                   :throw-exceptions false})]
        (is (= 400
               (:status response)))))

    (testing "returns 400 when only an end-date is provided"
      (let [response (client/post (url/email-subscription-processing)
                                  {:accept "application/json"
                                   :content-type mt/form-url-encoded
                                   :body (codec/form-encode {:revision-date ",2010-03-10T12:00:00Z"})
                                   :query-params {:token admin-update-token}
                                   :connection-manager (system/conn-mgr)
                                   :throw-exceptions false})]
        (is (= 400
               (:status response)))))))
