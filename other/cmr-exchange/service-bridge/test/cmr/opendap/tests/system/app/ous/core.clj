(ns ^:system cmr.opendap.tests.system.app.ous.core
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

(deftest regex-from-tags
  (let [collection-id "C1000000141-DEMO_PROV"
        granule-id "G1000003455-DEMO_PROV"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?granules=%s")
                           (test-system/http-port)
                           collection-id
                           granule-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= ["http://e4ftl01.cr.usgs.gov:40510/dir-replaced-by-tags/ASTT/AST_L1T.003/2001.11.29/AST_L1T_00311292001175440_20150303161825_63101.hdf.nc"]
           (util/parse-response response)))))

(deftest no-tag-replacement-uses-opendap-url
  (let [collection-id "C1200241219-DEMO_PROV"
        granule-id "G1200241220-DEMO_PROV"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?granules=%s")
                           (test-system/http-port)
                           collection-id
                           granule-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= ["https://opendap.cr.usgs.gov/opendap/hyrax/DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf.nc"]
           (util/parse-response response)))))

(deftest no-tag-replacement-uses-opendap-url-with-html
  (let [collection-id "C1200354716-DEMO_PROV"
        granule-id "G1200354717-DEMO_PROV"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?granules=%s")
                           (test-system/http-port)
                           collection-id
                           granule-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= ["https://podaac-opendap.jpl.nasa.gov/opendap/allData/ghrsst/data/GDS2/L3U/GMI/REMSS/v8.2a/2018/331/20181127000000-REMSS-L3U_GHRSST-SSTsubskin-GMI-f35_20181127v8.2-v02.0-fv01.0.nc.nc"]
           (util/parse-response response)))))

(deftest no-tag-replacement-and-no-opendap-url
  (let [collection-id "C1200237759-DEMO_PROV"
        granule-id "G1200303332-DEMO_PROV"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?granules=%s")
                           (test-system/http-port)
                           collection-id
                           granule-id)
                   options)]
    (is (= 400 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= {:errors ["There was a problem extracting an OPeNDAP URL or data URL from the granule's metadata file."
                    (format "Problematic granules: [%s]." granule-id)]}
           (util/parse-response response)))))

(deftest no-opendap-url-or-data-url
  (let [collection-id "C1200237759-DEMO_PROV"
        granule-id "G1200303332-DEMO_PROV"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?granules=%s")
                           (test-system/http-port)
                           collection-id
                           granule-id)
                   options)]
    (is (= 400 (:status response)))
    (is (= "cmr-service-bridge.v2.1; format=json"
           (get-in response [:headers :cmr-media-type])))
    (is (= {:errors ["There was a problem extracting an OPeNDAP URL or data URL from the granule's metadata file." "Problematic granules: [G1200303332-DEMO_PROV]."]}
           (util/parse-response response)))))

(deftest gridded-with-ummvar-1-1-api-v2-1
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))]
    (testing "GET without bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
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
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
               (util/parse-response response)))))
    (testing "GET without subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
                                    "?coverage=%s"
                                    "&rangesubset=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
               (util/parse-response response)))))))

(deftest gridded-with-ummvar-1-1-api-v2-1-bounds
  (let [collection-id "C1200276834-HMR_TME"
        granule-id "G1200276835-HMR_TME"
        variable-id "V1200276840-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))]
    (testing "GET with bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
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
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/DEMO/MUR-JPL-L4-GLOB-v4_1.001/2018.05.23/20180523090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc.nc?dt_1km_data_VarBounds[0:1:0][14610:1:15764][17001:1:19983],lat[14610:1:15764],lon[17001:1:19983]"]
               (util/parse-response response)))))
    (testing "GET with subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
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
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/DEMO/MUR-JPL-L4-GLOB-v4_1.001/2018.05.23/20180523090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc.nc?dt_1km_data_VarBounds[0:1:0][14610:1:15764][17001:1:19983],lat[14610:1:15764],lon[17001:1:19983]"]
               (util/parse-response response)))))))

(deftest gridded-with-ummvar-1-1-api-v2-1-bounds-reversed
  (let [collection-id "C1200276782-HMR_TME"
        granule-id "G1200276783-HMR_TME"
        variable-id "V1200276788-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))]
    (testing "GET with bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
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
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/MOAA/MOD08_D3.006/2012.01.02/MOD08_D3.A2012002.006.2015056234420.hdf.nc?Solar_Zenith_Mean[22:1:34][169:1:200],YDim[22:1:34],XDim[169:1:200]"]
               (util/parse-response response)))))
    (testing "GET with subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
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
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/MOAA/MOD08_D3.006/2012.01.02/MOD08_D3.A2012002.006.2015056234420.hdf.nc?Solar_Zenith_Mean[22:1:34][169:1:200],YDim[22:1:34],XDim[169:1:200]"]
               (util/parse-response response)))))))

(deftest gridded-with-ummvar-1-2-api-v2-1
  (let [collection-id "C1200276794-HMR_TME"
        granule-id "G1200276796-HMR_TME"
        variable-id "V1200276801-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token))
                    (util/override-api-version-header "v2.1"))]
    (testing "GET without bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
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
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?SurfPres_Forecast_A_VarBounds,Latitude,Longitude"]
               (util/parse-response response)))))
    (testing "GET without subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
                                    "?coverage=%s"
                                    "&rangesubset=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 200 (:status response)))
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?SurfPres_Forecast_A_VarBounds,Latitude,Longitude"]
               (util/parse-response response)))))
    (testing "GET with bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
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
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
               (util/parse-response response)))))
    (testing "GET with subset ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/service-bridge/ous/collection/%s"
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
        (is (= "cmr-service-bridge.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
               (util/parse-response response)))))))
