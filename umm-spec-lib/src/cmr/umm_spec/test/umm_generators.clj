(ns cmr.umm-spec.test.umm-generators
  "Contains code for creating Clojure test.check generators from a JSON schema."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [com.gfredericks.test.chuck.generators :as chgen]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.record-generator :as record-gen]
            [clojure.set :as set]))

;; We could move this to common lib if desired at some point. There's not much here that is UMM specific.

(defmulti ^:private schema-type->generator
  "Converts a schema type into a Clojure test.check generator."
  (fn [schema type-name schema-type]
    (cond
      (:type schema-type) (:type schema-type)
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

(defn- object-like-schema-type->generator
  "Takes an object-like schema type and generates a generator. By \"object-like\" it means a map
  with keys properties, required, and additionalProperties. This is used to handle a normal object
  with properties or an object which uses oneOf to specify between lists of properties."
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:properties :required :additionalProperties} schema-type)
  (let [constructor-fn (if type-name
                         (record-gen/schema-type-constructor schema type-name)
                         identity)
        properties (:properties schema-type)

        ;; Create a map of property keys to generators for those properties
        prop-gens (into {} (for [[k subtype] properties]
                             [k (schema-type->generator schema nil subtype)]))
        ;; Figure out which properties are required and which are optional
        required-properties (set (map keyword (:required schema-type)))
        optional-properties (vec (set/difference (set (keys properties)) required-properties))]

    (chgen/for [;; Determine which properties to generate in this instance of the object
                num-optional-fields (gen/choose 0 (count optional-properties))
                :let [selected-properties (concat required-properties
                                                  (subvec optional-properties
                                                          0 num-optional-fields))
                      ;; Create a map of property names to generators
                      selected-prop-gens (select-keys prop-gens selected-properties)]
                ;; Generate a hash map containing the properties
                prop-map (apply gen/hash-map (flatten (seq selected-prop-gens)))]
               ;; Construct a record from the hash map
               (constructor-fn prop-map))))

(defn- assert-field-not-present-with-one-of
  [schema-type k]
  (when (k schema-type)
    (throw (Exception. (format (str "UMM generator can not handle an object with both a top level "
                                    "%s and oneOf. schema-type: %s")
                               (name k) (pr-str schema-type))))))

(defmethod schema-type->generator "object"
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:properties :additionalProperties :required :oneOf :anyOf} schema-type)
  (if-let [one-of (:oneOf schema-type)]
    (do
      ;; These fields aren't supported in schema-type if oneOf is used
      (doseq [f [:anyOf :properties :required]]
        (assert-field-not-present-with-one-of schema-type f))

      (gen/one-of (mapv #(object-like-schema-type->generator schema type-name %) one-of)))

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
        (select-keys schema-type [:properties :required :additionalProperties])))))

(def array-defaults
  {:minItems 0
   :maxItems 3})

(defmethod schema-type->generator "array"
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:items :minItems :maxItems} schema-type)
  (let [{:keys [items minItems maxItems]} (merge array-defaults schema-type)
        item-generator (schema-type->generator schema type-name items)]
    (gen/vector item-generator
                minItems
                ;; Limit the maximum number of items in an array to 5.
                (min maxItems 5))))

(defmethod schema-type->generator :$ref
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:$ref} schema-type)
  (let [[ref-schema ref-schema-type] (js/lookup-ref schema schema-type)]
    (schema-type->generator ref-schema
                            (get-in schema-type [:$ref :type-name])
                            ref-schema-type)))

;; Primitive Types

(def string-defaults
  {:minLength 0
   :maxLength 10})

(defmethod schema-type->generator "string"
  [schema type-name schema-type]
  (rejected-unexpected-fields #{:format :enum :minLength :maxLength} schema-type)
  (cond
    (= (:format schema-type) "date-time")
    ext-gen/date-time

    ;; Check to see if another format is used that we weren't expected
    (:format schema-type)
    (throw (Exception. (format "Unsupported string format [%s] in schema type %s"
                               (pr-str (:format schema-type)) (pr-str schema-type))))

    (:enum schema-type)
    (gen/elements (:enum schema-type))

    :else
    (let [{:keys [minLength maxLength]} (merge string-defaults schema-type)]
      ;; Limit all strings to a maximum length so that it will make it easier to debug
      (ext-gen/string-ascii minLength (min maxLength (:maxLength string-defaults))))))

(defmethod schema-type->generator "number"
  [_ _ schema-type]
  (rejected-unexpected-fields #{:minimum :maximum} schema-type)
  (let [{:keys [minimum maximum]} schema-type
        double-gen (gen/fmap double gen/ratio)]
    (if (or minimum maximum)
      (let [[minv maxv] (sort [(double (or minimum -10.0))
                               (double (or maximum 10.0))])]
        (gen/such-that #(<= minv % maxv) double-gen))
      double-gen)))

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
  (schema->generator js/umm-c-schema))

(comment

  (last (gen/sample (schema->generator js/umm-c-schema) 10))

  )









