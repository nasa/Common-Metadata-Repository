(ns cmr.common.validations.core
  "Validation framework created from scratch. Both bouncer and validateur are Clojure Validation
  frameworks but they have bugs and limitations that made them inappropriate for use.

  TODO complete documentation not written yet. I will first make sure that we have something that
  will work for our needs before writing the documentation.

  Quick documentation notes:
  A validation is a function.
  It takes 2 arguments a field path vector and a value. It returns either nil or a map of field
  paths to a list of errors.

  ")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support functions

(declare auto-validation-convert)

(defn record-validation
  "Converts a map into a record validator"
  [field-map]
  (fn [field-path value]
    (reduce (fn [field-errors [validation-field validation]]
              (let [validation (auto-validation-convert validation)
                    errors (validation (conj field-path validation-field)
                                       (validation-field value))]
                (if (seq errors)
                  (merge field-errors errors)
                  field-errors)))
            {}
            field-map)))

(defn seq-of-validations
  "Returns a validator merging results from a list of validators. Short circuits of first failure.
  We could make short circuiting behavior optional."
  [validators]
  (fn [field-path value]
    (first
      (for [validator validators
            :let [validator (auto-validation-convert validator)
                  errors (validator field-path value)]
            :when (seq errors)]
        errors))))

(defn auto-validation-convert
  "Handles converting basic clojure data structures into a validation function."
  [validation]
  ;; TODO consider using protocols or other dispatch method
  (cond
    (map? validation) (record-validation validation)
    (sequential? validation) (seq-of-validations validation)
    :else validation))

(defn create-error-messages
  "TODO"
  []
  )

(defn validate
  [validation value]
  (let [validation (auto-validation-convert validation)]
    (validation [] value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations


(defn required
  "TODO"
  [field-path value]
  (when-not value
    {field-path ["%s is required."]}))

(defn integer
  "TODO"
  [field-path value]
  (when-not (integer? value)
    {field-path [(format "%%s must be an integer but was [%s]." value)]}))


(comment

  (def address-validations
    {:name required
     :street required})

  (defn last-not-first
    [field-path person-name]
    (when (= (:last person-name) (:first person-name))
      {field-path ["Last name must not equal first name"]}))

  (def person-validations
    {:address address-validations
     :name [{:first required
             :last required}
            last-not-first]
     :age [required integer]})



  (validate person-validations {:address {:street "5 Main"
                                          :city "Annapolis"}
                                :name {:first "Jason"
                                       :last "Jason"}
                                :age "35"})

  )