use_alert = false
google.load('earth', '1');

# Initializes google earth.
init = -> google.earth.createInstance('map', initCallback, failureCallback)

# Must be called to initialize google earth after everything loads
google.setOnLoadCallback(init)

initCallback = (ge) ->
  google.earth.addEventListener(ge.getWindow(), "mousedown", handleMouseDown)
  google.earth.addEventListener(ge.getGlobe(), "mousemove", handleMouseMove)
  google.earth.addEventListener(ge.getGlobe(), "mouseover", handleMouseOver)
  google.earth.addEventListener(ge.getGlobe(), "mouseup", handleMouseUp)
  google.earth.addEventListener(ge.getGlobe(), "dblclick", handleDoubleClick)
  google.earth.addEventListener(ge.getGlobe(), "click", handleClick)

  $("input[name='enable_grid']").change( (event) ->
      Map.map.enableGrid($(event.target).is(":checked")))
  $("input[name='enable_auto_zoom']").change( (event) ->
      Map.map.enableAutoZoom($(event.target).is(":checked")))
  $("input[name='dbl_clk_add_points']").change( (event) ->
      Map.map.enableAddPoints($(event.target).is(":checked")))
  $("#clear_button").click (event) ->
    Map.map.clearGeometries()

  Map.initSubCallback(ge)

# The failure callback
failureCallback = (errorCode) ->
  console.log("Error " + errorCode)
  alert("Error " + errorCode) if use_alert

handleMouseDown = (event) ->
  try
    Map.map.handleMouseDown(event, Map.map.ge)
  catch error
    failureCallback(error)

handleMouseUp = (event) ->
  try
    Map.map.handleMouseUp(event, Map.map.ge)
  catch error
    failureCallback(error)

handleMouseMove = (event) ->
  try
    Map.map.handleMouseMove(event, Map.map.ge)
  catch error
    failureCallback(error)

handleMouseOver = (event) ->
  try
    Map.map.handleMouseOver(event, Map.map.ge)
  catch error
    failureCallback(error)

handleDoubleClick = (event) ->
  try
    Map.map.handleDoubleClick(event, Map.map.ge)
  catch error
    failureCallback(error)

handleClick = (event) ->
  try
    Map.map.handleClick(event, Map.map.ge)
  catch error
    failureCallback(error)

class window.Map extends Module
  @include GoogleEarthEventEmitter
  # @include GuiEventEmitter

  # Colors are alpha, blue, green, red
  @YELLOW = '9900ffff'
  @ORANGE = '9918A0ff'
  @BLUE = '99D37A47'

  constructor: (@ge)->
    @ge.getNavigationControl().setVisibility(ge.VISIBILITY_SHOW)
    @ge.getOptions().setStatusBarVisibility(true)
    @ge.getOptions().setGridVisibility(true)
    @ge.getWindow().setVisibility(true)
    @gex = new GEarthExtensions(@ge)
    @autoZoomEnabled = true
    @dblClickAddPointsEnabled = false
    @currentGeometries = []

  clearGeometries: ()->
    for g in @currentGeometries
      g.undisplay(@ge)
      this.removeEventListener(g)
    @currentGeometries = []

  setGeometries: (geometries)->
    this.clearGeometries()
    this.addGeometries(geometries)

  addGeometries: (geometries)->
    newGeoms = _.map(geometries, (g)=>
      geom = switch g.type
                when "point"
                  if g.options && g.options.draggable
                    new DraggablePoint(g.lon, g.lat, g.options)
                  else
                    new Point(g.lon, g.lat, g.options)
                when "geodetic-ring"
                  if g.options && g.options.draggable
                    DraggableRing.fromOrdinates(g.ords, g.options, window.vddSession)
                  else
                    Ring.fromOrdinates(g.ords, g.options)
                when "cartesian-ring"
                  if g.options && g.options.draggable
                    DraggableCartesianRing.fromOrdinates(g.ords, g.options, window.vddSession)
                  else
                    CartesianRing.fromOrdinates(g.ords, g.options)
                when "bounding-rectangle"
                  if g.options && g.options.draggable
                    new DraggableBoundingRectangle(g.west, g.north, g.east, g.south, g.options)
                  else
                    new BoundingRectangle(g.west, g.north, g.east, g.south, g.options)
                else throw "Unexpected geometry type: #{g.type}"
      this.addEventListener(geom)
      geom.display(@ge)
      geom
    )
    @currentGeometries = @currentGeometries.concat(newGeoms)
    this.zoomToPoints(_.reduce(newGeoms, ((m,g)->
      m.concat(g.zoomablePoints())), []))

  # Removes geometries by id
  removeGeometries: (geometryIds)->
    [geometriesToRemove, @currentGeometries] = _.partition(@currentGeometries, (g)-> geometryIds.indexOf(g.id) >= 0)
    for g in geometriesToRemove
      g.undisplay(@ge)
      this.removeEventListener(g)

  enableGrid: (enabled=true)->
    @ge.getOptions().setGridVisibility(enabled)

  enableAutoZoom: (enabled=true)->
    @autoZoomEnabled = enabled

  enableAddPoints: (enabled=true)->
    @dblClickAddPointsEnabled = enabled

  zoomToPoints: (points)->
    return false unless @autoZoomEnabled
    bounds = new geo.Bounds()

    for point in points
      bounds.extend(new geo.Point(point.lat, point.lon, 0))
    map_element = $('#map')
    aspect = map_element.width() / map_element.height()
    @gex.view.setToBoundsView(bounds, {aspectRatio: aspect, scaleRange:2})

  handleDoubleClick: (event, ge) ->
    if @dblClickAddPointsEnabled
      lon = event.getLongitude()
      lat = event.getLatitude()
      lon = Math.roundTo(lon, 3)
      lat = Math.roundTo(lat, 3)
      p = new Point(lon, lat, label:"#{lon},#{lat}")
      this.addEventListener(p)
      p.display(@ge)
      @currentGeometries.push(p)
      event.preventDefault()


  # Moves the camera to view the specified lon and lat
  moveCamera: (lon, lat) ->
    lookAt = @ge.getView().copyAsLookAt(@ge.ALTITUDE_RELATIVE_TO_GROUND)
    lookAt.setLongitude(lon)
    lookAt.setLatitude(lat)
    @ge.getView().setAbstractView(lookAt)


Map.initSubCallback = (ge) =>
  Map.map = new Map(ge)
