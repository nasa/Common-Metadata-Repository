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
(def options (-> {}
                 (request/add-token-header (util/get-sit-token))))

(deftest one-var-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=ascii"
                                "&edsc-size-estimate=1")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 3.3354564705882355E11
             :mb 318093.9169491039
             :gb 310.63859077060926}]
           (util/parse-response response)))))

(deftest one-var-different-gran-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s"
                                "&format=ascii"
                                "&edsc-size-estimate=1")
                           (test-system/http-port)
                           collection-id
                           granule2-id
                           variable2-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 1232668.695652174
             :mb 1.175564475681471
             :gb 0.0011480121832826865}]
           (util/parse-response response)))))

(deftest multi-var-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s,%s"
                                "&format=ascii"
                                "&edsc-size-estimate=1")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id
                           variable3-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 3.3354564705882355E11
             :mb 318093.9169491039
             :gb 310.63859077060926}]
           (util/parse-response response)))))

(deftest multi-var-different-gran-size-test
  (let [response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/size-estimate/collection/%s"
                                "?granules=%s"
                                "&variables=%s,%s"
                                "&format=ascii"
                                "&edsc-size-estimate=1")
                           (test-system/http-port)
                           collection-id
                           granule2-id
                           variable2-id
                           variable3-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 1232668.695652174
             :mb 1.175564475681471
             :gb 0.0011480121832826865}]
           (util/parse-response response)))))

;; XXX Currently not working; see CMR-5268
#_(deftest multi-gran-multi-var-size-test
    (let [response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/size-estimate/collection/%s"
                                  "?granules=%s,%s"
                                  "&variables=%s"
                                  "&format=ascii"
                                  "&edsc-size-estimate=1")
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
      (is (= [{:bytes 8158
               :gb 7.597729563713074E-6
               :mb 0.0077800750732421875}]
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
                                "&format=ascii"
                                "&edsc-size-estimate=1")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= [{:bytes 1203984.0
             :mb 1.1482086181640625
             :gb 0.0011212974786758423}]
           (util/parse-response response)))))
