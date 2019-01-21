(ns cmr.orbits.orbits-runtime
  "Defines a component which can be used to calculate Orbits"
  (:require
   [cmr.common.lifecycle :as l]
   [clojure.java.io :as io])
  (:import
   (javax.script ScriptEngine ScriptEngineManager Invocable)
   (java.io ByteArrayInputStream)
   (org.jruby.embed.jsr223 JRubyEngine)))

(def system-key
  "The key to use when storing the orbit runtime"
  :orbits)

(defn ^JRubyEngine create-jruby-runtime
  "Creates and initializes a JRuby runtime."
  []
  (let [jruby (.. (ScriptEngineManager.)
                  (getEngineByName "jruby"))]
    (.eval jruby "load 'orbits/echo_orbits_impl.rb'")
    jruby))

;; An wrapper component for the JRuby runtime
(defrecord OrbitsRuntime
  [^JRubyEngine jruby-runtime]
  l/Lifecycle

  (start
    [this _system]
    (assoc this :jruby-runtime (create-jruby-runtime)))
  (stop
    [this _system]
    (dissoc this :jruby-runtime)))

(defn create-orbits-runtime
  "Returns an instance of the Orbits runtime"
  []
  (->OrbitsRuntime nil))

(defn area-crossing-range
  "Given a set of coordinates representing a shape and satellite information, returns a range of
   longitudes.  Orbital passes which cross the equator within the returned ranges will cover the
   given coordinate with their swath.

   Parameters
   * lat-range - The latitude range from the MBR of the search area
   * geometry-type - the type of the geometry. One of :br, :line, :point, :polygon
   * coords - the coordinates of the geometry
   * ascending? - true to indicate ascending orbit
   * inclination - The inclination angle of the orbit in degrees
   * period - The number of minutes it takes to complete one orbit
   * swath-width - The width of the orbital track in kilometers
   * start-clat - The starting circular latitude in degrees
   * num-orbits - The number of orbits per granule of data (may be a fraction)"
  [orbits-runtime
   {:keys [lat-range geometry-type coords ascending? inclination period swath-width start-clat
           num-orbits]}]
  (let [args [(to-array lat-range)
              (name geometry-type)
              (to-array coords)
              ascending?
              inclination
              period
              swath-width
              start-clat
              num-orbits]
        jruby-runtime ^JRubyEngine (:jruby-runtime orbits-runtime)]
    (.invokeFunction jruby-runtime "areaCrossingRange" (to-array args))))

(defn denormalize-latitude-range
  "Returns an array containing all min, max ranges crossing the range from min to max for ascending
   and descending passes."
  [^OrbitsRuntime orbits-runtime min max]
  (let [args [min max]
        jruby-runtime ^JRubyEngine (:jruby-runtime orbits-runtime)]
    (.invokeFunction jruby-runtime "denormalizeLatitudeRange" (to-array args))))

;; Allows easily evaluating Ruby code in the Clojure REPL.
(comment
 (def jruby (create-jruby-runtime))

 (defn eval-jruby
   [s]
   (.eval jruby (java.io.StringReader. s)))

 (area-crossing-range
  (:orbits user/system)
  {:lat-range [-45 45]
   :geometry-type :br
   :coords [-45, 45, 45, -45]
   :ascending? true
   :inclination 98.15
   :period 98.88
   :swath-width 1450.0
   :start-clat -90.0
   :num-orbits 0.5})

 (denormalize-latitude-range
  (:orbits user/system)
  50 720)

 (do
   (eval-jruby "load 'spec/coordinate_spec.rb'")
   (eval-jruby "load 'spec/geometry_backtracking_spec.rb'"))

 (eval-jruby "require 'rspec/core'; RSpec::Core::Runner.run([])"))
