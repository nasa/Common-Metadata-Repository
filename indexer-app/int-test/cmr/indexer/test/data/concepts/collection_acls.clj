(ns cmr.indexer.test.data.concepts.collection_acls
  (:require
   [clojure.test :refer :all]
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as a]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.indexer.data.concepts.collection.collection-util :as coll-util]))

(defn context-with-acls
  "Creates a fake context with the acls in an acl cache"
  [& acls]
  (let [acl-cache (af/create-acl-cache*
                    (mem-cache/create-in-memory-cache)
                    [:catalog-item])]
    (cache/set-value acl-cache :acls acls)
    {:system {:caches {af/acl-cache-key acl-cache}}}))

(defn group-ace
  [group-guid & permissions]
  {:permissions permissions
   :group-id group-guid})

(defn user-type-ace
  [user-type & permissions]
  {:permissions permissions
   :user-type user-type})

(def get-coll-permitted-group-ids
  "Allow testing private function"
  #'coll-util/get-coll-permitted-group-ids)

(deftest test-get-coll-permitted-group-ids
  (testing "group access"
    (let [acl1 {:group-permissions [(group-ace "read-order" "read" "order")
                                    (group-ace "order" "order")
                                    (group-ace "just-read" "read")
                                    (group-ace "order-read" "order" "read")
                                    (group-ace "no-permit")]
                :catalog-item-identity {:provider-id "PROV1"
                                        :collection-applicable true}}
          acl2 {:group-permissions [(group-ace "group2" "read")]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          acl3 {:group-permissions [(group-ace "group3" "read")]
                :catalog-item-identity {:provider-id "PROV1"
                                        :collection-applicable true}}
          context (context-with-acls acl1 acl2 acl3)]
      (is (= ["read-order" "just-read" "order-read" "group3"]
             (get-coll-permitted-group-ids context "PROV1" {:fake-coll "foo"})))))

  (testing "guest access"
    (let [acl1 {:group-permissions [(group-ace "group1" "read")
                                    (user-type-ace :guest "order" "read")]
                :catalog-item-identity {:provider-id "PROV1"
                                        :collection-applicable true}}
          acl2 {:group-permissions [(user-type-ace :registered "order")]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          context (context-with-acls acl1 acl2)]
      (is (= ["group1" "guest"]
             (get-coll-permitted-group-ids context "PROV1" {:fake-coll "foo"})))))

  (testing "registered user access"
    (let [acl1 {:group-permissions [(group-ace "group1" "read")
                                    (user-type-ace :registered "order" "read")]
                :catalog-item-identity {:provider-id "PROV1"
                                        :collection-applicable true}}
          acl2 {:group-permissions [(user-type-ace :guest "order")]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          context (context-with-acls acl1 acl2)]
      (is (= ["group1" "registered"]
             (get-coll-permitted-group-ids context "PROV1" {:fake-coll "foo"})))))

  (testing "registered user access"
    (let [acl1 {:group-permissions [(group-ace "group1" "read")
                                    (user-type-ace :registered "order" "read")]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          acl2 {:group-permissions [(user-type-ace :guest "read")]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          context (context-with-acls acl1 acl2)]
      (is (empty? (get-coll-permitted-group-ids context "PROV1" {:fake-coll "foo"}))))))
