(ns cmr.search.test.unit.api.semantic-search-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.test :refer :all]
   [cmr.search.api.semantic-search :as semantic]
   [cmr.search.config :as config]))

(deftest proxy-request-test
  (testing "valid parameters are forwarded and a valid response passes through"
    (with-redefs [config/semantic-search-url (constantly "http://semantic")
                  config/semantic-search-timeout-ms (constantly 25)
                  http/get (fn [url options]
                             (is (= "http://semantic/semantic-collections" url))
                             (is (= {:q "ocean" :mode "hybrid"} (:query-params options)))
                             {:status 200 :body (json/generate-string {:entries []})})]
      (is (= 200 (:status (semantic/proxy-request {:q "ocean" :mode "hybrid"}))))))
  (testing "invalid requests do not call the dependency"
    (with-redefs [http/get (fn [& _] (throw (AssertionError. "unexpected request")))]
      (is (= 400 (:status (semantic/proxy-request {:q " "}))))
      (is (= 400 (:status (semantic/proxy-request {:q "ok" :page_size "21"}))))))
  (testing "dependency and malformed responses become bad gateway"
    (with-redefs [config/semantic-search-url (constantly "http://semantic")
                  config/semantic-search-timeout-ms (constantly 25)
                  http/get (fn [& _] {:status 200 :body "not-json"})]
      (is (= 502 (:status (semantic/proxy-request {:q "ocean"})))))
    (with-redefs [config/semantic-search-url (constantly "http://semantic")
                  config/semantic-search-timeout-ms (constantly 25)
                  http/get (fn [& _] (throw (java.net.SocketTimeoutException.)))]
      (is (= 502 (:status (semantic/proxy-request {:q "ocean"})))))))
