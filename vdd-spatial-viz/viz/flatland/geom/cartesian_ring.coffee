class window.CartesianRing extends Module

  @LINE_STYLE = {straightFirst:false, straightLast:false, strokeWidth:1}

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
    new CartesianRing(Point.fromOrdinates(ordinates), options)

  toOrdinates: ->
    Point.toOrdinates(@points)

  # Returns a string of comma separate lon,lat,lon,lat...
  # for all the points in the ring.
  toOrdinatesString: (options={}) ->
    Point.toOrdinatesString(@points, options)

  display: (board)->
    p.display(board) for p in @points
    unless @lines
      displayedPoints = _.map(@points, (p)-> p.displayedPoint)
      @lines = []
      _.eachCons(displayedPoints, 2, (pair)=>
        @lines.push(board.create('line', pair, CartesianRing.LINE_STYLE)))
      if @closed
        pair = [displayedPoints[0], displayedPoints[displayedPoints.length - 1]]
        @lines.push(board.create("line", pair, CartesianRing.LINE_STYLE))

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

