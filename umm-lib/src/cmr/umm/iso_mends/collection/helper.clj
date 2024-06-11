(ns cmr.umm.iso-mends.collection.helper
  "Contains functions used by ISO collection generation"
  (:require [clojure.data.xml :as xml]))

(defn iso-string-element
  "Returns the iso element with gco:CharacterString that holds the given string value"
  [key value]
  (xml/element key {}
             (xml/element :gco:CharacterString {} value)))

(defn role-code-attributes
  "Returns the role code attributes for the given role type"
  [role]
  {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode"
   :codeListValue role})

(def scope-code-element
  (xml/element
    :gmd:MD_ScopeCode
    {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
     :codeListValue "series"} "series"))