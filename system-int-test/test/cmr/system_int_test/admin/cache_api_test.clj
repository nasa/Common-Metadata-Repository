(ns cmr.system-int-test.admin.cache-api-test
  "Verifies the cache api is working."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.common.config :as common-config]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each (ingest/reset-fixture
                     {"prov1guid" "PROV1" "prov2guid" "PROV2" "prov3guid" "PROV3"}))

(defn list-caches-for-app
  "Gets a list of the caches for the given url."
  [url token]
  (let [response (client/request {:url url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]

    ;; Make sure the status returned is success
    (when (not= status 200)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(defn- list-cache-keys
  "Gets a list of the cache keys for the given cache at the given url."
  [url cache-name token]
  (let [full-url (str url "/" cache-name)
        response (client/request {:url full-url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]
    ;; Make sure the status returned is success
    (when (not= status 200)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(defn get-cache-value
  "Gets the value for a given key from the given cache. Checks that the retruend status code
  matches the expected status code."
  [url cache-name cache-key token expected-status]
  (let [full-url (str url "/" cache-name "/" cache-key)
        response (client/request {:url full-url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]
    ;; Make sure the returend status matches the expected-status
    (when (not= status expected-status)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(deftest cache-apis
  ;; login as a member of group 1
  (let [admin-read-group-concept-id (e/get-or-create-group (s/context) "admin-read-group")
        admin-read-token (e/login (s/context) "admin" [admin-read-group-concept-id])
        normal-user-token (e/login (s/context) "user")
        _ (e/grant-group-admin (s/context) admin-read-group-concept-id :read)
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))]

    (testing "list caches"
      (are [url caches]
           (is (= (set caches) (set (list-caches-for-app url admin-read-token))))
        ;; XXX It would be better if we used the respective vars here instead
        ;; of string values. That way we can change cache names if needed
        ;; without updating tests.
        (url/indexer-read-caches-url) ["acls"
                                       "collection-granule-aggregation-cache"
                                       "health"
                                       "humanizer-cache"
                                       "indexer-index-set-cache"
                                       "kms"
                                       "token-imp"
                                       "usage-metrics-cache"]
        (url/mdb-read-caches-url) ["health"
                                   "token-imp"]
        (url/ingest-read-caches-url) ["acls"
                                      "health"
                                      "humanizer-alias-cache"
                                      "xsl-transformer-templates"
                                      "kms"
                                      "providers"
                                      "token-imp"
                                      "token-smp"
                                      "token-sid"
                                      "token-user-id"
                                      "token-user-ids"
                                      "write-enabled"]
        (url/access-control-read-caches-url) ["acls"
                                              "group-ids-guids"
                                              "health"
                                              "providers"
                                              "write-enabled"]
        (url/search-read-caches-url) ["acls"
                                      "collections-for-gran-acls"
                                      "has-granules-map"
                                      "has-granules-or-cwic-map"
                                      "has-granules-or-opensearch-map"
                                      "health"
                                      "humanizer-range-facet-cache"
                                      "humanizer-report-cache"
                                      "index-names"
                                      "kms"
                                      "metadata-cache"
                                      "scroll-id-cache"
                                      "first-page-cache"
                                      "token-imp"
                                      "token-sid"
                                      "token-user-id"
                                      "write-enabled"
                                      "xsl-transformer-templates"])
      ;; CMR-4337 - bootstrap
      #_(s/only-with-real-database
         (testing "list caches for bootstrap"
           (let [response (list-caches-for-app (url/bootstrap-read-caches-url) admin-read-token)]
             (is (= ["token-imp" "kms" "health"] response))))))

    (testing "normal user cannot access cache list API"
      (are [url]
           (let [response (client/request {:url url
                                           :method :get
                                           :query-params {:token normal-user-token}
                                           :connection-manager (s/conn-mgr)
                                           :throw-exceptions false})
                 errors (:errors (json/decode (:body response) true))]
             (is (= 401 (:status response)))
             (is (= ["You do not have permission to perform that action."] errors)))
        (url/indexer-read-caches-url)
        (url/mdb-read-caches-url)
        (url/ingest-read-caches-url)
        (url/access-control-read-caches-url)
        (url/search-read-caches-url))
      (s/only-with-real-database
       (testing "normal user cannot access cache list API for bootstrap"
         (let [response (client/request {:url (url/bootstrap-read-caches-url)
                                         :method :get
                                         :query-params {:token normal-user-token}
                                         :connection-manager (s/conn-mgr)
                                         :throw-exceptions false})
               errors (:errors (json/decode (:body response) true))]
           (is (= 401 (:status response)))
           (is (= ["You do not have permission to perform that action."] errors))))))

    (testing "retrieval of keys for non-existent cache results in a 404"
      (let [response (client/request {:url (str (url/indexer-read-caches-url) "/INVALID-CACHE-ABC")
                                      :method :get
                                      :query-params {:token admin-read-token}
                                      :connection-manager (s/conn-mgr)
                                      :throw-exceptions false})]
        (is (= 404 (:status response)))
        (is (= "Not Found" (:body response)))))

    (testing "normal user cannot retrieve cache keys"
      (are [url]
           (let [response (client/request {:url url
                                           :method :get
                                           :query-params {:token normal-user-token}
                                           :connection-manager (s/conn-mgr)
                                           :throw-exceptions false})
                 errors (:errors (json/decode (:body response) true))]
             (is (= 401 (:status response)))
             (is (= ["You do not have permission to perform that action."] errors)))
        (str (url/indexer-read-caches-url) "/acls")
        (str (url/mdb-read-caches-url) "/acls")
        (str (url/ingest-read-caches-url) "/acls")
        (str (url/access-control-read-caches-url) "/acls")
        (str (url/search-read-caches-url) "/acls"))
      (s/only-with-real-database
       (testing "normal user cannot retrieve cache keys for bootstrap"
         (let [response (client/request {:url (url/bootstrap-read-caches-url)
                                         :method :get
                                         :query-params {:token normal-user-token}
                                         :connection-manager (s/conn-mgr)
                                         :throw-exceptions false})
               errors (:errors (json/decode (:body response) true))]
           (is (= 401 (:status response)))
           (is (= ["You do not have permission to perform that action."] errors))))))

    (testing "list cache keys"
      (are [url cache cache-keys]
           (let [response (list-cache-keys url cache admin-read-token)]
             (is (= (set cache-keys) (set response))))

        (url/indexer-read-caches-url) "acls" ["acls"]
        (url/indexer-read-caches-url) "indexer-index-set-cache" ["concept-indices" "concept-mapping-types"]
        (url/indexer-read-caches-url) "token-imp" [["ABC-2" "read"] ["ABC-1" "read"]]
        (url/mdb-read-caches-url) "token-imp" [["mock-echo-system-token" "update"]
                                               ["ABC-1" "read"]
                                               ["ABC-2" "read"]]
        (url/ingest-read-caches-url) "token-imp" [[nil "update"]
                                                  ["ABC-1" "read"]
                                                  ["ABC-2" "read"]]
        (url/search-read-caches-url) "acls" ["acls"]
        (url/search-read-caches-url) "collections-for-gran-acls" []
        (url/search-read-caches-url) "has-granules-map" []
        (url/search-read-caches-url) "index-names" []
        (url/search-read-caches-url) "token-imp" [["ABC-1" "read"] ["ABC-2" "read"]]
        (url/search-read-caches-url) "token-sid" ["ABC-2" "ABC-1"]
        (url/search-read-caches-url) "xsl-transformer-templates" []
        (url/search-read-caches-url) "token-user-id" ["ABC-1" "ABC-2"])
      ;; CMR-4337 bootstrap
      #_(s/only-with-real-database
         (testing "list cache keys for bootstrap"
           (let [response (list-cache-keys (url/bootstrap-read-caches-url) "token-imp" admin-read-token)]
             (is (every? (set response)
                         [["ABC-1" "read"]
                          ["ABC-2" "read"]])))))) (testing "normal user cannot retrieve cache values"
                                                    (are [url]
                                                         (let [response (client/request {:url url
                                                                                         :method :get
                                                                                         :query-params {:token normal-user-token}
                                                                                         :connection-manager (s/conn-mgr)
                                                                                         :throw-exceptions false})
                                                               errors (:errors (json/decode (:body response) true))]
                                                           (is (= 401 (:status response)))
                                                           (is (= ["You do not have permission to perform that action."] errors)))
                                                      (str (url/indexer-read-caches-url) "/acls/acls")
                                                      (str (url/mdb-read-caches-url) "/acls/acls")
                                                      (str (url/access-control-read-caches-url) "/acls/acls")
                                                      (str (url/ingest-read-caches-url) "/acls/acls")
                                                      (str (url/search-read-caches-url) "/acls/acls"))
                                                    (s/only-with-real-database
                                                     (testing "normal user cannot retrieve cache values for bootstrap"
                                                       (let [response (client/request {:url (url/bootstrap-read-caches-url)
                                                                                       :method :get
                                                                                       :query-params {:token normal-user-token}
                                                                                       :connection-manager (s/conn-mgr)
                                                                                       :throw-exceptions false})
                                                             errors (:errors (json/decode (:body response) true))]
                                                         (is (= 401 (:status response)))
                                                         (is (= ["You do not have permission to perform that action."] errors))))))

    (testing "retrieval of value for non-existent key results in a 404"
      (let [response (client/request {:url (str (url/indexer-read-caches-url)
                                                "/acls/INVALID-CACHE-ABC")
                                      :method :get
                                      :query-params {:token admin-read-token}
                                      :connection-manager (s/conn-mgr)
                                      :throw-exceptions false})]
        (is (= 404 (:status response)))
        (is (= "{\"error\":\"missing key [:INVALID-CACHE-ABC] for cache [acls]\"}"
               (:body response)))))

    (testing "lookup value for cache key"
      (are [url cache cache-key value]
           (let [response (get-cache-value url cache cache-key admin-read-token 200)]
             (is (= (set value) (set response))))
        (url/indexer-read-caches-url)
        "indexer-index-set-cache"
        "concept-mapping-types"
        (merge
         {:collection "properties"
          :granule "properties"
          :tag "properties"
          :variable "properties"
          :service "properties"
          :tool "properties"
          :subscription "properties"}
         (zipmap 
          (reduce (fn [data, item] (conj data (keyword (str "generic-" (name item)))))
                  []
                  (keys (common-config/approved-pipeline-documents)))
          (repeat "properties")))))))
