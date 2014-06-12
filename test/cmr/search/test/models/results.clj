(ns cmr.search.test.models.results
  "Contains clojure.test.check generators for search results."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.search.models.results :as r]
            [clojure.string :as s]))

(def provider-ids
  (gen/elements ["PROV1" "PROV2" "PROV3" "PROV4" "PROV5"]))

(def concept-type->prefix
  {:collection "C"})

(def concept-types
  (gen/elements [:collection]))

(def concept-type-prefixes
  (gen/fmap concept-type->prefix concept-types))

(def concept-ids
  (gen/fmap (partial apply str)
            (gen/tuple concept-type-prefixes gen/s-pos-int (gen/return "-") provider-ids)))

(def revision-ids
  gen/pos-int)

(def native-ids
  (gen/such-that (fn [id]
                   (> (count (s/trim id)) 0))
                 gen/string-ascii))

(def references
  (gen/fmap (fn [[concept-type-prefix concept-num provider-id revision-id native-id]]
              (let [concept-id (str concept-type-prefix concept-num "-" provider-id)
                    location (str "http://localhost:3003/" concept-id)]
                (r/->Reference concept-id revision-id location native-id)))
            (gen/tuple concept-type-prefixes gen/s-pos-int provider-ids revision-ids native-ids)))

(def results
  (gen/fmap (fn [refs]
              (r/->Results (count refs) refs))
              (gen/vector references 1 20)))
