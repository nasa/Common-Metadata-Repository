(ns cmr.system-int-test.search.variable.force-delete-test
  "This tests force-deleting a variable concept and the impact on
  variable/collection associations."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-variable :as data-umm-v]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variable]
   [cmr.transmit.config :refer [mock-echo-system-token]
                        :rename {mock-echo-system-token token}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                variable/grant-all-variable-fixture]))

(deftest force-delete-variable-revisions
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection 1 {})
                                            {:token token})
        coll2 (d/ingest-umm-spec-collection "PROV2"
                                            (data-umm-c/collection 1 {})
                                            {:token token})
        _ (index/wait-until-indexed)
        var1-concept (variable/make-variable-concept
                      {:Name "Variable1"}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll1)})
        var1-1 (variable/ingest-variable-with-association var1-concept)
        var1-2-tombstone (merge (ingest/delete-concept var1-concept {:token token})
                                var1-concept
                                {:deleted true
                                 :user-id "user1"})
        var1-3 (variable/ingest-variable-with-association var1-concept)

        var2-1-concept (variable/make-variable-concept
                        {:Name "Variable2"
                         :LongName "LongName2"}
                        {:native-id "var2"
                         :coll-concept-id (:concept-id coll1)})
        var2-1 (variable/ingest-variable-with-association var2-1-concept)
        var2-2-tombstone (merge (ingest/delete-concept var2-1-concept {:token token})
                                var2-1-concept
                                {:deleted true
                                 :user-id "user1"})
        var2-3-concept (variable/make-variable-concept
                        {:Name "Variable2-3"
                         :LongName "LongName2-3"}
                        {:native-id "var2"
                         :coll-concept-id (:concept-id coll1)})
        var2-3 (variable/ingest-variable-with-association var2-3-concept)
        var3-concept (variable/make-variable-concept
                      {:Name "Variable1"
                       :LongName "LongName3"}
                      {:native-id "var3"
                       :coll-concept-id (:concept-id coll2)})
        var3 (variable/ingest-variable-with-association var3-concept)]
    (index/wait-until-indexed)

    (testing "force delete variable revision"
      ;; All variable revisions exist before force deletion
      (variable/assert-variable-references-match
       [var1-1 var1-2-tombstone var1-3 var2-1 var2-2-tombstone var2-3 var3]
       (search/find-refs :variable {:all-revisions true}))
      ;; force delete a regular revision
      (mdb/force-delete-concept
        (:concept-id var1-1) (:revision-id var1-1))
      ;; force delete a tombstone revision
      (mdb/force-delete-concept
        (:concept-id var2-2-tombstone) (:revision-id var2-2-tombstone))
      (index/wait-until-indexed)
      ;; force deleted revisions are no longer in the search result
      (variable/assert-variable-references-match
       [var1-2-tombstone var1-3 var2-1 var2-3 var3]
       (search/find-refs :variable {:all-revisions true}))
      (index/wait-until-indexed)
      (let [expected-associations {:collections [{:concept-id (:concept-id coll1)}]}]
        (testing "associations to collections are captured in the variables all revisions endpoint"
          (variable/assert-variable-associations var1-3 expected-associations
                                                 {:all_revisions true}))
        (let [var1-4 (variable/ingest-variable var1-concept)]
          (index/wait-until-indexed)
          (testing (str "associations are correct in the all revisions endpoint when ingesting a "
                        "new variable revision")
            (variable/assert-variable-associations var1-4 expected-associations
                                                   {:all_revisions true}))
          (testing "force delete does not cascade to variable association"
            ;; search collections by variable native-id found the collection
            (d/refs-match? [coll1] (search/find-refs :collection {:variable_native_id "var1"}))
            ;; force delete a revision of the variable does not cascade to variable association
            (mdb/force-delete-concept (:concept-id var1-3) (:revision-id var1-3))
            (index/wait-until-indexed)
            ;; search collections by variable native-id still find the collection
            (d/refs-match? [coll1] (search/find-refs :collection {:variable_native_id "var1"}))))))

    (testing "Cannot force delete the latest revision"
      (let [expected-errors [(format (str "Cannot force delete the latest revision of a concept "
                                          "[%s, %s], use regular delete instead.")
                                     (:concept-id var2-3) (:revision-id var2-3))]
            {:keys [status body]} (mdb/force-delete-concept
                                    (:concept-id var2-3) (:revision-id var2-3))
            errors (-> body
                       (json/decode true)
                       :errors)]
        (is (= 400 status))
        (is (= expected-errors errors))))))
