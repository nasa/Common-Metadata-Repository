(ns cmr.acl.acl-fetcher-test
  "test functions in acl-fetcher"
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.acl.acl-fetcher :as acl-fetcher]))

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
  (let [fun #'cmr.acl.acl-fetcher/object-identity-types->identity-strings]
    (is (= (fun object-identity-types-ex) ["system" "provider" "single_instance"]))))

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
  (let [acl-cache (acl-fetcher/create-consistent-acl-cache object-identity-types-ex)
        context {:system {:caches {acl-fetcher/acl-cache-key acl-cache}}}
        fun #'cmr.acl.acl-fetcher/get-all-acls]
    (testing "when there is one page of results"
      (with-redefs [cmr.transmit.access-control/search-for-acls (fn [context params] {:hits 10})]
        (is (= (fun context object-identity-types-ex) [{:hits 10}]))))
    (testing "when there is more than one page of results"
      (with-redefs [cmr.transmit.access-control/search-for-acls (fn [context params] {:hits 6000})]
        ;; should be 3 pages, so 3 item maps will be returned
        (is (= (fun context object-identity-types-ex) [{:hits 6000} {:hits 6000} {:hits 6000}]))))))

;(deftest process-search-for-acls-test
;  (let [acl-cache (acl-fetcher/create-consistent-acl-cache object-identity-types-ex)
;        context {:system {:caches {acl-fetcher/acl-cache-key acl-cache}}}
;        fun #'cmr.acl.acl-fetcher/process-search-for-acls]
;    (with-redefs [acl-fetcher/get-all-acls (fn [context object-identity-types] [{:hits 10
;                                                                                 :acl "acl"
;                                                                                 :items [{
;                                                                                          "revision_id" 1,
;                                                                                          "concept_id" "ACL1200000008-CMR",
;                                                                                          "identity_type" "Catalog Item",
;                                                                                          "name" "All Collections",
;                                                                                          "location" "https://cmr.earthdata.nasa.gov/access-control/acls/ACL1200000008-CMR"
;                                                                                          }]}])]
;      (println "result = " (fun context object-identity-types-ex))
;      ;(is (= (fun context object-identity-types-ex) [{:hits 10}]))
;      )
;    ))

;(deftest get-acls-test
;  (let [acl-cache (acl-fetcher/create-consistent-acl-cache object-identity-types-ex)
;        context {:system {:caches {acl-fetcher/acl-cache-key acl-cache}}}]
;    (with-redefs [cmr.acl.acl-fetcher/process-search-for-acls (fn [context object-identity-types] {
;                                                                                                    :hits 6000
;                                                                                                    :acl []
;                                                                                                    :items [{"revision_id" 1,
;                                                                                                            "concept_id" "ACL1200000008-CMR",
;                                                                                                            "identity_type" "Catalog Item",
;                                                                                                            "name" "All Collections",
;                                                                                                            "location" "https://cmr.earthdata.nasa.gov/access-control/acls/ACL1200000008-CMR"
;                                                                                                            }]})]
;      (acl-fetcher/get-acls context object-identity-types-ex)
;      )))