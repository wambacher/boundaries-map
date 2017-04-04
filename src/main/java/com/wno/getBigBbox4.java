package com.wno;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Array;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import java.util.*;
import java.text.*;
import java.util.Locale;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

// V3 log to file
// V4 use boundaries, limit to 50.

public class getBigBbox4 extends HttpServlet {

    private String myName   = "getBigBbox4";
    private String myLog    = "boundaries";
    private String myHeader = "";

    static String getStackTrace(Throwable aThrowable) {
       final Writer result = new StringWriter();
       final PrintWriter printWriter = new PrintWriter(result);
       aThrowable.printStackTrace(printWriter);
       return result.toString();
    }

    static String currentTime() {
       Date today;
       String output;
       SimpleDateFormat formatter;
       String pattern = "HH:mm:ss:SSS";
       formatter = new SimpleDateFormat(pattern);
       today = new Date();
       output = formatter.format(today);

       return output;
    }
    
/* ********************************** sendError ************************************ */

   private void sendError(HttpServletResponse response, String errorText, int errorCode) 
      throws ServletException, IOException{
      	 
      SimpleLog.write(myLog, myHeader+errorText+" rc="+errorCode);
      
      response.sendError(errorCode, errorText);
      return; 
   }
    

/* **************************************************************************** */

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {

        String database,caller;
          
        int     rc  = 0;
        int     nextLevel;
        int     debug=3;
        
        String geoJsonString;

        if (debug > 2) {
           SimpleLog.write(myLog, "starting "+myName);
        }
        caller = request.getParameter("caller");

        //is client behind something?
        String remoteAddr = request.getHeader("X-FORWARDED-FOR");  
        if (remoteAddr == null) {  
	       remoteAddr = request.getRemoteAddr();
        }

//      InetAddress inetAddress = InetAddress.getByName(remoteAddr);

        String spaces = "                                     ";
        myHeader = remoteAddr+spaces.substring(0,16-remoteAddr.length()) 
                        + caller + " " 
                        + myName+spaces.substring(0,30-myName.length()); 

        if (debug > 2) {
           SimpleLog.write(myLog, myHeader+"vor OsmOAuth.getOAuthSessionCookie()");
        }
  
        String OAuthSessionCookie = OsmOAuth.getOAuthSessionCookie(myLog, myHeader, request, 2);

        if (OAuthSessionCookie.equals("undefined")) {
//         if (remoteAddr.equals("192.168.188.20")) {
              SimpleLog.write(myLog,myHeader+"sollte killen");
              rc=401;
              response.setStatus(rc);  
              if (debug > 1) SimpleLog.write(myLog,myHeader+"done rc="+rc);
              return;
//         }
        }

        myHeader = myHeader + OAuthSessionCookie + " ";

        if (debug > 1) {  
           SimpleLog.write(myLog,myHeader+"started: request=\""+request.getQueryString()+"\"");
           SimpleLog.write(myLog,myHeader+"referer="+request.getHeader("referer"));
           SimpleLog.write(myLog,myHeader+"at "+currentTime()+" started");
        }

        database = request.getParameter("database");

        String selected = request.getParameter("selected");
        
        int nselected= selected.split(",").length+1;
        SimpleLog.write(myLog,myHeader+nselected+" boundaries selected");
        if (nselected >= 50) {
           SimpleLog.write(myLog,myHeader+"too many boundaries selected: "+nselected);
           sendError(response,"Too many boundaries seleced: "+nselected,400);
           return;
        }        
        
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
           conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/"+database,"osm", "");
           
           String queryCountries = "select count(*) from countries where id in("+selected+");";
           PreparedStatement pstmtc=conn.prepareStatement(queryCountries);
           ResultSet rsC = pstmtc.executeQuery();
           rsC.next();

           int Ccount = rsC.getInt("count");
           rsC.close();
           pstmtc.close();
           if (Ccount > 50) {
              conn.close();
              SimpleLog.write(myLog,myHeader+"too many countries selected: "+nselected);
              sendError(response,"Too many countries seleced: "+Ccount,400);
              return;
           }
           	  
           String query1;
           query1 = "select ST_AsGeoJSON(ST_SetSRID(ST_Extent(bbox),4326),5) bbbox"
                  + "  from boundaries"           
                  + " where id in("+selected+");";

           PreparedStatement pstmt1;
           pstmt1 = conn.prepareStatement(query1);

           if (debug > 2) {
              String[] s = selected.split(",");
              SimpleLog.write(myLog,myHeader+"query1: "+query1+" ("+selected+") length="+s.length);
 //           SimpleLog.write(myLog,myHeader+"query1: "+query1);
           }

           ResultSet rs = pstmt1.executeQuery();
           rs.next();

           String jBbbox = rs.getString("bbbox");

//         SimpleLog.write(myLog,myHeader+"BigBbox: "+jBbbox);
           rs.close();
           pstmt1.close();

           response.setCharacterEncoding("UTF-8");
           response.setContentType("application/json");   
      
           JSONObject Bbbox = new JSONObject(new JSONTokener(jBbbox));

           json = Bbbox.toString(3);
        } 
           catch (JSONException je) {
           SimpleLog.write(myLog,myHeader+"JSON Error: "+je.toString());
        } 
        catch (SQLException se) {
           SimpleLog.write(myLog,myHeader+"Couldn't connect: print out a stack trace and exit.");
           SimpleLog.write(myLog,myHeader+""+getStackTrace(se));
        }
        finally {
           try {
//            SimpleLog.write(myLog,myHeader+"BigBbox="+json); 
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
        if (debug > 1) SimpleLog.write(myLog,myHeader+"at "+currentTime()+" done rc="+rc);
    }
        
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
    throws IOException, ServletException
       {
           // Übergabe an doGet(), falls Anforderung mittels POST
           doGet(request, response);
    }  
}
