class window.Placemark extends Module
  @include GoogleEarthEventHandler

  URL_BASE = '/plugins/earth/images/map_markers/'

  @getUniquePlacemarkId: ->
    @nextId ||= 0
    @nextId += 1
    # We use a unique prefix to avoid conflicts with existing ids.
    "viz_#{@nextId}"

  constructor: (@lon, @lat, options={}) ->
    @lon = parseFloat(@lon)
    @lat = parseFloat(@lat)
    @placemark = null
    @label = options.label if options.label
    # This refers to the name of a image in the URL above
    @customIcon = options.customIcon if options.customIcon
    @balloonContents = options.balloonContents if options.balloonContents

  # updates the location of the point and notifies listeners
  setLonAndLat: (lon,lat) ->
    @lat = lat
    @lon = lon
    if @placemark
      @placemark.getGeometry().setLatitude @lat
      @placemark.getGeometry().setLongitude @lon

  display: (ge) ->
    @placemark = ge.createPlacemark(Placemark.getUniquePlacemarkId())
    @placemark.setName(Sp.fnValue(@label)) if @label

    if @customIcon
      icon = ge.createIcon('')
      icon.setHref(Sp.rootUrl() + URL_BASE + Sp.fnValue(@customIcon))
      style = ge.createStyle('')
      style.getIconStyle().setIcon(icon)
      # Correct the position of the icon
      hs = style.getIconStyle().getHotSpot()
      hs.setY(-0.01)
      hs.setYUnits(1) #google.earth.GEPlugin.UNITS_FRACTION
      @placemark.setStyleSelector(style)

    g_point = ge.createPoint("")
    g_point.setLongitude(@lon)
    g_point.setLatitude(@lat)
    @placemark.setGeometry(g_point)
    ge.getFeatures().appendChild(@placemark)

  handleClick: (event, ge) ->
    if @balloonContents && this.isEventTarget(event)
      event.preventDefault()
      balloon = ge.createHtmlStringBalloon('')
      balloon.setFeature(@placemark)
      balloon.setContentString(Sp.fnValue(@balloonContents))
      ge.setBalloon(balloon)

  undisplay: (ge) ->
    ge.getFeatures().removeChild(@placemark) if @placemark
    @placemark = null


  isEventTarget: (event) ->
    if event.getTarget().getGeometry
      target = event.getTarget()
      if @placemark && target.getId() == @placemark.getId()
        p_geom = @placemark.getGeometry()
        geom = event.getTarget().getGeometry()
        p_geom.getLongitude() == geom.getLongitude() && p_geom.getLatitude() == geom.getLatitude()




