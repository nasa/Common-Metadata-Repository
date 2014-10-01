(ns cmr.system-int-test.index-set.health-test
  "This tests the index-set health api."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.system-int-test.utils.url-helper :as url]))


(deftest index-set-health-test
  (testing "good health"
    (let [response (client/get (url/index-set-health-url)
                               {:accept :json
                                :connection-manager (url/conn-mgr)})
          health (json/decode (:body response) true)
          {:keys [elastic_search echo-rest]} health]
      (is (= 200 (:status response)))
      (is (some #{elastic_search} ["green" "yellow"]))
      (is (= "ok" echo-rest)))))
