class window.DraggableBoundingRectangle extends BoundingRectangle
  @include GuiEventEmitter

  @MOVE_EVENT = "move_rectangle"
  @DRAG_FINISH_EVENT = "drag_finish_rectangle"

  # Create a constant event so we don't have to create one every time
  @MOVE_EVENT_CACHED = new Event(@MOVE_EVENT)
  @DRAG_FINISH_EVENT_CACHED = new Event(@DRAG_FINISH_EVENT)

  constructor: (west, north, east, south, options={}) ->
    super(west, north, east, south, options)
     # A clojure function to call on the server when the ring is dragged
    @callbackFn = options.callbackFn if options.callbackFn

    @northWest = new DraggablePoint(@west, @north)
    @northWest.addGuiEventListener(this)
    this.addEventListener(@northWest)

    @southWest = new DraggablePoint(@west, @south)
    @southWest.addGuiEventListener(this)
    this.addEventListener(@southWest)

    @northEast = new DraggablePoint(@east, @north)
    @northEast.addGuiEventListener(this)
    this.addEventListener(@northEast)

    @southEast = new DraggablePoint(@east, @south)
    @southEast.addGuiEventListener(this)
    this.addEventListener(@southEast)

  @fromObject: (data)->
    new DraggableBoundingRectangle(data.west, data.north, data.east, data.south)

  # Adds the ring to google earth
  display: (ge) ->
    super(ge)
    @northWest.display(ge)
    @southWest.display(ge)
    @northEast.display(ge)
    @southEast.display(ge)

  undisplay: (ge) ->
    super(ge)
    @northWest.undisplay(ge)
    @southWest.undisplay(ge)
    @northEast.undisplay(ge)
    @southEast.undisplay(ge)

  # Updates the coodinates of the west, north, east, and south from the points
  updateCoordinatesFromPoints: (ge)->

    if @northWest.lat != @north || @northWest.lon != @west
      @west = @northWest.lon
      @north = @northWest.lat
    else if @southWest.lat != @south || @southWest.lon != @west
      @west = @southWest.lon
      @south = @southWest.lat
    else if @northEast.lat != @north || @northEast.lon != @east
      @east = @northEast.lon
      @north = @northEast.lat
    else if @southEast.lat != @south || @southEast.lon != @east
      @east = @southEast.lon
      @south = @southEast.lat
    else
      console.log("Uknown point moved")

    if @north < @south
      # North and south have flipped
      temp = @north
      @north = @south
      @south = temp

      temp = @northWest
      @northWest = @southWest
      @southWest = temp

      temp = @northEast
      @northEast = @southEast
      @southEast = temp

    # Update locations of all the points
    @northWest.setLonAndLat(ge, @west, @north, false)
    @southWest.setLonAndLat(ge, @west, @south, false)
    @northEast.setLonAndLat(ge, @east, @north, false)
    @southEast.setLonAndLat(ge, @east, @south, false)


  # Takes an existing displayed box polygon and updates its coordinates where it's displayed
  setBoxBoundary: (box, west, east, north=@north, south=@south)->
    coords = box.getGeometry().getOuterBoundary().getCoordinates()
    coords.clear()
    coords.pushLatLngAlt(north, west, 0)
    coords.pushLatLngAlt(north, east, 0)
    coords.pushLatLngAlt(south, east, 0)
    coords.pushLatLngAlt(south, west, 0)

  handleGuiEvent: (event, ge) ->
    if event.type == DraggablePoint.MOVE_EVENT
      this.updateCoordinatesFromPoints(ge)
      if this.crossesAntimeridian()
        this.setBoxBoundary(@box1, @west, 180)

        if @box2 != null
          this.setBoxBoundary(@box2, -180, @east)
        else
          @box2 = this.createBoxPolygon(ge, -180, @east)
          ge.getFeatures().appendChild(@box2)

      else
        this.setBoxBoundary(@box1, @west, @east)

        if @box2 != null
          # We were crossing the antimeridian but now we're not.
          ge.getFeatures().removeChild(@box2)
          @box2 = null

      this.notifyGuiEventListeners(DraggableBoundingRectangle.MOVE_EVENT_CACHED, ge)
    else if event.type == DraggablePoint.DRAG_FINISH_EVENT
      this.notifyGuiEventListeners(DraggableBoundingRectangle.DRAG_FINISH_EVENT_CACHED, ge)
      if @callbackFn
        pointStr = "#{@west},#{@north},#{@east},#{@south}"
        if @id && @id != null
          callbackStr = "#{@id}:#{pointStr}"
        else
          callbackStr = pointStr
        console.log("Calling callback #{@callbackFn} with #{callbackStr}")
        vdd_core.connection.callServerFunction(window.vddSession, @callbackFn, callbackStr);
    else
      console.log "Error: Unknown event to handle #{event.type}"
