(ns cmr.system-int-test.search.collection-with-new-granule-search-test
  "Integration test for searching collections created after a given date.
   These tests are to ensure proper CMR Harvesting functionality."
  (:require
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.dev-system-util :as dev-system-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url-helper]))


(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"
                                              "provguid2" "PROV2"})
                       (dev-system-util/freeze-resume-time-fixture)]))

(comment
 (ingest/create-provider {:provider-id "PROV1" :provider-guid "provguid1"} {:grant-all-ingest? true
                                                                            :grant-all-search? true})
 (ingest/create-provider {:provider-id "PROV2" :provider-guid "provguid2"} {:grant-all-ingest? true
                                                                            :grant-all-search? true})
 (create-test-granules))

(defn- create-test-granules
  "TODO"
  []
  (dev-system-util/freeze-time! "2010-05-01T10:00:00Z")
  (let [coll-w-may-2010-granule (d/ingest-umm-spec-collection
                                 "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "may2010"
                                    :Version "v1"
                                    :ShortName "New"}))

        may-2010-granule (d/ingest "PROV1"
                                  (dg/granule-with-umm-spec-collection
                                    coll-w-may-2010-granule (:concept-id coll-w-may-2010-granule)))

        _ (dev-system-util/freeze-time! "2015-05-01T10:00:00Z")
        coll-w-may-2015-granule (d/ingest-umm-spec-collection
                                 "PROV2"
                                 (data-umm-c/collection
                                   {:EntryTitle "may2015"
                                    :Version "v1"
                                    :ShortName "Regular"}))

        may-2015-granule (d/ingest "PROV2"
                                   (dg/granule-with-umm-spec-collection
                                     coll-w-may-2015-granule (:concept-id coll-w-may-2015-granule)))]
    (index/wait-until-indexed)
    ;; Force coll2 granules into their own index to make sure
    ;; granules outside of 1_small_collections get deleted properly.
    (bootstrap/start-rebalance-collection (:concept-id coll-w-may-2015-granule))
    (bootstrap/finalize-rebalance-collection (:concept-id coll-w-may-2015-granule))
    (index/wait-until-indexed)
    {:coll-w-may-2010-granule coll-w-may-2010-granule
     :coll-w-may-2015-granule coll-w-may-2015-granule}))

; (deftest collections-has-granules-created-at-test
;   ;; PROV1 and PROV2 collections and granules
;   (let [{:keys [coll-w-may-2010-granule coll-w-may-2015-granule coll-w-june-2016-granule]}
;         (create-test-granules)]
;     (testing "with only has_granules_created_at parameter"
;       (util/are3
;         [date-ranges expected-results]
;         (let [actual-results (search/find-refs :collection {:has-granules-created-at date-ranges})]
;           (is (d/refs-match? expected-results actual-results)))
;
;         "Single date range"
;         ["2015-04-01T10:10:00Z,2015-06-01T16:13:12Z"] [coll-w-may-2015-granule]
;
;         "Prior to date"
;         [",2015-06-01T16:13:12Z"] [coll-w-may-2010-granule coll-w-may-2015-granule]
;
;         "After date"
;         ["2015-06-01T16:13:12Z,"] [coll-w-june-2016-granule]
;
;         "Multiple time ranges"
;         [",2014-07-01T16:13:12Z"
;          "2015-04-01T10:10:00Z,2015-06-01T16:13:12Z"
;          "2016-04-01T10:10:00Z,2016-07-01T16:13:12Z"]
;         [coll-w-may-2010-granule coll-w-may-2015-granule coll-w-june-2016-granule]))))

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
