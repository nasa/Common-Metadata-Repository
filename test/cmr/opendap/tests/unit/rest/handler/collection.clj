(ns cmr.opendap.tests.unit.rest.handler.collection
  "Note: this namespace is exclusively for unit tests."
  (:require
   [clojure.test :refer :all]
   [cmr.opendap.rest.handler.collection :as collection]
   [cmr.opendap.testing.util :as util]))

(deftest generate-via-get
  (let [pvt-func #'collection/generate-via-get
        concept-id :C123-PROV]
    (is (= {:collection-id "C123-PROV"
            :format nil
            :granules nil
            :exclude-granules nil
            :variables nil
            :subset nil
            :bounding-box nil}
           (util/parse-response
            (pvt-func {:params {}} concept-id))))
    (is (= {:collection-id "C123-PROV"
            :format nil
            :granules nil
            :exclude-granules nil
            :variables ["V1-PROV" "V2-PROV"]
            :subset ["lat(56.109375,67.640625)"
                     "lon(-9.984375,19.828125)"]
            :bounding-box nil}
           (util/parse-response
            (pvt-func {:params
                       {:variables ["V1-PROV" "V2-PROV"]
                        :subset ["lat(56.109375,67.640625)"
                                 "lon(-9.984375,19.828125)"]}}
                      concept-id))))))
