(ns ^:system cmr.opendap.tests.system.app.sizing.ascii
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
                                "&format=ascii")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 2 
             :mb 0.0
             :gb 0.0}]
           (util/parse-response response)))))

(deftest one-var-different-gran-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=ascii")
                           (test-system/http-port)
                           collection-id
                           granule2-id
                           variable2-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 23039998 
             :mb 21.97 
             :gb 0.02}]
           (util/parse-response response)))))

(deftest multi-var-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s,%s"
                                "&format=ascii")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id
                           variable3-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 25920000 
             :mb 24.72 
             :gb 0.02}]
           (util/parse-response response)))))

(deftest multi-var-different-gran-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s,%s"
                                "&format=ascii")
                           (test-system/http-port)
                           collection-id
                           granule2-id
                           variable2-id
                           variable3-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 48959996 
             :mb 46.69 
             :gb 0.05}]
           (util/parse-response response)))))

;; XXX Currently not working; see CMR-5268
(deftest multi-gran-multi-var-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s,%s"
                                "&variables=%s,%s"
                                "&format=ascii")
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
    (is (= [{:bytes 46080000 
             :mb 43.95 
             :gb 0.04}]
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
                                "&format=ascii")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v3; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 7775998 
             :mb 7.42 
             :gb 0.01}]
           (util/parse-response response)))))
