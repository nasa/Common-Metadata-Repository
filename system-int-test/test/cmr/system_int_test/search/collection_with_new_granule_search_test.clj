(ns cmr.system-int-test.search.collection-with-new-granule-search-test
  "Integration test for searching collections created after a given date.
   These tests are to ensure proper CMR Harvesting functionality."
  (:require
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-system-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url-helper]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (dev-system-util/freeze-resume-time-fixture)]))

(deftest search-for-collections-with-new-granules
  (let [_ (dev-system-util/freeze-time! "2010-01-01T10:00:00Z")
        oldest-collection (d/ingest-umm-spec-collection
                            "PROV1"
                            (data-umm-c/collection
                              {:EntryTitle "oldie"
                               :Version "v1"
                               :ShortName "Oldie"}))

        _ (dev-system-util/freeze-time! "2010-01-01T10:00:00Z")
        old-granule (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          oldest-collection (:concept-id oldest-collection)))

        _ (dev-system-util/freeze-time! "2012-01-01T10:00:00Z")
        elder-collection (d/ingest-umm-spec-collection
                           "PROV1"
                           (data-umm-c/collection
                             {:EntryTitle "new"
                              :Version "v1"
                              :ShortName "New"}))

        _ (dev-system-util/freeze-time! "2012-01-01T10:00:00Z")
        elder-granule (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          elder-collection (:concept-id elder-collection)))

        _ (dev-system-util/freeze-time! "2016-01-01T10:00:00Z")
        regular-collection (d/ingest-umm-spec-collection
                             "PROV1"
                             (data-umm-c/collection
                               {:EntryTitle "regular"
                                :Version "v1"
                                :ShortName "Regular"}))

        _ (dev-system-util/freeze-time! "2016-01-01T10:00:00Z")
        regular-granule (d/ingest "PROV1"
                          (dg/granule-with-umm-spec-collection
                            regular-collection (:concept-id regular-collection)))

        _ (dev-system-util/freeze-time! "2016-01-01T10:00:00Z")
        deleted-collection (d/ingest-umm-spec-collection
                             "PROV1"
                             (data-umm-c/collection
                               {:EntryTitle "deleted"
                                :Version "v1"
                                :ShortName "Deleted"}))
        deleted-concept {:provider-id "PROV1"
                         :concept-type :collection
                         :native-id (:EntryTitle deleted-collection)}
        _ (ingest/delete-concept deleted-concept)

        _ (dev-system-util/freeze-time! "2017-01-01T10:00:00Z")
        whippersnapper-collection (d/ingest-umm-spec-collection
                                   "PROV1"
                                   (data-umm-c/collection
                                     {:EntryTitle "youngling"
                                      :Version "v1"
                                      :ShortName "whippersnapper"}))

        _ (dev-system-util/freeze-time! "2017-01-01T10:00:00Z")
        young-granule (d/ingest "PROV1"
                          (dg/granule-with-umm-spec-collection
                            whippersnapper-collection (:concept-id whippersnapper-collection)))]

    (index/wait-until-indexed)
    (testing "Old and deleted collections should not be found."
      (let [range-references (search/find-concepts-with-param-string
                               "collection"
                               "has_granules_created_at=2014-01-01T10:00:00Z,2016-02-01T10:00:00Z")
            ;; TODO test that an end date before a start date throws a 400 error
            none-found (client/get (str "http://localhost:3003/collections"
                                        "?has-granules-created-at=2016-02-01T10:00:00Z,2017-02-01T10:00:00Z"))]
        (d/refs-match? [regular-collection] range-references)
        (and (= (:body none-found) "")
             (= (get (:headers none-found) "CMR-Hits") 0))))
    (testing "Granule search by created-at"
      (let [references (search/find-concepts-with-param-string
                         "granule"
                         "created-at=2014-01-01T10:00:00Z,")]
        (d/refs-match? [regular-granule young-granule] references)))
    (testing "Using unsupported or incorrect parameters in conjunction with multi-part-query-params"
      (are [params]
        (let [{:keys [status errors]} (search/find-concepts-with-param-string
                                        "collection" params)]
          (= [400 [(format "Parameter [%s] was not recognized."
                           (first (string/split params #"=")))]]
             [status errors]))
        "birthday=2011-01-01T00:00:00Z&has_granules_created_at=2014-01-01T10:00:00Z"))))
