(ns cmr.system-int-test.ingest.misc.generic-document-validation-test
   "Provides tests for generic document validation functionality."
   (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.system-int-test.system :as s]
    [clojure.string :as string]
    [cheshire.core :as json]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(defn- create-citation-document
  "Creates a citation generic document with the given values"
  [provider-id native-id name identifier identifier-type & [optional-fields]]
  (let [doc (merge
             {:Name name
              :Identifier identifier
              :IdentifierType identifier-type
              :MetadataSpecification
              {:URL "https://cdn.earthdata.nasa.gov/generics/citation/v1.0.0"
               :Name "Citation"
               :Version "1.0.0"}}
             optional-fields)]
    (ingest/ingest-concept
     {:provider-id provider-id
      :concept-type :citation
      :native-id native-id
      :format "application/json"
      :metadata (json/generate-string doc)})))

(deftest citation-uniqueness-validation-test
  (testing "Citation document uniqueness validation"
    (let [_ (e/login (s/context) "user1")
          citation1-response (create-citation-document
                              "PROV1"
                              "citation-1"
                              "Example Citation"
                              "10.5067/ABC123XYZ"
                              "DOI"
                              {:ResolutionAuthority "https://doi.org"
                               :RelationshipType "IsCitedBy"})]
      (println "Response for citation 1:" (pr-str citation1-response))
      (is (= 201 (:status citation1-response)))
      (index/wait-until-indexed)

      (testing "Same Identifier and IdentifierType combination is rejected"
        (let [duplicate-response (create-citation-document
                                  "PROV1"
                                  "citation-2"
                                  "Different Citation Name"
                                  "10.5067/ABC123XYZ"
                                  "DOI"
                                  {:ResolutionAuthority "https://doi.org"
                                   :RelationshipType "IsReferencedBy"})]

          (is (= 422 (:status duplicate-response)))
          (is (string/includes?
               (:errors duplicate-response)
               "Values 10.5067/ABC123XYZ, DOI for fields Identifier, IdentifierType must be unique"))
          (is (string/includes?
               (:errors duplicate-response)
               (str "Duplicate concept IDs: " (get-in citation1-response [:concept-id]))))))

      (testing "Different Identifier with same IdentifierType is allowed"
        (let [different-id-response (create-citation-document
                                     "PROV1"
                                     "citation-3"
                                     "Another Citation"
                                     "10.5067/DIFFERENT"
                                     "DOI")]

          (is (= 201 (:status different-id-response)))))

      (testing "Same Identifier with different IdentifierType is allowed"
        (let [different-type-response (create-citation-document
                                       "PROV1"
                                       "citation-4"
                                       "Yet Another Citation"
                                       "10.5067/ABC123XYZ"
                                       "Other")]

          (is (= 201 (:status different-type-response)))))

      (testing "Update with same concept-id should not trigger uniqueness validation"
        (let [update-response (create-citation-document
                               "PROV1"
                               "citation-1"
                               "Updated Example Citation"
                               "10.5067/ABC123XYZ"
                               "DOI")]

          (is (= 200 (:status update-response)))))

      (testing "Delete and re-create with same fields"
        ;; Delete the first citation
        (let [delete-response (ingest/delete-concept
                               {:provider-id "PROV1"
                                :concept-type :citation
                                :native-id "citation-1"})]

          (is (= 200 (:status delete-response)))
          (index/wait-until-indexed)

          ;; Re-create with same fields should succeed now
          (let [recreate-response (create-citation-document
                                   "PROV1"
                                   "citation-2-new"
                                   "Recreated Citation"
                                   "10.5067/ABC123XYZ"
                                   "DOI")]

            (is (= 201 (:status recreate-response)))

            (testing "Cross-provider uniqueness"
              ;; Try to create a citation with the same identifier/type in a different provider
              (let [cross-provider-response (create-citation-document
                                             "PROV2"
                                             "citation-prov2"
                                             "Cross Provider Citation"
                                             "10.5067/ABC123XYZ"
                                             "DOI")]

                (is (= 422 (:status cross-provider-response)))
                (is (string/includes?
                     (:errors cross-provider-response)
                     "Values 10.5067/ABC123XYZ, DOI for fields Identifier, IdentifierType must be unique"))
                (is (string/includes?
                     (:errors cross-provider-response)
                     (str "Duplicate concept IDs: " (get-in recreate-response [:concept-id]))))))))))))
