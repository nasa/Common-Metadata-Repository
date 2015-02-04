(ns ^{:doc "CMR granule ingest integration tests"}
  cmr.system-int-test.ingest.granule-ingest-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.string :as string]
            [cmr.common.mime-types :as mt]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.old-ingest-util :as old-ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; Tests that a granule referencing a collection that had multiple concept ids (the native id changed
;; but the shortname or dataset id did not) will reference the correct collection.
;; See CMR-1104
(deftest granule-referencing-collection-with-changing-concept-id-test
  (let [common-fields {:entry-title "coll1" :short-name "short1" :version-id "V1"}
        orig-coll (dc/collection-concept (assoc common-fields :native-id "native1"))
        _ (ingest/ingest-concept orig-coll)

        ;; delete the collection
        deleted-response (ingest/delete-concept orig-coll)

        ;; Create collection again with same details but a different native id
        new-coll (d/ingest "PROV1" (dc/collection (assoc common-fields :native-id "native2")))

        ;; Create granules associated with the collection fields.
        gran1 (d/ingest "PROV1" (update-in (dg/granule new-coll) [:collection-ref]
                                           dissoc :short-name :version-id))
        gran2 (d/ingest "PROV1" (update-in (dg/granule new-coll) [:collection-ref]
                                           dissoc :entry-title))]

    ;; Make sure the granules reference the correct collection
    (is (= (:concept-id new-coll)
           (get-in (ingest/get-concept (:concept-id gran1) (:revision-id gran1))
                   [:extra-fields :parent-collection-id])))

    (is (= (:concept-id new-coll)
           (get-in (ingest/get-concept (:concept-id gran2) (:revision-id gran2))
                   [:extra-fields :parent-collection-id])))))

;; Verify a new granule is ingested successfully.
(deftest granule-ingest-test
  (testing "ingest of a new granule"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          granule (dg/umm-granule->granule-concept (dg/granule collection))
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id)))))

;; Verify a new granule with concept-id is ingested successfully.
(deftest granule-w-concept-id-ingest-test
  (testing "ingest of a new granule with concept-id present"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          supplied-concept-id "G1-PROV1"
          granule (dg/umm-granule->granule-concept
                    (dg/granule collection {:concept-id supplied-concept-id}))
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= supplied-concept-id concept-id))
      (is (= 1 revision-id)))))

;; Ingest same granule N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-granule-ingest-test
  (testing "ingest same granule n times ..."
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          n 4
          granule (dg/umm-granule->granule-concept (dg/granule collection {:concept-id "G1-PROV1"}))
          created-granules (take n (repeatedly n #(ingest/ingest-concept granule)))]
      (is (apply = (map :concept-id created-granules)))
      (is (= (range 1 (inc n)) (map :revision-id created-granules))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-granule-ingest-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (dg/umm-granule->granule-concept (dg/granule collection))
        granule-with-empty-body  (assoc granule :metadata "")
        {:keys [status errors]} (ingest/ingest-concept granule-with-empty-body)]
    (is (= 400 status))
    (is (re-find #"XML content is too short." (first errors)))))

;; Verify existing granule can be deleted and operation results in revision id 1 greater than
;; max revision id of the granule prior to the delete
(deftest delete-granule-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (dg/umm-granule->granule-concept (dg/granule collection {:concept-id "G1-PROV1"}))
        ingest-result (ingest/ingest-concept granule)
        delete-result (ingest/delete-concept granule)
        ingest-revision-id (:revision-id ingest-result)
        delete-revision-id (:revision-id delete-result)]
    (is (= 1 (- delete-revision-id ingest-revision-id)))))

;; Verify ingest is successful for request with content type that has parameters
(deftest content-type-with-parameter-ingest-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (dg/umm-granule->granule-concept
                  (assoc (dg/granule collection)
                         :format "application/echo10+xml; charset=utf-8"))
        {:keys [status errors]} (ingest/ingest-concept granule)]
    (is (= 200 status))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (dg/umm-granule->granule-concept (dg/granule collection))
        {:keys [status errors]} (ingest/ingest-concept (assoc granule :format ""))]
    (is (= 400 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify ingest behaves properly if request contains invalid  content type.
(deftest invalid-content-type-ingest-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (dg/umm-granule->granule-concept (dg/granule collection))
        {:keys [status errors]} (ingest/ingest-concept (assoc granule :format "blah"))]
    (is (= 400 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify deleting same granule twice is not an error if ignore conflict is true.
(deftest delete-same-granule-twice-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        granule (dg/umm-granule->granule-concept
                  (dg/granule collection {:concept-id "G1-PROV1"}))
        ingest-result (ingest/ingest-concept granule)
        delete1-result (ingest/delete-concept granule)
        delete2-result (ingest/delete-concept granule)]
    (is (= 200 (:status ingest-result)))
    (is (= 200 (:status delete1-result)))
    (is (= 200 (:status delete2-result)))))

;; Verify that attempts to ingest a granule whose parent does not exist result in a 404
(deftest ingest-orphan-granule-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        umm-granule (dg/granule collection {:concept-id "G1-PROV1"})
        granule (dg/umm-granule->granule-concept umm-granule)
        _ (ingest/delete-concept (d/item->concept collection :echo10))
        {:keys [status]} (ingest/ingest-concept granule)]
    (is (= 404 status))
    (is (not (ingest/concept-exists-in-mdb? "G1-PROV1" 0)))))

;; Verify that granules with embedded / (%2F) in the native-id are handled correctly
(deftest ingest-granule-with-slash-in-native-id-test
  (let [collection (d/ingest "PROV1" (dc/collection {}))
        umm-granule (dg/granule collection {:native-id "Name/With/Slashes"})
        granule (dg/umm-granule->granule-concept umm-granule)
        {:keys [concept-id revision-id] :as response} (ingest/ingest-concept granule)
        ingested-concept (ingest/get-concept concept-id)]
    (is (= 200 (:status response)))
    (is (ingest/concept-exists-in-mdb? concept-id revision-id))
    (is (= 1 revision-id))
    (is (= "Name/With/Slashes" (:native-id ingested-concept)))))

(deftest granule-schema-validation-test
  (are [concept-format validation-errors]
       (let [collection (d/ingest "PROV1" (dc/collection {}))
             umm-granule (dg/granule collection {:native-id "Name/With/Slashes"})
             concept (dg/umm-granule->granule-concept
                       (dg/granule collection {:beginning-date-time "2010-12-12T12:00:00Z"})
                       concept-format)
             {:keys [status errors]}
             (ingest/ingest-concept
               (assoc concept
                      :format (mt/format->mime-type concept-format)
                      :metadata (-> concept
                                    :metadata
                                    (string/replace "2010-12-12T12:00:00" "A")
                                    ;; this is to cause validation error for iso-smap format
                                    (string/replace "gmd:DS_Series" "XXXX"))))]
         (= [400 validation-errors] [status errors]))

       :echo10 ["Line 1 - cvc-datatype-valid.1.2.1: 'A.000Z' is not a valid value for 'dateTime'."
                "Line 1 - cvc-type.3.1.3: The value 'A.000Z' of element 'BeginningDateTime' is not valid."]

       :iso-smap ["Line 1 - cvc-elt.1: Cannot find the declaration of element 'XXXX'."]))

