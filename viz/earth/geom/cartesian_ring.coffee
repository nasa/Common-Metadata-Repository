class window.CartesianRing extends Module
  @include GoogleEarthEventEmitter

  @DEFAULT_STYLE = {width: 5, color: Map.YELLOW}

  # Takes a list of points on the polygon with lon and lat members. The points themselves
  # are not actually displayed
  constructor: (@points, options={}) ->
    super()
    @id = options.id
    @hidingPoints = false
    @hidingPoints = options.hidingPoints if options.hidingPoints

    @style = CartesianRing.DEFAULT_STYLE
    @style = options.style if options.style

    # Determine if the points self close
    # Remove last point if it does
    if @points[0].equals(@points[@points.length - 1])
      @points = @points[0..-2]

    this.addEventListener(p) for p in @points

  zoomablePoints: ()->
    this.getPoints()

  # Returns all the points in the polygon in a new array.
  # Use this instead of the points attribute which won't have the repeated last point
  getPoints: ->
    @points.concat(@points[0])

  # Converts a string of points lon,lat,lon,lat to a polygon
  @fromOrdinates: (ordinates, options={})->
    new CartesianRing(Point.fromOrdinates(ordinates), options)

  toOrdinates: ->
    Point.toOrdinates(this.getPoints())

  # Returns a string of comma separate lon,lat,lon,lat...
  # for all the points in the polygon.
  toOrdinatesString: (options={}) ->
    Point.toOrdinatesString(this.getPoints(), options)

  # Adds the polygon to google earth
  display: (ge) ->
    @polygonPlacemark = ge.createPlacemark("")
    polygon = ge.createPolygon("")
    polygon.setAltitudeMode(ge.ALTITUDE_CLAMP_TO_GROUND)
    @polygonPlacemark.setGeometry(polygon)

    outer = ge.createLinearRing("")
    outer.setAltitudeMode(ge.ALTITUDE_CLAMP_TO_GROUND)
    polygon.setOuterBoundary(outer)

    @polygonPlacemark.setStyleSelector(ge.createStyle(''))
    lineStyle = @polygonPlacemark.getStyleSelector().getLineStyle()
    lineStyle.setWidth(@style.width)
    lineStyle.getColor().set(@style.color)
    # Set polygon area to be transparent
    @polygonPlacemark.getStyleSelector().getPolyStyle().getColor().set("00ffffff")

    ge.getFeatures().appendChild(@polygonPlacemark)
    this.drawLineString()

    unless @hidingPoints
      p.display(ge) for p in @points

  undisplay: (ge) ->
    p.undisplay(ge) for p in @points

    ge.getFeatures().removeChild(@polygonPlacemark) if @polygonPlacemark
    @polygonPlacemark = null

  hidePoints: (ge, do_hide=true) ->
    if @hidingPoints != do_hide
      @hidingPoints = do_hide
      if @hidingPoints
        point.undisplay(ge) for point in @points
      else
        p.display(ge) for p in @points

  # Clears all the points in the line string and redraws them from the
  # points in the ring.
  drawLineString: ->
    coordinates = @polygonPlacemark.getGeometry().getOuterBoundary().getCoordinates()
    coordinates.clear()

    for point in this.getPoints()
      coordinates.pushLatLngAlt(point.lat, point.lon, 0)

