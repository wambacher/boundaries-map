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

public class getChildsWithLowestAL extends HttpServlet {

    private String myName   = "getChildsWithLowestAL";
    private String myLog    = "boundaries";
    private int    debug    = 3;
    private String myHeader = "dummy";

    static String getStackTrace(Throwable aThrowable) {
       final Writer result = new StringWriter();
       final PrintWriter printWriter = new PrintWriter(result);
       aThrowable.printStackTrace(printWriter);
       return result.toString();
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {

        String database,caller,father;
          
        int     rc  = 0;
             
        caller = request.getParameter("caller");
        //is client behind something?
        String remoteAddr = request.getHeader("X-FORWARDED-FOR");  
        if (remoteAddr == null) {  
	   remoteAddr = request.getRemoteAddr();
        }

//      InetAddress inetAddress = InetAddress.getByName(remoteAddr);

        String spaces = "                                     ";
        String myHeader = remoteAddr+spaces.substring(0,16-remoteAddr.length()) 
                        + caller + " " 
                        + myName+spaces.substring(0,30-myName.length());

        String callerVersion = caller.substring(11);
        if (debug > 2) SimpleLog.write(myLog, myHeader+"callerVersion="+callerVersion); 

        database = request.getParameter("database");

        father = request.getParameter("father");

        response.setContentType("application/json");

        String childs="dummy"; 
      
        try {
           Class.forName("org.postgresql.Driver");
        } 
        catch (ClassNotFoundException cnfe) {
           SimpleLog.write(myLog, myHeader+"Couldn't find the driver!");
           cnfe.printStackTrace();
           System.exit(1);
        }
        
        Connection conn = null;
        try {
           conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/"+database,"osm", "");

           String query1;
           query1 = "select array_to_json(array_agg(id)) json"
                  + "  from boundaries"
                  + " where rpath[2]="+father
                  + "   and wno_number(level) =" 
                  + " (select min(wno_number(level)) from boundaries where rpath[2]="+father+");";

           if (debug > 2) SimpleLog.write(myLog, myHeader+"query1: "+query1);
           PreparedStatement pstmt1;
           pstmt1 = conn.prepareStatement(query1);

           ResultSet rs = pstmt1.executeQuery();

           rs.next();

           childs = rs.getString("json");

           if (debug > 1) SimpleLog.write(myLog, myHeader+childs.split(",").length+" childs: "+childs);
                  
           rs.close();
           pstmt1.close();

           response.setCharacterEncoding("UTF-8");
           response.setContentType("application/json");                
        } 
        catch (SQLException se) {
           SimpleLog.write(myLog, myHeader+"Couldn't connect: print out a stack trace and exit.");
           SimpleLog.write(myLog, myHeader+getStackTrace(se));
        }
        finally {
           try {
              if (debug > 1) SimpleLog.write(myLog, myHeader+"response="+childs); 
              response.getWriter().write(childs);   // send to client

              if (conn != null) {
                 conn.close();
              }
           }
           catch (SQLException se) {
              SimpleLog.write(myLog, myHeader+se.toString());
              SimpleLog.write(myLog, myHeader+getStackTrace(se));
           }
        }

        rc = HttpServletResponse.SC_OK;		// 200           
        
        response.setStatus(rc);  
        if (debug > 2) SimpleLog.write(myLog, myHeader+"done rc="+rc);
    }
    
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
    throws IOException, ServletException
       {
           // Übergabe an doGet(), falls Anforderung mittels POST
           doGet(request, response);
    }  
}
