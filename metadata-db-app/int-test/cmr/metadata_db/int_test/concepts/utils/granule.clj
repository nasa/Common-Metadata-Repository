(ns cmr.metadata-db.int-test.concepts.utils.granule
  "Defines implementations for all of the multi-methods for granules in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def granule-xml
  "Valid ECHO10 granule for concept generation"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <RestrictionFlag>0.0</RestrictionFlag>
    <Orderable>false</Orderable>
  </Granule>")

(defmethod concepts/get-sample-metadata :granule
  [_]
  granule-xml)

(defn- create-granule-concept
  "Creates a granule concept"
  [provider-id parent-collection uniq-num attributes]
  (let [extra-fields (merge {:parent-collection-id (:concept-id parent-collection)
                             :parent-entry-title (get-in parent-collection
                                                         [:extra-fields :entry-title])
                             :delete-time nil
                             :granule-ur (str "granule-ur " uniq-num)}
                            (:extra-fields attributes))
        attributes (merge {:format "application/echo10+xml"
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id :granule uniq-num attributes)))

(defmethod concepts/parse-create-concept-args :granule
  [concept-type args]
  (let [[provider-id parent-collection uniq-num attributes] args
        attributes (or attributes {})]
    [provider-id parent-collection uniq-num attributes]))

(defmethod concepts/parse-create-and-save-args :granule
  [concept-type args]
  (let [[provider-id parent-collection uniq-num num-revisions attributes] args
        num-revisions (or num-revisions 1)
        attributes (or attributes {})]
    [[provider-id parent-collection uniq-num attributes] num-revisions]))

(defmethod concepts/create-concept :granule
  [concept-type & args]
  (let [[provider-id parent-collection uniq-num attributes] (concepts/parse-create-concept-args
                                                             :granule args)]
    (create-granule-concept provider-id parent-collection uniq-num attributes)))
