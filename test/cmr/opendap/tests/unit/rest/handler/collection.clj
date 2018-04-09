(ns cmr.opendap.tests.unit.rest.handler.collection
  "Note: this namespace is exclusively for unit tests."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.opendap.rest.handler.collection :as collection]))

(defn parse-response
  [response]
  (json/parse-string (:body response) true))

(deftest generate-via-get
  (let [pvt-func #'collection/generate-via-get
        concept-id :C123-PROV]
    (is (= {:collection-id "C123-PROV"
            :format nil
            :granules nil
            :exclude-granules? nil
            :variables nil
            :subset nil
            :bounding-box nil}
           (parse-response (pvt-func {:query-params {}}
                                     concept-id))))
    (is (= {:collection-id "C123-PROV"
            :format nil
            :granules nil
            :exclude-granules? nil
            :variables ["V1-PROV" "V2-PROV"]
            :subset ["lat(22,34)" "lon(169,200)"]
            :bounding-box nil}
           (parse-response (pvt-func {:query-params
                                      {:variables ["V1-PROV" "V2-PROV"]
                                       :subset ["lat(22,34)" "lon(169,200)"]}}
                                     concept-id))))))
