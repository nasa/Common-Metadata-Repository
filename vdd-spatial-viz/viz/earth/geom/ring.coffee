class window.Ring extends Module
  @include GoogleEarthEventEmitter

  @DEFAULT_STYLE = {width: 5, color: Map.YELLOW}

  # Takes a list of points on the ring with lon and lat members. The points themselves
  # are not actually displayed
  # The ring determines whether it should close itself or not based on whether the first point
  # matches the last point
  constructor: (@points, options={}) ->
    super()
    @id = options.id
    @hidingPoints = false
    @hidingPoints = options.hidingPoints if options.hidingPoints

    @style = Ring.DEFAULT_STYLE
    @style = options.style if options.style

    # Determine if the points self close
    # Remove last point if it does
    if @points[0].equals(@points[@points.length - 1])
      @points = @points[0..-2]
      @closed = true
    else
      @closed = false

    this.addEventListener(p) for p in @points

  zoomablePoints: ()->
    this.getPoints()


  # Returns all the points in the ring in a new array.
  # Use this instead of the points attribute which won't have the repeated last point
  getPoints: ->
    if @closed
      @points.concat(@points[0])
    else
      # Create a copy of the points array
      @points.slice(0)

  # Converts a string of points lon,lat,lon,lat to a ring
  @fromOrdinates: (ordinates, options={})->
    new Ring(Point.fromOrdinates(ordinates), options)

  toOrdinates: ->
    Point.toOrdinates(this.getPoints())

  # Returns a string of comma separate lon,lat,lon,lat...
  # for all the points in the ring.
  toOrdinatesString: (options={}) ->
    Point.toOrdinatesString(this.getPoints(), options)

  # Adds the ring to google earth
  display: (ge) ->
    @lineStringPlacemark = ge.createPlacemark("")
    line_string = ge.createLineString("")
    line_string.setExtrude(true)
    line_string.setTessellate(true)
    line_string.setAltitudeMode(ge.ALTITUDE_CLAMP_TO_GROUND)
    @lineStringPlacemark.setGeometry(line_string)

    @lineStringPlacemark.setStyleSelector(ge.createStyle(''))
    lineStyle = @lineStringPlacemark.getStyleSelector().getLineStyle()
    lineStyle.setWidth(@style.width)
    lineStyle.getColor().set(@style.color)

    ge.getFeatures().appendChild(@lineStringPlacemark)
    this.drawLineString()

    unless @hidingPoints
      p.display(ge) for p in @points

  undisplay: (ge) ->
    p.undisplay(ge) for p in @points

    ge.getFeatures().removeChild(@lineStringPlacemark) if @lineStringPlacemark
    @lineStringPlacemark = null

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
    coordinates = @lineStringPlacemark.getGeometry().getCoordinates()
    coordinates.clear()

    for point in this.getPoints()
      coordinates.pushLatLngAlt(point.lat, point.lon, 0)

