package com.wno;

/* Version	Änderung 
   V2 Suche auch Relation-Id 
   V3 Suche für Permalink angepasst 
   V4 ?
   V5 Optimierungen der Speed,
      Suche nach ISO3
   V6 Tests gegen SQL-Insertion */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Array;

import java.io.*;
import java.net.*;

import java.util.regex.Pattern;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.http.HttpEntity;
import org.apache.http.impl.client.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpResponse;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

public class searchTree6 extends HttpServlet {
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {
        
        String myName = "searchTree6";
        String myLog =  "boundaries";

        HttpSession httpsession = request.getSession();
        String sessionId = httpsession.getId();
        String caller = request.getParameter("caller");

        //is client behind something?
        String remoteAddr = request.getHeader("X-FORWARDED-FOR");  
        if (remoteAddr == null) {  
	   remoteAddr = request.getRemoteAddr();
        }

//      InetAddress inetAddress = InetAddress.getByName(remoteAddr);

        String spaces = "                          ";
        String myHeader = remoteAddr+spaces.substring(0,16-remoteAddr.length()) 
                        + caller + " " 
                        + myName+spaces.substring(0,30-myName.length()); 
        
        int rc = 0;
        int debug = 1;

        SimpleLog.write(myLog,myHeader+"started: "+remoteAddr+" request=\""+request.getQueryString()+"\"");
//      String engine = request.getParameter("engine");
//      String options = request.getParameter("options");
        String search = request.getParameter("search"); // original search in Klein/Grossschreibung
        String searchFor = null;
        SimpleLog.write(myLog,myHeader+"search="+search);
        String mode = request.getParameter("mode");
        SimpleLog.write(myLog,myHeader+"mode="+mode);
        String database = request.getParameter("database");
        SimpleLog.write(myLog,myHeader+"database="+database);
        String encode = request.getParameter("encode");
        SimpleLog.write(myLog,myHeader+"encode="+encode);

        String[] s = search.split(" ");					// warum?
        SimpleLog.write(myLog,myHeader+"s[0]="+s[0]+" ("+s.length+")");	// warum?

        String json = "";
        boolean doSearch = true;

//      illegal sind | \ = : _ < > [ ] { } ^ @ ~ § $ ? * + ;
//      legal sind - & " ' # %
        Pattern illegalPattern = Pattern.compile(".*[|\\\\=:_<>\\[\\]\\{\\}\\^@~§$\\?\\*\\+;].*");
        
//      SimpleLog.write(myLog,myHeader+"illegalPattern="+illegalPattern);

        Pattern numericPattern = Pattern.compile(".*[^0-9%].*");
        
        if (illegalPattern.matcher(search).matches()) doSearch = false;
        if (search.indexOf("--") > -1)                doSearch = false;

        
        SimpleLog.write(myLog,myHeader+"doSearch="+doSearch);
        
        boolean twice = true;
        
        if (doSearch) {
           String searchCommand = "";
           
           if (search.indexOf("%") < 0) {
              searchCommand = " and (lower(value) = ? or lower(localname) = ?) order by wno_number(level),value, country";
           }
           else {
              searchCommand = " and (lower(value) like ? or lower(localname) like ?) order by wno_number(level),value, country";
           }
           
           searchFor = search.toLowerCase();
           if (search.length() == 3) {
              if (search.equals(search.toUpperCase())) {
                 searchFor = search;
                 SimpleLog.write(myLog, myHeader+search+" may be iso3 code");
                 searchCommand = " and id = (select id from countries_referenz where country = ?)";
                 twice = false;
              }
           }
           
           if (!numericPattern.matcher(search).matches()) {
              SimpleLog.write(myLog,myHeader+search+" is number");
              searchCommand = " and id = ?::bigint order by id,value, country";
              twice = false;
           }
           
           SimpleLog.write(myLog, myHeader+"searchCommand="+searchCommand+" twice="+twice);
           
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
              conn.setAutoCommit(false);
              
              String query = "select id,country,value,level,path"
                           + "  from boundaries"
                           + " where type = 'admin'"
                           + searchCommand
                           + " limit 20;"
              ;      
              if (debug > 0) SimpleLog.write(myLog,myHeader+"Query="+query+" searchCommand="+searchCommand);

              PreparedStatement pstmt1 = conn.prepareStatement(query);
              
              pstmt1.setString(1, searchFor);
              if (twice) 
              	 pstmt1.setString(2,searchFor);              
              
              SimpleLog.write(myLog,myHeader+query+" searchFor='"+searchFor+"' twice="+twice);
              
              String query2 = "select value, level"
                            + "  from boundaries"
                            + " where id = ?;" 
              ;
              
              PreparedStatement pstmt2;
              pstmt2 = conn.prepareStatement(query2); 
              
              
              SimpleLog.write(myLog,myHeader+"vor ResultSet rs = pstmt1.executeQuery();");
              ResultSet rs = pstmt1.executeQuery();
              
              response.setCharacterEncoding("UTF-8");
              response.setContentType("application/json");  
              
              JSONArray Cities= new JSONArray();
              
              int cnt = 0; 
              
              while (rs.next()) {           
              	 cnt ++;
              	 if (debug > 1) SimpleLog.write(myLog,myHeader+"cnt: "+cnt);
              	 JSONObject City = new JSONObject();
              	 
              	 City.put("id",rs.getString("id"));              
              	 City.put("country",rs.getString("country"));
              	 City.put("name",rs.getString("value"));
              	 City.put("level",rs.getString("level"));
              	 if (debug > 1) SimpleLog.write(myLog,myHeader+"level: "+rs.getString("level"));
              	 Array path = rs.getArray("path");              
              	 if (debug > 1) SimpleLog.write(myLog,myHeader+"nach getArray(\"path\")");
              	 
              	 Object obj = path.getArray();
              	 Long [] xpath = (Long []) obj; 
              	 String temp = "";
              	 for (int i=0;i<xpath.length;i++) {
              	    if (i > 0) temp = temp+","; 
                    temp = temp + xpath[i];
                 }
                 City.put("path",temp); 
                 if (debug > 0) SimpleLog.write(myLog,myHeader+"path: "+temp);
                 
                 if (mode.equals("full")) { // return is_in für normale Suche
                    String is_in = "";
                    for (int i=1;i<xpath.length-1;i++) {  // skip 0
                       pstmt2.setLong(1, xpath[i]);
                       if (debug > 1) SimpleLog.write(myLog,myHeader+"query2: "+query2+" ("+xpath[i]+")");
                       ResultSet rs2 = pstmt2.executeQuery();
                       rs2.next();
                       String tmp = rs2.getString("value")+" ("+rs2.getString("level")+")";
                       is_in = is_in+tmp;
                       if (i<xpath.length-2) is_in=is_in+", ";
                    }
                    City.put("is_in",is_in);
                    if (debug > 0) SimpleLog.write(myLog,myHeader+"is_in: "+is_in);
                 }
                 Cities.put(City); 
              }
              if (cnt > 0) {
              	 
              	 json = Cities.toString(3);
              	 
              	 rc = HttpServletResponse.SC_OK;		 // 200     
              }
              else {
              	 json="{}";
              	 rc = HttpServletResponse.SC_NO_CONTENT;	// 204
              }
           } 
           catch (JSONException je) {
              SimpleLog.write(myLog,myHeader+je.toString());
           }
           catch (SQLException se) {
              SimpleLog.write(myLog,myHeader+"Couldn't connect: print out a stack trace and exit.");
              throw new ServletException(se);
           }
           finally {
              try {
              	 if (conn != null) {
              	    conn.close();
              	    if (debug > 1) SimpleLog.write(myLog,myHeader+"closing SQL-Connection");
              	 }
              }  
              catch (SQLException e) {
              	 SimpleLog.write(myLog,myHeader+e.toString());
              }
           } 
           if (debug > 1) SimpleLog.write(myLog,myHeader+"Cities="+json);
           response.getWriter().write(json);
           response.setStatus(rc);  
           if (debug > 1) SimpleLog.write(myLog,myHeader+"done rc="+rc);
        }
        else {
           response.getWriter().write("{}");
           rc = HttpServletResponse.SC_NO_CONTENT;	// 204
           if (debug > 1) SimpleLog.write(myLog,myHeader+"done rc="+rc);           
           response.setStatus(rc);
        }
    } 

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
    throws IOException, ServletException
       {
           // Übergabe an doGet(), falls Anforderung mittels POST
           doGet(request, response);
       }  
}
