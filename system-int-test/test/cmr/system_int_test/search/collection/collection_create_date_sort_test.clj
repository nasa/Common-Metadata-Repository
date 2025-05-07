(ns cmr.system-int-test.search.collection.collection-create-date-sort-test
  "Tests sorting collections by their CREATE DataDate"
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common.time-keeper :as tk]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(defn- sort-order-correct?
  [items sort-key]
  (d/refs-match-order?
    items
    (search/find-refs :collection {:page-size 20 :sort-key sort-key})))

(deftest collection-create-date-sort-test
  (let [time-now (tk/now)

        ;; Collection with a CREATE date only
        coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:EntryTitle "Collection with CREATE date only"
                 :ShortName "COLL1"
                 :DataDates [(umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 10)))
                               :Type "CREATE"})]})
               {:format :umm-json})

        ;; Collection with CREATE and UPDATE dates
        coll2 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:EntryTitle "Collection with CREATE and UPDATE dates"
                 :ShortName "COLL2"
                 :DataDates [(umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 5)))
                               :Type "CREATE"})
                             (umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 2)))
                               :Type "UPDATE"})]})
               {:format :umm-json})

        ;; Collection with CREATE, UPDATE, and REVIEW dates
        coll3 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:EntryTitle "Collection with CREATE, UPDATE, and REVIEW dates"
                 :ShortName "COLL3"
                 :DataDates [(umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 2)))
                               :Type "CREATE"})
                             (umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 1)))
                               :Type "UPDATE"})
                             (umm-cmn/map->DateType
                              {:Date (str time-now)
                               :Type "REVIEW"})]})
               {:format :umm-json})

        ;; Collection with UPDATE date but no CREATE date
        coll4 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:EntryTitle "Collection with UPDATE date but no CREATE date"
                 :ShortName "COLL4"
                 :DataDates [(umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 3)))
                               :Type "UPDATE"})]})
               {:format :umm-json})

        ;; Collection with no DataDates at all
        coll5 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:EntryTitle "Collection with no DataDates"
                 :ShortName "COLL5"
                 :DataDates nil})
               {:format :umm-json})

        ;; Collection with multiple CREATE dates (should use the earliest one)
        coll6 (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection
                {:EntryTitle "Collection with multiple CREATE dates"
                 :ShortName "COLL6"
                 :DataDates [(umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 7)))
                               :Type "CREATE"})
                             (umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 3)))
                               :Type "CREATE"})
                             (umm-cmn/map->DateType
                              {:Date (str (t/minus time-now (t/days 1)))
                               :Type "UPDATE"})]})
               {:format :umm-json})]

    (index/wait-until-indexed)
    (testing "Sorting by create-data-date ascending"
      (is (sort-order-correct?
           [coll1 coll6 coll2 coll3 coll4 coll5]
           "create-data-date")))

    (testing "Sorting by create-data-date descending"
      (is (sort-order-correct?
           [coll3 coll2 coll6 coll1 coll4 coll5]
           "-create-data-date")))))
