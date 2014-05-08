(ns cmr.system-int-test.search.granule-sorting-search-test
  "Tests searching for collections using basic collection identifiers"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.search.services.messages.common-messages :as msg]))


(use-fixtures :each (ingest/reset-fixture "PROV1" "PROV2"))

(deftest invalid-sort-key-test
  (is (= {:status 422
          :errors [(msg/invalid-sort-key "foo" :granule)]}
         (search/find-refs :granule {:sort-key "foo"}))))

(deftest coll-identifier-sorting-test
  (let [make-gran (fn [provider entry-title short-name version]
                    (let [coll (d/ingest provider
                                         (dc/collection {:entry-title entry-title
                                                         :short-name short-name
                                                         :version-id version}))]
                      (d/ingest provider (dg/granule coll {}))))
        g1 (make-gran "PROV1" "et10" "SN45" "v10")
        g2 (make-gran "PROV1" "et20" "sn35" "v20")
        g3 (make-gran "PROV1" "et30" "sn25" "v30")
        g4 (make-gran "PROV1" "et40" "sn15" "v40")
        g5 (make-gran "PROV2" "et15" "sn40" "v15")
        g6 (make-gran "PROV2" "et25" "sn30" "v25")
        g7 (make-gran "PROV2" "et35" "sn20" "v35")
        g8 (make-gran "PROV2" "ET45" "sn10" "v45")]
    (index/flush-elastic-index)

    (are [sort-key items]
         (d/refs-match-order? items
                              (search/find-refs :granule {:page-size 20
                                                          :sort-key sort-key}))
         "entry_title" [g1 g5 g2 g6 g3 g7 g4 g8]
         "-entry_title" (reverse [g1 g5 g2 g6 g3 g7 g4 g8])
         "dataset_id" [g1 g5 g2 g6 g3 g7 g4 g8]
         "-dataset_id" (reverse [g1 g5 g2 g6 g3 g7 g4 g8])
         "short_name" [g8 g4 g7 g3 g6 g2 g5 g1]
         "-short_name" (reverse [g8 g4 g7 g3 g6 g2 g5 g1])
         "version" [g1 g5 g2 g6 g3 g7 g4 g8]
         "-version" (reverse [g1 g5 g2 g6 g3 g7 g4 g8])

         ;; Multiple keys and provider
         ["provider" "version"] [g1 g2 g3 g4 g5 g6 g7 g8]
         ["provider" "-version"] [g4 g3 g2 g1 g8 g7 g6 g5]
         ["-provider" "version"] [g5 g6 g7 g8 g1 g2 g3 g4])))


(deftest temporal-sorting-test
  (let [make-coll (fn [provider begin end]
                    (d/ingest provider
                              (dc/collection {:beginning-date-time (d/make-datetime begin)
                                              :ending-date-time (d/make-datetime end)})))
        make-gran (fn [coll begin end]
                    (d/ingest (:provider-id coll)
                              (dg/granule coll {:beginning-date-time (d/make-datetime begin)
                                                :ending-date-time (d/make-datetime end)})))
        c1 (make-coll "PROV1" 1 200)
        c2 (make-coll "PROV2" 1 200)

        g1 (make-gran c1 10 20)
        g2 (make-gran c1 14 24)
        g3 (make-gran c1 19 30)
        g4 (make-gran c1 24 35)
        g5 (make-gran c2 9 19)
        g6 (make-gran c2 15 25)
        g7 (make-gran c2 20 29)
        g8 (make-gran c2 25 36)
        g9 (make-gran c1 nil nil)
        g10 (make-gran c2 nil nil)
        g11 (make-gran c1 12 nil)
        g12 (make-gran c2 nil 22)]
    (index/flush-elastic-index)

    (testing "default sorting is by provider id and start date"
      (is (d/refs-match-order?
            [g1 g11 g2 g3 g4 g9 g5 g6 g7 g8 g10 g12]
            (search/find-refs :granule {:page-size 20}))))

    (testing "temporal start date"
      (are [sort-key items] (d/refs-match-order?
                              items
                              (search/find-refs :granule {:page-size 20
                                                          :sort-key sort-key}))
           "start_date" [g5 g1 g11 g2 g6 g3 g7 g4 g8 g9 g10 g12]
           "-start_date" [g8 g4 g7 g3 g6 g2 g11 g1 g5 g9 g10 g12]))

    (testing "temporal end date"
      (are [sort-key items] (d/refs-match-order?
                              items
                              (search/find-refs :granule {:page-size 20
                                                          :sort-key sort-key}))
           "end_date" [g5 g1 g12 g2 g6 g7 g3 g4 g8 g9 g10 g11]
           "-end_date" [g8 g4 g3 g7 g6 g2 g12 g1 g5 g9 g10 g11]))))
