(in-ns 'cmr.indexer.test.services.index-fields)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Test Data   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- es-doc-var-rev-1
  "Returns dummy elasticsearch doc for testing"
  []
  (let [concept-id "V1234-PROV1"
        native-id "native-id-1"
        entry-title "Dummy entry title"
        provider-id "PROV1"
        short-name "DummyShort"
        measurement1 "Measure1"
        measurement2 "Measure2"
        variable1 "Var1"
        variable2 "Var2"
        measurements (format "%s %s" measurement1 measurement2)]
    {:concept-id concept-id
     :native-id native-id
     :entry-title entry-title
     :entry-title-lowercase (string/lower-case entry-title)
     :provider-id provider-id
     :provider-id-lowercase (string/lower-case provider-id)
     :short-name short-name
     :short-name-lowercase (string/lower-case short-name)
     :measurements measurements
     :measurements-lowercase (string/lower-case measurements)
     :variables [{:measurement measurement1
                  :measurement-lowercase (string/lower-case measurement1)
                  :variable variable1
                  :variable-lowercase (string/lower-case variable1)
                  :originator-id-lowercase "alice"}
                 {:measurement measurement2
                  :measurement-lowercase (string/lower-case measurement2)
                  :variable variable2
                  :variable-lowercase (string/lower-case variable2)
                  :originator-id-lowercase "bob"}]}))

(defn- es-doc-var-rev-2
  "Returns dummy elasticsearch doc for testing"
  []
  (let [concept-id "V1234-PROV1"
        native-id "native-id-1"
        entry-title "Dummy entry title"
        provider-id "PROV1"
        short-name "DummyShort"
        measurement1 "Ska"
        measurement2 "Doooosh!"
        measurements (format "%s %s" measurement1 measurement2)]
    {:concept-id concept-id
     :native-id native-id
     :entry-title entry-title
     :entry-title-lowercase (string/lower-case entry-title)
     :provider-id provider-id
     :provider-id-lowercase (string/lower-case provider-id)
     :short-name short-name
     :short-name-lowercase (string/lower-case short-name)
     :measurements measurements
     :measurements-lowercase (string/lower-case measurements)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- save-variable
  "Save a variable Elasticsearch document."
  ([es-doc concept-id revision-id]
   (save-variable es-doc concept-id revision-id {}))
  ([es-doc concept-id revision-id options]
   (save-document-in-elastic "variable" es-doc concept-id revision-id options)))

(defn- delete-variable
  "Delete a variable Elasticsearch document."
  ([concept-id revision-id]
   (delete-variable concept-id revision-id {}))
  ([concept-id revision-id options]
   (delete-document-in-elastic "variable" concept-id revision-id options)))

(defn- assert-match-in-var
  "Assert that value associated with the nested fields for the retrieved
  document with the given concept-id contains the given substring."
  [concept-id fields substr]
  (assert-field-match-in "variable" concept-id fields substr))

(defn- assert-not-match-in-var
  "Assert that value associated with the nested fields for the retrieved
  document with the given concept-id does not contain the given substring."
  [concept-id fields substr]
  (assert-not-field-match-in "variable" concept-id fields substr))

(defn- assert-delete-var
  "Assert the document with the given id is deleted."
  [concept-id]
  (assert-delete "variable" concept-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Tests   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-variable-test
  (save-variable (es-doc-var-rev-1) "V1234-PROV1" 1)
  (assert-match-in-var "V1234-PROV1" [:entry-title] "Dummy")
  (assert-match-in-var "V1234-PROV1" [:measurements] "Measure")
  (assert-not-match-in-var "V1234-PROV1" [:measurements-lowercase] "Measure")
  (assert-match-in-var "V1234-PROV1" [:measurements-lowercase] "measure")
  (assert-match-in-var "V1234-PROV1" [:variables 0 :measurement] "Measure")
  (assert-match-in-var "V1234-PROV1" [:variables 0 :variable-lowercase] "var")
  (delete-variable "V1234-PROV1" 1)
  (assert-delete-var "V1234-PROV1")
  (assert-not-match-in-var "V1234-PROV1" :entry-title "Dummy"))

(deftest save-variable-all-revisions-test
  (save-variable (es-doc-var-rev-1) "V1234-PROV1" 1 {:all-revisions-index? true})
  (save-variable (es-doc-var-rev-2) "V1234-PROV1" 2 {:all-revisions-index? true})
  (assert-match-in-var "V1234-PROV1,1" [:measurements-lowercase] "measure")
  (assert-not-match-in-var "V1234-PROV1,1" [:measurements-lowercase] "skadoooosh!")
  (assert-match-in-var "V1234-PROV1,2" [:measurements-lowercase] "ska doooosh!")
  (assert-not-match-in-var "V1234-PROV1,2" [:measurements-lowercase] "measure")
  (delete-variable "V1234-PROV1" 1 {:all-revisions-index? true})
  (assert-delete-var "V1234-PROV1")
  (assert-not-match-in-var "V1234-PROV1,1" [:measurements] "measure")
  (delete-variable "V1234-PROV1" 2 {:all-revisions-index? true})
  (assert-delete-var "V1234-PROV1")
  (assert-not-match-in-var "V1234-PROV1,2" [:measurements-lowercase] "skadoooosh!"))
