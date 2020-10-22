(ns cmr.metadata-db.int-test.concepts.force-delete-test
  "Contains integration test for emptying database via force-delete."
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.concept-delete-spec :as cd-spec]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as cs-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture
                     {:provider-id "REG_PROV" :small false}
                     {:provider-id "SMAL_PROV" :small false}))

(defmethod cs-spec/gen-concept :service
  [_ provider-id uniq-num attributes]
  (concepts/create-concept :service provider-id uniq-num attributes))

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
         tag1 (concepts/create-and-save-concept :tag "CMR" 1)
         tag2 (concepts/create-and-save-concept :tag "CMR" 2)
         tag3 (concepts/create-and-save-concept :tag "CMR" 3)]
     ;; create some collection revisions
     (dorun (repeatedly 3 #(util/save-concept concept)))
     ;; associate tag1 to the whole collection, tag2 to revision 2 and tag3 to revision 3
     ;; this set up is not realistic, but will test the scenarios more thoroughly
     (let [ta1 (concepts/create-and-save-concept :tag-association (dissoc saved-concept :revision-id) tag1 1)
           ta2 (concepts/create-and-save-concept :tag-association (assoc saved-concept :revision-id 2) tag2 2)
           ta3 (concepts/create-and-save-concept :tag-association (assoc saved-concept :revision-id 3) tag3 3)]

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

(deftest force-delete-service-with-associations
  (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
        coll-concept-id (:concept-id coll)
        svc-concept (concepts/create-and-save-concept :service "REG_PROV" 1 3)
        svc-concept-id (:concept-id svc-concept)
        svc-assn (concepts/create-and-save-concept :service-association
                  coll svc-concept 1)
        svc-assn-concept-id (:concept-id svc-assn)]
    (testing "initial conditions"
      ;; creation results as expected
      (is (= 3 (:revision-id svc-concept)))
      ;; service revisions in db
      (is (= 3
             (count (:concepts (util/find-concepts :service)))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           svc-concept-id 3))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           svc-concept-id 2))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           svc-concept-id 1))))
      ;; make sure service association in place
      (is (= coll-concept-id
             (get-in svc-assn [:extra-fields :associated-concept-id])))
      (is (= svc-concept-id
             (get-in svc-assn [:extra-fields :service-concept-id])))
      ;; make sure collection associations in place
      (is (not (:deleted (:concept (util/get-concept-by-id svc-assn-concept-id))))))
    (testing "revision 2 is force deleted"
      (util/force-delete-concept svc-concept-id 2)
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           svc-concept-id 3))))
      (is (= 404 (:status (util/get-concept-by-id-and-revision
                           svc-concept-id 2))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           svc-concept-id 1))))
      ;; verify the association hasn't been deleted
      (is (not (:deleted (:concept (util/get-concept-by-id svc-assn-concept-id))))))
    (testing "Cannot force delete the latest revision of a concept"
      (let [expected-errors [(format (str "Cannot force delete the latest revision of a concept "
                                          "[%s, %s], use regular delete instead.")
                                     svc-concept-id 3)]
            {:keys [status errors]} (util/force-delete-concept svc-concept-id 3)]
        (is (= 400 status))
        (is (= expected-errors errors))
        ;; latest revision of the service concept and service associations are not deleted
        (is (= 200 (:status (util/get-concept-by-id-and-revision
                             svc-concept-id 3))))
        (is (not (:deleted (:concept (util/get-concept-by-id svc-assn-concept-id)))))))
    (testing "cannot delete non-existing revision"
      (let [non-extant-revision 4
            response (util/force-delete-concept svc-concept-id non-extant-revision)]
        (is (= 404 (:status response)))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        svc-concept-id non-extant-revision)]
               (:errors response)))))))

(deftest force-delete-tool-with-associations
  (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
        coll-concept-id (:concept-id coll)
        tool-concept (concepts/create-and-save-concept :tool "REG_PROV" 1 3)
        tool-concept-id (:concept-id tool-concept)
        tool-assn (concepts/create-and-save-concept :tool-association
                   coll tool-concept 1)
        tool-assn-concept-id (:concept-id tool-assn)]
    (testing "initial conditions"
      ;; creation results as expected
      (is (= 3 (:revision-id tool-concept)))
      ;; tool revisions in db
      (is (= 3
             (count (:concepts (util/find-concepts :tool)))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           tool-concept-id 3))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           tool-concept-id 2))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           tool-concept-id 1))))
      ;; make sure tool association in place
      (is (= coll-concept-id
             (get-in tool-assn [:extra-fields :associated-concept-id])))
      (is (= tool-concept-id
             (get-in tool-assn [:extra-fields :tool-concept-id])))
      ;; make sure collection associations in place
      (is (not (:deleted (:concept (util/get-concept-by-id tool-assn-concept-id))))))
    (testing "revision 2 is force deleted"
      (util/force-delete-concept tool-concept-id 2)
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           tool-concept-id 3))))
      (is (= 404 (:status (util/get-concept-by-id-and-revision
                           tool-concept-id 2))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           tool-concept-id 1))))
      ;; verify the association hasn't been deleted
      (is (not (:deleted (:concept (util/get-concept-by-id tool-assn-concept-id))))))
    (testing "Cannot force delete the latest revision of a concept"
      (let [expected-errors [(format (str "Cannot force delete the latest revision of a concept "
                                          "[%s, %s], use regular delete instead.")
                                     tool-concept-id 3)]
            {:keys [status errors]} (util/force-delete-concept tool-concept-id 3)]
        (is (= 400 status))
        (is (= expected-errors errors))
        ;; latest revision of the tool concept and tool associations are not deleted
        (is (= 200 (:status (util/get-concept-by-id-and-revision
                             tool-concept-id 3))))
        (is (not (:deleted (:concept (util/get-concept-by-id tool-assn-concept-id)))))))
    (testing "cannot delete non-existing revision"
      (let [non-extant-revision 4
            response (util/force-delete-concept tool-concept-id non-extant-revision)]
        (is (= 404 (:status response)))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        tool-concept-id non-extant-revision)]
               (:errors response)))))))

(deftest force-delete-variable-with-associations
  (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
        coll-concept-id (:concept-id coll)
        var-concept (concepts/create-and-save-concept :variable "REG_PROV" 1 3 {:coll-concept-id coll-concept-id})
        var-concept-id (:concept-id var-concept)
        var-assn-concept-id (get-in var-concept [:variable-association :concept-id])
        var-assn (:concept (util/get-concept-by-id var-assn-concept-id))]
    (testing "initial conditions"
      ;; creation results as expected
      (is (= 3 (:revision-id var-concept)))
      ;; variable revisions in db
      (is (= 3
             (count (:concepts (util/find-concepts :variable)))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           var-concept-id 3))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           var-concept-id 2))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           var-concept-id 1))))
      ;; make sure variable association in place
      (is (= coll-concept-id
             (get-in var-assn [:extra-fields :associated-concept-id])))
      (is (= var-concept-id
             (get-in var-assn [:extra-fields :variable-concept-id])))
      ;; make sure collection associations in place
      (is (not (:deleted (:concept (util/get-concept-by-id var-assn-concept-id))))))
    (testing "revision 2 is force deleted"
      (util/force-delete-concept var-concept-id 2)
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           var-concept-id 3))))
      (is (= 404 (:status (util/get-concept-by-id-and-revision
                           var-concept-id 2))))
      (is (= 200 (:status (util/get-concept-by-id-and-revision
                           var-concept-id 1))))
      ;; verify the association hasn't been deleted
      (is (not (:deleted (:concept (util/get-concept-by-id var-assn-concept-id))))))
    (testing "Cannot force delete the latest revision of a concept"
      (let [expected-errors [(format (str "Cannot force delete the latest revision of a concept "
                                          "[%s, %s], use regular delete instead.")
                                     var-concept-id 3)]
            {:keys [status errors]} (util/force-delete-concept var-concept-id 3)]
        (is (= 400 status))
        (is (= expected-errors errors))
        ;; latest revision of the service concept and service associations are not deleted
        (is (= 200 (:status (util/get-concept-by-id-and-revision
                             var-concept-id 3))))
        (is (not (:deleted (:concept (util/get-concept-by-id var-assn-concept-id)))))))
    (testing "cannot delete non-existing revision"
      (let [non-extant-revision 4
            response (util/force-delete-concept var-concept-id non-extant-revision)]
        (is (= 404 (:status response)))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        var-concept-id non-extant-revision)]
               (:errors response)))))))
