 class window.Point extends Module
  @include GoogleEarthEventEmitter

  constructor: (@lon, @lat, options={}) ->
    super()
    @lon = parseFloat(@lon)
    @lat = parseFloat(@lat)
    @id = options.id

    # Set it to a function so it's always correct for the current location
    # of the point
    unless options.balloonContents
      options.balloonContents = => "Exact #{@lon},#{@lat}"
    @placemark = new Placemark(@lon, @lat, options)
    this.addEventListener(@placemark)

  @fromOrdinates: (ordinates, type=Point)->
    points = []
    _.eachSlice(ordinates, 2, (slice, i) ->
      options =
        label: (i+1).toString()
      points.push(new type(slice[0], slice[1], options))
    )
    points

  @stringToOrdinates: (ordinates_string) ->
    _.map(ordinates_string.split(/\s*,\s*/), (o) -> parseFloat(o))

  @toOrdinates: (points) ->
    _.reduce(points, (memo, p) ->
      memo.push(p.lon)
      memo.push(p.lat)
      memo
    ,[])

  # Returns a string of comma separate lon,lat,lon,lat...
  # for all the points in the ring.
  @toOrdinatesString: (points, options={})->
    if options.includeSpace
      join_str = ", "
    else
      join_str = ","

    ordinate_pairs = _.reduce(points, ((a,p)->
      a.push "#{p.lon},#{p.lat}"
      a
      a), [])
    ordinate_pairs.join(join_str)

  zoomablePoints: ()->
    [this]

  # Adds the point to google earth
  display: (ge) ->
    @placemark.display(ge)

  undisplay: (ge) ->
    @placemark.undisplay(ge)

  equals: (p) ->
    @lon == p.lon and @lat == p.lat

