(ns cmr.metadata-db.int-test.concepts.force-delete-test
  "Contains integration test for emptying database via force-delete."
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.concept-delete-spec :as cd-spec]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as cs-spec]
   [cmr.metadata-db.int-test.utility :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture
                     {:provider-id "REG_PROV" :small false}
                     {:provider-id "SMAL_PROV" :small false}))

(defmethod cs-spec/gen-concept :service
  [_ provider-id uniq-num attributes]
  (util/service-concept provider-id uniq-num attributes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest force-delete-concepts-test
  (doseq [concept-type [:collection :granule]]
    (cd-spec/general-force-delete-test concept-type ["REG_PROV" "SMAL_PROV"])))

(deftest force-delete-tag-test
  (cd-spec/general-force-delete-test :tag ["CMR"]))

(deftest force-delete-group-general
  (cd-spec/general-force-delete-test :access-group ["REG_PROV" "SMAL_PROV" "CMR"]))

(deftest force-delete-non-existent-test
  (testing "id not exist"
    (is (= 404 (:status (util/force-delete-concept "C22-REG_PROV" 0))))
    (is (= 404 (:status (util/force-delete-concept "G22-REG_PROV" 0))))
    (is (= 404 (:status (util/force-delete-concept "T22-REG_PROV" 0))))
    (is (= 404 (:status (util/force-delete-concept "AG22-REG_PROV" 0)))))
  (testing "provider not exist"
    (is (= 404 (:status (util/force-delete-concept "C22-PROV3" 0))))
    (is (= 404 (:status (util/force-delete-concept "G22-PROV3" 0))))
    (is (= 404 (:status (util/force-delete-concept "T22-PROV3" 0))))
    (is (= 404 (:status (util/force-delete-concept "AG22-PROV3" 0))))))

(deftest force-delete-collection-revision-delete-associated-tag
 (testing "force delete collection revision cascade to delete tag associations associated with the collection revision"
   (let [concept (cs-spec/gen-concept :collection "REG_PROV" 1 {})
         saved-concept (util/save-concept concept)
         concept-id (:concept-id saved-concept)
         tag1 (util/create-and-save-tag 1)
         tag2 (util/create-and-save-tag 2)
         tag3 (util/create-and-save-tag 3)]
     ;; create some collection revisions
     (dorun (repeatedly 3 #(util/save-concept concept)))
     ;; associate tag1 to the whole collection, tag2 to revision 2 and tag3 to revision 3
     ;; this set up is not realistic, but will test the scenarios more thoroughly
     (let [ta1 (util/create-and-save-tag-association (dissoc saved-concept :revision-id) tag1 1)
           ta2 (util/create-and-save-tag-association (assoc saved-concept :revision-id 2) tag2 2)
           ta3 (util/create-and-save-tag-association (assoc saved-concept :revision-id 3) tag3 3)]

       ;; no tag associations are deleted before the force delete
       (util/is-tag-association-deleted? ta1 false)
       (util/is-tag-association-deleted? ta2 false)
       (util/is-tag-association-deleted? ta3 false)

       ;; force delete collection revision 2
       (is (= 200 (:status (util/force-delete-concept concept-id 2))))

       ;; the tag association associated with the collection revision is deleted
       (util/is-tag-association-deleted? ta2 true)
       ;; tag association associated with other collection revision or whole collection is not deleted
       (util/is-tag-association-deleted? ta1 false)
       (util/is-tag-association-deleted? ta3 false)))))

(deftest force-delete-service
  (let [coll (util/create-and-save-collection "REG_PROV" 1)
        coll-concept-id (:concept-id coll)
        misc-svc (util/create-and-save-service "REG_PROV" 42)
        misc-svc-assn (util/create-and-save-service-association
                       coll misc-svc 1)
        expected-svc-concept-id "S1200000003-REG_PROV"
        expected-revision-ids [1 2 3]
        revision-count 3]
    (testing "delete most recent revision"
      (let [latest-revision (util/create-and-save-service
                             "REG_PROV" 1 revision-count)
            concept-id (:concept-id latest-revision)
            svc-assn (util/create-and-save-service-association
                      coll latest-revision 1)]
        (testing "initial conditions"
          ;; creation results as expected
          (is (= expected-svc-concept-id concept-id))
          (is (= (last expected-revision-ids) (:revision-id latest-revision)))
          ;; service revisions in db
          (is (= 4
                 (count (:concepts (util/find-concepts :service)))))
          (is (= 200 (:status (util/get-concept-by-id-and-revision
                               concept-id (last expected-revision-ids)))))
          (is (= 200 (:status (util/get-concept-by-id-and-revision
                               concept-id (second expected-revision-ids)))))
          (is (= 200 (:status (util/get-concept-by-id-and-revision
                               concept-id (first expected-revision-ids)))))
          ;; make sure service association in place
          (is (= "SA1200000004-CMR" (:concept-id svc-assn)))
          (is (= coll-concept-id
                 (get-in svc-assn [:extra-fields :associated-concept-id])))
          (is (= expected-svc-concept-id
                 (get-in svc-assn [:extra-fields :service-concept-id])))
          ;; make sure collection associations in place
          (is (not (:deleted (:concept (util/get-concept-by-id "SA1200000004-CMR"))))))
        (testing "just the most recent revision is deleted"
          (util/force-delete-concept concept-id (last expected-revision-ids))
          (is (= 404 (:status (util/get-concept-by-id-and-revision
                               concept-id (last expected-revision-ids)))))
          (is (= 200 (:status (util/get-concept-by-id-and-revision
                               concept-id (second expected-revision-ids)))))
          (is (= 200 (:status (util/get-concept-by-id-and-revision
                               concept-id (first expected-revision-ids))))))
        (testing "service association has been deleted"
          (is (:deleted (:concept (util/get-concept-by-id "SA1200000004-CMR")))))))
    (testing "delete oldest revision"
      (let [latest-revision (util/create-and-save-service
                             "REG_PROV" 2 revision-count)
            concept-id (:concept-id latest-revision)
            svc-assn (util/create-and-save-service-association
                      coll latest-revision 2)]
        (is (= "SA1200000006-CMR" (:concept-id svc-assn)))
        (util/force-delete-concept concept-id (first expected-revision-ids))
        (is (= 200 (:status (util/get-concept-by-id-and-revision
                             concept-id (last expected-revision-ids)))))
        (is (= 200 (:status (util/get-concept-by-id-and-revision
                             concept-id (second expected-revision-ids)))))
        (is (= 404 (:status (util/get-concept-by-id-and-revision
                             concept-id (first expected-revision-ids)))))
        (testing "service association has not been deleted"
          (is (not (:deleted (:concept (util/get-concept-by-id "SA1200000006-CMR"))))))))
    (testing "delete middle revision"
      (let [latest-revision (util/create-and-save-service
                             "REG_PROV" 3 revision-count)
            concept-id (:concept-id latest-revision)
            svc-assn (util/create-and-save-service-association
                      coll latest-revision 3)]
        (is (= "SA1200000008-CMR" (:concept-id svc-assn)))
        (util/force-delete-concept concept-id (second expected-revision-ids))
        (is (= 200 (:status (util/get-concept-by-id-and-revision
                             concept-id (last expected-revision-ids)))))
        (is (= 404 (:status (util/get-concept-by-id-and-revision
                             concept-id (second expected-revision-ids)))))
        (is (= 200 (:status (util/get-concept-by-id-and-revision
                             concept-id (first expected-revision-ids)))))
        (testing "service association has not been deleted"
          (is (not (:deleted (:concept (util/get-concept-by-id "SA1200000008-CMR"))))))))))
