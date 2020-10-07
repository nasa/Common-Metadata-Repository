(ns cmr.metadata-db.int-test.concepts.utils.interface
  "Defines all of the multi-methods for concept specific implementations. When adding a new
  concept type be sure to implement each of the following multi-methods."
  (:require
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.metadata-db.int-test.utility :as util]))

(defmulti get-expected-concept
  "Returns a concept for comparison with a retrieved concept."
  (fn [concept]
    (let [{:keys [concept-type]} concept]
      (if (contains? concept-service/system-level-concept-types concept-type)
        :system-level-concept
        concept-type))))

(defmethod get-expected-concept :system-level-concept
  [concept]
  (assoc concept :provider-id "CMR"))

(defmethod get-expected-concept :default
  [concept]
  concept)

(defmulti get-sample-metadata
  "Returns sample metadata for the concept type."
  (fn [concept-type]
    concept-type))

(defmulti create-concept
  "Create a concept map for the concept type."
  (fn [concept-type & args]
    concept-type))

(defmulti create-and-save-concept
  "Create a concept map for the concept type and saves the specified number of revisions of that
  concept. Returns the last revision that was saved."
  (fn [concept-type & args]
    concept-type))

(defmulti parse-create-concept-args
  "Parses the create concept arguments to handle multiple arrity or differences between concept
  types fields needed."
  (fn [concept-type & args]
    concept-type))

(defmulti parse-create-and-save-args
  "Parses the create and save concept arguments to handle multiple arrity or differences between
  concept types fields needed."
  (fn [concept-type & args]
    concept-type))

(defn- default-parse-create-concept-args
  "Function used to parse the arguments sent to the create-concept function."
  [args]
  (let [[provider-id uniq-num attributes] args
        attributes (or attributes {})]
    [provider-id uniq-num attributes]))

(defn- default-parse-create-and-save-args
  "Function used to parse the arguments sent to the create-and-save-concept function. Returns a
  collection of arguments useful for creating a concept and the number of revisions to create for
  that concept."
  [args]
  (let [[provider-id uniq-num num-revisions attributes] args
        num-revisions (or num-revisions 1)
        attributes (or attributes {})]
    [[provider-id uniq-num attributes] num-revisions]))

(defn parse-create-associations-args
  "Function used to parse the arguments sent to the create-concept function for associations
  concepts."
  [args]
  (let [[associated-concept concept uniq-num attributes] args
        attributes (or attributes {})]
    [associated-concept concept uniq-num attributes]))

(defn parse-create-and-save-associations-args
  "Function used to parse the arguments sent to the create-and-save-concept function for
  associations concepts. Returns a collection of arguments useful for creating a concept and the
  number of revisions to create for that concept."
  [args]
  (let [[associated-concept concept uniq-num num-revisions attributes] args
        num-revisions (or num-revisions 1)
        attributes (or attributes {})]
    [[associated-concept concept uniq-num attributes] num-revisions]))

(defmethod parse-create-concept-args :default
  [concept-type args]
  (default-parse-create-concept-args args))

(defmethod parse-create-and-save-args :default
  [concept-type args]
  (default-parse-create-and-save-args args))

(defn create-any-concept
  "Create a concept map for any concept type."
  [provider-id concept-type uniq-num attributes]
  (merge {:native-id (str "native-id " uniq-num)
          :metadata (get-sample-metadata concept-type)
          :deleted false}
         attributes
         ;; concept-type and provider-id args take precedence over attributes
         {:provider-id provider-id
          :concept-type concept-type}))

(defn create-and-save-concept
  "Create a concept map for the concept type and saves the specified number of revisions of that
  concept. Returns the last revision that was saved."
  [concept-type & args]
  (let [[parsed-args num-revisions] (parse-create-and-save-args concept-type args)
        concept (apply create-concept concept-type parsed-args)
        _ (dotimes [n (dec num-revisions)]
            (util/assert-no-errors (util/save-concept concept)))
        {:keys [concept-id revision-id variable-association]} (util/save-concept concept)]
    (if (= :variable concept-type)
      (assoc concept :concept-id concept-id :revision-id revision-id :variable-association variable-association)
      (assoc concept :concept-id concept-id :revision-id revision-id))))
