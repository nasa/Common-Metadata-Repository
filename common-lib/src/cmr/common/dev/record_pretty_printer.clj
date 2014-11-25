(ns cmr.common.dev.record-pretty-printer
  "Changes the way clojure records are pretty printed. Normally records that are pretty printed
  do not include type information. This pretty prints the records as a map with a type field
  that contains the object class type. It also prints out the fields in the order they are defined
  in the record.

  This pretty printing capability is not enabled by default. It must be enabled for a type by calling
  the enable-record-pretty-printing macro with the record type. This would typically be done directly
  in the namespace after defining the record."
  (:require [cmr.common.util :as util]
            [clojure.pprint :as pprint]))

(defn mapify-record
  "Converts a record into a map with a :_type key containing a simple name of the key. The map keys
  are ordered to match the order defined in the record"
  ([r]
   (let [type-name (symbol (.getSimpleName ^Class (type r)))]
     (util/remove-nil-keys (into {} (assoc r :_type type-name)))))
  ([fields r]
   (let [type-name (symbol (.getSimpleName ^Class (type r)))
         ;; We include custom fields on r in the list of all fields that should be ordered.
         ;; :_type should be the first key that is printed. Custom keys that aren't part of
         ;; the defined record fields go at the end.
         all-fields (distinct (concat (cons :_type fields) (keys r)))
         key-order-map (into {} (map-indexed #(vector %2 %1) all-fields))
         sorted-by-key-order (sorted-map-by #(compare (key-order-map %1) (key-order-map %2)))]
     (util/remove-nil-keys (into sorted-by-key-order (assoc r :_type type-name))))))

(defmacro enable-record-pretty-printing
  "Enables record pretty printing for the passed in record type."
  [& record-types]
  (cons
    'do
    (for [record-type record-types]
      ;; Register the record type as getting pretty printing through the functions here.
      `(.addMethod ^clojure.lang.MultiFn pprint/simple-dispatch
                   ~record-type
                   (fn [q#]
                     (pprint/simple-dispatch
                       (mapify-record (util/record-fields ~record-type) q#)))))))
