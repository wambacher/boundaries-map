<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<meta http-equiv="Pragma" content="no-cache"> 
<title>OSM boundaries 4.1i</title>
<base target="_top" />
 
<!-- V 1.6    abgeleitet von residentials Live 
     V 1.6.1  umgestellt auf jstree
     V 1.6.5  deselect all children
     V 1.6.6  bpopup reaktiviert, poly aktiviert
     V 1.6.7i Umstellung auf IFrame, Bing komplet raus!!!
     V 1.6.8i Kleinere Spielereien mit ArgParser und Permalink
     V 1.4.0  boundaries selber rendern
     V 1.5.0  Permalink 2. Anlauf / Places Layer
     V 1.6    Layers besser initalisiert
     V 2.0    Data from collected_admin_boundaries only
     V 2.1    Simplify polygons
     V 2.2    Slider for Buffer
     V 2.3    Union to MP
     V 2.4    DataBase Timestamp
     V 2.5    File based logging
     V 2.6    Export full country
     V 2.7    Anpassungen Footer
     V 3.0    OAuth2
     V 3.1    fixed Download
     V 3.2    tuned select/deselect children
     V 3.3    Anpassungen für Flat Export
     V 3.4    getBixBox auch nur wenn eingeloggt
     V 3.5    Export single/levels/flat
     V 3.6    Better errors more then 660 boundaries
     V 3.7    Select next level
     V 3.8    Ways aus Boundaries und nicht mehr aus CAB
              Export land or water area
     V 4.0    Umstellung auf POST
     V 4.1    Umstellungen für API
              Permalink Control ausgeschaltet
-->

<link rel="icon"       href="images/favicon.gif" type="image/gif">
<link rel="StyleSheet" href="css/bmap41.css"     type="text/css"/> 
<link rel="StyleSheet" href="css/ui-layout.css"  type="text/css"/> 

<!--[if IE 6]>
   <link href="https://wambachers-osm.website/common/css/ie6.css" rel="stylesheet" type="text/css" />
<![endif]-->

<script>
   var myBase       = "boundaries";
   var myVersion    = "4";
   var mySubversion = "1";   
   var myName       = myBase+"-"+myVersion+"."+mySubversion;
   var outerFrame   = "idx"+myVersion+mySubversion+"o.jsp";
   var innerFrame   = "idx"+myVersion+mySubversion+"i.jsp";
   var database     = "planet3";
   var loading      = 0;
   var selected     = ""; // für permalink
   var exportAreas  = "dummy";
   var sep          = ",";
   var host         = window.location.hostname;

   if (typeof console === "undefined" || typeof console.log === "undefined") {
     console = {};
     console.log = function() {};
   }

</script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/openlayers/OpenLayers-2.13/OpenLayers.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/openlayers/deprecated.js"></script>

<script type="text/javascript" src="https://wambachers-osm.website/common/js/layers/global.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/layers/grid.js"></script>

<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery-1.10.4.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery-ui-1.10.4.custom.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.ui-contextmenu.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.layout-latest.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.bpopup.min.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.cookie.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/purl.js"></script>

<script type="text/javascript" src="https://wambachers-osm.website/common/js/functions/global_functions.js"></script>

<script> 
   var map;
   var PermalinkControl;

// avoid pink tiles
   OpenLayers.IMAGE_RELOAD_ATTEMPTS = 3;
   OpenLayers.Util.onImageLoadErrorColor = "transparent";

$(document).ready(function() { 
   console.log("init document.ready");
   console.log("######################### Map Starting #####################"); 

   var myParent = window.parent;
   console.log("i'm inside of a frame and my parent is", myParent.src);
   var iframe = window.frameElement;
   console.log("i'm inside of a frame and my source is", iframe.src);
   var ParentDoc = window.parent.document;

   console.log("Using OpenLayers "+OpenLayers.VERSION_NUMBER);

   $("<style type='text/css'> .olControlLayerSwitcher { opacity:0.7;filter:alpha(opacity=80);}</style>").appendTo("head");

// OpenLayers.ImgPath = "js/openlayers/img/";

   var projfrom = new OpenLayers.Projection("EPSG:4326");
   var projto   = new OpenLayers.Projection("EPSG:3857");
   
   var options_proj = {
    'internalProjection': projto,
    'externalProjection': projfrom
   };

   var geojson_format = new OpenLayers.Format.GeoJSON(options_proj);

   var le=5.6; var ue=45.7; var re=17.2; var oe=55;    // dach
   var extent = new OpenLayers.Bounds(le,ue,re,oe).transform(projfrom,projto);
   
   var latitude   = 50.1115; var longitude =  8.098; // wambach 
   var mapZoom    = 16; 
   var searchZoom = 14; // minimum zoom für poi-suche

   var myJSONText; // nur zum debuggen !!

      map = new OpenLayers.Map("map_div" ,{
         fractionalZoom: false,
    	 controls: [
    	      new OpenLayers.Control.KeyboardDefaults(),
    	      new OpenLayers.Control.Navigation(),
    	      new OpenLayers.Control.Zoom(),
    	      new OpenLayers.Control.ZoomBox(),
    	      new OpenLayers.Control.ScaleLine(),
    	      new OpenLayers.Control.MousePosition({
    	         "numDigits":		4,
	         prefix:                "lonlat=",
    	         div:			ParentDoc.getElementById('mouse_div'),
    	         displayProjection:	new OpenLayers.Projection(projfrom)} ),
              new OpenLayers.Control.Attribution()
            ]} 
      );
      
      // ***************** Base Layers ****************************** */
    
      var mapnik = new OpenLayers.Layer.OSM('OSM Mapnik',
                       ['https://a.tile.openstreetmap.org/${z}/${x}/${y}.png',
                        'https://b.tile.openstreetmap.org/${z}/${x}/${y}.png',
                        'https://c.tile.openstreetmap.org/${z}/${x}/${y}.png'],
                     { resolutions:		resolutions,
                       serverResolutions:	serverResolutions,
                       attribution: 		'Tiles &copy; <a href="https://www.openstreetmap.org/" target="_blank">OpenStreetMap</a> contributors',
                       displayInLayerSwitcher:  true,
                       isBaseLayer:		true,
		               visibility:		true,
                       opacity:                 1,     
                       numZoomLevels:		19,   	       
//           	       transitionEffect:	'resize',
                       permalink:		'mapnik'
                     });

      var mono = new OpenLayers.Layer.OSM('OSM Mapnik Black & White', 
                       ['https://a.tile.openstreetmap.org/${z}/${x}/${y}.png',
                        'https://b.tile.openstreetmap.org/${z}/${x}/${y}.png',
                        'https://c.tile.openstreetmap.org/${z}/${x}/${y}.png'],
                     { resolutions:		resolutions,
                       serverResolutions:	serverResolutions,
//                     displayOutsideMaxExtent: true, 
                       attribution: 		'Tiles &copy; <a href="https://www.openstreetmap.org/" target="_blank">OpenStreetMap</a> contributors', 
                       displayInLayerSwitcher:  true,
                       displayProjection:       projfrom,
                       isBaseLayer:		true,
		       visibility:		false,
                       opacity:                 1, 
                       numZoomLevels:		19, 
                       permalink: 		"mono", 
                       eventListeners: {
                       tileloaded: function(evt) {
                        var ctx = evt.tile.getCanvasContext();
                        if (ctx) {
                            var imgd = ctx.getImageData(0, 0, evt.tile.size.w, evt.tile.size.h);
                            var pix = imgd.data;
                            for (var i = 0, n = pix.length; i < n; i += 4) {
                                pix[i] = pix[i + 1] = pix[i + 2] = (3 * pix[i] + 4 * pix[i + 1] + pix[i + 2]) / 8;
                            }
                            ctx.putImageData(imgd, 0, 0);
                            evt.tile.imgDiv.removeAttribute("crossorigin");
                            evt.tile.imgDiv.src = ctx.canvas.toDataURL();
                        }
                    }
                }
            });

      var polmap = new OpenLayers.Layer.OSM('Political Map',
                       'https://'+host+':4483/${z}/${x}/${y}.png',
                     { resolutions:		resolutions,
                       serverResolutions:	serverResolutions,
                       attribution: 		'Tiles &copy; <a href="https://www.openstreetmap.org/" target="_blank">OpenStreetMap</a> contributors',
                       displayInLayerSwitcher:  true,
                       isBaseLayer:		true,
		       visibility:		true,
                       opacity:                 1,     
                       numZoomLevels:		19,   	       
//           	       transitionEffect:	'resize',
                       permalink:		'polmap'
                     });

//    map.addLayer(bingsat);


      // ***************** layer admborders *******************************/

      // registerLoader(admborders);

      // ***************** layer admlabels ****************************** */

      // registerLoader(admlabels);

      // ************************** layer boundaries ********************************* */

      console.log("init boundaries");

      var boundariesStyleMap = new OpenLayers.StyleMap({
	 'default':	new OpenLayers.Style({
                       fillColor:	        '${color}',
                       fillOpacity:		'0.3', 
                       fill:			true,
		       strokeColor:		'${color}',
                       strokeWidth:		3	
         }),
	 'select':  new OpenLayers.Style({
                       fillColor:		'rgb(176,255,176)',
                       fillOpacity:		'0.6', 
                       fill:			true
//		       strokeColor:		'#66bb66',
//                     strokeWidth:		3
         })
      });

      boundaries = new OpenLayers.Layer.Vector("boundaries", {
                       	displayInLayerSwitcher: true,
                        attribution: 		'Map Data &copy; <a href="https://www.openstreetmap.org/"target="_blank">OpenStreetMap</a> contributors',
                       	isBaseLayer:		false,
		       	visibility:		true,
//                     	opacity:                0.5, 
//                	numZoomLevels:		19,
       			permalink: 		"boundaries",
//           		maxResolution: 		resolutions[11], 
			projection:		projfrom,        
            		strategies:		[new OpenLayers.Strategy.BBOX({ratio: 1,resFactor: 1})
                                                ],
            		protocol:		new OpenLayers.Protocol.HTTP({
             			url:		"getBoundaries7",
             			readWithPOST:   true,
				params:		{debug:1,
                                                 database:database,
                                                 selected: selected,
                                                 exportAreas: exportAreas,
                                                 caller: myName,
                                                 zoom: -1  // wird in fireBoundaries() angepasst
                                                }, 
//                              callback:       function(resp, options) {
//                                                 alert(resp.responseText); 
//                                              },      
             			format:		new OpenLayers.Format.GeoJSON()
             	        }),            
			styleMap:		boundariesStyleMap
      });

      registerLoader(boundaries); 

      // *********************************************************************
      // ****************************** start main ***************************
      // **********************************************************************/
  
      var ls = "open";

//IF  document.getElementById("hiddenIframe").src ="";

      var cookie = $.cookie("osm_boundaries_map");  // map-spezifischer cookie

      var lCode = "0BT0";

      if (cookie != null) {
         console.log("got cookie osm_boundaries_map: "+cookie);
         var x =cookie.split('|');
         var version       = x[0];
         longitude         = x[1];
         latitude          = x[2];
         mapZoom           = x[3];
         lCode             = x[4];
         ls                = x[5];						// layerswitcher
      }

      console.log("I nach cookies");

      var url=$.url();
      console.log("I url: "+url.data.attr.source);

      var p =url.param("zoom");
      if (p != null) mapZoom = p;

      p =url.param("lat");
      if (p != null) latitude = p;

      p =url.param("lon");
      if (p != null) longitude = p;

      p =url.param("layers");
     
      if (p != null) {
         if (p.length == 2) setMapLayers(p);  // nur "eigene" layers verwenden
      }

      map.addLayer(mapnik);
      map.addLayer(mono);
//    map.addLayer(polmap);
//    map.addLayer(bingsat); // NEEE !     
      map.addLayer(boundaries);
      setMapLayers(lCode);
 
      console.log("I nach addLayers");

// ************************ LayerSwitcher Control **************************************/

      var LayerSwitcherControl = new OpenLayers.Control.LayerSwitcher();
      map.addControl(LayerSwitcherControl);

      if (ls == "closed")
         LayerSwitcherControl.minimizeControl();
      else
         LayerSwitcherControl.maximizeControl();

// ************************ Permalink Control **************************************/

// erzeugt https://osm.wno-edv-service.de/boundaries/idx098i.jsp?zoom=3&lat=-14.29778&lon=-170.69034&layers=0B0T
   
    PermalinkControl =  new OpenLayers.Control.Permalink(
        "permalink", 
        "https://"+host+"/boundaries/" +outerFrame ,
        {   div:               document.getElementById('permalink_div'),
    	    displayProjection: new OpenLayers.Projection(projfrom),
            createParams:      function() {
                                  var args = OpenLayers.Control.Permalink.prototype.createParams.apply(this, arguments);
                                  return args;
                               }
        }
    );
    
   map.addControl(PermalinkControl);

// ************************************************************************/
// ************************************************************************/
// ************************************************************************/

      //Set start centrepoint and zoom 
                 
      console.log("longitude="+longitude+" latitude="+latitude+" mapZoom="+mapZoom);            
      var lonLat = new OpenLayers.LonLat(longitude, latitude)
          .transform(projfrom, projto);
      console.log("calling map.setCenter("+longitude+","+latitude+","+mapZoom+")");
      map.setCenter (lonLat, mapZoom);

      map.events.register("moveend", map, moveHandler);
//    map.events.register("changelayerstate", map, ChangeLayerState);
//    map.events.register("changelayer",map, LayerChangedHandler); 
 
//   function ChangeLayerState(e) {
//       console.log("ChangeLayerState: "+e.property);
//       if(e.property === "visibility") {
//          console.log("visibility changed: " + e.layer.permalink+" now "+e.layer.visibility);
//       }
//       console.log("exit ChangeLayerState: "+e.property);
//    }

      parent.$(parent.document).bind("my_notifications", function(e, Alarm) {  
         console.log(Alarm);
         switch(Alarm) {
           case "boundaries_off":
               console.log("off");
               map.layers[2].setVisibility(false);
               break;
           case "boundaries_on":
               console.log("on");
               map.layers[2].setVisibility(true);
               break;
           default: 
              console.log("wat denn nu?");
              break;
         }
      });
      
      function LayerChangedHandler(e) {
         console.log("LayerChangedHandler: "+e.property);
         if(e.property === "visibility") {
            console.log("visibility changed: " + e.layer.permalink+" now "+e.layer.visibility);
         }
         console.log("exit LayerChangedHandler: "+e.property);
      }
  
      window.onresize = function()
      {
        setTimeout( function() { 
                 map.updateSize();
              }, 200);
      }

// ################# registerLoader #########################

      function registerLoader(layer) {
         console.log("Starting registerLoader("+layer.name+")");

         layer.events.register("loadstart", layer, function() {
            loading++; 
            console.log("loadStart("+layer.name+"): "+loading); 
//          ParentDoc.getElementById("loader_div").style.width = (32*loading)+"px"; // lupen an 
            ParentDoc.getElementById("loader_div").style.visibility="visible"; // lupe an     
         }); 

         layer.events.register("loadend", layer, function() { 
            loading--;
            console.log("loadEnd("+layer.name+"): "+loading);           
//          ParentDoc.getElementById("loader_div").style.width = (32*loading)+"px"; // lupen aus
            ParentDoc.getElementById("loader_div").style.visibility="hidden"; // lupe aus     
         }); 
      }

      moveHandler();
           
// ########################## Functions ######################################## */        

      function moveHandler() {
//       ParentDoc.getElementById("bbox_div").innerHTML = "bbox="+map.getExtent().transform(projto,projfrom).toBBOX(5).replace(/,/g,", ");
         ParentDoc.getElementById("zoom_div").innerHTML = "Z"+map.getZoom().toString();
      }

      function setMapLayers(layerConfig) {
         var l = 0;
         var baselayers = map.getLayersBy("isBaseLayer", true);
         console.log("setMapLayers: "+layerConfig+" baselayers.length:"+baselayers.length); 
         for (i = 0; i < baselayers.length; i++) {
            var c = layerConfig.charAt(l++);
            if (c == "B") {
               map.setBaseLayer(baselayers[i]);
            }
         }
  
         while (layerConfig.charAt(l) == "B" || layerConfig.charAt(l) == "0") {
            l++;
         }

         ovls = map.getLayersBy("isBaseLayer", false);
         console.log("setMapLayers: ovls.length:"+ovls.length);
         for (i = 0; i < ovls.length; i++) {
            var c = layerConfig.charAt(l++);
            console.log("setting "+ovls[i].name+" "+c);
            if (c == "T") {
               ovls[i].setVisibility(true);
            } else if(c == "F") {
                ovls[i].setVisibility(false);
            }
         }

         var i = map.getLayerIndex(mapnik); 

         if (map.layers[i].getVisibility())
            $("#slider_div").show();
         else
            $("#slider_div").hide();
      }

      // display lag */

      function ReloadPage() {
         console.log("ReloadPage(): loading="+loading);
         if (loading == 0)
            window.location.reload();
      };

// ************************************************************************** */

      function saveCookie() {

//  Version 1 init

         var mapcenter = new OpenLayers.LonLat(map.getCenter().lon,map.getCenter().lat).transform(projto, projfrom);
         var cookietext = "1|" + mapcenter.lon + "|" + mapcenter.lat;

         var zoom = map.getZoom();
//       console.log("saveCookie: zoom="+zoom);
         if (zoom == null) {
            console.log("zoom ist null!!!");
            zoom = 0;
         }
         cookietext = cookietext + "|" + zoom;
//       console.log("saveCookie: cookietext="+cookietext);

         lCode = "|";

         var layers = map.getLayersBy("isBaseLayer", true);
         for (var i = 0; i < layers.length; i++) {
            var flag = layers[i] == map.baseLayer ? "B" : "0";
            lCode += flag;
//          console.log("saveCookie(): \""+layers[i].name+"\" = "+flag);
         }  

         layers = map.getLayersBy("isBaseLayer", false);
         for (var i = 0; i < layers.length; i++) {
            if (layers[i].displayInLayerSwitcher) {
               var flag = layers[i].getVisibility() ? "T" : "F";   
               lCode += flag;
//             console.log("saveCookie(): \""+layers[i].name+"\" = "+flag);
            }
         }

         cookietext += lCode; 

         if (getLayerSwitcherStatus() == "none")
            LayerSwitcherIsExpanded = "open";
         else
            LayerSwitcherIsExpanded = "closed";

         cookietext += "|" + LayerSwitcherIsExpanded;

         console.log("setting cookie osm_boundaries_map: "+cookietext);

         $.cookie("osm_boundaries_map",cookietext,{expires: 365}); 

         console.log("exit ####################### Map ################################");
      }
      $(myParent.document).ready(function(){
         $(window).unload(function(e) {
            console.log("######################### reload/exit frame ###########################");
            saveCookie(); 
            var parent$ = parent.jQuery;
            parent$(iframe).trigger("iframeunloaded"); // sag "oben" Bescheid ;)
         });
      });

});   // big end --------------------------------------------------------------------

      function jumpTo() {       
         var jump = $("input[name='jumpTo']:checked").val();
         var jlonlat = JSON.parse(jump);
         console.log("jumpTo(): lon="+jlonlat.lon+" lat="+jlonlat.lat);
         lonlat = new OpenLayers.LonLat(jlonlat.lon,jlonlat.lat).transform(projfrom, projto); 
         popupClear();
         map.setCenter(lonlat,13);
      }

      function popupClear() {
         // alert('number of popups '+map.popups.length);
         while( map.popups.length ) {
            map.removePopup(map.popups[0]);
         }
      }

      function fireBoundaries(args) {
         console.log("I: here is fireBoundaries");

         for (var i = 0; i < args.selected.length; i++) {
//          console.log("I fireBoundaries: seleced= "+args.selected[i]);
            var rel = args.selected[i];
            if (i==0) 
               selected = rel;           
            else
               selected = selected + sep + rel; 
         }
         exportAreas = args.exportAreas;
//       console.log("I fireBoundaries: got "+selected+", "+exportAreas);

         boundaries.options.protocol.params.selected = selected;
         boundaries.options.protocol.params.exportAreas = exportAreas;
         boundaries.options.protocol.params.zoom = map.getZoom();
         rd = boundaries.redraw();
         PermalinkControl.updateLink();
      }

      function doBigBox(l,b,r,t) {
//       console.log("here ist doBigBox: "+l+" "+b+" "+r+" "+t);
         var bounds = new OpenLayers.Bounds(l,b,r,t).transform(projfrom, projto);
         map.zoomToExtent(bounds,false); 
      }

</script>

</head>

<body> 
   <noscript><div>
      <br/>
      <br/>
      <h2>Aktivieren Sie bitte Javascript um die Boundaries-Karte zu benutzen.</h2>
   </div></noscript>

   <div id="map_div">
      <!--div id="zoom_div"></div-->
   </div>

</body>

<!--script type="text/javascript">
//<![CDATA[
   fireOnReadyEvent();
   parent.IFrameLoaded();
//]]
</script>-->
</html>
