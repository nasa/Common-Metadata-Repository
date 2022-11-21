(ns ^:integration cmr.opendap.tests.integration.app.ous
  "Note: this namespace is exclusively for integration tests; all tests defined
  here will use one or more integration test fixtures.

  Warning: To run the integration tests, you will need to create CMR/ECHO
  tokens and export these as shell environment variables. In particular, each
  token gets its own ENV var:
  * CMR_SIT_TOKEN
  * CMR_UAT_TOKEN
  * CMR_PROD_TOKEN

  Definition used for integration tests:
  * https://en.wikipedia.org/wiki/Software_testing#Integration_testing"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.opendap.testing.system :as test-system]
   [cmr.opendap.testing.util :as util]
   [org.httpkit.client :as httpc]))

(use-fixtures :once test-system/with-system)

(deftest cmr-hits-and-search-after-test
  (let [options (-> {}
                    (request/add-token-header (util/get-sit-token)))
        ous-url (format (str "http://localhost:%s"
                             "/service-bridge/ous/collection/%s")
                        (test-system/http-port)
                        "C1200442179-HMR_TME")]
    (testing "cmr-hits no page size with 12 hits"
      (let [response @(httpc/get
                       ous-url
                       options)]
        (is (= 200 (:status response)))
        (is (= "12" (get-in response [:headers :cmr-hits])))
        (is (= "[\"hmr_tme\",1031097600000,1200442190]" (get-in response [:headers :cmr-search-after])))
        (is (= 10 (count (util/parse-response response))))))
    (testing "cmr-hits page size 3 with 12 hits"
      (let [response @(httpc/get
                       (str ous-url "?page_size=3")
                       options)]
        (is (= 200 (:status response)))
        (is (= "12" (get-in response [:headers :cmr-hits])))
        (is (= "[\"hmr_tme\",1031097600000,1200442183]" (get-in response [:headers :cmr-search-after])))
        (is (= (util/parse-response response)
               ["https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020621.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020622.he5.dap.nc4"]))))
    (testing "cmr-hits page size 2 with 12 hits using search after"
      (let [response @(httpc/get
                       (str ous-url "?page_size=2")
                       (request/add-search-after options "[\"hmr_tme\",1031097600000,1200442183]"))]
        (is (= 200 (:status response)))
        (is (= "12" (get-in response [:headers :cmr-hits])))
        (is (= "[\"hmr_tme\",1031097600000,1200442185]" (get-in response [:headers :cmr-search-after])))
        (is (= (util/parse-response response)
               ["https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020623.he5.dap.nc4"
                "https://f5eil01.edn.ecs.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m13208020624.he5.dap.nc4"]))))))

(deftest ous-collection-get-without-token
  "Note that when a token is not provided, the request doesn't make it past
  the network boundaries of CMR OPeNDAP, as such this is an integration test.
  With tokens, however, it does: those tests are system tests."
  (testing "Minimal get"
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format "http://localhost:%s/service-bridge/ous/collection/%s"
                             (test-system/http-port)
                             collection-id))]
      (is (= 403 (:status response)))
      (is (= {:errors ["An ECHO token is required to access this resource."]}
             (response/parse-json-result (:body response)))))))
