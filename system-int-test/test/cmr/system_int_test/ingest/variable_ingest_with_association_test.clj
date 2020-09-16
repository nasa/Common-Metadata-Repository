(ns cmr.system-int-test.ingest.variable-ingest-with-association-test
  "CMR variable ingest with association integration tests."
  (:require
   [clojure.test :refer :all]
   [clojure.string :as string]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.variable-util :as variable-util]
   [cmr.umm-spec.models.umm-variable-models :as umm-v]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest variable-ingest-with-association-test
  (let [;; ingest 4 collections, each with 2 revisions
        coll1-PROV1-1 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                                           :ShortName "S1"}))
        coll1-PROV1-2 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                                            :ShortName "S1"}))
        coll2-PROV1-1 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E2"
                                                                                            :ShortName "S2"}))
        coll2-PROV1-2 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E2"
                                                                                            :ShortName "S2"}))
        coll1-PROV2-1 (data-core/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E1"
                                                                                            :ShortName "S1"}))
        coll1-PROV2-2 (data-core/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E1"
                                                                                            :ShortName "S1"}))
        coll2-PROV2-1 (data-core/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E2"
                                                                                            :ShortName "S2"}))
        coll2-PROV2-2 (data-core/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E2"
                                                                                            :ShortName "S2"}))
        _ (index/wait-until-indexed)]

    (testing "ingest of a new variable concept with association on PROV1"
      (let [concept (variable-util/make-variable-concept
                     {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                              :Size 3
                                                              :Type "OTHER"})]}
                     {:native-id "var1"
                      :coll-concept-id (:concept-id coll1-PROV1-1)})
            {:keys [concept-id revision-id variable-association]}
              (variable-util/ingest-variable-with-association concept)
            var-concept-id concept-id
            va-concept-id (:concept-id variable-association)
            va-revision-id (:revision-id variable-association)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))
        (is (mdb/concept-exists-in-mdb? va-concept-id va-revision-id))
        (is (= 1 va-revision-id))

        (testing "ingest the same concept with a collection on PROV2 is OK"
          (let [concept (variable-util/make-variable-concept
                         {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                                  :Size 3
                                                                 :Type "OTHER"})]}
                         {:native-id "var1"
                          :coll-concept-id (:concept-id coll1-PROV2-1)
                          :coll-revision-id (:revision-id coll1-PROV2-1)})
                {:keys [concept-id revision-id variable-association]}
                  (variable-util/ingest-variable-with-association concept)
                va-concept-id (:concept-id variable-association)
                va-revision-id (:revision-id variable-association)]
            (is (mdb/concept-exists-in-mdb? concept-id revision-id))
            (is (= 1 revision-id))
            (is (mdb/concept-exists-in-mdb? va-concept-id va-revision-id))
            (is (= 1 va-revision-id))))

        (testing "Update the same concept on PROV2 with an association on another revision is okay"
          (let [concept (variable-util/make-variable-concept
                         {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                                  :Size 3
                                                                 :Type "OTHER"})]}
                         {:native-id "var1"
                          :coll-concept-id (:concept-id coll1-PROV2-1)
                          :coll-revision-id (:revision-id coll1-PROV2-2)})
                {:keys [concept-id revision-id variable-association]}
                  (variable-util/ingest-variable-with-association concept)
                va-concept-id (:concept-id variable-association)
                va-revision-id (:revision-id variable-association)]
            (is (mdb/concept-exists-in-mdb? concept-id revision-id))
            (is (= 2 revision-id))
            (is (mdb/concept-exists-in-mdb? va-concept-id va-revision-id))
            (is (= 1 va-revision-id))))

        (testing "ingest of the variable with negligible changes and the same native-id becomes an update"
          ;; now this is using the existing variable ingest endpoint to update.
          (let [concept (variable-util/make-variable-concept
                         {:Dimensions [(umm-v/map->DimensionType {:Name " Solution_3_Land "
                                                                  :Size 3
                                                                  :Type "OTHER"})]}
                         {:native-id "var1"})
                {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                                  concept)]
            (is (mdb/concept-exists-in-mdb? concept-id revision-id))
            (is (= 2 revision-id))))

        (testing "ingest of the existing variable with a different native-id is not allowed"
          (let [concept (variable-util/make-variable-concept
                         {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                                  :Size 3
                                                                  :Type "OTHER"})]}
                         {:native-id "var2"
                          :coll-concept-id (:concept-id coll1-PROV1-1)})
                ;; Two variables with the same name (var1, var2) can not be associated
                ;; with the same collection.
                {:keys [status errors]} (variable-util/ingest-variable-with-association concept)]
            (is (= 409 status))
            (is (= (format (str "collection [%s] can not be associated because the collection "
                                 "is already associated with another variable [%s] with same name.")
                           (:concept-id coll1-PROV1-1) var-concept-id)
                   (second (string/split (first errors) #" and "))))))))

    (testing "ingest of a new variable concept with association on specific revision on PROV1"
      (let [concept (variable-util/make-variable-concept
                     {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land_3"
                                                              :Size 3
                                                              :Type "OTHER"})]}
                     {:native-id "var3"
                      :coll-concept-id (:concept-id coll2-PROV1-1)
                      :coll-revision-id 1})
            {:keys [concept-id revision-id variable-association]}
              (variable-util/ingest-variable-with-association concept)
            va-concept-id (:concept-id variable-association)
            va-revision-id (:revision-id variable-association)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))
        (is (mdb/concept-exists-in-mdb? va-concept-id va-revision-id))
        (is (= 1 va-revision-id))))

    (testing "ingest of a new variable concept with association on an invalid revision on PROV1"
      (let [concept (variable-util/make-variable-concept
                     {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land_4"
                                                              :Size 3
                                                              :Type "OTHER"})]}
                     {:native-id "var4"
                      :coll-concept-id (:concept-id coll2-PROV1-1)
                      :coll-revision-id 3})
            response
              (variable-util/ingest-variable-with-association concept)
            errors (:errors response)
            expected-errors [(format "Collection [%s] revision [3] does not exist"
                                     (:concept-id coll2-PROV1-1))]]
         (is (= expected-errors errors))))

    (testing "ingest of a new variable concept with association on a deleted collection on PROV1"
      (let [concept1 (variable-util/make-variable-concept
                       {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land_4"
                                                                :Size 3
                                                                :Type "OTHER"})]}
                       {:native-id "var4"
                        :coll-concept-id (:concept-id coll2-PROV1-1)
                        :coll-revision-id 3})
            concept2 (variable-util/make-variable-concept
                       {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land_4"
                                                                :Size 3
                                                                :Type "OTHER"})]}
                       {:native-id "var4"
                        :coll-concept-id (:concept-id coll2-PROV1-1)})
            _ (ingest/delete-concept
                (data-core/umm-c-collection->concept coll2-PROV1-1 :echo10)
                {:accept-format :json
                 :raw? true})
            _ (index/wait-until-indexed)
            response1
              (variable-util/ingest-variable-with-association concept1)
            response2
              (variable-util/ingest-variable-with-association concept2)
            errors1 (:errors response1)
            expected-errors1 [(format "Collection [%s] revision [3] is deleted"
                                      (:concept-id coll2-PROV1-1))]
            errors2 (:errors response2)
            expected-errors2 [(format "Collection [%s] does not exist or is not visible."
                                      (:concept-id coll2-PROV1-1))]]
         (is (= expected-errors1 errors1))
         (is (= expected-errors2 errors2))))

    (testing "ingest of a variable concept with a revision id"
      (let [concept (variable-util/make-variable-concept
                      {}
                      {:native-id "var1"
                       :revision-id 5
                       :coll-concept-id (:concept-id coll1-PROV1-1)})
            {:keys [concept-id revision-id variable-association]} (variable-util/ingest-variable-with-association concept)]
        (is (= 5 revision-id))
        (is (mdb/concept-exists-in-mdb? concept-id 5))
        (is (= 2 (:revision-id variable-association)))
        (is (mdb/concept-exists-in-mdb? (:concept-id variable-association) 1))))

    (testing "Deletion of a collection propagates to deletion of variables associated with the collection."
      (let [concept (variable-util/make-variable-concept
                      {}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll1-PROV1-1)})
            {:keys [concept-id variable-association]} (variable-util/ingest-variable-with-association concept)
            ;; delete the collection.
            response (ingest/delete-concept
                       (data-core/umm-c-collection->concept coll1-PROV1-1 :echo10)
                       {:accept-format :json
                        :raw? true})]
         (is (= 200 (:status response)))
         (index/wait-until-indexed)
         ;; both the variable and variable association should be deleted too.
         (is (= true (:deleted (mdb/get-concept concept-id))))
         (is (= true (:deleted (mdb/get-concept (:concept-id variable-association)))))))))

(deftest variable-ingest-with-association-update-test
  (let [;; ingest 4 collections, each with 2 revisions
        coll1-PROV1-1 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                                           :ShortName "S1"}))
        coll1-PROV1-2 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                                            :ShortName "S1"}))
        coll2-PROV1-1 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E2"
                                                                                           :ShortName "S2"}))
        coll2-PROV1-2 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E2"
                                                                                            :ShortName "S2"}))
        _ (index/wait-until-indexed)]

    (testing "ingest of a new variable concept with association on PROV1"
      (let [concept (variable-util/make-variable-concept
                     {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                              :Size 3
                                                              :Type "OTHER"})]}
                     {:native-id "var1"
                      :coll-concept-id (:concept-id coll1-PROV1-1)})
            {:keys [concept-id revision-id variable-association]}
              (variable-util/ingest-variable-with-association concept)
            var-concept-id concept-id
            va-concept-id-1 (:concept-id variable-association)
            va-revision-id-1 (:revision-id variable-association)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))
        (is (mdb/concept-exists-in-mdb? va-concept-id-1 va-revision-id-1))
        (is (= 1 va-revision-id-1))

        (testing  "update the variable above with the same collection with revision on PROV1 is OK"
          (let [concept (variable-util/make-variable-concept
                          {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                                   :Size 3
                                                                   :Type "OTHER"})]}
                          {:native-id "var1"
                           :coll-concept-id (:concept-id coll1-PROV1-1)
                           :coll-revision-id (:revision-id coll1-PROV1-1)})
                {:keys [concept-id revision-id variable-association]}
                  (variable-util/ingest-variable-with-association concept)
                va-concept-id-2 (:concept-id variable-association)
                va-revision-id-2 (:revision-id variable-association)]
            (is (mdb/concept-exists-in-mdb? concept-id revision-id))
            (is (= 2 revision-id))
            (is (mdb/concept-exists-in-mdb? va-concept-id-2 va-revision-id-2))
            (is (= 1 va-revision-id-2))
            (is (not= va-concept-id-2 va-concept-id-1))

            ;; Verify va-concept-1 is deleted.
            (is (= true (:deleted (mdb/get-concept va-concept-id-1))))

            (testing  "update the variable above with different collection on PROV1 is OK"
              (let [concept (variable-util/make-variable-concept
                              {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                                       :Size 3
                                                                       :Type "OTHER"})]}
                            {:native-id "var1"
                             :coll-concept-id (:concept-id coll2-PROV1-1)
                             :coll-revision-id (:revision-id coll2-PROV1-1)})
                    {:keys [concept-id revision-id variable-association]}
                      (variable-util/ingest-variable-with-association concept)
                    va-concept-id-3 (:concept-id variable-association)
                    va-revision-id-3 (:revision-id variable-association)]
            (is (mdb/concept-exists-in-mdb? concept-id revision-id))
            (is (= 3 revision-id))
            (is (mdb/concept-exists-in-mdb? va-concept-id-3 va-revision-id-3))
            (is (= 1 va-revision-id-3))
            (is (not= va-concept-id-3 va-concept-id-2))

            ;; Verify va-concept-2 is deleted.
            (is (= true (:deleted (mdb/get-concept va-concept-id-2))))))))))))

