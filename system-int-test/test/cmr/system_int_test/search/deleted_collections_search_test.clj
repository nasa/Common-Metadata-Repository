(ns cmr.system-int-test.search.deleted-collections-search-test
  "Integration test for searching deleted collections. Searching deleted collections Returns the
   xml references to the highest collection revisions prior to the tombstones for collections that
   are deleted after a given revision date."
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

(deftest search-deleted-collections
  (let [;; coll1 is a collection that has deleted collection revision and is re-ingeested
        ;; it will not be found as a deleted collection
        coll1-1 (d/ingest-umm-spec-collection "PROV1"
                                              (data-umm-c/collection {:EntryTitle "Dataset1"
                                                                      :Version "v1"
                                                                      :ShortName "s1"}))
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:EntryTitle coll1-1)}
        _ (ingest/delete-concept concept1)
        coll1-3 (d/ingest-umm-spec-collection "PROV1"
                                              (data-umm-c/collection {:EntryTitle "Dataset1"
                                                                      :Version "v2"
                                                                      :ShortName "s1"}))

        ;; coll2 is a collection that is deleted
        coll2-1 (d/ingest-umm-spec-collection "PROV1"
                                              (data-umm-c/collection {:EntryTitle "Dataset2"
                                                                      :Version "v1"
                                                                      :ShortName "s2"}))
        coll2-2 (d/ingest-umm-spec-collection "PROV1"
                                              (data-umm-c/collection {:EntryTitle "Dataset2"
                                                                      :Version "v2"
                                                                      :ShortName "s2"}))
        concept2 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:EntryTitle coll2-2)}
        _ (ingest/delete-concept concept2)

        ;; coll3 is a collection that is deleted and with multiple tombstones
        coll3-1 (d/ingest-umm-spec-collection "PROV1"
                                              (data-umm-c/collection {:EntryTitle "Dataset3"
                                                                      :Version "v1"
                                                                      :ShortName "s3"}))
        concept3 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:EntryTitle coll3-1)}
        _ (ingest/delete-concept concept3)
        coll3-3 (d/ingest-umm-spec-collection "PROV1"
                                              (data-umm-c/collection {:EntryTitle "Dataset3"
                                                                      :Version "v2"
                                                                      :ShortName "s3"}))
        _ (ingest/delete-concept concept3)

        ;; coll4 is a collection that has no deleted revisions
        coll4 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection {:EntryTitle "Dataset4"
                                                                    :Version "v4"
                                                                    :ShortName "s4"}))]
    (index/wait-until-indexed)
    (testing "search for deleted collections"
      (let [deleted-collections (search/find-deleted-collections {})]
        (d/refs-match? [coll2-2 coll3-3] deleted-collections)))))

(deftest ^:in-memory-db search-collections-by-revision-date
  ;; We only test in memory mode here as this test uses time-keeper to freeze time. This will not
  ;; work for external db mode since the revision date would be set automatically by oracle
  ;; when concepts are saved and would not depend on the current time in time-keeper.
  (s/only-with-in-memory-database
    (let [;; coll1 is deleted in 2015
          _ (dev-sys-util/freeze-time! "2015-01-01T10:00:00Z")
          coll1-1 (d/ingest-umm-spec-collection "PROV1"
                                                (data-umm-c/collection {:EntryTitle "Dataset1"
                                                                        :Version "v1"
                                                                        :ShortName "s1"}))
          _ (ingest/delete-concept {:provider-id "PROV1"
                                    :concept-type :collection
                                    :native-id (:EntryTitle coll1-1)})

          ;; coll2 is deleted in 2016
          _ (dev-sys-util/freeze-time! "2016-01-01T10:00:00Z")
          coll2-1 (d/ingest-umm-spec-collection "PROV1"
                                                (data-umm-c/collection {:EntryTitle "Dataset2"
                                                                        :Version "v1"
                                                                        :ShortName "s2"}))
          _ (ingest/delete-concept {:provider-id "PROV1"
                                    :concept-type :collection
                                    :native-id (:EntryTitle coll2-1)})

          ;; coll3 is created in 2016 and deleted in 2017
          coll3-1 (d/ingest-umm-spec-collection "PROV1"
                                                (data-umm-c/collection {:EntryTitle "Dataset3"
                                                                        :Version "v1"
                                                                        :ShortName "s3"}))
          _ (dev-sys-util/freeze-time! "2017-04-01T10:00:00Z")
          _ (ingest/delete-concept {:provider-id "PROV1"
                                    :concept-type :collection
                                    :native-id (:EntryTitle coll3-1)})

         ;;; coll4 is a collection that has no deleted revisions
          coll4 (d/ingest-umm-spec-collection "PROV1"
                                              (data-umm-c/collection {:EntryTitle "Dataset4"
                                                                      :Version "v4"
                                                                      :ShortName "s4"}))]
      (index/wait-until-indexed)

      (testing "search for deleted collections with revision date"
        (util/are2 [colls revision-date]
                   (let [references (search/find-deleted-collections {"revision_date[]" revision-date})]
                     (d/refs-match? colls references))

                   "revision date starting at 2015 - find all deleted collections"
                   [coll1-1 coll2-1 coll3-1] "2015-01-01T01:00:00Z"

                   "revision date starting at 2016 - find coll2 and coll3"
                   [coll2-1 coll3-1] "2016-01-01T01:00:00Z"

                   "revision date starting at 2017 - find coll3"
                   [coll3-1] "2017-01-01T01:00:00Z")))))

(deftest search-deleted-collections-error-cases
  (testing "search deleted collections with unsupported params"
    (are [params]
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/find-deleted-collections params))
            param-name (-> params
                           keys
                           first
                           name
                           csk/->snake_case_string)]
        (= [400 [(format "Parameter [%s] was not recognized." param-name)]]
           [status errors]))

      {:concept-id "C1-PROV1"}
      {:page_num 1}
      {:page-size 20}))

  (testing "search deleted collections with invalid revision date"
    (are [revision-date]
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/find-deleted-collections
                                      {:revision-date revision-date}))]
        (= [400 [(format (str "Invalid format for revision date, only a starting date is allowed "
                              "for deleted collections search, but was [%s]") revision-date)]]
           [status errors]))

      "2017-01-01T01:00:00Z,"
      "2015-01-01T01:00:00Z,2016-01-01T01:00:00Z"
      ",2017-01-01T01:00:00Z"))

  (testing "search deleted collections with multiple revision date"
    (let [{:keys [status errors]}
          (search/get-search-failure-xml-data
           (search/find-deleted-collections
            {:revision-date ["2016-01-01T01:00:00Z" "2017-01-01T01:00:00Z"]}))]
      (is (= [400 ["Only one revision date is allowed, but 2 were provided."]]
             [status errors]))))

  (testing "unsupported format for deleted collections search"
    (testing "formats return common json response"
      (are [search-format]
        (let [{:keys [status errors]} (search/get-search-failure-data
                                       (search/find-deleted-collections {} search-format))]
          (= [400 [(format (str "Result format [%s] is not supported by deleted collections search. "
                                "The only format that is supported is xml.")
                           (name search-format))]]
             [status errors]))

        :json
        :opendata))

    (testing "formats return common xml response"
      (are [search-format]
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                       (search/find-deleted-collections {} search-format))]
          (= [400 [(format (str "Result format [%s] is not supported by deleted collections search. "
                                "The only format that is supported is xml.")
                           (name search-format))]]
             [status errors]))

        :echo10
        :iso19115
        :dif
        :dif10
        :atom
        :kml
        :native))))
