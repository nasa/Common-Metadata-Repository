(ns cmr.acl.test.core
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as cache]
            [cmr.acl.core :as a]
            [cmr.acl.acl-cache :as ac]))

(defn context-with-acls
  "Creates a fake context with the acls in an acl cache"
  [& acls]
  (let [acl-cache (ac/create-acl-cache)]
    (cache/update-cache
      acl-cache
      #(assoc % :acls acls))
    {:system {:caches {ac/acl-cache-key acl-cache}}}))

(defn group-ace
  [group-guid & permissions]
  {:permissions permissions
   :group-guid group-guid})

(defn user-type-ace
  [user-type & permissions]
  {:permissions permissions
   :user-type user-type})

(deftest test-get-coll-permitted-group-ids
  (testing "group access"
    (let [acl1 {:aces [(group-ace "read-order" :read :order)
                       (group-ace "order" :order)
                       (group-ace "just-read" :read)
                       (group-ace "order-read" :order :read)
                       (group-ace "no-permit")]
                :catalog-item-identity {:provider-id "PROV1"
                                        :collection-applicable true}}
          acl2 {:aces [(group-ace "group2" :read)]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          acl3 {:aces [(group-ace "group3" :read)]
                :catalog-item-identity {:provider-id "PROV1"
                                        :collection-applicable true}}
          context (context-with-acls acl1 acl2 acl3)]
      (is (= ["read-order" "just-read" "order-read" "group3"]
             (a/get-coll-permitted-group-ids context "PROV1" {:fake-coll "foo"})))))

  (testing "guest access"
    (let [acl1 {:aces [(group-ace "group1" :read)
                       (user-type-ace :guest :order :read)]
                :catalog-item-identity {:provider-id "PROV1"
                                        :collection-applicable true}}
          acl2 {:aces [(user-type-ace :registered :order)]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          context (context-with-acls acl1 acl2)]
      (is (= ["group1" "guest"]
             (a/get-coll-permitted-group-ids context "PROV1" {:fake-coll "foo"})))))

  (testing "registered user access"
    (let [acl1 {:aces [(group-ace "group1" :read)
                       (user-type-ace :registered :order :read)]
                :catalog-item-identity {:provider-id "PROV1"
                                        :collection-applicable true}}
          acl2 {:aces [(user-type-ace :guest :order)]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          context (context-with-acls acl1 acl2)]
      (is (= ["group1" "registered"]
             (a/get-coll-permitted-group-ids context "PROV1" {:fake-coll "foo"})))))

  (testing "registered user access"
    (let [acl1 {:aces [(group-ace "group1" :read)
                       (user-type-ace :registered :order :read)]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          acl2 {:aces [(user-type-ace :guest :read)]
                :catalog-item-identity {:provider-id "PROV2"
                                        :collection-applicable true}}
          context (context-with-acls acl1 acl2)]
      (is (empty? (a/get-coll-permitted-group-ids context "PROV1" {:fake-coll "foo"}))))))