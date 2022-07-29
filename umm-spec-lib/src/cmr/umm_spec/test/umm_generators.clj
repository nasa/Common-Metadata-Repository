(ns cmr.umm-spec.test.umm-generators
  "Contains code for creating Clojure test.check generators from a JSON schema."
  (:require
   [clojure.set :as set]
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext-gen]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.record-generator :as record-gen]
   [cmr.umm-spec.test.umm-record-sanitizer :as san]
   [com.gfredericks.test.chuck.generators :as chgen]))

;;; We could move this to common lib if desired at some point. There's not much here that is UMM specific.

(defmulti ^:private schema-type->generator
  "Converts a schema type into a Clojure test.check generator."
  (fn [schema type-name schema-type]
    (cond
      (:type schema-type) (:type schema-type)
      (:oneOf schema-type) "oneOf"
      (:anyOf schema-type) "anyOf"
      (:if schema-type) "if"
      (:then schema-type) "then"
      (:else schema-type) "else"
      (:$ref schema-type) :$ref)))

(defmethod schema-type->generator :default
  [schema type-name schema-type]
  (throw (Exception. (str "No method for " (pr-str schema-type)))))

(defn- rejected-unexpected-fields
  "Checks that the schema-type does not contain unexpected fields. This lets us know if some JSON
  schema feature that's not supported by the generator code is being used. We should update our
  code if this exception is thrown."
  [expected-field-set schema-type]
  (let [expected-field-set (into expected-field-set [:description :type])]
    (when-let [unexpected-fields (seq (set/difference (set (keys schema-type)) expected-field-set))]
      (throw (Exception. (format "The fields [%s] are not supported by generators with schema type [%s]"
                                 (pr-str unexpected-fields) (pr-str schema-type)))))))

(defn- add-dependencies
  "Checks if property has a dependency, if the dependency is not present it will add it
   to the selected-properties collection"
  [dependencies selected-properties]
  (reduce
   (fn [properties property]
     (vec (set/union (set properties)
                     (set (map keyword (get dependencies property)))
                     #{property})))
   []
   selected-properties))

(defn- object-like-schema-type->generator
  "Takes an object-like schema type and generates a generator. By \"object-like\" it means a map
  with keys properties, required, and additionalProperties. This is used to handle a normal object
  with properties or an object which uses oneOf to specify between lists of properties."
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:properties :required :additionalProperties :dependencies :not :allOf
                                :anyOf :if :then :else} schema-type)
  (let [constructor-fn (if type-name
                         (record-gen/schema-type-constructor schema type-name)
                         identity)
        properties (:properties schema-type)
        ;; The following line is a hack!!!
        ;; Since UMM-G InstrumentType has ComposedOf field that is also an InstrumentType
        ;; and there could be multiple levels of ComposedOf within an InstrumentType,
        ;; We randomly (1 out of 2) drop the ComposedOf field from properties
        ;; to not get into infinite loops of generating InstrumentTypes
        properties (if (= 0 (rand-int 2))
                     (dissoc properties :ComposedOf)
                     properties)
        dependencies (:dependencies schema-type)
        ;; Create a map of property keys to generators for those properties
        prop-gens (into {} (for [[k subtype] properties]
                             [k (schema-type->generator schema nil subtype)]))
        any-of-required (as-> (get schema-type :anyOf) any-of
                          (map :required any-of)
                          (when (seq any-of)
                            (rand-nth any-of)))
        ;; Figure out which properties are required and which are optional
        required-properties (set (map keyword (concat (:required schema-type)
                                                      any-of-required)))
        optional-properties (vec (set/difference (set (keys properties)) required-properties))
        ;; Every object must have at least one field
        min-optional-fields (if (empty? required-properties) 1 0)]
    (chgen/for [;; Determine which properties to generate in this instance of the object
                num-optional-fields (gen/choose min-optional-fields (count optional-properties))
                :let [selected-properties (concat required-properties
                                                  (subvec optional-properties
                                                          0 num-optional-fields))
                      ;; Check for dependencies, remove properties that lack their dependencies
                      selected-properties (if (seq dependencies)
                                            (add-dependencies dependencies selected-properties)
                                            selected-properties)

                      ;; Create a map of property names to generators
                      selected-prop-gens (select-keys prop-gens selected-properties)]
                ;; Generate a hash map containing the properties
                prop-map (apply gen/hash-map (flatten (seq selected-prop-gens)))]
      (do
        (when-not (seq prop-map)
          (throw (Exception. (str "Generated object with no fields for schema-type: "
                                  (pr-str schema-type)))))

        ;; Construct a record from the hash map
        (constructor-fn prop-map)))))

(defn- assert-field-not-present-with-one-of
  [schema-type k]
  (when (k schema-type)
    (throw (Exception. (format (str "UMM generator can not handle an object with both a top level "
                                    "%s and oneOf. schema-type: %s")
                               (name k) (pr-str schema-type))))))

(defn- object-one-of->generator
  "Creates a generator for a schema type with a oneOf option. oneOf in JSON schema lists separate
  sub-schemas of which only _one_ must be valid. This is difficult to generate so we limit the
  possibilities when used within an object mapping. It can be used in two different ways

  1. Specifying different sets of required fields. Similar to the choice in XML.
  2. Specifying complete different definitions of an object by specifying properties, required, and
  additionalProperties."
  [schema type-name schema-type]
  (let [one-of (:oneOf schema-type)
        uniq-keys (set (mapcat keys one-of))]
    (if (= uniq-keys #{:required})
      ;; Using oneOfs with each only specifying :required
      (let [field-sets (mapv (comp set (partial mapv keyword)) (mapv :required one-of))
            all-required-fields (reduce into field-sets)
            all-fields (set (keys (:properties schema-type)))
            one-of-types (for [field-set field-sets
                               :let [excluded-fields (set/difference all-required-fields field-set)]]
                           (-> schema-type
                               (update-in [:properties] #(apply dissoc % excluded-fields))
                               (update-in [:required] concat field-set)
                               (dissoc :oneOf)))]
        (gen/one-of (mapv #(object-like-schema-type->generator schema type-name %) one-of-types)))
      ;; Using oneOf with each specifying the full object mapping
      (do
        ;; These fields aren't supported in schema-type if oneOf is used with other fields
        (doseq [f [:anyOf :properties :required]]
          (assert-field-not-present-with-one-of schema-type f))

        (gen/one-of (mapv #(object-like-schema-type->generator schema type-name %) one-of))))))

(defmethod schema-type->generator "object"
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:properties :additionalProperties :required :oneOf
                                :anyOf :allOf :if :then :else :not :dependencies :$id} schema-type)
  (if-let [one-of (:oneOf schema-type)]
    (object-one-of->generator schema type-name schema-type)
    ;; else
    (if-let [any-of (:anyOf schema-type)]
      (->> any-of
           (map #(merge-with into schema-type %))
           (map #(dissoc % :anyOf))
           (map #(object-like-schema-type->generator schema type-name %))
           vec
           gen/one-of)
      (object-like-schema-type->generator
       schema type-name
       (select-keys schema-type [:properties :required :additionalProperties :dependencies])))))

(defmethod schema-type->generator "oneOf"
  [schema type-name schema-type]
  (gen/one-of (mapv #(schema-type->generator schema type-name %)
                    (js/expand-refs schema (:oneOf schema-type)))))

(defmethod schema-type->generator "anyOf"
  [schema type-name schema-type]
  (gen/one-of (mapv #(schema-type->generator schema type-name %)
                    (js/expand-refs schema (:anyOf schema-type)))))

(defmethod schema-type->generator "if"
  [_ _ _]
  (gen/return ""))

(defmethod schema-type->generator "then"
  [_ _ _]
  (gen/return ""))

(defmethod schema-type->generator "else"
  [_ _ _]
  (gen/return ""))

(def array-min-items 0)
(def array-max-items 5)
(def array-defaults
  {:minItems array-min-items
   :maxItems array-max-items})

(defmethod schema-type->generator "array"
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:items :minItems :maxItems :uniqueItems} schema-type)
  (let [{:keys [items minItems maxItems]} (merge array-defaults schema-type)
        item-generator (gen/such-that some? (schema-type->generator schema type-name items))
        ;; Limit the maximum number of items in an array to array-max-items.
        maxItems (min maxItems array-max-items)]
    (if (= minItems 0)
      ;; Return nil instead of empty vectors.
      (gen/one-of [(gen/return nil) (gen/vector item-generator 1 maxItems)])
      (gen/vector item-generator minItems maxItems))))

(defmethod schema-type->generator :$ref
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:$ref} schema-type)
  (let [[ref-schema ref-schema-type] (js/lookup-ref schema schema-type)]
    (schema-type->generator ref-schema
                            (get-in schema-type [:$ref :type-name])
                            ref-schema-type)))

;;; Primitive Types

(def string-defaults
  {:minLength 0
   :maxLength 10})

(defmethod schema-type->generator "string"
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:format :enum :minLength :maxLength :pattern} schema-type)
  (cond
    (= (:format schema-type) "date-time")
    ext-gen/date-time

    (= (:format schema-type) "uri")
    ext-gen/file-url-string

    ;; Check to see if another format is used that we weren't expected
    (:format schema-type)
    (throw (Exception. (format "Unsupported string format [%s] in schema type %s"
                               (pr-str (:format schema-type)) (pr-str schema-type))))

    (:enum schema-type)
    (gen/elements (:enum schema-type))

    (:pattern schema-type)
    (chgen/string-from-regex (re-pattern (:pattern schema-type)))

    :else
    (let [{:keys [minLength maxLength]} (merge string-defaults schema-type)]
      ;; Limit all strings to a maximum length so that it will make it easier to debug
      (ext-gen/string-ascii minLength (min maxLength (:maxLength string-defaults))))))

(defn scale-to-range
  "Takes the value x which is from the range minv to maxv and scales it to the range minv2 maxv2.
  From: http://stackoverflow.com/questions/5294955/how-to-scale-down-a-range-of-numbers-with-a-known-min-and-max-value"
  [minv maxv x minv2 maxv2]
  (+ (/ (* (- maxv2 minv2) (- x minv))
        (- maxv minv))
     minv2))

(defmethod schema-type->generator "number"
  [_ _ schema-type]
  (rejected-unexpected-fields #{:minimum :maximum} schema-type)
  (let [{:keys [minimum maximum]} schema-type
        seed-double-gen (ext-gen/choose-double 0 10)]
    (if (or minimum maximum)
      (let [[minv maxv] (sort [(double (or minimum -10.0))
                               (double (or maximum 10.0))])]
        (gen/fmap #(scale-to-range 0.0 10.0 % minv maxv) seed-double-gen))
      (gen/fmap double gen/ratio))))

(defmethod schema-type->generator "integer"
  [_ _ {:keys [minimum maximum]}]
  (if (or minimum maximum)
    (let [[minv maxv] (sort [(or minimum -10) (or maximum 10)])]
      (gen/choose minv maxv))
    gen/int))

(defmethod schema-type->generator "boolean"
  [_ _ _]
  gen/boolean)

(defn schema->generator
  "Given a JSON schema will return a test.check generator that will produce random valid values."
  [schema]
  (let [root-type-def (get-in schema [:definitions (:root schema)])]
    (schema-type->generator schema (:root schema) root-type-def)))

(def umm-c-generator
  (gen/fmap san/sanitized-umm-c-record
            (schema->generator js/umm-c-schema)))

(def umm-g-generator
  (gen/fmap san/sanitized-umm-g-record
            (schema->generator js/umm-g-schema)))

(def umm-s-generator
  (gen/fmap san/sanitized-umm-s-record
            (schema->generator js/umm-s-schema)))

(def umm-t-generator
  (gen/fmap san/sanitized-umm-t-record
            (schema->generator js/umm-t-schema)))

(def umm-sub-generator
  (gen/fmap san/sanitized-umm-sub-record
            (schema->generator js/umm-sub-schema)))

(def umm-var-generator
  (gen/fmap san/sanitized-umm-var-record
            (schema->generator js/umm-var-schema)))

(comment

 (schema->generator js/umm-g-schema)

 (last (gen/sample umm-c-generator 10)))
