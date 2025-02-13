(ns cmr.transmit.test.metadata-db2-test
  "These tests will check the functions in cmr.transmit.metadata-db2."
  (:require
    [cheshire.core :as json]
    [clojure.test :refer [deftest is]]
    [clj-http.client :as client]
    [cmr.transmit.metadata-db2 :as mdb2])
  )

(deftest get-subscription-cache-content
  (let [context {:system :metadata-db}
        coll-concept-id "C123-PROV1"
        response {:status 200
                  :headers {},
                  :body (json/encode {:Mode {:New ["url1"]
                                             :Update ["url2"]}})
                  :request-time 6,
                  :trace-redirects []}
        expected-content-cache {:Mode {:New ["url1"]
                                       :Update ["url2"]}}]
    ;; successful response returns the json decoded body
    (with-redefs [client/get (fn [request-url params] response)]
      (is (= expected-content-cache (mdb2/get-subscription-cache-content context coll-concept-id))))
    ;; unsuccessful response throws an error
    (with-redefs [client/get (fn [request-url params] {:status 500})]
      (is (thrown? Exception (mdb2/get-subscription-cache-content context coll-concept-id))))
    ))