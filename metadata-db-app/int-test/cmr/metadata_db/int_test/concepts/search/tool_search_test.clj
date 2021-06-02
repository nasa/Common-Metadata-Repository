(ns cmr.metadata-db.int-test.concepts.search.tool-search-test
  "Contains integration tests for searching tools."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-tools
  (let [;; create two tools with the same native-id on different providers with multiple revisions
        tool1 (concepts/create-and-save-concept :tool "REG_PROV" 1 3)
        tool2 (concepts/create-and-save-concept :tool "SMAL_PROV1" 1 2)]
    (testing "find latest revisions"
      (are3 [tools params]
        (let [found-tools (->> (util/find-latest-concepts :tool params)
                               :concepts
                               util/concepts-for-comparison)]
          ;; this verifies the number of tools found is correct,
          ;; we had bug where the same tool shows up multiple times in the results
          (is (= (count tools)
                 (count found-tools)))
          ;; this verifies the content of the tools found is correct
          (is (= (set tools)
                 (set found-tools))))

        "with metadata search by provider-id"
        [tool1] {:provider-id "REG_PROV"}

        "with metadata search by concept-id"
        [tool1] {:concept-id (:concept-id tool1)}

        "with metadata search by native-id"
        [tool1 tool2] {:native-id (:native-id tool2)}

        "with metadata search by provider-id and native-id"
        [tool2] {:provider-id "SMAL_PROV1"
                 :native-id (:native-id tool2)}

        "exclude metadata search by concept-id"
        [(dissoc tool1 :metadata)] {:concept-id (:concept-id tool1)
                                    :exclude-metadata true}

        "exclude metadata search by provider-id and native-id"
        [(dissoc tool2 :metadata)] {:provider-id "SMAL_PROV1"
                                    :native-id (:native-id tool2)
                                    :exclude-metadata true}

        "no match by native id"
        [] {:native-id "foo"}

        "no match by concept id"
        [] {:concept-id "TL99-REG_PROV"}))

    (testing "find all revisions"
      (let [num-of-tools (-> (util/find-concepts :tool {})
                             :concepts
                             count)]
        (is (= 5 num-of-tools))))))

(deftest find-tools-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [tool] with parameters [short-name] is not supported."]}
           (util/find-concepts :tool {:short-name "TLN1"})))))
