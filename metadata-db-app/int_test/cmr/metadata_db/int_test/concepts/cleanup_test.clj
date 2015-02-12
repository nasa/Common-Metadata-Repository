(ns cmr.metadata-db.int-test.concepts.cleanup-test
  "Tests that old revisions of concepts are cleaned up"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clj-time.core :as t]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as messages]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.common.time-keeper :as tk]))

(use-fixtures :each (join-fixtures
                      [(util/reset-database-fixture "PROV1" "PROV2")
                       (tk/freeze-resume-time-fixture)]))

(comment

  (do
    (util/reset-database)
    (util/save-provider "PROV1")
    (util/save-provider "PROV2"))

  )

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

(deftest old-collection-revisions-are-cleaned-up
  (let [coll1 (util/create-and-save-collection "PROV1" 1 13)
        coll2 (util/create-and-save-collection "PROV1" 2 3)
        coll3 (util/create-and-save-collection "PROV2" 1 12)
        coll4 (util/create-and-save-collection "PROV2" 4 3)
        collections [coll1 coll2 coll3 coll4]]

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

    (is (revisions-exist? coll4 (range 1 5)))))

(deftest old-granule-revisions-are-cleaned-up
  (let [coll1 (util/create-and-save-collection "PROV1" 1 1)
        gran1 (util/create-and-save-granule "PROV1" (:concept-id coll1) 1 3)
        gran2 (util/create-and-save-granule "PROV1" (:concept-id coll1) 2 3)
        coll2 (util/create-and-save-collection "PROV2" 2 1)
        gran3 (util/create-and-save-granule "PROV2" (:concept-id coll2) 1 3)
        gran4 (util/create-and-save-granule "PROV2" (:concept-id coll2) 4 2)
        granules [gran1 gran2 gran3 gran4]]

    ;; Granule 4 has a tombstone
    (util/delete-concept (:concept-id gran4))

    ;; Verify prior revisions exist
    (is (every? all-revisions-exist? granules))

    (is (= 204 (util/old-revision-concept-cleanup)))

    (is (every? #(revisions-removed? % (range 1 3)) granules))
    (is (every? #(concept-revision-exists? % 3) granules))))

(deftest old-tombstones-are-cleaned-up
  (let [coll1 (util/create-and-save-collection "PROV1" 1)
        base-revision-date (tk/now)
        offset->date #(t/plus base-revision-date (t/days %))

        ;; Creates a granule based on the number with a revision date offset-days in the future
        make-gran (fn [uniq-num offset-days]
                    (let [gran (assoc (util/granule-concept "PROV1" (:concept-id coll1) uniq-num)
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

        gran1 (-> (make-gran 1 0)
                  (update-gran 1)
                  (delete-gran 2))
        gran2 (-> (make-gran 2 0)
                  (update-gran 1))
        gran3 (-> (make-gran 3 (dec (dec (* -1 days-to-keep-tombstone))))
                  (delete-gran (dec (* -1 days-to-keep-tombstone))))
        ;; granule 4 is way in the future
        gran4-1 (make-gran 4 days-to-keep-tombstone)
        _ (delete-gran gran4-1 (inc days-to-keep-tombstone))
        gran4-3 (update-gran gran4-1 (inc (inc days-to-keep-tombstone)))

        all-concept-tuples (concat (for [n (range 1 10)]
                                     [(:concept-id gran1) n])
                                   (for [n (range 1 10)]
                                     [(:concept-id gran2) n])
                                   (for [n (range 1 10)]
                                     [(:concept-id gran3) n])
                                   (for [n (range 1 10)]
                                     [(:concept-id gran4-3) n]))
        all-concepts (:concepts (util/get-concepts all-concept-tuples true))]

    ;; Make sure we have the right number of concepts
    (is (= 10 (count all-concepts)))

    ;; Do the cleanup
    (is (= 204 (util/old-revision-concept-cleanup)))

    (let [concepts-after-cleanup (:concepts (util/get-concepts all-concept-tuples true))]
      (is (= #{gran1 gran2 gran4-3}
             (set concepts-after-cleanup))))

    ;; Back to the future!
    ;; Advance one second past granule 1's tombstone cleanup time
    (tk/advance-time! (+ 1 (* (+ 2 days-to-keep-tombstone) 24 3600)))

    ;; Do the cleanup again
    (is (= 204 (util/old-revision-concept-cleanup)))

    (let [concepts-after-cleanup (:concepts (util/get-concepts all-concept-tuples true))]
      (is (= #{gran2 gran4-3}
             (set concepts-after-cleanup))))))


