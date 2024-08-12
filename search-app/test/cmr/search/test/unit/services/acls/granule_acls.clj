(ns cmr.search.test.unit.services.acls.granule-acls
  (:require [clojure.test :refer :all]
            [cmr.common-app.data.collections-for-gran-acls-by-concept-id-cache :as cmn-coll-for-gran-acls-caches]
            [cmr.common.hash-cache :as hash-cache]
            [cmr.redis-utils.config :as redis-config]
            [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
            [cmr.common.util :refer [are3]]
            [cmr.redis-utils.test.test-util :as test-util]
            [cmr.search.services.acls.granule-acls :as g]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(defn access-value
  "Creates an access value filter"
  ([min-v max-v]
   (access-value min-v max-v nil))
  ([min-v max-v include-undefined?]
   {:include-undefined-value include-undefined?
    :min-value min-v
    :max-value max-v}))

(defn coll-id
  "Creates an ACL collection identifier"
  ([entry-titles]
   (coll-id entry-titles nil))
  ([entry-titles access-value-filter]
   {:entry-titles entry-titles
    :access-value access-value-filter}))

(defn gran-id
  "Creates an ACL granule identifier"
  [access-value-filter]
  {:access-value access-value-filter})

(defn make-acl
  "Creates an ACL for testing."
  ([provider-id]
   (make-acl provider-id nil))
  ([provider-id coll-identifier]
   (make-acl provider-id coll-identifier nil))
  ([provider-id coll-identifier gran-identifier]
   {:catalog-item-identity
    {:provider-id provider-id
     :collection-identifier coll-identifier
     :granule-identifier gran-identifier}}))

(defn concept
  "Creates an example concept for testing."
  ([prov collection-concept-id]
   (concept prov collection-concept-id nil))
  ([prov collection-concept-id access-value]
   {:provider-id prov
    :access-value access-value
    :concept-type :granule
    :collection-concept-id collection-concept-id}))

(defn collection
  "Creates a collection that would be stored in the collection cache."
  ([concept-id entry-title]
   (collection concept-id entry-title nil))
  ([concept-id entry-title access-value]
   {:EntryTitle entry-title
    :concept-id concept-id
    :AccessConstraints {:Value access-value}}))

(use-fixtures :each test-util/embedded-redis-server-fixture)

(defn create-collection-for-gran-acls-test-entry
  [provider-id entry-title coll-concept-id access-value]
  (let [collection {:concept-type :collection,
                    :provider-id provider-id,
                    :EntryTitle entry-title,
                    :AccessConstraints {:Value access-value},
                    :TemporalExtents nil,
                    :concept-id coll-concept-id}]
    (if (not (nil? access-value))
      (assoc collection :AccessConstraints {:Value access-value}))
    collection))

(deftest acl-match-granule-concept-test
  (testing "provider ids"
    (is (not (g/acl-match-concept? {} (make-acl "P1") (concept "P2" "C2-P2"))) "not same provider failed")
    (is (g/acl-match-concept? {} (make-acl "P1") (concept "P1" "C2-P1")) "is same provider failed"))

  (let [coll-by-concept-id-cache-key cmn-coll-for-gran-acls-caches/coll-by-concept-id-cache-key
        coll-by-concept-id-cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-concept-id-cache-key]
                                                                            :read-connection (redis-config/redis-read-conn-opts)
                                                                            :primary-connection (redis-config/redis-conn-opts)})
        _ (hash-cache/reset coll-by-concept-id-cache coll-by-concept-id-cache-key)
        context {:system {:caches {coll-by-concept-id-cache-key coll-by-concept-id-cache}}}]

    (hash-cache/set-value coll-by-concept-id-cache coll-by-concept-id-cache-key "C1-P1" (create-collection-for-gran-acls-test-entry "P1" "coll1" "C1-P1" nil))
    (hash-cache/set-value coll-by-concept-id-cache coll-by-concept-id-cache-key "C2-P1" (create-collection-for-gran-acls-test-entry "P1" "coll2" "C2-P1" 1))
    (hash-cache/set-value coll-by-concept-id-cache coll-by-concept-id-cache-key "C3-P1" (create-collection-for-gran-acls-test-entry "P1" "coll3" "C3-P1" 2))
    (hash-cache/set-value coll-by-concept-id-cache coll-by-concept-id-cache-key "C4-P2" (create-collection-for-gran-acls-test-entry "P2" "coll1" "C4-P2" nil))
    (hash-cache/set-value coll-by-concept-id-cache coll-by-concept-id-cache-key "C5-P2" (create-collection-for-gran-acls-test-entry "P2" "coll2" "C5-P2" 1))

    (testing "collection identifier"
      (are3 [entry-titles access-value-args collection-concept-id should-match?]
            (let [access-value-filter (when access-value-args (apply access-value access-value-args))
                  coll-identifier (coll-id entry-titles access-value-filter)
                  acl (make-acl "P1" coll-identifier)
                  granule (concept "P1" collection-concept-id)
                  matches? (not (not (g/acl-match-concept? context acl granule)))]
              (is (= should-match? matches?) "match failed"))

            "all good"
            ["coll1"] nil "C1-P1" true

            ;; collection doesn't exist
            "foo"
            ["foo"] nil "C1-P1" false
            "coll2"
            ["foo" "coll2"] nil "C1-P1" false
            "found with coll1"
            ["foo" "coll1"] nil "C1-P1" true

            ;;access value filter
            ;; this is checked more completely in collection matcher tests
            "c2 check"
            nil [1 2] "C2-P1" true
            "c1 check"
            nil [1 2] "C1-P1" false

            "c1 check"
            ["foo" "coll2" "coll1"] [1 2] "C1-P1" false
            "c2 check"
            ["foo" "coll2" "coll1"] [1 2] "C2-P1" true))

    (testing "collection identifier and granule access-value"
      (are3
       [entry-titles access-value-args collection-concept-id gran-access-value should-match?]
       (let [granule-identifier (gran-id (apply access-value access-value-args))
             coll-identifier (coll-id entry-titles nil)
             acl (make-acl "P1" coll-identifier granule-identifier)
             granule (concept "P1" collection-concept-id gran-access-value)
             matches? (not (not (g/acl-match-concept? context acl granule)))]
         (is (= should-match? matches?) "match failed"))

       "- access value is good"
       ["coll1"] [1 2] "C1-P1" 1 true

       "- access value outside range"
       ["coll1"] [1 2] "C1-P1" 3 false

       "- different collection"
       ["coll1"] [1 2] "C2-P1" 1 false

       "- collection does not exist"
       ["foo"] [1 2] "C1-P1" 1 false)))

  (testing "granule access value"
    (are3
     [access-value-args gran-value should-match?]
     (let [acl (make-acl "P1" nil (gran-id (apply access-value access-value-args)))
           granule (concept "P1" "C1-P1" gran-value)
           ;; not not makes truthy values true or false
           matches? (not (not (g/acl-match-concept? {} acl granule)))]
       (is (= should-match? matches?)) "match failed")

     "include undefined"
     [1 2 true] nil true

     "no value"
     [nil nil true] 3 false
     "outside"
     [1 2 true] 3 false
     "on edge"
     [1 2 true] 2 true

     "just min - 7.0"
     [7 nil] 7.0 true
     "min - 7"
     [7 nil] 7 true
     "min - just inside"
     [7 nil] 7.1 true
     "min - well over"
     [7 nil] 8 true
     "min - just under"
     [7 nil] 6.999 false
     "min - way under"
     [7 nil] -7.999 false

     ;;just max
     "Right on max"
     [nil 8] 8.0 true
     "on max"
     [nil 8] 8 true
     "just over max"
     [nil 8] 8.1 false
     "under max"
     [nil 8] 7 true
     "just under max"
     [nil 8] 7.999 true
     "way under max"
     [nil 8] -8.1 true

     ;; min and max
     "on min"
     [5 7] 5 true
     "exact on min"
     [5 7] 5.0 true
     "on max"
     [5 7] 7.0 true
     "between"
     [5 7] 6 true
     "just between, min side"
     [5 7] 5.5 true
     "just over max"
     [5 7] 7.2 false
     "just under min"
     [5 7] 4.2 false
     "way under min"
     [5 7] -7.2 false)))
