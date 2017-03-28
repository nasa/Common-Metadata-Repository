(ns cmr.system-int-test.ingest.enable-disable-test
  "CMR Ingest Enable/Disable endpoint test"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; * Ingest of collection, granule, create provider all work before disabling ingest.
;; * Delete of a collection, granule work before disabling ingest.
;; * Ingest of collection, granule, create provider fail with a 503 after disabling, then work again after enabling.
;; * Delete of a collection, granule fail with a 503 after disabling, then work again after enabling.
;; * Validation still works when disabled

(deftest enable-disable-enable-ingest-writes-concepts
  (testing "disable / re-enable ingest of concepts"
    (let [common-fields {:EntryTitle "coll1" :ShortName "short1" :Version "V1"}
          ;; collection/granule ingested before disabling ingest
          pre-disable-orig-coll (data-umm-c/collection-concept (assoc common-fields :native-id "native1"))
          pre-disable-orig-coll-resp (ingest/ingest-concept pre-disable-orig-coll)

          ;; delete the collection
          pre-disable-deleted-coll-resp (ingest/delete-concept pre-disable-orig-coll)

          ;; Create collection again with same details but a different native id
          new-coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection (assoc common-fields :native-id "native2")))

          ;; Create granules associated with the collection fields.
          pre-disable-gran (update-in (dg/granule-with-umm-spec-collection new-coll (:concept-id new-coll)) [:collection-ref] dissoc :short-name :version-id :entry-id)
          pre-disable-gran-resp (d/ingest "PROV1" pre-disable-gran)
          ;; delete the granule
          pre-disable-deleted-gran (ingest/delete-concept (d/item->concept pre-disable-gran) {:accept-format :json :raw? true})
          ;; disable ingest
          _ (ingest/disable-ingest-writes)
          ;; these should fail
          after-disable-coll-resp (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection (assoc common-fields :native-id "native3")) {:allow-failure? true})
          after-disable-delete (ingest/delete-concept pre-disable-orig-coll)
          after-disable-gran (d/ingest "PROV1"
                                       (update-in (dg/granule-with-umm-spec-collection new-coll (:concept-id new-coll)) [:collection-ref] dissoc :short-name :version-id :entry-id)
                                       {:allow-failure? true})
          ;; re-enable ingest
          _ (ingest/enable-ingest-writes)
          ;; these should succeed
          after-enable-coll (data-umm-c/collection-concept {:native-id "native3"
                                                            :EntryTitle "coll3"
                                                            :Version "V1"
                                                            :ShortName "short3"})
          after-enable-coll-resp (ingest/ingest-concept after-enable-coll)
          after-enable-delete-coll (ingest/delete-concept after-enable-coll)
          after-enable-gran (update-in (dg/granule-with-umm-spec-collection new-coll (:concept-id new-coll)) [:collection-ref] dissoc :short-name :version-id :entry-id)
          after-enable-gran-resp (d/ingest "PROV1" after-enable-gran)
          after-enable-delete-gran (ingest/delete-concept (d/item->concept after-enable-gran) {:accept-format :json :raw? true})]

      (index/wait-until-indexed)
      (is (= 201 (:status pre-disable-orig-coll-resp)))
      (is (= 200 (:status pre-disable-deleted-coll-resp)))
      (is (= 201 (:status pre-disable-gran-resp)))
      (is (= 200 (:status pre-disable-deleted-gran)))
      ;; after disable
      (is (= 503 (:status after-disable-coll-resp)))
      (is (= 503 (:status after-disable-gran)))
      (is (= 503 (:status after-disable-delete)))
      ;; after re-enable
      (is (= 201 (:status after-enable-coll-resp)))
      (is (= 200 (:status after-enable-delete-coll)))
      (is (= 201 (:status after-enable-gran-resp)))
      (is (= 200 (:status after-enable-delete-gran)))))


  (testing "disable / re-enable affects provider creation"
    (let [pre-disable-prov (ingest/create-ingest-provider {:provider-id "PRE_PROV"
                                                           :short-name "pre-prov"
                                                           :cmr-only true
                                                           :small false})]
      (is (= 201 (:status pre-disable-prov))))
    ;; disable ingest
    (ingest/disable-ingest-writes)

    (let [after-disable-prov (ingest/create-ingest-provider {:provider-id "AFTER_DIS_PROV"
                                                             :short-name "after-dis-prov"
                                                             :cmr-only true
                                                             :small false})]
      (is (= 503 (:status after-disable-prov)))))

  (testing "disable does not prevent validation"
    (let [concept (data-umm-c/collection-concept {})
          {:keys [status errors]} (ingest/validate-concept concept)]
      (is (= [200 nil] [status errors]))))

  (ingest/enable-ingest-writes))
