(ns cmr.umm.dif10.collection
  "Contains functions for parsing and generating the DIF dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.umm.dif.core :as dif-core]
            [cmr.umm.collection :as c]
            [cmr.common.xml :as v]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.dif.collection.project-element :as pj]
            [cmr.umm.dif.collection.related-url :as ru]
            [cmr.umm.dif.collection.science-keyword :as sk]
            [cmr.umm.dif.collection.org :as org]
            [cmr.umm.dif.collection.temporal :as t]
            [cmr.umm.dif.collection.product-specific-attribute :as psa]
            [cmr.umm.dif.collection.collection-association :as ca]
            [cmr.umm.dif.collection.platform :as platform]
            [cmr.umm.dif.collection.spatial-coverage :as sc]
            [cmr.umm.dif.collection.extended-metadata :as em]
            [cmr.umm.dif.collection.personnel :as personnel])
  (:import cmr.umm.collection.UmmCollection))

(defn validate-xml
  "Validates the XML against the DIF schema."
  [xml]
  (v/validate-xml (io/resource "schema/dif10/dif_v10.1.xsd") xml))