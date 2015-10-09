(ns cmr.system-int-test.admin.cache-api-test
  "Verifies the cache api is working."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.system :as s]
            [clj-http.client :as client]
            [cheshire.core :as json]))

(use-fixtures :each (ingest/reset-fixture
                      {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}))

(defn- list-caches-for-app
  "Gets a list of the caches for the given url."
  [url token]
  (let [response (client/request {:url url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]

    ;; Make sure the status returned is success
    (when (< status 200)
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
    (when (< status 200)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(defn- get-cache-value
  "Gets the value for a given key from the given cache."
  [url cache-name cache-key token]
  (let [full-url (str url "/" cache-name "/" cache-key)
        response (client/request {:url full-url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]
    ;; Make sure the status returned success
    (when (< status 200)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(deftest cache-apis
  (e/grant-group-admin (s/context) "admin-read-group-guid" :read)
  ;; login as a member of group 1
  (let [admin-read-token (e/login (s/context) "admin" ["admin-read-group-guid"])
        normal-user-token (e/login (s/context) "user")
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))]
    (testing "list caches"
      (are [url caches]
           (let [response (list-caches-for-app url admin-read-token)]
             (is (= (set caches) (set response))))

           (url/indexer-read-caches-url) ["acls" "general" "token-imp" "kms"]
           (url/index-set-read-caches-url) ["token-imp"]
           (url/mdb-read-caches-url) ["token-imp"]
           (url/ingest-read-caches-url) ["token-imp" "providers" "acls" "token-user-ids" "kms"]
           (url/search-read-caches-url) ["acls"
                                         "collections-for-gran-acls"
                                         "has-granules-map"
                                         "index-names"
                                         "token-imp"
                                         "token-sid"
                                         "xsl-transformer-templates"
                                         "kms"])
      (s/only-with-real-database
        (testing "list caches for bootstrap"
          (let [response (list-caches-for-app (url/bootstrap-read-caches-url) admin-read-token)]
            (is (= ["token-imp" "kms"] response))))))


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
           (url/index-set-read-caches-url)
           (url/mdb-read-caches-url)
           (url/ingest-read-caches-url)
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
           (str (url/index-set-read-caches-url) "/acls")
           (str (url/mdb-read-caches-url) "/acls")
           (str (url/ingest-read-caches-url) "/acls")
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
           (url/indexer-read-caches-url) "general" ["concept-indices" "concept-mapping-types"]
           (url/indexer-read-caches-url) "token-imp" [["ABC-2" "read"] ["ABC-1" "read"]]
           (url/index-set-read-caches-url) "token-imp" [["mock-echo-system-token" "update"]
                                                        ["mock-echo-system-token" "read"]
                                                        ["ABC-1" "read"]
                                                        ["ABC-2" "read"]]
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
           (url/search-read-caches-url) "token-sid" []
           (url/search-read-caches-url) "xsl-transformer-templates" [])
      (s/only-with-real-database
        (testing "list cache keys for bootstrap"
          (let [response (list-cache-keys (url/bootstrap-read-caches-url) "token-imp" admin-read-token)]
            (is (every? (set response)
                        [["ABC-1" "read"]
                         ["ABC-2" "read"]]))))))


    (testing "normal user cannot retrieve cache values"
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
           (str (url/index-set-read-caches-url) "/acls/acls")
           (str (url/mdb-read-caches-url) "/acls/acls")
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
        (is (= "Not Found" (:body response)))))

    (testing "lookup value for cache key"
      (are [url cache cache-key value]
           (let [response (get-cache-value url cache cache-key admin-read-token)]
             (is (= (set value) (set response))))
           (url/indexer-read-caches-url) "general" "concept-mapping-types" {:collection "collection"
                                                                            :granule "granule"
                                                                            :tag "tag"}))))

