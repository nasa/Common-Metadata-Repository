class window.Ring extends Module

  constructor: (@points, options={}) ->
    super()
    @id = options.id
    # A clojure function to call on the server when the ring is dragged
    @callbackFn = options.callbackFn if options.callbackFn

    # Determine if the points self close
    # Remove last point if it does
    if @points[0].equals(@points[@points.length - 1])
      @points = @points[0..-2]
      @closed = true
    else
      @closed = false

    for point in @points
      point.addGuiEventListener(this)

  # Converts a string of points lon,lat,lon,lat to a ring
  @fromOrdinates: (ordinates, options={})->
    new Ring(Point.fromOrdinates(ordinates), options)

  toOrdinates: ->
    Point.toOrdinates(@points)

  # Returns a string of comma separate lon,lat,lon,lat...
  # for all the points in the ring.
  toOrdinatesString: (options={}) ->
    Point.toOrdinatesString(@points, options)

  gcPointsToRanges: (p1, p2)->
    p1x = p1.X()
    p2x = p2.X()
    westX = Math.min(p1x, p2x)
    eastX = Math.max(p1x, p2x)
    crossesAm = false
    if eastX - westX > 180
      crossesAm = true

    if crossesAm
      [{min: -180.0, max: westX}, {min: eastX, max: 180.0}]
    else
      [{min: westX, max: eastX}]

  limitWithinGCBounds: (fn, p1, p2)->
    (x)=>
      ranges = this.gcPointsToRanges(p1, p2)

      withinBounds = false
      for range in ranges
        withinBounds = withinBounds || (x >= range.min && x <= range.max)

      if withinBounds
        fn(x)

  createArc: (board, p1, p2)->
    gcFn = (x)->
              lon1_rad = Math.degreesToRadians(p1.X())
              lat1_rad = Math.degreesToRadians(p1.Y())
              lon2_rad = Math.degreesToRadians(p2.X())
              lat2_rad = Math.degreesToRadians(p2.Y())
              x_rad = Math.degreesToRadians(x)

              a = Math.sin(lat1_rad) * Math.cos(lat2_rad)
              b = Math.sin(lat2_rad) * Math.cos(lat1_rad)
              bottom = Math.cos(lat1_rad) * Math.cos(lat2_rad) * Math.sin(lon1_rad - lon2_rad)

              top = a * Math.sin(x_rad - lon2_rad) - b * Math.sin(x_rad - lon1_rad)
              Math.radiansToDegrees(Math.atan(top/bottom))
    gcFn = this.limitWithinGCBounds(gcFn, p1, p2)

    board.create('functiongraph', [gcFn])

  display: (board)->
    p.display(board) for p in @points
    unless @lines
      displayedPoints = _.map(@points, (p)-> p.displayedPoint)
      @lines = []
      _.eachCons(displayedPoints, 2, (pair)=>
        @lines.push(this.createArc(board, pair[0], pair[1])))
      if @closed
        @lines.push(this.createArc(board, displayedPoints[0], displayedPoints[displayedPoints.length - 1]))

  undisplay: (board)->
    p.undisplay(board) for p in @points
    if @lines
      board.removeObject(line) for line in @lines
      @lines = null


  handleGuiEvent: (event, ge) ->
    console.log("handling event")
    if event.type == Point.DRAG_FINISH_EVENT
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
