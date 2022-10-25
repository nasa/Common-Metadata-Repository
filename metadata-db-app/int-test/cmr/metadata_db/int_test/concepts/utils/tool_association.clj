(ns cmr.metadata-db.int-test.concepts.utils.tool-association
  "Defines implementations for all of the multi-methods for tool associations in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [clojure.string :as string]))

(def tool-association-edn
  "Valid EDN for variable association metadata"
  (pr-str {:tool-concept-id "TL120000008-PROV1"
           :associated-concept-id "C120000000-PROV1"
           :revision-id 1
           :data {:XYZ "ZYX"}}))

(defmethod concepts/get-sample-metadata :tool-association
  [_]
  tool-association-edn)

(defn- create-tool-association-concept
  "Creates a tool association concept"
  [assoc-concept tool uniq-num attributes]
  (let [{:keys [concept-id revision-id]} assoc-concept
        tool-concept-id (:concept-id tool)
        user-id (str "user" uniq-num)
        native-id (string/join "/" [tool-concept-id concept-id revision-id])
        extra-fields (merge {:associated-concept-id concept-id
                             :associated-revision-id revision-id
                             :tool-concept-id tool-concept-id}
                            (:extra-fields attributes))
        attributes (merge {:user-id user-id
                           :format "application/edn"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    ;; no provider-id should be specified for tool associations
    (dissoc (concepts/create-any-concept nil :tool-association uniq-num attributes)
            :provider-id)))

(defmethod concepts/create-concept :tool-association
  [concept-type & args]
  (let [[associated-concept concept uniq-num attributes] (concepts/parse-create-concept-args
                                                          :tool-association args)]
    (create-tool-association-concept associated-concept concept uniq-num attributes)))

(defmethod concepts/parse-create-concept-args :tool-association
  [concept-type args]
  (concepts/parse-create-associations-args args))

(defmethod concepts/parse-create-and-save-args :tool-association
  [concept-type args]
  (concepts/parse-create-and-save-associations-args args))
