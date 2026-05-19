(ns cmr.system-int-test.ingest.index-set-versioning-test
  "Tests that index-set updates are versioned in Metadata DB."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.index-set :as data-index-set]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture))

(deftest index-set-versioning-test
  (let [index-set-id 1234
        updated-reason "updated reason"
        index-set (data-index-set/sample-index-set index-set-id)
        ;; Grant permissions
        admin-group-guid (echo-util/get-or-create-group (s/context) "admin-group")
        _ (echo-util/grant-group-admin (s/context) admin-group-guid :update)
        admin-token (echo-util/login (s/context) "admin" [admin-group-guid])]

    (testing "Create index-set creates revision 1 in Metadata DB"
      (let [response (index/create-index-set index-set)
            _ (is (= 201 (:status response)))
            concept-id (mdb/get-concept-id :index-set "CMR" (str index-set-id))]
        (is (some? concept-id))
        (let [concept (mdb/get-concept concept-id)]
          (is (= 1 (:revision-id concept)))
          (is (= false (:deleted concept)))
          (is (= "CMR" (:provider-id concept))))))

    (testing "Update index-set creates revision 2 in Metadata DB"
      (let [updated-index-set (assoc-in index-set [:index-set :create-reason] updated-reason)
            response (index/update-index-set updated-index-set index-set-id)
            _ (is (= 200 (:status response)))
            concept-id (mdb/get-concept-id :index-set "CMR" (str index-set-id))
            concept (mdb/get-concept concept-id)]
        (is (= 2 (:revision-id concept)))
        (is (= false (:deleted concept)))))

    (testing "Retrieve specific revisions and operational state via Indexer API"
      (let [rev1-data (index/get-index-set-by-id index-set-id {:revision-id 1})
            rev2-data (index/get-index-set-by-id index-set-id {:revision-id 2})
            ops-data (index/get-index-set-by-id index-set-id)]
        (is (= 1 (:revision-id rev1-data)))
        (is (= "sample index" (get-in rev1-data [:index-set :create-reason])))
        (is (= 2 (:revision-id rev2-data)))
        (is (= updated-reason (get-in rev2-data [:index-set :create-reason])))
        (is (= 2 (:revision-id ops-data)))
        (is (= false (:deleted ops-data)))))

    (testing "Disaster Recovery: Accidental deletion and restoration"
      (let [response (index/delete-index-set index-set-id)
            _ (is (= 204 (:status response)))]

        (testing "Operational state returns 404 after deletion"
          (let [data (index/get-index-set-by-id index-set-id)]
            (is (= 404 (:status data)))))

        (testing "Can still fetch deleted revision from MDB"
          (let [rev3-data (index/get-index-set-by-id index-set-id {:revision-id 3})]
            (is (= 3 (:revision-id rev3-data)))
            (is (= true (:deleted rev3-data)))
            (is (nil? (:index-set rev3-data)))))

        (testing "Restore by fetching Revision 2 and PUTing it back"
          (let [rev2-data (index/get-index-set-by-id index-set-id {:revision-id 2})
                ;; Pass inner index-set map to PUT
                last-good-config {:index-set (:index-set rev2-data)}
                restore-resp (index/update-index-set last-good-config index-set-id)]
            (is (= 200 (:status restore-resp)))

            (testing "Operational state is restored with new revision 4"
              (let [data (index/get-index-set-by-id index-set-id)]
                (is (= 4 (:revision-id data)))
                (is (= false (:deleted data)))
                (is (= updated-reason (get-in data [:index-set :create-reason])))))))))

    (testing "Multiple index-sets are versioned independently"
      (let [id1 1111
            id2 2222
            is1 (data-index-set/sample-index-set id1)
            is2 (data-index-set/sample-index-set id2)
            _ (is (= 201 (:status (index/create-index-set is1))))
            _ (is (= 201 (:status (index/create-index-set is2))))
            cid1 (mdb/get-concept-id :index-set "CMR" (str id1))
            cid2 (mdb/get-concept-id :index-set "CMR" (str id2))]
        (is (not= cid1 cid2))
        (index/update-index-set is1 id1)
        (is (= 2 (:revision-id (mdb/get-concept cid1))))
        (is (= 1 (:revision-id (mdb/get-concept cid2))))
        (index/update-index-set is2 id2)
        (is (= 2 (:revision-id (mdb/get-concept cid2))))))

    (testing "Disaster Recovery: Restore lost ES cluster from Oracle"
      (let [id 5555
            is (data-index-set/sample-index-set id)]
        (index/create-index-set is)
        (is (some? (index/get-index-set-by-id id)))
        ;; Simulate ES loss
        (index/index-set-reset)
        (is (= 404 (:status (index/get-index-set-by-id id))))
        ;; Sync
        (is (= 204 (:status (index/sync-index-sets-from-db))))
        ;; Verify
        (let [restored (index/get-index-set-by-id id)]
          (is (some? restored))
          (is (= id (get-in restored [:index-set :id]))))))))
