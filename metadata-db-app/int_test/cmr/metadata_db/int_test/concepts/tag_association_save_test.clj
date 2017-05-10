(ns cmr.metadata-db.int-test.concepts.tag-association-save-test
  "Contains integration tests for saving tag associations. Tests saves with various configurations
   including checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are2)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :tag-association
  [_ _ uniq-num attributes]
  (let [concept-attributes (or (:concept-attributes attributes) {})
        concept (util/create-and-save-collection "REG_PROV" uniq-num 1 concept-attributes)
        tag-attributes (or (:tag-attributes attributes) {})
        tag (util/create-and-save-tag uniq-num 1 tag-attributes)
        attributes (dissoc attributes :concept-attributes :tag-attributes)]
    (util/tag-association-concept concept tag uniq-num attributes)))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-tag-association-test
  (c-spec/general-save-concept-test :tag-association ["CMR"]))

(deftest save-tag-association-specific-test
  (testing "saving new tag associations"
    (are2 [tag-association exp-status exp-errors]
          (let [tag-collection (util/create-and-save-collection "REG_PROV" 1)
                tag (util/create-and-save-tag 1)
                {:keys [status errors]} (util/save-concept tag-association)]

            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))

          "failure when using non system-level provider"
          (assoc (util/tag-association-concept tag-collection tag 2) :provider-id "REG_PROV")
          422
          [(str "Tag association could not be associated with provider [REG_PROV]. "
                "Tag associations are system level entities.")])))
