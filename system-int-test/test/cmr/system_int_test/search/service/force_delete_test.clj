(ns cmr.system-int-test.search.service.force-delete-test
  "This tests force-deleting a service concept and the impact on
  service/collection associations."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-service :as data-umm-s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service]
   [cmr.transmit.config :refer [mock-echo-system-token]
                        :rename {mock-echo-system-token token}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures
 :each
 (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- get-collection-services
  "A utility function for extracting a collection's services associations.

  Note that we used destructuring here instead of `get-in` due to the
  non-(Clojure)indexing of `clojure.lang.ChunkedCons` of the `entries` which
  prevented this from working:
  `(get-in response [:results :entries 0 :associations :services])`."
  []
  (let [{{[{associations :associations}] :entries} :results}
       (search/find-concepts-json :collection {})]
    (:services associations)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest force-delete-service-with-associations
  (let [coll (data-umm-c/collection-concept {:ShortName "C1"
                                             :native-id "Force-Delete-C1"})
        {coll-concept-id :concept-id} (ingest/ingest-concept coll)
        svc (data-umm-s/service-concept {:Name "S1"
                                         :native-id "Force-Delete-S1"})
        ;; Make three revisions, capturing the third
        svc-concept (last (for [n (range 3)] (service/ingest-service svc)))
        {svc-concept-id :concept-id} svc-concept
        _ (index/wait-until-indexed)
        {[assn-response] :body} (au/associate-by-concept-ids
                                 token
                                 svc-concept-id
                                 [{:concept-id coll-concept-id}])
        {{svc-assn-concept-id :concept-id} :service-association} assn-response]
    (testing "initial conditions"
      (is (= 3 (:revision-id svc-concept)))
      ;; make sure that the collection's `associations` field is present, that
      ;; it contains `:services` and that the right service is associated
      (is (= coll-concept-id
             (get-in assn-response [:associated-item :concept-id])))
      (is (contains? (get-collection-services) svc-concept-id))
      ;; revision 3 of the service is found
      (d/refs-match? [svc-concept] (search/find-refs :service {})))
    (testing "revision 2 is force deleted"
      (mdb/force-delete-concept svc-concept-id 2)
      ;; make sure the service association has not been deleted
      (is (contains? (get-collection-services) svc-concept-id))
      ;; revision 3 of the service is found
      (d/refs-match? [svc-concept] (search/find-refs :service {})))
    (testing "Cannot force delete the latest revision"
      (let [expected-errors [(format (str "Cannot force delete the latest revision of a concept "
                                          "[%s, %s], use regular delete instead.")
                                     svc-concept-id 3)]
            {:keys [status body]} (mdb/force-delete-concept svc-concept-id 3)
            errors (-> body
                       (json/decode true)
                       :errors)]
        (is (= 400 status))
        (is (= expected-errors errors))))))
