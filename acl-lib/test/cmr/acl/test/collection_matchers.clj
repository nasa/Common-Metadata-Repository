(ns cmr.acl.test.collection-matchers
  (:require [clojure.test :refer :all]
            [cmr.acl.collection-matchers :as a]))

(defn acl-with-cat-identity
  "Creates an acl with the given catalog item identity"
  [catalog-item-identity]
  {:catalog-item-identity catalog-item-identity})

(defn collection-identifier-with-access-value
  "Creates a collection identifier with a restriction flag filter"
  [min-v max-v include-undefined]
  {:access-value {:min-value min-v
                  :max-value max-v
                  :include-undefined include-undefined}})

(defn collection
  "Helper function for creating a collection"
  ([]
   (collection {}))
  ([{:keys [entry-title access-value]}]
   {:entry-title (or entry-title "entry title")
    :access-value access-value}))

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
               "PROV1"
               (collection)
               (acl-with-cat-identity
                 {:collection-applicable false})))))
  (testing "applicable to all collections in provider"
    (is (a/coll-applicable-acl?
          "PROV1" (collection) (acl-with-cat-identity
                                      {:collection-applicable true
                                       :provider-id "PROV1"})))
    (is (not (a/coll-applicable-acl?
               "PROV2" (collection) (acl-with-cat-identity
                                           {:collection-applicable true
                                            :provider-id "PROV1"})))))
  (testing "applicable by collection identifier with collection id"
    (let [acl (acl-with-cat-identity
                {:collection-applicable true
                 :provider-id "PROV1"
                 :collection-identifier {:entry-titles ["d1" "d2"]}})]
      (are [applicable? coll]
           (= applicable?
              (truthy? (a/coll-applicable-acl? "PROV1" coll acl)))

           ;; dataset id
           true (collection {:entry-title "d1"})
           true (collection {:entry-title "d2"})
           false (collection {:entry-title "d3"}))))
  (testing "applicable by collection identifier with access value filter"
    (are [applicable? access-value-fields coll-access-value]
         (let [acl (acl-with-cat-identity
                     {:collection-applicable true
                      :provider-id "PROV1"
                      :collection-identifier
                      (apply collection-identifier-with-access-value
                             access-value-fields)})]
           (= applicable?
              (truthy? (a/coll-applicable-acl? "PROV1"
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

  (testing "applicable by collection identifier with access value filter and collection identifier"
    (let [collection-identifier (merge {:entry-titles ["d1" "d2"]}
                                       (collection-identifier-with-access-value
                                         1 10 true))
          acl (acl-with-cat-identity
                {:collection-applicable true
                 :provider-id "PROV1"
                 :collection-identifier collection-identifier})]
      (are [applicable? coll]
           (= applicable?
              (truthy? (a/coll-applicable-acl? "PROV1" coll acl)))

           ;; matches none
           false (collection)
           ;; matches dataset id but not restriction flag
           false (collection {:entry-title "d1" :access-value 11})
           true (collection {:entry-title "d1" :access-value 10})
           ;; Matches on undefined restriction flag
           true (collection {:entry-title "d1"})))))



