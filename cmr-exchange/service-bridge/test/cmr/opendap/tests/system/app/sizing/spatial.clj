(ns ^:system cmr.opendap.tests.system.app.sizing.spatial
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
(def options (request/add-token-header {} (util/get-sit-token)))

(deftest sinusoidal-grid-size-test
  (let [collection-id "C1200297231-HMR_TME"
        granule-id "G1200297234-HMR_TME"
        variable-id "V1200297235-HMR_TME"]
    (testing "Bounding box smaller than tiling size"
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=ascii"
                                    "&bounding-box=140,50,141,51")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v3; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 1
                 :mb 0.0
                 :gb 0.0}]
               (util/parse-response response)))))

    (testing "Bounding box larger than tiling size"
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=ascii"
                                    "&bounding-box=140,40,170,70")
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

    (testing "Bounding box equal to tiling size"
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=ascii"
                                    "&bounding-box=140,50,150,60")
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
               (util/parse-response response)))))))

(deftest ease-grid-size-test
  (let [collection-id "C1200303115-HMR_TME"
        granule-id "G1200303116-HMR_TME"
        variable-id "V1200297235-HMR_TME"]
    (testing "Bounding box smaller than tiling size"
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=ascii"
                                    "&bounding-box=140,50,141,51")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v3; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 1
                 :mb 0.0
                 :gb 0.0}]
               (util/parse-response response)))))

    (testing "Bounding box larger than tiling size"
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=ascii"
                                    "&bounding-box=140,40,170,70")
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

    (testing "Bounding box equal to tiling size"
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=ascii"
                                    "&bounding-box=140,50,150,60")
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
               (util/parse-response response)))))))

(deftest no-tiling-size-test
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"]
    (let [response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/size-estimate/collection/%s"
                                  "?granules=%s"
                                  "&variables=%s"
                                  "&format=ascii"
                                  "&bounding-box=140,50,141,51")
                             (test-system/http-port)
                             collection-id
                             granule-id
                             variable-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= "cmr-service-bridge.v3; format=json"
             (get-in response [:headers :cmr-media-type])))
      (is (= [{:bytes 120
               :mb 0.0
               :gb 0.0}]
             (util/parse-response response))))))
