(ns cmr.system-int-test.search.ge-helper
  "Helpers for spatial visualization"
  (:require [clojure.string :as str]
            [clojure.java.shell :as shell]
            [cmr.search.results-handlers.kml-results-handler :as kml-rh]
            [cmr.spatial.mbr :as m]
            [clojure.data.xml :as x]
            ))

(comment

  (shapes->kml [m/whole-world])

  )

(def kml-cartesian-style-xml-elem
  "A clojure data.xml element representing the XML element containing the cartesian style"
  (x/element :Style {:id "cartesian_style"}
             (x/element :LineStyle {}
                        (x/element :color {} "ffffffff")
                        (x/element :colorMode {} "random")
                        (x/element :width {} "2"))
             (x/element :IconStyle {}
                        (x/element :color {} "ffffffff")
                        (x/element :colorMode {} "random")
                        (x/element :scale {} "3.0"))
             (x/element :PolyStyle {}
                        (x/element :color {} "ffffffff"))))

(defn shapes->kml
  [shapes]
  (x/indent-str
    (x/element :kml kml-rh/KML_XML_NAMESPACE_ATTRIBUTES
               (x/element :Document {}
                          kml-rh/kml-geodetic-style-xml-elem
                          kml-cartesian-style-xml-elem
                          (for [shape shapes]
                            (x/element :Placemark {}
                                       (x/element :name {} "none")
                                       (x/element :styleUrl {}
                                                  (kml-rh/coordinate-system->style-url :cartesian))
                                       (kml-rh/shape->xml-element shape)))))))

(defn display-shapes
  [results-name shapes]
  (spit results-name (shapes->kml shapes))
  (shell/sh "open" results-name))