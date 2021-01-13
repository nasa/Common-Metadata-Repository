(ns cmr.system-int-test.search.collection-relevancy.collection-temporal-relevancy-test
  "This tests the CMR Search API's temporal relevancy scoring and ranking
  capabilities"
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.config :as config]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.data.elastic-relevancy-scoring :as elastic-relevancy-scoring]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as umm-common]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; TEST:
;; * Temporal Ranges
;;   * Start, end
;;   * Start,
;;   * ,End
;;   * 2000-01-01T10:00:00Z/P10Y2M10DT2H
;;   * Multiple ranges
;; * Collection temporal extents
;;   * Start and end dates
;;   * Ends at present
;;   * Single date time
;;   * Start at Unix epoch time
;;   * Start and end before unix epoch time
;; * Sort by end date

(deftest relevancy-temporal-ranges
  (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-sort-use-temporal-relevancy! true))
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 1
                                 {:EntryTitle "coll 1"
                                  :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "2003-08-01T00:00:00Z"
                                                                                 :ending-date-time "2005-10-01T00:00:00Z"})]}))
        coll2 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 2
                                 {:EntryTitle "coll 2"
                                  :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "1995-08-01T00:00:00Z"
                                                                                 :ending-date-time "2000-10-01T00:00:00Z"})]}))
        coll3 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 3
                                 {:EntryTitle "coll 3"
                                  :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "2009-10-15T12:00:00Z"
                                                                                 :ends-at-present? true})]}))
        coll4 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 4
                                 {:EntryTitle "coll 4"
                                  :TemporalExtents [(umm-common/temporal-extent {:single-date-time "2008-5-15T12:00:00Z"})]}))
        coll5 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 5
                                 {:EntryTitle "coll 5"
                                  :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "1970-01-01T00:00:00Z"
                                                                                 :ending-date-time "1996-10-01T00:00:00Z"})]}))
        coll6 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 6
                                {:EntryTitle "coll 6"
                                 :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "1910-05-01T00:00:00Z"
                                                                                :ending-date-time "1968-10-01T00:00:00Z"})]}))]
    (index/wait-until-indexed)

    (are3 [temporal-search-ranges expected-collections]
      (is (d/refs-match-order? expected-collections (search/find-refs :collection {:temporal temporal-search-ranges})))

      "Temporal range with start and end"
      ["2000-01-01T10:00:00Z,2010-03-01T0:00:00Z"] [coll1 coll2 coll3 coll4]

      "Range with start and end, earlier"
      ["1996-01-01T10:00:00Z,2010-03-01T0:00:00Z"] [coll2 coll1 coll5 coll3 coll4]

      "Temporal range with no end date"
      ["2004-06-01T10:00:00Z"] [coll3 coll1 coll4]

      "Temporal range with no start date"
      [",2010-01-01T10:00:00Z"] [coll6 coll5 coll2 coll1 coll3 coll4]

      "Temporal range with span"
      ["2000-01-01T10:00:00Z/P10Y2M10DT2H"] [coll1 coll2 coll3 coll4]

      "Multiple temporal ranges"
      ["2001-01-01T10:00:00Z,2006-01-01T10:00:00Z" "1996-01-01T10:00:00Z,1997-01-01T10:00:00Z"]
      [coll1 coll2 coll5]

      "Multiple temporal ranges, no end date"
      ["2001-01-01T10:00:00Z,2006-01-01T10:00:00Z" "1996-01-01T10:00:00Z,1997-01-01T10:00:00Z" "2008-01-01T12:00:00Z"]
      [coll3 coll1 coll2 coll5 coll4]

      "Date range including collection with early ranges"
      ["1955-01-01T10:00:00Z,1999-03-01T0:00:00Z"] [coll5 coll6 coll2])

   (testing "Keyword search, sort by end date"
     (is (d/refs-match-order? [coll3 coll4 coll1 coll2 coll5 coll6]
                              (search/find-refs :collection {:keyword "coll"})))

     (testing "Multiple ongoing collections, date in future"
       (let [coll7 (d/ingest-umm-spec-collection
                    "PROV1"
                    (umm-c/collection 7
                                      {:EntryTitle "coll 7"
                                       :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "2015-10-15T12:00:00Z"
                                                                                      :ends-at-present? true})]}))
             coll8 (d/ingest-umm-spec-collection
                          "PROV1"
                          (umm-c/collection 8
                                            {:EntryTitle "coll 8"
                                             :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "2015-10-15T12:00:00Z"
                                                                                            :ending-date-time "2070-8-4T12:00:00Z"})]}))]

         (index/wait-until-indexed)
         (is (d/refs-match-order? [coll8 coll3 coll7 coll4 coll1 coll2 coll5 coll6]
                                  (search/find-refs :collection {:keyword "coll"}))))))))

(deftest relevancy-multiple-temporal-ranges
  (dev-sys-util/eval-in-dev-sys `(elastic-relevancy-scoring/set-sort-use-temporal-relevancy! true))
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 1
                                 {:EntryTitle "coll 1"
                                  :ShortName "coll 1"
                                  :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "1992-08-01T00:00:00Z"
                                                                                 :ending-date-time "1994-10-01T00:00:00Z"})
                                                    (umm-common/temporal-extent {:beginning-date-time "2002-08-01T00:00:00Z"
                                                                                 :ending-date-time "2005-10-01T00:00:00Z"})]}))
        coll2 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 2
                                 {:EntryTitle "coll 2"
                                  :ShortName "coll 2"
                                  :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "1995-08-01T00:00:00Z"
                                                                                 :ending-date-time "2000-10-01T00:00:00Z"})
                                                    (umm-common/temporal-extent {:beginning-date-time "2003-08-01T00:00:00Z"
                                                                                  :ending-date-time "2005-10-01T00:00:00Z"})]}))

        coll3 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 3
                                 {:EntryTitle "coll 3"
                                  :ShortName "coll 3"
                                  :TemporalExtents [(umm-common/temporal-extent {:beginning-date-time "1995-08-01T00:00:00Z"
                                                                                 :ending-date-time "1996-10-01T00:00:00Z"})
                                                    (umm-common/temporal-extent {:beginning-date-time "2003-08-01T00:00:00Z"
                                                                                 :ending-date-time "2005-10-01T00:00:00Z"})
                                                    (umm-common/temporal-extent {:beginning-date-time "2010-08-01T00:00:00Z"
                                                                                 :ends-at-present? true})]}))]
    (index/wait-until-indexed)

    (are3 [temporal-search-ranges expected-collections]
      (is (d/refs-match-order? expected-collections (search/find-refs :collection {:temporal temporal-search-ranges})))

      "Multiple temporal conditions"
      ["2003-08-02T10:00:00Z,2003-09-02T10:00:00Z" "1996-01-01T10:00:00Z,1997-01-01T10:00:00Z"] [coll2 coll3 coll1]

      "Single temporal condition spanning multiple ranges"
      ["1998-01-02T10:00:00Z,2004-09-02T10:00:00Z"] [coll2 coll1 coll3])))
