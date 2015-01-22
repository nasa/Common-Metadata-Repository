(ns ^{:doc "CMR collection ingest integration tests"}
  cmr.system-int-test.ingest.collection-ingest-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [clojure.string :as string]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.old-ingest-util :as old-ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [clj-time.core :as t]
            [cmr.common.mime-types :as mt]
            [cmr.system-int-test.utils.search-util :as search]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; tests
;; ensure metadata, indexer and ingest apps are accessable on ports 3001, 3004 and 3002 resp;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Verify a new concept is ingested successfully.
(deftest collection-ingest-test
  (testing "ingest of a new concept"
    (let [concept (dc/collection-for-ingest {})
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id)))))

;; Collection with concept-id ingest and update scenarios.
(deftest collection-w-concept-id-ingest-test
  (let [supplied-concept-id "C1000-PROV1"
        concept (dc/collection-for-ingest {:concept-id supplied-concept-id
                                           :native-id "Atlantic-1"})]
    (testing "ingest of a new concept with concept-id present"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (ingest/concept-exists-in-mdb? concept-id revision-id))
        (is (= [supplied-concept-id 1] [concept-id revision-id]))))

    (testing "Update the concept with the concept-id"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (= [supplied-concept-id 2] [concept-id revision-id]))))

    (testing "update the concept without the concept-id"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept (dissoc concept :concept-id))]
        (is (= [supplied-concept-id 3] [concept-id revision-id]))))

    (testing "update concept with a different concept-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept (assoc concept :concept-id "C1111-PROV1"))]
        (is (= [400 ["Concept-id [C1111-PROV1] does not match the existing concept-id [C1000-PROV1] for native-id [Atlantic-1]"]]
               [status errors]))))))

;; Ingest same concept N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-collection-ingest-test
  (testing "ingest same concept n times ..."
    (let [n 4
          concept (dc/collection-for-ingest {})
          created-concepts (take n (repeatedly n #(ingest/ingest-concept concept)))]
      (is (apply = (map :concept-id created-concepts)))
      (is (= (range 1 (inc n)) (map :revision-id created-concepts))))))

(deftest update-collection-with-different-formats-test
  (testing "update collection in different formats ..."
    (doseq [[expected-rev coll-format] (map-indexed #(vector (inc %1) %2) [:echo10 :dif :iso19115 :iso-smap])]
      (let [coll (d/ingest "PROV1"
                           (dc/collection {:entry-id "S1"
                                           :short-name "S1"
                                           :version-id "V1"
                                           :entry-title "ET1"
                                           :long-name "L4"
                                           :summary (name coll-format)
                                           ;; The following fields are needed for DIF to pass xml validation
                                           :science-keywords [(dc/science-keyword {:category "upcase"
                                                                                   :topic "Cool"
                                                                                   :term "Mild"})]
                                           :organizations [(dc/org :distribution-center "Larc")]})
                           coll-format)]
        (index/refresh-elastic-index)
        (is (= expected-rev (:revision-id coll)))
        (is (= 1 (:hits (search/find-refs :collection {:keyword (name coll-format)}))))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-collection-ingest-test
  (let [concept-with-empty-body  (assoc (dc/collection-for-ingest {}) :metadata "")
        {:keys [status errors]} (ingest/ingest-concept concept-with-empty-body)]
    (is (= status 400))
    (is (re-find #"XML content is too short." (first errors)))))

;; Verify old DeleteTime concept results in 400 error.
(deftest old-delete-time-collection-ingest-test
  (let [coll (dc/collection {:delete-time "2000-01-01T12:00:00Z"})
        {:keys [status errors]} (ingest/ingest-concept
                                  (d/item->concept (assoc coll :provider-id "PROV1") :echo10))]
    (is (= status 400))
    (is (re-find #"DeleteTime 2000-01-01T12:00:00.000Z is before the current time." (first errors)))))

;; Verify non-existent concept deletion results in not found / 404 error.
;; TODO commented out this test because it causes other tests to fail.  This test is currently
;; returning a 401 because there is no ACL given ingest permission for a non-existent provider.
#_(deftest delete-non-existent-collection-test
  (let [concept (dc/collection-for-ingest {})
        fake-provider-id (str (:provider-id concept) (:native-id concept))
        non-existent-concept (assoc concept :provider-id fake-provider-id)
        {:keys [status]} (ingest/delete-concept non-existent-concept)]
    (is (= status 404))))

;; Verify existing concept can be deleted and operation results in revision id 1 greater than
;; max revision id of the concept prior to the delete
(deftest delete-collection-test-old
  (let [concept (dc/collection-for-ingest {})
        ingest-result (ingest/ingest-concept concept)
        delete-result (ingest/delete-concept concept)
        ingest-revision-id (:revision-id ingest-result)
        delete-revision-id (:revision-id delete-result)]
    (is (= 1 (- delete-revision-id ingest-revision-id)))))

(comment

  (ingest/reset)
  (ingest/create-provider "provguid1" "PROV1")
  (def coll1 (d/ingest "PROV1" (dc/collection)))
  (ingest/delete-concept coll1)
  (get-in user/system [:apps :metadata-db :db])
  )

(deftest delete-collection-test
  (let [coll1 (d/ingest "PROV1" (dc/collection))
        gran1 (d/ingest "PROV1" (dg/granule coll1))
        gran2 (d/ingest "PROV1" (dg/granule coll1))
        coll2 (d/ingest "PROV1" (dc/collection))
        gran3 (d/ingest "PROV1" (dg/granule coll2))]
    (index/refresh-elastic-index)

    ;; delete collection
    (is (= 200 (:status (ingest/delete-concept (d/item->concept coll1 :echo10)))))
    (index/refresh-elastic-index)

    (is (:deleted (ingest/get-concept (:concept-id coll1))) "The collection should be deleted")
    (is (not (ingest/concept-exists-in-mdb? (:concept-id gran1) (:revision-id gran1)))
        "Granules in the collection should be deleted")
    (is (not (ingest/concept-exists-in-mdb? (:concept-id gran2) (:revision-id gran2)))
        "Granules in the collection should be deleted")

    (is (empty? (:refs (search/find-refs :collection {"concept-id" (:concept-id coll1)}))))
    (is (empty? (:refs (search/find-refs :granule {"concept-id" (:concept-id gran1)}))))
    (is (empty? (:refs (search/find-refs :granule {"concept-id" (:concept-id gran2)}))))


    (is (ingest/concept-exists-in-mdb? (:concept-id coll2) (:revision-id coll2)))
    (is (ingest/concept-exists-in-mdb? (:concept-id gran3) (:revision-id gran3)))

    (is (d/refs-match?
          [coll2]
          (search/find-refs :collection {"concept-id" (:concept-id coll2)})))
    (is (d/refs-match?
          [gran3]
          (search/find-refs :granule {"concept-id" (:concept-id gran3)})))))


;; Verify ingest is successful for request with content type that has parameters
(deftest content-type-with-parameter-ingest-test
  (let [concept  (assoc (dc/collection-for-ingest {})
                        :format "application/echo10+xml; charset=utf-8")
        {:keys [status]} (ingest/ingest-concept concept)]
    (is (= status 200))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [concept-with-no-content-type  (assoc (dc/collection-for-ingest {}) :format "")
        {:keys [status errors]} (ingest/ingest-concept concept-with-no-content-type)]
    (is (= status 400))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify ingest behaves properly if request contains invalid  content type.
(deftest invalid-content-type-ingest-test
  (let [concept (assoc (dc/collection-for-ingest {}) :format "blah")
        {:keys [status errors]} (ingest/ingest-concept concept)]
    (is (= status 400))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify deleting same concept twice is not an error if ignore conflict is true.
(deftest delete-same-collection-twice-test
  (let [concept (dc/collection-for-ingest {})
        ingest-result (ingest/ingest-concept concept)
        delete1-result (ingest/delete-concept concept)
        delete2-result (ingest/delete-concept concept)]
    (is (= 200 (:status ingest-result)))
    (is (= 200 (:status delete1-result)))
    (is (= 200 (:status delete2-result)))))

;; Verify that collections with embedded / (%2F) in the native-id are handled correctly
(deftest ingest-collection-with-slash-in-native-id-test
  (let [crazy-id "`1234567890-=qwertyuiop[]\\asdfghjkl;'zxcvbnm,./ ~!@#$%^&*()_+QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?"
        collection (dc/collection-for-ingest {:entry-title crazy-id})
        {:keys [concept-id revision-id] :as response} (ingest/ingest-concept collection)
        ingested-concept (ingest/get-concept concept-id)]
    (is (= 200 (:status response)))
    (is (ingest/concept-exists-in-mdb? concept-id revision-id))
    (is (= 1 revision-id))
    (is (= crazy-id (:native-id ingested-concept)))

    (testing "delete"
      (let [delete-result (ingest/delete-concept ingested-concept)]
        (is (= 200 (:status delete-result)))))))

(deftest schema-validation-test
  (are [concept-format validation-errors]
       (let [concept (dc/collection-for-ingest
                       {:beginning-date-time "2010-12-12T12:00:00Z"} concept-format)
             {:keys [status errors]}
             (ingest/ingest-concept
               (assoc concept
                      :format (mt/format->mime-type concept-format)
                      :metadata (-> concept
                                    :metadata
                                    (string/replace "2010-12-12T12:00:00" "A")
                                    ;; this is to cause validation error for iso19115 format
                                    (string/replace "fileIdentifier" "XXXX")
                                    ;; this is to cause validation error for iso-smap format
                                    (string/replace "gmd:DS_Series" "XXXX"))))]
         (= [400 validation-errors] [status errors]))

       :echo10 ["Line 1 - cvc-datatype-valid.1.2.1: 'A.000Z' is not a valid value for 'dateTime'."
                "Line 1 - cvc-type.3.1.3: The value 'A.000Z' of element 'BeginningDateTime' is not valid."]

       :dif [(str "Line 1 - cvc-complex-type.2.4.a: Invalid content was found "
                  "starting with element 'Temporal_Coverage'. "
                  "One of '{\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Data_Set_Citation, "
                  "\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Personnel, "
                  "\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Discipline, "
                  "\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Parameters}' is expected.")]

       :iso19115 [(str "Line 1 - cvc-complex-type.2.4.a: Invalid content was found "
                       "starting with element 'gmd:XXXX'. One of "
                       "'{\"http://www.isotc211.org/2005/gmd\":fileIdentifier, "
                       "\"http://www.isotc211.org/2005/gmd\":language, "
                       "\"http://www.isotc211.org/2005/gmd\":characterSet, "
                       "\"http://www.isotc211.org/2005/gmd\":parentIdentifier, "
                       "\"http://www.isotc211.org/2005/gmd\":hierarchyLevel, "
                       "\"http://www.isotc211.org/2005/gmd\":hierarchyLevelName, "
                       "\"http://www.isotc211.org/2005/gmd\":contact}' is expected.")]

       :iso-smap ["Line 1 - cvc-elt.1: Cannot find the declaration of element 'XXXX'."]))

