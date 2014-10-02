(ns cmr.system-int-test.index-set.health-test
  "This tests the index-set health api."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn get-health
  "Temporary for testing"
  []
  (let [response (client/get (url/index-set-health-url)
                               {:accept :json
                                :throw-exceptions false
                                :connection-manager (url/conn-mgr)})
          status-code (:status response)
          health-detail (json/decode (:body response) true)]

      {:status status-code
       :detail health-detail}))


(deftest index-set-health-test

  (println (pr-str (get-health)))
  (Thread/sleep 2000)
  (println (pr-str (get-health)))
  (Thread/sleep 2000)
  (println (pr-str (get-health)))
  (Thread/sleep 2000)

  (testing "good health"
    (let [response (client/get (url/index-set-health-url)
                               {:accept :json
                                :throw-exceptions false
                                :connection-manager (url/conn-mgr)})
          status-code (:status response)
          health-detail (json/decode (:body response) true)]

      (is (= [200 {:elastic_search {:ok? true}, :echo {:ok? true}}]
             [status-code health-detail])))))
