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
   [clojure.test :refer :all]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.testing.system :as test-system]
   [org.httpkit.client :as httpc]))

(use-fixtures :once test-system/with-system)

(deftest ous-collection-get-without-token
  "Note that when a token is not provided, the request doesn't make it past
  the network boundaries of CMR OPeNDAP, as such this is an integration test.
  With tokens, however, it does: those tests are system tests."
  (testing "Minimal get"
    (let [collection-id "C1200267318-HMR_TME"
          response @(httpc/get
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id))]
      (is (= 403 (:status response)))
      (is (= {:errors ["An ECHO token is required to access this resource."]}
             (response/parse-json-body (:body response)))))))
