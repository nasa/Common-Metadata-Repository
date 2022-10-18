(ns cmr.system-int-test.ingest.collection-ingest-test
  "CMR collection ingest integration tests.

  For collection permissions tests, see `provider-ingest-permissions-test`."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as test-util]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]
   [cmr.umm-spec.test.location-keywords-helper :as location-keywords-helper]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(def test-context (location-keywords-helper/setup-context-for-test))

;; tests
;; ensure metadata, indexer and ingest apps are accessable on ports 3001, 3004 and 3002 resp;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Verify a new concept fails ingest when StandardProduct validation fails. 
(deftest standard-validation-test
  (ingest/create-provider {:provider-guid "provguid_consortium3" :provider-id "PROV3" :consortiums "geoss"})
  (ingest/create-provider {:provider-guid "provguid_consortium4" :provider-id "PROV4" :consortiums "eosdis geoss"})
  (let [coll3-non-eosdis-consortium (ingest/ingest-concept
                                     (data-umm-c/collection-concept 
                                      {:provider-id "PROV3"
                                       :StandardProduct true}
                                      :umm-json))
        coll4-wrong-collection-data-type (ingest/ingest-concept
                                          (data-umm-c/collection-concept
                                           {:provider-id "PROV4"
                                            :StandardProduct true
                                            :CollectionDataType "NEAR_REAL_TIME"}
                                           :umm-json))]
     (is (= ["Standard product validation failed: Standard Product designation is only allowed for NASA data products. This collection is being ingested using a non-NASA provider which means the record is not a NASA record. Please remove the StandardProduct element from the record."]
            (:errors coll3-non-eosdis-consortium)))
     (is (= ["Standard product validation failed: Standard Product cannot be true with the CollectionDataType being one of the following values: NEAR_REAL_TIME, LOW_LATENCY, or EXPEDITED. The CollectionDataType is [NEAR_REAL_TIME]."]
            (:errors coll4-wrong-collection-data-type)))))
    
;; Verify a new concept is ingested successfully.
(deftest collection-ingest-test
  (testing "ingest of a new concept"
    (let [concept (data-umm-c/collection-concept {})
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id))))
  (testing "ingest of a concept with a revision id"
    (let [concept (data-umm-c/collection-concept {:revision-id 5})
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (index/wait-until-indexed)
      (is (= 5 revision-id))
      (is (mdb/concept-exists-in-mdb? concept-id 5)))))

;; Verify a concept can be ingested twice to get two revisions and ignore_conflict can impact the reindex status.
(deftest collection-ingest-test
  (testing "ingest of a new concept twice to get two revisions, and reindex revision 1, with ignore_conflict on and off."
    (let [concept (data-umm-c/collection-concept {})
          ingested-concept1 (ingest/ingest-concept concept)
          ingested-concept2 (ingest/ingest-concept concept)
          concept-id1 (:concept-id ingested-concept1)
          concept-id2 (:concept-id ingested-concept2)
          revision-id1 (:revision-id ingested-concept1)
          revision-id2 (:revision-id ingested-concept2)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id1 revision-id1))
      (is (mdb/concept-exists-in-mdb? concept-id2 revision-id2))
      (is (= 1 revision-id1))
      (is (= 2 revision-id2))
      (is (= concept-id1 concept-id2))
      (let [response1 (index/reindex-concept-with-ignore-conflict-param concept-id1 revision-id1)
            response2 (index/reindex-concept-with-ignore-conflict-param concept-id1 revision-id1 "not-false")
            response3 (index/reindex-concept-with-ignore-conflict-param concept-id1 revision-id1 "false")]
        (is (= 201 (:status response1)) (:body response1))
        (is (= 201 (:status response2)) (:body response2))
        (is (= 409 (:status response3)) (:body response3))))))

;; Verify that user-id is saved from User-Id or token header
(deftest collection-ingest-user-id-test
  (testing "ingest of new concept"
    (util/are2 [ingest-headers expected-user-id]
      (let [concept (data-umm-c/collection-concept {})
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept ingest-headers)]
        (index/wait-until-indexed)
        (ingest/assert-user-id concept-id revision-id expected-user-id))

      "user id from token"
      {:token (echo-util/login (system/context) "user1")} "user1"

      "user id from user-id header"
      {:user-id "user2"} "user2"

      "both user-id and token in the header results in the revision getting user id from user-id header"
      {:token (echo-util/login (system/context) "user3")
       :user-id "user4"} "user4"

      "neither user-id nor token in the header"
      {} nil))
  (testing "update of existing concept with new user-id"
    (util/are2 [ingest-header1 expected-user-id1
                ingest-header2 expected-user-id2
                ingest-header3 expected-user-id3
                ingest-header4 expected-user-id4]
      (let [concept (data-umm-c/collection-concept {})
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept ingest-header1)]
        (ingest/ingest-concept concept ingest-header2)
        (ingest/delete-concept concept ingest-header3)
        (ingest/ingest-concept concept ingest-header4)
        (index/wait-until-indexed)
        (ingest/assert-user-id concept-id revision-id expected-user-id1)
        (ingest/assert-user-id concept-id (inc revision-id) expected-user-id2)
        (ingest/assert-user-id concept-id (inc (inc revision-id)) expected-user-id3)
        (ingest/assert-user-id concept-id (inc (inc (inc revision-id))) expected-user-id4))

      "user id from token"
      {:token (echo-util/login (system/context) "user1")} "user1"
      {:token (echo-util/login (system/context) "user2")} "user2"
      {:token (echo-util/login (system/context) "user3")} "user3"
      {:token nil} nil

      "user id from user-id header"
      {:user-id "user1"} "user1"
      {:user-id "user2"} "user2"
      {:user-id "user3"} "user3"
      {:user-id nil} nil)))

;; Verify deleting non-existent concepts returns good error messages
(deftest deletion-of-non-existent-concept-error-message-test
  (testing "collection"
    (let [concept (data-umm-c/collection-concept {})
          response (ingest/delete-concept concept {:raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Collection with native id \[.*?\] in provider \[PROV1\] does not exist"
                   (first errors))))))

;; Collection with concept-id ingest and update scenarios.
(deftest collection-w-concept-id-ingest-test
  (let [supplied-concept-id "C1000-PROV1"
        concept (data-umm-c/collection-concept {:concept-id supplied-concept-id
                                                :native-id "Atlantic-1"})]
    (testing "ingest of a new concept with concept-id present"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (index/wait-until-indexed)
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [supplied-concept-id 1] [concept-id revision-id]))))

    (testing "Update the concept with the concept-id"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (index/wait-until-indexed)
        (is (= [supplied-concept-id 2] [concept-id revision-id]))))

    (testing "update the concept without the concept-id"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept (dissoc concept :concept-id))]
        (index/wait-until-indexed)
        (is (= [supplied-concept-id 3] [concept-id revision-id]))))

    (testing "update concept with a different concept-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept (assoc concept :concept-id "C1111-PROV1"))]
        (index/wait-until-indexed)
        (is (= [422 ["Concept-id [C1111-PROV1] does not match the existing concept-id [C1000-PROV1] for native-id [Atlantic-1]"]]
               [status errors]))))))

;; Verify that the accept header works
(deftest collection-ingest-accept-header-response-test
  (testing "json response"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E1"
                                                  :ShortName "S1"
                                                  :concept-id "C1200000000-PROV1"})
          response (ingest/ingest-concept concept {:accept-format :json :raw? true})]
      (is (= {:concept-id (:concept-id concept) :revision-id 1}
             (select-keys (ingest/parse-ingest-body :json response) [:concept-id :revision-id])))))
  (testing "xml response"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E2"
                                                  :ShortName "S2"
                                                  :concept-id "C1200000001-PROV1"})
          response (ingest/ingest-concept concept {:accept-format :xml :raw? true})]
      (is (= {:concept-id (:concept-id concept) :revision-id 1}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id]))))))

;; Verify that the accept header works with returned errors
(deftest collection-ingest-with-errors-accept-header-test
  (testing "json response"
    (let [concept-with-empty-body  (assoc (data-umm-c/collection-concept {}) :metadata "")
          response (ingest/ingest-concept concept-with-empty-body
                                          {:accept-format :json :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :json response)]
      (is (re-find #"Request content is too short." (first errors)))))
  (testing "xml response"
    (let [concept-with-empty-body  (assoc (data-umm-c/collection-concept {}) :metadata "")
          response (ingest/ingest-concept concept-with-empty-body
                                          {:accept-format :xml :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Request content is too short." (first errors))))))

;; Verify that XML is returned for ingest errros when the headers aren't set
(deftest collection-ingest-with-errors-no-accept-header-test
  (testing "xml response"
    (let [concept-with-empty-body  (assoc (data-umm-c/collection-concept {}) :metadata "")
          response (ingest/ingest-concept concept-with-empty-body
                                          {:raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Request content is too short." (first errors))))))

;; Verify that the accept header works with deletions
(deftest delete-collection-with-accept-header-test
  (testing "json response"
    (let [coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                      (data-umm-c/collection {:EntryTitle "E1"
                                                                              :ShortName "S1"}))
          response (ingest/delete-concept (data-core/umm-c-collection->concept coll1 :echo10)
                                          {:accept-format :json
                                           :raw? true})]
      (is (= {:concept-id (:concept-id coll1) :revision-id 2}
             (select-keys (ingest/parse-ingest-body :json response) [:concept-id :revision-id])))))
  (testing "xml response"
    (let [coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                      (data-umm-c/collection {:EntryTitle "E2"
                                                                              :ShortName "S2"}))
          _ (index/wait-until-indexed)
          response (ingest/delete-concept (data-core/umm-c-collection->concept coll1 :echo10)
                                          {:accept-format :xml
                                           :raw? true})]
      (is (= {:concept-id (:concept-id coll1) :revision-id 2}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id]))))))

;; Verify that XML is returned for deletion errros when the accept header isn't set
(deftest collection-deletion-with-errors-no-accept-header-test
  (testing "xml response"
    (let [concept (data-umm-c/collection-concept {})
          response (ingest/delete-concept concept {:raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Collection with native id \[.*?\] in provider \[PROV1\] does not exist"
                   (first errors))))))

;; Verify that xml response is returned for ingests of xml content type
(deftest collection-ingest-with-reponse-format-from-content-type
  (testing "echo10"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E1"
                                                  :ShortName "S1"
                                                  :concept-id "C1-PROV1"} :echo10)
          response (ingest/ingest-concept concept {:raw? true})]
      (is (= {:concept-id "C1-PROV1" :revision-id 1}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id])))))
  (testing "dif"
    (let [concept (data-core/umm-c-collection->concept (assoc (data-umm-c/collection {:EntryTitle "E2"
                                                                                      :ShortName "S2"
                                                                                      :concept-id "C2-PROV1"})
                                                        :provider-id "PROV1") :dif)
          response (ingest/ingest-concept concept {:raw? true})]
      (is (= {:concept-id "C2-PROV1" :revision-id 1}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id])))))
  (testing "iso"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E3"
                                                  :ShortName "S3"
                                                  :concept-id "C3-PROV1"} :iso-smap)
          response (ingest/ingest-concept concept {:raw? true})]
      (is (= {:concept-id "C3-PROV1" :revision-id 1}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id]))))))

;; Note entry-id only exists in the DIF format.  For other formats we set the entry ID to be a
;; a concatenation of short name and version ID.
(deftest collection-w-entry-id-validation-test
  (let [coll1-1 (data-umm-c/collection {:concept-id "C1-PROV1"
                                        :ShortName "EID-1"
                                        :EntryTitle "ET-1"
                                        :native-id "NID-1"})
        coll1-2 (assoc-in coll1-1 [:ShortName] "EID-2")
        coll1-3 (data-umm-c/collection {:concept-id "C3-PROV1"
                                        :ShortName "EID-3"
                                        :EntryTitle "ET-3"
                                        :native-id "NID-3"})
        coll1-4 (assoc-in coll1-3 [:ShortName] "EID-4")
        ;; ingest the collections/granules for test
        _ (data-core/ingest-umm-spec-collection "PROV1" coll1-1 {:format :dif})
        coll3 (data-core/ingest-umm-spec-collection "PROV1" coll1-3 {:format :dif})
        gran1 (data-core/ingest "PROV1" (granule/granule-with-umm-spec-collection coll3 (:concept-id coll3) {:granule-ur "Granule1"}))
        gran2 (data-core/ingest "PROV1" (granule/granule-with-umm-spec-collection coll3 (:concept-id coll3) {:granule-ur "Granule2"}))]
    (index/wait-until-indexed)

    (testing "update the collection with a different entry-id is OK"
      (let [{:keys [status concept-id revision-id errors]}
            (data-core/ingest-umm-spec-collection "PROV1" coll1-2 {:format :dif})]
        (is (= ["C1-PROV1" 2 200 nil] [concept-id revision-id status errors]))))

    (testing "update the collection that has granules with a different entry-id is allowed"
      ;; For CMR-2403 we decided to temporary allow collection identifiers to be updated even
      ;; with existing granules for the collection. We will change this with CMR-2485.
      (let [{:keys [status concept-id revision-id errors]}
            (data-core/ingest-umm-spec-collection "PROV1" coll1-4 {:format :dif :allow-failure? true})]
        (is (= ["C3-PROV1" 2 200 nil] [concept-id revision-id status errors]))))

    (testing "ingest collection with entry-id used by a different collection latest revision within the same provider is invalid"
      (let [{:keys [status errors]} (data-core/ingest-umm-spec-collection
                                     "PROV1"
                                     (assoc coll1-2
                                            :concept-id "C2-PROV1"
                                            :native-id "NID-2"
                                            :EntryTitle "EID-2")
                                     {:format :dif :allow-failure? true})]
        (is (= [409 ["The Short Name [EID-2] and Version Id [V1] combined must be unique. The following concepts with the same Short Name and Version Id were found: [C1-PROV1]."]]
               [status errors]))))

    (testing "ingest collection with entry-id used by a different collection, but not the latest revision within the same provider is OK"
      (let [{:keys [status concept-id revision-id errors]}
            (data-core/ingest-umm-spec-collection
             "PROV1"
             (assoc coll1-1
                    :concept-id "C2-PROV1"
                    :native-id "NID-2"
                    :EntryTitle "EID-2")
             {:format :dif :allow-failure? true})]
        (is (= ["C2-PROV1" 1 201 nil] [concept-id revision-id status errors]))))

    (testing "entry-id and entry-title constraint violations return multiple errors"
      (let [{:keys [status errors]} (data-core/ingest-umm-spec-collection
                                     "PROV1"
                                     (assoc coll1-2
                                            :concept-id "C5-PROV1"
                                            :native-id "NID-5")
                                     {:format :dif :allow-failure? true})]
        (is (= [409 ["The Entry Title [ET-1] must be unique. The following concepts with the same entry title were found: [C1-PROV1]."
                     "The Short Name [EID-2] and Version Id [V1] combined must be unique. The following concepts with the same Short Name and Version Id were found: [C1-PROV1]."]]
               [status errors]))))

    (testing "ingest collection with entry-id used by a collection in a different provider is OK"
      (let [{:keys [status]} (data-core/ingest-umm-spec-collection
                              "PROV2"
                              (assoc coll1-2 :concept-id "C1-PROV2")
                              {:format :dif})]
        (is (= 201 status))))))

;; Ingest same concept N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-collection-ingest-test
  (testing "ingest same concept n times ..."
    (let [n 4
          concept (data-umm-c/collection-concept {})
          created-concepts (take n (repeatedly n #(ingest/ingest-concept concept)))]
      (index/wait-until-indexed)
      (is (apply = (map :concept-id created-concepts)))
      (is (= (range 1 (inc n)) (map :revision-id created-concepts))))))

(deftest update-collection-with-different-formats-test
  (testing "update collection in different formats ..."
    (doseq [[expected-rev coll-format] (map-indexed #(vector (inc %1) %2) [:echo10 :dif :dif10 :iso19115 :iso-smap])]
      (let [coll (data-core/ingest-umm-spec-collection
                  "PROV1"
                  (data-umm-c/collection {:ShortName "S1"
                                          :Version "V1"
                                          :EntryTitle "ET1"
                                          :LongName "L4"
                                          :Abstract (name coll-format)
                                          ;; Needed for DIF to pass xml validation
                                          :ScienceKeywords [(data-umm-cmn/science-keyword
                                                             {:Category "upcase"
                                                              :Topic "Cool"
                                                              :Term "Mild"})]
                                          :DataCenters [(data-umm-cmn/data-center
                                                         {:Roles ["DISTRIBUTOR"]
                                                          :ShortName "Larc"})]
                                          ;; Needed for DIF10 to pass xml validation
                                          :TemporalExtents [(data-umm-cmn/temporal-extent
                                                             {:beginning-date-time "1965-12-12T12:00:00Z"
                                                              :ending-date-time "1967-12-12T12:00:00Z"})]})
                  {:format coll-format})]
        (index/wait-until-indexed)
        (is (= expected-rev (:revision-id coll)))
        (is (= 1 (:hits (search/find-refs :collection {:keyword (name coll-format)}))))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-collection-ingest-test
  (let [concept-with-empty-body  (assoc (data-umm-c/collection-concept {}) :metadata "")
        {:keys [status errors]} (ingest/ingest-concept concept-with-empty-body)]
    (index/wait-until-indexed)
    (is (= status 400))
    (is (re-find #"Request content is too short." (first errors)))))

;; Verify old DeleteTime concept results in 400 error.
(deftest old-delete-time-collection-ingest-test
  (let [coll (data-umm-c/collection {:DataDates [(umm-cmn/map->DateType {:Date (date-time-parser/parse-datetime "2000-01-01T12:00:00Z")
                                                                         :Type "DELETE"})]})
        {:keys [status errors]} (ingest/ingest-concept
                                  (data-core/umm-c-collection->concept (assoc coll :provider-id "PROV1") :echo10))]
    (index/wait-until-indexed)
    (is (= status 422))
    (is (re-find #"DeleteTime 2000-01-01T12:00:00.000Z is before the current time." (first errors)))))

(deftest delete-collection-test-old
  (testing "It should be possible to delete existing concept and the operation without revision id should
           result in revision id 1 greater than max revision id of the concept prior to the delete"
           (let [concept (data-umm-c/collection-concept {})
                 ingest-result (ingest/ingest-concept concept)
                 delete-result (ingest/delete-concept concept)
                 ingest-revision-id (:revision-id ingest-result)
                 delete-revision-id (:revision-id delete-result)]
             (index/wait-until-indexed)
             (is (= (inc ingest-revision-id) delete-revision-id))))
  (testing "Deleting existing concept with a revision-id should respect the revision id"
    (let [concept (data-umm-c/collection-concept {})
          ingest-result (ingest/ingest-concept concept)
          delete-result (ingest/delete-concept concept {:revision-id 5})
          delete-revision-id (:revision-id delete-result)]
      (index/wait-until-indexed)
      (is (= 5 delete-revision-id))
      (is (mdb/concept-exists-in-mdb? (:concept-id ingest-result) 5)))))

(deftest delete-collection-test
  (let [coll1 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                                    :ShortName "S1"}))
        gran1 (data-core/ingest "PROV1" (granule/granule-with-umm-spec-collection coll1 (:concept-id coll1)))
        gran2 (data-core/ingest "PROV1" (granule/granule-with-umm-spec-collection coll1 (:concept-id coll1)))
        coll2 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E2"
                                                                                    :ShortName "S2"}))
        gran3 (data-core/ingest "PROV1" (granule/granule-with-umm-spec-collection coll2 (:concept-id coll2)))]
    (index/wait-until-indexed)

    ;; delete collection
    (is (= 200 (:status (ingest/delete-concept (data-core/umm-c-collection->concept coll1 :echo10)))))
    (index/wait-until-indexed)

    (is (:deleted (mdb/get-concept (:concept-id coll1))) "The collection should be deleted")
    (is (not (mdb/concept-exists-in-mdb? (:concept-id gran1) (:revision-id gran1)))
        "Granules in the collection should be deleted")
    (is (not (mdb/concept-exists-in-mdb? (:concept-id gran2) (:revision-id gran2)))
        "Granules in the collection should be deleted")

    (is (empty? (:refs (search/find-refs :collection {"concept-id" (:concept-id coll1)}))))
    (is (empty? (:refs (search/find-refs :granule {"concept-id" (:concept-id gran1)}))))
    (is (empty? (:refs (search/find-refs :granule {"concept-id" (:concept-id gran2)}))))


    (is (mdb/concept-exists-in-mdb? (:concept-id coll2) (:revision-id coll2)))
    (is (mdb/concept-exists-in-mdb? (:concept-id gran3) (:revision-id gran3)))

    (is (data-core/refs-match?
          [coll2]
          (search/find-refs :collection {"concept-id" (:concept-id coll2)})))
    (is (data-core/refs-match?
          [gran3]
          (search/find-refs :granule {"concept-id" (:concept-id gran3)})))))

(deftest delete-collection-acls-test
  ;; Remove search fixture acls
  (let [fixture-acls (:items (test-util/search-for-acls (transmit-config/echo-system-token) {:identity_type "catalog_item"}))
        _ (doseq [fixture-acl fixture-acls]
            (echo-util/ungrant (test-util/conn-context) (:concept_id fixture-acl)))

        ;; Ingest a collection under PROV1.
        coll1 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                                    :ShortName "S1"}))
        coll2 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E2"
                                                                                    :ShortName "S2"}))
        coll3 (data-core/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E3"
                                                                                    :ShortName "S3"}))
        coll4 (data-core/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E4"
                                                                                    :ShortName "S4"}))
        token (echo-util/login-guest (system/context))]

    ;; wait for the collections to be indexed so that ACLs will be valid
    (index/wait-until-indexed)
    (test-util/create-acl (transmit-config/echo-system-token)
                          {:group_permissions [{:user_type "guest"
                                                :permissions ["read" "create"]}]
                           :provider_identity {:provider_id "PROV1"
                                               :target "CATALOG_ITEM_ACL"}})

    (test-util/create-acl (transmit-config/echo-system-token)
                          {:group_permissions [{:user_type "guest"
                                                :permissions ["read" "create"]}]
                           :provider_identity {:provider_id "PROV2"
                                               :target "CATALOG_ITEM_ACL"}})
    (index/wait-until-indexed)
    ;; Ingest some ACLs that reference the collection by concept id.
    (test-util/create-acl token {:group_permissions [{:user_type "guest"
                                                      :permissions ["read" "order"]}]
                                 :catalog_item_identity {:name "coll1 ACL"
                                                         :provider_id "PROV1"
                                                         :collection_applicable true
                                                         :collection_identifier {:entry_titles [(:EntryTitle coll1)]}}})

    (test-util/create-acl token {:group_permissions [{:user_type "guest"
                                                      :permissions ["read"]}]
                                 :catalog_item_identity {:name "coll1/coll2 ACL"
                                                         :provider_id "PROV1"
                                                         :collection_applicable true
                                                         :collection_identifier {:entry_titles [(:EntryTitle coll1) (:EntryTitle coll2)]}}})

    (test-util/create-acl token {:group_permissions [{:user_type "guest"
                                                      :permissions ["read" "order"]}]
                                 :catalog_item_identity {:name "coll3 ACL"
                                                         :provider_id "PROV2"
                                                         :collection_applicable true
                                                         :collection_identifier {:concept_ids [(:concept-id coll3)]}}})
    (test-util/create-acl token {:group_permissions [{:user_type "guest"
                                                      :permissions ["read"]}]
                                 :catalog_item_identity {:name "coll3/coll4 ACL"
                                                         :provider_id "PROV2"
                                                         :collection_applicable true
                                                         :collection_identifier {:concept_ids [(:concept-id coll3) (:concept-id coll4)]}}})
    (index/wait-until-indexed)

    (testing "Entry title based catalog item acls"
      ;; Verify that the ACLs are found in Access Control Service search.
      (let [results (:items (test-util/search-for-acls (transmit-config/echo-system-token) {:identity_type "catalog_item" :provider "PROV1"}))]
        (is (= [1 1] (map :revision_id results)))
        (is (= ["coll1 ACL" "coll1/coll2 ACL"] (map :name results))))
      ;; Delete the collection via ingest.
      (ingest/delete-concept (data-core/umm-c-collection->concept coll1 :echo10))
      (index/wait-until-indexed)
      ;; Verify that those ACLs are NOT found.
      (let [results (:items (test-util/search-for-acls (transmit-config/echo-system-token) {:identity_type "catalog_item" :provider "PROV1"}))]
        (is (= [2] (map :revision_id results)))
        (is (= ["coll1/coll2 ACL"] (map :name results)))
        (is (= [(:EntryTitle coll2)]
               (-> (test-util/get-acl token (:concept_id (first results)))
                   :catalog_item_identity
                   :collection_identifier
                   :entry_titles)))))

    ;; Verify that the ACLs are found in Access Control Service search.
    (testing "Concept id based catalog item acls"
      (let [results (:items (test-util/search-for-acls (transmit-config/echo-system-token) {:identity_type "catalog_item" :provider "PROV2"}))]
        (is (= [1 1] (map :revision_id results)))
        (is (= ["coll3 ACL" "coll3/coll4 ACL"] (map :name results))))
      ;; Delete the collection via ingest.
      (ingest/delete-concept (data-core/umm-c-collection->concept coll3 :echo10))
      (index/wait-until-indexed)
      ;; Verify that those ACLs are NOT found.
      (let [results (:items (test-util/search-for-acls (transmit-config/echo-system-token) {:identity_type "catalog_item" :provider "PROV2"}))]
        (is (= [2] (map :revision_id results)))
        (is (= ["coll3/coll4 ACL"] (map :name results)))
        (is (= [(:concept-id coll4)]
               (-> (test-util/get-acl token (:concept_id (first results)))
                   :catalog_item_identity
                   :collection_identifier
                   :concept_ids)))))))

(deftest delete-deleted-collection-with-new-revision-id-returns-404
  (let [coll (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection))
        concept-id (:concept-id coll)
        native-id (:EntryTitle coll)
        coll-del1 (ingest/delete-concept (data-core/umm-c-collection->concept coll :echo10) {:revision-id 5})
        coll-del2 (ingest/delete-concept (data-core/umm-c-collection->concept coll :echo10) {:revision-id 7})]
    (is (= 200 (:status coll-del1)))
    (is (= 404 (:status coll-del2)))
    (is (= [(format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                    native-id concept-id)]
           (:errors coll-del2)))
    (is (= 5 (:revision-id coll-del1)))
    (index/wait-until-indexed)
    (is (empty? (:refs (search/find-refs :collection {"concept-id" concept-id}))))
    (is (mdb/concept-exists-in-mdb? concept-id 5))
    (is (not (mdb/concept-exists-in-mdb? concept-id 7)))))

;; Verify ingest is successful for request with content type that has parameters
(deftest content-type-with-parameter-ingest-test
  (let [concept (assoc (data-umm-c/collection-concept {})
                       :format "application/echo10+xml; charset=utf-8")
        {:keys [status]} (ingest/ingest-concept concept)]
    (index/wait-until-indexed)
    (is (= 201 status))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [concept-with-no-content-type  (assoc (data-umm-c/collection-concept {}) :format "")
        response (ingest/ingest-concept concept-with-no-content-type {:accept-format :json :raw? true})
        status (:status response)
        {:keys [errors]} (ingest/parse-ingest-body :json response)]
    (index/wait-until-indexed)
    (is (= 415 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify ingest behaves properly if request contains invalid content type.
(deftest invalid-content-type-ingest-test
  (let [concept (assoc (data-umm-c/collection-concept {}) :format "blah")
        response (ingest/ingest-concept concept {:accept-format :json :raw? true})
        status (:status response)
        {:keys [errors]} (ingest/parse-ingest-body :json response)]
    (index/wait-until-indexed)
    (is (= 415 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify that collections with embedded / (%2F) in the native-id are handled correctly
(deftest ingest-collection-with-slash-in-native-id-test
  (let [crazy-id "`1234567890-=qwertyuiop[]\\asdfghjkl;'zxcvbnm,./ ~!@#$%^&*()_+QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?"
        collection (data-umm-c/collection-concept {:EntryTitle crazy-id})
        {:keys [concept-id revision-id] :as response} (ingest/ingest-concept collection)
        ingested-concept (mdb/get-concept concept-id)]
    (index/wait-until-indexed)
    (is (= 201 (:status response)))
    (is (mdb/concept-exists-in-mdb? concept-id revision-id))
    (is (= 1 revision-id))
    (is (= crazy-id (:native-id ingested-concept)))

    (testing "delete"
      (let [delete-result (ingest/delete-concept ingested-concept)]
        (is (= 200 (:status delete-result)))))))

(deftest schema-validation-test
  (are [concept-format validation-errors]
       (let [concept (data-umm-c/collection-concept
                       {:TemporalExtents [(data-umm-cmn/temporal-extent
                                            {:beginning-date-time "2010-12-12T12:00:00Z"})]}
                       concept-format)
             {:keys [status errors]}
             (ingest/ingest-concept
               (assoc concept
                      :format (mime-types/format->mime-type concept-format)
                      :metadata (-> concept
                                    :metadata
                                    (string/replace "2010-12-12T12:00:00" "A")
                                    ;; this is to cause validation error for iso19115 format
                                    (string/replace "fileIdentifier" "XXXX")
                                    ;; this is to cause validation error for iso-smap format
                                    (string/replace "gmd:DS_Series" "XXXX"))))]
         (index/wait-until-indexed)
         (= [400 validation-errors] [status errors]))

       :echo10 ["Exception while parsing invalid XML: Line 1 - cvc-datatype-valid.1.2.1: 'A.000Z' is not a valid value for 'dateTime'."
                "Exception while parsing invalid XML: Line 1 - cvc-type.3.1.3: The value 'A.000Z' of element 'BeginningDateTime' is not valid."]

       :dif10 ["Exception while parsing invalid XML: Line 1 - cvc-datatype-valid.1.2.3: 'A.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'."
               "Exception while parsing invalid XML: Line 1 - cvc-type.3.1.3: The value 'A.000Z' of element 'Beginning_Date_Time' is not valid."]

       :iso19115 [(str "Exception while parsing invalid XML: Line 1 - cvc-complex-type.2.4.a: Invalid content was found "
                       "starting with element 'gmd:XXXX'. One of "
                       "'{\"http://www.isotc211.org/2005/gmd\":fileIdentifier, "
                       "\"http://www.isotc211.org/2005/gmd\":language, "
                       "\"http://www.isotc211.org/2005/gmd\":characterSet, "
                       "\"http://www.isotc211.org/2005/gmd\":parentIdentifier, "
                       "\"http://www.isotc211.org/2005/gmd\":hierarchyLevel, "
                       "\"http://www.isotc211.org/2005/gmd\":hierarchyLevelName, "
                       "\"http://www.isotc211.org/2005/gmd\":contact}' is expected.")]

       :iso-smap ["Exception while parsing invalid XML: Line 1 - cvc-elt.1: Cannot find the declaration of element 'XXXX'."]))

(deftest ingest-umm-json
  (let [json (umm-spec/generate-metadata test-context expected-conversion/curr-ingest-ver-example-collection-record :umm-json)
        coll-map {:provider-id "PROV1"
                  :native-id "umm_json_coll_V1"
                  :revision-id "1"
                  :concept-type :collection
                  ;; assumes the current version
                  :format "application/vnd.nasa.cmr.umm+json"
                  :metadata json}
        response (ingest/ingest-concept coll-map)]
    (is (= 201 (:status response)))
    (is (= nil (:errors response)))
    (index/wait-until-indexed)
    (is (mdb/concept-exists-in-mdb? (:concept-id response) 1))
    (is (= 1 (:revision-id response)))

    (testing "UMM-JSON collections are searchable after ingest"
      (is (= 1 (count (:refs (search/find-refs :collection {"entry-title" "The entry title V5"}))))))

    (testing "Updating a UMM-JSON collection"
      (let [response (ingest/ingest-concept (assoc coll-map :revision-id "2"))]
        (is (= 200 (:status response)))
        (index/wait-until-indexed)
        (is (mdb/concept-exists-in-mdb? (:concept-id response) 2))
        (is (= 2 (:revision-id response))))))

  (testing "ingesting UMM JSON with parsing errors"
    (let [json (umm-spec/generate-metadata test-context (assoc expected-conversion/curr-ingest-ver-example-collection-record
                                                         :DataDates
                                                         [{:Date "invalid date"
                                                           :Type "CREATE"}])
                                           :umm-json)
          concept-map {:provider-id "PROV1"
                       :native-id "umm_json_coll_2"
                       :revision-id "1"
                       :concept-type :collection
                       :format "application/vnd.nasa.cmr.umm+json"
                       :metadata json}
          response (ingest/ingest-concept concept-map {:accept-format :json})]
      (is (= ["#/DataDates/0/Date: [invalid date] is not a valid date-time. Expected [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}Z, yyyy-MM-dd'T'HH:mm:ss[+-]HH:mm, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}[+-]HH:mm]"] (:errors response)))
      (is (= 400 (:status response))))))

(deftest ingest-old-json-versions
  (let [json     (umm-spec/generate-metadata test-context expected-conversion/example-collection-record "application/vnd.nasa.cmr.umm+json;version=1.0")
        coll-map {:provider-id  "PROV1"
                  :native-id    "umm_json_coll_V1"
                  :concept-type :collection
                  :format       "application/vnd.nasa.cmr.umm+json;version=1.0"
                  :metadata     json}
        response (ingest/ingest-concept coll-map {:accept-format :json})]
    (is (= 201 (:status response)))))

(deftest ingest-higher-than-accepted-umm-version
  (let [accepted-version (common-config/collection-umm-version)
        _ (side/eval-form `(common-config/set-collection-umm-version! "1.8"))
        coll-map {:provider-id  "PROV1"
                  :native-id    "umm_json_coll_V1"
                  :concept-type :collection
                  :format       "application/vnd.nasa.cmr.umm+json;version=1.9"
                  :metadata     "{\"foo\":\"bar\"}"}
        response (ingest/ingest-concept coll-map {:accept-format :json})
        _ (side/eval-form `(common-config/set-collection-umm-version! ~accepted-version))]
    (is (= 400 (:status response)))
    (is (= [(str "UMM JSON version 1.8 or lower can be ingested. Any version above that is considered in-development and cannot be ingested at this time.")]
           (:errors response)))))

(deftest ingest-invalid-umm-version
  (let [coll-map {:provider-id  "PROV1"
                  :native-id    "umm_json_coll_V1"
                  :concept-type :collection
                  :format       "application/vnd.nasa.cmr.umm+json;version=1.0009"
                  :metadata     "{\"foo\":\"bar\"}"}
        response (ingest/ingest-concept coll-map {:accept-format :json})]
    (is (= 400 (:status response)))
    (is (= ["Invalid UMM JSON schema version: 1.0009"]
           (:errors response)))))

(deftest ingest-invalid-umm-version-with-quotes
  (let [coll-map {:provider-id  "PROV1"
                  :native-id    "umm_json_coll_V1"
                  :concept-type :collection
                  :format       "application/vnd.nasa.cmr.umm+json;version=\"1.1\""
                  :metadata     "{\"foo\":\"bar\"}"}
        response (ingest/ingest-concept coll-map {:accept-format :json})]
    (is (= 400 (:status response)))
    (is (= ["Invalid UMM JSON schema version: \"1.1\""]
           (:errors response)))))

;; Verify ingest of collection with string larger than 80 characters for project(campaign) long name is successful (CMR-1361)
(deftest project-long-name-can-be-upto-1024-characters
  (let [project (data-umm-cmn/project "p1"
                                      (str "A long name longer than eighty characters should not result"
                                           " in a schema validation error"))
        concept (assoc (data-umm-c/collection-concept {:projects [project]})
                       :format "application/echo10+xml; charset=utf-8")
        {:keys [status]} (ingest/ingest-concept concept)]
    (index/wait-until-indexed)
    (is (= 201 status))))

(deftest dif9-missing-version-ingest
  (testing "Ingest of a DIF9 collection with missing version. This verifies that we apply the
           defaults to collections by default during ingest."
    (let [coll-metadata (slurp (io/resource "example-data/dif/C1214305813-AU_AADC.xml"))
          {:keys [status]} (ingest/ingest-concept
                            (ingest/concept :collection "PROV1" "foo" :dif coll-metadata))]
      (is (= 201 status)))))

(deftest invalid-mimetype-ingest
  (testing "Ingest of a json collection with invalid mimetypes. This verifies that we validate mimetypes against kms."
    (let [coll-metadata (slurp (io/resource "CMR-7647/CMR-7647.json"))
          {:keys [status errors]} (ingest/ingest-concept
                            (ingest/concept :collection "PROV1" "foo" :umm-json coll-metadata))]
      (is (= 422 status))
      (is (= [["Related URL Content Type, Type, and Subtype [ContactPerson1: BadURLContentType1>HOME PAGE>null] are not a valid set together."]
              ["Related URL Content Type, Type, and Subtype [ContactPerson1: BadURLContentType2>HOME PAGE>null] are not a valid set together."]
              ["Related URL Content Type, Type, and Subtype [ContactPerson2: BadURLContentType1>HOME PAGE>null] are not a valid set together."]
              ["Related URL Content Type, Type, and Subtype [ContactPerson2: BadURLContentType2>HOME PAGE>null] are not a valid set together."]
              ["Related URL Content Type, Type, and Subtype [ContactGroup1: BadURLCotentType1>HOME PAGE>null] are not a valid set together."]
              ["Related URL Content Type, Type, and Subtype [ContactGroup1: BadURLCotentType2>HOME PAGE>null] are not a valid set together."]
              ["Related URL Content Type, Type, and Subtype [ContactGroup2: BadURLContentType1>HOME PAGE>null] are not a valid set together."]
              ["Related URL Content Type, Type, and Subtype [ContactGroup2: BadURLContentType2>HOME PAGE>null] are not a valid set together."]]
             (map :errors errors))))))

(deftest CMR-4920-DOI-test
  (testing "Ingest echo10 collection with new DOI values"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E1"
                                                  :ShortName "S1"
                                                  :DOI {:MissingReason "Not Applicable"
                                                        :Explanation "Explanation String"}
                                                  :concept-id "C1-PROV1"} :echo10)
          response (ingest/ingest-concept concept {:raw? true})]
      (is (= {:concept-id "C1-PROV1" :revision-id 1}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id]))))))

(deftest CMR-6174-Collection-Progress-test
  (testing "Ingest UMM-C 1.15.1 collection with new Collection Progress DEPRECATED value"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E2"
                                                  :ShortName "S2"
                                                  :CollectionProgress "DEPRECATED"
                                                  :concept-id "C2-PROV1"} :umm-json)
          response (ingest/ingest-concept concept {:raw? true})]
      (is (= {:concept-id "C2-PROV1" :revision-id 1}
             (select-keys (ingest/parse-ingest-body :xml response) [:concept-id :revision-id]))))))

(deftest CMR-8657-GET-DATA-subtype-test
  (testing "Ingest ECHO10 collection with GET DATA RelatedUrl type and invalid KMS subtype is OK"
    (let [coll-metadata (-> "CMR-8657/coll_invalid_kms_getdata_subtypes.xml" io/resource slurp)
          {:keys [status errors]} (ingest/ingest-concept
                                   (ingest/concept :collection "PROV1" "CMR-8657-GET-DATA-subtype" :echo10 coll-metadata))]
      (is (= 201 status))
      (is (= nil errors)))))