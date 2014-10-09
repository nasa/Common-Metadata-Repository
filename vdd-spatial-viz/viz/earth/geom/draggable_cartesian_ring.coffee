class window.DraggableCartesianRing extends CartesianRing
  @include GuiEventEmitter

  @MOVE_EVENT = "move_cartesian_polygon"
  @DRAG_FINISH_EVENT = "drag_finish_cartesian_polygon"

  @POINT_INSERTED_EVENT = "point_inserted"

  # Create a constant event so we don't have to create one every time
  @MOVE_EVENT_CACHED = new Event(@MOVE_EVENT)
  @DRAG_FINISH_EVENT_CACHED = new Event(@DRAG_FINISH_EVENT)
  @POINT_INSERTED_EVENT_CACHED = new Event(@POINT_INSERTED_EVENT)

  constructor: (points, options) ->
    super(points, options)
    # A clojure function to call on the server when the ring is dragged
    @callbackFn = options.callbackFn if options.callbackFn
    for point in @points
      point.addGuiEventListener(this)

  # Converts a string of points lon,lat,lon,lat to a ring
  @fromOrdinates: (ordinates, options={})->
    new DraggableCartesianRing(DraggablePoint.fromOrdinates(ordinates), options)

  handleGuiEvent: (event, ge) ->
    if event.type == DraggablePoint.MOVE_EVENT
      this.drawLineString()
      this.notifyGuiEventListeners(DraggableCartesianRing.MOVE_EVENT_CACHED, ge)
    else if event.type == DraggablePoint.DRAG_FINISH_EVENT
      this.notifyGuiEventListeners(DraggableCartesianRing.DRAG_FINISH_EVENT_CACHED, ge)
      if @callbackFn
        pointStr = this.toOrdinatesString()
        if @id && @id != null
          callbackStr = "#{@id}:#{pointStr}"
        else
          callbackStr = pointStr
        console.log("Calling callback #{@callbackFn} with #{callbackStr}")
        vdd_core.connection.callServerFunction(window.vddSession, @callbackFn, callbackStr)
    else
      console.log "Error: Unknown event to handle #{event.type}"

  handleDoubleClick: (event, ge) ->
    p = {lon: event.getLongitude(), lat:event.getLatitude()}

    findArc = =>return arc for arc in @arcs when arc.pointOnArc(p)
    found_arc = findArc()

    if found_arc
      console.log("Point #{p.lon},#{p.lat} intersects #{found_arc}")

      # Find the index where to insert the point
      index = -1
      curr_index = 0
      for point in @points
        if found_arc.p1.lon == point.lon && found_arc.p1.lat == point.lat
          index = curr_index+1
        curr_index += 1

      this.insertPoint(p.lon, p.lat, index,ge)
    event.preventDefault()

  insertPoint: (lon, lat, index, ge) ->
    name = (@points.length+1).toString()
    point = new DraggablePoint(lon, lat, {label: name})
    point.addGuiEventListener(this)
    this.addEventListener(point)
    point.display(ge)
    point.snapToGrid()
    @points.splice(index, 0, point)
    this.reinitializeArcs(ge)
    this.drawLineString()

    this.notifyGuiEventListeners(DraggableCartesianRing.POINT_INSERTED_EVENT_CACHED, ge)

