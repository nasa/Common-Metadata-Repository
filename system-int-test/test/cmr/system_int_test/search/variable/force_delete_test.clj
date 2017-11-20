(ns cmr.system-int-test.search.variable.force-delete-test
  "This tests force-deleting a variable concept and the impact on
  variable/collection associations."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-variable :as data-umm-v]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variable]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.transmit.config :refer [mock-echo-system-token]
                        :rename {mock-echo-system-token token}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures
 :each
 (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- get-collection-variables
  "A utility function for extracting a collection's variables associations.

  Note that we used destructuring here instead of `get-in` due to the
  non-(Clojure)indexing of `clojure.lang.ChunkedCons` of the `entries` which
  prevented this from working:
  `(get-in response [:results :entries 0 :associations :variables])`."
  []
  (let [{{[{associations :associations}] :entries} :results}
       (search/find-concepts-json :collection {})]
    (:variables associations)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest force-delete-variable-with-associations
  (let [coll (data-umm-c/collection-concept {:ShortName "C1"
                                             :native-id "Force-Delete-C1"})
        {coll-concept-id :concept-id} (ingest/ingest-concept coll)
        variable (data-umm-v/variable-concept {:Name "V1"
                                               :native-id "Force-Delete-V1"})
        ;; Make three revisions, capturing the third
        var-concept (last (for [n (range 3)] (variable/ingest-variable variable)))
        {var-concept-id :concept-id} var-concept
        _ (index/wait-until-indexed)
        {[assn-response] :body} (au/associate-by-concept-ids
                                 token
                                 var-concept-id
                                 [{:concept-id coll-concept-id}])
        {{var-assn-concept-id :concept-id} :variable-association} assn-response]
    (testing "initial conditions"
      (is (= 3 (:revision-id var-concept)))
      ;; make sure that the collection's `associations` field is present, that
      ;; it contains `:variables` and that the right variable is associated
      (is (= coll-concept-id
             (get-in assn-response [:associated-item :concept-id])))
      (is (contains? (get-collection-variables) var-concept-id)))
    (testing "just the last revision is deleted"
      (mdb/force-delete-concept var-concept-id 2)
      ;; make sure the variable association has not been deleted
      (is (contains? (get-collection-variables) var-concept-id)))
    (testing "just the most recent revision is deleted"
      ;; now ensure that the variable association has been deleted
      (mdb/force-delete-concept var-concept-id 3)
      (index/wait-until-indexed)
      (is (not (contains? (get-collection-variables) var-concept-id))))))
