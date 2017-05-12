(ns cmr.system-int-test.search.collection-creation-search-test
  "Integration test for searching collections created after a given date.
   These tests are to ensure proper CMR Harvesting functionality."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.dev-system-util :as dev-system-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(defn- current-time
  []
  (str (first (clojure.string/split (str (java.time.LocalDateTime/now)) #"\.")) "Z"))

(deftest search-for-new-collections
  (let [_ (dev-system-util/freeze-time! "2010-01-01T10:00:00Z")
        oldest-collection (d/ingest-umm-spec-collection
                            "PROV1"
                            (data-umm-c/collection
                              {:EntryTitle "oldie"
                               :Version "v1"
                               :ShortName "Oldie"}))

        _ (dev-system-util/freeze-time! "2012-01-01T10:00:00Z")
        elder-collection (d/ingest-umm-spec-collection
                           "PROV1"
                           (data-umm-c/collection
                             {:EntryTitle "new"
                              :Version "v1"
                              :ShortName "New"}))

        _ (dev-system-util/freeze-time! "2016-01-01T10:00:00Z")
        regular-collection (d/ingest-umm-spec-collection
                             "PROV1"
                             (data-umm-c/collection
                               {:EntryTitle "regular"
                                :Version "v1"
                                :ShortName "Regular"}))

        _ (dev-system-util/freeze-time! "2016-01-01T10:00:00Z")
        deleted-collection (d/ingest-umm-spec-collection
                             "PROV1"
                             (data-umm-c/collection
                               {:EntryTitle "deleted"
                                :Version "v1"
                                :ShortName "Deleted"}))

        _ (dev-system-util/freeze-time! "2017-01-01T10:00:00Z")
        youngling-collection (d/ingest-umm-spec-collection
                               "PROV1"
                               (data-umm-c/collection
                                 {:EntryTitle "oldie-with-updates"
                                  :Version "v1"
                                  :ShortName "Oldie 2"}))

        oldest-collection-revision (d/ingest-umm-spec-collection
                                     "PROV1"
                                     (data-umm-c/collection
                                       {:EntryTitle "oldie"
                                        :Version "v1"
                                        :ShortName "Oldie"
                                        :Abstract "On second thought, this collection isn't so abstract after all"}))]

    (index/wait-until-indexed)
    (testing "Old and deleted collections should not be found."
      (let [search-results (search/find-collections-created-after-date
                            {:created-at "2014-01-01T10:00:00Z"})]
        (d/refs-match? [youngling-collection regular-collection] search-results)))
    (testing "Using unsupported parameters"
      (are [params]
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                       (search/find-collections-created-after-date params))]
          (= [400 [(format "Incorrect parameters or date-time given: %s" params)]]
             [status errors]))
        {:insert-time "2011-01-01T00:00:00Z" :result-format :xml}
        {:birthday "2012-01-01T00:00:00Z" :result-format :xml}))
    (testing "Using invalid parameters"
      (are [params]
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                       (search/find-collections-created-after-date params))]
          (= [400 [(format "Incorrect parameters or date-time given: %s" params)]]
             [status errors]))
        {:created-at "JUNE" :result-format :xml}))))
