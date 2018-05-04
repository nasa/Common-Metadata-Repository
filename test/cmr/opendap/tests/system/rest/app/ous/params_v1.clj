(ns ^:system cmr.opendap.tests.system.rest.app.ous.params-v1
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
  (let [collection-id "C1200187767-EDF_OPS"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s"
                                "?coverage=G1200187775-EDF_OPS,G1200245955-EDF_OPS,"
                                collection-id)
                           (test-system/http-port)
                           collection-id)
                   (request/add-token-header {} (util/get-sit-token)))]
    (is (= 200 (:status response)))
    (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"]
           (util/parse-response response)))))


(deftest collection-GET-query-coverage-rangesubset
  (let [collection-id "C1200187767-EDF_OPS"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s"
                                "?coverage=G1200187775-EDF_OPS,G1200245955-EDF_OPS,"
                                collection-id
                                "&rangesubset=V1200241812-EDF_OPS,V1200241813-EDF_OPS")
                           (test-system/http-port)
                           collection-id)
                   (request/add-token-header {} (util/get-sit-token)))]
    (is (= 200 (:status response)))
    (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct,CH4_VMR_A_max,Latitude,Longitude"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct,CH4_VMR_A_max,Latitude,Longitude"]
           (util/parse-response response)))))

(deftest collection-GET-query-coverage-subset
  (let [collection-id "C1200187767-EDF_OPS"
        response @(httpc/get
                   (format (str "http://localhost:%s"
                                "/opendap/ous/collection/%s"
                                "?coverage=G1200187775-EDF_OPS,G1200245955-EDF_OPS,"
                                collection-id
                                "&subset=lat(56.109375,67.640625)"
                                "&subset=lon(-9.984375,19.828125)")
                           (test-system/http-port)
                           collection-id)
                   (request/add-token-header {} (util/get-sit-token)))]
    (is (= 200 (:status response)))
    (is (= ["https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"
            "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc?CH4_VMR_A_ct[*][22:1:34][169:1:200],CH4_VMR_A_max[*][22:1:34][169:1:200],CH4_VMR_A_sdev[*][22:1:34][169:1:200],CH4_VMR_D_ct[*][22:1:34][169:1:200],CH4_VMR_D_max[*][22:1:34][169:1:200],CH4_VMR_D_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_ct[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_max[*][22:1:34][169:1:200],CH4_VMR_TqJ_A_sdev[*][22:1:34][169:1:200],CH4_VMR_TqJ_D_ct[*][22:1:34][169:1:200],Latitude[22:1:34],Longitude[169:1:200]"]
           (util/parse-response response)))))
