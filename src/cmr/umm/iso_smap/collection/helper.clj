(ns cmr.umm.iso-smap.collection.helper
  "Contains functions used by SMAP ISO collection generation"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]))

(defn xml-elem-with-path-value
  "Returns the identification element with the given path and value"
  [id-elems path value]
  (first (filter #(= value (cx/string-at-path % path)) id-elems)))

(def scope-code-element
  (x/element
    :gmd:MD_ScopeCode
    {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
     :codeListValue "series"} "series"))

(defn iso-string-element
  "Returns the iso element with gco:CharacterString that holds the given string value"
  [key value]
  (x/element key {}
             (x/element :gco:CharacterString {} value)))

(defn generate-id
  "Returns a 5 character random id to use as an ISO id"
  []
  (str "d" (last (gen/sample (ext-gen/string-alpha-numeric 4 4)))))

(defn role-code-attributes
  "Returns the role code attributes for the given role type"
  [role]
  {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_RoleCode"
   :codeListValue role})
