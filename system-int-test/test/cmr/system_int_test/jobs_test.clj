(ns cmr.system-int-test.jobs-test
  "This tests the jobs api."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.transmit.config :as transmit-config]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.system :as s]
            [cheshire.core :as json]))

(defn- perform-action-on-jobs
  "Call the specified endpoint on the jobs api and return the response."
  [method app-jobs-url action token]
  (client/request
    {:url (str app-jobs-url (name action))
     :method method
     :accept :json
     :throw-exceptions false
     :connection-manager (s/conn-mgr)
     :headers {"echo-token" token}}))

(defn- paused?
  "Returns true if the response indicates jobs are paused. Otherwise returns false."
  [response]
  (:paused (json/decode (:body response) true)))

(defn- assert-successful-jobs-control
  "Pause, resume, and verify status for the given jobs URL using a token with permissions."
  [url token]
  (testing "pause returns 204"
    (is (= 204 (:status (perform-action-on-jobs :post url :pause token)))))
  (s/only-with-real-database
    (testing "jobs are marked as paused"
      (is (= [200 true]
             (let [response (perform-action-on-jobs :get url :status token)]
               [(:status response)
                (paused? response)])))))
  (testing "resume returns 204"
    (is (= 204 (:status (perform-action-on-jobs :post url :resume token)))))
  (testing "jobs are not marked as paused"
    (is (= [200 false]
           (let [response (perform-action-on-jobs :get url :status token)]
             [(:status response)
              (paused? response)])))))

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
    (assert-successful-jobs-control (url/ingest-jobs-url) token)
    (s/only-with-real-database
      (assert-successful-jobs-control (url/bootstrap-jobs-url) token))))

(deftest jobs-permissions-test
  (let [token "NO_PERMISSIONS_TOKEN"]
    (assert-invalid-permissions (url/mdb-jobs-url) token)
    (assert-invalid-permissions (url/ingest-jobs-url) token)
    (s/only-with-real-database
      ;; Bootstrap application does not enforce ACLs
      (assert-successful-jobs-control (url/bootstrap-jobs-url) token))))
