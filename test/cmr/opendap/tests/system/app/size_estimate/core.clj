(ns ^:system cmr.opendap.tests.system.app.size-estimate.core
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

(deftest not-implemented
  (let [collection-id "C1200267318-HMR_TME"
        granule-id "G1200267320-HMR_TME"
        variable-id "V1200267322-HMR_TME"
        options (-> {}
                    (request/add-token-header (util/get-sit-token)))]
    (testing "GET without bounding box ..."
      (let [response @(httpc/get
                       (format (str "http://localhost:%s"
                                    "/opendap/size-estimate/collection/%s"
                                    "?granules=%s"
                                    "&variables=%s")
                               (test-system/http-port)
                               collection-id
                               granule-id
                               variable-id)
                       options)]
        (is (= 400 (:status response)))
        (is (= "cmr-opendap.v2.1; format=json"
               (get-in response [:headers :cmr-media-type])))
        (is (= {:errors ["sizing estimate not-implemented"]}
               (util/parse-response response)))))))
