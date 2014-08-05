(ns cmr.system-int-test.data2.aql
  "Contains helper functions for converting parameters into aql string."
  (:require [clojure.string :as s]))

(defn- value->aql
  [provider-id params]
  (let [{:keys [ignore-case pattern]} params
        case-insensitive-aql (case ignore-case
                               true " caseInsensitive=\"Y\""
                               false " caseInsensitive=\"N\""
                               "")]
    (if pattern
      (format "<textPattern%s>%s</textPattern>" case-insensitive-aql provider-id)
      (format "<value%s>%s</value>" case-insensitive-aql provider-id))))

(defn- values->aql
  "Converts the given provider-ids into dataCenterId portion of the aql"
  [provider-ids params]
  (cond
    (nil? provider-ids) "<all/>"
    (and (sequential? provider-ids) (> (count provider-ids) 1))
    (if (= true (:pattern params))
      (str "<patternList>"
           (s/join "" (map #(value->aql % params) provider-ids))
           "</patternList>")
      (str "<list>"
           (s/join "" (map #(value->aql % params) provider-ids))
           "</list>"))
    :else (let [provider-id (if (sequential? provider-ids) (first provider-ids) provider-ids)]
            (value->aql provider-id params))))

(def key->elem-name
  "Mapping of parameter key to AQL element name"
  {:provider-id "dataCenterId"
   :short-name "shortName"
   :version-id "versionId"
   :entry-title "dataSetId"
   :processing-level-id "processingLevel"
   :echo-collection-id "ECHOCollectionID"
   :dif-entry-id "difEntryId"})

(defn- param->aql
  "Returns aql snippet for one parameter with its vaules"
  [params key values]
  (let [elem-name (key->elem-name key)]
    (format "<%s>%s</%s>" elem-name (values->aql values params) elem-name)))

(defn params->aql
  "Returns aql search string from input params which specifies provider-ids and aql-condition-string"
  [concept-type params]
  (let [{:keys [provider-ids where negated]} params
        aql-where-string (if (empty? where) ""
                           (s/join "" (map #(apply param->aql params %) where)))
        condition-elem-name (if (= :collection concept-type) "collectionCondition" "granuleCondition")
        condition-elem-start (if negated (str condition-elem-name " negated=\"Y\"") condition-elem-name)]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
         "<!DOCTYPE query SYSTEM \"https://api.echo.nasa.gov/echo/dtd/IIMSAQLQueryLanguage.dtd\">"
         (format "<query><for value=\"%ss\"/>" (name concept-type))
         (param->aql params :provider-id provider-ids)
         (format "<where><%s>%s</%s></where>" condition-elem-start aql-where-string condition-elem-name)
         "</query>")))

