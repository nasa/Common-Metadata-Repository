(ns cmr.search.test.services.acls.granule-acls
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as cache]
            [cmr.search.services.acls.granule-acls :as g]
            [cmr.search.services.acls.collections-cache :as coll-cache]
            [cmr.common.cache.in-memory-cache :as mem-cache]))

(defn access-value
  "Creates an access value filter"
  ([min-v max-v]
   (access-value min-v max-v nil))
  ([min-v max-v include-undefined?]
   {:include-undefined include-undefined?
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
   {:entry-title entry-title
    :concept-id concept-id
    :access-value access-value}))

(defn context-with-cached-collections
  "Creates a context with the specified collections in the collections cache"
  [collections]
  (let [coll-cache (mem-cache/create-in-memory-cache
                     :default
                     {:collections {:by-concept-id
                                    (into {} (for [{:keys [concept-id] :as coll} collections]
                                               [concept-id coll]))}})]
    {:system
     {:caches
      {coll-cache/cache-key coll-cache}}}))

(deftest acl-match-granule-concept-test
  (testing "provider ids"
    (is (not (g/acl-match-concept? {} (make-acl "P1") (concept "P2" "C2-P2"))))
    (is (g/acl-match-concept? {} (make-acl "P1") (concept "P1" "C2-P1"))))

  (let [collections [["C1-P1" "coll1" nil]
                     ["C2-P1" "coll2" 1]
                     ["C3-P1" "coll3" 2]
                     ["C4-P2" "coll1" nil]
                     ["C5-P2" "coll2" 1]]
        context (context-with-cached-collections
                  (for [coll-args collections]
                    (apply collection coll-args)))]
    (testing "collection identifier"
      (are [entry-titles access-value-args collection-concept-id should-match?]
           (let [access-value-filter (when access-value-args (apply access-value access-value-args))
                 coll-identifier (coll-id entry-titles access-value-filter)
                 acl (make-acl "P1" coll-identifier)
                 granule (concept "P1" collection-concept-id)
                 matches? (not (not (g/acl-match-concept? context acl granule)))]
             (when-not (= should-match? matches?)
               (println "context" (pr-str context))
               (println "acl" (pr-str acl))
               (println "granule" (pr-str granule)))
             (= should-match? matches?))

           ["coll1"] nil "C1-P1" true

           ;; collection doesn't exist
           ["foo"] nil "C1-P1" false
           ["foo" "coll2"] nil "C1-P1" false
           ["foo" "coll1"] nil "C1-P1" true

           ;;access value filter
           ;; this is checked more completely in collection matcher tests
           nil [1 2] "C2-P1" true
           nil [1 2] "C1-P1" false

           ["foo" "coll2" "coll1"] [1 2] "C1-P1" false
           ["foo" "coll2" "coll1"] [1 2] "C2-P1" true))

    (testing "collection identifier and granule access-value"
      (are [entry-titles access-value-args collection-concept-id gran-access-value should-match?]
           (let [granule-identifier (gran-id (apply access-value access-value-args))
                 coll-identifier (coll-id entry-titles nil)
                 acl (make-acl "P1" coll-identifier granule-identifier)
                 granule (concept "P1" collection-concept-id gran-access-value)
                 matches? (not (not (g/acl-match-concept? context acl granule)))]
             (when-not (= should-match? matches?)
               (println "context" (pr-str context))
               (println "acl" (pr-str acl))
               (println "granule" (pr-str granule)))
             (= should-match? matches?))

           ["coll1"] [1 2] "C1-P1" 1 true

           ;; access value outside range
           ["coll1"] [1 2] "C1-P1" 3 false

           ;; different collection
           ["coll1"] [1 2] "C2-P1" 1 false

           ;; collection doesn't exist
           ["foo"] [1 2] "C1-P1" 1 false)))


  (testing "granule access value"
    (are [access-value-args gran-value should-match?]
         (let [acl (make-acl "P1" nil (gran-id (apply access-value access-value-args)))
               granule (concept "P1" "C1-P1" gran-value)
               ;; not not makes truthy values true or false
               matches? (not (not (g/acl-match-concept? {} acl granule)))]
           (when-not (= should-match? matches?)
             (println "acl" (pr-str acl))
             (println "granule" (pr-str granule)))
           (= should-match? matches?))

         ;; include undefined
         [1 2 true] nil true
         [nil nil true] 3 false
         [1 2 true] 3 false
         [1 2 true] 2 true

         ;; just min
         [7 nil] 7.0 true
         [7 nil] 7 true
         [7 nil] 7.1 true
         [7 nil] 8 true
         [7 nil] 6.999 false
         [7 nil] -7.999 false

         ;; just max
         [nil 8] 8.0 true
         [nil 8] 8 true
         [nil 8] 8.1 false
         [nil 8] 7 true
         [nil 8] 7.999 true
         [nil 8] -8.1 true

         ;; min and max
         [5 7] 5 true
         [5 7] 5.0 true
         [5 7] 7.0 true
         [5 7] 6 true
         [5 7] 5.5 true
         [5 7] 7.2 false
         [5 7] 4.2 false
         [5 7] -7.2 false)))