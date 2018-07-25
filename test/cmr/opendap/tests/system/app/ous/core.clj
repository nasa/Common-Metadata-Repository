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

(deftest gridded-with-ummvar-1-1-api-v2
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2"))]
    (testing "GET without bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
               (util/parse-response response)))))
    (testing "GET without subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?coverage=%s"
                                    "&rangesubset=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
               (util/parse-response response)))))
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
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2; format=json"
               (get-in response [:headers :cmr-media-type])))
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
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A[0:1:23][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
               (util/parse-response response)))))))

(deftest gridded-with-ummvar-1-1-api-v2-1
  ;; All of these ingested items are UMM-Var 1.1; since they are using v2.1 of
  ;; the REST API, Dimensions will be searched (and not found, since that field
  ;; is in UMM-Var 1.2), and will subsequently conclude that the data is not
  ;; gridded, stripping away the bounding info from all OPeNDAP queries.
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))]
    (testing "GET without bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
               (util/parse-response response)))))
    (testing "GET without subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?coverage=%s"
                                    "&rangesubset=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
               (util/parse-response response)))))
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
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
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
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
               (util/parse-response response)))))))

(deftest gridded-with-ummvar-1-2-api-v2-1
  (let [collection-id "C1200274974-HMR_TME"
        granule-id "G1200274976-HMR_TME"
        variable-id "V1200274983-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))]
    (testing "GET without bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct_VarDims,Latitude,Longitude"]
               (util/parse-response response)))))
    (testing "GET without subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/ous/collection/%s"
                                    "?coverage=%s"
                                    "&rangesubset=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct_VarDims,Latitude,Longitude"]
               (util/parse-response response)))))
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
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct_VarDims[0:1:23][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
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
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct_VarDims[0:1:23][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
               (util/parse-response response)))))))
