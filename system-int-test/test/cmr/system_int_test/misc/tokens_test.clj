(ns cmr.system-int-test.misc.tokens-test
  (:require
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def bad-gateway-body "<html>\r\n<head><title>504 Gateway Time-out</title></head>\r\n<body>\r\n<center><h1>504 Gateway Time-out</h1></center>\r\n</body>\r\n</html>\r\n")

(deftest multiple-authentication-tokens-test
  (ingest/ingest-concept
   (dc/collection-concept {:short-name "a-collection"})
   {:raw? true})
  (index/wait-until-indexed)

  (testing "Mis-matched header and token return bad request"
    (let [response (client/get (str (url/search-root) "collections")
                               {:headers {"Authorization" "Bearer notreallyjwt"}
                                :query-params {"token" "EDL-iamnotarealtoken"}
                                :throw-exceptions? false})]
      (is (= 400 (:status response)))
      (is (some? (re-find #"Multiple authorization tokens found" (:body response))))))

  (testing "matching invalid tokens attempt to be authenticated and are rejected"
    (let [response (client/get (str (url/search-root) "collections")
                               {:headers {"Authorization" "Bearer mock-bearer-token"}
                                :query-params {"token" "Bearer mock-bearer-token"}
                                :throw-exceptions? false})]
      (is (= 401 (:status response)))))

  (testing "matching valid tokens attempt to be authenticated and are accepted"
    (let [response (client/get (str (url/search-root) "collections")
                               {:headers {"Authorization" (transmit/echo-system-token)}
                                :query-params {"token" (transmit/echo-system-token)}
                                :throw-exceptions? false})]
      (is (= 200 (:status response))))))
