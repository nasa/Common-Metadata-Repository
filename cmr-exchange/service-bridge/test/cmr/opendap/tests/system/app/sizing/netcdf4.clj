(ns ^:system cmr.opendap.tests.system.app.sizing.netcdf4
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clojure.test :refer :all]
    [cmr.http.kit.request :as request]
    [cmr.opendap.testing.system :as test-system]
    [cmr.opendap.testing.util :as util]
    [org.httpkit.client :as httpc]))

(use-fixtures :once test-system/with-system)

(def collection-id "C1200297231-HMR_TME")
(def granule-id "G1200297234-HMR_TME")
(def variable-id "V1200297235-HMR_TME")
(def granule2-id "G1200301322-HMR_TME")
(def variable2-id "V1200301323-HMR_TME")
(def variable3-id "V1200297236-HMR_TME")
(def variable-alias "/Data_5HZ/Geolocation/d_lat")
(def variable2-alias "/Data_5HZ/Geolocation/test")
(def variable3-alias "/Data_5HZ/Geolocation/d_lon")
(def group-node-alias "/Data_5HZ/Geolocation")
(def options (request/add-token-header {} (util/get-sit-token)))

(deftest one-var-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 250 
             :mb 0.0
             :gb 0.0}]
           (util/parse-response response)))))

(deftest one-var-size-egi-test 
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=netcdf4-cf"
                                "&service_id=S1200341767-DEMO_PROV"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 250 
             :mb 0.0
             :gb 0.0}]
           (util/parse-response response)))))

(deftest one-var-size-egi-test-2
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=netcdf4"
                                "&service_id=S1200341767-DEMO_PROV"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 250 
             :mb 0.0
             :gb 0.0}]
           (util/parse-response response)))))

(deftest one-var-size-egi-test-3
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=netcdf-4"
                                "&service_id=S1200341767-DEMO_PROV"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 250 
             :mb 0.0
             :gb 0.0}]
           (util/parse-response response)))))

(deftest one-var-size-egi-test-4
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=nc4"
                                "&service_id=S1200341767-DEMO_PROV"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 250 
             :mb 0.0
             :gb 0.0}]
           (util/parse-response response)))))

(deftest one-var-different-gran-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule2-id
                           variable2-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 790000 
             :mb 0.75
             :gb 0.0}]
           (util/parse-response response)))))

(deftest multi-var-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s,%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=100000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id
                           variable3-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 13225000 
             :mb 12.61 
             :gb 0.01}]
           (util/parse-response response)))))

(deftest mix-alias-var-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&variable_aliases=%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=100000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id
                           variable3-alias)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 13225000 
             :mb 12.61 
             :gb 0.01}]
           (util/parse-response response)))))

(deftest mix-one-alias-multi-var-with-duplicate-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s,%s"
                                "&variable_aliases=%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=100000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id
                           variable3-id
                           variable3-alias)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 13225000 
             :mb 12.61 
             :gb 0.01}]
           (util/parse-response response)))))

(deftest mix-multi-alias-one-var-with-duplicate-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&variable_aliases=%s,%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=100000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id
                           variable-alias
                           variable3-alias)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 13225000 
             :mb 12.61 
             :gb 0.01}]
           (util/parse-response response)))))

(deftest mix-multi-alias-multi-var-with-duplicate-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s,%s"
                                "&variable_aliases=%s,%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=100000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id
                           variable3-id
                           variable-alias
                           variable3-alias)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 13225000 
             :mb 12.61 
             :gb 0.01}]
           (util/parse-response response)))))

(deftest multi-alias-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variable_aliases=%s,%s"
                                "&format=nc4"
                                "&service_id=S1200341768-DEMO_PROV"
                                "&total-granule-input-bytes=100000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-alias
                           variable3-alias)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 13225000 
             :mb 12.61 
             :gb 0.01}]
           (util/parse-response response)))))

(deftest multi-alias-size-test-2
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variable_aliases=%s,%s,%s"
                                "&format=nc4"
                                "&service_id=S1200341768-DEMO_PROV"
                                "&total-granule-input-bytes=100000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-alias
                           variable2-alias
                           variable3-alias)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 92225000 
             :mb 87.95 
             :gb 0.09}]
           (util/parse-response response)))))

(deftest group-node-alias-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variable_aliases=%s"
                                "&format=nc4"
                                "&service_id=S1200341768-DEMO_PROV"
                                "&total-granule-input-bytes=100000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           group-node-alias)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 92225000 
             :mb 87.95 
             :gb 0.09}]
           (util/parse-response response)))))

(deftest multi-var-different-gran-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s,%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule2-id
                           variable2-id
                           variable3-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 922000 
             :mb 0.88
             :gb 0.0}]
           (util/parse-response response)))))

(deftest multi-gran-multi-var-size-test
    (let [response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/size-estimate/collection/%s"
                                  "?granules=%s,%s"
                                  "&variables=%s,%s"
                                  "&format=nc4"
                                  "&total-granule-input-bytes=1000000")
                             (test-system/http-port)
                             collection-id
                             granule-id
                             granule2-id
                             variable-id
                             variable2-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= "cmr-service-bridge.v3; format=json"
             (get-in response [:headers :cmr-media-type])))
      (is (= [{:bytes 790250 
               :mb 0.75
               :gb 0.0}]
             (util/parse-response response)))))

(deftest size-with-no-sizing-metadata-test
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=nc4"
                                "&total-granule-input-bytes=1000000")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 132000 
             :mb 0.13
             :gb 0.0}]
           (util/parse-response response)))))
