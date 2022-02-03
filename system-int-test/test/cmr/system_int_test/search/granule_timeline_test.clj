(ns cmr.system-int-test.search.granule-timeline-test
  "This tests the granule timeline feature of the search api."
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as c]
   [clj-time.core :as t]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.concepts :as concepts]
   [cmr.common.dev.util :as dev]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn make-gran
  ([coll n single-date-str]
   (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll
                                                          (:concept-id coll)
                                                          {:granule-ur (str "gran" n)
                                                           :single-date-time single-date-str})))
  ([coll n start-date-str end-date-str]
   (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll
                                                          (:concept-id coll)
                                                          {:granule-ur (str "gran" n)
                                                           :beginning-date-time start-date-str
                                                           :ending-date-time end-date-str}))))

(comment
  (do
    (dev-sys-util/reset)
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})))

(deftest timeline-test
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection {:EntryTitle "Dataset1"
                                       :ShortName "S1"
                                       :Version "V1"
                                       :TemporalExtents
                                        [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
        coll2 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection {:EntryTitle "Dataset2"
                                       :ShortName "S2"
                                       :Version "V2"
                                       :TemporalExtents
                                        [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
        ;; Coll1 granules
        ;; Date range granules
        gran1 (make-gran coll1 1 "2000-02-01T00:00:00Z" "2000-05-01T00:00:00Z")
        gran2 (make-gran coll1 2 "2000-04-01T00:00:00Z" "2000-06-01T00:00:00Z")
        gran3 (make-gran coll1 3 "2000-06-01T00:00:00Z" "2000-07-01T00:00:00Z")
        gran4 (make-gran coll1 4 "2000-11-01T00:00:00Z" "2001-01-01T00:00:00Z")
        gran5 (make-gran coll1 5 "2001-01-01T00:00:00Z" "2001-03-01T00:00:00Z")
        gran6 (make-gran coll1 6 "2001-06-01T00:00:00Z" "2001-08-01T00:00:00Z")
        gran7 (make-gran coll1 7 "2001-09-01T00:00:00Z" nil) ; no end date

        ;; Single date granules
        gran8 (make-gran coll1 8 "2000-04-01T00:00:00Z")
        gran9 (make-gran coll1 9 "2000-09-01T00:00:00Z")
        gran10 (make-gran coll1 10 "2000-11-01T00:00:00Z")
        gran11 (make-gran coll1 11 "2001-07-01T00:00:00Z")

        ;; Coll2 granules
        ;; These are all daily granules
        gran12 (make-gran coll2 12 "1995-08-01T08:30:45Z" "1995-08-01T17:20:00Z")
        gran13 (make-gran coll2 13 "1995-08-02T08:30:45Z" "1995-08-02T17:20:00Z")
        gran14 (make-gran coll2 14 "1995-08-03T08:30:45Z" "1995-08-03T17:20:00Z")
        gran15 (make-gran coll2 15 "1995-08-04T08:30:45Z" "1995-08-04T17:20:00Z")
        gran16 (make-gran coll2 16 "1995-08-05T08:30:45Z" "1995-08-05T17:20:00Z")
        ;; granule with start-date that is represented as Integer in elasticsearch result (CMR-1061)
        gran17 (make-gran coll2 17 "1970-01-01T00:00:45Z" "1995-01-05T17:20:00Z")

        all-colls [coll1 coll2]
        coll-ids (map :concept-id all-colls)]
    (index/wait-until-indexed)

    (testing "invalid cases"
      (let [interval-msg (str "Timeline interval is a required parameter for timeline search "
                              "and must be one of year, month, day, hour, minute, or second.")]
        (testing "missing parameters"
          (is (= {:status 400
                  :errors ["Parameter [foo] was not recognized."
                           "start_date is a required parameter for timeline searches"
                           "end_date is a required parameter for timeline searches"
                           "interval is a required parameter for timeline searches"]}
                 (search/get-granule-timeline {:foo 5}))))

        (testing "query validation is run"
          (is (= {:status 400
                  :errors ["The shape contained duplicate points. Points 1 [lon=1 lat=2], 2 [lon=1 lat=2] and 3 [lon=1 lat=2] were considered equivalent or very close."]}
                 (search/get-granule-timeline {:start-date "1994-01-01T00:00:00Z"
                                               :end-date "2001-05-01T00:00:00Z"
                                               :interval :year
                                               :provider "PROV1"
                                               :polygon "1,2,1,2,1,2,1,2"}))))

        (testing "invalid start-date"
          (is (= {:status 400
                  :errors ["Timeline parameter start_date datetime is invalid: [foo] is not a valid datetime."]}
                 (search/get-granule-timeline {:start-date "foo"
                                               :end-date "2000-01-01T00:00:00Z"
                                               :interval :month}))))
        (testing "invalid end-date"
          (is (= {:status 400
                  :errors ["Timeline parameter end_date datetime is invalid: [foo] is not a valid datetime."]}
                 (search/get-granule-timeline {:end-date "foo"
                                               :start-date "2000-01-01T00:00:00Z"
                                               :interval :month}))))
        (testing "invalid interval"
          (is (= {:status 400
                  :errors [interval-msg]}
                 (search/get-granule-timeline {:start-date "1999-01-01T00:00:00Z"
                                               :end-date "2000-01-01T00:00:00Z"
                                               :interval :foo}))))
        (testing "start after end"
          (is (= {:status 400
                  :errors ["start_date [2000-01-01T00:00:00Z] must be before the end_date [1999-01-01T00:00:00Z]"]}
                 (search/get-granule-timeline {:end-date "1999-01-01T00:00:00Z"
                                               :start-date "2000-01-01T00:00:00Z"
                                               :interval :month}))))

        (testing "missing parameters with post"
          (is (= {:status 400 :errors ["Parameter [foo] was not recognized."
                                       "start_date is a required parameter for timeline searches"
                                       "end_date is a required parameter for timeline searches"
                                       "interval is a required parameter for timeline searches"]}
                 (search/get-granule-timeline-with-post {:foo 5}))))))


    (testing "multiple collections"
      (is (= {:status 200
              :results [{:concept-id (:concept-id coll1)
                         :intervals [["2000-01-01T00:00:00.000Z" "2001-05-01T00:00:00.000Z" 8]]}
                        {:concept-id (:concept-id coll2)
                         :intervals [["1994-01-01T00:00:00.000Z" "1996-01-01T00:00:00.000Z" 6]]}]}
             (search/get-granule-timeline {:start-date "1994-01-01T00:00:00Z"
                                           :end-date "2001-05-01T00:00:00Z"
                                           :interval :year}))))

    (testing "query parameters apply to granule counts"
      (let [selected-granules [gran1 gran2 gran3 gran8 gran9 gran12 gran13]]
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-01-01T00:00:00.000Z" "2001-01-01T00:00:00.000Z" 5]]}
                          {:concept-id (:concept-id coll2)
                           :intervals [["1995-01-01T00:00:00.000Z" "1996-01-01T00:00:00.000Z" 2]]}]}
               (search/get-granule-timeline {:concept-id coll-ids
                                             :granule-ur (map :granule-ur selected-granules)
                                             :start-date "1994-01-01T00:00:00Z"
                                             :end-date "2001-05-01T00:00:00Z"
                                             :interval :year})))))

    (testing "different intervals granulations"
      (testing "year interval"
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-01-01T00:00:00.000Z" "2002-02-01T00:00:00.000Z" 11]]}]}
               (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                             :start-date "2000-01-01T00:00:00Z"
                                             :end-date "2002-02-01T00:00:00Z"
                                             :interval :year}))))


      ;; This doesn't match Catalog REST but that's because it appears to have a bug with times.
      ;; It expects the first interval end date to be 965001600 which is 2000-07-31T00:00:00.000Z
      ;; It should return 965088000 which is 2000-08-01 00:00:00 UTC
      (testing "month interval ending after neverending granule"
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-02-01T00:00:00.000Z" "2000-08-01T00:00:00.000Z" 4]
                                       ["2000-09-01T00:00:00.000Z" "2000-10-01T00:00:00.000Z" 1]
                                       ["2000-11-01T00:00:00.000Z" "2001-04-01T00:00:00.000Z" 3]
                                       ["2001-06-01T00:00:00.000Z" "2002-02-01T00:00:00.000Z" 3]]}]}
               (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                             :start-date "2000-01-01T00:00:00Z"
                                             :end-date "2002-02-01T00:00:00Z"
                                             :interval :month}))))

      (testing "month interval after ending granules"
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-02-01T00:00:00.000Z" "2000-08-01T00:00:00.000Z" 4]
                                       ["2000-09-01T00:00:00.000Z" "2000-10-01T00:00:00.000Z" 1]]}]}
               (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                             :start-date "2000-01-01T00:00:00Z"
                                             :end-date "2000-10-01T00:00:00Z"
                                             :interval :month}))))

      (testing "day"
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-02-01T00:00:00.000Z" "2000-07-02T00:00:00.000Z" 4]
                                       ["2000-09-01T00:00:00.000Z" "2000-09-02T00:00:00.000Z" 1]
                                       ["2000-11-01T00:00:00.000Z" "2001-03-02T00:00:00.000Z" 3]
                                       ["2001-06-01T00:00:00.000Z" "2001-08-02T00:00:00.000Z" 2]
                                       ["2001-09-01T00:00:00.000Z" "2002-02-01T00:00:00.000Z" 1]]}]}
               (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                             :start-date "2000-01-01T00:00:00Z"
                                             :end-date "2002-02-01T00:00:00Z"
                                             :interval :day}))))

      (testing "hour"
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-02-01T00:00:00.000Z" "2000-07-01T01:00:00.000Z" 4]
                                       ["2000-09-01T00:00:00.000Z" "2000-09-01T01:00:00.000Z" 1]
                                       ["2000-11-01T00:00:00.000Z" "2001-03-01T01:00:00.000Z" 3]
                                       ["2001-06-01T00:00:00.000Z" "2001-08-01T01:00:00.000Z" 2]
                                       ["2001-09-01T00:00:00.000Z" "2002-02-01T00:00:00.000Z" 1]]}]}
               (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                             :start-date "2000-01-01T00:00:00Z"
                                             :end-date "2002-02-01T00:00:00Z"
                                             :interval :hour}))))
      (testing "minute"
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-02-01T00:00:00.000Z" "2000-07-01T00:01:00.000Z" 4]
                                       ["2000-09-01T00:00:00.000Z" "2000-09-01T00:01:00.000Z" 1]
                                       ["2000-11-01T00:00:00.000Z" "2001-03-01T00:01:00.000Z" 3]
                                       ["2001-06-01T00:00:00.000Z" "2001-08-01T00:01:00.000Z" 2]
                                       ["2001-09-01T00:00:00.000Z" "2002-02-01T00:00:00.000Z" 1]]}]}
               (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                             :start-date "2000-01-01T00:00:00Z"
                                             :end-date "2002-02-01T00:00:00Z"
                                             :interval :minute}))))
      (testing "second"
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-02-01T00:00:00.000Z" "2000-07-01T00:00:01.000Z" 4]
                                       ["2000-09-01T00:00:00.000Z" "2000-09-01T00:00:01.000Z" 1]
                                       ["2000-11-01T00:00:00.000Z" "2001-03-01T00:00:01.000Z" 3]
                                       ["2001-06-01T00:00:00.000Z" "2001-08-01T00:00:01.000Z" 2]
                                       ["2001-09-01T00:00:00.000Z" "2002-02-01T00:00:00.000Z" 1]]}]}
               (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                             :start-date "2000-01-01T00:00:00Z"
                                             :end-date "2002-02-01T00:00:00Z"
                                             :interval :second}))))
      (testing "Using URL extension .json"
        (is (= {:status 200
                :results [{:concept-id (:concept-id coll1)
                           :intervals [["2000-02-01T00:00:00.000Z" "2000-07-01T00:00:01.000Z" 4]
                                       ["2000-09-01T00:00:00.000Z" "2000-09-01T00:00:01.000Z" 1]
                                       ["2000-11-01T00:00:00.000Z" "2001-03-01T00:00:01.000Z" 3]
                                       ["2001-06-01T00:00:00.000Z" "2001-08-01T00:00:01.000Z" 2]
                                       ["2001-09-01T00:00:00.000Z" "2002-02-01T00:00:00.000Z" 1]]}]}
               (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                             :start-date "2000-01-01T00:00:00Z"
                                             :end-date "2002-02-01T00:00:00Z"
                                             :interval :second}
                                            {:url-extension "json"}))))

      (testing "get and post matches"
        (are [search-params]
             (= (search/get-granule-timeline search-params)
                (search/get-granule-timeline-with-post search-params))

             {:concept-id [(:concept-id coll1) (:concept-id coll2)]
              :start-date "1992-01-01T00:00:00Z"
              :end-date "2002-02-01T00:00:00Z"
              :interval :year}

             {:entry-title ["Dataset1" "Dataset2"]
              :start-date "1992-01-01T00:00:00Z"
              :end-date "2002-02-01T00:00:00Z"
              :interval :month}))
      (testing "correct headers are returned"
        (let [response (search/get-granule-timeline {:concept-id (:concept-id coll1)
                                                     :start-date "2000-01-01T00:00:00Z"
                                                     :end-date "2002-02-01T00:00:00Z"
                                                     :interval :second}
                                                    {:url-extension "json"
                                                     :include-headers true})
              headers (:headers response)]
          (is (= "application/json;charset=utf-8" (get headers "Content-Type")))
          (is (= "CMR-Hits, CMR-Request-Id, X-Request-Id, CMR-Scroll-Id, CMR-Search-After, CMR-Timed-Out, CMR-Shapefile-Original-Point-Count, CMR-Shapefile-Simplified-Point-Count"
                 (get headers "Access-Control-Expose-Headers"))))))))
