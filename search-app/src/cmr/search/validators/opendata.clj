(ns cmr.search.validators.opendata
 "Validate Opendata formatted collections against JSON schema in /resources/schema"
 (:require
  [clojure.java.io :as io]
  [cmr.common.validations.json-schema :as json-schema]))

(defn- load-opendata-schema
  "Load and parse named opendata schema from resources"
  [schema-name]
  (json-schema/parse-json-schema-from-path
   (str "schema/opendata/" schema-name ".json")))

(def opendata-schemas
  {:catalog (load-opendata-schema "catalog")
   :dataset (load-opendata-schema "dataset")})

(defn validate-dataset
  "Validate a given opendata record"
  [dataset]
  ;; A catalog refers to a collection of datasets, which is what we're dealing with
  (json-schema/validate-json (:catalog opendata-schemas) dataset))
