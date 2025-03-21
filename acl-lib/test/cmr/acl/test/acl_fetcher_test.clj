(ns cmr.acl.test.acl-fetcher-test
  "test functions in acl-fetcher"
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.acl.acl-fetcher :as acl-fetcher]
    [cmr.transmit.access-control :as access-control]))

(def object-identity-types-ex
  [:system-object :provider-object :single-instance-object])

(deftest acl-cache-key-test
  (is (= acl-fetcher/acl-cache-key :acls)))

(deftest acl-keys-to-track-test
  (is (= acl-fetcher/acl-keys-to-track [":acls-hash-code"])))

(deftest create-acl-cache
  (let [acl-cache (acl-fetcher/create-acl-cache [:system-object :provider-object :single-instance-object])]
    (is (instance? cmr.common.cache.single_thread_lookup_cache.SingleThreadLookupCache acl-cache))))

(deftest identity-string-map-test
  (let [expected-map {:system-object "system"
                      :provider-object "provider"
                      :single-instance-object "single_instance"
                      :catalog-item "catalog_item"}]
    (is (= acl-fetcher/identity-string-map expected-map))))

(deftest object-identity-types->identity-strings-test
  (is (= (#'cmr.acl.acl-fetcher/object-identity-types->identity-strings object-identity-types-ex) ["system" "provider" "single_instance"])))

(deftest context->cached-object-identity-types-test
  (let [acl-cache (acl-fetcher/create-consistent-acl-cache object-identity-types-ex)
        context {:system {:caches {acl-fetcher/acl-cache-key acl-cache}}}
        fun #'cmr.acl.acl-fetcher/context->cached-object-identity-types]
    (is (= (fun context) object-identity-types-ex))))

;; sample acl response
  ;{
  ; "hits" : 3,
  ; "took" : 6,
  ; "items" : [ {
  ;              "revision_id" : 1,
  ;              "concept_id" : "ACL1200000008-CMR",
  ;              "identity_type" : "Catalog Item",
  ;              "name" : "All Collections",
  ;              "location" : "https://cmr.earthdata.nasa.gov/access-control/acls/ACL1200000008-CMR"
  ;              }, {
  ;                  "revision_id" : 1,
  ;                  "concept_id" : "ACL1200000009-CMR",
  ;                  "identity_type" : "Catalog Item",
  ;                  "name" : "All Granules",
  ;                  "location" : "https://cmr.earthdata.nasa.gov/access-control/acls/ACL1200000009-CMR"
  ;                  }, {
  ;                      "revision_id" : 1,
  ;                      "concept_id" : "ACL1200000006-CMR",
  ;                      "identity_type" : "Group",
  ;                      "name" : "Group - AG1234-CMR",
  ;                      "location" : "https://cmr.earthdata.nasa.gov/access-control/acls/ACL1200000006-CMR"
  ;                      } ]
  ; }

(deftest get-all-acls-test
    (testing "when there is one page of results"
      (with-redefs [access-control/search-for-acls (fn [_context _params] {:hits 10})]
        (is (= (#'cmr.acl.acl-fetcher/get-all-acls {} object-identity-types-ex) [{:hits 10}]))))
    (testing "when there is more than one page of results"
      (with-redefs [access-control/search-for-acls (fn [_context _params] {:hits 6000})]
        ;; should be 3 pages, so 3 item maps will be returned
        (is (= (#'cmr.acl.acl-fetcher/get-all-acls {} object-identity-types-ex) [{:hits 6000} {:hits 6000} {:hits 6000}])))))
