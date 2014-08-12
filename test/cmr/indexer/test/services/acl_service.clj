(ns cmr.indexer.test.services.acl-service
  (:require [clojure.test :refer :all]
            [cmr.indexer.services.acl-service :as a]
            [cmr.umm.collection :as c]))

(defn acl-with-cat-identity
  "Creates an acl with the given catalog item identity"
  [catalog-item-identity]
  {:catalog-item-identity catalog-item-identity})

(defn collection-identifier-with-ids
  "Creates a collection identifier with collection id filters"
  [& dataset-short-name-version-tuples]
  {:collection-ids
   (map (fn [[d s v]]
          {:data-set-id d
           :short-name s
           :version v})
        dataset-short-name-version-tuples)})

(defn collection-identifier-with-restriction-flag
  "Creates a collection identifier with a restriction flag filter"
  [min-v max-v include-undefined]
  {:restriction-flag {:min-value min-v
                      :max-value max-v
                      :include-undefined-value include-undefined}})

(defn collection
  "Helper function for creating a collection"
  ([]
   (collection {}))
  ([{:keys [entry-title short-name version-id access-value]}]
   (c/map->UmmCollection {:entry-title (or entry-title "entry title")
                          :access-value access-value
                          :product (c/map->Product {:short-name (or short-name "short-name")
                                                    :version-id (or version-id "version")})})))

(defn truthy?
  "Returns true if v is a truthy value"
  [v]
  (not (or (nil? v)
           (false? v))))

(deftest truthy-helper-test
  (is (truthy? true))
  (is (truthy? "something"))
  (is (truthy? '()))
  (is (truthy? []))
  (is (not (truthy? false)))
  (is (not (truthy? nil))))

(deftest collection-applicable-acl-test
  (testing "collection-applicable flag false"
    (is (not (a/coll-applicable-acl?
               "prov-guid1"
               (collection)
               (acl-with-cat-identity
                 {:collection-applicable false})))))
  (testing "applicable to all collections in provider"
    (is (a/coll-applicable-acl?
          "prov-guid1" (collection) (acl-with-cat-identity
                                      {:collection-applicable true
                                       :provider-guid "prov-guid1"})))
    (is (not (a/coll-applicable-acl?
               "prov-guid2" (collection) (acl-with-cat-identity
                                           {:collection-applicable true
                                            :provider-guid "prov-guid1"})))))
  (testing "applicable by collection identifier with collection id"
    (let [acl (acl-with-cat-identity
                {:collection-applicable true
                 :provider-guid "prov-guid1"
                 :collection-identifier
                 (collection-identifier-with-ids ["d1" "s1" "v1"]
                                                 ["d2" "s2" "v2"])})]
      (are [applicable? coll]
           (= applicable?
              (truthy? (a/coll-applicable-acl? "prov-guid1" coll acl)))

           ;; dataset id
           true (collection {:entry-title "d1"})
           true (collection {:entry-title "d2"})
           false (collection {:entry-title "d3"})
           ;; short name and version
           true (collection {:short-name "s1" :version-id "v1"})
           true (collection {:short-name "s2" :version-id "v2"})
           false (collection {:short-name "s1" :version-id "v3"})
           false (collection {:short-name "s3" :version-id "v1"})
           false (collection {:short-name "s1" :version-id "v2"})
           false (collection {:short-name "s2" :version-id "v1"}))))
  (testing "applicable by collection identifier with restriction flag filter"
    (are [applicable? restriction-flag-fields coll-access-value]
         (let [acl (acl-with-cat-identity
                     {:collection-applicable true
                      :provider-guid "prov-guid1"
                      :collection-identifier
                      (apply collection-identifier-with-restriction-flag
                             restriction-flag-fields)})]
           (= applicable?
              (truthy? (a/coll-applicable-acl? "prov-guid1"
                                               (collection {:access-value coll-access-value})
                                               acl))))

         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Both min and max specified
         ;; - within bounds (min and max are inclusive)
         true [3.2 10.5 false] 3.2
         true [3.2 10.5 false] 4
         true [3.2 10.5 false] 10.5

         ;; - out of bounds
         false [3.2 10.5 false] 3.19999999
         false [3.2 10.5 false] -12
         false [3.2 10.5 false] 10.51
         ;; - include undefined
         false [3.2 10.5 false] nil
         true [3.2 10.5 true] nil

         ;; Same value specified
         true [15.0 15.0 false] 15.0
         false [15.0 15.0 false] 14.3
         false [15.0 15.0 false] 15.3

         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Only max specified
         ;; - within bounds (min and max are inclusive)
         true [nil 10.5 false] -9
         true [nil 10.5 false] 9
         true [nil 10.5 false] 10.5

         ;; - out of bounds
         false [nil 10.5 false] 10.51

         ;; - include undefined
         false [nil 10.5 false] nil
         true [nil 10.5 true] nil

         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Only min specified
         ;; - within bounds (min and max are inclusive)
         true [3.2 nil false] 3.2
         true [3.2 nil false] 4

         ;; - out of bounds
         false [3.2 nil false] 3.19999999

         ;; - include undefined
         false [3.2 nil false] nil
         true [3.2 nil true] nil

         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Neither min nor max specified
         ;; include undefined must be true in that case
         false [nil nil true] 5
         true [nil nil true] nil))

  (testing "applicable by collection identifier with restriction flag filter and collection identifier"
    (let [collection-identifier (merge (collection-identifier-with-ids
                                         ["d1" "s1" "v1"]
                                         ["d2" "s2" "v2"])
                                       (collection-identifier-with-restriction-flag
                                         1 10 true))
          acl (acl-with-cat-identity
                {:collection-applicable true
                 :provider-guid "prov-guid1"
                 :collection-identifier collection-identifier})]
      (are [applicable? coll]
           (= applicable?
              (truthy? (a/coll-applicable-acl? "prov-guid1" coll acl)))

           ;; matches none
           false (collection)
           ;; matches dataset id but not restriction flag
           false (collection {:entry-title "d1" :access-value 11})
           true (collection {:entry-title "d1" :access-value 10})
           ;; Matches on undefined restriction flag
           true (collection {:entry-title "d1"})

           ;; matches on short name and version but not restriction flag
           false (collection {:short-name "s1"  :version-id "v1" :access-value 11})
           true (collection {:short-name "s1"  :version-id "v1" :access-value 10})))))



