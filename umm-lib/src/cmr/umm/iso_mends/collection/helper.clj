(ns cmr.umm.iso-mends.collection.helper
  "Contains functions used by ISO collection generation"
  (:require [clojure.data.xml :as x]))

(defn iso-string-element
  "Returns the iso element with gco:CharacterString that holds the given string value"
  [key value]
  (x/element key {}
             (x/element :gco:CharacterString {} value)))

(defn role-code-attributes
  "Returns the role code attributes for the given role type"
  [role]
  {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode"
   :codeListValue role})

(def scope-code-element
  (x/element
    :gmd:MD_ScopeCode
    {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
     :codeListValue "series"} "series"))