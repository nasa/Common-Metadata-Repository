
class window.Map extends Module

  # Creates the initial map state
  constructor: ()->
    @board = JXG.JSXGraph.initBoard('map', {boundingbox: [-180, 90, 180, -90], axis:true})
    @geometries = []

  addGeometries: (geometries)->
    newGeoms = _.map(geometries, (g)=>
      geom = switch g.type
                when "point"
                  new Point(g.lon, g.lat, g.options)
                when "cartesian-ring"
                  CartesianRing.fromOrdinates(g.ords, g.options)
                else throw "Unexpected geometry type: #{g.type}"
      geom.display(@board)
      geom
    )
    @geometries = @geometries.concat(newGeoms)


