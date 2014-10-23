(ns cmr.metadata-db.int-test.concepts.cleanup-test
  "Tests that old revisions of concepts are cleaned up"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as messages]))

(use-fixtures :each (util/reset-database-fixture "PROV1" "PROV2"))

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
