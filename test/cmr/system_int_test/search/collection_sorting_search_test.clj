(ns cmr.system-int-test.search.collection-sorting-search-test
  "Tests searching for collections using basic collection identifiers"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.search.services.messages.common-messages :as msg]))


(use-fixtures :each (ingest/reset-fixture "PROV1" "PROV2"))

(defn make-coll
  "Helper for creating and ingesting a collection"
  [provider entry-title begin end]
  (d/ingest provider
            (dc/collection {:entry-title entry-title
                            :beginning-date-time (d/make-datetime begin)
                            :ending-date-time (d/make-datetime end)})))

(deftest invalid-sort-key-test
  (is (= {:status 422
          :errors [(msg/invalid-sort-key "foo" :collection)]}
         (search/find-refs :collection {:sort-key "foo"}))))

(deftest sorting-test
  (let [c1 (make-coll "PROV1" "et99" 10 20)
        c2 (make-coll "PROV1" "et90" 15 25)
        c3 (make-coll "PROV1" "et80" 20 30)
        c4 (make-coll "PROV1" "et70" 25 35)
        c5 (make-coll "PROV2" "et98" 10 20)
        c6 (make-coll "PROV2" "et91" 15 25)
        c7 (make-coll "PROV2" "et79" 20 30)
        c8 (make-coll "PROV2" "ET75" 25 35)

        c9 (make-coll "PROV1" "et95" nil nil)
        c10 (make-coll "PROV2" "et85" nil nil)
        all-colls [c1 c2 c3 c4 c5 c6 c7 c8 c9 c10]]
    (index/flush-elastic-index)

    (testing "default sorting"
      (is (d/refs-match-order?
            (sort-by (comp str/lower-case :entry-title) all-colls)
            (search/find-refs :collection {}))))

    (testing "Sort by entry title ascending"
      (are [sort-key] (d/refs-match-order?
            (sort-by (comp str/lower-case :entry-title) all-colls)
            (search/find-refs :collection {:sort-key sort-key}))
           "entry_title"
           "+entry_title"
           "dataset_id" ; this is an alias for entry title
           "+dataset_id"))

    (testing "Sort by entry title descending"
      (are [sort-key] (d/refs-match-order?
            (reverse (sort-by (comp str/lower-case :entry-title) all-colls))
            (search/find-refs :collection {:sort-key sort-key}))
           "-entry_title"
           "-dataset_id"))))

