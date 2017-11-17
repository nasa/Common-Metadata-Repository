(ns cmr.system-int-test.search.service.force-delete-test
  "This tests force-deleting a service concept and the impact on
  service/collection associations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.transmit.config :refer [mock-echo-system-token]
                        :rename {mock-echo-system-token token}]))

(use-fixtures
 :each
 (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- get-collection-services
  []
  (let [{{[{{services :services} :associations}] :entries} :results}
       (search/find-concepts-json :collection {})]
    services))

(deftest force-delete-service-with-associations
  (let [coll (data-umm-c/collection-concept {})
        {coll-concept-id :concept-id} (ingest/ingest-concept coll)
        _ (service/ingest-service)
        svc-concept (service/ingest-service)
        {svc-concept-id :concept-id} svc-concept
        _ (index/wait-until-indexed)
        {[assn-response] :body} (au/associate-by-concept-ids
                                 token
                                 svc-concept-id
                                 [{:concept-id coll-concept-id}])
        {{svc-assn-concept-id :concept-id} :service-association} assn-response]
    (testing "initial conditions"
      (is (= 2 (:revision-id svc-concept)))
      (is (= coll-concept-id
             (get-in assn-response [:associated-item :concept-id])))
      (is (contains? (get-collection-services) svc-concept-id)))
    (testing "just the last revision is deleted"
      (mdb/force-delete-concept svc-concept-id 1)
      ;; the service association has not been deleted
      (is (contains? (get-collection-services) svc-concept-id)))
    (testing "just the most recent revision is deleted"
      ;; now the service association has been deleted
      (mdb/force-delete-concept svc-concept-id 2)
      (index/wait-until-indexed)
      (is (not (contains? (get-collection-services) svc-concept-id))))))
