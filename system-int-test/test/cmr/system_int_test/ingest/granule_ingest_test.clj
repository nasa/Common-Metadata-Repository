(ns cmr.system-int-test.ingest.granule-ingest-test
  "CMR granule ingest integration tests"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.search-util :as search]
            [clojure.string :as str]
            [cmr.common.mime-types :as mt]
            [cmr.umm.granule :as umm-g]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.indexer.system :as indexer-system]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; Tests that a granule referencing a collection that had multiple concept ids (the native id changed
;; but the shortname or dataset id did not) will reference the correct collection.
;; See CMR-1104
(deftest granule-referencing-collection-with-changing-concept-id-test
  (let [common-fields {:entry-title "coll1" :short-name "short1" :version-id "V1" :entry-id "short1_V1" }
        orig-coll (dc/collection-concept (assoc common-fields :native-id "native1"))
        _ (ingest/ingest-concept orig-coll)

        ;; delete the collection
        deleted-response (ingest/delete-concept orig-coll)

        ;; Create collection again with same details but a different native id
        new-coll (d/ingest "PROV1" (dc/collection (assoc common-fields :native-id "native2")))

        ;; Create granules associated with the collection fields.
        gran1 (d/ingest "PROV1" (update-in (dg/granule new-coll) [:collection-ref]
                                           dissoc :short-name :version-id :entry-id))
        gran2 (d/ingest "PROV1" (update-in (dg/granule new-coll) [:collection-ref]
                                           dissoc :entry-title :entry-id))
        gran3 (d/ingest "PROV1" (update-in (dg/granule new-coll) [:collection-ref]
                                           dissoc :short-name :version-id :entry-title))]
    (index/wait-until-indexed)
    ;; Make sure the granules reference the correct collection
    (is (= (:concept-id new-coll)
           (get-in (ingest/get-concept (:concept-id gran1) (:revision-id gran1))
                   [:extra-fields :parent-collection-id])))

    (is (= (:concept-id new-coll)
           (get-in (ingest/get-concept (:concept-id gran2) (:revision-id gran2))
                   [:extra-fields :parent-collection-id])))
    (is (= (:concept-id new-coll)
           (get-in (ingest/get-concept (:concept-id gran3) (:revision-id gran3))
                   [:extra-fields :parent-collection-id])))))

;; Verify a new granule is ingested successfully.
(deftest granule-ingest-test
  (testing "ingest of a new granule"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          granule (d/item->concept (dg/granule collection))
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (index/wait-until-indexed)
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id)))))

;; Verify a new granule with concept-id is ingested successfully.
(deftest granule-w-concept-id-ingest-test
  (testing "ingest of a new granule with concept-id present"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          supplied-concept-id "G1-PROV1"
          granule (d/item->concept
                    (dg/granule collection {:concept-id supplied-concept-id}))
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (index/wait-until-indexed)
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= supplied-concept-id concept-id))
      (is (= 1 revision-id)))))

;; Ingest same granule N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-granule-ingest-test
  (testing "ingest same granule n times ..."
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          n 4
          granule (d/item->concept (dg/granule collection {:concept-id "G1-PROV1"}))
          created-granules (doall (take n (repeatedly n #(ingest/ingest-concept granule))))]
      (index/wait-until-indexed)
      (is (apply = (map :concept-id created-granules)))
      (is (= (range 1 (inc n)) (map :revision-id created-granules))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-granule-ingest-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (d/item->concept (dg/granule collection))
        granule-with-empty-body  (assoc granule :metadata "")
        {:keys [status errors]} (ingest/ingest-concept granule-with-empty-body)]
    (index/wait-until-indexed)
    (is (= 400 status))
    (is (re-find #"XML content is too short." (first errors)))))

;; Verify that the accept header works
(deftest granule-ingest-accept-header-response-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))]
    (testing "json response"
      (let [granule (d/item->concept (dg/granule collection))
            response (ingest/ingest-concept granule {:accept-format :json :raw? true})]
        (is (= {:concept-id "G1200000001-PROV1" :revision-id 1}
               (ingest/parse-ingest-response :json response)))))
    (testing "xml response"
      (let [granule (d/item->concept (dg/granule collection))
            response (ingest/ingest-concept granule {:accept-format :xml :raw? true})]
        (is (= {:concept-id "G1200000002-PROV1" :revision-id 1}
               (ingest/parse-ingest-response :xml response)))))))

;; Verify that the accept header works with returned errors
(deftest granule-ingest-with-errors-accept-header-test
  (let [collection (dc/collection {:entry-title "Coll1"})]
    (testing "json response"
      (let [umm-granule (dg/granule collection {:concept-id "G1-PROV1"
                                                :granule-ur "Gran1"})
            granule (d/item->concept umm-granule)
            response (ingest/ingest-concept granule {:accept-format :json :raw? true})
            status (:status response)
            {:keys [errors]} (ingest/parse-ingest-response :json response)]
        (is (= [400 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
               [status errors]))))
    (testing "xml response"
      (let [umm-granule (dg/granule collection {:concept-id "G1-PROV1"
                                                :granule-ur "Gran1"})
            granule (d/item->concept umm-granule)
            response (ingest/ingest-concept granule {:accept-format :xml :raw? true})
            status (:status response)
            {:keys [errors]} (ingest/parse-ingest-response :xml response)]
        (is (= [400 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
               [status errors]))))))

;; Verify that the accept header works with deletions
(deftest delete-granule-with-accept-header-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))]
    (testing "json response"
      (let [granule (d/item->concept (dg/granule collection {:concept-id "G1-PROV1"}))
            _ (ingest/ingest-concept granule)
            response (ingest/delete-concept granule {:accept-format :json :raw? true})]
         (is (= {:concept-id "G1-PROV1" :revision-id 2}
             (ingest/parse-ingest-response :json response)))))
    (testing "xml response"
      (let [granule (d/item->concept (dg/granule collection {:concept-id "G2-PROV1"}))
            _ (ingest/ingest-concept granule)
            response (ingest/delete-concept granule {:accept-format :xml :raw? true})]
        (is (= {:concept-id "G2-PROV1" :revision-id 2}
             (ingest/parse-ingest-response :xml response)))))))

;; Verify existing granule can be deleted and operation results in revision id 1 greater than
;; max revision id of the granule prior to the delete
(deftest delete-granule-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (d/item->concept (dg/granule collection {:concept-id "G1-PROV1"}))
        ingest-result (ingest/ingest-concept granule)
        delete-result (ingest/delete-concept granule)
        ingest-revision-id (:revision-id ingest-result)
        delete-revision-id (:revision-id delete-result)]
    (index/wait-until-indexed)
    (is (= 1 (- delete-revision-id ingest-revision-id)))))

;; Verify ingest is successful for request with content type that has parameters
(deftest content-type-with-parameter-ingest-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (d/item->concept
                  (assoc (dg/granule collection)
                         :format "application/echo10+xml; charset=utf-8"))
        {:keys [status errors]} (ingest/ingest-concept granule)]
    (index/wait-until-indexed)
    (is (= 200 status))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (d/item->concept (dg/granule collection))
        response (ingest/ingest-concept (assoc granule :format "") {:accept-format :json :raw? true})
         status (:status response)
        {:keys [errors]} (ingest/parse-ingest-response :json response)]
    (index/wait-until-indexed)
    (is (= 400 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify ingest behaves properly if request contains invalid  content type.
(deftest invalid-content-type-ingest-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (d/item->concept (dg/granule collection))
        response (ingest/ingest-concept (assoc granule :format "blah") {:accept-format :json :raw? true})
        status (:status response)
        {:keys [errors]} (ingest/parse-ingest-response :json response)]
    (index/wait-until-indexed)
    (is (= 400 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify deleting same granule twice is not an error if ignore conflict is true.
(deftest delete-same-granule-twice-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (d/item->concept
                  (dg/granule collection {:concept-id "G1-PROV1"}))
        ingest-result (ingest/ingest-concept granule)
        delete1-result (ingest/delete-concept granule)
        delete2-result (ingest/delete-concept granule)]
    (index/wait-until-indexed)
    (is (= 200 (:status ingest-result)))
    (is (= 200 (:status delete1-result)))
    (is (= 200 (:status delete2-result)))))

;; Verify that attempts to ingest a granule whose parent does not exist result in a 400 error
(deftest ingest-orphan-granule-test
  (let [collection (d/ingest "PROV1" (dc/collection {:entry-title "Coll1"}))
        umm-granule (dg/granule collection {:concept-id "G1-PROV1"
                                            :granule-ur "Gran1"})
        granule (d/item->concept umm-granule)
        _ (ingest/delete-concept (d/item->concept collection :echo10))
        {:keys [status errors]} (ingest/ingest-concept granule)]
    (is (= [400 ["Collection with Entry Title [Coll1] referenced in granule [Gran1] provider [PROV1] does not exist."]]
           [status errors]))
    (is (not (ingest/concept-exists-in-mdb? "G1-PROV1" 0)))))

;; Verify that granules with embedded / (%2F) in the native-id are handled correctly
(deftest ingest-granule-with-slash-in-native-id-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        umm-granule (dg/granule collection {:native-id "Name/With/Slashes"})
        granule (d/item->concept umm-granule)
        {:keys [concept-id revision-id] :as response} (ingest/ingest-concept granule)
        ingested-concept (ingest/get-concept concept-id)]
    (index/wait-until-indexed)
    (is (= 200 (:status response)))
    (is (ingest/concept-exists-in-mdb? concept-id revision-id))
    (is (= 1 revision-id))
    (is (= "Name/With/Slashes" (:native-id ingested-concept)))))

(deftest granule-schema-validation-test
  (are [concept-format validation-errors]
       (let [collection (d/ingest "PROV1" (dc/collection {}))
             umm-granule (dg/granule collection {:native-id "Name/With/Slashes"})
             concept (d/item->concept
                       (dg/granule collection {:beginning-date-time "2010-12-12T12:00:00Z"})
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
  (let [collection (d/ingest "PROV1" (dc/collection {:entry-title "correct"
                                                     :short-name "S1"
                                                     :version-id "V1"}))]
    (testing "Valid SMAP ISO granule with collection-ref attributes"
      (are [attrs]
           (let [granule (-> (dg/granule collection {:granule-ur "Gran1"})
                             (assoc :collection-ref (umm-g/map->CollectionRef attrs))
                             (d/item->concept :iso-smap))
                 {:keys [status]} (ingest/ingest-concept granule)]
             (= 200 status))

           {:entry-title "correct"}
           {:short-name "S1" :version-id "V1"}
           {:entry-title "correct" :short-name "S1"}
           {:entry-title "correct" :version-id "V1"}
           {:entry-title "correct" :short-name "S1" :version-id "V1"}))

    (testing "Invalid SMAP ISO granule with collection-ref attributes"
      (are [attrs expected-errors]
           (let [collection-ref (umm-g/map->CollectionRef attrs)
                 granule (-> (dg/granule collection {:granule-ur "Gran1"})
                             (assoc :collection-ref collection-ref)
                             (d/item->concept :iso-smap))
                 {:keys [status errors]} (ingest/ingest-concept granule)]
             (= [400 expected-errors] [status errors]))

           {}
           ["Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."]

           {:entry-title "wrong"}
           ["Collection with Entry Title [wrong] referenced in granule [Gran1] provider [PROV1] does not exist."]

           {:short-name "S2"}
           ["Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."]

           {:version-id "V2"}
           ["Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."]

           {:short-name "S2" :version-id "V1"}
           ["Collection with Short Name [S2], Version Id [V1] referenced in granule [Gran1] provider [PROV1] does not exist."]

           {:entry-title "correct" :short-name "S2" :version-id "V1"}
           ["Collection with Entry Title [correct], Short Name [S2], Version Id [V1] referenced in granule [Gran1] provider [PROV1] does not exist."]))))

