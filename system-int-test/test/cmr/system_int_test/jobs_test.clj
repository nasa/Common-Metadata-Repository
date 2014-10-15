(ns cmr.system-int-test.jobs-test
  "This tests the jobs api."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.transmit.config :as transmit-config]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn- perform-action-on-jobs
  "Returns the status code from performing the given action on jobs api"
  [token action app-jobs-url]
  (let [response (client/post (str app-jobs-url (name action))
                              {:accept :json
                               :throw-exceptions false
                               :connection-manager (url/conn-mgr)
                               :headers {"echo-token" token}})]
    (:status response)))

(deftest metadata-db-jobs-test
  (testing "invalid token"
    (are [action]
         (= 401
            (perform-action-on-jobs "WRONG-TOKEN" action (url/mdb-jobs-url)))

         :pause
         :resume))

  (testing "successful action"
    (are [action]
         (= 204
            (perform-action-on-jobs (transmit-config/echo-system-token) action (url/mdb-jobs-url)))

         :pause
         :resume)))

(deftest ingest-jobs-test
  (testing "invalid token"
    (are [action]
         (= 401
            (perform-action-on-jobs "WRONG-TOKEN" action (url/ingest-jobs-url)))

         :pause
         :resume))

  (testing "successful action"
    (are [action]
         (= 204
            (perform-action-on-jobs (transmit-config/echo-system-token) action (url/ingest-jobs-url)))

         :pause
         :resume)))
