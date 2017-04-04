<!DOCTYPE html>
<!-- OUTER -->
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<meta http-equiv="Pragma" content="no-cache"> 
<title>OSM Boundaries 4.1</title>
<base target="_top" /> 

<!-- V 0.9    abgeleitet von residentials Live 
     V 0.9.1  umgestellt auf jstree
     V 0.9.5  deselect all children
     V 0.9.6  bpopup reaktiviert, poly aktiviert
     V 0.9.7o Umstellung auf IFrame
     V 0.9.8o Freigabe von bpoly
     V 1.0    Suche
     V 1.1    Alle Länder im Tree
     V 1.1b   Suche auch Relationen
     V 1.2    Export von SVG
     V 1.3    Permalink mit selected
     V 1.4    Boundaries selber rendern
     V 1.5    Permalink 2. Anlauf
     V 1.6    Layer besser initalisiert
     V 2.0    Data from collected_admin_boundaries only
     V 2.1    Simplify polygons 
     V 2.2    Spinner for BufferDist
     V 2.3    Union to MP, Export GeoJson
     V 2.4    DataBase Timestamp
     V 2.5    File based logging
     V 2.6    Export full country
     V 2.7    Anpassungen Header/Footer
     V 3.0    OAuth2
     V 3.1    fix Download
     V 3.2    tuned select/deselect children
     V 3.3    Flat Export angefangen
              Export ohne OAUTH früher verhindern
     V 3.4    getBigBbox auch nur wenn eingeloggt
     V 3.5    Export single/levels/flat
     V 3.6    Better error messages
     V 3.7    Select next level
     V 3.8    Ways aus Boundaries und nicht mehr aus CAB
              Export land or water areas
              Edit im Context
              Flattr
     V 4.0    Umstellung auf POST
     V 4.1    Umstellung auf API
              Anpassungen in Footer-Zeile
              Subtree auch für SVG
              API-Popup
              bmap37 für jstree shortcut_label - funzt net :(
              Versionspopup nur wenn alte Version 4.0
              Splash Screen
-->

<link rel="icon"       href="images/favicon.gif" type="image/gif" >
<link rel="stylesheet" href="https://wambachers-osm.website/common/js/jquery/jquery-ui-1.10.4.custom/css/south-street/jquery-ui-1.10.4.custom.css"
                       type="text/css">
<link rel="stylesheet" href="https://wambachers-osm.website/common/css/jquery/jstree/themes/default/style.css" type="text/css"/>
<link rel="StyleSheet" href="css/bmap41.css" type="text/css"/> <!-- vorerst lokal -->
<link rel="StyleSheet" href="css/ui-layout.css" type="text/css"/> <!-- vorerst lokal -->

<!--[if IE 6]>
   <link href="https://wambachers-osm.website/common/css/ie6.css" rel="stylesheet" type="text/css" />
<![endif]-->

<script>
   var myBase        = "boundaries";
   var myVersion     = "4";
   var mySubversion  = "1";   
   var myName        = myBase+"-"+myVersion+"."+mySubversion;
   var doSplashScreen = 0;
   var outerFrame    = "idx"+myVersion+mySubversion+"o.jsp";
   var innerFrame    = "idx"+myVersion+mySubversion+"i.jsp";
   var database      = "planet3";
   var loading       = 0;
   var selected      = "";
   var sep           = ",";
   var spinner_value = 10.0;
   var union         = false;
   var doApiPopup    = true;
   var exportLayout  = "levels";
   var saved_list    = "";
   var max_selected  = 50;
   var max_countries = 10;
   var host          = window.location.hostname;

   if (typeof console === "undefined" || typeof console.log === "undefined") {
     console = {};
     console.log = function() {};
   }

</script>

<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery-1.10.4.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery-ui-1.10.4.custom.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.ui-contextmenu.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.layout-latest.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.bpopup.min.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.cookie.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/purl.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jstree-3.0.0.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.scrollTo.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/jquery.fileDownload.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/jquery/noty-2.3.7/js/noty/packaged/jquery.noty.packaged.min.js"></script>
<script type="text/javascript" src="https://wambachers-osm.website/common/js/functions/global_functions2.js"></script>

<script> 
   displayAllCookies();

   $.removeCookie('osm_boundaries', { path: '' }); // cleanup old version
   $.removeCookie('test', { path: '/boundaries/' });

   $.removeCookie('__vjstorage', { path: '' }); 

$(document).ready(function() {
   
   console.log("init document.ready.");
   console.log("### Starting Boundaries Map. host="+host+" version="+myVersion+"."+mySubversion+" ###");

   var myLayout = $('#script').layout({ 
         applyDefaultStyles:    true
//  ,stateManagement__enabled:  true 

        ,north__closable:       false
        ,north__resizable:      false
        ,north__size:           55

        ,south__size:           50
        ,south__resizable:      false
    ,south__showOverflowOnHover:true

        ,west__size:            300        
        ,west__resizable:       false // funzt nicht !!!
        ,west__initClosed:      false        
        ,west__initHidden:      false
//  ,west__showOverflowOnHover: true

        ,east__size:            300 
        ,east__resizable:       false
        ,east__initClosed:      true        
   });

      myLayout.bindButton("#legend",'toggle','east'); 
      $("#legend").attr('title', 'Click for more infos');
  
      console.log("######################### Base Starting #####################"); 
   
      var ls = "open";

      var legend = false;
      var exportFormat = "json";  // default json
      var exportCompression = "zip";
      var exportAreas = "water";
      var from_al = 2;
      var to_al = 4;
      var api = "1.0";
      var ignoreMyMails = false;
      var ignoreMsg = false;
      var oldGuiVersion = null;
      var currentSplashScreen = 2;
      var selected_countries = 0;

      document.getElementById("hiddenIframe").src ="";  // ?????????????????????
      
// ****************** READ COOKIES ************************************
     
      cookie = $.cookie("osm_boundaries_base");
      var lCode = "";

      if (cookie != null) {
         console.log("O got cookie osm_boundaries_base: "+cookie);
         x             = cookie.split('|');
         console.log("O got ",x.length+" token");
         var version       = x[0];
         legend            = x[1];          // legende 
         exportFormat      = x[2];
         exportCompression = x[3];
         spinner_value     = x[4]; 
         if (spinner_value == "null") spinner_value = "10.0";
         
         union         = x[5];
         if (typeof union == "undefined") union = false;
         if (union == "undefined") union = false;
         
         exportLayout      = x[6];
         if (typeof exportLayout == "undefined") exportLayout = "levels";
         if (exportLayout == "undefined") exportLayout = "levels";
         
         exportAreas       = x[7];
         if (typeof exportAreas == "undefined") exportAreas = "water";
         if (exportAreas == "undefined") exportAreas = "water";
        
         oldGuiVersion     = x[8];
         if (typeof oldGuiVersion == "undefined") oldGuiVersion = "4.0";
         if (oldGuiVersion == "undefined") oldGuiVersion = "4.0";

         doApiPopup        = x[9];
         if (typeof doApiPopup == "undefined") doApiPopup = false;
         if (doApiPopup == "undefined") doApiPopup = false;
         
         doSplashScreen    = x[10];
         if (typeof doSplashScreen == "undefined") doSplashScreen = 0;
         if (doSplashScreen == "undefined") doSplashScreen = 0;
         if (doSplashScreen == "false") doSplashScreen = "0";

         console.log("cookies: ", version, legend, exportFormat, exportCompression, spinner_value, union, exportLayout, exportAreas,
                     oldGuiVersion, doApiPopup, doSplashScreen);
      }
         
      if (legend=='true') 
         myLayout.open('east');

      console.log("O nach cookies");

      var url=$.url();
      console.log("O url: "+url.data.attr.source);
      p =url.param("selected");
      if (p != null) {
         console.log("O selected: "+p);
         selected = (p+"").replace(/_/g,",");
      }

      document.getElementById('iframe').src=innerFrame+"?"+url.data.attr.query;

// check if gui changed

      if ((oldGuiVersion != myVersion+"."+mySubversion) && (oldGuiVersion != null)) {                           
         console.log("start noty");
         var n = noty({text: "Version changed: "+oldGuiVersion+" -> "+myVersion+"."+mySubversion+"<br/><br/>"
                            +   "See <a href=https://"+host+"/index.php/projekte/internationale-administrative-grenzen/boundaries-map-4-1'"
                            +   " target='_blank'>german</a> "                        
                            +      " or <a href='https://"+host+"/index.php/projekte/internationale-administrative-grenzen/boundaries-map-4-1-english-version'"
                            +   " target='_blank'>english</a> documentation",
                         buttons: [
                      { addClass:   'btn btn-danger',
                            text:       'Close', 
                         onClick:   function($noty) {
                                       $noty.close();
                                    }
                      }
                                  ],
                          layout:       "center",         
                            type:       "success",
                           theme:       "defaultTheme",
                         timeout:       30000,
                          killer:       false,
                    dismissQueue:       true
                      }
         );
      };
      
// check for Splash Screen
//    doSplashScreen = 0;
      if ((doSplashScreen != currentSplashScreen)) {                           
         console.log("start noty");
         var n = noty({text: 
/*                           "Ich m&ouml;chte hier an den Spenden-Button oben rechts auf der Webseite erinnern. "
                           + "Gerade von den Firmen, die nicht unerhebliche Kosten durch meine Dienstleistung sparen konnten, "
                           + "h&auml;tte ich bisher ein wenig mehr an Unterst&uuml;tzung erwartet."
                           + "Konkret: Da kam in den letzten 4 Monaten NIX, aber auch garnix :( Aus deren Zusagen 'Nat&uuml;rlich spenden wir was.' ist nie was geworden."
                           + "<p />"
                           + "Ich m&ouml;chte es nicht soweit kommen lassen m&uuml;ssen, dass Downloads von kommerziellen Anwendern kostenpflichtig werden - zumindest solange, bis der neue Server finanziert ist. Oder gar den Dienst v&ouml;llig einzustellen."
                           + "<p /p><hr/><p />"
                           + "I would like to remind you about the donate button top right of the website here. "
                           + "I would have expected more support from the companies which save money by using my service. "
                           + "Specifically: nothing was donated in the last 4 months - NOTHING, not even a cent :( "
                           + "From their commitments 'Of course we'll donate something.' nothing materialized. "
                           + "<p />"
                           + "I do not want to have to go as far as charging for downloads of commercial users or even completely discontinue the service "
                           + "- at least until the new server is financed. Or even completely discontinue the service." */
                             "Removed big bug while doing simple export (without using the API)."
                           + "<p />"
                           + "The GUI did a full subtree export instead of only selected boundaries."
                           + "<p />"
                           + "sorry for that :("
                           ,
                        buttons: [
                              { addClass:   'btn btn-danger',
                                    text:   'Close', 
                                 onClick:   function($noty) {
                                               $noty.close();
                                            }
                              }
                             ],
                          layout:       "center",         
                            type:       "success",
                           theme:       "defaultTheme",
                         timeout:       30000,
                          killer:       false,
                    dismissQueue:       true
                      }
         );
         doSplashScreen = currentSplashScreen;
      };
      
      console.log("O nach osm_boundaries_base");

// ******************** TREE *************************/

// http://doc.iut2.upmf-grenoble.fr/modules/webportal/jquery/jsTree.v.0.9.5/documentation/

// https://simpledotnetsolutions.wordpress.com/2012/11/25/jstree-few-examples-with-asp-netc/   !!!!!! sau gut

// http://www.miketyka.com/2012/10/lazy-loading-with-jstree-and-ajax/

      var dsacLevel = 0;
      var doing_children = false;

      $('#jstree').jstree({
         core : {
            multible: true,
            html_titles: false,
//          hide_stripes: true,
            animation: 300, 
            theme : {
               variant : 'large'
            },
            data : {
                url : "getJsTree5",
                async: false,  // sehr wichtig !!!
                data: function(node) {
                         console.log("jstree.data(node) called");
                         var parent = node.id;
                         var path = node.path;
                         var adminLevel;
                         if (parent === "#") {
                            parent = 0;
                            path = ""; // ???
                            adminLevel=1; // Parent der Länder !
                         }                      
                         var ret = { caller: myName,
                                     database: database,
                                     parent: parent,
                                     path: path,
                                     admin_level: adminLevel 
                                   }; 
                         return(ret);                                       
                },
                type  : "POST", 
                success  : function(result) {
//                            console.log("result: "+JSON.stringify(result));
                              return result;
                }
            }
         },
         plugins : [ "contextmenu", "checkbox" ],
         contextmenu : {
            select_node: false,
            show_at_node : false,
            items : {
               "select_children" : {
                  label   : "Select children",         
                  icon    : "images/green_check.png",
                  shortcut : 83,
                  shortcut_label: "S",
                  action  : function (data) { 
                               var inst = $.jstree.reference(data.reference);
                               var obj = inst.get_node(data.reference);
                               var state = obj.state;
                               var loaded = state.loaded;
                               if (!loaded) {
                                  var node = obj.id;
                                  var x= tree.load_node(node);
                               }                         
                               var childs = obj.children;   // offene Children des n&auml;chsten Levels
                               var first_childs = childs.slice(0,childs.length-1);
                               console.log("first_childs: "+first_childs.length);
                               if (first_childs.length > 0) {
                                  doing_children=true;
                                  tree.select_node(first_childs);
                               }
                               console.log("loading last child: "+childs[childs.length-1]);
                               doing_children = false;
                               tree.select_node(childs[childs.length-1]);
                            }           
               },
               "deselect_children" : {
                 label   : "Deselect children",         
                 icon    : "images/grey_x.png",
                 shortcut : 68,
                 shortcut_label: "D",
                 action  : function (data) { 
                              var inst = $.jstree.reference(data.reference);
                              var obj = inst.get_node(data.reference);
                              var childs = obj.children;    // offene Children des n&auml;chsten Levels
//                              doing_children=true;
                              tree.deselect_node(childs);
//                             doing_children = false;
                           }           
               } ,
               "deselect_all_children" : {
                 label   : "Deselect all children",         
                 icon    : "images/grey_x.png",
                 shortcut : 65,
                 shortcut_label: "A",
                 action  :  function (data) { 
                              var inst = $.jstree.reference(data.reference);
                              var obj = inst.get_node(data.reference);
                              var id = obj.id; 
                              dsacLevel = 0;
//                              doing_children=true;
                              deselectAllChildren(id);
//                              doing_children = false;
                           }        
               },
               "select_next_level" : {
                  label   : "Select next admin level",         
                  icon    : "images/green_check.png",
                  shortcut : 78,
                  shortcut_label: "N",
                  action  : function (data) { 
                               var inst = $.jstree.reference(data.reference);
                               var obj = inst.get_node(data.reference);
                               var state = obj.state;
                               var loaded = state.loaded;
                               if (!loaded) {
                                  var node = obj.id;
                                  var x= tree.load_node(node);
                               }                         
                               var childs = obj.children;   // offene Children des nächsten Levels
                                                            // als Ids der Relationen
                               // get lowest admin_level of all childs
                               var lowestChilds = getChildsWithLowestAdminLevel(obj.id);
                               console.log("lowestChilds="+lowestChilds);

                               var first_childs = lowestChilds.slice(0,lowestChilds.length-1);
                               console.log("first_childs: "+first_childs.length);
                               if (first_childs.length > 0) {
                                  doing_children=true;
                                  tree.select_node(first_childs);
                               }
                               console.log("loading last child: "+lowestChilds[lowestChilds.length-1]);
                               doing_children = false;
                               tree.select_node(lowestChilds[lowestChilds.length-1]);
                            }           
               },
               "export_subtree" : {
                 label   : "Export full subtree (json, shp & svg) WILL BE REMOVED IN RELEASE 4.3 - Start using the API",         
                 icon    : "images/green_check.png",
                 shortcut : 88,
                 shortcut_label: "X",
                 action  :  function (data) { 
                              if (oauth) {
                                 var inst = $.jstree.reference(data.reference);
                                 var obj = inst.get_node(data.reference);
                                 var id = obj.id; 
                                 console.log(id);
                                 if (exportFormat == "json" || exportFormat=="shp" || exportFormat == "svg") {
                                    doExport(id,true);
                                 }
                                 else {
                                    alert("Full Subtree Export only for GeoJson, Shapes and SVG");
                                 }
                              }
                              else
                                 alert("You must be logged in to export. Please enable OAuth.");
                           }        
               },
               "edit" : {
                 label   : "Edit (JOSM &  Merkaartor only)",         
                 icon    : "images/green_check.png",
                 shortcut : 69,
                 shortcut_label: "E",
                 action  : function (data) { 
                              var inst = $.jstree.reference(data.reference);
                              var obj = inst.get_node(data.reference);
                              var id = obj.id; 
                              console.log(id);
                              var url = "https://localhost:8112/load_object?new_layer=false&relation_members=true&objects=r"+id;
                              $("#hiddenIframe").load(url,function(response, status, xhr) {
                                                             if (status=="error") 
                                                                alert("No external editor running.");
                                                          });

                           },                 
                 error :  function(result) {
                               console.log("result: ", result);
                               return;
                           }                   
               } 
            }
       
         },
         checkbox : {
            three_state: false,
            keep_selected_style : true
         }
      });

      function deselectAllChildren(id) {        
            dsacLevel = dsacLevel+1;
            console.log("deselectAllChildren level: "+dsacLevel+" id: "+id);
            var node = tree.get_node(id);
            var childs = node.children;
            tree.deselect_node(childs);
            for (var i=0;i<childs.length;i++) {
                deselectAllChildren(childs[i]);
            };
            dsacLevel = dsacLevel-1;
            console.log("deselectAllChildren done. level: "+dsacLevel);
      }

      function getChildsWithLowestAdminLevel(father) {
         console.log("getChildsWithLowestAdminLevel(",father,") starting");
         var lowest = null;
         $.ajax({
                 type:      "POST",
                 async:         false,
                 timeout:   30000,
                 url:       "getChildsWithLowestAL",
                 data: {
                      caller:   myName,
                      database: database,
                      father:   father,
                  debug:    2
                       },
                 dataType:  "json",
                 success: function(json, status) {
                             lowest = json;
//                           alert("childs: "+json);
                 },
                 error: function(XMLHttpRequest, textStatus, errorThrown) {
                     console.log("error");
                     alert("error");
                 }   
         });
         return(lowest);  // dummy
      }

      var tree = $.jstree.reference('#jstree');
      
      // versuche selects der Länder (admin_level=2) zu beschränken

      $('#jstree').on("changed.jstree", function (e, data) {
         console.log("O changed.jstree: data.action=",data.action);
         console.log("O changed.jstree: "+data.selected.length+": "+data.selected," doing_children=",doing_children);
         if (data.action == "select_node") {
            if (data.selected.length > max_selected) { // war 660
               alert ("Too many boundaries selected, use the new API or mail wnordmann@gmx.de");
               return;
            }
            console.log("O changed.jstree: data.node.data.admin_level=",data.node.data.admin_level,
                                         " doing_children=",doing_children);
            if ((data.node.data.admin_level == "2") & (doing_children==false)) {
               selected_countries = selected_countries + 1;
               console.log("max_countries=",max_countries,"selected_countries=",selected_countries);
               if (selected_countries > max_countries) {
                  alert ("Too many countries selected - please use API to fetch country borders");
                  // deselect last border
                  tree.deselect_node(data.node);
                  return;
               }
            }
         } else { // action=deselect_node
            console.log("O changed.jstree: data.node.data.admin_level="+data.node.data.admin_level);
            if (data.node.data.admin_level = "2") {
               selected_countries = selected_countries -1;
            }
         }
       
//       if (!doing_children) getBoundaries(data.selected, exportAreas);  // ????????????????
      });
      
      $('#jstree').on("changed.jstree", function (e, data) {
         console.log("O changed.jstree: "+data.selected.length+": "+data.selected);
         console.log("O changed.jstree: doing_children="+doing_children);
         if (data.selected.length > max_selected) { // war 660
            alert ("Too many boundaries selected, use new API or mail wnordmann@gmx.de");
            return;
         }
         if (!doing_children) getBoundaries(data.selected, exportAreas);  // ????????????????
      });

      var jsn = $.jstree.reference('#jstree').get_json('#',{});

//    process "selected" from Permalink - hope so ;)

      console.log("Permalink: selected="+selected);
      if (selected != "") {
         var rels = selected.split(sep); 

         for (var ri=0;ri<rels.length;ri++) {
            var rel = rels[ri];
            console.log("O rel["+ri+"]: "+rel);
            $.ajax({
               type:            "POST",
               timeout:         30000,
               url:             "searchTree6",
               async:           false,            // sehr wichtig !!!
               data: {
                  search:       rel,
                  mode:         "simple",
                  encode:       "true",
                  caller:       myName,
                  database:     database
               },
               dataType:        "json",
               success: function(json, status) {
                  console.log("O loadSelected: success");  
                  var path = json[0].path;
                  jumpToLeaf(path);           
               },
               error: function(XMLHttpRequest, textStatus, errorThrown) {
                  console.log("loadSelected: An error has occurred making the request: " + errorThrown);
               }
            }); 
         }
      }

      tree.hide_stripes();
      tree.show_dots();

// ***************************************************************************** */

      function getBoundaries(list, exportAreas) {        
         if (list =='') list='0';
         console.log("getBoundaries("+list+", "+exportAreas+") starting");
//       boundaries.events.triggerEvent("loadstart",boundaries); 
         saved_list = list;
         var list2 = "";
         for (i=0;i<list.length;i++) {
            list2 = list2+list[i]+sep;
         };
         list2 = list2.substring(0,list2.length-1);

         var now = new Date();
         var ts = now.getTime();

         console.log("list2.length="+list2.length);
//4      if (list2.length > 5300) {
//4         alert ("2 Too many boundaries selected, use new API or mail wnordmann@gmx.de");
//4         return;
//4      } 
      
         var oa = true;
         if (list2!='0') oa = getBigBbox(list2, exportAreas); // oa : oauth
         if (oa) {    
            console.log("Fireing with {'timestamp':"+ts+", selected:"+list+", exportAreas:"+exportAreas+"}"); 
            document.getElementById('iframe').contentWindow.fireBoundaries({'timestamp':ts,  // fire Boundaries-Layer 
                                                                             selected:list,   
                                                                             'exportAreas':exportAreas});  
            console.log("getBoundaries("+list+", "+exportAreas+") done");
         }
      }

// ***************************************************************************** */

// hier kann ich Areas vorerst ignoriereren.

      function getBigBbox(list, exportAreas) {
         console.log("getBigBbox("+list+", "+exportAreas+") starting");
         var oa = true;
         $.ajax({
                 type:      "POST",
                 async:     false,
                 timeout:   30000,
                 url:       "getBigBbox4",
                 data: {
                      caller:   myName,
                      database: database,
                      selected: list,
                      debug:    1
                       },
                 dataType:  "json",
                 success: function(json, status) {
                             var l = json.coordinates[0][0][0];
                             var b = json.coordinates[0][0][1];
                             var r = json.coordinates[0][2][0];
                             var t = json.coordinates[0][1][1];
                             document.getElementById('iframe').contentWindow.doBigBox(l,b,r,t);
                 },
                 error: function(XMLHttpRequest, textStatus, errorThrown) {
                     console.log("OAuthLogin: An error has occurred making the request: " + errorThrown);
                     if (errorThrown.substring(0,3)=="Too")
                        alert (errorThrown+" Please use new API or mail wnordmann@gmx.de");
                     else {
                        alert(errorThrown+" Please login and enable OAuth");
                        oa = false;
                     }
                 }   
         });
         return(oa);
      }

// ***************************************************************************** */

    function doExportInner(exportList, subtree) {
          var url = "exportBoundaries";  
          
          console.log("lupe an");
          document.getElementById("loader_div").style.visibility="visible"; // lupe an
          
          console.log("myName=", myName);
          
          // cookie: osm_boundaries_oauth_session=31497e1f-1bb9-4083-bb14-7f3db6529397 ist apikey ??????????????
          
          console.log("doExportInner: exportList=",exportList,"subtree=",subtree);
      
          var apikey =  $.cookie("osm_boundaries_oauth_session");
          
          $.fileDownload(url, {
             httpMethod: "POST",
             data: {
               selected:        exportList,
               caller:          myName,
               debug:           2,
               apikey:          apikey,
               apiversion:      "none",
               database:        database,
               exportFormat:    exportFormat,
               compression:     exportCompression,
               bufferDist:      spinner_value*1000,
               union:           union,
               subtree:         subtree,
               exportLayout:    exportLayout,
               exportAreas:     exportAreas,
               target:          "file"
            },
            prepareCallback: function (url) {
               console.log("prepareCallback called");
            },
            successCallback: function (url) {
               console.log("successCallback: ", url);
            },
            failCallback:   function (url) {
               console.log("failCallback: ", url);
               alert("Error doing export - please contact admin (wambacher)");
            }       
             });
             console.log("lupe aus");
             document.getElementById("loader_div").style.visibility="hidden"; // lupe aus ????
    }

    function doExport(exportList, subtree) { 
         console.log("doExport("+subtree+") starting");
         spinner_value = spinner.spinner("value");
         union = $("#union").prop('checked');
         doApiPopup = $("#doApiPopup").prop('checked');
         exportFormat = $("input[name='exportFormat']:checked").val();
         exportLayout = $("input[name='exportLayout']:checked").val();
         if (exportFormat == "svg" ) exportLayout = "single"; // svg nur single
         console.log("exportFormat:",exportFormat,"exportLayout:",exportLayout);
               
         $( "#api_popup" ).dialog({
            autoOpen: false,
            closeText: "Close",
            title: "Api",
            height: 300,
            width: 550,
            buttons: {
               "Continue": function() {
                  $( this ).dialog( "close" );
                  doExportInner(exportList, subtree);
               },
               Cancel: function() {
                  $( this ).dialog( "close" );
               }             
            }    
         });
         
         // cookie: osm_boundaries_oauth_session=31497e1f-1bb9-4083-bb14-7f3db6529397 ist apikey JA!

         var apikey =  $.cookie("osm_boundaries_oauth_session");
         
         $("#api_popup").dialog("option", "title", "API 1.0");

         if (doApiPopup) {
         
            var content;
         
            content = "URL: <br>https://"+host+"/boundaries/exportBoundaries"
                    + "?apiversion=1.0"
                    + "&apikey="+apikey
                    + "&exportFormat="+exportFormat
                    + "&exportLayout="+exportLayout
                    + "&exportAreas="+exportAreas                   
                    + "&from_al="+from_al
                    + "&to_al="+to_al;                   

//          if (subtree) content = content
//                  + "&subtree=true";

            content = content
                    + "&union="+union
                    + "&selected="+exportList
                    + "<br>"
                    + "<br> e.g. curl -f -o file.zip --url 'URL' will save data to file."
                    + "<br> Don't forget the ' before and after the url!" ;
                 
            document.getElementById("api_popup").innerHTML = content; 
            $("#api_popup").dialog("open");
         }
         else
            doExportInner(exportList, subtree);
         
         console.log("ende lupe aus");
         document.getElementById("loader_div").style.visibility="hidden"; // lupe aus, funzt net
   } 

// ***************************************************************************** */

      console.log("setting #"+exportFormat+" to checked");
      $("#"+exportFormat).prop("checked",true);

      if (union=="true") {
         console.log("setting #union to checked");
         $("#union").prop("checked",true);
      }

      if (doApiPopup=="true") {
         console.log("setting #doApiPopup to checked");
         $("#doApiPopup").prop("checked",true);
      }

      console.log("setting #"+exportLayout+" to checked");
      $("#"+exportLayout).prop("checked",true);
      
      var XexportAreas = $("input[name='exportAreas']:checked").val();
      console.log(XexportAreas);

      console.log("setting #"+exportAreas+" to checked");
      $("#"+exportAreas).prop("checked",true);
      
      XexportAreas = $("input[name='exportAreas']:checked").val();
      console.log(XexportAreas);

// spinner start

      var spinner = $("#spinner" ).spinner({
         step:      0.1,
         numberFormat:  "n"
      });
      spinner.spinner( "disable" );
      $("#spinner_div").hide();
      if (exportFormat == "bpoly") {
         console.log("enabling spinner");
         spinner.spinner( "enable" );
         $("#spinner_div").show();
         spinner.spinner( "value", spinner_value );
      }
      if (exportFormat == "svg") $("#exportLayout_div").hide();

// spinner end

// Click handler für ExportFormat und ExportLayout

      $("#format_div").on("click", function() {
         exportFormat = $("input[name='exportFormat']:checked").val();
         console.log("exportFormat "+exportFormat + " is set");
         
         if (exportFormat == 'svg') {
            console.log("disabling single/levels/split")
            $("#exportLayout_div").hide();
         }
         else {
            console.log("enabling single/levels/split")
            $("#exportLayout_div").show();
         }
         
         if (exportFormat != "bpoly") {
            console.log("disabling spinner");
            spinner.spinner( "disable" );  
            $("#spinner_div").hide();
         }
         else {
            console.log("enabling spinner");
            spinner.spinner( "enable" ); 
            $("#spinner_div").show();
         }
      });
      
      $("#union_div").on("click", function() {
         union = $("#union").prop('checked');
         console.log("union is set to", union); 
      });
      
      $("#exportLayout_div").on("click", function() {
         exportLayout = $("input[name='exportLayout']:checked").val();
         console.log("exportLayout "+exportLayout+" is set"); 
      });
      
      $("#exportAreas_div").on("click", function() {
         var exportAreas_old = exportAreas;
         exportAreas = $("input[name='exportAreas']:checked").val();
         if (exportAreas != exportAreas_old) {
            console.log("exportAreas is set to "+exportAreas);
            getBoundaries(saved_list, exportAreas);
         }
      });

      $("#apiPopup_div").on("click", function() {
         doApiPopup = $("#doApiPopup").prop('checked');
         console.log("doApiPopup is set to", doApiPopup); 
      });
      
      $("#ebutton").button(
         { icons: { primary:   "ui-icon-disk",
                    secondary: "ui-icon-disk"}
         }
      ).click(function() {
      console.log("export button pressed");

      if (oauth) {
         var selectedNodes = tree.get_selected();
         var selectedList = $.map(selectedNodes, function(object){
                  return -object;
         });

//       var exportList = selectedList.join(sep);
//       console.log("Export",exportList);

         var exportList = selectedNodes.join(sep);
         console.log("Export2",exportList);
        
         if (exportList == "")
            alert("Export does not make sense.\nNo boundaries selected in left sided tree.");
         else {
            document.getElementById("loader_div").style.visibility="visible"; // lupe an
            doExport(exportList,false);
            document.getElementById("loader_div").style.visibility="hidden"; // lupe aus
         };
      }
      else
         alert("You must be logged in to export. Please enable OAuth.");
     });

// ######################## search ########################

      $("#searchButton").click(function() {
         var searchString = $('#searchBox').val().trim();
         if (searchString != "") {
            doSearch(searchString);  
         }
         else
            alert("Bitte Suchbegriff eingeben");
      });
      
      $("#searchButton").attr('title', 'Name, ISO3-Code in upper case (e.g. DEU) or Relation id');      

      $("#searchBox").keyup(function(event) {
         var searchString = $(this).val();
         console.log("keyup: event="+event.which+" "+searchString+" ("+searchString.length+")");
         if (event.which == 13) {
            console.log("doSearch("+searchString+")");
            if (searchString != "") {
               doSearch(searchString);  
            }
            else
               alert("Enter Search String");
         }
      });

      $( "#search_popup" ).dialog({
         autoOpen:  false,
         closeText: "Close",
         title:     "Select boundary",
         height:    300,
         width:     550
      });

      function doSearch(search) {
         console.log("O doSearch("+search+")");
         loading++; 
         var vis = 
         document.getElementById("loader_div").style.visibility="visible"; // lupe an     
      
         $.ajax({
            type:           "POST",
            async:          false,
            timeout:        30000,
            url:            "searchTree6",
            data: {
               search:          search,
               mode:            "full",
               encode:          "true",
               caller:          myName,
               database:        database
            },
            dataType:           "json",
            success: function(json, status) {
               console.log("O doSearch: status="+status);  
               if (status == "success") { 
                  var content = "";
                  content += "<div style='font-size:.7em;'>\n";

                  content += "<table width='500' border='1' cellspacing='1' cellpadding='2'>\n";
                  content += "<th width='10px'></th><th>Name</th><th>AL</th><th>is_in (generated)</th><th>id</th>\n";

                  for (i=0;i<json.length;i++) {
                     var f = json[i];
                     var id = f.id;
                     var country = f.country;
                     var name = f.name; 
                     var is_in = f.is_in;  
                     var level = f.level;
                     var path = f.path;   
                     content += "<tr>\n";
                     content += "<td>\n";
                     content += "<input type='radio' name='jumpTo' value='"+path+"'";
                     if (i == 0) content += " checked='checked'";
                     content += "></input>\n";
                     content += "</td>\n"; 
                     content += "<td>"+name+"</td>\n";
                     content += "<td align='center'>"+level+"</td>\n";
                     content += "<td>"+is_in+"</td>\n";
                     content += "<td align='right'>"+id+"</td>\n";
                     content += "</tr>"; 
                  }
                  content += "</table>\n";
                  content +=       "<input id='goto' type='button' value='Goto'";
                  content +=       " onclick='jumpTo();'";
                  content += "</div>";

//                console.log(content);

                  document.getElementById("search_popup").innerHTML = content;
                  $("#search_popup").dialog("option", "title", "Select boundary: " + search 
                                       + "     (first 20 hits)");
                  $("#search_popup").dialog("open");
               } else {
                  alert("\""+search+"\" not found in database");
//                var path = json[0].path;
//                jumpToLeaf(path);
               }
            },
            error: function(XMLHttpRequest, textStatus, errorThrown) {
                console.log("doSearch: An error has occurred making the request: " + errorThrown);               
            }
         });
         loading--;           
         document.getElementById("loader_div").style.visibility="hidden"; // lupe aus
      }

// ++++++++++++++++++++++++++++++++++++++++ OAuth2 +++++++++++++++++++++++++++++++++++

       $("#oauth").click(function() {
          console.log("OAuth: oauth="+oauth);

          if (!oauth) { // nicht bereits eingeloggt 
             $.ajax({
               type:        "POST",
               timeout:     30000,
               url:         "OAuthlogin3a",
               data: {
                    myBase:  myBase,
                    caller:  myName,
                    debug:   2
               },
               dataType:        "json",
               success: function(json, status) {                        
                  if (json.redirect) {
                     window.location.href = json.redirect;
                  }
                  else {              
                     // wat nu?
                  }
               },
               error: function(XMLHttpRequest, textStatus, errorThrown) {
                   console.log("OAuthLogin3a: " + errorThrown);
               }   
             });
          }
          else {
             console.log("logging out");
             $.ajax({
               type:        "POST",
               timeout:     30000,
               url:         "OAuthLogin3c",
               data: {
                    myBase:  myBase,
                    caller:  myName,
                    debug:   2
               } 
             });
             $.removeCookie('osm_boundaries_oauth_session', { path: '' });

             $("#oauth").html("Enable OAuth");
             changeBackgroundColor('oauth', '#ffaaaa'); // red
             changeBackgroundColor('ebutton', '#ffaaaa');   // red
             oauth_session = null;
             oauth = false;
          }
       });

//     -------------------

       var oauth_session = $.cookie("osm_boundaries_oauth_session");
       var oauth = false;

       if (oauth_session == null) {
          $("#oauth").html("Enable OAuth");
          changeBackgroundColor('oauth', '#ffaaaa');    // red
          changeBackgroundColor('ebutton', '#ffaaaa');  // red
       }
       else {
          $("#oauth").html("Disable OAuth");
          changeBackgroundColor('oauth', '#aaffaa') // green
          changeBackgroundColor('ebutton', '#aaffaa');  // red
          oauth = true;
       }

       console.log("nach oauth");

// ++++++++++++++++++++++++++++++ Ende OAuth +++++++++++++++++++++++++++++++++++++++++++

        setInterval(getAction,60*10*1000); 
        getAction(); 

        getTimestamp();
          
// ########################## Functions ######################################## */ 

      function getAction() {
         $.ajax({
                 type:      "POST",
                 timeout:   30000,
                 url:       "getAction5", 
                 data: {
                      caller:   myName,
                      base:     myBase,
                      debug:    1,
                      database:database
                 },
                 async:     false,
                 dataType:  "text",
                 success: function(action, status) {
                    var ac = action.split(":"); 
                    switch(ac[0]) {
                       case "reload":
                          console.log("getAction: got order to reload page");
                          console.log("pathname = "+window.location.pathname); // Returns path only
                          console.log("url      = "+window.location.href);     // Returns full URL
                          ReloadPage();
                          break;
                       case "unread":
                          if (! ignoreMyMails) {
                             console.log("start noty");
                             var n = noty({text:        "You got "+ac[1]+" unread mails in your "
                                                      +     "<a href='http://openstreetmap.org/user/"+ac[2]
                                                      +     "/inbox' target='_blank'>OSM-Mailbox</a> "
                                                      +         "Click link to open mailbox.",
                                           buttons: [
//                             /*            {addClass: 'btn btn-primary', 
//                                             text:        '   Read Mail', 
//                                             onClick:     function($noty) {
//                                                 $noty.close();
//                                                                 ignoreMyMails = true;
//                                          }
//                                    }, */
                                      {addClass:    'btn btn-primary', 
                                               text:        'Ignore in current session', 
                                               onClick:     function($noty) {
                                           $noty.close();
                                                                   ignoreMyMails = true;
                                            }
                                      },
                          {addClass:    'btn btn-danger',
                           text:        'Close', 
                                               onClick:     function($noty) {
                                   $noty.close();
                                }
                          }
                                       ],
                                           layout:      "center",         
                                           type:        "success",
                                           theme:       "defaultTheme",
                                           timeout:     30000,
                           killer:      true,
                       dismissQueue:    false
                                          }
                                         );
                          }
                          break;
                       case "msg":
                          if (!ignoreMsg) {
                             var n = noty({text:        ac[1],
                                           buttons: [
                              {addClass:    'btn btn-primary',
                           text:        'Ignore in current session', 
                                               onClick:     function($noty) {
                                           $noty.close();
                                                                   ignoreMsg = true;
                                }
                          },
                                      {addClass:    'btn btn-primary', 
                                               text:        '   Close', 
                                               onClick:     function($noty) {
                                   $noty.close();
                                            }
                                      }
                                       ],
                                           layout:      "center",         
                                           type:        "success",
                                           theme:       "defaultTheme",
                                           timeout:         30000,
                       killer:      true,
                       dismissQueue:    false
                                       }
                                    );
                          }
                          break;
                    }
                 },
                 error: function(XMLHttpRequest, textStatus, errorThrown) {
                     console.log("getAction: An error has occurred making the request: " + errorThrown);
                 }   
               });
      }

      function getTimestamp() {
         $.ajax({
                 type:      "POST",
                 timeout:   30000,
                 url:       "getTimestamp", 
                 data: {
                      caller:   myName,
                      debug:    1,
                      database: database,
                      table:    'collected_admin_boundaries'
                 },
                 async:     false,
                 dataType:  "text",
                 success: function(ts, status) { 
                             document.getElementById("lag_div").innerHTML = "timestamp="+ts;
                 },
                 error: function(XMLHttpRequest, textStatus, errorThrown) {
                     console.log("getTimestamp(): An error has occurred making the request: " + errorThrown);
                 }   
               });
      }
     
      function ReloadPage() {
         console.log("ReloadPage(): loading="+loading);
         window.location.reload(true); // ohne cache
      }

// ************************************************************************** */

      function saveCookie() {

//  Version 1 init
//  Version 2 spinner_value
//  Version 3 exportLayout + exportAreas + myVersion/subversion
//  Version 4 do Splash Screen

         var cookietext = "4";  // version

         cookietext += "|" + !myLayout.state.east.isClosed; //  1 legend
         cookietext += "|" + exportFormat;                  //  2 exportFormat
         cookietext += "|" + exportCompression;             //  3 exportCompression
         cookietext += "|" + spinner_value;                 //  4 spinner
         cookietext += "|" + union;                         //  5 union
         cookietext += "|" + exportLayout;                  //  6 exportFormat
         cookietext += "|" + exportAreas;                   //  7 exportAreas
         cookietext += "|" + myVersion+"."+mySubversion;    //  8 Version
         cookietext += "|" + doApiPopup;                    //  9 doApiPopup
         cookietext += "|" + doSplashScreen;                // 10 doSplashScreen

         // save tree

         console.log("setting cookie osm_boundaries_base: "+cookietext);

         $.cookie("osm_boundaries_base",cookietext,{expires: 365}); 

         console.log("exit ########################## base #############################");
      }

      $("iframe").on("iframeunloaded", function(e){
         alert("here is iframeunload event handler:");
         console.log("here is iframeunload event handler:",e.type);
         saveCookie();
      });
      
// ************************************************************************** */ 

});   // big end

// jump to search result in tree

      function jumpTo() {          
         var path = $("input[name='jumpTo']:checked").val();  
         $("#search_popup").dialog("close"); 
         document.getElementById("loader_div").style.visibility="visible"; // lupe an  
         
         jumpToLeaf(path);
         
         document.getElementById("loader_div").style.visibility="hidden"; // lupe aus     
      }

      function jumpToLeaf(path) {
         console.log("jumpToLeaf: "+path);
         var leafs = path.split(sep);
         var tree = $.jstree.reference('#jstree');
         for (var n=1;n<leafs.length;n++) {
            var node = leafs[n];
            if (!tree.is_loaded(node)) {
               console.log("loading node: "+node);
               tree.load_node(node);
            }
            if (n==(leafs.length-1)) {
               console.log("jumpToLeaf: selecting node "+node);
               tree.select_node(node);
//               $("html, body").animate({
//                  scrollTop: $("#"+node).offset().top},'slow', function() { 
//                                          alert("Finished animating");
//                                        }
//                                );
               $("#jstree").scrollTo("50%",500);
                 
            }
         }         
      }

      function jtl_callback(node, status) {
//       do nothing

//       var tree = $.jstree.reference('#jstree');
//       if (!tree.is_open(node)) {   
//          tree.open_node(node);
//       }
      }

</script>

</head>

<body> 
 <!--noscript><div>
    <br/>
    <br/>
    <h2>Aktivieren Sie bitte Javascript um die Boundaries-Karte zu benutzen.</h2>
 </div></noscript-->
 
 <div id="script">

      <div class="ui-layout-north">
         <div id="header_div">

            <div id="lheader_div">
               <div id="title_div"><a href='./'>OSM Boundaries Map 4.1</a></div>
               <div id="lag_div"></div>
            </div>

            <div id="rheader_div">
               <div id="loader_div"></div>
               <div id="search_div">
                  <input id="searchBox" class='searchBox' type='text' value="">
                  <button id='searchButton'>Search</button>
                  use % as wildcard
               </div>
               <div id="legend_div">
                  <button id="legend">Info/Help</button>
               </div>               
               <div id="oauth_div">
                  <button id="oauth"></button>
               </div>
            </div>

         </div>
      </div>

      <div class="ui-layout-center">
         <iframe id="iframe" src="idx41i.jsp" 
                 width="100%" height="100%" style="position: absolute; border:0px;">
         </iframe>
         <div id="zoom_div"></div>
         <div id="mouse_div"></div>
         <div id="flattr_div">
             <a href="https://flattr.com/submit/auto?fid=w7z656&url=https%3A%2F%2F"+host+
                target="_blank"><img src="//button.flattr.com/flattr-badge-large.png" 
                alt="Flattr this" title="Flattr this" border="0"></a>
         </div>
         <form id='donate_div' action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
            <input type="hidden" name="cmd" value="_s-xclick">
            <input type="hidden" name="hosted_button_id" value="UXBDFTP8MC44L">
            <input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
            <img alt="" border="0" src="https://www.paypalobjects.com/de_DE/i/scr/pixel.gif" width="1" height="1">
         </form>
      </div>

      <div class="ui-layout-east">   
      <h2>Infos:</h2>
        <ul>
          <li>Hier ein Link zur <a href='https://'+host+'/index.php/projekte/internationale-administrative-grenzen/boundaries-map-4-1' target='_blank'>Dokumentation</a>.
          Es fehlt dort noch einiges, was besser beschrieben werden kann.</li>
          <li>Link to the <a href='https://'+host+'/index.php/projekte/internationale-administrative-grenzen/boundaries-map-4-1-english-version' target='_blank'>english documentation</a>.
          </li>
          <li>TODO-Liste zeigt offene Features, W&uuml;nsche und Fehler an.
              Meldungen und Vorschl&auml;ge bitte an <a href='https://www.openstreetmap.org/user/wambacher/'>per OSM Mail</a></li>
        </ul>
          
      <h2><a href='Todo?database='+database+'&amp;caller=boundaries' target='blank'>TODO-Liste</a></h2>

      <h2>Spenden/Donations</h2>
If you would like to keep this service up and running 24/7/365 please donate a little bit. It will be used for paying power, communications and buying additional hardware.<br/><br/>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="DAHKWQD6SEQ5W">
<input type="image" src="https://www.paypalobjects.com/en_US/DE/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form>
<p>Thank's,<br/>
Walter</p>

      <h2>Impressum & Kontakt</h2>
<p>Angaben gem&auml;&szlig; &sect; 5 Telemediengesetz (TMG):</p>
<p>Walter Nordmann<br /> Obergasse 31<br /> 65388 Schlangenbad<br /> Deutschland</p>
<p>Kontakt:</p>
<p>Tel.: +49 (0) 6129 509036<br /> Fax: -<br /> E-Mail: <a href='mailto:wnordmann@gmx.de'>wnordmann@gmx.de</a></p>
<p>Verantwortlicher f&uuml;r den Inhalt ist gem&auml;&szlig; &sect; 55 Abs. 2 Rundfunkstaatsvertrag (RStV):</p>
<p>Walter Nordmann<br /> Obergasse 31<br /> 65388 Schlangenbad<br /> Deutschland</p>
<p>Ausschlu&szlig; der Haftung:</p>
<p>1. Haftung f&uuml;r Inhalte<br /> Der Inhalt unserer Internetseite wurde mit gr&ouml;sstm&ouml;glicher Sorgfalt erstellt. Wir &uuml;bernehmen jedoch keine Gew&auml;hr daf&uuml;r, dass dieser Inhalt richtig, vollst&auml;ndig, und aktuell ist und zudem noch gef&auml;llt. Gem&auml;&szlig; &sect; 7 Abs. 1 TMG sind wir f&uuml;r den Inhalt verantwortlich, selbst wenn dieser wurde bestellt.<br /> Gem&auml;&szlig; den &sect;&sect; 8, 9 und 10 TMG ist f&uuml;r uns keine Verpflichtung gegeben, da&szlig; wir Informationen von Dritten, die &uuml;bermittelt oder gespeichert wurden, &uuml;berwachen oder Umst&auml;nde erheben, die Hinweise auf nicht rechtm&auml;&szlig;ige T&auml;tigkeiten ergeben.<br /> Davon nicht ber&uuml;hrt, ist unsere Verpflichtung zur Sperrung oder Entfernung von Informationen, welche von den allgemeinen Gesetzen herr&uuml;hrt.<br /> Wir haften allerdings erst in dem Moment, in dem wir von einer konkreten Verletzung von Rechten Kenntnis bekommen. Dann wird eine unverz&uuml;gliche Entfernung des entsprechenden Inhalts vorgenommen.</p>
<p>2. Haftung f&uuml;r Links<br /> Unsere Internetseite enth&auml;lt Links, die zu externen Internetseiten von Dritten f&uuml;hren, auf deren Inhalte wir jedoch keinen Einflu&szlig; haben. Es ist uns daher nicht m&ouml;glich, eine Gew&auml;hr f&uuml;r diese Inhalte zu tragen.<br /> Die Verantwortung daf&uuml;r hat immer der jeweilige Anbieter/Betreiber der entsprechenden Internetseite. Wir &uuml;berpr&uuml;fen die von uns verlinkten Internetseiten zum Zeitpunkt der Verlinkung auf einen m&ouml;glichen Rechtsversto&szlig; in voller Breite.<br /> Es kann uns jedoch, ohne einen konkreten Anhaltspunkt, nicht zugemutet werden, st&auml;ndig die verlinkten Internetseiten inhaltlich zu &uuml;berwachen. Wenn wir jedoch von einer Rechtsverletzung Kenntnis erlangen, werden wir den entsprechenden Link unverz&uuml;glich entfernen, das k&ouml;nnen wir machen.</p>
<p>3. Urheberrecht<br /> Die auf unserer Internetseite enthaltenen Inhalte sind, soweit m&ouml;glich, urheberrechtlich gesch&uuml;tzt. Es bedarf einer schriftlichen Genehmigung des Erstellers f&uuml;r denjenigen, der die Inhalte vervielf&auml;ltigt, bearbeitet, verbreitet oder n&uuml;tzt.<br /> Das Herunterladen und Kopieren unserer Internetseite ist sowohl f&uuml;r den privaten als auch den kommerziellen Gebrauch von uns schriftlich zu gestatten. Wir weisen darauf hin, dass wir hinsichtlich der Inhalte auf unserer Internetseite, soweit sie nicht von uns erstellt worden sind, das Urheberrecht von Dritten jederzeit beachtet hatten.<br /> Wenn Sie uns mitteilen w&uuml;rden, dass Sie trotzdem eine Urheberrechtsverletzung gefunden haben, w&uuml;rden wir das sehr sch&auml;tzen. Dann k&ouml;nnen wir den entsprechenden Inhalt sofort entfernen und w&uuml;rden damit das Urheberrecht nicht mehr verletzen.</p>
<p>4. Datenschutz<br /> Unsere Internetseite kann regelm&auml;ssig ohne die Angabe von personenbezogenen Daten genutzt werden. Falls solche Daten (z.B. Name, Adresse oder E-Mail) doch erhoben werden sollten, geschieht das, freiwillig oder nur mit ausdr&uuml;cklicher Zustimmung durch Sie.<br /> Die &Uuml;bertragung von Daten im Internet ist mit Sicherheitsl&uuml;cken befangen. Es ist daher m&ouml;glich, dass Dritte Zugriff auf diese Daten erlangen. Ein l&uuml;ckenloser Schutz ist nicht m&ouml;glich, wenn auch l&ouml;blich.<br /> Wir widersprechen an dieser Stelle der Nutzung unserer Kontaktdaten, um uns damit nicht verlangte Werbung/Informationsmaterial/Spam-Mails zukommen zu lassen. Sollte dies dennoch geschehen, m&uuml;ssten wir rechtliche Schritte ins Auge fassen.</p>
<p>Quelle: Flegl Rechtsanw&auml;lte GmbH</p>

      </div>

      <div class="ui-layout-west">       
         <div id="jstree"></div>
      </div>

      <div class="ui-layout-south">

         <div id="bbox_div"></div>

         <div id="format_div">
            <input type="radio" id="shp"   name="exportFormat" value="shp"/>           <label for="shp">shp</label>
            <input type="radio" id="json"  name="exportFormat" value="json"/>          <label for="json">json</label>
            <input type="radio" id="poly"  name="exportFormat" value="poly"/>          <label for="poly">poly</label>
            <br/>
<!--        <input type="radio" id="osm"   name="exportFormat" value="osm"   disabled/><label for="osm">osm</label> -->
            <input type="radio" id="svg"   name="exportFormat" value="svg"/>           <label for="svg">svg </label>
            <input type="radio" id="bpoly" name="exportFormat" value="bpoly"/>         <label for="bpoly">bpoly</label>
         </div> 

         <div id="spinner_div">
            <div id="spinnerContent">                   
               <input id="spinner" size="2.5em" name="value"/><label>&nbsp;km</label>
            </div>
         </div>

         <div id="union_div"> 
            <div id="unionContent">                        
               <input type="checkbox" id="union" name="union"/><label for="union">union</label>
            </div>
         </div>

         <div id="exportLayout_div">
            <div id="exportLayoutContent">
               <input type="radio" id="single" name="exportLayout" value="single"/> 
               <label for="single">single</label>
               <input type="radio" id="levels" name="exportLayout" value="levels" checked="checked"/> 
               <label for="levels">levels</label><br/>
               <input type="radio" id="split"  name="exportLayout" value="split"/>  
               <label for="split">split</label>
            </div>
         </div>

         <div id="exportAreas_div">
            <div id="exportAreasContent">
               <input type="radio" id="water" name="exportAreas" value="water" checked="checked"/> 
               <label for="water">water</label>
               <input type="radio" id="land" name="exportAreas" value="land"/> 
               <label for="land">land</label><br/>
            </div>
         </div>

         <div id="apiPopup_div"> 
            <div id="apiPopupContent">                        
               <input type="checkbox" id="doApiPopup" name="doApiPopup"/><label for="doApiPopup">API</label>
            </div>
         </div>

         <div id="export_div">
            <button id="ebutton">Export</button>
         </div>
      </div>

   <!--div id="lag_popup" style="width:500px;height:300px;"></div-->

   <div id="search_popup"></div>
   <div id="api_popup"></div>
   <div class="container">
      <div id="customContainer"></div>
   </div>
   <iframe style="display:none" id="hiddenIframe" name="hiddenIframe"></iframe>

</div>

</body>
</html>
