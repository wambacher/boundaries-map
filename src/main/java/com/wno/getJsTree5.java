package com.wno;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Array;

import java.io.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;

import java.util.*;
//import java.util.logging.Logger;
import java.text.*;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

// V2 alle Länder
// V3 log to file
// V4 für 4.0 lade keine falschen AL2
// V5 

public class getJsTree5 extends HttpServlet {

//  private static final Logger log = Logger.getLogger( "org.eclipse.jetty" );

    private String myName   = "getjsTree5";
    private String myLog    = "boundaries";
    private String dbserver = "server3";

    static String getStackTrace(Throwable aThrowable) {
       final Writer result = new StringWriter();
       final PrintWriter printWriter = new PrintWriter(result);
       aThrowable.printStackTrace(printWriter);
       return result.toString();
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {

        String database,caller;
          
        int     rc  = 0;
        int     nextLevel;
        int     debug = 2;
        
        String geoJsonString;
     
        //is client behind something?
        String remoteAddr = request.getHeader("X-FORWARDED-FOR");  
        if (remoteAddr == null) {  
	   remoteAddr = request.getRemoteAddr();
        }

//      InetAddress inetAddress = InetAddress.getByName(remoteAddr);
   
        database = request.getParameter("database");
        caller = request.getParameter("caller");

        String spaces = "                                      ";
        String myHeader = remoteAddr+spaces.substring(0,16-remoteAddr.length()) 
                        + caller + " " 
                        + myName+spaces.substring(0,30-myName.length()); 
/* unbenutzt
        Cookie[] cookies = request.getCookies();
        for (int i=0;i<cookies.length;i++) {
           if (cookies[i].getName().substring(0,3) != "lca") 
              if (debug>1) SimpleLog.write(myLog,myHeader+"cookie "+cookies[i].getName()+": "+cookies[i].getValue());
        }
*/
        if (debug>0) {
           SimpleLog.write(myLog,myHeader+"started: requestURI=\""+request.getRequestURI()+"\"");
           SimpleLog.write(myLog,myHeader+"started: request=\""+request.getQueryString()+"\"");
           SimpleLog.write(myLog,myHeader+"remoteAddr="+remoteAddr);
        }

        String parent = request.getParameter("parent");
//      int adminLevel = Integer.parseInt(request.getParameter("admin_level"));

//      if (parent.equals("#")) parent= "0";  // unnötig?
        if (debug>1) SimpleLog.write(myLog,myHeader+"parent="+parent);
        Long id = Long.parseLong(parent);

        response.setContentType("application/json");
        String json=""; 
      
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
           conn = DriverManager.getConnection("jdbc:postgresql://"+dbserver+":5432/"+database,"osm", "");

           String query1;
           query1 = "select path,"
                  + "       country,"
                  + "       array_upper(path,1) size,"
                  + "       baseonly"
                  + "  from boundaries"           
                  + " where id = ?;";

           PreparedStatement pstmt1;
           pstmt1 = conn.prepareStatement(query1);
           pstmt1.setLong(1, id);
           if (debug>1) SimpleLog.write(myLog,myHeader+"query1: "+query1+" ("+id+")");

           ResultSet rs = pstmt1.executeQuery();
           rs.next();

           Array path = rs.getArray("path");
           int ps = rs.getInt("size");
           int level = rs.getInt("size");
           boolean baseonly = rs.getBoolean("baseonly");
           if (debug>1) SimpleLog.write(myLog,myHeader+"path from db: "+path+" ("+level+") baseonly: "+baseonly);
           rs.close();
           pstmt1.close();

           String query2;
           if (debug>1) SimpleLog.write(myLog,myHeader+"path.size = "+ps);

           if (ps==1) {
              query2 = "select id,"
                    + "        country,"
                    + "        value \"name\","
                    + "        coalesce(localname,'') localname,"
                    + "        level admin_level,"
                    + "        path,"
                    + "        ST_AsGeoJSON(bbox,5) bbox,"
                    + "        baseonly,"
                    + "        coalesce(ts::text||'+01',' ') ts" 
                    + "  from boundaries"           
                    + " where path @> ?"			// @>   contains
                    + "   and array_length(path,1) = ?"
                    + "   and level = '2'"
                    + " order by name;"              // notwendig? JA!!!
              ;
           }
           else {
              query2 = "select id,"
                    + "        country,"
                    + "        value \"name\","
                    + "        coalesce(localname,'') localname,"
                    + "        level admin_level,"
                    + "        path,"
                    + "        ST_AsGeoJSON(bbox,5) bbox,"
                    + "        baseonly,"
                    + "        coalesce(ts::text||'+01',' ') ts" 
                    + "  from boundaries"           
                    + " where path @> ?"			// @>   contains
                    + "   and array_length(path,1) = ?"
                    + " order by name;"              // notwendig? JA!!!
              ;
           }
//         query2 = query2 + "\n limit 20"; 


           PreparedStatement pstmt2;
           pstmt2 = conn.prepareStatement(query2); 
 
           nextLevel = level+1;
           if (debug>1) SimpleLog.write(myLog,myHeader+"path: "+path+" array_length(path,1)="+nextLevel);

           pstmt2.setArray(1, path);
           pstmt2.setInt(  2, nextLevel);
                     
           if (debug>1) SimpleLog.write(myLog,myHeader+"query2: "+query2+" ("+path+" "+nextLevel+")");

           rs = pstmt2.executeQuery();
           
           response.setCharacterEncoding("UTF-8");
           response.setContentType("application/json");         
 
           JSONArray borders = new JSONArray();
           JSONObject border = new JSONObject();

           JSONArray children = new JSONArray(); 
    
           while (rs.next()) 
           {
              id                 = rs.getLong("id");
    	      String name 	 = rs.getString("name"); 
              String localname   = rs.getString("localname");
              int admin_level    = rs.getInt("admin_level");
              Array mypath       = rs.getArray("path");
//            if (debug>1) SimpleLog.write(myLog,myHeader+"mypath from db: "+mypath);
              String geoJson     = rs.getString("bbox");
              JSONObject bbox    = new JSONObject(new JSONTokener(geoJson));
              String country     = rs.getString("country");
              String ts          = rs.getString("ts");

              JSONObject child   = new JSONObject();
              child.put("id",id);
	      child.put("text",name+" ("+admin_level+")");  
            
              JSONObject data = new JSONObject();
               data.put("admin_level",admin_level);
               data.put("bbox",bbox);
              child.put("data",data);

              JSONObject state = new JSONObject();
               state.put("opened",false);
               state.put("disabled",false);
               state.put("selected",false);

               baseonly = rs.getBoolean("baseonly");

               state.put("loaded",baseonly); 
               child.put("state",state);

               JSONObject a_attr = new JSONObject();

               String attr = country+" "+id+" "+ name+" ";
               if (localname.length() > 0) 
                  attr = attr + "("+localname+") ";
               attr = attr + ts; 
               a_attr.put("title",attr);    // für hover im tree
               child.put("a_attr",a_attr);
//             if (debug>1) SimpleLog.write(myLog,myHeader+"country: "+country+" path="+path+" baseonly="+baseonly);
              children.put(child); 
                      
           } 
           json = children.toString(3);
        } 
           catch (JSONException je) {
           SimpleLog.write(myLog,myHeader+"JSON Error: "+je.toString());
        } 
        catch (SQLException se) {
           SimpleLog.write(myLog,myHeader+"Couldn't connect: print out a stack trace and exit.");
           SimpleLog.write(myLog,myHeader+getStackTrace(se));
        }
        finally {
           try {
              if (debug>2) SimpleLog.write(myLog,myHeader+"borders="+json); 
              response.getWriter().write(json); 

              if (conn != null) {
                 conn.close();
              }
           }
           catch (SQLException se) {
              SimpleLog.write(myLog,myHeader+se.toString());
              SimpleLog.write(myLog,myHeader+getStackTrace(se));
           }
        }

        rc = HttpServletResponse.SC_OK;		// 200           
        
        response.setStatus(rc);  
//      if (debug>1) SimpleLog.write(myLog,myHeader+"done rc="+rc);
    }
        
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
    throws IOException, ServletException
       {
           // Übergabe an doGet(), falls Anforderung mittels POST
           doGet(request, response);
    }  
}
