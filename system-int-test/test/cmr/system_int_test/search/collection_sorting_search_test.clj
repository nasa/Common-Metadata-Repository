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


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn make-coll
  "Helper for creating and ingesting a collection"
  [provider entry-title begin end]
  (d/ingest provider
            (dc/collection {:entry-title entry-title
                            :beginning-date-time (d/make-datetime begin)
                            :ending-date-time (d/make-datetime end)})))

(deftest invalid-sort-key-test
  (is (= {:status 400
          :errors [(msg/invalid-sort-key "foo_bar" :collection)]}
         (search/find-refs :collection {:sort-key "foo_bar"})))
  (is (= {:status 400
          :errors [(msg/invalid-sort-key "foo_bar" :collection)]}
         (search/find-refs-with-aql :collection [] {}
                                    {:query-params {:sort-key "foo_bar"}}))))

(defn- sort-order-correct?
  [items sort-key]
  (and
    (d/refs-match-order?
      items
      (search/find-refs :collection {:page-size 20 :sort-key sort-key}))
    (d/refs-match-order?
      items
      (search/find-refs-with-aql :collection [] {}
                                 {:query-params {:page-size 20 :sort-key sort-key}}))))

(deftest sorting-test
  (let [c1 (make-coll "PROV1" "et99" 10 20)
        c2 (make-coll "PROV1" "et90" 14 24)
        c3 (make-coll "PROV1" "et80" 19 30)
        c4 (make-coll "PROV1" "et70" 24 35)

        c5 (make-coll "PROV2" "et98" 9 19)
        c6 (make-coll "PROV2" "et91" 15 25)
        c7 (make-coll "PROV2" "et79" 20 29)
        c8 (make-coll "PROV2" "ET94" 25 36)

        c9 (make-coll "PROV1" "et95" nil nil)
        c10 (make-coll "PROV2" "et85" nil nil)
        c11 (make-coll "PROV1" "et96" 12 nil)
        all-colls [c1 c2 c3 c4 c5 c6 c7 c8 c9 c10 c11]]
    (index/wait-until-indexed)


    (testing "Sort by entry title ascending"
      (let [sorted-colls (sort-by (comp str/lower-case :entry-title) all-colls)]
        (are [sort-key]
             (sort-order-correct? sorted-colls sort-key)
             "entry_title"
             "+entry_title"
             "dataset_id" ; this is an alias for entry title
             "+dataset_id")))

    (testing "Sort by entry title descending"
      (let [sorted-colls (reverse (sort-by (comp str/lower-case :entry-title) all-colls))]
        (are [sort-key]
             (sort-order-correct? sorted-colls sort-key)
             "-entry_title"
             "-dataset_id")))

    (testing "temporal start date"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
           "start_date" [c5 c1 c11 c2 c6 c3 c7 c4 c8 c9 c10]
           "-start_date" [c8 c4 c7 c3 c6 c2 c11 c1 c5 c9 c10]))

    (testing "temporal end date"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
           "end_date" [c5 c1 c2 c6 c7 c3 c4 c8 c9 c10 c11]
           "-end_date" [c8 c4 c3 c7 c6 c2 c1 c5 c9 c10 c11]))

    (testing "revision date"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
           "revision_date" [c1 c2 c3 c4 c5 c6 c7 c8 c9 c10 c11]
           "-revision_date" [c11 c10 c9 c8 c7 c6 c5 c4 c3 c2 c1]))))

(deftest default-sorting-test
  (let [c1 (make-coll "PROV1" "et99" 10 20)
        c2 (make-coll "PROV2" "et99" 14 24)
        c3 (make-coll "PROV2" "et80" 19 30)
        c4 (make-coll "PROV1" "et80" 24 35)
        all-colls [c1 c2 c3 c4]]
    (index/wait-until-indexed)
    (let [sorted-colls (sort-by (juxt (comp str/lower-case :entry-title)
                                      (comp str/lower-case :provider-id)) all-colls)]
      (is (d/refs-match-order?
            sorted-colls
            (search/find-refs :collection {:page-size 20})))
      (is (d/refs-match-order?
            sorted-colls
            (search/find-refs-with-aql :collection [] {}
                                       {:query-params {:page-size 20}}))))))

(deftest multiple-sort-key-test
  (let [c1 (make-coll "PROV1" "et10" 10 nil)
        c2 (make-coll "PROV1" "et20" 10 nil)
        c3 (make-coll "PROV1" "et30" 10 nil)
        c4 (make-coll "PROV1" "et40" 10 nil)

        c5 (make-coll "PROV2" "et10" 20 nil)
        c6 (make-coll "PROV2" "et20" 20 nil)
        c7 (make-coll "PROV2" "et30" 20 nil)
        c8 (make-coll "PROV2" "et40" 20 nil)]
    (index/wait-until-indexed)

    (are [sort-key items]
         (sort-order-correct? items sort-key)
         ["entry_title" "start_date"] [c1 c5 c2 c6 c3 c7 c4 c8]
         ["entry_title" "-start_date"] [c5 c1 c6 c2 c7 c3 c8 c4]
         ["start_date" "entry_title"] [c1 c2 c3 c4 c5 c6 c7 c8]
         ["start_date" "-entry_title"] [c4 c3 c2 c1 c8 c7 c6 c5]
         ["-start_date" "entry_title"] [c5 c6 c7 c8 c1 c2 c3 c4]

         ;; Tests provider sorting for collections
         ["provider" "-entry_title"] [c4 c3 c2 c1 c8 c7 c6 c5]
         ["-provider" "-entry_title"] [c8 c7 c6 c5 c4 c3 c2 c1])))

(deftest collection-platform-sorting-test
  (let [make-collection (fn [& platforms]
                          (d/ingest "PROV1"
                                    (dc/collection
                                      {:platforms (map dc/platform platforms)})))
        c1 (make-collection "c10" "c41")
        c2 (make-collection "c20" "c51")
        c3 (make-collection "c30")
        c4 (make-collection "c40")
        c5 (make-collection "c50")]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         ;; Descending sorts by the min value of a multi value fields
         "platform" [c1 c2 c3 c4 c5]
         ;; Descending sorts by the max value of a multi value fields
         "-platform" [c2 c5 c1 c4 c3])))

(deftest collection-instrument-sorting-test
  (let [make-collection (fn [& instruments]
                          (d/ingest "PROV1"
                                    (dc/collection
                                      {:platforms [(apply dc/platform (d/unique-str "platform")
                                                          "long-name"
                                                          nil
                                                          (map #(dc/instrument {:short-name %})
                                                               instruments))]})))
        c1 (make-collection "c10" "c41")
        c2 (make-collection "c20" "c51")
        c3 (make-collection "c30")
        c4 (make-collection "c40")
        c5 (make-collection "c50")]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         ;; Descending sorts by the min value of a multi value fields
         "instrument" [c1 c2 c3 c4 c5]
         ;; Descending sorts by the max value of a multi value fields
         "-instrument" [c2 c5 c1 c4 c3])))

(deftest collection-sensor-sorting-test
  (let [make-collection (fn [& sensors]
                          (d/ingest
                            "PROV1"
                            (dc/collection
                              {:platforms [(dc/platform
                                             (d/unique-str "platform")
                                             "long-name"
                                             nil
                                             (dc/instrument
                                               {:short-name (d/unique-str "instrument")
                                                :sensors (map #(dc/sensor {:short-name %}) sensors)}))]})))
        c1 (make-collection "c10" "c41")
        c2 (make-collection "c20" "c51")
        c3 (make-collection "c30")
        c4 (make-collection "c40")
        c5 (make-collection "c50")]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         ;; Descending sorts by the min value of a multi value fields
         "sensor" [c1 c2 c3 c4 c5]
         ;; Descending sorts by the max value of a multi value fields
         "-sensor" [c2 c5 c1 c4 c3])))
