(ns cmr.umm.iso-mends.collection.collection-association
  "Contains functions for parsing and generating the ISO MENDS collection associations"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-mends.collection.helper :as h]))

(defn xml-elem->CollectionAssociation
  [ca-elem]
  (let [short-name (cx/string-at-path ca-elem [:CI_Citation :title :CharacterString])
        version-id (cx/string-at-path ca-elem [:CI_Citation :edition :CharacterString])]
    (c/map->CollectionAssociation {:short-name short-name
                                   :version-id version-id})))

(defn xml-elem->CollectionAssociations
  [id-elem]
  (seq (map xml-elem->CollectionAssociation
            (cx/elements-at-path
              id-elem
              [:aggregationInfo :MD_AggregateInformation :aggregateDataSetName]))))

(defn generate-collection-associations
  [cas]
  (for [ca cas]
    (x/element
      :gmd:aggregationInfo {}
      (x/element
        :gmd:MD_AggregateInformation {}
        (x/element
          :gmd:aggregateDataSetName {}
          (x/element :gmd:CI_Citation {}
                     (h/iso-string-element :gmd:title (:short-name ca))
                     (x/element :gmd:date {:gco:nilReason "unknown"})
                     (h/iso-string-element :gmd:edition (:version-id ca))
                     (h/iso-string-element :gmd:otherCitationDetails "Extra data")))
        (x/element
          :gmd:aggregateDataSetIdentifier {}
          (x/element :gmd:MD_Identifier {}
                     (h/iso-string-element :gmd:code (:short-name ca))))
        (x/element
          :gmd:associationType {}
          (x/element
            :gmd:DS_AssociationTypeCode
            {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"
             :codeListValue "Input Collection"}
            "Input Collection"))))))
