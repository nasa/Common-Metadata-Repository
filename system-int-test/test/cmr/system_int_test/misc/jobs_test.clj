(ns cmr.system-int-test.misc.jobs-test
  "This tests the jobs api."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit-config]))

(defn- perform-action-on-jobs
  "Call the specified endpoint on the jobs api. Parses the response body and returns the status
  and parsed body."
  [method app-jobs-url action token]
  (let [response (client/request
                   {:url (str app-jobs-url (name action))
                    :method method
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)
                    :headers {"authorization" token}})]
    {:status (:status response)
     :body (json/decode (:body response) true)}))

(defn- assert-successful-jobs-control
  "Pause, resume, and verify status for the given jobs URL using a token with permissions."
  [url token]
  (testing "pause returns 204"
    (is (= 204 (:status (perform-action-on-jobs :post url :pause token)))))
  ;; Quartz jobs use the database, so we can only check if jobs are paused when we are using
  ;; the real database
  (s/only-with-real-database
    (testing "jobs are marked as paused"
      (let [response (perform-action-on-jobs :get url :status token)]
        (is (= {:status 200 :body {:paused true}} response)))))
  (testing "resume returns 204"
    (is (= 204 (:status (perform-action-on-jobs :post url :resume token)))))
  (testing "jobs are not marked as paused"
    (let [response (perform-action-on-jobs :get url :status token)]
      (is (= {:status 200 :body {:paused false}} response)))))


(defn- assert-invalid-permissions
  "Ensure the jobs endpoints require a token with ingest system management permission."
  [url token]
  (testing "pause returns 401"
    (is (= 401 (:status (perform-action-on-jobs :post url :pause token)))))
  (testing "resume returns 401"
    (is (= 401 (:status (perform-action-on-jobs :post url :resume token)))))
  (testing "status returns 401"
    (is (= 401 (:status (perform-action-on-jobs :get url :status token))))))

(deftest general-jobs-test
  (let [token (transmit-config/echo-system-token)]
    (assert-successful-jobs-control (url/mdb-jobs-url) token)
    (assert-successful-jobs-control (url/ingest-jobs-url) token)))

(deftest jobs-permissions-test
  (let [token "NO_PERMISSIONS_TOKEN"]
    (assert-invalid-permissions (url/mdb-jobs-url) token)
    (assert-invalid-permissions (url/ingest-jobs-url) token)))
