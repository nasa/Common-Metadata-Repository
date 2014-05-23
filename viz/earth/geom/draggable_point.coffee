class window.DraggablePoint extends Point
  @include GuiEventEmitter

  @MOVE_EVENT = "move_point"
  @DRAG_FINISH_EVENT = "drag_finish_point"

  # Create a constant event so we don't have to create one every time
  @MOVE_EVENT_CACHED = new Event(@MOVE_EVENT)
  @DRAG_FINISH_EVENT_CACHED = new Event(@DRAG_FINISH_EVENT)

  constructor: (lon, lat, options={}) ->
    super(lon, lat, options)
    @dragging = false

  @fromOrdinates: (ordinates)->
    Point.fromOrdinates(ordinates, DraggablePoint)

  # updates the location of the point and notifies listeners
  setLonAndLat: (lon,lat) ->
    @lat = lat
    @lon = lon
    this.placemark.setLonAndLat(lon,lat)
    this.notifyGuiEventListeners(DraggablePoint.MOVE_EVENT_CACHED)

  # Makes the point move to a position rounded on lat and lon
  # of numDigits
  snapToGrid: (numDigits=2) ->
    power = Math.pow(10, numDigits)
    lon = Math.round(@lon*power)/power
    lat = Math.round(@lat*power)/power
    this.setLonAndLat(lon,lat)

  # Handles mouse events
  handleMouseDown: (event, ge) ->
    if @placemark.isEventTarget(event)
      @dragging = true
      @moved = false

  handleMouseMove: (event, ge) ->
    if @dragging
      @moved = true
      this.setLonAndLat event.getLongitude(), event.getLatitude()
      event.preventDefault()

  handleMouseUp: (event, ge) ->
    if @dragging
      @dragging = false
      if @moved
        this.snapToGrid()
        this.notifyGuiEventListeners(DraggablePoint.DRAG_FINISH_EVENT_CACHED)

  handleClick: (event, ge) ->
    super(event, ge) unless @moved
    @moved = false

