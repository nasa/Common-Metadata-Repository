(ns cmr.common.validations.core
  "Validation framework created from scratch. Both bouncer and validateur are
  Clojure Validation frameworks but they have bugs and limitations that made
  them inappropriate for use.

  A validation is a function. It takes 2 arguments a field path vector and a
  value. It returns either nil or a map of field paths to a list of errors.
  Maps and lists will automatically be converted into record-validation or
  seq-of-validations."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.messages :as msg]))

(comment

  ;; Example Validations

  (def address-validations
    {:city required
     :street required})

  (defn last-not-first
    [field-path person-name]
    (when (= (:last person-name) (:first person-name))
      {field-path ["Last name must not equal first name"]}))

  (def person-validations
    {:addresses (every address-validations)
     :name (first-failing {:first required
                           :last required}
                          last-not-first)
     :age [required integer]})

  (validate person-validations {:addresses [{:street "5 Main"
                                             :city "Annapolis"}
                                            {:city "dd"}
                                            {:city "dd"}
                                            {:street "dfkkd"}]
                                :name {:first "Jason"
                                       :last "Jason"}
                                :age "35"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support functions

(defn humanize-field
  "Converts a keyword to a humanized field name"
  [field]
  (when field
    (->> (str/split (csk/->kebab-case (name field)) #"-")
         (map str/capitalize)
         (str/join " "))))

(defn create-error-message
  "Formats a single error message using the field path and the error format."
  [field-path error]
  ;; Get the last field path value that's not a number.
  (let [field (last (remove number? field-path))]
    (format error (humanize-field field))))

(defn create-error-messages
  "Creates error messages with the response from validate."
  [field-errors]
  (for [[field-path errors] field-errors
        error errors]
    (create-error-message field-path error)))

(defn validate
  "Returns a map of fields to error messages."
  ([validation value]
   (validate validation [] value))
  ([validation key-path value]
   (cond
     (sequential? validation)
     (reduce
       (fn [error-map v]
         (merge-with concat error-map (validate v key-path value)))
       {}
       validation)
     (map? validation)
     (reduce
       (fn [error-map [k v]]
          (merge error-map (validate v (conj key-path k) (get value k))))
       {}
       validation)
     (fn? validation) (validation key-path value))))

(defn validate!
  "Validates the given value with the validation. If there are any errors
  they are thrown as a service error"
  [validation value]
  (let [errors (validate validation [] value)]
    (when (seq errors)
      (errors/throw-service-errors
        :bad-request (create-error-messages errors)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations

(defn first-failing
  "Returns a validator that returns the output of the first validator in
  validators to return any errors."
  [& validators]
  (fn [key-path value]
    (loop [[v & more] validators]
      (when v
        (let [errors (validate v key-path value)]
          (if (seq errors)
            errors
            (recur more)))))))

(defn pre-validation
  "Runs a function on the value before validation begins. The result of prefn
  is passed to the validation."
  [prefn validation]
  (fn [field-path value]
    (validate validation field-path (prefn value))))

(defn field-cannot-be-blank
  "Validates that the value is not nil, whitespace, or an empty string"
  [field-path value]
  (when (str/blank? value)
    {field-path [(msg/required)]}))

(defn required
  "Validates that the value is not nil"
  [field-path value]
  (when (nil? value)
    {field-path [(msg/required)]}))

(defn every
  "Validate a validation against every item in a sequence. The field path will
  contain the index of the item that had an error"
  [validation]
  (fn [field-path values]
    (let [error-maps (for [[idx value] (map-indexed vector values)]
                       (validate validation (conj field-path idx) value))]
      (apply merge-with concat error-maps))))

(defn validate-integer
  "Validates that the value is an integer"
  [field-path value]
  (when (and value (not (integer? value)))
    {field-path [(msg/integer value)]}))

(defn validate-datetime
  "Validates that the value is a datetime."
  [field-path value]
  (when value
    (try
      (date-time-parser/parse-datetime (str value))
      nil
      (catch Exception e
        {field-path [(msg/datetime value)]}))))

(defn validate-number
  "Validates that the value is a number"
  [field-path value]
  (when (and value (not (number? value)))
    {field-path [(msg/number value)]}))

(defn within-range
   "Creates a validator within a specified range"
   [minv maxv]
   (fn [field-path value]
    (when (and value (or (neg? (compare value minv))
                         (pos? (compare value maxv))))
      {field-path [(msg/within-range minv maxv value)]})))

(defn field-cannot-be-changed
  "Validation that a field in a object has not been modified. Accepts optional
  nil-allowed? parameter which indicates the validation should be skipped if
  the new value is nil."
  ([field]
   (field-cannot-be-changed field false))
  ([field nil-allowed?]
   (fn [field-path object]
     (let [existing-value (get-in object [:existing field])
           new-value (get object field)
           ;; if nil is allowed and the new value is nil we skip validation.
           skip-validation? (and nil-allowed? (nil? new-value))]
       (when (and (not skip-validation?)
                  (not= existing-value new-value))
         {(conj field-path field)
          [(msg/field-cannot-be-changed existing-value new-value)]})))))

(defn when-present
  "Validation-transformer: Returns a validation which only runs validations
  when a value is present."
  [validation]
  (fn [key-path v]
    (when (some? v)
      (validate validation key-path v))))
