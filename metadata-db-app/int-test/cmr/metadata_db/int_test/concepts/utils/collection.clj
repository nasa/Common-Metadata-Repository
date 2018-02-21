(ns cmr.metadata-db.int-test.concepts.utils.collection
  "Defines implementations for all of the multi-methods for collections in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def collection-xml
  "Valid ECHO10 collection for concept generation"
  "<Collection>
    <ShortName>MINIMAL</ShortName>
    <VersionId>1</VersionId>
    <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
    <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
    <LongName>A minimal valid collection</LongName>
    <DataSetId>A minimal valid collection V 1</DataSetId>
    <Description>A minimal valid collection</Description>
    <Orderable>true</Orderable>
    <Visible>true</Visible>
  </Collection>")

(defmethod concepts/get-sample-metadata :collection
  [_]
  collection-xml)

(defn- create-collection-concept
  "Creates a collection concept"
  [provider-id uniq-num attributes]
  (let [short-name (str "short" uniq-num)
        version-id (str "V" uniq-num)
        ;; ensure that the required extra-fields are available but allow them to be
        ;; overridden in attributes
        extra-fields (merge {:short-name short-name
                             :version-id version-id
                             :entry-id (if version-id
                                         (str short-name "_" version-id)
                                         short-name)
                             :entry-title (str "dataset" uniq-num)
                             :delete-time nil}
                            (:extra-fields attributes))
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/echo10+xml"
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id :collection uniq-num attributes)))

(defmethod concepts/create-concept :collection
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :collection args)]
    (create-collection-concept provider-id uniq-num attributes)))
