(ns cmr.system-int-test.search.granule-search-production-date-time-test
  "Integration tests for granule search by production date time"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.common.date-time-parser :as p]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- ingest-granule-with-production-date-time
  "ingest the granule for the given provider and collection with the given production date time"
  [provider-id coll prod-datetime]
  (d/ingest provider-id (dg/granule-with-umm-spec-collection
                           coll
                           (:concept-id coll)
                           {:production-date-time (p/parse-datetime prod-datetime)})))

(deftest search-granules-by-production-date-time
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection {:EntryTitle "ET1" :ShortName "S1"}))
        coll2 (d/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection {:EntryTitle "ET2" :ShortName "S2"}))
        gran1 (ingest-granule-with-production-date-time "PROV1" coll1 "2000-01-01T10:00:00Z")
        gran2 (ingest-granule-with-production-date-time "PROV1" coll1 "2000-02-01T10:00:00Z")
        gran3 (ingest-granule-with-production-date-time "PROV1" coll2 "2000-03-01T10:00:00Z")
        gran4 (ingest-granule-with-production-date-time "PROV1" coll2 "2000-04-01T10:00:00Z")]
    (index/wait-until-indexed)

    (testing "search granules with production_date[] ranges and options"
      (are3 [grans value options]
        (let [references (search/find-refs :granule
                                           (merge {"production_date[]" value} options))]
          (is (d/refs-match? grans references)))

        "range without begin - find all"
        [gran1 gran2 gran3 gran4] ",2015-01-01T10:00:00Z" {}

        "range without begin - find some"
        [gran1 gran2 gran3] ",2000-03-01T10:00:00Z" {}

        "range without begin - find none"
        [] ",2000-01-01T09:00:00Z" {}

        "range without end - find all"
        [gran1 gran2 gran3 gran4] "2000-01-01T10:00:00Z," {}

        "range without end - omitting ,"
        [gran1 gran2 gran3 gran4] "2000-01-01T10:00:00Z" {}

        "range without end - find some"
        [gran4] "2000-04-01T10:00:00Z," {}

        "range without end - find none"
        [] "2015-01-01T11:00:00Z," {}

        "range - find all"
        [gran1 gran2 gran3 gran4] "2000-01-01T10:00:00Z,2015-01-01T10:00:00Z" {}

        "range - find some"
        [gran1 gran2] "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z" {}

        "range - find none"
        [] "2000-01-01T11:00:00Z,2000-02-01T09:00:00Z" {}

        "find a single value"
        [gran1] "2000-01-01T10:00:00Z,2000-01-01T10:00:00Z" {}

        "multiple ranges without options, should default to AND false"
        [gran3 gran4] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {}

        "multiple ranges with option and false"
        [gran3 gran4] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"]
        {"options[production_date][and]" "false"}

        "multiple ranges with option and true"
        [gran4] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"]
        {"options[production_date][and]" "true"}))

    (testing "search granules with production_date is OK"
      (are3 [grans param value]
        (let [references (search/find-refs :granule {param value})]
          (is (d/refs-match? grans references)))
        "search with single range"
        [gran1 gran2]
        "production_date"
        "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z"

        "search with multiple ranges"
        [gran3 gran4]
        "production_date"
        ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"]))

    (testing "search granules with invalid production_date date"
      (are3 [value err-pattern]
        (let [{:keys [status errors]} (search/find-refs :granule {"production_date" value})
              err (first errors)]
          (is (and (= 400 status)
                   (re-find err-pattern err))))
        "invalid date time"
        "2000-01-01T10:00:99Z" #"datetime is invalid:.*"

        "too many datetimes"
        "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z,2008-04-01T10:00:00Z"
        #"Too many commas in production-date.*"

        "too many commas"
        "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z," #"Too many commas in production-date.*"))))
