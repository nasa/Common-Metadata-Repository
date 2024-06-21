(ns cmr.transmit.test.metadata-db
  "These tests will check the functions in cmr.transmit.metadata-db. The client-id tests will not
   check the functionality of the function called, but instead will check that the request includes
   a client id. To do this the client/request object will be mocked and the mocked function will
   contain a test for the client-id"
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-http.client :as client]
   [cmr.common.util :refer [are3]]
   [cmr.transmit.metadata-db :as mdb]))

(deftest metadata-db-client-id-tests
  (testing "tests provider functions in metadata db to ensure that client id passes through"
      (are3
       [func expected given]
       (let [context {:system {:search-connection
                               {:protocol "http" :host "localhost" :port "3001" :context ""}}}
             action-tester (fn [arg]
                             (is (= "cmr-internal" (:client-id (:headers arg)))
                                 (format "Failed testing %s" (:url arg)))
                             {:status expected :body "{}" :headers {"CMR-Hits" "42"}})]
         (with-redefs [client/request action-tester]
           (let [result (func context given)]
             (is (= expected (:status result)) result))))

       "Create test"
       #'cmr.transmit.metadata-db/create-provider-raw
       200 "prov-01"

       "Read test"
       #'cmr.transmit.metadata-db/read-provider
       200 "prov-01"

       "Update test"
       #'cmr.transmit.metadata-db/update-provider-raw
       200 "prov-01"

       "Delete test"
       #'cmr.transmit.metadata-db/delete-provider-raw
       200 "prov-01")))
