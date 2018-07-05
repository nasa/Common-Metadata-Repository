(ns ^:system cmr.opendap.tests.system.app.ous.core
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.http.request :as request]
    [cmr.opendap.testing.system :as test-system]
    [cmr.opendap.testing.util :as util]
    [org.httpkit.client :as httpc]
    [ring.util.codec :as codec]))

(use-fixtures :once test-system/with-system)

(deftest processing-level-1-and-2-no-spatial)

(deftest processing-level-1-and-2-with-spatial)

(deftest processing-level-3+-no-spatial
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"]
    (testing "GET with bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       (request/add-token-header {} (util/get-sit-token)))]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
               (util/parse-response response)))))
    (testing "GET with subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?coverage=%s"
                                    "&rangesubset=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       (request/add-token-header {} (util/get-sit-token)))]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
               (util/parse-response response)))))))

(deftest processing-level-3+-with-spatial
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"]
    (testing "GET with bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s"
                                    "&bounding-box="
                                    "-9.984375,56.109375,19.828125,67.640625")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       (request/add-token-header {} (util/get-sit-token)))]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A[0:1:23][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
               (util/parse-response response)))))
    (testing "GET with subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?coverage=%s"
                                    "&rangesubset=%s"
                                    "&subset=lat(56.109375,67.640625)"
                                    "&subset=lon(-9.984375,19.828125)")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       (request/add-token-header {} (util/get-sit-token)))]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A[0:1:23][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
               (util/parse-response response)))))))

(deftest processing-level-not-provided-no-spatial)

(deftest processing-level-not-provided-with-spatial)
