(ns cmr.opendap.tests.unit.rest.handler.collection
  "Note: this namespace is exclusively for unit tests."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.opendap.rest.handler.collection :as collection]
   [cmr.opendap.testing.util :as util]))

;; XXX These now need to be re-worked ... and moved, since the latest code
;; changes mean they're not simple unit tests anymore (they make multiple calls
;; to CMR Search, so they're integration or system tests).

; (deftest generate-via-get
;   (let [pvt-func #'collection/generate-via-get
;         concept-id :C123-PROV]
;     (is (= {:collection-id "C123-PROV"
;             :format "nc"
;             :granules []
;             :exclude-granules false
;             :variables []
;             :subset nil
;             :bounding-box nil}
;            (util/parse-response
;             (pvt-func {:params {}} "/search" "token" concept-id))))
;     (is (= {:collection-id "C123-PROV"
;             :format "nc"
;             :granules []
;             :exclude-granules false
;             :variables ["V1-PROV" "V2-PROV"]
;             :subset ["lat(56.109375,67.640625)"
;                      "lon(-9.984375,19.828125)"]
;             :bounding-box nil}
;            (util/parse-response
;             (pvt-func {:params
;                        {:variables "V1-PROV,V2-PROV"
;                         :subset ["lat(56.109375,67.640625)"
;                                  "lon(-9.984375,19.828125)"]}}
;                       "/search"
;                       "token"
;                       concept-id))))))

; (deftest generate-via-post
;   (let [pvt-func #'collection/generate-via-post
;         concept-id :C123-PROV]
;     (is (= {:collection-id "C123-PROV"
;             :format "nc"
;             :granules []
;             :exclude-granules false
;             :variables []
;             :subset nil
;             :bounding-box nil}
;            (util/parse-response
;             (pvt-func (util/create-json-stream-payload {})
;                       "/search"
;                       "token"
;                       concept-id))))
;     (is (= {:collection-id "C123-PROV"
;             :format "nc"
;             :granules []
;             :exclude-granules false
;             :variables ["V1-PROV" "V2-PROV"]
;             :subset ["lat(56.109375,67.640625)"
;                      "lon(-9.984375,19.828125)"]
;             :bounding-box nil}
;            (util/parse-response
;             (pvt-func (util/create-json-stream-payload
;                        {:variables "V1-PROV,V2-PROV"
;                         :subset ["lat(56.109375,67.640625)"
;                                  "lon(-9.984375,19.828125)"]})
;                       "/search"
;                       "token"
;                       concept-id))))))
