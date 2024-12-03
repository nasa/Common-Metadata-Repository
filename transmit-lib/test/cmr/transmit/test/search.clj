(ns cmr.transmit.test.search
  "These tests will check the functions in cmr.transmit.search. The client-id tests will not check
   the functionality of the function called, but instead will check that the request includes a
   client id."
  (:require
   [clj-time.core :as time]
   [clojure.test :refer [deftest is testing]]
   [clj-http.client :as client]
   [cmr.transmit.search :as search]))

(deftest token-header-check
  (testing "pull out the token"
    (let [context {}
          result (search/token-header {})]
      (is (= {"authorization" "mock-echo-system-token"} result)))))

(deftest test-search-client-id
  ;; You can not mock client/put or other http verbs because those are macros, need to go after the
  ;; actual handler client/request
  (testing "check for client id"
    (let [context {:system {:metadata-db-connection
                            {:protocol "http" :host "localhost" :port "3001" :context ""}}}
          action-tester (fn [arg]
                          (is (= "cmr-internal" (:client-id (:headers arg)))
                              (format "Failed testing %s" (:url arg)))
                          {:status 204 :body ""})]
      (with-redefs [client/request action-tester]
        (let [result (search/save-subscription-notification-time context "sub-id-1" (str (time/now)))]
          (is (nil? result))))))

  (testing "check for client id"
    (let [context {:system {:search-connection
                            {:protocol "http" :host "localhost" :port "3003" :context ""}}}
          action-tester (fn [arg]
                          (is (= "cmr-internal" (:client-id (:headers arg)))
                              (format "Failed testing %s" (:url arg)))
                          {:status 200 :body "" :headers {"CMR-Hits" "42"}})]

      (with-redefs [client/request action-tester]
        (let [result (search/find-granule-hits context {})]
          (is (= 42 result))))))

  (testing "check for client id from find concept references"
    (let [context {:system {:search-connection
                            {:protocol "http" :host "localhost" :port "3003" :context ""}}}
          action-tester (fn [arg]
                          (is (= "cmr-internal" (:client-id (:headers arg)))
                              (format "Failed testing %s" (:url arg)))
                          {:status 400 :body "a bad xml document" :headers {"CMR-Hits" "42"}})]

      (with-redefs [client/request action-tester]
        (try
          ;; This call will throw an error which we don't care about, what we want is to trigger the
          ;; call to request which holds the actual test
          (let [result (search/find-concept-references context {} :collection)]
            (is (not (true? result))))
          (catch Exception e)))))

  (testing "check for client id from validate search params"
    (let [context {:system {:search-connection
                            {:protocol "http" :host "localhost" :port "3003" :context ""}}}
          action-tester (fn [arg]
                          (is (= "cmr-internal" (:client-id (:headers arg)))
                              (format "Failed testing %s" (:url arg)))
                          {:status 200 :body "" :headers {"CMR-Hits" "42"}})]

      (with-redefs [client/request action-tester]
        (let [result (search/validate-search-params context {} :collection)]
          (is (not (true? result)))))))

  (testing "check for client id in APIs created by the defsearcher macro"
    (let [context {:system {:search-connection
                            {:protocol "http" :host "localhost" :port "3003" :context ""
                             :conn-mgr :non-emtpy-value}}}
          action-tester (fn [arg]
                          (is (= "cmr-internal" (:client-id (:headers arg)))
                              (format "Failed testing %s" (:url arg)))
                          {:status 200 :body "" :headers {"CMR-Hits" "42"}})]

      (with-redefs [client/request action-tester]
        (let [result (search/search-for-collections context {})
              _ (search/search-for-variables context {})
              _ (search/search-for-services context {})
              _ (search/search-for-tools context {})
              _ (search/search-for-subscriptions context {})]
          ;; you need at least one test here to trigger the testing function.
          (is (= "" result) "search-for-collections response check"))))))
