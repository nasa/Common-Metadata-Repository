(ns cmr.system-int-test.admin.cache-api-test
  "Verifies the cache api is working."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.echo-util :as e]
            [cmr.system-int-test.utils.url-helper :as url]
            [clj-http.client :as client]
            [cheshire.core :as json]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}
                                          false))

(defn list-caches-for-app
  "Attempts to perform the given action using the url and method with the token. Returns true
  if the action was successful."
  [url token]
  (let [response (client/request {:url url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (url/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]

    ;; Make sure the status returned is success
    (when (< status 200)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(deftest cache-apis
  ;; grant read permission to group "admin-read-group-guid"
  (e/grant-group-admin "admin-read-group-guid" :read)
  ;; login as a member of group 1
 	(let [admin-read-token (e/login "admin" ["admin-read-group-guid"])]
    (testing "list caches"
      (are [url caches]
           (let [response (list-caches-for-app url admin-read-token)]
             (is (= (set caches) (set response))))

           (url/indexer-read-caches-url) ["acls" "general" "token-imp"]
           (url/index-set-read-caches-url) ["token-imp"]
           (url/mdb-read-caches-url) ["token-imp"]
           (url/search-read-caches-url) ["acls"
                                         "collections-for-gran-acls"
                                         "has-granules-map"
                                         "index-names"
                                         "token-imp"
                                         "token-sid"
                                         "xsl-transformer-templates"]))))