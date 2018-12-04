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
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 45
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
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 139324
             :mb 0.13
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
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 438984930
             :mb 418.65
             :gb 0.41}]
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
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 183178
             :mb 0.17
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
      (is (= "cmr-service-bridge.v2.1; format=json"
             (get-in response [:headers :cmr-media-type])))
      (is (= [{:bytes 69684
               :mb 0.07
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
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 43855
             :mb 0.04
             :gb 0.0}]
           (util/parse-response response)))))
