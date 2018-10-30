(ns ^:system cmr.opendap.tests.system.app.sizing.core
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clojure.test :refer :all]
    [cmr.http.kit.request :as request]
    [cmr.opendap.testing.system :as test-system]
    [cmr.opendap.testing.util :as util]
    [org.httpkit.client :as httpc]
    [ring.util.codec :as codec]))

(use-fixtures :once test-system/with-system)

(deftest dods-size-with-no-sizing-metadata
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token)))]
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=dods")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 6220800
                 :gb 0.005793571472167969
                 :mb 5.9326171875}]
               (util/parse-response response))))))

(deftest netcdf3-size-with-no-sizing-metadata
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token)))]
    (testing "Explicit format ... "
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=nc")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 6231034
                 :mb 5.942377090454102
                 :gb 0.0058031026273965836}]
               (util/parse-response response)))))
    (testing "Implicit format ... "
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 6231034
                 :mb 5.942377090454102
                 :gb 0.0058031026273965836}]
               (util/parse-response response)))))))

(deftest dods-size
  (let [collection-id "C1200297231-HMR_TME"
        granule-id "G1200297234-HMR_TME"
        variable-id "V1200297235-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token)))]
    (testing "One variable ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=dods")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 1
                 :gb 9.313225746154785E-10
                 :mb 9.5367431640625E-7}]
               (util/parse-response response)))))
    (testing "One variable (from a different granule) ..."
      (let [granule-id "G1200301322-HMR_TME"
            variable-id "V1200301323-HMR_TME"
            response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=dods")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 11520000
                 :gb 0.010728836059570312
                 :mb 10.986328125}]
               (util/parse-response response)))))
    (testing "Multiple variables ..."
      (let [variable2-id "V1200297236-HMR_TME"
            response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s,%s"
                                    "&format=dods")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id
                               variable2-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 5760001
                 :gb 0.005364418961107731
                 :mb 5.493165016174316}]
               (util/parse-response response)))))
    (testing "Multiple variables (from a different granule) ..."
      (let [granule-id "G1200301322-HMR_TME"
            variable1-id "V1200301323-HMR_TME"
            variable2-id "V1200301324-HMR_TME"
            response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s,%s"
                                    "&format=dods")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable1-id
                               variable2-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 17280000
                 :gb 0.01609325408935547
                 :mb 16.4794921875}]
               (util/parse-response response)))))
    ;; XXX Currently not working; see CMR-5268
    #_(testing "Mutlple granules, multiple variables ..."
      (let [variable1-id "V1200297236-HMR_TME"
            granule2-id "G1200301322-HMR_TME"
            variable2-id "V1200301324-HMR_TME"
            response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s,%s"
                                    "&variables=%s"
                                    "&format=dods")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               granule2-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 8158
                 :gb 7.597729563713074E-6
                 :mb 0.0077800750732421875}]
               (util/parse-response response)))))))

(deftest netcdf3-size
  (let [collection-id "C1200297231-HMR_TME"
        granule-id "G1200297234-HMR_TME"
        variable-id "V1200297235-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token)))]
    (testing "One variable ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=nc")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 8158
                 :gb 7.597729563713074E-6
                 :mb 0.0077800750732421875}]
               (util/parse-response response)))))
    (testing "One variable (from a different granule) ..."
      (let [granule-id "G1200301322-HMR_TME"
            variable-id "V1200301323-HMR_TME"
            response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&format=nc")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 11527615
                 :gb 0.01073592808097601
                 :mb 10.993590354919434}]
               (util/parse-response response)))))
    (testing "Multiple variables ..."
      (let [variable2-id "V1200297236-HMR_TME"
            response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s,%s"
                                    "&format=nc")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id
                               variable2-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 5768158
                 :gb 0.005372015759348869
                 :mb 5.500944137573242}]
               (util/parse-response response)))))
    (testing "Multiple variables (from a different granule) ..."
      (let [granule-id "G1200301322-HMR_TME"
            variable1-id "V1200301323-HMR_TME"
            variable2-id "V1200301324-HMR_TME"
            response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s,%s"
                                    "&format=nc")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable1-id
                               variable2-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 17287615
                 :gb 0.016100346110761166
                 :mb 16.486754417419434}]
               (util/parse-response response)))))
    ;; XXX Currently not working; see CMR-5268
    #_(testing "Mutlple granules, multiple variables ..."
      (let [variable1-id "V1200297236-HMR_TME"
            granule2-id "G1200301322-HMR_TME"
            variable2-id "V1200301324-HMR_TME"
            response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/size-estimate/collection/%s"
                                    "?granules=%s,%s"
                                    "&variables=%s"
                                    "&format=nc")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               granule2-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= [{:bytes 8158
                 :gb 7.597729563713074E-6
                 :mb 0.0077800750732421875}]
               (util/parse-response response)))))))
