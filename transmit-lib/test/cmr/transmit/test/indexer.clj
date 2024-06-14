(ns cmr.transmit.test.indexer
  "These tests will check the functions in cmr.transmit.metadata-db. The client-id tests will not
   check the functionality of the function called, but instead will check that the request includes
   a client id."
  (:require
   [clojure.test :refer [deftest is testing]]
   [clj-http.client :as client]
   [cmr.transmit.indexer :as indexer]))

(deftest indexer-client-id-tests
  (testing "check for client id"
    (let [context {:system {:search-connection
                            {:protocol "http" :host "localhost" :port "3003" :context ""}}}
          action-tester (fn [arg]
                          (is (= "cmr-internal" (:client-id (:headers arg)))
                              (format "Failed testing %s" (:url arg)))
                          {:status 200 :body "" :headers {"CMR-Hits" "42"}})]

      (with-redefs [client/request action-tester]
        (let [result (indexer/get-index-set context :id)]
          (is (nil? result)))))))
