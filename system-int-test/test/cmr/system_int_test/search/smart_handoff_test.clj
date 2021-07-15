(ns cmr.system-int-test.search.smart-handoff-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.utils.search-util :as search]))

(deftest retrieve-smart-handoff-schemas
  (testing "successful retrival of smart handoff schemas"
    (are3
      [client]
      (let [expected-schema (->> client
                                 name
                                 (format "smart-handoff/%s-schema.json")
                                 io/resource
                                 slurp)]
        (is (= {:status 200
                :body expected-schema}
               (search/get-smart-handoff-schema client))))

      "retrieve smart handoff schema for SOTO"
      :soto

      "retrieve smart handoff schema for Giovanni"
      :giovanni

      "retrieve smart handoff schema for EDSC"
      :edsc))

  (testing "retrieval of undefined schema returns 404 error"
    (is (= 404
           (:status (search/get-smart-handoff-schema :foo))))))
