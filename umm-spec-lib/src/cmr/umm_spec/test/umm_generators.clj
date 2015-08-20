(ns cmr.umm-spec.test.umm-generators
  "TODO"
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [com.gfredericks.test.chuck.generators :as chgen]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.record-generator :as record-gen]
            [clojure.set :as set]))

;; Consider moving this to common lib

(defmulti ^:private schema-type->generator
  "TODO"
  (fn [schema type-name schema-type]
    (cond
      (:type schema-type) (:type schema-type)
      (:$ref schema-type) :$ref)))

(defmethod schema-type->generator :default
  [schema type-name schema-type]
  (throw (Exception. (str "No method for " (pr-str schema-type)))))

(defmethod schema-type->generator "object"
  [schema type-name schema-type]
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

(comment

  (gen/sample (schema->generator js/umm-c-schema) 1)


  )



(defmethod schema-type->generator "array"
  [schema type-name schema-type]
  (let [item-generator (schema-type->generator schema type-name (:items schema-type))]

    ;; TODO this should choose the min and max from defaults and should limit max in a better way
    (gen/vector item-generator
                (:minItems schema-type 0)
                ;; Limit the maximum number of items in an array to 5. Default is 3
                (min (:maxItems schema-type 3) 5))))

(defmethod schema-type->generator :$ref
  [schema type-name schema-type]
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
  (cond
    (= (:format schema-type) "date-time")
    ext-gen/date-time

    (:enum schema-type)
    (gen/elements (:enum schema-type))

    :else
    (let [{:keys [minLength maxLength]} (merge string-defaults schema-type)]
      ;; Limit all strings to a maximum length so that it will make it easier to debug
      (ext-gen/string-ascii minLength (min maxLength (:maxLength string-defaults))))))

(defmethod schema-type->generator "number"
  [_ _ {:keys [minimum maximum]}]
  (cmr.common.dev.capture-reveal/capture-all)
  (let [double-gen (gen/fmap double gen/ratio)]
    (if (or minimum maximum)
      (let [[minv maxv] (sort [(double (or minimum -10.0))
                               (double (or maximum 10.0))])]
        (gen/such-that #(<= minv % maxv) double-gen))
      double-gen)))

(defmethod schema-type->generator "integer"
  [_ _ {:keys [minimum maximum]}]
  (if (or minimum maximum)
    (let [[minv maxv] (sort [(or minimum -10) (or maximum 10)])]
      (gen/such-that #(<= minv % maxv) gen/int))
    gen/int))

(defmethod schema-type->generator "boolean"
  [_ _ _]
  gen/boolean)

(defn schema->generator
  "TODO"
  [schema]
  (let [root-type-def (get-in schema [:definitions (:root schema)])]
    (schema-type->generator schema (:root schema) root-type-def)))











