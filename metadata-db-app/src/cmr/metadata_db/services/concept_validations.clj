(ns cmr.metadata-db.services.concept-validations
  (:require
   [clojure.set :as set]
   [cmr.common.concepts :as cc]
   [cmr.common.date-time-parser :as p]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.provider-service :as providers]))

(def MAX_REVISION_ID
  "The maximum value a revision ID can take so as not to conflict with transaction ids.
  This is necessary because we use revision id as the version in elasticsearch. We will
  eventually switch to using the global transaction id as the elasticsearch version so all
  transaction ids must be greater than the revision ids. Once this switch has been completely
  made we can remove the limitation on the maximum revision id."
  999999999)

(defn concept-type-missing-validation
  [concept]
  (when-not (:concept-type concept)
    [(msg/missing-concept-type)]))

(defn provider-id-missing-validation
  [concept]
  (when-not (:provider-id concept)
    [(msg/missing-provider-id)]))

(defn native-id-missing-validation
  [concept]
  (when-not (:native-id concept)
    [(msg/missing-native-id)]))

(defn concept-id-missing-validation
  [concept]
  (when-not (:concept-id concept)
    [(msg/missing-concept-id-field)]))

(def concept-type->required-extra-fields
  "A map of concept type to the deleted flag to the required extra fields."
  {:collection {true #{}
                false #{:short-name :version-id :entry-id :entry-title}}
   :granule {true #{:parent-collection-id}
             false #{:parent-collection-id :parent-entry-title :granule-ur}}
   :service {true #{}
             false #{:service-name}}
   :tool {true #{}
          false #{:tool-name}}
   :subscription {true #{}
                  false #{:subscription-name :subscriber-id :normalized-query :subscription-type}}
   :tag-association {true #{}
                     false #{:associated-concept-id :associated-revision-id}}
   :variable {true #{}
              false #{:variable-name :measurement}}
   :variable-association {true #{}
                          false #{:associated-concept-id :associated-revision-id}}
   :service-association {true #{}
                         false #{:associated-concept-id :associated-revision-id}}
   :tool-association {true #{}
                      false #{:associated-concept-id :associated-revision-id}}})

(defn extra-fields-missing-validation
  "Validates that the concept is provided with extra fields and that all of them are present
  and not nil."
  [concept]
  (if-let [extra-fields (util/remove-nil-keys (:extra-fields concept))]
    (map #(msg/missing-extra-field %)
         (set/difference (get-in concept-type->required-extra-fields
                                 [(:concept-type concept) (true? (:deleted concept))])
                         (set (keys extra-fields))))
    (when (contains? concept-type->required-extra-fields (:concept-type concept))
      [(msg/missing-extra-fields)])))

(defn nil-fields-validation
  "Validates that none of the fields are nil."
  [concept]
  (reduce-kv (fn [acc field value]
               (if (nil? value)
                 (conj acc (msg/nil-field field))
                 acc))
             []
             (dissoc concept :revision-date :revision-id :user-id)))

(defn datetime-validator
  [field-path]
  (fn [concept]
    (when-let [value (get-in concept field-path)]
      (try
        (p/parse-datetime value)
        nil
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (:errors data)))))))

(defn- nil-extra-fields-validation
  "Validates that among the extra fields, only delete-time, version-id, source-revision-id  and associated-revision-id
  can sometimes be nil."
  [concept]
  (nil-fields-validation (apply dissoc (:extra-fields concept)
                                [:delete-time :version-id :source-revision-id :associated-revision-id :target-provider-id :collection-concept-id])))

(defn concept-id-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (cc/concept-id-validation concept-id)))

(defn- max-revision-id-validation
  "Validates that the revision id given (if any) is less than the start of the transaction-id
  sequence start."
  [concept]
  (when-let [revision-id (:revision-id concept)]
    (when (> revision-id MAX_REVISION_ID)
      [(format "revision-id [%d] exceeds the maximum allowed value of %d." revision-id MAX_REVISION_ID)])))

(defn- concept-id-matches-concept-fields-validation-no-provider
  "Validate that the concept-id has the correct form and that values represented in the concept's
  concept-id match the values in the concept's concept map. In particular, that the concept-type
  in the map matches the concept-type parsed from the concpet-id.

  This is a subset of the fields validation done for other concept types. Other types require
  the provider-id to match as well. This check is not done here as the provider-id for tags
  and groups is not yet set at the point at which this validation is called."
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (when-not (cc/concept-id-validation concept-id)
      (let [{:keys [concept-type _]} (cc/parse-concept-id concept-id)]
        (when-not (= concept-type (:concept-type concept))
          [(msg/invalid-concept-id concept-id "CMR" (:concept-type concept))])))))

(defn- humanizer-native-id-validation
  "Validate that the native id of humanizer concept is 'humanizer'. We only have one humanizer
  concept in CMR. This check is to make sure that no more than one humanizer exists in CMR."
  [concept]
  (when-not (= cc/humanizer-native-id (:native-id concept))
    [(format "Humanizer concept native-id can only be [%s], but was [%s]."
             cc/humanizer-native-id (:native-id concept))]))

(defn- concept-id-match-fields-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (when-not (cc/concept-id-validation concept-id)
      (let [{:keys [concept-type provider-id]} (cc/parse-concept-id concept-id)]
        (when-not (and (= concept-type (:concept-type concept))
                       (= provider-id (:provider-id concept)))
          [(msg/invalid-concept-id concept-id (:provider-id concept) (:concept-type concept))])))))

(def ^:private base-concept-validations
  "Validations for all concept types"
  [concept-type-missing-validation
   native-id-missing-validation
   concept-id-validation
   max-revision-id-validation
   nil-fields-validation
   nil-extra-fields-validation
   (datetime-validator [:revision-date])
   (datetime-validator [:extra-fields :delete-time])])

(def default-concept-validation
  "Builds a function that validates a concept and returns a list of errors"
  (util/compose-validations
    (conj base-concept-validations
          extra-fields-missing-validation
          concept-id-match-fields-validation
          provider-id-missing-validation)))

(def tag-concept-validation
  "Builds a function that validates a concept map that has no provider and returns a list of errors"
  (util/compose-validations (conj base-concept-validations
                                  concept-id-matches-concept-fields-validation-no-provider)))

(def association-concept-validation
  "Builds a function that validats a tag association concept map that has no provider and returns
  a list of errors"
  (util/compose-validations (conj base-concept-validations
                                  concept-id-matches-concept-fields-validation-no-provider)))

(def group-concept-validation
  "Builds a function that validates a group concept"
  (util/compose-validations (conj base-concept-validations
                                  concept-id-match-fields-validation
                                  provider-id-missing-validation)))

(def humanizer-concept-validation
  "Builds a function that validates a concept map that has no provider and returns a list of errors"
  (util/compose-validations (conj base-concept-validations
                                  concept-id-matches-concept-fields-validation-no-provider
                                  humanizer-native-id-validation)))

(def validate-concept-default
  "Validates a concept. Throws an error if invalid."
  (util/build-validator :invalid-data default-concept-validation))

(def validate-tag-concept
  "validates a tag concept. Throws an error if invalid."
  (util/build-validator :invalid-data tag-concept-validation))

(def validate-association-concept
  "Validates a tag association concept. Throws an error if invalid."
  (util/build-validator :invalid-data association-concept-validation))

(def validate-concept-group
  "Validates a group concept. Throws and error if invalid."
  (util/build-validator :invalid-data group-concept-validation))

(def validate-humanizer-concept
  "validates a humanizer concept. Throws an error if invalid."
  (util/build-validator :invalid-data humanizer-concept-validation))

(defmulti validate-concept
  "Validates a concept. Throws an error if invalid."
  (fn [concept]
    (:concept-type concept)))

(defmethod validate-concept :tag
  [concept]
  (validate-tag-concept concept))

(defmethod validate-concept :tag-association
  [concept]
  (validate-association-concept concept))

(defmethod validate-concept :access-group
  [concept]
  (validate-concept-group concept))

(defmethod validate-concept :humanizer
  [concept]
  (validate-humanizer-concept concept))

(defmethod validate-concept :service-association
  [concept]
  (validate-association-concept concept))

(defmethod validate-concept :tool-association
  [concept]
  (validate-association-concept concept))

(defmethod validate-concept :variable-association
  [concept]
  (validate-association-concept concept))

(defmethod validate-concept :generic-association
  [concept]
  (validate-association-concept concept))

(defmethod validate-concept :default
  [concept]
  (validate-concept-default concept))

(def valid-tombstone-keys
  #{:concept-id :revision-id :revision-date :concept-type :deleted :user-id :skip-publication})

(defn validate-tombstone-keys
  "Validates that there are no extraneous keys"
  [tombstone]
  (map msg/invalid-tombstone-field
       (set/difference (set (keys tombstone))
                       valid-tombstone-keys)))

(def tombstone-request-validation
  (util/compose-validations [concept-id-missing-validation
                             validate-tombstone-keys]))

(def validate-tombstone-request
  (util/build-validator :invalid-data tombstone-request-validation))
