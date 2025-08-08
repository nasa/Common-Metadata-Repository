(ns cmr.indexer.test.data.concepts.generic
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.concepts.generic :as generic]))

(def sample-citation-data
  {:RelatedIdentifiers [{:RelationshipType "Cites"
                         :RelatedIdentifier "10.5067/MODIS/MOD08_M3.061"
                         :RelatedIdentifierType "DOI"}
                        {:RelationshipType "Describes"
                         :RelatedIdentifier "ark:/13030/tf1p17542"
                         :RelatedIdentifierType "ARK"}]
   :CitationMetadata {:Title "Global Climate Study"
                      :Year 2021
                      :Author [{:Given "John" :Family "Smith" :ORCID "0000-0002-1825-0097"}]}})

(def sample-single-object-data
  {:CitationMetadata {:Title "Climate Research"
                      :Year 2020}})

(def empty-data {})

(defn assert-collections-equal-unordered
  "Asserts two collections contain the same elements, ignoring order"
  [expected actual]
  (is (= (set expected) (set actual))))

(deftest field->index-complex-field-test
  (testing "Complex field indexer with field names in format"
    (let [settings {:Field ".CitationMetadata"
                    :Name "Citation-Info"
                    :Configuration {:sub-fields ["Title" "Year"]
                                    :format "%s=%s"}}
          result (generic/field->index-complex-field settings sample-single-object-data)]
      (is (= {:citation-info "Title=Climate Research, Year=2020"
              :citation-info-lowercase "title=climate research, year=2020"}
             result))))

  (testing "Complex field with missing data"
    (let [settings {:Field ".NonExistentField"
                    :Name "Missing-Field"
                    :Configuration {:sub-fields ["Title" "Year"]
                                    :format "%s=%s"}}
          result (generic/field->index-complex-field settings sample-single-object-data)]
      (is (= {:missing-field "Title=null, Year=null"
              :missing-field-lowercase "title=null, year=null"}
             result)))))

(deftest field->index-complex-field-with-values-only-test
  (testing "Complex field with values only, single object"
    (let [settings {:Field ".CitationMetadata"
                    :Name "Citation-Values"
                    :Configuration {:sub-fields ["Title" "Year"]
                                    :format "%s:%s"}}
          result (generic/field->index-complex-field-with-values-only settings sample-single-object-data)]
      (is (= {:citation-values "Climate Research:2020"
              :citation-values-lowercase "climate research:2020"}
             result))))

  (testing "Complex field with values only, array data"
    (let [settings {:Field ".RelatedIdentifiers"
                    :Name "Related-Identifier-With-Type"
                    :Configuration {:sub-fields ["RelationshipType" "RelatedIdentifier"]
                                    :format "%s:%s"}}
          result (generic/field->index-complex-field-with-values-only settings sample-citation-data)]
      (is (= {:related-identifier-with-type ["Cites:10.5067/MODIS/MOD08_M3.061"
                                             "Describes:ark:/13030/tf1p17542"]
              :related-identifier-with-type-lowercase ["cites:10.5067/modis/mod08_m3.061"
                                                       "describes:ark:/13030/tf1p17542"]}
             result))))

  (testing "Complex field with empty array"
    (let [settings {:Field ".EmptyArray"
                    :Name "Empty-Field"
                    :Configuration {:sub-fields ["Title" "Year"]
                                    :format "%s:%s"}}
          result (generic/field->index-complex-field-with-values-only settings {:EmptyArray []})]
      (is (= {:empty-field []
              :empty-field-lowercase []}
             result)))))

(deftest field->index-simple-array-field-test
  (testing "Simple array field extracts values from array elements"
    (let [settings {:Field ".RelatedIdentifiers"
                     :Name "Related-Identifier"
                     :Configuration {:sub-fields ["RelatedIdentifier"]}}
           result (generic/field->index-simple-array-field settings sample-citation-data)]
       (assert-collections-equal-unordered
        ["10.5067/MODIS/MOD08_M3.061" "ark:/13030/tf1p17542"]
        (:related-identifier result))
       (assert-collections-equal-unordered
        ["10.5067/modis/mod08_m3.061" "ark:/13030/tf1p17542"]
        (:related-identifier-lowercase result))))

  (testing "Simple array field with multiple sub-fields"
    (let [settings {:Field ".RelatedIdentifiers"
                    :Name "Relationship-Info"
                    :Configuration {:sub-fields ["RelationshipType" "RelatedIdentifier"]}}
          result (generic/field->index-simple-array-field settings sample-citation-data)]
      (assert-collections-equal-unordered
       #{"Cites" "10.5067/MODIS/MOD08_M3.061" "Describes" "ark:/13030/tf1p17542"}
       (set (:relationship-info result)))))

  (testing "Simple array field with nested data"
    (let [settings {:Field ".CitationMetadata.Author"
                    :Name "Author-Info"
                    :Configuration {:sub-fields ["Given" "Family"]}}
          result (generic/field->index-simple-array-field settings sample-citation-data)]
      (assert-collections-equal-unordered ["John" "Smith"] (:author-info result))
      (assert-collections-equal-unordered ["john" "smith"] (:author-info-lowercase result))))

  (testing "Simple array field with empty data"
    (let [settings {:Field ".NonExistent"
                    :Name "Missing-Array"
                    :Configuration {:sub-fields ["Field1"]}}
          result (generic/field->index-simple-array-field settings empty-data)]
      (is (= {:missing-array []
              :missing-array-lowercase []}
             result))))

(deftest field->index-default-field-test
  (testing "Default field indexer for simple values"
    (let [settings {:Field ".CitationMetadata.Title"
                    :Name "Title"}
          result (generic/field->index-default-field settings sample-citation-data)]
      (is (= {:title "Global Climate Study"
              :title-lowercase "global climate study"}
             result))))

  (testing "Default field indexer with nested path"
    (let [settings {:Field ".CitationMetadata.Author.0.Given"
                    :Name "First-Author-Given"}
          result (generic/field->index-default-field settings sample-citation-data)]
      (is (= {:first-author-given "John"
              :first-author-given-lowercase "john"}
             result))))

  (testing "Default field indexer with missing field"
    (let [settings {:Field ".NonExistent.Field"
                    :Name "Missing-Field"}
          result (generic/field->index-default-field settings empty-data)]
      (is (= {:missing-field nil
              :missing-field-lowercase nil}
             result))))))
