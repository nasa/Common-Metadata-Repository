(ns cmr.umm-spec.test.acl-matchers-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.test.time-util :as tu]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :refer [are2 are3]]
   [cmr.umm-spec.acl-matchers :as a]
   [taoensso.timbre :as t]))

(use-fixtures :each tk/freeze-resume-time-fixture)

(t/set-level! :error)

(defn acl-with-cat-identity
  "Creates an acl with the given catalog item identity"
  [catalog-item-identity]
  {:catalog-item-identity catalog-item-identity})

(defn collection-identifier-with-access-value
  "Creates a collection identifier with a restriction flag filter"
  [min-v max-v include-undefined]
  {:access-value {:min-value min-v
                  :max-value max-v
                  :include-undefined-value include-undefined}})

(defn collection
  "Helper function for creating a collection"
  ([]
   (collection {}))
  ([{:keys [entry-title access-value concept-id]}]
   {:concept-id concept-id
    :EntryTitle (or entry-title "entry title")
    :AccessConstraints {:Value access-value}}))

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
  (testing "applicable by collection identifier with concept id"
    (let [acl (acl-with-cat-identity
                {:collection-applicable true
                 :provider-id "PROV1"
                 :collection-identifier {:concept-ids ["C1" "C2"]}})]
      (are3 [applicable? coll]
           (= applicable?
              (boolean (a/coll-applicable-acl? "PROV1" coll acl)))

           ;; dataset id
           "for C1"
           true
           (collection {:concept-id "C1"})

           "for C2"
           true
           (collection {:concept-id "C2"})

           "for C3"
           false
           (collection {:concept-id "C3"}))))

  (testing "applicable by collection identifier with entry-title"
    (let [acl (acl-with-cat-identity
                {:collection-applicable true
                 :provider-id "PROV1"
                 :collection-identifier {:entry-titles ["d1" "d2"]}})]
      (are [applicable? coll]
           (= applicable?
              (boolean (a/coll-applicable-acl? "PROV1" coll acl)))

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
              (boolean (a/coll-applicable-acl? "PROV1"
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
              (boolean (a/coll-applicable-acl? "PROV1" coll acl)))

           ;; matches none
           false (collection)
           ;; matches dataset id but not restriction flag
           false (collection {:entry-title "d1" :access-value 11})
           true (collection {:entry-title "d1" :access-value 10})
           ;; Matches on undefined restriction flag
           true (collection {:entry-title "d1"})))))


(def now-n
  "The N value for the current time. Uses N values for date times as describd in
  cmr.common.test.time-util."
  10)

(defn temporal-filter
  "Creates a temporal between the two given N times"
  [mask start-n end-n]
  {:start-date (tu/n->date-time start-n)
   :stop-date (tu/n->date-time end-n)
   :mask mask})

(defn coll-w-temporal
  "Helper for creating a collection with temporal at given n and end date times. end-n can be nil
  to simulate a non ending time."
  [start-n end-n]
  {:TemporalExtents
   [{:RangeDateTimes
     [{:BeginningDateTime (tu/n->date-time start-n)
       :EndingDateTime (tu/n->date-time end-n)}]}]})

(deftest collection-applicable-temporal-acl-test
  (tk/set-time-override! (tu/n->date-time now-n))
  (are2 [applicable? tf coll]
        (= applicable? (boolean (a/coll-applicable-acl?
                                  "PROV1" coll
                                  (acl-with-cat-identity
                                    {:collection-applicable true
                                     :provider-id "PROV1"
                                     :collection-identifier
                                     {:temporal tf}}))))

        "Collection with no temporal"
        false (temporal-filter "intersect" 1 3) (collection)

        ;; Intersects Mask
        "Intersects - Collection is exact match"
        true (temporal-filter "intersect" 1 3) (coll-w-temporal 1 3)
        "Contains - Collection start matches range start and end within"
        true (temporal-filter "intersect" 1 4) (coll-w-temporal 1 2)
        "Contains - Collection end matches range end and starts within"
        true (temporal-filter "intersect" 1 4) (coll-w-temporal 2 4)
        "Intersects - Collection contained within temporal range"
        true (temporal-filter "intersect" 2 7) (coll-w-temporal 3 4)
        "Intersects - temporal within collection"
        true (temporal-filter "intersect" 3 4) (coll-w-temporal 2 5)
        "Intersects - collection end = start"
        true (temporal-filter "intersect" 2 7) (coll-w-temporal 1 2)
        "Intersects - collection start = end"
        true (temporal-filter "intersect" 2 7) (coll-w-temporal 7 8)
        "Intersects - collection before with no end date"
        true (temporal-filter "intersect" 2 7) (coll-w-temporal 1 nil)
        "Intersects - collection start and end equal range end"
        true (temporal-filter "intersect" 2 7) (coll-w-temporal 7 7)
        "Intersects - collection start and end equal range start"
        true (temporal-filter "intersect" 2 7) (coll-w-temporal 2 2)
        "Intersects - collection after temporal range"
        false (temporal-filter "intersect" 1 3) (coll-w-temporal 4 5)
        "Intersects - collection before temporal range"
        false (temporal-filter "intersect" 2 4) (coll-w-temporal 0 1)

        ;; Disjoint Mask (The opposite of intersects)
        "Disjoint - Collection is exact match"
        false (temporal-filter "disjoint" 1 3) (coll-w-temporal 1 3)
        "Contains - Collection start matches range start and end within"
        false (temporal-filter "disjoint" 1 4) (coll-w-temporal 1 2)
        "Contains - Collection end matches range end and starts within"
        false (temporal-filter "disjoint" 1 4) (coll-w-temporal 2 4)
        "Disjoint - Collection contained within temporal range"
        false (temporal-filter "disjoint" 2 7) (coll-w-temporal 3 4)
        "Disjoint - temporal within collection"
        false (temporal-filter "disjoint" 3 4) (coll-w-temporal 2 5)
        "Disjoint - collection end = start"
        false (temporal-filter "disjoint" 2 7) (coll-w-temporal 1 2)
        "Disjoint - collection start = end"
        false (temporal-filter "disjoint" 2 7) (coll-w-temporal 7 8)
        "Disjoint - collection before with no end date"
        false (temporal-filter "disjoint" 2 7) (coll-w-temporal 1 nil)
        "Disjoin - collection start and end equal range end"
        false (temporal-filter "disjoint" 2 7) (coll-w-temporal 7 7)
        "Disjoint - collection start and end equal range start"
        false (temporal-filter "disjoint" 2 7) (coll-w-temporal 2 2)
        "Disjoint - collection after temporal range"
        true (temporal-filter "disjoint" 1 3) (coll-w-temporal 4 5)
        "Disjoint - collection before temporal range"
        true (temporal-filter "disjoint" 2 4) (coll-w-temporal 0 1)

        ;; Contains
        "Contains - Collection is exact match"
        true (temporal-filter "contains" 1 3) (coll-w-temporal 1 3)
        "Contains - Collection start matches range start and end within"
        true (temporal-filter "contains" 1 4) (coll-w-temporal 1 2)
        "Contains - Collection end matches range end and starts within"
        true (temporal-filter "contains" 1 4) (coll-w-temporal 2 4)
        "Contains - Collection contained within temporal range"
        true (temporal-filter "contains" 2 7) (coll-w-temporal 3 4)
        "Contains - temporal within collection"
        false (temporal-filter "contains" 3 4) (coll-w-temporal 2 5)
        "Contains - collection end = start"
        false (temporal-filter "contains" 2 7) (coll-w-temporal 1 2)
        "Contains - collection start = end"
        false (temporal-filter "contains" 2 7) (coll-w-temporal 7 8)
        "Contains - collection start and end equal range end"
        true (temporal-filter "contains" 2 7) (coll-w-temporal 7 7)
        "Contains - collection start and end equal range start"
        true (temporal-filter "contains" 2 7) (coll-w-temporal 2 2)
        "Contains - collection before with no end date"
        false (temporal-filter "contains" 2 7) (coll-w-temporal 1 nil)
        "Contains - collection after temporal range"
        false (temporal-filter "contains" 1 3) (coll-w-temporal 4 5)
        "Contains - collection before temporal range"
        false (temporal-filter "contains" 2 4) (coll-w-temporal 0 1)))
