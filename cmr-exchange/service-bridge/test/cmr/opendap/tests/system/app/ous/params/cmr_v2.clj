(ns ^:system cmr.opendap.tests.system.app.ous.params.cmr-v2
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
    [ring.util.codec :as codec])
  (:import
    (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(def options (-> {}
                 (request/add-token-header (util/get-sit-token))
                 (util/override-api-version-header "v2")))

(deftest collection-GET
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s")
                           (test-system/http-port)
                           collection-id)
                   options)]
    (is (= 200 (:status response)))
    (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
           (util/parse-response response)))))

(deftest collection-GET-variables
  (testing "GET one variable ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?variables=V1200267322-HMR_TME")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
             (util/parse-response response)))))
  (testing "GET with variables ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?variables=V1200267322-HMR_TME,V1200267323-HMR_TME")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"]
             (util/parse-response response)))))
  (testing "GET with repeated variables ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?variables=V1200267322-HMR_TME&"
                                  "variables=V1200267323-HMR_TME")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"]
             (util/parse-response response))))))

(deftest collection-GET-variables-array
  (testing "GET one variable in an array..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "variables[]")
                          "=V1200267322-HMR_TME")
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
             (util/parse-response response)))))
  (testing "GET with variables in an array ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "variables[]")
                          "=V1200267322-HMR_TME&"
                          (codec/url-encode "variables[]")
                          "=V1200267323-HMR_TME")
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"]
             (util/parse-response response))))))

(deftest collection-GET-granules
  (testing "GET with one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?granules=G1200267320-HMR_TME")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET with granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?granules=G1200267320-HMR_TME,G1200267319-HMR_TME")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET with repeated granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?granules=G1200267320-HMR_TME&"
                                  "granules=G1200267319-HMR_TME")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET without one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?granules=G1200267320-HMR_TME"
                                  "&exclude-granules=true")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET without any granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?granules=G1200267320-HMR_TME,G1200267319-HMR_TME"
                                  "&exclude-granules=true")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= []
             (util/parse-response response))))))

(deftest collection-GET-granules-array
  (testing "GET with one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "granules[]")
                          "=G1200267320-HMR_TME")
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET with granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "granules[]")
                          "=G1200267320-HMR_TME&"
                          (codec/url-encode "granules[]")
                          "=G1200267319-HMR_TME")
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET without one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "granules[]")
                          "=G1200267320-HMR_TME"
                          "&exclude-granules=true")
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET without any granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "granules[]")
                          "=G1200267320-HMR_TME&"
                          (codec/url-encode "granules[]")
                          "=G1200267319-HMR_TME"
                          "&exclude-granules=true")
                     options)]
      (is (= 200 (:status response)))
      (is (= []
             (util/parse-response response))))))

(deftest collection-GET-spatial
  (testing "GET with subset ..."
    (let [collection-id "C1200276794-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?subset=lat(56.109375,67.640625)"
                                  "&subset=lon(-9.984375,19.828125)")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.01.01/AIRS.2016.01.01.L3.RetStd001.v6.0.31.0.G16004140142.hdf.nc?SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200],SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200],SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response)))))
  (testing "GET with bounding box ..."
    (let [collection-id "C1200276794-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?bounding-box="
                                  "-9.984375,56.109375,19.828125,67.640625")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.01.01/AIRS.2016.01.01.L3.RetStd001.v6.0.31.0.G16004140142.hdf.nc?SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200],SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200],SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response))))))

(deftest collection-GET-invalid-bounding-box
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?bounding-box="
                                  "-181,-91,19.828125,67.640625")
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

(deftest collection-GET-query-temporal
  (testing "A timespan that should not include any granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?temporal=2000-01-01T00:00:00Z"
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
                                  "?temporal=2016-07-01T00:00:00Z"
                                           ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "A timespan that should include two granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?temporal=2002-09-01T00:00:00Z"
                                           ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "Multiple timespans ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?temporal=2000-01-01T00:00:00Z"
                                           ",2002-10-01T00:00:00Z"
                                  "&temporal=2010-07-01T00:00:00Z"
                                           ",2016-07-03T00:00:00Z")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response))))))

(deftest collection-GET-query-temporal-array
  (testing "A timespan that should not include any granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "temporal[]")
                          "="
                          (codec/url-encode "2000-01-01T00:00:00Z")
                          ","
                          (codec/url-encode "2000-01-02T00:00:00Z"))
                     options)]
      (is (= 200 (:status response)))
      (is (= []
             (util/parse-response response)))))
  (testing "A timespan that should include one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "temporal[]")
                          "="
                          (codec/url-encode
                           "2016-07-01T00:00:00Z,2016-07-03T00:00:00Z"))
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "A timespan that should include two granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "temporal[]")
                          "=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z")
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "Multiple timespans ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (str "http://localhost:"
                          (test-system/http-port)
                          "/service-bridge/ous/collection/"
                          collection-id
                          "?"
                          (codec/url-encode "temporal[]")
                          "=2000-01-01T00:00:00Z,2002-10-01T00:00:00Z"
                          "&"
                          (codec/url-encode "temporal[]")
                          "=2010-07-01T00:00:00Z,2016-07-03T00:00:00Z")
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response))))))

(deftest collection-GET-multiple-params
  (testing "GET with variables, graules, and bounding box ..."
    (let [collection-id "C1200276794-HMR_TME"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s"
                                  "?variables=V1200276801-HMR_TME&"
                                  "granules=G1200276795-HMR_TME&"
                                  "bounding-box="
                                  "-9.984375,56.109375,19.828125,67.640625")
                             (test-system/http-port)
                             collection-id)
                     options)]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.01.01/AIRS.2016.01.01.L3.RetStd001.v6.0.31.0.G16004140142.hdf.nc?SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response))))))

(deftest collection-POST
  (let [collection-id "C1200267318-HMR_TME"
        response @(httpc/post
                   (format (str "http://localhost:%s"
                                "/service-bridge/ous/collection/%s")
                           (test-system/http-port)
                           collection-id)
                   (merge
                    (util/create-json-payload
                     {})
                    options))]
    (is (= 200 (:status response)))
    (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
           (util/parse-response response)))))

(deftest collection-POST-variables
  (testing "POST with one variable ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:variables ["V1200267322-HMR_TME"]})
                       options))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,Latitude,Longitude"]
             (util/parse-response response)))))
  (testing "POST with variables ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:variables ["V1200267322-HMR_TME"
                                    "V1200267323-HMR_TME"]})
                       options))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A,CH4_VMR_A_ct,Latitude,Longitude"]
             (util/parse-response response))))))

(deftest collection-POST-granules
  (testing "POST with one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200267320-HMR_TME"]})
                      options))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
             (util/parse-response response)))))
  (testing "POST with granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200267320-HMR_TME"
                                   "G1200267319-HMR_TME"]})
                      options))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "POST without one granule ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200267319-HMR_TME"]
                        :exclude-granules true})
                      options))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
             (util/parse-response response)))))
  (testing "POST without any granules ..."
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200267320-HMR_TME"
                                   "G1200267319-HMR_TME"]
                        :exclude-granules true})
                      options))]
      (is (= 200 (:status response)))
      (is (= []
             (util/parse-response response))))))

(deftest collection-POST-spatial
  (testing "POST with subset ..."
    (let [collection-id "C1200276794-HMR_TME"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:subset ["lat(56.109375,67.640625)"
                                 "lon(-9.984375,19.828125)"]})
                      options))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.01.01/AIRS.2016.01.01.L3.RetStd001.v6.0.31.0.G16004140142.hdf.nc?SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200],SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200],SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response)))))
  (testing "POST with bounding box ..."
    (let [collection-id "C1200276794-HMR_TME"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/service-bridge/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:bounding-box "-9.984375,56.109375,19.828125,67.640625"})
                      options))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.01.01/AIRS.2016.01.01.L3.RetStd001.v6.0.31.0.G16004140142.hdf.nc?SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200],SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user/FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?SurfPres_Forecast_A_ct_VarBounds[22:1:34][169:1:200],SurfPres_Forecast_A_VarBounds[22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response))))))
