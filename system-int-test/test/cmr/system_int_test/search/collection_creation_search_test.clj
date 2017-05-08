(ns cmr.system-int-test.search.collection-creation-search-test
  "Integration test for searching collections created after a given date.
   These tests are to ensure proper CMR Harvesting functionality."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :refer [are2] :as util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(deftest search-for-new-collections
  (let [old-collection (d/ingest-umm-spec-collection
                         "PROV1"
                         (data-umm-c/collection
                           {:EntryTitle "oldie"
                            :Version "v1"
                            :ShortName "Oldie"
                            :DataDates [{:Type "CREATE"
                                         :Date "2010-11-17T00:00:00Z"}]}))
        new-collection (d/ingest-umm-spec-collection
                         "PROV1"
                         (data-umm-c/collection
                           {:EntryTitle "new"
                            :Version "v1"
                            :ShortName "New"
                            :DataDates [{:Type "CREATE"}]
                            :Date "2017-01-01T00:00:00Z"}))

        ;; Default insert-time in d/ingest-umm-spec-collection is Jan 1st, 2012
        regular-collection (d/ingest-umm-spec-collection
                             "PROV1"
                             (data-umm-c/collection
                               {:EntryTitle "regular"
                                :Version "v1"
                                :ShortName "Regular"}))
        old-collection-with-updates (d/ingest-umm-spec-collection
                                      "PROV1"
                                      (data-umm-c/collection
                                        {:EntryTitle "oldie"
                                         :Version "v1"
                                         :ShortName "Oldie"
                                         :DataDates [{:Type "CREATE"
                                                      :Date "2010-11-17T00:00:00Z"}
                                                     {:Type "UPDATE"
                                                      :Date "2016-11-17T00:00:00Z"}]}))]
    (index/wait-until-indexed)
    (testing "Old collections should not be found."
      (let [search-results (search/find-collections-created-after-date
                            {:created-date "2011-01-01T00:00:00Z"})]
        (d/refs-match? [new-collection regular-collection] search-results)))
    (testing "Using unsupported parameters"
      (are [params]
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                       (search/find-collections-created-after-date params))]
          (= [400 [(format "Parameters not supported! %s" params)]]
             [status errors]))
        {:insert-time "2011-01-01T00:00:00Z" :result-format :xml}
        {:birthday "2012-01-01T00:00:00Z" :result-format :xml}))
    (testing "Using invalid parameters"
      (let [search-results (search/find-collections-created-after-date
                            {:created-date "JUNE"})]))))

(deftest default-insert-time-test
  ;; This is to test whether collections have an insert-time by default
  (testing "Collections have default insert-time"
    (let [umm-collection (data-umm-c/collection
                           {:EntryTitle "regular"
                            :Version "v1"
                            :ShortName "Regular"})
        ; collection (dissoc collection :DataDates)
        ; collection (assoc collection :DataDates [])
          collection (d/ingest-umm-spec-collection
                      "PROV1" umm-collection)]
                      ; (-> umm-collection
                      ;     (dissoc :DataDates)))]
                          ; (assoc :DataDates [])))]
      (index/wait-until-indexed)
      (not= nil (println (:DataDates collection))))))
