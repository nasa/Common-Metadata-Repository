(ns cmr.system-int-test.search.collection-relevancy.collection-temporal-relevancy
  "This tests the CMR Search API's temporal relevancy scoring and ranking
  capabilities"
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.config :as config]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.data.query-to-elastic :as query-to-elastic]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
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

(deftest relevancy-temporal-ranges
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-use-temporal-relevancy! true))
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                                :temporal (dc/temporal {:beginning-date-time "2003-08-01T00:00:00Z"
                                                                        :ending-date-time "2005-10-01T00:00:00Z"})}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"
                                                :temporal (dc/temporal {:beginning-date-time "1995-08-01T00:00:00Z"
                                                                        :ending-date-time "2000-10-01T00:00:00Z"})}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3"
                                                :temporal (dc/temporal {:beginning-date-time "2009-10-15T12:00:00Z"
                                                                        :ends-at-present? true})}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "coll4"
                                                :temporal (dc/temporal {:single-date-time "2008-5-15T12:00:00Z"})}))
        coll5 (d/ingest "PROV1" (dc/collection {:entry-title "coll5"
                                                :temporal (dc/temporal {:beginning-date-time "1970-01-01T00:00:00Z"
                                                                        :ending-date-time "1997-10-01T00:00:00Z"})}))
        coll6 (d/ingest "PROV1" (dc/collection {:entry-title "coll6"
                                                :temporal (dc/temporal {:beginning-date-time "1910-05-01T00:00:00Z"
                                                                        :ending-date-time "1968-10-01T00:00:00Z"})}))]
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
      [coll1 coll5 coll2]

      "Multiple temporal ranges, no end date"
      ["2001-01-01T10:00:00Z,2006-01-01T10:00:00Z" "1996-01-01T10:00:00Z,1997-01-01T10:00:00Z" "2008-01-01T12:00:00Z"]
      [coll3 coll1 coll5 coll2 coll4]

      "Date range including collection with early ranges"
      ["1955-01-01T10:00:00Z,1999-03-01T0:00:00Z"] [coll5 coll6 coll2])))
