(ns cmr.system-int-test.search.granule-sorting-search-test
  "Tests searching for collections using basic collection identifiers"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
            [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common-app.services.search.messages :as cmsg]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest invalid-sort-key-test
  (is (= {:status 400
          :errors [(cmsg/invalid-sort-key "foo_bar" :granule)]}
         (search/find-refs :granule {:sort-key "foo_bar"})))
  (is (= {:status 400
          :errors [(cmsg/invalid-sort-key "foo_bar" :granule)]}
         (search/find-refs-with-aql :granule [] {}
                                    {:query-params {:sort-key "foo_bar"}}))))

(defn- sort-order-correct?
  [items sort-key]
  (and
    (d/refs-match-order?
      items
      (search/find-refs :granule {:page-size 20 :sort-key sort-key}))
    (d/refs-match-order?
      items
      (search/find-refs-with-aql :granule [] {}
                                 {:query-params {:page-size 20 :sort-key sort-key}}))))

(deftest granule-identifier-revision-date-sorting-test
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        make-gran (fn [granule-ur producer-gran-id]
                    (d/ingest "PROV1"
                              (dg/granule-with-umm-spec-collection
                               coll
                               (:concept-id coll)
                               {:granule-ur granule-ur
                                :producer-gran-id producer-gran-id})))
        g1 (make-gran "gur10" nil)
        g2 (make-gran "gur20" "pg50")
        g3 (make-gran "gur30" "pg40")
        g4 (make-gran "gur40" "pg30")
        g5 (make-gran "gur50" nil)]
    (index/wait-until-indexed)
    (testing "granule identifiers sorting"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
           "granule_ur" [g1 g2 g3 g4 g5]
           "-granule_ur" (reverse [g1 g2 g3 g4 g5])

           "producer_granule_id" [g4 g3 g2 g1 g5]
           "-producer_granule_id" [g2 g3 g4 g1 g5]

           "readable_granule_name" [g1 g5 g4 g3 g2]
           "-readable_granule_name" (reverse [g1 g5 g4 g3 g2])))
    (testing "granule revision date sorting"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
           "revision_date" [g1 g2 g3 g4 g5]
           "-revision_date" (reverse [g1 g2 g3 g4 g5])))))

(deftest granule-campaign-sorting-test
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                         {:Projects (data-umm-cmn/projects "c10" "c20" "c30" "c40" "c50" "c41" "c51")}))
        make-gran (fn [& campaigns]
                    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:project-refs campaigns})))
        g1 (make-gran "c10" "c41")
        g2 (make-gran "c20" "c51")
        g3 (make-gran "c30")
        g4 (make-gran "c40")
        g5 (make-gran "c50")]
    (index/wait-until-indexed)
    (testing "granule campaign sorting"
      (are [sort-key items]
           (sort-order-correct? items sort-key)

           ;; Descending sorts by the min value of a multi value fields
           "campaign" [g1 g2 g3 g4 g5]
           ;; Descending sorts by the max value of a multi value fields
           "-campaign" [g2 g5 g1 g4 g3]
           "project" [g1 g2 g3 g4 g5]
           "-project" [g2 g5 g1 g4 g3]))))

(deftest granule-numeric-sorting-test
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        make-gran (fn [number]
                    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection
                                       coll
                                       (:concept-id coll)
                                       {:size number :cloud-cover number})))
        g1 (make-gran 20)
        g2 (make-gran 30)
        g3 (make-gran 10)
        g4 (make-gran nil)
        g5 (make-gran 25)]
    (index/wait-until-indexed)

    (testing "granule numeric field sorting"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
           "data_size" [g3 g1 g5 g2 g4]
           "-data_size" [g2 g5 g1 g3 g4]
           "cloud_cover" [g3 g1 g5 g2 g4]
           "-cloud_cover" [g2 g5 g1 g3 g4]))))

(deftest coll-identifier-sorting-test
  (let [make-gran (fn [provider entry-title short-name version]
                    (let [coll (d/ingest-umm-spec-collection provider
                                (data-umm-c/collection {:EntryTitle entry-title
                                                        :ShortName short-name
                                                        :Version version}))]
                      (d/ingest provider (dg/granule-with-umm-spec-collection coll (:concept-id coll) {}))))
        g1 (make-gran "PROV1" "et10" "SN45" "v10")
        g2 (make-gran "PROV1" "et20" "sn35" "v20")
        g3 (make-gran "PROV1" "et30" "sn25" "v30")
        g4 (make-gran "PROV1" "et40" "sn15" "v40")
        g5 (make-gran "PROV2" "et15" "sn40" "v15")
        g6 (make-gran "PROV2" "et25" "sn30" "v25")
        g7 (make-gran "PROV2" "et35" "sn20" "v35")
        g8 (make-gran "PROV2" "ET45" "sn10" "v45")]
    (index/wait-until-indexed)

    (testing "granule sorting by collection identifiers"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
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
           ["-provider" "version"] [g5 g6 g7 g8 g1 g2 g3 g4]))))


(deftest temporal-sorting-test
  (let [make-coll (fn [n provider begin end]
                    (d/ingest-umm-spec-collection
                     provider (data-umm-c/collection
                     n
                     {:TemporalExtents [(data-umm-cmn/temporal-extent {
                      :beginning-date-time (d/make-datetime begin)
                      :ending-date-time (d/make-datetime end)
                      :ends-at-present? true})]})))
        make-gran (fn [coll begin end]
                    (d/ingest (:provider-id coll)
                              (dg/granule-with-umm-spec-collection
                               coll
                               (:concept-id coll)
                               {:beginning-date-time (d/make-datetime begin)
                                :ending-date-time (d/make-datetime end)})))
        c1 (make-coll 1 "PROV1" 1 200)
        c2 (make-coll 2 "PROV2" 1 200)

        g1 (make-gran c1 10 20)
        g2 (make-gran c1 14 24)
        g3 (make-gran c1 19 30)
        g4 (make-gran c1 24 35)
        g5 (make-gran c2 9 19)
        g6 (make-gran c2 15 25)
        g7 (make-gran c2 20 29)
        g8 (make-gran c2 25 36)
        g11 (make-gran c1 12 nil)]
    (index/wait-until-indexed)

    (testing "default sorting is by provider id and start date"
      (are [items]
           (and (d/refs-match-order? items
                                     (search/find-refs :granule {:page-size 20}))
                (d/refs-match-order? items
                                     (search/find-refs-with-aql :granule [] {}
                                                                {:query-params {:page-size 20}})))
           [g1 g11 g2 g3 g4 g5 g6 g7 g8]))

    (testing "temporal start date"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
           "start_date" [g5 g1 g11 g2 g6 g3 g7 g4 g8]
           "-start_date" [g8 g4 g7 g3 g6 g2 g11 g1 g5]))

    (testing "temporal end date"
      (are [sort-key items]
           (sort-order-correct? items sort-key)
           "end_date" [g5 g1 g2 g6 g7 g3 g4 g8 g11]
           "-end_date" [g8 g4 g3 g7 g6 g2 g1 g5 g11]))))

(deftest granule-platform-sorting-test
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms
                               (map #(data-umm-cmn/platform {:ShortName %})
                                    ["c10" "c41" "c20" "c51" "c30" "c40" "c50"])}))
        make-gran (fn [& platforms]
                    (d/ingest "PROV1"
                              (dg/granule-with-umm-spec-collection
                               coll
                               (:concept-id coll)
                               {:platform-refs (map #(dg/platform-ref {:short-name %})
                                                    platforms)})))
        g1 (make-gran "c10" "c41")
        g2 (make-gran "c20" "c51")
        g3 (make-gran "c30")
        g4 (make-gran "c40")
        g5 (make-gran "c50")]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)
         ;; Descending sorts by the min value of a multi value fields
         "platform" [g1 g2 g3 g4 g5]
         ;; Descending sorts by the max value of a multi value fields
         "-platform" [g2 g5 g1 g4 g3])))

(deftest granule-instrument-sorting-test
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms
                               [(data-umm-cmn/platform
                                  {:ShortName "platform"
                                   :Instruments (map #(data-umm-cmn/instrument {:ShortName %})
                                                     ["c10" "c41" "c20" "c51" "c30" "c40" "c50"])})]}))
        make-gran (fn [& instruments]
                    (d/ingest "PROV1"
                              (dg/granule-with-umm-spec-collection
                               coll
                               (:concept-id coll)
                               {:platform-refs
                                [(dg/platform-ref
                                  {:short-name "platform"
                                   :instrument-refs (map #(dg/instrument-ref {:short-name %})
                                                         instruments)})]})))
        g1 (make-gran "c10" "c41")
        g2 (make-gran "c20" "c51")
        g3 (make-gran "c30")
        g4 (make-gran "c40")
        g5 (make-gran "c50")]

    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         ;; Descending sorts by the min value of a multi value fields
         "instrument" [g1 g2 g3 g4 g5]
         ;; Descending sorts by the max value of a multi value fields
         "-instrument" [g2 g5 g1 g4 g3])))

(deftest granule-sensor-sorting-test
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                 {:Platforms
                  [(data-umm-cmn/platform
                     {:ShortName "platform"
                      :Instruments [(data-umm-cmn/instrument
                                      {:ShortName "instrument"
                                       :ComposedOf (map #(data-umm-cmn/instrument {:ShortName %})
                                                     ["c10" "c41" "c20" "c51" "c30" "c40" "c50"])})]})]}))
        make-gran (fn [& sensors]
                    (d/ingest "PROV1"
                              (dg/granule-with-umm-spec-collection
                               coll
                               (:concept-id coll)
                               {:platform-refs
                                [(dg/platform-ref
                                  {:short-name "platform"
                                   :instrument-refs [(dg/instrument-ref
                                                      {:short-name "instrument"
                                                       :sensor-refs (map #(dg/sensor-ref {:short-name %})
                                                                         sensors)})]})]})))
        g1 (make-gran "c10" "c41")
        g2 (make-gran "c20" "c51")
        g3 (make-gran "c30")
        g4 (make-gran "c40")
        g5 (make-gran "c50")]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         ;; Descending sorts by the min value of a multi value fields
         "sensor" [g1 g2 g3 g4 g5]
         ;; Descending sorts by the max value of a multi value fields
         "-sensor" [g2 g5 g1 g4 g3])))

(deftest granule-day-night-flag-sorting-test
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        g1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:day-night "DAY"}))
        g2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:day-night "NIGHT"}))
        g3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:day-night "BOTH"}))
        g4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:day-night "UNSPECIFIED"}))]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         "day_night_flag" [g3 g1 g2 g4]
         "-day_night_flag" [g4 g2 g1 g3])))

(deftest granule-downloadable-sorting-test
  (let [ru1 (dg/related-url {:type "GET DATA"})
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        g1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru1]}))
        g2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {}))]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         "downloadable" [g2 g1]
         "online_only" [g2 g1]
         "-downloadable" [g1 g2]
         "-online_only" [g1 g2])))

(deftest granule-browse-only-sorting-test
  (let [ru1 (dg/related-url {:type "GET RELATED VISUALIZATION"})
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        g1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru1]}))
        g2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {}))]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         "browse_only" [g2 g1]
         "-browse_only" [g1 g2]
         "+browse_only" [g2 g1]
         "browsable" [g2 g1]
         "-browsable" [g1 g2]
         "+browsable" [g2 g1])))
