(ns ^:system cmr.opendap.tests.system.app.ous.params.wcs-v3
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clojure.test :refer :all]
    [cmr.http.kit.request :as request]
    [cmr.opendap.testing.system :as test-system]
    [cmr.opendap.testing.util :as util]
    [org.httpkit.client :as httpc])
  (:import
    (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(def options (-> {}
                 (request/add-token-header (util/get-sit-token))
                 (util/override-api-version-header "v3")))

(deftest collection-GET-query-coverage
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?coverage=G1200267320-HMR_TME,G1200267319-HMR_TME,"
                                collection-id)
                           (test-system/http-port)
                           collection-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.dap.nc4"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4"]
           (util/parse-response response)))))


(deftest collection-GET-query-coverage-rangesubset
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?coverage=G1200267320-HMR_TME,G1200267319-HMR_TME,"
                                collection-id
                                "&rangesubset=V1200267322-HMR_TME,V1200267323-HMR_TME")
                           (test-system/http-port)
                           collection-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.dap.nc4?dap4.ce=/CH4_VMR_A;/CH4_VMR_A_ct;/Latitude;/Longitude"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4?dap4.ce=/CH4_VMR_A;/CH4_VMR_A_ct;/Latitude;/Longitude"]
           (util/parse-response response)))))

(deftest collection-GET-query-coverage-subset
  (let [collection-id "C1200276794-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?coverage=G1200276795-HMR_TME,G1200276796-HMR_TME,"
                                collection-id
                                "&subset=lat(56.109375,67.640625)"
                                "&subset=lon(-9.984375,19.828125)")
                           (test-system/http-port)
                           collection-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.01.01/AIRS.2016.01.01.L3.RetStd001.v6.0.31.0.G16004140142.hdf.dap.nc4?dap4.ce=/SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200];/SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200];/Latitude[22:1:34];/Longitude[169:1:200]"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4?dap4.ce=/SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200];/SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200];/Latitude[22:1:34];/Longitude[169:1:200]"]
           (util/parse-response response)))))

(deftest collection-GET-query-coverage-invalid-subset
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?coverage=G1200267320-HMR_TME,G1200267319-HMR_TME,"
                                collection-id
                                "&subset=lat(-91,67.640625)"
                                "&subset=lon(-181,19.828125)")
                           (test-system/http-port)
                           collection-id)
                   options)]
    (is (= 400 (:status response)))
    (is (= {:errors ["The values provided for latitude are not within the valid range of -90 degrees through 90 degrees."
                     "The values provided for longitude are not within the valid range of -180 degrees through 180 degrees."
                     "West must be within [-180.0] and [180.0] but was [-181.0]."
                     "South must be within [-90.0] and [90.0] but was [-91.0]."
                     "There was a problem extracting an OPeNDAP URL or data URL from the granule's metadata file."]}
           (util/parse-response response)))))

(deftest collection-GET-query-timeposition
  (testing "A timespan that should not include any granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?timeposition=2000-01-01T00:00:00Z"
                                               ",2000-01-02T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= []
             (util/parse-response response)))))
  (testing "A timespan that should include one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?timeposition=2016-07-01T00:00:00Z"
                                               ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4"]
             (util/parse-response response)))))
  (testing "A timespan that should include two granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?timeposition=2002-09-01T00:00:00Z"
                                               ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.dap.nc4"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4"]
             (util/parse-response response)))))
  (testing "Multiple timespans ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?timeposition=2000-01-01T00:00:00Z"
                                               ",2002-10-01T00:00:00Z"
                                  "&timeposition=2010-07-01T00:00:00Z"
                                               ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.dap.nc4"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4"]
             (util/parse-response response))))))

(deftest collection-GET-query-coverage-rangesubset-hires
  (let [collection-id "C1200268967-HMR_TME"
        granule-id "G1200268968-HMR_TME"
        variable-ids "V1200268970-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?coverage=%s"
                                "&rangesubset=%s")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-ids)
                   options)]
    (is (= 200 (:status response)))
    (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/DEMO/MUR-JPL-L4-GLOB-v4_1.001/2018.05.23/20180523090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc.dap.nc4?dap4.ce=/analysed_sst;/lat;/lon"]
           (util/parse-response response)))))

(deftest collection-GET-query-coverage-subset-hires
  (let [collection-id "C1200268967-HMR_TME"
        granule-id "G1200268968-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?coverage=%s"
                                "&subset=lat(41.625,80.71875)"
                                "&subset=lon(-73.6875,-65.8125)")
                           (test-system/http-port)
                           collection-id
                           granule-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/DEMO/MUR-JPL-L4-GLOB-v4_1.001/2018.05.23/20180523090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc.dap.nc4?dap4.ce=/analysed_sst[0:1:0][13161:1:17071][10630:1:11419];/analysis_error[0:1:0][13161:1:17071][10630:1:11419];/mask[0:1:0][13161:1:17071][10630:1:11419];/sea_ice_fraction[0:1:0][13161:1:17071][10630:1:11419];/lat[13161:1:17071];/lon[10630:1:11419]"]
           (util/parse-response response)))))

(deftest collection-GET-query-coverage-subset-rangesubset-hires
  (let [collection-id "C1200268967-HMR_TME"
        granule-id "G1200268968-HMR_TME"
        variable-ids "V1200268970-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s"
                                "?coverage=%s"
                                "&subset=lat(41.625,80.71875)"
                                "&subset=lon(-73.6875,-65.8125)"
                                "&rangesubset=%s")
                           (test-system/http-port)
                           collection-id
                           granule-id
                           variable-ids)
                   options)]
    (is (= 200 (:status response)))
    (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/DEMO/MUR-JPL-L4-GLOB-v4_1.001/2018.05.23/20180523090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc.dap.nc4?dap4.ce=/analysed_sst[0:1:0][13161:1:17071][10630:1:11419];/lat[13161:1:17071];/lon[10630:1:11419]"]
           (util/parse-response response)))))

(deftest dap-version-parameter-test
  (let [collection-id "C1200267318-HMR_TME"
        base-url (format (str "http://localhost:%s"
                              "/service-bridge/ous/collection/%s"
                              "?coverage=G1200267320-HMR_TME,G1200267319-HMR_TME,"
                              collection-id
                              "&rangesubset=V1200267322-HMR_TME,V1200267323-HMR_TME")
                         (test-system/http-port)
                         collection-id)]
    (testing "valid dap-version"
      (let [dap4-response @(httpc/get (str base-url "&dap-version=4") options)
            dap2-response @(httpc/get (str base-url "&dap-version=2") options)]
        (is (= 200 (:status dap4-response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.dap.nc4?dap4.ce=/CH4_VMR_A;/CH4_VMR_A_ct;/Latitude;/Longitude"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4?dap4.ce=/CH4_VMR_A;/CH4_VMR_A_ct;/Latitude;/Longitude"]
               (util/parse-response dap4-response)))

        (is (= 200 (:status dap2-response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"]
               (util/parse-response dap2-response)))))

    (testing "valid dap-version, with default API version"
      (let [dap4-response @(httpc/get (str base-url "&dap-version=4") (request/add-token-header {} (util/get-sit-token)))
            dap2-response @(httpc/get (str base-url "&dap-version=2") (request/add-token-header {} (util/get-sit-token)))]
        (is (= 200 (:status dap4-response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.dap.nc4?dap4.ce=/CH4_VMR_A;/CH4_VMR_A_ct;/Latitude;/Longitude"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4?dap4.ce=/CH4_VMR_A;/CH4_VMR_A_ct;/Latitude;/Longitude"]
               (util/parse-response dap4-response)))

        (is (= 200 (:status dap2-response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"]
               (util/parse-response dap2-response)))))

    (testing "invalid dap-version"
      (let [response @(httpc/get (str base-url "&dap-version=3") options)]
        (is (= 400 (:status response)))
        (is (= {:errors ["Parameter dap-version can only be either 2 or 4, but was 3."]}
               (util/parse-response response)))))

    (testing "invalid dap-version, with default API version"
      (let [response @(httpc/get (str base-url "&dap-version=3") (request/add-token-header {} (util/get-sit-token)))]
        (is (= 400 (:status response)))
        (is (= {:errors ["Parameter dap-version can only be either 2 or 4, but was 3."]}
               (util/parse-response response)))))))

(deftest collection-paging
  (let [collection-id "C1200442179-HMR_TME"
        base-url (format "http://localhost:%s/service-bridge/ous/collection/%s?timeposition=2001-07-01T00:00:00Z,2016-07-03T00:00:00Z"
                         (test-system/http-port)
                         collection-id)]
    (testing "default paging"
      (let [response @(httpc/get base-url options)]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020621.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020622.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020623.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020624.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020625.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020626.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020627.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020628.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020629.he5.dap.nc4"]
               (util/parse-response response))))
      (let [response @(httpc/get (str base-url "&page_num=2") options)]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020630.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/FS2/AIRS/AIRX3TEST.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4"]
               (util/parse-response response)))))

    (testing "specific page_size and page_num"
      (let [response @(httpc/get (str base-url "&page_size=5") options)]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020621.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020622.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020623.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020624.he5.dap.nc4"]
               (util/parse-response response))))
      (let [response @(httpc/get (str base-url "&page_size=5&page_num=2") options)]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020625.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020626.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020627.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020628.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020629.he5.dap.nc4"]
               (util/parse-response response))))
      (let [response @(httpc/get (str base-url "&page_size=5&page_num=3") options)]
        (is (= 200 (:status response)))
        (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020630.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/FS2/AIRS/AIRX3TEST.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.dap.nc4"]
               (util/parse-response response)))))))