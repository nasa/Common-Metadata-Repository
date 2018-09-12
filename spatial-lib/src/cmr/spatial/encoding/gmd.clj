(ns cmr.spatial.encoding.gmd
  "Functions for encoding and decoding between spatial geometries and
  gmd:geographicElement elements.

  GMD is the Geographic MetaData schema for describing geographic
  information in ISO XML documents.

  see: http://www.isotc211.org/schemas/2005/gmd/"
  (:require
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [cmr.common.xml :as cx]
   [cmr.common.util :as util]
   [cmr.common.validations.core :as v]
   [cmr.spatial.validation :as sv]
   [cmr.spatial.encoding.gml :as gml]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.orbit :as orbit]))

(declare decode-geo-content)

;; Interface

(defmulti encode
  "Returns a gmd:geographicElement xml element from a spatial geometry."
  type)

(defn decode
  "Returns spatial geometry parsed from a gmd:geographicElement xml element."
  [gmd-geo-element]
  (-> gmd-geo-element :content first decode-geo-content))

;; Implementations

(defmulti decode-geo-content
  "Decode the content of a gmd:geographicElement"
  :tag)

(defmethod decode-geo-content :default
  [_]
  nil)

(defmethod encode cmr.spatial.mbr.Mbr
  [geometry]
  (let [{:keys [west north east south]} geometry
        gen-point-fn (fn [tag content]
                       (x/element tag {}
                                  (x/element :gco:Decimal {} content)))]
    (x/element :gmd:geographicElement {}
               (x/element :gmd:EX_GeographicBoundingBox {:id (str "geo-" (java.util.UUID/randomUUID))}
                          (x/element :gmd:extentTypeCode {}
                                     (x/element :gco:Boolean {} 1))
                          (gen-point-fn :gmd:westBoundLongitude west)
                          (gen-point-fn :gmd:eastBoundLongitude east)
                          (gen-point-fn :gmd:southBoundLatitude south)
                          (gen-point-fn :gmd:northBoundLatitude north)))))

(defmethod decode-geo-content :EX_GeographicBoundingBox
  [bounding-box-elem]
  (let [parse #(cx/double-at-path bounding-box-elem [% :Decimal])]
    (apply mbr/mbr (map parse [:westBoundLongitude :northBoundLatitude
                               :eastBoundLongitude :southBoundLatitude]))))

(defmethod encode cmr.spatial.orbit.Orbit
  [orbit]
  (let [{:keys [ascending-crossing start-lat start-direction end-lat end-direction]} orbit]
    (x/element :gmd:geographicElement {}
               (x/element :gmd:EX_GeographicDescription {}
                          (x/element :gmd:MD_Identifier {}
                                     (x/element :gmd:code {}
                                                (x/element :gco:CharacterString {} (orbit/build-orbit-string orbit)))
                                     (x/element :gmd:codeSpace {}
                                                (x/element :gco:CharacterString {} "gov.nasa.esdis.umm.orbitparameters"))
                                     (x/element :gmd:description {}
                                                (x/element :gco:CharacterString {} "OrbitParameters")))))))

;; GMD treats everything else (points, lines, polygons) as polygons

(defmethod encode :default
  [polygon]
  (x/element
   :gmd:geographicElement {}
   (x/element
    :gmd:EX_BoundingPolygon {}
    (x/element :gmd:extentTypeCode {}
               (x/element :gco:Boolean {} 1))
    (x/element :gmd:polygon {}
               (gml/encode polygon)))))

(defmethod decode-geo-content :EX_BoundingPolygon
  [bounding-polygon-element]
  ;; EXBoundingPolygon elements contain a gmd:polygon which contains a
  ;; single gml geometry element
  (let [polygons (-> bounding-polygon-element (cx/elements-at-path [:polygon]))]
    (map #(-> % :content first gml/decode) polygons)))


(defmethod decode-geo-content :EX_GeographicDescription
  [geo-desc]
  (let [orbit-str (cx/string-at-path geo-desc [:geographicIdentifier :MD_Identifier :code :CharacterString])
        ascending-crossing (util/get-index-or-nil orbit-str "AscendingCrossing:")
        start-lat (util/get-index-or-nil orbit-str "StartLatitude:")
        start-direction (util/get-index-or-nil orbit-str "StartDirection:")
        end-lat (util/get-index-or-nil orbit-str "EndLatitude:")
        end-direction (util/get-index-or-nil orbit-str "EndDirection:")]
    (orbit/->Orbit
     (when ascending-crossing
       (let [asc-c (subs orbit-str
                         ascending-crossing
                         (or start-lat start-direction end-lat end-direction
                             (count orbit-str)))]
         (Float. (str/trim (subs asc-c (inc (.indexOf asc-c ":")))))))
     (when start-lat
       (let [sl (subs orbit-str
                      start-lat
                      (or start-direction end-lat end-direction
                          (count orbit-str)))]
         (Float. (str/trim (subs sl (inc (.indexOf sl ":")))))))
     (when start-direction
       (let [sd (subs orbit-str
                      start-direction
                      (or end-lat end-direction
                          (count orbit-str)))]
         (str/trim (subs sd (inc (.indexOf sd ":"))))))
     (when end-lat
       (let [el (subs orbit-str
                      end-lat
                      (or end-direction
                          (count orbit-str)))]
         (Float. (str/trim (subs el (inc (.indexOf el ":")))))))
     (when end-direction
       (let [ed (subs orbit-str
                      end-direction)]
         (str/trim (subs ed (inc (.indexOf ed ":")))))))))
