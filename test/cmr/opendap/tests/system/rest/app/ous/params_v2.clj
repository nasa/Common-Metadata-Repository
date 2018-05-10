(ns ^:system cmr.opendap.tests.system.rest.app.ous.params-v2
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

(deftest collection-GET
  (let [collection-id "C1200187767-EDF_OPS"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s")
                           (test-system/http-port)
                           collection-id)
                   (request/add-token-header {} (util/get-sit-token)))]
    (is (= 200 (:status response)))
    (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
           (util/parse-response response)))))

(deftest collection-GET-variables
  (testing "GET one variable ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?variables=V1200241812-EDF_OPS")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct,Latitude,Longitude"]
             (util/parse-response response)))))
  (testing "GET with variables ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?variables=V1200241812-EDF_OPS,V1200241813-EDF_OPS")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct,CH4_VMR_A_max,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct,CH4_VMR_A_max,Latitude,Longitude"]
             (util/parse-response response))))))

(deftest collection-GET-granules
  (testing "GET with one granule ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?granules=G1200187775-EDF_OPS")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET with granules ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET without one granule ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?granules=G1200187775-EDF_OPS"
                                  "&exclude-granules=true")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "GET without any granules ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS"
                                  "&exclude-granules=true")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= []
             (util/parse-response response))))))

(deftest collection-GET-spatial
  (testing "GET with subset ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?subset=lat(56.109375,67.640625)"
                                  "&subset=lon(-9.984375,19.828125)")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_sdev[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_sdev[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response)))))
  (testing "GET with bounding box ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?bounding-box="
                                  "-9.984375,56.109375,19.828125,67.640625")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_sdev[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_sdev[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response))))))

(deftest collection-GET-multiple-params
  (testing "GET with variables, graules, and bounding box ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s"
                                  "?variables=V1200241812-EDF_OPS&"
                                  "granules=G1200187775-EDF_OPS&"
                                  "bounding-box="
                                  "-9.984375,56.109375,19.828125,67.640625")
                             (test-system/http-port)
                             collection-id)
                     (request/add-token-header {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response))))))

(deftest collection-POST
  (let [collection-id "C1200187767-EDF_OPS"
        response @(httpc/post
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s")
                           (test-system/http-port)
                           collection-id)
                   (merge
                    (util/create-json-payload
                     {})
                    (request/add-token-header
                     {} (util/get-sit-token))))]
    (is (= 200 (:status response)))
    (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
           (util/parse-response response)))))

(deftest collection-POST-variables
  (testing "POST with one variable ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:variables ["V1200241812-EDF_OPS"]})
                       (request/add-token-header {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct,Latitude,Longitude"]
             (util/parse-response response)))))
  (testing "POST with variables ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:variables ["V1200241812-EDF_OPS"
                                    "V1200241813-EDF_OPS"]})
                       (request/add-token-header {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct,CH4_VMR_A_max,Latitude,Longitude"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct,CH4_VMR_A_max,Latitude,Longitude"]
             (util/parse-response response))))))

(deftest collection-POST-granules
  (testing "POST with one granule ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200187775-EDF_OPS"]})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
             (util/parse-response response)))))
  (testing "POST with granules ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200187775-EDF_OPS"
                                   "G1200245955-EDF_OPS"]})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
             (util/parse-response response)))))
  (testing "POST without one granule ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200245955-EDF_OPS"]
                        :exclude-granules true})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"]
             (util/parse-response response)))))
  (testing "POST without any granules ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:granules ["G1200187775-EDF_OPS"
                                   "G1200245955-EDF_OPS"]
                        :exclude-granules true})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= []
             (util/parse-response response))))))

(deftest collection-POST-spatial
  (testing "POST with subset ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:subset ["lat(56.109375,67.640625)"
                                 "lon(-9.984375,19.828125)"]})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_sdev[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_sdev[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response)))))
  (testing "POST with bounding box ..."
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/post
                     (format (str "http://localhost:%s"
                                  "/opendap/ous/collection/%s")
                             (test-system/http-port)
                             collection-id)
                     (merge
                      (util/create-json-payload
                       {:bounding-box "-9.984375,56.109375,19.828125,67.640625"})
                      (request/add-token-header
                       {} (util/get-sit-token))))]
      (is (= 200 (:status response)))
      (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_sdev[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
              "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01/user//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_sdev[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
             (util/parse-response response))))))
