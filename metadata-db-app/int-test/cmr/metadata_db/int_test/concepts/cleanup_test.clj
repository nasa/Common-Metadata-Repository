(ns cmr.metadata-db.int-test.concepts.cleanup-test
  "Tests that old revisions of concepts are cleaned up"
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.time-keeper :as tk]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.concept-service :as concept-service]))

(use-fixtures :each (join-fixtures
                      [(util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                    {:provider-id "SMAL_PROV1" :small true}
                                                    {:provider-id "SMAL_PROV2" :small true})]))

(defn concept-revision-exists?
  "Returns true if the revision of the concept exists"
  [concept revision]
  (-> concept
      :concept-id
      (util/get-concept-by-id-and-revision revision)
      :status
      (= 200)))

(defn revisions-exist?
  "Returns true if all the listed revision exist."
  [concept revisions]
  (every? (partial concept-revision-exists? concept) revisions))

(defn all-revisions-exist?
  "Returns true if all previous and current revisions existing for a client."
  [concept]
  (every? (partial concept-revision-exists? concept) (range 1 (inc (:revision-id concept)))))

(defn revisions-removed?
  "Returns true if all the listed revisions do not exist."
  [concept revisions]
  (every? (complement (partial concept-revision-exists? concept)) revisions))

(defn- concept-exist?
  "Returns true if the concept exists in metadata db as a record including tombstone"
  [concept]
  (= 200 (:status (util/get-concept-by-id (:concept-id concept)))))

(deftest old-acl-revisions-are-cleaned-up-1
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [acl (concepts/create-and-save-concept :acl "CMR" 1 13)]

    (is (all-revisions-exist? acl))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the oldest acl revisions should have been cleaned up
    (is (revisions-removed? acl (range 1 4)))

    ;; The latest 10 revisions should be kept
    (is (revisions-exist? acl (range 4 14)))
  (side/eval-form `(tk/clear-current-time!))))

(deftest old-acl-revisions-are-cleaned-up-2
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [acl (concepts/create-and-save-concept :acl "CMR" 1 10)]

    (is (all-revisions-exist? acl))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; All revisions should be kept since it's not more than 10.
    (is (all-revisions-exist? acl))
  (side/eval-form `(tk/clear-current-time!))))

(deftest old-collection-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 13)
        coll2 (concepts/create-and-save-concept :collection "REG_PROV" 2 3)
        coll3 (concepts/create-and-save-concept :collection "SMAL_PROV1" 1 12 {:native-id "foo"})
        coll4 (concepts/create-and-save-concept :collection "SMAL_PROV1" 4 3)
        coll5 (concepts/create-and-save-concept :collection "SMAL_PROV2" 4 3 {:native-id "foo"})
        collections [coll1 coll2 coll3 coll4 coll5]
        ;; set up tag and tag associations
        tag1 (concepts/create-and-save-concept :tag "CMR" 1)
        tag2 (concepts/create-and-save-concept :tag "CMR" 2)
        tag3 (concepts/create-and-save-concept :tag "CMR" 3)
        tag4 (concepts/create-and-save-concept :tag "CMR" 4)
        ta1 (concepts/create-and-save-concept :tag-association (dissoc coll1 :revision-id) tag1 1)
        ta2 (concepts/create-and-save-concept :tag-association (assoc coll1 :revision-id 1) tag2 2)
        ta3 (concepts/create-and-save-concept :tag-association (assoc coll3 :revision-id 9) tag3 3)
        ta4 (concepts/create-and-save-concept :tag-association (assoc coll3 :revision-id 1) tag4 4)]

    ;; Collection 4 has a tombstone
    (util/delete-concept (:concept-id coll4))

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? collections))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the collection revisions should have been cleaned up
    (is (revisions-removed? coll1 (range 1 4)))
    (is (revisions-exist? coll1 (range 4 13)))

    (is (all-revisions-exist? coll2))

    (is (revisions-removed? coll3 (range 1 3)))
    (is (revisions-exist? coll3 (range 3 13)))

    (is (revisions-exist? coll4 (range 1 5)))
    (is (revisions-exist? coll5 (range 1 4)))

    ;; tag associations that are associated to deleted collection revisions are also deleted
    (util/is-tag-association-deleted? ta2 true)
    (util/is-tag-association-deleted? ta4 true)
    ;; tag association associated to the whole collection is not deleted
    (util/is-tag-association-deleted? ta1 false)
    ;; tag association not associated to deleted collection revisions is not deleted
    (util/is-tag-association-deleted? ta3 false))
 (side/eval-form `(tk/clear-current-time!)))

(deftest old-granule-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 1)
        gran1 (concepts/create-and-save-concept :granule "REG_PROV" coll1 1 3)
        gran2 (concepts/create-and-save-concept :granule "REG_PROV" coll1 2 3)
        coll2 (concepts/create-and-save-concept :collection "SMAL_PROV1" 2 1)
        gran3 (concepts/create-and-save-concept :granule "SMAL_PROV1" coll2 1 3 {:native-id "foo"})
        gran4 (concepts/create-and-save-concept :granule "SMAL_PROV1" coll2 4 2)
        coll3 (concepts/create-and-save-concept :collection "SMAL_PROV2" 2 1)
        gran5 (concepts/create-and-save-concept :granule "SMAL_PROV2" coll3 1 12 {:native-id "foo"})
        granules [gran1 gran2 gran3 gran4 gran5]]

    ;; Granule 4 has a tombstone
    (util/delete-concept (:concept-id gran4))

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? granules))

    (is (= 204 (util/old-revision-concept-cleanup)))

    (is (every? #(revisions-removed? % (range 1 3)) granules))
    (is (every? #(concept-revision-exists? % 3) [gran1 gran2 gran3 gran4]))
    (is (concept-revision-exists? gran5 12)))
 (side/eval-form `(tk/clear-current-time!)))

(deftest old-tombstones-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1)
        base-revision-date (tk/now)
        offset->date #(t/plus base-revision-date (t/days %))

        ;; Creates a granule based on the number with a revision date offset-days in the future
        make-gran (fn [uniq-num offset-days]
                    (let [gran (assoc (concepts/create-concept :granule "REG_PROV" coll1 uniq-num)
                                      :revision-date (offset->date offset-days))
                          {:keys [concept-id revision-id]} (util/assert-no-errors
                                                             (util/save-concept gran))]
                      (assoc gran :concept-id concept-id :revision-id revision-id)))

        ;; Updates granule with a revision date offset-days in the future
        update-gran (fn [gran offset-days]
                      (let [gran (assoc gran
                                        :revision-date (offset->date offset-days)
                                        :revision-id nil)
                            {:keys [revision-id]} (util/assert-no-errors (util/save-concept gran))]
                        (assoc gran :revision-id revision-id)))

        ;; Deletes a granule with a tombstone having a revision date offset-days in the future
        delete-gran (fn [gran offset-days]
                      (let [revision-date (offset->date offset-days)
                            {:keys [revision-id]} (util/assert-no-errors
                                                    (util/delete-concept
                                                      (:concept-id gran) nil revision-date))]
                        (assoc gran
                               :revision-id revision-id
                               :revision-date revision-date
                               :metadata ""
                               :deleted true)))
        days-to-keep-tombstone (concept-service/days-to-keep-tombstone)


        ;; Granule 1
        ;; Creates 3 revisions of granule 1. The last revision is a tombstone.
        ;; All but the tombstone should be removed
        gran1 (-> (make-gran 1 0)
                  (update-gran 1)
                  (delete-gran 2))

        ;; Granule 2
        ;; Creates 2 revisions of granule 2.
        ;; All but the last revision should be removed.
        gran2 (-> (make-gran 2 0)
                  (update-gran 1))

        ;; Granule 3
        ;; Creates 2 revisions of granule 3 with last revision a tombstone.
        ;; The revisions are very old.
        ;; Every revision should be removed.
        gran3 (-> (make-gran 3 (dec (dec (* -1 days-to-keep-tombstone))))
                  (delete-gran (dec (* -1 days-to-keep-tombstone))))

        ;; Granule 4
        ;; Creates 3 revisions of granule 4. The second is a tombstone.
        ;; Granule 4 is farther in the future than granule 1 and 2.
        ;; All but the last revision should be removed.

        ;; Granule 4 - 1st revision
        gran4-1 (make-gran 4 days-to-keep-tombstone)
        ;; Granule 4 - 2nd revision - tombstone
        _ (delete-gran gran4-1 (inc days-to-keep-tombstone))
        ;; Granule 4 - 3rd revision
        gran4-3 (update-gran gran4-1 (inc (inc days-to-keep-tombstone)))

        ;; Create a set of revision ids matching what was saved.
        all-concept-tuples (for [gran [gran1 gran2 gran3 gran4-1]
                                 :let [concept-id (:concept-id gran)]
                                 ;; Using a number higher than the revision ids created.
                                 ;; This is ok since unknown revision ids will be ignored
                                 revision-id (range 1 5)]
                             [concept-id revision-id])

        ;; Get a list of all the concepts from Metadata DB we have saved
        all-concepts (:concepts (util/get-concepts all-concept-tuples true))]

    ;; Make sure we have the right number of concepts
    (is (= 10 (count all-concepts)))

    ;; Do the cleanup
    (is (= 204 (util/old-revision-concept-cleanup)))

    (let [concepts-after-cleanup (:concepts (util/get-concepts all-concept-tuples true))]
      (is (= (set (map util/expected-concept [gran1 gran2 gran4-3]))
             (set (map #(dissoc % :created-at :transaction-id) concepts-after-cleanup))))

      ;; Back to the future!
      ;; Advance one second past granule 1's tombstone cleanup time

      (side/eval-form `(tk/advance-time! ~(+ 1 (* (+ 2 days-to-keep-tombstone) 24 3600))))

      ;; Do the cleanup again
      (is (= 204 (util/old-revision-concept-cleanup)))

      (let [concepts-after-cleanup (:concepts (util/get-concepts all-concept-tuples true))]
        (is (= (set (map util/expected-concept [gran2 gran4-3]))
               (set (map #(dissoc % :created-at :transaction-id) concepts-after-cleanup)))))))
  (side/eval-form `(tk/clear-current-time!)))

(deftest old-tag-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [tag1 (concepts/create-and-save-concept :tag "CMR" 1 13)
        tag2 (concepts/create-and-save-concept :tag "CMR" 2 3)]

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? [tag1 tag2]))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the tag revisions should have been cleaned up
    (is (revisions-removed? tag1 (range 1 4)))
    (is (revisions-exist? tag1 (range 4 13)))

    (is (all-revisions-exist? tag2)))
 (side/eval-form `(tk/clear-current-time!)))

(deftest old-tag-association-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 1)
        tag1 (concepts/create-and-save-concept :tag "CMR" 1 1)
        tag2 (concepts/create-and-save-concept :tag "CMR" 2 1)
        ta1 (concepts/create-and-save-concept :tag-association coll1 tag1 1 13)
        ta2 (concepts/create-and-save-concept :tag-association coll1 tag2 2 3)]

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? [ta1 ta2]))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the tag revisions should have been cleaned up
    (is (revisions-removed? ta1 (range 1 4)))
    (is (revisions-exist? ta1 (range 4 13)))

    (is (all-revisions-exist? ta2)))
 (side/eval-form `(tk/clear-current-time!)))

(deftest old-variable-and-association-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 1)
        coll-concept-id (:concept-id coll1)
        variable1 (concepts/create-and-save-concept :variable "REG_PROV" 1 13 {:coll-concept-id coll-concept-id})
        va1 {:concept-id (get-in variable1 [:variable-association :concept-id])
             :revision-id (get-in variable1 [:variable-association :revision-id])}
        variable2 (concepts/create-and-save-concept :variable "REG_PROV" 2 3 {:coll-concept-id coll-concept-id})
        va2 {:concept-id (get-in variable2 [:variable-association :concept-id])
             :revision-id (get-in variable2 [:variable-association :revision-id])}]

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? [variable1 variable2 va1 va2]))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the variable revisions should have been cleaned up
    (is (revisions-removed? variable1 (range 1 4)))
    (is (revisions-exist? variable1 (range 4 13)))
    (is (revisions-removed? va1 (range 1 4)))
    (is (revisions-exist? va1 (range 4 13)))

    (is (all-revisions-exist? variable2))
    (is (all-revisions-exist? va2)))
  (side/eval-form `(tk/clear-current-time!)))

(deftest old-service-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [service1 (concepts/create-and-save-concept :service "REG_PROV" 1 13)
        service2 (concepts/create-and-save-concept :service "REG_PROV" 2 3)]

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? [service1 service2]))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the service revisions should have been cleaned up
    (is (revisions-removed? service1 (range 1 4)))
    (is (revisions-exist? service1 (range 4 13)))

    (is (all-revisions-exist? service2)))
  (side/eval-form `(tk/clear-current-time!)))

(deftest old-service-association-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 1)
        service1 (concepts/create-and-save-concept :service "REG_PROV" 1 1)
        service2 (concepts/create-and-save-concept :service "REG_PROV" 2 1)
        sa1 (concepts/create-and-save-concept :service-association coll1 service1 1 13)
        sa2 (concepts/create-and-save-concept :service-association coll1 service2 2 3)]

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? [sa1 sa2]))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the service revisions should have been cleaned up
    (is (revisions-removed? sa1 (range 1 4)))
    (is (revisions-exist? sa1 (range 4 13)))

    (is (all-revisions-exist? sa2)))
  (side/eval-form `(tk/clear-current-time!)))

(deftest old-tool-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [tool1 (concepts/create-and-save-concept :tool "REG_PROV" 1 13)
        tool2 (concepts/create-and-save-concept :tool "REG_PROV" 2 3)]

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? [tool1 tool2]))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the tool revisions should have been cleaned up
    (is (revisions-removed? tool1 (range 1 4)))
    (is (revisions-exist? tool1 (range 4 13)))

    (is (all-revisions-exist? tool2)))
  (side/eval-form `(tk/clear-current-time!)))

(deftest old-tool-association-revisions-are-cleaned-up
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 1)
        tool1 (concepts/create-and-save-concept :tool "REG_PROV" 1 1)
        tool2 (concepts/create-and-save-concept :tool "REG_PROV" 2 1)
        tla1 (concepts/create-and-save-concept :tool-association coll1 tool1 1 13)
        tla2 (concepts/create-and-save-concept :tool-association coll1 tool2 2 3)]

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? [tla1 tla2]))

    (is (= 204 (util/old-revision-concept-cleanup)))

    ;; Any more than 10 of the tool revisions should have been cleaned up
    (is (revisions-removed? tla1 (range 1 4)))
    (is (revisions-exist? tla1 (range 4 13)))

    (is (all-revisions-exist? tla2)))
  (side/eval-form `(tk/clear-current-time!)))
