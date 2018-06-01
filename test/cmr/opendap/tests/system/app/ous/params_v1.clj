(ns ^:system cmr.opendap.tests.system.app.ous.params-v1
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.http.request :as request]
    [cmr.opendap.testing.system :as test-system]
    [cmr.opendap.testing.util :as util]
    [org.httpkit.client :as httpc])
  (:import
    (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(deftest collection-GET-query-coverage
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s"
                                "?coverage=G1200267320-HMR_TME,G1200267319-HMR_TME,"
                                collection-id)
                           (test-system/http-port)
                           collection-id)
                   (request/add-token-header {} (util/get-sit-token)))]
    (is (= 200 (:status response)))
    (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
           (util/parse-response response)))))


(deftest collection-GET-query-coverage-rangesubset
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s"
                                "?coverage=G1200267320-HMR_TME,G1200267319-HMR_TME,"
                                collection-id
                                "&rangesubset=V1200267322-HMR_TME,V1200267323-HMR_TME")
                           (test-system/http-port)
                           collection-id)
                   (request/add-token-header {} (util/get-sit-token)))]
    (is (= 200 (:status response)))
    (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"]
           (util/parse-response response)))))

(deftest collection-GET-query-coverage-subset
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s"
                                "?coverage=G1200267320-HMR_TME,G1200267319-HMR_TME,"
                                collection-id
                                "&subset=lat(56.109375,67.640625)"
                                "&subset=lon(-9.984375,19.828125)")
                           (test-system/http-port)
                           collection-id)
                   (request/add-token-header {} (util/get-sit-token)))]
    (is (= 200 (:status response)))
    (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A[0:1:23][22:1:34][169:1:200],CH4_VMR_A_ct[0:1:23][22:1:34][169:1:200],CH4_VMR_A_err[0:1:23][22:1:34][169:1:200],CH4_VMR_A_max[0:1:23][22:1:34][169:1:200],CH4_VMR_A_min[0:1:23][22:1:34][169:1:200],CH4_VMR_A_sdev[0:1:23][22:1:34][169:1:200],CH4_VMR_D[0:1:23][22:1:34][169:1:200],CH4_VMR_D_ct[0:1:23][22:1:34][169:1:200],CH4_VMR_D_err[0:1:23][22:1:34][169:1:200],CH4_VMR_D_max[0:1:23][22:1:34][169:1:200],CH4_VMR_D_min[0:1:23][22:1:34][169:1:200],CH4_VMR_D_sdev[0:1:23][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A[0:1:23][22:1:34][169:1:200],CH4_VMR_A_ct[0:1:23][22:1:34][169:1:200],CH4_VMR_A_err[0:1:23][22:1:34][169:1:200],CH4_VMR_A_max[0:1:23][22:1:34][169:1:200],CH4_VMR_A_min[0:1:23][22:1:34][169:1:200],CH4_VMR_A_sdev[0:1:23][22:1:34][169:1:200],CH4_VMR_D[0:1:23][22:1:34][169:1:200],CH4_VMR_D_ct[0:1:23][22:1:34][169:1:200],CH4_VMR_D_err[0:1:23][22:1:34][169:1:200],CH4_VMR_D_max[0:1:23][22:1:34][169:1:200],CH4_VMR_D_min[0:1:23][22:1:34][169:1:200],CH4_VMR_D_sdev[0:1:23][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
           (util/parse-response response)))))

(deftest collection-GET-query-coverage-invalid-subset
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s"
                                "?coverage=G1200267320-HMR_TME,G1200267319-HMR_TME,"
                                collection-id
                                "&subset=lat(-91,67.640625)"
                                "&subset=lon(-181,19.828125)")
                           (test-system/http-port)
                           collection-id)
                   (request/add-token-header {} (util/get-sit-token)))]
    (is (= 400 (:status response)))
    (is (= {:errors ["The values provided for latitude are not within the valid range of -90 degrees through 90 degress."
                     "The values provided for longitude are not within the valid range of -180 degrees through 180 degress."
                     "West must be within [-180.0] and [180.0] but was [-181.0]."
                     "South must be within [-90.0] and [90.0] but was [-91.0]."]}
           (util/parse-response response)))))

(deftest collection-GET-query-timeposition
  (testing "A timespan that should not include any granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?timeposition=2000-01-01T00:00:00Z"
                                               ",2000-01-02T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= []
             (util/parse-response response)))))
  (testing "A timespan that should include one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?timeposition=2016-07-01T00:00:00Z"
                                               ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "A timespan that should include two granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?timeposition=2002-09-01T00:00:00Z"
                                               ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "Multiple timespans ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?timeposition=2000-01-01T00:00:00Z"
                                               ",2002-10-01T00:00:00Z"
                                  "&timeposition=2010-07-01T00:00:00Z"
                                               ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response))))))
