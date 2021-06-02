(ns cmr.system-int-test.search.tool.force-delete-test
  "This tests force-deleting a tool concept and the impact on
  service/collection associations."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-tool :as data-umm-t]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tool-util :as tool]
   [cmr.transmit.config :refer [mock-echo-system-token]
                        :rename {mock-echo-system-token token}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures
 :each
 (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- get-collection-tools
  "A utility function for extracting a collection's tools associations.

  Note that we used destructuring here instead of `get-in` due to the
  non-(Clojure)indexing of `clojure.lang.ChunkedCons` of the `entries` which
  prevented this from working:
  `(get-in response [:results :entries 0 :associations :tools])`."
  []
  (let [{{[{associations :associations}] :entries} :results}
       (search/find-concepts-json :collection {})]
    (:tools associations)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest force-delete-tool-with-associations
  (let [coll (data-umm-c/collection-concept {:ShortName "C1"
                                             :native-id "Force-Delete-C1"})
        {coll-concept-id :concept-id} (ingest/ingest-concept coll)
        tl (data-umm-t/tool-concept {:Name "Tl1"
                                     :native-id "Force-Delete-Tl1"})
        ;; Make three revisions, capturing the third
        tl-concept (last (for [n (range 3)] (tool/ingest-tool tl)))
        {tl-concept-id :concept-id} tl-concept
        _ (index/wait-until-indexed)
        {[assn-response] :body} (au/associate-by-concept-ids
                                 token
                                 tl-concept-id
                                 [{:concept-id coll-concept-id}])
        {{tl-assn-concept-id :concept-id} :tool-association} assn-response]
    (testing "initial conditions"
      (is (= 3 (:revision-id tl-concept)))
      ;; make sure that the collection's `associations` field is present, that
      ;; it contains `:tools` and that the right tool is associated
      (is (= coll-concept-id
             (get-in assn-response [:associated-item :concept-id])))
      (is (contains? (get-collection-tools) tl-concept-id))
      ;; revision 3 of the tool is found
      (d/refs-match? [tl-concept] (search/find-refs :tool {})))
    (testing "revision 2 is force deleted"
      (mdb/force-delete-concept tl-concept-id 2)
      ;; make sure the tool association has not been deleted
      (is (contains? (get-collection-tools) tl-concept-id))
      ;; revision 3 of the tool is found
      (d/refs-match? [tl-concept] (search/find-refs :tool {})))
    (testing "Cannot force delete the latest revision"
      (let [expected-errors [(format (str "Cannot force delete the latest revision of a concept "
                                          "[%s, %s], use regular delete instead.")
                                     tl-concept-id 3)]
            {:keys [status body]} (mdb/force-delete-concept tl-concept-id 3)
            errors (-> body
                       (json/decode true)
                       :errors)]
        (is (= 400 status))
        (is (= expected-errors errors))))))
