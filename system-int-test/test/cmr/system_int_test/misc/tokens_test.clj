(ns cmr.system-int-test.misc.tokens-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

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
      (is (some? (re-find #"Multiple authorization tokens found" (:body response)))))))
