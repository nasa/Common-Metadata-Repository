(ns cmr.search.test.services.collection-concept-id-extractor
  (:require [clojure.test :refer :all]
            [cmr.search.services.collection-concept-id-extractor :as c]
            [cmr.search.models.query :as q]))


(defn and-conds
  [& conds]
  (q/and-conds conds))

(defn or-conds
  [& conds]
  (q/or-conds conds))

(defn collection-concept-id
  [concept-id]
  (q/string-condition :collection-concept-id concept-id))

(defn collection-concept-ids
  [& concept-id]
  (q/string-conditions :collection-concept-id concept-id))

(defn other
  []
  (q/string-conditions :foo "foo"))

(deftest extract-collection-concept-ids-test
  (are [condition expected-ids]
       (= expected-ids
          (c/extract-collection-concept-ids (q/query {:condition condition})))

       (collection-concept-id "a") #{"a"}
       (and-conds (collection-concept-id "a") (collection-concept-id "b")) #{"a" "b"}
       (or-conds (collection-concept-id "a") (collection-concept-id "b")) #{"a" "b"}
       (or-conds (collection-concept-id "a") (other)) #{}
       (and-conds (collection-concept-id "a") (other)) #{"a"}

       ;; Nested conditions
       (and-conds (collection-concept-id "c") (and-conds (collection-concept-id "a") (collection-concept-id "b"))) #{"a" "b" "c"}
       (and-conds (collection-concept-id "c") (or-conds (collection-concept-id "a") (other))) #{"c"}

       ;; multiple
       (collection-concept-ids "C5-a" "C7-b") #{"C5-a" "C7-b"}))