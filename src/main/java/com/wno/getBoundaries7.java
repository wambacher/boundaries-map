package com.wno;

/* version 1
   version 2 negate selected 
   version 3 use collected_admin_boundaries
   version 4 simplify polygons to speed up display
   version 5 Log to File
   version 6 exportLandareas support
   version 7 use boundaries, not cab
*/

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

import java.io.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

public class getBoundaries7 extends HttpServlet { 
       
    private String myName = "getBoundaries7";
    private String myLog  = "boundaries";

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {

        HttpSession httpsession = request.getSession();
        String sessionId = httpsession.getId();
        String caller = request.getParameter("caller");

        //is client behind something?
        String remoteAddr = request.getHeader("X-FORWARDED-FOR");  
        if (remoteAddr == null) {  
	       remoteAddr = request.getRemoteAddr();
        }

//      InetAddress inetAddress = InetAddress.getByName(remoteAddr);

        SimpleLog.write(myLog, myName+" remoteAddr.length()="+remoteAddr.length());

        String spaces = "                                      ";
        String myHeader = remoteAddr+spaces.substring(0,16-remoteAddr.length()) 
                        + caller + " " 
                        + myName+spaces.substring(0,30-myName.length()); 

        myHeader = myHeader + OsmOAuth.getOAuthSessionCookie(myLog, myHeader, request, 2) + " ";
          
        boolean first=true;
        int    rc  = 0;
        int    cnt = 0;
        
        String geoJsonString;
        
//      SimpleLog.write(myLog,myHeader+"started: request=\""+request.getQueryString()+"\"");

        int debug = Integer.parseInt(request.getParameter("debug"));
        debug = 2;
        
        if (debug>0)
           SimpleLog.write(myLog,myHeader+"started: requestURI=\""+request.getRequestURI()+"\"");
               
        String database = request.getParameter("database");
        String bbox_param = request.getParameter("bbox");
        String bbox = "st_MakeEnvelope("+bbox_param+")";
        String selected = request.getParameter("selected");
        String exportAreas = request.getParameter("exportAreas");
        String zoom = request.getParameter("zoom");

        int selected_count = selected.split(",").length;

        if (debug > 0) SimpleLog.write(myLog,myHeader+selected_count+" boundaries selected: "+selected);

        if (selected_count > 100) {

        }
        if (selected_count > 0) {
           if (debug > 1) SimpleLog.write(myLog,myHeader+"bbox_param: "+bbox_param); 

           String[] bb = bbox_param.split(",");
           float l = Float.parseFloat(bb[0]);
           float r = Float.parseFloat(bb[2]);
           float g = Math.abs(l-r);
           if (debug > 1) SimpleLog.write(myLog,myHeader+"l="+l+" r="+r+" --> g="+g); 
       
           float       t = 0f;
           if (g > 60) t = 0.005f;
           else
           if (g > 30) t = 0.001f;
           else
           if (g > 1)  t = 0.0005f;
           else        t = 0.0001f;
           
           String json = "";
    
           try {
              Class.forName("org.postgresql.Driver");
           } 
           catch (ClassNotFoundException cnfe) {
              SimpleLog.write(myLog,myHeader+"Couldn't find the driver!");
              cnfe.printStackTrace();
              System.exit(1);
           }
       
           Connection conn = null;
           try {
              String dburl = "jdbc:postgresql://localhost/"+database+"?user=osm&password=&ApplicationName=boundaries";
              conn = DriverManager.getConnection(dburl);
                      
              Statement statement = conn.createStatement();

              ResultSet rs = null;
  
              String query1 = 
                   "SELECT id,"
                 + "       name,"
                 + "       admin_level,"
                 + "       color," 
                 + "       coalesce(ST_AsGeoJSON(ST_SimplifyPreserveTopology(ST_Intersection(ST_SetSRID("+bbox+",4326), way),"+t+"),5),'{}') geoJson"
                 + "  FROM wno_get_boundaries2('"+selected+"', '"+exportAreas+"',"+zoom+") "
                 + " order by wno_number(admin_level)"
                 + ";"
              ;
              if (debug>1) SimpleLog.write(myLog,myHeader+"query1: "+query1);

              rs = statement.executeQuery(query1);

              response.setCharacterEncoding("UTF-8");
              response.setContentType("application/json");   

              try {
                 JSONObject FeatureCollection = new JSONObject();
                 FeatureCollection.put("type","FeatureCollection");

                 JSONArray Features= new JSONArray();
                 if (debug>1) SimpleLog.write(myLog,myHeader+"starting rs-loop");
                 while (rs.next()) 
                 {
                    if (debug>2) SimpleLog.write(myLog,myHeader+"got rs-record");
	            String id 	    	= rs.getString("id");
                    String name 	= rs.getString("name");
                    String admin_level 	= rs.getString("admin_level");
                    String color       	= rs.getString("color");
                    String geoJson	= rs.getString("geoJson");                    
                    JSONObject Feature = new JSONObject();
                    Feature.put("type","feature");
                    Feature.put("id",id);
                    JSONObject Properties = new JSONObject();
                    Properties.put("name",name);
                    Properties.put("admin_level",admin_level);
                    Properties.put("color",color);
                    Feature.put("properties",Properties);
                    JSONTokener tokener = new JSONTokener(geoJson);
                    JSONObject geometry = new JSONObject(tokener);
                    Feature.put("geometry",geometry);
        	    Features.put(Feature);
                    cnt++;
                    if (debug>2) SimpleLog.write(myLog,myHeader+"   created json Feature");
                 }
                 FeatureCollection.put("features",Features);

                 json = FeatureCollection.toString(3);

                 if (cnt > 20) SimpleLog.write(myLog,myHeader+"Selected="+cnt+" by "+remoteAddr);           
              }
              catch (JSONException je) {
                 SimpleLog.write(myLog,myHeader+je.toString());
              }
           }
           catch (SQLException se) {
              SimpleLog.write(myLog,myHeader+"Couldn't connect: print out a stack trace and exit.");
              throw new ServletException(se);
           }
           finally {
              try {
                 if (conn != null) {
                    conn.close();
                    if (debug>0) SimpleLog.write(myLog,myHeader+"finally closing SQL-Connection");
                 }
              }
              catch (SQLException e) {
                 SimpleLog.write(myLog,myHeader+e.toString());
              }
           }
       
           response.getWriter().write(json);
           if (debug > 2) SimpleLog.write(myLog,myHeader+"Json="+json);
        }               
        rc = HttpServletResponse.SC_OK;		// 200           
        response.setStatus(rc);  
        if (debug>0) SimpleLog.write(myLog,myHeader+"done rc="+rc);
    }
    
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
    throws IOException, ServletException
       {
           // Übergabe an doGet(), falls Anforderung mittels POST
           doGet(request, response);
    }  
}
