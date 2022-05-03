(ns cmr.system-int-test.ingest.granule-ingest-test
  "CMR granule ingest integration tests.

  For granule permissions tests, see `provider-ingest-permissions-test`."
  (:require
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as u :refer [are3]]
   [cmr.indexer.data.index-set :as index-set]
   [cmr.indexer.system :as indexer-system]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm.umm-granule :as umm-g]))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1"})
                                    (dev-sys-util/freeze-resume-time-fixture)]))

(defn- get-granule-parent-collection-id
  "Returns the concept id of the parent collection of the given granule revision."
  ([gran]
   (get-granule-parent-collection-id (:concept-id gran) (:revision-id gran)))
  ([concept-id revision-id]
   (get-in (mdb/get-concept concept-id revision-id)
           [:extra-fields :parent-collection-id])))

;; Tests that a granule referencing a collection that had multiple concept ids (the native id changed
;; but the shortname or dataset id did not) will reference the correct collection.
;; See CMR-1104
(deftest granule-referencing-collection-with-changing-concept-id-test
  (let [common-fields {:EntryTitle "coll1" :ShortName "short1" :Version "V1"}
        orig-coll (data-umm-c/collection-concept (assoc common-fields :native-id "native1"))
        _ (ingest/ingest-concept orig-coll)

        ;; delete the collection
        deleted-response (ingest/delete-concept orig-coll)

        ;; Create collection again with same details but a different native id
        new-coll (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection (assoc common-fields :native-id "native2")))

        ;; Create granules associated with the collection fields.
        gran1 (data-core/ingest "PROV1" (update-in (granule/granule-with-umm-spec-collection new-coll (:concept-id new-coll))
                                                   [:collection-ref]
                                                   dissoc :ShortName :Version))
        gran2 (data-core/ingest "PROV1" (update-in (granule/granule-with-umm-spec-collection new-coll (:concept-id new-coll))
                                                   [:collection-ref]
                                                   dissoc :EntryTitle))
        gran3 (data-core/ingest "PROV1" (update-in (granule/granule-with-umm-spec-collection new-coll (:concept-id new-coll))
                                                   [:collection-ref]
                                                   dissoc :ShortName :Version :EntryTitle))]
    (index/wait-until-indexed)
    ;; Make sure the granules reference the correct collection
    (is (= (:concept-id new-coll)
           (get-granule-parent-collection-id gran1)))

    (is (= (:concept-id new-coll)
           (get-granule-parent-collection-id gran2)))
    (is (= (:concept-id new-coll)
           (get-granule-parent-collection-id gran3)))))

(deftest granule-change-parent-collection-test
  (testing "Cannot change granule's parent collection"
    (let [coll1 (data-core/ingest-umm-spec-collection
                 "PROV1" (data-umm-c/collection {:EntryTitle "coll1"
                                                 :ShortName "short1"
                                                 :Version "V1"
                                                 :native-id "native1"}))
          coll2 (data-core/ingest-umm-spec-collection
                 "PROV1" (data-umm-c/collection {:EntryTitle "coll2"
                                                 :ShortName "short2"
                                                 :Version "V2"
                                                 :native-id "native2"}))
          gran1 (data-core/item->concept
                 (granule/granule-with-umm-spec-collection
                  coll1
                  (:concept-id coll1)
                  {:native-id "gran-native1-1"}))
          gran2 (data-core/item->concept
                 (granule/granule-with-umm-spec-collection
                  coll2
                  (:concept-id coll2)
                  {:native-id "gran-native1-1"}))
          {:keys [concept-id revision-id]} (ingest/ingest-concept gran1)
          {:keys [status errors]} (ingest/ingest-concept gran2 {:allow-failure? true})]
      (is (= 422 status))
      (is (= [(format "Granule's parent collection cannot be changed, was [%s], now [%s]."
                      (:concept-id coll1) (:concept-id coll2))]
             errors))
      (testing "Ingest granule with the same native id as a deleted granule in another collection is OK"
        (ingest/delete-concept gran1)
        (let [{:keys [status]} (ingest/ingest-concept gran2 {:allow-failure? true})]
          (is (= 200 status))
          ;; revision 1 granule's parent collection is coll1
          (is (= (:concept-id coll1)
                 (get-granule-parent-collection-id concept-id revision-id)))
          ;; revision 3 granule's parent collection is coll2
          (is (= (:concept-id coll2)
                 (get-granule-parent-collection-id concept-id (+ 2 revision-id)))))))))

;; Verify that granule indexing is not being changed too quickly.
;; Changes being made to Granule indexed fields should be implemented across two
;; sprints to allow time for re-indexing of granules and to avoid breaking the search-app.
;;
;; Changes need to be made to start indexing the new field in one sprint,
;; and the search application changes should be made in the following sprint.
;;
;; In order to make this test pass, all that should need to be done is to update
;; the resource being slurped in the `let` below.
(deftest granule-ingest-change-cadence-test
  (let [allowed-granule-index-fields (-> "index_set_granule_mapping.clj"
                                         io/resource
                                         slurp
                                         edn/read-string)
        actual-granule-index-fields (:properties index-set/granule-mapping)]
    (is (= allowed-granule-index-fields actual-granule-index-fields))))

;; Verify a new granule is ingested successfully.
(deftest granule-ingest-test
  (testing "ingest of a new granule"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection)))
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id))))
  (testing "ingest of a new granule with a revision id"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (assoc (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection))) :revision-id 5)
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id 5))
      (is (= 5 revision-id)))))

;; Verify a new granule with concept-id is ingested successfully.
(deftest granule-w-concept-id-ingest-test
  (testing "ingest of a new granule with concept-id present"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          supplied-concept-id "G1-PROV1"
          granule (data-core/item->concept
                    (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id supplied-concept-id}))
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= supplied-concept-id concept-id))
      (is (= 1 revision-id)))))

;; Ingest same granule N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-granule-ingest-test
  (testing "ingest same granule n times ..."
    (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          n 4
          granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
          created-granules (doall (take n (repeatedly n #(ingest/ingest-concept granule))))]
      (index/wait-until-indexed)
      (is (apply = (map :concept-id created-granules)))
      (is (= (range 1 (inc n)) (map :revision-id created-granules))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-granule-ingest-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection)))
        granule-with-empty-body  (assoc granule :metadata "")
        {:keys [status errors]} (ingest/ingest-concept granule-with-empty-body)]
    (index/wait-until-indexed)
    (is (= 400 status))
    (is (re-find #"Request content is too short." (first errors)))))

;; Verify that the accept header works
(deftest granule-ingest-accept-header-response-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))]
    (testing "json response"
      (let [granule (data-core/item->concept
                     (granule/granule-with-umm-spec-collection collection (:concept-id collection)))
            response (ingest/ingest-concept granule {:accept-format :json :raw? true})
            {:keys [concept-id revision-id]} (ingest/parse-ingest-body :json response)]
        (index/wait-until-indexed)
        (is (= 201 (:status response)))
        (is (re-matches #"G\d+-PROV1" concept-id))
        (is (= 1 revision-id))))
    (testing "xml response"
      (let [granule (data-core/item->concept
                     (granule/granule-with-umm-spec-collection collection (:concept-id collection)))
            response (ingest/ingest-concept granule {:accept-format :xml :raw? true})
            {:keys [status concept-id revision-id]} (ingest/parse-ingest-body :xml response)]
        (index/wait-until-indexed)
        (is (= 201 (:status response)))
        (is (re-matches #"G\d+-PROV1" concept-id))
        (is (= 1 revision-id))))))

;; Verify that the accept header works with returned errors
(deftest granule-ingest-with-errors-accept-header-test
  (let [collection (data-umm-c/collection {:EntryTitle "Coll1"})]
    (testing "json response"
      (let [umm-granule (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"
                                                                                                       :granule-ur "Gran1"})
            granule (data-core/item->concept umm-granule)
            response (ingest/ingest-concept granule {:accept-format :json :raw? true})
            status (:status response)
            {:keys [errors]} (ingest/parse-ingest-body :json response)]
        (is (= [422 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
               [status errors]))))
    (testing "xml response"
      (let [umm-granule (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"
                                                                                                       :granule-ur "Gran1"})
            granule (data-core/item->concept umm-granule)
            response (ingest/ingest-concept granule {:accept-format :xml :raw? true})
            status (:status response)
            {:keys [errors]} (ingest/parse-ingest-body :xml response)]
        (is (= [422 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
               [status errors]))))))

;; Verify that the accept header works with deletions
(deftest delete-granule-with-accept-header-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))]
    (testing "json response"
      (let [granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
            _ (ingest/ingest-concept granule)
            response (ingest/delete-concept granule {:accept-format :json :raw? true})]
        (index/wait-until-indexed)
        (is (= {:concept-id "G1-PROV1" :revision-id 2}
             (select-keys (ingest/parse-ingest-body :json response) [:concept-id :revision-id])))))
    (testing "xml response"
      (let [granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G2-PROV1"}))
            _ (ingest/ingest-concept granule)
            response (ingest/delete-concept granule {:accept-format :xml :raw? true})]
        (index/wait-until-indexed)
        (is (= {:concept-id "G2-PROV1" :revision-id 2}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id])))))))

(deftest delete-granule-test
  (testing "It should be possible to delete existing concept and the operation without revision id should
           result in revision id 1 greater than max revision id of the concept prior to the delete"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
          ingest-result (ingest/ingest-concept granule)
          delete-result (ingest/delete-concept granule)
          ingest-revision-id (:revision-id ingest-result)
          delete-revision-id (:revision-id delete-result)]
      (index/wait-until-indexed)
      (is (= 1 (- delete-revision-id ingest-revision-id)))))
  (testing "Deleting existing concept with a revision-id should respect the revision id"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G2-PROV1"}))
          _ (ingest/ingest-concept granule)
          delete-result (ingest/delete-concept granule {:revision-id 5})
          delete-revision-id (:revision-id delete-result)]
      (index/wait-until-indexed)
      (is (= 5 delete-revision-id))
      (is (mdb/concept-exists-in-mdb? (:concept-id delete-result) 5)))))

;; Verify deleting non-existent concepts returns good error messages
(deftest delete-non-existing-concept-gives-good-error-message-test
  (testing "granule"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
          response (ingest/delete-concept granule {:raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Granule with native id \[.*?\] in provider \[PROV1\] does not exist"
                   (first errors))))))

;; Verify ingest is successful for request with content type that has parameters
(deftest content-type-with-parameter-ingest-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (data-core/item->concept
                  (assoc (granule/granule-with-umm-spec-collection collection (:concept-id collection))
                         :format "application/echo10+xml; charset=utf-8"))
        {:keys [status errors]} (ingest/ingest-concept granule)]
    (index/wait-until-indexed)
    (is (= 201 status))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection)))
        response (ingest/ingest-concept (assoc granule :format "") {:accept-format :json :raw? true})
         status (:status response)
        {:keys [errors]} (ingest/parse-ingest-body :json response)]
    (index/wait-until-indexed)
    (is (= 415 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify ingest behaves properly if request contains invalid content type.
(deftest invalid-content-type-ingest-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (data-core/item->concept (granule/granule-with-umm-spec-collection collection (:concept-id collection)))
        response (ingest/ingest-concept (assoc granule :format "blah") {:accept-format :json :raw? true})
        status (:status response)
        {:keys [errors]} (ingest/parse-ingest-body :json response)]
    (index/wait-until-indexed)
    (is (= 415 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify deleting same granule twice returns a 404
(deftest delete-same-granule-twice-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (data-core/item->concept
                 (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
        ingest-result (ingest/ingest-concept granule)
        delete1-result (ingest/delete-concept granule)
        delete2-result (ingest/delete-concept granule)]
    (index/wait-until-indexed)
    (is (= 201 (:status ingest-result)))
    (is (= 200 (:status delete1-result)))
    (is (= 404 (:status delete2-result)))
    (is (= [(format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                   (:native-id granule) (:concept-id granule))]
           (:errors delete2-result)))))

;; Verify that attempts to ingest a granule whose parent does not exist result in a 422 error
(deftest ingest-orphan-granule-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Coll1"}))
        umm-granule (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"
                                                                                                   :granule-ur "Gran1"})
        granule (data-core/item->concept umm-granule)
        _ (ingest/delete-concept (data-core/item->concept collection :echo10))
        {:keys [status errors]} (ingest/ingest-concept granule)]
    (index/wait-until-indexed)
    (is (= [422 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
           [status errors]))
    (is (not (mdb/concept-exists-in-mdb? "G1-PROV1" 0)))))

;; Verify that granules with embedded / (%2F) in the native-id are handled correctly
(deftest ingest-granule-with-slash-in-native-id-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        umm-granule (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:native-id "Name/With/Slashes"})
        granule (data-core/item->concept umm-granule)
        {:keys [concept-id revision-id] :as response} (ingest/ingest-concept granule)
        ingested-concept (mdb/get-concept concept-id)]
    (index/wait-until-indexed)
    (is (= 201 (:status response)))
    (is (mdb/concept-exists-in-mdb? concept-id revision-id))
    (is (= 1 revision-id))
    (is (= "Name/With/Slashes" (:native-id ingested-concept)))))

(deftest granule-schema-validation-test
  (are3 [concept-format validation-errors]
    (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          concept (data-core/item->concept
                   (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:beginning-date-time "2010-12-12T12:00:00Z"})
                   concept-format)
          invalid-granule (update concept :metadata
                                  #(-> %
                                       (str/replace "2010-12-12T12:00:00" "A")
                                       ;; this is to cause validation error for iso-smap format
                                       (str/replace "gmd:DS_Series" "XXXX")))
          {:keys [status errors]} (ingest/ingest-concept invalid-granule)]
      (is (= [400 validation-errors] [status errors])))

    "ECHO10 invalid datetime format"
    :echo10 ["Exception while parsing invalid XML: Line 1 - cvc-datatype-valid.1.2.1: 'A.000Z' is not a valid value for 'dateTime'."
             "Exception while parsing invalid XML: Line 1 - cvc-type.3.1.3: The value 'A.000Z' of element 'BeginningDateTime' is not valid."]

    "ISO SMAP invalid datetime format"
    :iso-smap ["Exception while parsing invalid XML: Line 1 - cvc-elt.1: Cannot find the declaration of element 'XXXX'."]

    "UMM-G invalid datetime format"
    :umm-json ["#/TemporalExtent/RangeDateTime/BeginningDateTime: [A.000Z] is not a valid date-time. Expected [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}Z, yyyy-MM-dd'T'HH:mm:ss[+-]HH:mm, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}[+-]HH:mm]"
               "#/TemporalExtent: required key [SingleDateTime] not found"
               "#/TemporalExtent: extraneous key [RangeDateTime] is not permitted"]))

(deftest ingest-smap-iso-granule-test
  (let [collection (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "correct"
                                                                                         :ShortName "S1"
                                                                                         :Version "V1"}))]
    (testing "Valid SMAP ISO granule with collection-ref attributes"
      (are3 [attrs]
        (let [granule (-> (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:granule-ur "Gran1"})
                          (assoc :collection-ref (umm-g/map->CollectionRef attrs))
                          (data-core/item->concept :iso-smap))
              {:keys [status] :as response} (ingest/ingest-concept granule)]
          (index/wait-until-indexed)
          (is (#{200 201} status) (pr-str response)))

        "EntryTitle"
        {:entry-title "correct"}
        "ShortName Version"
        {:short-name "S1" :version-id "V1"}
        "EntryTitle ShortName"
        {:entry-title "correct" :short-name "S1"}
        "EntryTitle Version"
        {:entry-title "correct" :version-id "V1"}
        "EntryTitle ShortName Version"
        {:entry-title "correct" :short-name "S1" :version-id "V1"}))

    (testing "Invalid SMAP ISO granule with collection-ref attributes"
      (are3 [attrs expected-errors]
        (let [collection-ref (umm-g/map->CollectionRef attrs)
              granule (-> (granule/granule-with-umm-spec-collection collection (:concept-id collection) {:granule-ur "Gran1"})
                          (assoc :collection-ref collection-ref)
                          (data-core/item->concept :iso-smap))
              {:keys [status errors]} (ingest/ingest-concept granule)]
          (is (= [422 expected-errors] [status errors])))

        "Missing everything"
        {}
        ["Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."]

        "Wrong Entry Title"
        {:entry-title "wrong"}
        ["Collection with Entry Title [wrong] referenced in granule [Gran1] provider [PROV1] does not exist."]

        "Only ShortName"
        {:short-name "S2"}
        ["Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."]

        "Only Version"
        {:version-id "V2"}
        ["Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."]

        "Wrong ShortName Version"
        {:short-name "S2" :version-id "V1"}
        ["Collection with Short Name [S2], Version Id [V1] referenced in granule [Gran1] provider [PROV1] does not exist."]

        "Wrong EntryTitle ShortName Version"
        {:entry-title "correct" :short-name "S2" :version-id "V1"}
        ["Collection with Entry Title [correct], Short Name [S2], Version Id [V1] referenced in granule [Gran1] provider [PROV1] does not exist."]))))

(deftest ingest-granule-with-parent-umm-collection-test
  (let [cddis-umm (-> "example-data/umm-json/collection/v1.2/CDDIS.json" io/resource slurp)
        metadata-format "application/vnd.nasa.cmr.umm+json;version=1.2"
        coll-concept-id "C1-PROV1"
        gran-concept-id "G1-PROV1"
        coll-map {:provider-id  "PROV1"
                  :native-id    "umm_json_cddis_V1"
                  :concept-type :collection
                  :concept-id   coll-concept-id
                  :format       metadata-format
                  :metadata     cddis-umm}
        ingest-collection-response (ingest/ingest-concept coll-map {:accept-format :json})
        granule (data-core/item->concept
                 (granule/granule-with-umm-spec-collection (json/parse-string cddis-umm true)
                                                           coll-concept-id
                                                           {:concept-id gran-concept-id}))
        ingest-granule-response (ingest/ingest-concept granule)
        _ (index/wait-until-indexed)
        coll-content-type (-> (search/retrieve-concept coll-concept-id 1 {:url-extension "native"})
                              :headers
                              (get "Content-Type"))
        granule-search-response (search/find-refs :granule {:concept-id gran-concept-id})]
    (testing "Collection ingested and indexed successfully as version 1.2 UMM JSON"
      (is (= 201 (:status ingest-collection-response)))
      (is (= "application/vnd.nasa.cmr.umm+json;version=1.2; charset=utf-8"
             coll-content-type)))
    (testing "Granule ingested successfully"
      (is (= 201 (:status ingest-granule-response))))
    (testing "Granule successfully indexed for search"
      (is (= 1 (:hits granule-search-response)))
      (is (= gran-concept-id (-> granule-search-response :refs first :id))))))

(deftest CMR-5129-invalid-iso-smap-orbit-values-test
  (let [coll-metadata (-> "iso-samples/CMR-5129-coll.xml" io/resource slurp)
        invalid-gran-metadata (-> "iso-samples/invalid-CMR-5129-gran.xml" io/resource slurp)
        valid-gran-metadata (-> "iso-samples/valid-CMR-5129-gran.xml" io/resource slurp)]
    (testing "Invalid orbit"
      (ingest/ingest-concept
        (ingest/concept :collection "PROV1" "foo" :iso19115 coll-metadata))
      (let [{:keys [status]} (ingest/ingest-concept
                               (ingest/concept :granule "PROV1" "foo" :iso-smap invalid-gran-metadata))]
         (is (= 422 status))))
    (testing "Valid orbit"
      (let [{:keys [status]} (ingest/ingest-concept
                               (ingest/concept :granule "PROV1" "foo" :iso-smap valid-gran-metadata))]
         (is (= 201 status))))))

(deftest CMR-5226-invalid-iso-smap-geographic-description-test
  (let [coll-metadata (-> "iso-samples/CMR-5129-coll.xml" io/resource slurp)
        invalid-gran-metadata (-> "iso-samples/invalid-CMR-5226-gran.xml" io/resource slurp)]
    (testing "Invalid geographic description "
      (ingest/ingest-concept
        (ingest/concept :collection "PROV1" "foo" :iso19115 coll-metadata))
      (let [{:keys [status errors]} (ingest/ingest-concept
                                      (ingest/concept :granule "PROV1" "foo" :iso-smap invalid-gran-metadata))]
         (is (= 422 status))
         (is (= ["Spatial validation error: Unsupported gmd:description inside gmd:EX_GeographicDescription - The supported ones are: OrbitParameters and OrbitCalculatedSpatialDomains"] (:errors (first errors))))))))

(deftest CMR-5216-invalid-iso-smap-ocsd-values-test
  (let [coll-metadata (-> "iso-samples/5216_IsoMends_Collection.xml" io/resource slurp)
        invalid-gran-metadata (-> "iso-samples/5216_IsoSmap_Granule.xml" io/resource slurp)
        expected-errors
         [{:errors ["Spatial validation error: Orbit Number must be an integer but was [abc]."
                    "Spatial validation error: Start Orbit Number must be an integer but was [1.2]."
                    "Spatial validation error: Equator Crossing Longitude must be within [-180.0] and [180.0] but was [240.0]."
                    "Spatial validation error: Equator Crossing Date Time must be a datetime but was [Z]."],
           :path ["OrbitCalculatedSpatialDomains" 0]}
          {:errors ["Spatial validation error: Orbit Number must be an integer but was [abc]."],
            :path ["OrbitCalculatedSpatialDomains" 1]}]]
    (testing "Invalid orbit calculated spatial domain"
      (ingest/ingest-concept
        (ingest/concept :collection "PROV1" "foo" :iso19115 coll-metadata))
      (let [{:keys [status errors]} (ingest/ingest-concept
                                      (ingest/concept :granule "PROV1" "foo" :iso-smap invalid-gran-metadata))]
         (is (= 422 status))
         (is (= expected-errors errors))))))

(deftest CMR-5216-valid-iso-smap-ocsd-values-test
  (let [coll-metadata (-> "iso-samples/5216_IsoMends_Collection.xml" io/resource slurp)
        valid-gran-metadata (-> "iso-samples/5216_Valid_IsoSmap_Granule.xml" io/resource slurp)]
    (testing "Invalid orbit calculated spatial domain"
      (ingest/ingest-concept
        (ingest/concept :collection "PROV1" "foo" :iso19115 coll-metadata))
      (let [{:keys [status errors]} (ingest/ingest-concept
                                      (ingest/concept :granule "PROV1" "foo" :iso-smap valid-gran-metadata))]
         (is (= 201 status))
         (is (= nil errors))))))

(deftest CMR-5216-invalid-echo10-ocsd-values-test
  (let [coll-metadata (-> "iso-samples/5216_IsoMends_Collection.xml" io/resource slurp)
        invalid-gran-metadata (-> "5216_Echo10_Granule.xml" io/resource slurp)
        ;; Note: type errors for most of the fields are caught by xml validation for echo10 granule,
        ;; except for start/stop orbit numbers, which double is allowed in the xml schema.
        ;; So, it will pass the xml validation and get caught by the ocsd-validations.
        expected-errors
         [{:errors ["Spatial validation error: Start Orbit Number must be an integer but was [1.0]."]
           :path ["OrbitCalculatedSpatialDomains" 0]}]]
    (testing "Invalid orbit calculated spatial domain"
      (ingest/ingest-concept
        (ingest/concept :collection "PROV1" "foo" :iso19115 coll-metadata))
      (let [{:keys [status errors]} (ingest/ingest-concept
                                      (ingest/concept :granule "PROV1" "foo" :echo10 invalid-gran-metadata))]
         (is (= 422 status))
         (is (= expected-errors errors))))))

(deftest CMR-5216-valid-echo10-ocsd-values-test
  (let [coll-metadata (-> "iso-samples/5216_IsoMends_Collection.xml" io/resource slurp)
        invalid-gran-metadata (-> "5216_Valid_Echo10_Granule.xml" io/resource slurp)]
    (testing "Invalid orbit calculated spatial domain"
      (ingest/ingest-concept
        (ingest/concept :collection "PROV1" "foo" :iso19115 coll-metadata))
      (let [{:keys [status errors]} (ingest/ingest-concept
                                      (ingest/concept :granule "PROV1" "foo" :echo10 invalid-gran-metadata))]
         (is (= 201 status))
         (is (= nil errors))))))

(deftest ^:oracle delete-time-granule-ingest-test
  (s/only-with-real-database
   (let [collection (data-core/ingest-umm-spec-collection
                     "PROV1"
                     (data-umm-c/collection 1 {}))]
     (testing "DeleteTime in past results in validation error"
       (let [granule (granule/granule-with-umm-spec-collection
                      collection
                      (:concept-id collection)
                      {:granule-ur "gran1"
                       :data-provider-timestamps {:delete-time "2000-01-01T00:00:00Z"}})
             {:keys [status errors]} (data-core/ingest
                                      "PROV1"
                                      granule
                                      {:format :umm-json
                                       :allow-failure? true})]
         (is (= 422 status))
         (is (= ["DeleteTime 2000-01-01T00:00:00.000Z is before the current time."]
                errors))))
     (testing "DeleteTime in future is successful"
       (let [granule (granule/granule-with-umm-spec-collection
                      collection
                      (:concept-id collection)
                      {:granule-ur "gran2"
                       :data-provider-timestamps {:delete-time
                                                  (t/plus (tk/now) (t/seconds 90))}})
             response (data-core/ingest "PROV1" granule {:format :umm-json})]
         (is (= 201 (:status response)))
         (index/wait-until-indexed)
         (data-core/assert-refs-match [response] (search/find-refs :granule {}))

         ;; wait until the granule expires in database
         (Thread/sleep 91000)

         (mdb/cleanup-expired-concepts)
         (index/wait-until-indexed)
         (data-core/assert-refs-match [] (search/find-refs :granule {})))))))

(deftest ingest-umm-g-granule-test
  (let [collection (data-core/ingest-umm-spec-collection
                    "PROV1" (data-umm-c/collection {:EntryTitle "correct"
                                                    :ShortName "S1"
                                                    :Version "V1"}))]
    (testing "Valid UMM-G granule with collection-ref attributes, default UMM-G version"
      (are3 [attrs]
        (let [granule (-> (granule/granule-with-umm-spec-collection
                           collection
                           (:concept-id collection)
                           {:granule-ur "Gran1"
                            :collection-ref (umm-g/map->CollectionRef attrs)})
                          (data-core/item->concept :umm-json))
              {:keys [status] :as response} (ingest/ingest-concept granule)]
          (index/wait-until-indexed)
          (is (#{200 201} status) (pr-str response)))

        "EntryTitle"
        {:entry-title "correct"}
        "ShortName Version"
        {:short-name "S1" :version-id "V1"}
        "EntryTitle ShortName"
        {:entry-title "correct" :short-name "S1"}
        "EntryTitle Version"
        {:entry-title "correct" :version-id "V1"}
        "EntryTitle ShortName Version"
        {:entry-title "correct" :short-name "S1" :version-id "V1"}))

    (testing "Invalid UMM-G granule with collection-ref attributes"
      (are3 [attrs expected-status expected-errors]
        (let [collection-ref (umm-g/map->CollectionRef attrs)
              granule (-> (granule/granule-with-umm-spec-collection
                           collection (:concept-id collection) {:granule-ur "Gran1"
                                                                :collection-ref collection-ref})
                          (data-core/item->concept :umm-json))
              {:keys [status errors]} (ingest/ingest-concept granule)]
          (is (= expected-status status))
          (is (= expected-errors errors)))

        "Wrong Entry Title"
        {:entry-title "wrong"}
        422
        ["Collection with Entry Title [wrong] referenced in granule [Gran1] provider [PROV1] does not exist."]

        "Wrong ShortName Version"
        {:short-name "S2" :version-id "V1"}
        422
        ["Collection with Short Name [S2], Version Id [V1] referenced in granule [Gran1] provider [PROV1] does not exist."]

        "Wrong EntryTitle ShortName Version"
        {:entry-title "incorrect" :short-name "S2" :version-id "V1"}
        422
        ["Collection with Entry Title [incorrect] referenced in granule [Gran1] provider [PROV1] does not exist."]

        "Only ShortName"
        {:short-name "S2"}
        400
        ["#/CollectionReference: required key [Version] not found"
         "#/CollectionReference: required key [EntryTitle] not found"
         "#/CollectionReference: extraneous key [ShortName] is not permitted"]

        "Only Version"
        {:version-id "V2"}
        400
        ["#/CollectionReference: required key [ShortName] not found"
         "#/CollectionReference: required key [EntryTitle] not found"
         "#/CollectionReference: extraneous key [Version] is not permitted"]

        "Missing everything"
        {}
        400
        ["#/CollectionReference: required key [EntryTitle] not found"
         "#/CollectionReference: required key [ShortName] not found"
         "#/CollectionReference: required key [Version] not found"]))

    (testing "Valid UMM-G granule with specific valid UMM-G version"
      (let [granule (-> (granule/granule-with-umm-spec-collection
                         collection
                         (:concept-id collection)
                         {:granule-ur "Gran1"
                          :collection-ref (umm-g/map->CollectionRef {:entry-title "correct"
                                                                     :identifiers [:identifier "a very nice granule :)"
                                                                                   :identifier-type "a type of identifier"]})})
                        (data-core/item->concept {:format :umm-json
                                                  :version "1.6.4"}))
            {:keys [status] :as response} (ingest/ingest-concept granule)]
        (is (#{200 201} status) (pr-str response))))

    (testing "Ingest UMM-G granule with invalid UMM-G version"
      (let [granule (-> (granule/granule-with-umm-spec-collection
                         collection
                         (:concept-id collection)
                         {:granule-ur "Gran1"
                          :collection-ref (umm-g/map->CollectionRef {:entry-title "correct"})})
                        (data-core/item->concept {:format :umm-json
                                                  :version "1.1"}))
            {:keys [status errors]} (ingest/ingest-concept granule)]
        (is (= 400 status))
        (is (= ["Invalid UMM JSON schema version: 1.1"] errors))))

    (testing "Ingest UMM-G granule with empty body"
      (let [granule (-> (granule/granule-with-umm-spec-collection
                         collection
                         (:concept-id collection)
                         {:granule-ur "Gran1"
                          :collection-ref (umm-g/map->CollectionRef {:entry-title "correct"})})
                        (data-core/item->concept :umm-json))
            granule-with-empty-body (assoc granule :metadata "")
            {:keys [status errors]} (ingest/ingest-concept granule-with-empty-body)]
        (is (= 400 status))
        (is (= ["Request content is too short."] errors))))

    (testing "Ingest invalid UMM-G granule record"
      (let [granule (-> (granule/granule-with-umm-spec-collection
                         collection
                         (:concept-id collection)
                         {:granule-ur ""
                          :collection-ref (umm-g/map->CollectionRef {:entry-title "correct"})})
                        (data-core/item->concept :umm-json))
            {:keys [status errors]} (ingest/ingest-concept granule)]
        (is (= 400 status))
        (is (= ["#/GranuleUR: expected minLength: 1, actual: 0"] errors))))

    (testing "Ingest UMM-G granule with invalid OrbitCalculatedSpatialDomains"
      (are3 [attrs expected-errors]
        (let [granule (-> (granule/granule-with-umm-spec-collection
                           collection
                           (:concept-id collection)
                           {:granule-ur "Gran1"
                            :orbit-calculated-spatial-domains [(granule/orbit-calculated-spatial-domain
                                                                attrs)]
                            :collection-ref (umm-g/map->CollectionRef {:entry-title "correct"})})
                          (data-core/item->concept :umm-json))
              {:keys [status errors]} (ingest/ingest-concept granule)]
          (is (= 400 status))
          (is (= expected-errors errors)))

        "OrbitNumber not Integer"
        {:orbital-model-name "Orbit1"
         :orbit-number 1.0}
        ["#/OrbitCalculatedSpatialDomains/0/OrbitNumber: expected type: Integer, found: BigDecimal"]

        "both OrbitNumber and BeginOrbitNumber"
        {:orbital-model-name "Orbit1"
         :orbit-number 1
         :start-orbit-number 1}
        ["#/OrbitCalculatedSpatialDomains/0: subject must not be valid against schema {\"required\":[\"OrbitNumber\",\"BeginOrbitNumber\"]}"]

        "both OrbitNumber and EndOrbitNumber"
        {:orbital-model-name "Orbit1"
         :orbit-number 1
         :stop-orbit-number 1}
        ["#/OrbitCalculatedSpatialDomains/0: subject must not be valid against schema {\"required\":[\"OrbitNumber\",\"EndOrbitNumber\"]}"]))))

        ;; ECSE-503: The following test should be enabled once ECSE-503 is fixed.
        ;; Currently, the UMM-G schema does not define the validation rules correctly for OrbitCalculatedSpatialDomain
        ; "BeginOrbitNumber without EndOrbitNumber"
        ; {:orbital-model-name "Orbit1"
        ;  :stop-orbit-number 1}
        ; [(str "/OrbitCalculatedSpatialDomains/0 instance failed to match all required schemas "
        ;       "(matched only 1 out of 2)")]
