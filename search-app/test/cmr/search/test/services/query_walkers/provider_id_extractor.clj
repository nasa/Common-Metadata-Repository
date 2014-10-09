(ns cmr.search.test.services.query-walkers.provider-id-extractor
  (:require [clojure.test :refer :all]
            [cmr.search.test.models.helpers :refer :all]
            [cmr.search.services.query-walkers.provider-id-extractor :as p]
            [cmr.search.models.query :as q]))

(defn provider
  [provider-id]
  (q/string-condition :provider provider-id))

(defn concept-id
  [concept-id]
  (q/string-condition :concept-id concept-id))

(defn collection-concept-id
  [concept-id]
  (q/string-condition :collection-concept-id concept-id))

(defn providers
  [& provider-id]
  (q/string-conditions :provider provider-id))

(defn concept-ids
  [& concept-id]
  (q/string-conditions :concept-id concept-id))

(defn collection-concept-ids
  [& concept-id]
  (q/string-conditions :collection-concept-id concept-id))

(deftest extract-provider-ids-test
  (are [condition expected-provider-ids]
       (= expected-provider-ids
          (p/extract-provider-ids (q/query {:condition condition})))

       (provider "a") #{"a"}
       (providers "a" "b") #{"a" "b"}
       (or-conds (provider "a") (provider "b")) #{"a" "b"}
       (or-conds (provider "a") (other)) #{}
       (and-conds (provider "a") (other)) #{"a"}

       ;; Nested conditions
       (and-conds (provider "c") (or-conds (provider "a") (other))) #{"c"}

       ;; Concept types

       (concept-id "G5-a") #{"a"}
       (concept-id "C5-a") #{"a"}
       (collection-concept-id "C5-a") #{"a"}
       (concept-ids "G5-a" "G7-b") #{"a" "b"}
       (collection-concept-ids "C5-a" "C7-b") #{"a" "b"}))