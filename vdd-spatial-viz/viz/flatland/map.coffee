
class window.Map extends Module

  # Creates the initial map state
  constructor: ()->
    @board = JXG.JSXGraph.initBoard('map', {boundingbox: [-180, 90, 180, -90], axis:true})
    @geometries = []

  resize: (width, height)->
    @board.needsFullUpdate = true
    @board.resizeContainer(width, height)
    @board.setBoundingBox([-180, 90, 180, -90], true)
    @board.update()


  addGeometries: (geometries)->
    newGeoms = _.map(geometries, (g)=>
      geom = switch g.type
                when "point"
                  new Point(g.lon, g.lat, g.options)
                when "cartesian-ring"
                  CartesianRing.fromOrdinates(g.ords, g.options)
                when "geodetic-ring"
                  Ring.fromOrdinates(g.ords, g.options)
                when "bounding-rectangle"
                  new BoundingRectangle(g.west, g.north, g.east, g.south, g.options)
                else throw "Unexpected geometry type: #{g.type}"
      geom.display(@board)
      geom
    )
    @geometries = @geometries.concat(newGeoms)

  # Removes geometries by id
  removeGeometries: (geometryIds)->
    [geometriesToRemove, @geometries] = _.partition(@geometries, (g)-> geometryIds.indexOf(g.id) >= 0)
    for g in geometriesToRemove
      g.undisplay(@board)


  clearGeometries: (geometries)->
    geom.undisplay(@board) for geom in @geometries
    @geometries = []


