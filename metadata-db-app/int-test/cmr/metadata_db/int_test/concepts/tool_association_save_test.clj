(ns cmr.metadata-db.int-test.concepts.tool-association-save-test
  "Contains integration tests for saving tool associations. Tests saves with various
   configurations including checking for proper error handling."
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :tool-association
  [_ _ uniq-num attributes]
  (let [concept-attributes (or (:concept-attributes attributes) {})
        concept (concepts/create-and-save-concept :collection "REG_PROV" uniq-num 1
                                                  concept-attributes)
        tool-attributes (or (:tool-attributes attributes) {})
        tool (concepts/create-and-save-concept :tool "REG_PROV" uniq-num 1 tool-attributes)
        attributes (dissoc attributes :concept-attributes :tool-attributes)]
    (concepts/create-concept :tool-association concept tool uniq-num attributes)))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-tool-association-test
  (c-spec/general-save-concept-test :tool-association ["CMR"]))

(deftest save-tool-association-failure-test
  (testing "saving new tool associations on non system-level provider"
    (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
          tool (concepts/create-and-save-concept :tool "REG_PROV" 1)
          tool-association (-> (concepts/create-concept :tool-association coll tool 2)
                               (assoc :provider-id "REG_PROV"))
          {:keys [status errors]} (util/save-concept tool-association)]

      (is (= 422 status))
      (is (= [(str "Tool association could not be associated with provider [REG_PROV]. "
                   "Tool associations are system level entities.")]
             errors)))))

(deftest save-tool-assoc-data-test
  (testing "ensure that saving worked for an association with a data payload."
    (let [coll-concept (concepts/create-and-save-concept :collection "REG_PROV" 1)
          tool-concept (concepts/create-and-save-concept :tool "REG_PROV" 1)
          assoc-concept (concepts/create-concept :tool-association coll-concept tool-concept 1 {:data {:XYZ "ZYX"}})
          saved-assoc (util/save-concept assoc-concept)
          stored-assoc (:concept (util/get-concept-by-id-and-revision (:concept-id saved-assoc)
                                                                      (:revision-id saved-assoc)))
          assoc-metadata (edn/read-string (:metadata stored-assoc))]
      (is (= {:XYZ "ZYX"} (:data assoc-metadata))))))
