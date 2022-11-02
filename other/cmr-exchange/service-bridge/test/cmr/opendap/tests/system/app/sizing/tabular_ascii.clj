(ns ^:system cmr.opendap.tests.system.app.sizing.tabular-ascii
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.http.kit.request :as request]
    [cmr.opendap.testing.system :as test-system]
    [cmr.opendap.testing.util :as util]
    [org.httpkit.client :as httpc]))

(use-fixtures :once test-system/with-system)

(def collection-id "C1200297231-HMR_TME")
(def granule-id "G1200297234-HMR_TME")
(def variable-id "V1200297235-HMR_TME")
(def variable-alias "/Data_5HZ/Geolocation/d_lat")
(def options (request/add-token-header {} (util/get-sit-token)))

(deftest one-var-size-opendap-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=tabular_ascii"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (string/includes? (:body response) "Cannot estimate size for service type: [opendap] and format: [tabular_ascii]"))))

(deftest one-var-size-egi-test 
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variable_aliases=%s"
                                "&format=tabular_ascii"
                                "&service_id=S1200341767-DEMO_PROV"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-alias)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 56 
             :mb 0.0
             :gb 0.0}]
           (util/parse-response response)))))
