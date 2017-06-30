(ns cmr.system-int-test.ingest.granule-ingest-test
  "CMR granule ingest integration tests"
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as u :refer [are3]]
   [cmr.indexer.data.index-set :as index-set]
   [cmr.indexer.system :as indexer-system]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm.umm-granule :as umm-g]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

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
        new-coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection (assoc common-fields :native-id "native2")))

        ;; Create granules associated with the collection fields.
        gran1 (d/ingest "PROV1" (update-in (dg/granule-with-umm-spec-collection new-coll (:concept-id new-coll)) [:collection-ref]
                                           dissoc :ShortName :Version))
        gran2 (d/ingest "PROV1" (update-in (dg/granule-with-umm-spec-collection new-coll (:concept-id new-coll)) [:collection-ref]
                                           dissoc :EntryTitle))
        gran3 (d/ingest "PROV1" (update-in (dg/granule-with-umm-spec-collection new-coll (:concept-id new-coll)) [:collection-ref]
                                           dissoc :ShortName :Version :EntryTitle))]
    (index/wait-until-indexed)
    ;; Make sure the granules reference the correct collection
    (is (= (:concept-id new-coll)
           (get-in (mdb/get-concept (:concept-id gran1) (:revision-id gran1))
                   [:extra-fields :parent-collection-id])))

    (is (= (:concept-id new-coll)
           (get-in (mdb/get-concept (:concept-id gran2) (:revision-id gran2))
                   [:extra-fields :parent-collection-id])))
    (is (= (:concept-id new-coll)
           (get-in (mdb/get-concept (:concept-id gran3) (:revision-id gran3))
                   [:extra-fields :parent-collection-id])))))

(deftest granule-change-parent-collection-test
  (testing "Cannot change granule's parent collection"
    (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "coll1"
                                                                              :ShortName "short1"
                                                                              :Version "V1"
                                                                              :native-id "native1"}))
          coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "coll2"
                                                                              :ShortName "short2"
                                                                              :Version "V2"
                                                                              :native-id "native2"}))
          _ (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "gran1"}))
          {:keys [status errors]} (d/ingest "PROV1"
                                            (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:granule-ur "gran1"})
                                            {:allow-failure? true})]
      (is (= 422 status))
      (is (= [(format "Granule's parent collection cannot be changed, was [%s], now [%s]."
                      (:concept-id coll1) (:concept-id coll2))]
             errors)))))

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
        actual-granule-index-fields (:properties (index-set/granule-mapping :granule))]
    (is (= allowed-granule-index-fields actual-granule-index-fields))))

;; Verify a new granule is ingested successfully.
(deftest granule-ingest-test
  (testing "ingest of a new granule"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id))))
  (testing "ingest of a new granule with a revision id"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (assoc (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection))) :revision-id 5)
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id 5))
      (is (= 5 revision-id)))))

;; Verify a new granule with concept-id is ingested successfully.
(deftest granule-w-concept-id-ingest-test
  (testing "ingest of a new granule with concept-id present"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          supplied-concept-id "G1-PROV1"
          granule (d/item->concept
                    (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id supplied-concept-id}))
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= supplied-concept-id concept-id))
      (is (= 1 revision-id)))))

;; Ingest same granule N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-granule-ingest-test
  (testing "ingest same granule n times ..."
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          n 4
          granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
          created-granules (doall (take n (repeatedly n #(ingest/ingest-concept granule))))]
      (index/wait-until-indexed)
      (is (apply = (map :concept-id created-granules)))
      (is (= (range 1 (inc n)) (map :revision-id created-granules))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-granule-ingest-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
        granule-with-empty-body  (assoc granule :metadata "")
        {:keys [status errors]} (ingest/ingest-concept granule-with-empty-body)]
    (index/wait-until-indexed)
    (is (= 400 status))
    (is (re-find #"Request content is too short." (first errors)))))

;; Verify that the accept header works
(deftest granule-ingest-accept-header-response-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))]
    (testing "json response"
      (let [granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
            response (ingest/ingest-concept granule {:accept-format :json :raw? true})]
        (index/wait-until-indexed)
        (is (= {:concept-id "G1200000006-PROV1" :revision-id 1}
               (select-keys (ingest/parse-ingest-body :json response) [:concept-id :revision-id])))))
    (testing "xml response"
      (let [granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
            response (ingest/ingest-concept granule {:accept-format :xml :raw? true})]
        (index/wait-until-indexed)
        (is (= {:concept-id "G1200000007-PROV1" :revision-id 1}
               (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id])))))))

;; Verify that the accept header works with returned errors
(deftest granule-ingest-with-errors-accept-header-test
  (let [collection (data-umm-c/collection {:EntryTitle "Coll1"})]
    (testing "json response"
      (let [umm-granule (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"
                                                                                                  :granule-ur "Gran1"})
            granule (d/item->concept umm-granule)
            response (ingest/ingest-concept granule {:accept-format :json :raw? true})
            status (:status response)
            {:keys [errors]} (ingest/parse-ingest-body :json response)]
        (is (= [422 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
               [status errors]))))
    (testing "xml response"
      (let [umm-granule (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"
                                                                                                  :granule-ur "Gran1"})
            granule (d/item->concept umm-granule)
            response (ingest/ingest-concept granule {:accept-format :xml :raw? true})
            status (:status response)
            {:keys [errors]} (ingest/parse-ingest-body :xml response)]
        (is (= [422 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
               [status errors]))))))

;; Verify that the accept header works with deletions
(deftest delete-granule-with-accept-header-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))]
    (testing "json response"
      (let [granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
            _ (ingest/ingest-concept granule)
            response (ingest/delete-concept granule {:accept-format :json :raw? true})]
        (index/wait-until-indexed)
        (is (= {:concept-id "G1-PROV1" :revision-id 2}
             (select-keys (ingest/parse-ingest-body :json response) [:concept-id :revision-id])))))
    (testing "xml response"
      (let [granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G2-PROV1"}))
            _ (ingest/ingest-concept granule)
            response (ingest/delete-concept granule {:accept-format :xml :raw? true})]
        (index/wait-until-indexed)
        (is (= {:concept-id "G2-PROV1" :revision-id 2}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id])))))))

(deftest delete-granule-test
  (testing "It should be possible to delete existing concept and the operation without revision id should
           result in revision id 1 greater than max revision id of the concept prior to the delete"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
          ingest-result (ingest/ingest-concept granule)
          delete-result (ingest/delete-concept granule)
          ingest-revision-id (:revision-id ingest-result)
          delete-revision-id (:revision-id delete-result)]
      (index/wait-until-indexed)
      (is (= 1 (- delete-revision-id ingest-revision-id)))))
  (testing "Deleting existing concept with a revision-id should respect the revision id"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G2-PROV1"}))
          _ (ingest/ingest-concept granule)
          delete-result (ingest/delete-concept granule {:revision-id 5})
          delete-revision-id (:revision-id delete-result)]
      (index/wait-until-indexed)
      (is (= 5 delete-revision-id))
      (is (mdb/concept-exists-in-mdb? (:concept-id delete-result) 5)))))

;; Verify deleting non-existent concepts returns good error messages
(deftest delete-non-existing-concept-gives-good-error-message-test
  (testing "granule"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
          response (ingest/delete-concept granule {:raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Granule with native id \[.*?\] in provider \[PROV1\] does not exist"
                   (first errors))))))

;; Verify ingest is successful for request with content type that has parameters
(deftest content-type-with-parameter-ingest-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (d/item->concept
                  (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                         :format "application/echo10+xml; charset=utf-8"))
        {:keys [status errors]} (ingest/ingest-concept granule)]
    (index/wait-until-indexed)
    (is (= 201 status))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
        response (ingest/ingest-concept (assoc granule :format "") {:accept-format :json :raw? true})
         status (:status response)
        {:keys [errors]} (ingest/parse-ingest-body :json response)]
    (index/wait-until-indexed)
    (is (= 415 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify ingest behaves properly if request contains invalid content type.
(deftest invalid-content-type-ingest-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
        response (ingest/ingest-concept (assoc granule :format "blah") {:accept-format :json :raw? true})
        status (:status response)
        {:keys [errors]} (ingest/parse-ingest-body :json response)]
    (index/wait-until-indexed)
    (is (= 415 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify deleting same granule twice returns a 404
(deftest delete-same-granule-twice-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        granule (d/item->concept
                  (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
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
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Coll1"}))
        umm-granule (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"
                                                                                              :granule-ur "Gran1"})
        granule (d/item->concept umm-granule)
        _ (ingest/delete-concept (d/item->concept collection :echo10))
        {:keys [status errors]} (ingest/ingest-concept granule)]
    (index/wait-until-indexed)
    (is (= [422 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
           [status errors]))
    (is (not (mdb/concept-exists-in-mdb? "G1-PROV1" 0)))))

;; Verify that granules with embedded / (%2F) in the native-id are handled correctly
(deftest ingest-granule-with-slash-in-native-id-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        umm-granule (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:native-id "Name/With/Slashes"})
        granule (d/item->concept umm-granule)
        {:keys [concept-id revision-id] :as response} (ingest/ingest-concept granule)
        ingested-concept (mdb/get-concept concept-id)]
    (index/wait-until-indexed)
    (is (= 201 (:status response)))
    (is (mdb/concept-exists-in-mdb? concept-id revision-id))
    (is (= 1 revision-id))
    (is (= "Name/With/Slashes" (:native-id ingested-concept)))))

(deftest granule-schema-validation-test
  (are [concept-format validation-errors]
       (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
             umm-granule (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:native-id "Name/With/Slashes"})
             concept (d/item->concept
                       (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:beginning-date-time "2010-12-12T12:00:00Z"})
                       concept-format)
             {:keys [status errors]}
             (ingest/ingest-concept
               (assoc concept
                      :format (mt/format->mime-type concept-format)
                      :metadata (-> concept
                                    :metadata
                                    (str/replace "2010-12-12T12:00:00" "A")
                                    ;; this is to cause validation error for iso-smap format
                                    (str/replace "gmd:DS_Series" "XXXX"))))]
         (index/wait-until-indexed)
         (= [400 validation-errors] [status errors]))

       :echo10 ["Line 1 - cvc-datatype-valid.1.2.1: 'A.000Z' is not a valid value for 'dateTime'."
                "Line 1 - cvc-type.3.1.3: The value 'A.000Z' of element 'BeginningDateTime' is not valid."]

       :iso-smap ["Line 1 - cvc-elt.1: Cannot find the declaration of element 'XXXX'."]))

(deftest ingest-smap-iso-granule-test
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "correct"
                                                                                 :ShortName "S1"
                                                                                 :Version "V1"}))]
    (testing "Valid SMAP ISO granule with collection-ref attributes"
      (are3 [attrs]
        (let [granule (-> (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:granule-ur "Gran1"})
                          (assoc :collection-ref (umm-g/map->CollectionRef attrs))
                          (d/item->concept :iso-smap))
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
              granule (-> (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:granule-ur "Gran1"})
                          (assoc :collection-ref collection-ref)
                          (d/item->concept :iso-smap))
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
  (let [cddis-umm (-> "example_data/umm-json/1.2/CDDIS.json" io/resource slurp)
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
        granule (d/item->concept
                 (dg/granule-with-umm-spec-collection (json/parse-string cddis-umm true)
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
