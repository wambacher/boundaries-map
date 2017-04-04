package com.wno;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Array;
import java.sql.Timestamp;

import org.postgis.*;

import java.io.*;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Closeable;
import java.io.File;
import java.io.Writer;
import java.io.FileWriter;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.IOException;

import java.net.InetAddress; 

import java.nio.charset.Charset;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.http.Cookie;

import java.util.*;
import java.util.Date;
import java.lang.Math;
import org.apache.commons.compress.archivers.zip.*;

import java.util.UUID;
import java.lang.Object.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.*;

import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Comment;
import org.w3c.dom.CDATASection;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.DOMImplementation;

import org.apache.batik.dom.svg.SVGDOMImplementation;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource; 
import javax.xml.transform.stream.StreamResult;

/* Version  1 Erzeuge Json
   Version  2 Erzeuge shapes
   Version  3 UTF (ruht)
   Version  4 Export SVG
   Version  5 Verbessertes SVG
   Version  6 SVG Hull als extra Gruppe
   Version  6.1 Tempfiles für Shapes -> /data/tmp/boundaries
   Version  6.2 More fields in Shapes
   version  7 All Data from collected_admin_boundaries
   version  8 Variable Buffer (from slider)
   version  9 Union to one MP
   version 10 Union auch für Shapes !
   version 11 Erzeuge GeoJson
   version 12 Looging to file
   version 13 Export full subtree
              Log bytes to RRD
   version 14 check OAuth
   version 15 Flat Export, Accounting
   version 16 Export single/level/flat
   version 17 use boundaries
              switch water/land
   version 18 auch für API verwenden
   version 19 caching der Exports
              .bpoly -> .poly für beide
              return errors as payload
              more tags for SHP
*/

// http://examples.javacodegeeks.com/core-java/json/jackson/jackson-streaming-api-to-read-and-write-json-example/
// http://wiki.fasterxml.com/JacksonInFiveMinutes

// http://fasterxml.github.io/jackson-core/javadoc/2.3.0/index.html

// http://postgis.refractions.net/documentation/javadoc/org/postgis/

// TODO
// Prüfen, ob die Abfrage wirklich von der Karte kommt.
// Timestamp im Output
//

public class exportBoundaries19 extends HttpServlet {

   private int DEFAULT_BUFFER_SIZE = 10240;
   private String myBaseName = "exportBoundaries";
   private String myVersion = "19";
   private String myName = myBaseName+myVersion;
   private String myLog  = "boundaries";
   private int    debug = 2;
   private String myHeader = "dummy";
   private Connection conn = null;
   private ResultSet BaseRS = null;
   private String remoteAddr = "dummy";
   private String user = "dummy";
   private int userid = -1;
   private double bufferDist = 0l;
   private boolean union = false;   
   private String exportFormat = "shp";
   private String exportLayout = "levels";
   private String exportAreas  = "water";
   private boolean subtree = false;
   private int from_al = 0;
   private int to_al   = 99;
   private boolean planet = false;
   private String database = "dummy";
   private String selected = "dummy";
   private String tmpTable = "dummy";
   private static final String OAUTH_SESSION_COOKIE = "osm_boundaries_oauth_session";
   private int indent;
   private boolean api = false;
   private String timestamp = "dummy"; // database timestamp
   private String bCache = "/data1/osm/cache/boundaries/";
   
   private int dpCnt = 999; 
   
   private static <T> T coalesce(T ...items) {
      for(T i : items) if(i != null) return i;
      return null;
   }
   
   private String getStackTrace(Throwable aThrowable) {
      final Writer result = new StringWriter();
      final PrintWriter printWriter = new PrintWriter(result);
      aThrowable.printStackTrace(printWriter);
      return result.toString();
   };

   private String getDateTime() {
      DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      Date date = new Date();
      return dateFormat.format(date);
   }

   private class DumpedPoint {
      int   polygon;
      int   ring;
      double    x;
      double    y;
   }

   private DumpedPoint getNextPoint(ResultSet rs) 
      throws SQLException{

      DumpedPoint pt = new DumpedPoint();  
      if (rs.next()) {
         pt.polygon = rs.getInt("polygon");   
         pt.ring    = rs.getInt("ring");      
         pt.x       = rs.getDouble("x");
         pt.y       = rs.getDouble("y");
         dpCnt = dpCnt+1;
//       SimpleLog.write(myLog, myHeader+"DumpedPoint: "+dpCnt+"= "+pt.polygon+" "+pt.ring+" "+pt.x+" "+pt.y);
         return pt;
      }       
      else
         return null;
   }

   private double BufferParams(int al) {
 
      double BufferRadius[]  = { 0.0000,0.0000,0.010,0.005,0.002,0.0015,0.0010,0.00075,0.0005,0.0003,0.00010,0.00011,0.0004,0.0002};
//                                0      1      2     3     4     5      6     7        8      9      10      11      12     13
      double Simplify[]      = { 0.0000,0.0000,0.005,0.003,0.001,0.001, 0.0005,0.00030,0.0002,0.0001,0.00003,0.00011,0.0004,0.0002};

      SimpleLog.write("BufferParams "+al+" --> "+BufferRadius[al]+"/"+Simplify[al]);
      return BufferRadius[al]*100000+Simplify[al];
   }

   private double BufferParams2(float km) {
 
      double BufferRadius[]  =
         { 0.0000,0.0000,0.010,0.005,0.002,0.0015,0.0010,0.00075,0.0005,0.0003,0.00010,0.00011,0.0004,0.0002};
//               0     1      2     3     4     5      6     7        8      9      10      11      12     13

      return BufferRadius[8];
   }

   private void writeSvgPath(JsonGenerator jsonGenerator, Point point) 
      throws ServletException, IOException{

      jsonGenerator.writeStartArray();
      jsonGenerator.writeNumber(point.getX());
      jsonGenerator.writeNumber(point.getY());
      jsonGenerator.writeEndArray();
   };

/*   private void getAction() {

   }; */

   private void log2RRD(String rrd, String type, long bytes, int debug) {
      if (debug > 0) SimpleLog.write(myLog, myHeader+"adding "+bytes+" bytes to rrd "+rrd+"_"+type+".rrd");

      String data = " N:"+bytes;
      String text = "dummy";

      String[] cmd = {"/usr/bin/rrdtool","updatev","/data/osm/rrd/boundaries_"+type+".rrd", data};
      if (debug > 0) SimpleLog.write(myLog, myHeader+"cmd="+cmd[0]+" "+cmd[1]+" "+cmd[2]+" "+cmd[3]);  
         try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader res = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((text = res.readLine()) != null) {
               SimpleLog.write(myLog, myHeader+"rrdtool update: "+text);
            }
            res.close();
         }
         catch (IOException e) {
             e.printStackTrace();
         }
   }      
   
//            logUsage(myBaseName, userid, user, "export", exportFormat, bufferDist, union, exportLayout,
//                     exportAreas, total, remoteAddr, selected, subtree, ,api, 0);

private void logUsage(String application, 
                         int userid,
                         String username, 
                         String info, 
                         String format, 
                         double bufferDist, 
                         boolean union, 
                         String layout, 
                         String areas, 
                         long bytes, 
                         String ip, 
                         String selected,
                         boolean subtree,
                         String api,
                         int debug) 
      throws SQLException {
         
      if (debug > 0) SimpleLog.write(myLog, myHeader+application);

      String qInsert = "insert into usage(application, userid, username, ts, info, bytes, ip, \"format\", buffer, "
                     + "\"union\", layout, areas, selected, subtree, api) "
                     + "           values(?,?,?,current_timestamp(0),?,?,?,?,?,?,?,?,?,?,?);";

      if (debug > 1) SimpleLog.write(myLog, myHeader+qInsert);      
      
      PreparedStatement pInsert = conn.prepareStatement(qInsert);

      pInsert.setString(   1, application);
      pInsert.setInt(      2, userid);
      pInsert.setString(   3, user);
      pInsert.setString(   4, info);      
      pInsert.setLong(     5, bytes);
      pInsert.setString(   6, ip);
      pInsert.setString(   7, format);
      pInsert.setDouble(   8, bufferDist);
      pInsert.setBoolean(  9, union);
      pInsert.setString(  10, layout);
      pInsert.setString(  11, areas);
      pInsert.setString(  12, selected);
      pInsert.setBoolean( 13, subtree);
      pInsert.setString(  14, api);

      if (debug > 1) SimpleLog.write(myLog, myHeader+"doing "+qInsert);
      int done = pInsert.executeUpdate();
      pInsert.close();      
    }
    
// **************************************************************************************************

    private void displayHttpResponse(HttpServletResponse response) {
       SimpleLog.write(myLog, myHeader+"displayHttpResponse BufferSize="+response.getBufferSize());
       SimpleLog.write(myLog, myHeader+"displayHttpResponse CharacterEncoding="+response.getCharacterEncoding());
       SimpleLog.write(myLog, myHeader+"displayHttpResponse ContentType="+response.getContentType());
       SimpleLog.write(myLog, myHeader+"displayHttpResponse Locale="+response.getLocale());
    }
    
// *********************** createbCacheFilename ****************************************************
  
   private String createbCacheFilename(String exportFormat, String exportLayout, String exportAreas, String timestamp) {

      String myName = "createbCacheFilename()";
      String fileUUID = UUID.randomUUID().toString();

      String suffix = "";
      switch(exportFormat) {
         case "json":
            suffix = ".geojson.zip";
            break;
         case "shp":
            suffix = ".shp.zip";
            break; 
         case "svg":
            suffix = ".svg";
            break;
      }

      String bCacheFilename = exportFormat+"/"+exportLayout+"/"+exportAreas+"/"
                            + "export_"+timestamp.replace(' ','_')+"_"+fileUUID+suffix;
      if (debug > 0) SimpleLog.write(myLog, myHeader+myName+" bCacheFilename="+bCacheFilename);

      return bCacheFilename;
   }
   
// *********************** insertBCache ****************************************************

   private void insertBCache(String username, String ts, String format, boolean union, String layout,
                             String areas, boolean subtree, Integer from_al, Integer to_al,
                             String filename, String selected,
                             String baseName) 
      throws SQLException {
         
      String myName = "insertBcache()";
             
      if (debug > 0) SimpleLog.write(myLog, myHeader+myName);
      
      // wieso werden hier mehrere selected erlaubt ?????????????????????????

      boolean swap = true;
      int j = 0;

      String[] ids = selected.split(",");
      
      int[] nids = new int[ids.length];
      for (j=0; j<ids.length; j++) {
         nids[j] = Integer.parseInt(ids[j]);
      }

      while (swap) {
         swap = false;
         j++;
         for (int i = 0; i < ids.length - 1; i++) {
            if (nids[i] > nids[i+1]) {
                int tmp = nids[i];
                nids[i] = nids[i + 1];
                nids[i + 1] = tmp;
                swap = true;
            }
         }
      }
      selected = "";
      for (j=0; j<nids.length; j++) {
//       SimpleLog.write(myLog, myHeader+nids[j]);
         selected = selected + nids[j] + ",";
      } 
      selected = selected.substring(0,selected.length()-1);
//    SimpleLog.write(myLog, myHeader+">"+selected+"<");
         
      String qInsert = "insert into bcache(dtm, username, ts, format, \"union\", layout, areas,"
                     + "subtree, from_al, to_al, basename, filename, key, used)"
                     + "           values(now(),?,?,?,?,?,?,?,?,?,?,?,?,0);";

      if (debug > 0) SimpleLog.write(myLog, myHeader+myName+" "+qInsert);      
      
      PreparedStatement pInsert = conn.prepareStatement(qInsert);

      pInsert.setString(  1, username);
      pInsert.setString(  2, ts);
      pInsert.setString(  3, format);
      pInsert.setBoolean( 4, union);
      pInsert.setString(  5, layout);
      pInsert.setString(  6, areas);
      pInsert.setBoolean( 7, subtree);      
      pInsert.setInt(     8, from_al);
      pInsert.setInt(     9, to_al);
      pInsert.setString( 10, baseName);   
      pInsert.setString( 11, filename);
      pInsert.setString( 12, selected);      

      if (debug > 0) SimpleLog.write(myLog, myHeader+myName+" doing "+qInsert+" ("+username+","
                                                +ts+","
                                                +format+","
                                                +union+","
                                                +layout+","
                                                +areas+","
                                                +subtree+","
                                                +from_al+","
                                                +to_al+","    
                                                +baseName+","
                                                +filename+","
                                                +selected+")");
      int done = pInsert.executeUpdate();
    }
    
// *********************** checkBCache ****************************************************

   private String checkBCache(String timestamp, String format, boolean union, String layout, String areas,
                              boolean subtree, Integer from_al, Integer to_al, String selected) 
      throws SQLException {
             
      if (debug > 0) SimpleLog.write(myLog, myHeader+"checkBCache");
      
      // wieso werden hier mehrere selected erlaubt ?????????????????????????
      
      boolean swap = true;
      int j = 0;

      String[] ids = selected.split(",");
      
      int[] nids = new int[ids.length];
      for (j=0; j<ids.length; j++) {
         nids[j] = Integer.parseInt(ids[j]);
      }

      while (swap) {
         swap = false;
         j++;
         for (int i = 0; i < ids.length - 1; i++) {
            if (nids[i] > nids[i+1]) {
                int tmp = nids[i];
                nids[i] = nids[i + 1];
                nids[i + 1] = tmp;
                swap = true;
            }
         }
      }
      selected = "";
      for (j=0; j<nids.length; j++) {
//       SimpleLog.write(myLog, myHeader+nids[j]);
         selected = selected + nids[j] + ",";
      } 
      selected = selected.substring(0,selected.length()-1);
//    SimpleLog.write(myLog, myHeader+">"+selected+"<");
         
      String qQuery = "select * from bcache where format=? and \"union\"=? and layout=?"
                    + "and areas=? and subtree=? and from_al=? and to_al=? and ts=? and key=?";

      if (debug > 1) SimpleLog.write(myLog, myHeader+qQuery);      
      
      PreparedStatement pQuery = conn.prepareStatement(qQuery);

      pQuery.setString( 1, format);
      pQuery.setBoolean(2, union);
      pQuery.setString( 3, layout);
      pQuery.setString( 4, areas);
      pQuery.setBoolean(5, subtree);
      pQuery.setInt(    6, from_al);
      pQuery.setInt(    7, to_al);
      pQuery.setString( 8, timestamp);
      pQuery.setString( 9, selected);      

      if (debug > 1) SimpleLog.write(myLog, myHeader+"doing "+qQuery+" ("+format+", "+union+", "+layout+", "
                                    +areas+","+selected+")");
      ResultSet rs = pQuery.executeQuery();

      while (rs.next()) {
         String basename = rs.getString("basename");
         String bCacheFilename = rs.getString("filename");
         rs.close();
         return basename+"#"+bCacheFilename;
      }
      rs.close();
      return null;
   }
   
// *********************** updateBCache ****************************************************

   private void updateBCache(String ts, String format, boolean union, String layout,
                             String areas, boolean subtree, Integer from_al, Integer to_al,
                             String filename, String selected,
                             String baseName,
                             int debug) 
      throws SQLException {
             
      if (debug > 0) SimpleLog.write(myLog, myHeader+"updateBCache");
      
      // wieso werden hier mehrere selected erlaubt ?????????????????????????

      boolean swap = true;
      int j = 0;

      String[] ids = selected.split(",");
      
      int[] nids = new int[ids.length];
      for (j=0; j<ids.length; j++) {
         nids[j] = Integer.parseInt(ids[j]);
      }

      while (swap) {
         swap = false;
         j++;
         for (int i = 0; i < ids.length - 1; i++) {
            if (nids[i] > nids[i+1]) {
                int tmp = nids[i];
                nids[i] = nids[i + 1];
                nids[i + 1] = tmp;
                swap = true;
            }
         }
      }
      selected = "";
      for (j=0; j<nids.length; j++) {
//       SimpleLog.write(myLog, myHeader+nids[j]);
         selected = selected + nids[j] + ",";
      } 
      selected = selected.substring(0,selected.length()-1);
//    SimpleLog.write(myLog, myHeader+">"+selected+"<");
         
      String qUpdate = "update bcache set used = used +1"
                     + " where ts=?" 
                     + "   and format=?"
                     + "   and \"union\"=?"
                     + "   and layout=?"
                     + "   and areas=?"
                     + "   and subtree=?"
                     + "   and from_al=?"
                     + "   and to_al=?"
                     + "   and basename=?"
                     + "   and filename=?"
                     + "   and key=?;";

      if (debug > 1) SimpleLog.write(myLog, myHeader+qUpdate);      
      
      PreparedStatement pUpdate = conn.prepareStatement(qUpdate);

      pUpdate.setString(  1, ts);
      pUpdate.setString(  2, format);
      pUpdate.setBoolean( 3, union);
      pUpdate.setString(  4, layout);
      pUpdate.setString(  5, areas);
      pUpdate.setBoolean( 6, subtree);      
      pUpdate.setInt(     7, from_al);
      pUpdate.setInt(     8, to_al);
      pUpdate.setString(  9, baseName);   
      pUpdate.setString( 10, filename);
      pUpdate.setString( 11, selected);      

      if (debug > 0) SimpleLog.write(myLog, myHeader+"doing "+qUpdate+" ("
                                                    +ts+","
                                                    +format+","
                                                    +union+","
                                                    +layout+","
                                                    +areas+","
                                                    +subtree+","
                                                    +from_al+","
                                                    +to_al+","
                                                    +baseName+","
                                                    +filename+","
                                                    +selected+")");
      
      if (debug > 0) SimpleLog.write(myLog, myHeader+"doing pUpdate");
      if (debug > 0) SimpleLog.write(myLog, myHeader+"doing: "+pUpdate);
      
      int done = pUpdate.executeUpdate();
      if (debug > 0) SimpleLog.write(myLog, myHeader+"did pUpdate");      
    }
    
// *************************************** getCountryFromISO3 *******************************
   
   private String getCountryFromISO3(String ISO3) 
      throws SQLException {
             
      if (debug > 0) SimpleLog.write(myLog, myHeader+"getCountryFromISO3 ISO3="+ISO3);
      
      String qQuery = "select id from boundaries where country=? and type='admin' and level='2';";
      if (debug > 0) SimpleLog.write(myLog, myHeader+qQuery);      
      
      PreparedStatement pQuery = conn.prepareStatement(qQuery);

      pQuery.setString( 1, ISO3.toUpperCase());

      if (debug > 0) SimpleLog.write(myLog, myHeader+"doing "+qQuery+" ("+ISO3.toUpperCase()+")");
      ResultSet rs = pQuery.executeQuery();

      while (rs.next()) {
         String id = rs.getString("id");
         if (debug > 0) SimpleLog.write(myLog, myHeader+"getCountryFromISO3 selected="+id);
         rs.close();
         return id;
      }
      rs.close();
      return null;
   }
   
// ********************************** getBaseName ************************************/

   private String getBaseName(String name, String selected, boolean subtree, int from_al, int to_al, boolean planet) 
      throws SQLException {
             
      if (debug > 0) SimpleLog.write(myLog, myHeader+"getBaseName: "+name+", "
                                                    +"selected: "+selected+", "
                                                    +subtree+", "
                                                    +from_al+", "
                                                    +to_al+", "
                                                    +planet);
      
      if (!subtree & !planet)
         return(name.replace(' ','_').replace('/','_'));
      else if (planet)
         return "World";
      else {
         String qQuery = "select value, level from boundaries where id=?";
         if (debug > 0) SimpleLog.write(myLog, myHeader+qQuery);      
      
         PreparedStatement pQuery = conn.prepareStatement(qQuery);

         pQuery.setLong( 1, Long.parseLong(selected));
  
         if (debug > 0) SimpleLog.write(myLog, myHeader+"doing "+qQuery+" ("+selected+")");
         ResultSet rs = pQuery.executeQuery();

         while (rs.next()) {
            String rName = rs.getString("value");
            String rLevel = rs.getString("level");

            if (debug > 0) SimpleLog.write(myLog, myHeader+"name="+rName+" level="+rLevel);
            rs.close();
            if (from_al >= 0) {
               return rName.replace(' ','_').replace('/','_')+"_AL"+/*Math.max(from_al,2)*/rLevel+"-AL"+to_al;
            }
            else {
               return rName.replace(' ','_').replace('/','_');
            }              
         }
         rs.close();
         return null;
      }
   }
   
// ********************************** sendError ***************************************

   private void sendError(HttpServletResponse response, String errorText, int errorCode) 
      throws ServletException, IOException{
         
      SimpleLog.write(myLog, myHeader+errorText+" rc="+errorCode);
      
      response.sendError(400, errorText);
      return; 
   }
   
// ********************************** checkBlocked *************************************

   private boolean checkBlocked(int userid, String user, int debug) 
      throws ServletException, IOException{
         
      String myName = "checkBlocked()"; 
      boolean active = false;
      boolean doit;
      
      if (debug > 1) SimpleLog.write(myLog, myHeader+myName+" userid="+userid+" user="+user);
               
      String qQuery = " select active from oauth where userid=?";
      if (debug > 0) SimpleLog.write(myLog, myHeader+myName+qQuery+" ("+userid+")");      
      try {
         PreparedStatement pQuery = conn.prepareStatement(qQuery);
   
         pQuery.setInt( 1, userid);
     
         ResultSet rs = pQuery.executeQuery();
         
         while (rs.next()) {
            active = rs.getBoolean("active");
         }
         rs.close();
         pQuery.close();
      }
      catch (SQLException se) {
         SimpleLog.write(myLog, myHeader+"Couldn't connect: print out a stack trace and exit.");
         SimpleLog.write(myLog, myHeader+getStackTrace(se));
      }
      if (debug > 1) SimpleLog.write(myLog, myHeader+myName+" active="+active);
      
      doit = active;
//    if (user.equals("wambacher")) doit = false;
//    when active=false ist user -#-userid !!!
      if (!doit) SimpleLog.write(myLog, myHeader+"######## account "+user.substring(3)+" blocked ############");
      return doit; 
   }
   
// ********************************** checkQuotaUsageOfUser *************************************

   private boolean checkQuotaUsageOfUser(int userid, String user, int debug) 
      throws ServletException, IOException{
         
      String myName = "checkQuotaUsageOfUser()"; 
      boolean doit = false;
      Long qBytes = 0L;
      Long maxBytes = 500000000L;
      
      if (debug > 1) SimpleLog.write(myLog, myHeader+myName+" userid="+userid+" user="+user);
               
      String qQuery = " select q_bytes from usage_by_user where userid=?;";
      if (debug > 0) SimpleLog.write(myLog, myHeader+myName+qQuery+" ("+userid+")");      
      try {
         PreparedStatement pQuery = conn.prepareStatement(qQuery);
   
         pQuery.setInt( 1, userid);
     
         ResultSet rs = pQuery.executeQuery();
         
         while (rs.next()) {
            qBytes = rs.getLong("q_bytes");
         }
         rs.close();
         pQuery.close();
      }
      catch (SQLException se) {
         SimpleLog.write(myLog, myHeader+"Couldn't connect: print out a stack trace and exit.");
         SimpleLog.write(myLog, myHeader+getStackTrace(se));
      }
      if (debug > 1) SimpleLog.write(myLog, myHeader+myName+" q_bytes="+qBytes);
      
      doit = (qBytes <= maxBytes); 
            
      if (user.equals("wambacher")) doit = true; 

      if (!doit) SimpleLog.write(myLog, myHeader+"############ account "+user+" quota exceeded: "
//                                              + wno_IntWithDots(qBytes)+ "/" + wno_IntWithDots(maxBytes)
                                                + qBytes + "/" + maxBytes
                                                + " ############");
      return true; // dummy
   }

// ********************************** checkQuotaUsageOfIp *************************************

   private boolean checkQuotaUsageOfIp(String ip4, String ip6, int debug) 
      throws ServletException, IOException{
         
      String myName = "checkQuotaUsageOfIp()"; 
      boolean doit = false;
      Long qBytes = 0L;
      Long maxBytes = 500000000L;
      
      if (debug > 1) SimpleLog.write(myLog, myHeader+myName+" ip4="+ip4+" ip6="+ip6);
               
      String qQuery = " select q_bytes from usage_by_ip where ip=?;";
      if (debug > 0) SimpleLog.write(myLog, myHeader+myName+qQuery+" ("+ip4+")");      
      try {
         PreparedStatement pQuery = conn.prepareStatement(qQuery);
   
         pQuery.setString( 1, ip4);
     
         ResultSet rs = pQuery.executeQuery();
         
         while (rs.next()) {
            qBytes = rs.getLong("q_bytes");
         }
         rs.close();
         pQuery.close();
      }
      catch (SQLException se) {
         SimpleLog.write(myLog, myHeader+"Couldn't connect: print out a stack trace and exit.");
         SimpleLog.write(myLog, myHeader+getStackTrace(se));
      }
      if (debug > 1) SimpleLog.write(myLog, myHeader+myName+" q_bytes="+qBytes);
      
      doit = (qBytes <= maxBytes); 
                  
      if (ip4.equals("130.180.47.146")) doit = true;
      if (ip4.equals("192.168.188.20")) doit = true;
      
      if (!doit) SimpleLog.write(myLog, myHeader+"############ account "+ip4+" quota exceeded: "
//                                              + wno_IntWithDots(qBytes)+ "/" + wno_IntWithDots(maxBytes)
                                                + qBytes + "/" + maxBytes
                                                + " ############");
      return true; // dummy 
   }
   
/* ********************************************************************************* */
/* ********************************** GEOJSON ************************************** */
/* ********************************************************************************* */

// Start JSON Functions 

   private void Point2Json(JsonGenerator jsonGenerator, Point point) 
      throws IOException{

      jsonGenerator.writeStartArray();
      jsonGenerator.writeNumber(point.getX());
      jsonGenerator.writeNumber(point.getY());
      jsonGenerator.writeEndArray();
   };

   private void Ring2Json(JsonGenerator jsonGenerator, LinearRing rng) 
      throws IOException{

      jsonGenerator.writeStartArray();

      for (int p = 0; p < rng.numPoints(); p++) { 
         Point pt = rng.getPoint(p); 
         Point2Json(jsonGenerator,pt);
      }

      jsonGenerator.writeEndArray();
      jsonGenerator.writeRaw("\n");
   };

   private void Polygon2Json(JsonGenerator jsonGenerator, Polygon pl) 
      throws IOException{

      jsonGenerator.writeStartArray();

      for (int r = 0; r < pl.numRings(); r++) { 
         LinearRing rng = pl.getRing(r);  
         Ring2Json(jsonGenerator,rng); 
      } 

      jsonGenerator.writeEndArray();            
      jsonGenerator.writeRaw("\n");
   };

   private void MultiPolygon2Json(JsonGenerator jsonGenerator, MultiPolygon mp) 
      throws IOException{

      jsonGenerator.writeStartArray();

      for (int p = 0; p < mp.numPolygons(); p++) { 
         Polygon pol = mp.getPolygon(p); ; 
         Polygon2Json(jsonGenerator,pol); 
      } 
      jsonGenerator.writeEndArray();
   };

   private void createZipEntry(ZipArchiveOutputStream zip, String Filename) 
      throws IOException {

      if (debug > 2) SimpleLog.write(myLog, myHeader+"adding "+Filename);

      zip.putArchiveEntry(new ZipArchiveEntry(Filename));
      zip.setComment("(C) OpenStreetMap contributors");
      zip.setLevel(9);
   }

    private void createFeatureCollection(JsonGenerator jsonGenerator) 
       throws IOException {
                   
       jsonGenerator.writeStartObject();
       jsonGenerator.writeStringField("type", "FeatureCollection");
    }

    private void writeJsonStartObject(JsonGenerator jsonGenerator, String comment) 
       throws IOException {

       if (debug > 2) SimpleLog.write(myLog, myHeader+"JsonStartObject - " + "               ".substring(1,indent) + comment);
       jsonGenerator.writeStartObject();
       indent=indent+2;
    } 

    private void writeJsonEndObject(JsonGenerator jsonGenerator, String comment) 
       throws IOException {
 
       indent = indent-2;
       if (debug > 2) SimpleLog.write(myLog, myHeader+"JsonEndObject -   " + "               ".substring(1,indent) + comment);
       jsonGenerator.writeEndObject();
    }

    private void writeJsonStartArray(JsonGenerator jsonGenerator, String comment) 
       throws IOException {
     
       if (debug > 2) SimpleLog.write(myLog, myHeader+"JsonStartArray  - " + "               ".substring(1,indent) + comment);
       jsonGenerator.writeStartArray();  
//     indent = indent+2;
    }

    private void writeJsonEndArray(JsonGenerator jsonGenerator, String comment) 
       throws IOException {
 
//     indent = indent-2;
       if (debug > 2) {
          SimpleLog.write(myLog, myHeader+"JsonEndArray: indent="+indent);
          SimpleLog.write(myLog, myHeader+"JsonEndArray: comment="+comment+"<");
          SimpleLog.write(myLog, myHeader+"JsonEndArray    - " + "               ".substring(1,indent) + comment);
       }
       jsonGenerator.writeEndArray();
    }
    
/* ***************************** addBoundary2JSON *********************************** */

    private void addBoundary2JSON(int startLevel, JsonGenerator jsonGenerator, ZipArchiveOutputStream zip, int indent) 
       throws IOException, SQLException, ServletException {
       String rpath = BaseRS.getString("rpath");
       int currentLevel = StringUtils.countMatches(rpath, ","); 
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  rpath="+rpath+" currentLevel="+currentLevel);
       Long id = BaseRS.getLong("id");
       if (debug > 2) SimpleLog.write(myLog, myHeader+"  adding entry "+id);
       writeJsonStartObject(jsonGenerator, "feature");
       jsonGenerator.writeStringField("type", "Feature");

       jsonGenerator.writeNumberField("id", id);

       String timestamp = BaseRS.getString("timestamp");
       jsonGenerator.writeStringField("timestamp", timestamp);

       rpath = BaseRS.getString("rpath");   // doppelt?
       jsonGenerator.writeFieldName("rpath");
               
       writeJsonStartArray(jsonGenerator,"pm 1");
       String[] pm = rpath.substring(1,rpath.length()-1).split(",");
       for (int i=0;i<pm.length;i++) {
          if (debug > 2) SimpleLog.write(myLog, myHeader+"  pm["+i+"]="+pm[i]);  
          jsonGenerator.writeString(pm[i]);
       }
       writeJsonEndArray(jsonGenerator,"pm 1");
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  pm 1 closed");
       
       jsonGenerator.writeFieldName("properties");
       writeJsonStartObject(jsonGenerator, "Properties");
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  properties object created");

       String name       = BaseRS.getString("name");
       String localname   = BaseRS.getString("localname");
       int admin_level    = BaseRS.getInt("admin_level");

       String tags = BaseRS.getString("tags");
       if (debug > 2) SimpleLog.write(myLog, myHeader+"  tags="+tags+" tl="+tags.length());

       String[] xxx = {"osm_user"}; // Krücke
       String[] mtags = (tags.length() > 0) ? (String[]) BaseRS.getArray("mtags").getArray() : xxx;
     
       String geoJson     = BaseRS.getString("geoJson");

       if (debug > 3) SimpleLog.write(myLog, myHeader+"  id: "+id+" name: "+name+" "+geoJson);
      
       PGgeometry geom    = (PGgeometry) BaseRS.getObject("way");
       if (debug > 2) SimpleLog.write(myLog, myHeader+"  geom created");
       if (debug > 3) SimpleLog.write(myLog, myHeader+"  id: "+id+" name: "+name+" geoJson: "+geom.toString()); 
       jsonGenerator.writeNumberField("id", id);
       jsonGenerator.writeStringField("name", name);
       jsonGenerator.writeStringField("localname", localname);
       jsonGenerator.writeStringField("timestamp", timestamp);
       jsonGenerator.writeStringField("SRID", "4326");
       jsonGenerator.writeNumberField("admin_level", admin_level); 
       jsonGenerator.writeFieldName("rpath");
       writeJsonStartArray(jsonGenerator,"pm 2");
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  array pm 2 created");

       for (int i=0;i<pm.length;i++) {
          if (debug > 2) SimpleLog.write(myLog, myHeader+"  pm["+i+"]="+pm[i]);  
          jsonGenerator.writeString(pm[i]);
       }
       writeJsonEndArray(jsonGenerator,"pm 2");
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  array pm 2 closed");

       jsonGenerator.writeFieldName("tags");

       writeJsonStartObject(jsonGenerator, "tags");
       if (debug > 2) SimpleLog.write(myLog, myHeader+"  tags object created");
       
       for (int i=0;i<mtags.length-1;i=i+2) {
          String tag = mtags[i];
          switch(tag) {
             case "osm_user":break;
             case "osm_uid":break;
             case "osm_timestamp":break;
             case "osm_version":break;
             case "way_area":break; 
             default:
                jsonGenerator.writeStringField(tag,mtags[i+1]);
          }
       }
       writeJsonEndObject(jsonGenerator, "tags");
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  tags object closed");
       
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  closing properties object");     
       writeJsonEndObject(jsonGenerator, "properties");
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  properties object closed");

       jsonGenerator.writeFieldName("geometry");
       writeJsonStartObject(jsonGenerator, "geometry");

       if (debug > 2) SimpleLog.write(myLog, myHeader+"  got geometry "+geom.getType()+" ("+geom.getGeoType()+")"); 
       switch(geom.getGeoType()) {
          case Geometry.POLYGON: { 
             Polygon pl = (Polygon)geom.getGeometry();
             jsonGenerator.writeStringField("type","Polygon");
             jsonGenerator.writeFieldName("coordinates");
             Polygon2Json(jsonGenerator, pl);
             break; 
          }
          case Geometry.MULTIPOLYGON: { 
             MultiPolygon mp = (MultiPolygon)geom.getGeometry();
             jsonGenerator.writeStringField("type","MultiPolygon");
             jsonGenerator.writeFieldName("coordinates");
             MultiPolygon2Json(jsonGenerator, mp);
             break; 
          }
          default:
             SimpleLog.write(myLog, myHeader+"   unknown geometry");      
       }
       writeJsonEndObject(jsonGenerator, "geometry");
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  geometries closed");

       writeJsonEndObject(jsonGenerator, "feature");
       if (debug > 2) SimpleLog.write(myLog, myHeader+ "  feature closed");

       jsonGenerator.flush();
    } 
    
/* ****************** exportJSON ************************************ */

    private long exportJSON(HttpServletResponse response, String target, boolean subtree,
           Integer from_al, Integer to_al, String bCacheFilename) 
       throws IOException, SQLException, ServletException {

       long total = 0;
       
       try { 
          int old_admin_level = 0;
          int startLevel = 0;
          int currentLevel = 0;
          indent = 1;
          boolean skip = false;
         
          JsonFactory jsonfactory = null;
          JsonGenerator jsonGenerator = null;

//        File ZipFile = File.createTempFile("exported_boundaries_", ".geojson.zip");

//          String fileUUID = UUID.randomUUID().toString();
//          String bCacheFile = exportFormat+"/"+exportLayout+"/"+exportAreas+"/"
//                            + "export_"+timestamp+"_"+fileUUID+".geojson.zip";
//          String ZipFile = "/data/osm/boundaries_cache/"+ bCacheFile;
//          if (debug > 0) SimpleLog.write(myLog, myHeader+"writing to "+ZipFile);

//        String bCacheFilename = createbCacheFilename(exportFormat, exportLayout, exportAreas, timestamp);

          String baseName = "dummy";
          String ZipFile = "dummy";

          if (bCacheFilename == null) { // create new one
             String newbCacheFilename = createbCacheFilename(exportFormat, exportLayout, exportAreas, timestamp);
             ZipFile = bCache + newbCacheFilename;
             if (debug > 0) SimpleLog.write(myLog, myHeader+"Creating ZipFile "+ZipFile);

             ZipArchiveOutputStream zip = new ZipArchiveOutputStream(new FileOutputStream(ZipFile));
             zip.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);
             
             if (debug > 0) SimpleLog.write(myLog, myHeader+"exportLayout="+exportLayout);
             if (debug > 0) SimpleLog.write(myLog, myHeader+"planet="+planet);
             
             switch(exportLayout) {
             case "split":  // ############################### SPLIT ##########################
                   
                if (debug > 1 ) {
                   SimpleLog.write(myLog, myHeader+"doing split");
                   SimpleLog.write(myLog, myHeader+"old_admin_level="+old_admin_level);
                }

                while (BaseRS.next()) {  
                   int admin_level = BaseRS.getInt("admin_level");
                   String rpath = BaseRS.getString("rpath");
                                      
                   if (debug > 2 ) {
                      SimpleLog.write(myLog, myHeader+"while next(): admin_level="+admin_level);
                      SimpleLog.write(myLog, myHeader+"while next(): rpath="+rpath);
                   }
                   
                   if (old_admin_level == 0) { // first boundary at all
                      baseName = BaseRS.getString("name").replace(' ','_').replace('/','_');    // first name
                      if (planet) baseName = "World";
                      startLevel = StringUtils.countMatches(rpath, ",");    
                      if (debug > 1) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" startLevel="+startLevel);
                   }
                   old_admin_level = admin_level;
                   
                   currentLevel = StringUtils.countMatches(rpath, ","); 
                   if (debug > 2) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" currentLevel="+currentLevel);
                   jsonfactory = new JsonFactory();
                   jsonGenerator = jsonfactory.createJsonGenerator(zip, JsonEncoding.UTF8);           
                   jsonGenerator.useDefaultPrettyPrinter();
                   
                   String Filename = BaseRS.getString("name").replace(' ','_').replace('/','_')
                   + "#AL" + BaseRS.getString("admin_level")
                   + "_" + BaseRS.getString("id")
                   + ".GeoJson";
                   
                   createZipEntry(zip, Filename);

                   addBoundary2JSON(startLevel, jsonGenerator, zip, indent);
                   
                };  // end loop über die Boundaries  
                
                if (debug > 1 ) SimpleLog.write(myLog, myHeader+"flushing jsonGenerator");
                
                jsonGenerator.flush();                 
                jsonfactory = null; // ???????????????????????

                zip.closeArchiveEntry();     
                break;
                
             case "single": // ######################### SINGLE #############
                
                if (debug > 1 ) {
                   SimpleLog.write(myLog, myHeader+"doing single");
                   SimpleLog.write(myLog, myHeader+"old_admin_level="+old_admin_level);
                }
                while (BaseRS.next()) {
                   int admin_level = BaseRS.getInt("admin_level");
                   String rpath = BaseRS.getString("rpath");
                   
                   if (old_admin_level == 0) { // first boundary at all
//                    baseName = BaseRS.getString("name").replace(' ','_').replace('/','_');    // first name
//                    if (planet) baseName = "World";
                      baseName = getBaseName(BaseRS.getString("name"),selected, subtree, from_al, to_al, planet);
                      if (planet) baseName = "World";
                      startLevel = StringUtils.countMatches(rpath, ",");    
                      if (debug > 1) SimpleLog.write(myLog, myHeader+ " rpath="+rpath+" startLevel="+startLevel);
                      
                      jsonfactory = new JsonFactory();
                      jsonGenerator = jsonfactory.createJsonGenerator(zip, JsonEncoding.UTF8);           
                      jsonGenerator.useDefaultPrettyPrinter();
                      
//                    String Filename = BaseRS.getString("name").replace(' ','_').replace('/','_')
//                    + ".GeoJson";
                      String Filename = baseName+".GeoJson";
                      createZipEntry(zip, Filename);
                      createFeatureCollection(jsonGenerator);
                      jsonGenerator.writeFieldName("features");
                      jsonGenerator.writeStartArray();
                   }
                   old_admin_level = admin_level;
                   
                   addBoundary2JSON(startLevel, jsonGenerator, zip, indent);

                };  // end loop über die Boundaries 
                
                jsonGenerator.writeEndArray();                  // Features   
                
                jsonGenerator.writeEndObject();
                jsonGenerator.flush();
                jsonfactory = null; // ???????????????????????

                zip.closeArchiveEntry(); 
                break;
                
             case "levels": // ####################################### LEVELS ############################
                if (debug > 1 ) {
                   SimpleLog.write(myLog, myHeader+"doing levels");
                   SimpleLog.write(myLog, myHeader+"old_admin_level="+old_admin_level);
                }
                
                while (BaseRS.next()) {  
                   int admin_level = BaseRS.getInt("admin_level");
                   String rpath = BaseRS.getString("rpath");
                   skip = false;
                   if (debug > 2 ) {
                      SimpleLog.write(myLog, myHeader+"while next(): admin_level="+admin_level);
                      SimpleLog.write(myLog, myHeader+"while next(): rpath="+rpath);
                   }
                   
                   if (admin_level > old_admin_level) {
                      if (old_admin_level == 0) { // first boundary at all
                         baseName = BaseRS.getString("name").replace(' ','_').replace('/','_'); // first name
                         if (planet) baseName = "World";
                         if (debug > 1) SimpleLog.write(myLog, myHeader+ "baseName="+baseName);
                         startLevel = StringUtils.countMatches(rpath, ","); 
                         if (debug > 1) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" startLevel="+startLevel);
                      }
                      else {
                         // close old level
                         SimpleLog.write(myLog, myHeader+"new al="+admin_level+" finishing and closing current level"); 
                         SimpleLog.write(myLog, myHeader+"close Features"); 
                         writeJsonEndArray(jsonGenerator,"features");       // close Features    
                         writeJsonEndObject(jsonGenerator, "FeatureCollection");        
                         jsonGenerator.flush();  
                         SimpleLog.write(myLog, myHeader+"close ZIP-Level"); 
                         zip.closeArchiveEntry();       // close ZIP-Level              
                      }
                      
                      jsonfactory = new JsonFactory();
                      jsonGenerator = jsonfactory.createJsonGenerator(zip, JsonEncoding.UTF8);           
                      jsonGenerator.useDefaultPrettyPrinter();
                      
                      writeJsonStartObject(jsonGenerator, "FeatureCollection");
                      jsonGenerator.writeStringField("type", "FeatureCollection");
                      String AlFilename = baseName+"_AL"+admin_level+".GeoJson";
                      if (debug > 2) SimpleLog.write(myLog, myHeader+"adding "+AlFilename);
                      
                      zip.putArchiveEntry(new ZipArchiveEntry(AlFilename));
                      zip.setComment("(C) OpenStreetMap contributors");
                      zip.setLevel(9);              
                      jsonGenerator.writeFieldName("features");
                      writeJsonStartArray(jsonGenerator,"features");
                      old_admin_level = admin_level;
                   }
                   addBoundary2JSON(startLevel, jsonGenerator, zip, indent);
                };  // end loop über die Boundaries 
                
                writeJsonEndArray(jsonGenerator,"features 2???");                   // Features 
                
                writeJsonEndObject(jsonGenerator, "FeatureCollection"); 
                
                if (debug > 1 ) SimpleLog.write(myLog, myHeader+"flushing jsonGenerator");
                jsonGenerator.flush();              
                jsonfactory = null; // ???????????????????????
                
                zip.closeArchiveEntry();  
                break;
             }
             
             zip.close();
             if (debug > 0 ) SimpleLog.write(myLog, myHeader+exportFormat+" part1 done");
             if (debug > 0 ) SimpleLog.write(myLog, myHeader+"bCacheFilename="+bCacheFilename);

             insertBCache(user, timestamp, exportFormat, union, exportLayout, exportAreas, subtree, from_al, to_al, newbCacheFilename, selected, baseName);
          } // finish creating zip.
          else {
             baseName = bCacheFilename.split("#")[0];
             ZipFile = bCache+bCacheFilename.split("#")[1];
//           updateBcache(exportFormat, exportLayout, exportAreas, timestamp); -- used=used+1
          }
          
          SimpleLog.write(myLog, myHeader+"reading "+ZipFile);

          // Init servlet response.
          response.reset();      
          response.setCharacterEncoding("UTF-8");
          BufferedOutputStream output = new BufferedOutputStream(response.getOutputStream(), DEFAULT_BUFFER_SIZE);

          if (target.equals("file")) {
             response.setContentType("application/zip");
             String header = "attachment; filename=\"exported_boundaries_"+baseName+".GeoJson.zip\"";
             if (debug > 0) SimpleLog.write(myLog, myHeader+"Content-Disposition="+ header);
             response.setHeader("Content-Disposition", header);
             response.setHeader("Content-Transfer-Encoding", "binary");
          }
          else {
             SimpleLog.write(myLog, myHeader+"writing to html-stream");
             response.setContentType("text/plain");
          }
                                  
          File z = new File(ZipFile);
          long bytes = z.length();
          response.setHeader("Content-Length",bytes+"");

          BufferedInputStream input = new BufferedInputStream(new FileInputStream(ZipFile), DEFAULT_BUFFER_SIZE);

          // Write file contents to response.
          byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
          int length;

          while ((length = input.read(buffer)) > 0) {
             output.write(buffer, 0, length);
             total = total+length;
          }

          // close streams.
          SimpleLog.write(myLog, myHeader+"[json] "+total+" bytes sent to "+remoteAddr);
          log2RRD("boundaries", "json", total, 2);
          input.close();
          output.close();
       }
       catch (JsonGenerationException je) {
          SimpleLog.write(myLog, myHeader+"JSON Error: "+je.toString());
          SimpleLog.write(myLog, myHeader+""+getStackTrace(je));
       } 
       return (total);
    }

/* ************************************* POLY/BPOLY ********************************** */

    private long exportPOLY(String format, HttpServletResponse response, String target) 
       throws IOException, SQLException, ServletException {

       byte[] bytes = null; 
       long total = 0;
       indent = 9;

       File ZipFile = File.createTempFile("boundaries_", ".poly.zip");
       SimpleLog.write(myLog, myHeader+"WRiting to "+ZipFile);

       ZipArchiveOutputStream zip = new ZipArchiveOutputStream(new FileOutputStream(ZipFile));
       zip.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);

// init Query für Points

       String qPoints = "dummy";
       SimpleLog.write(myLog, myHeader+myName+" format="+format);
 
       if (format.equals("poly")) {
          qPoints = "select id,"
                  + "       name,"
                  + "       localname,"
                  + "       admin_level,"
                  + "       path[1] polygon,"
                  + "       path[2] ring,"
                  + "       ST_x(point) x,"
                  + "       ST_y(point) y"
                  + "  from ("
                  + "        SELECT id,"
                  + "               name,"
                  + "               localname,"
                  + "               admin_level,"  
                  + "               (st_dumppoints(st_multi(way))).path,"
                  + "               (st_dumppoints(st_multi(way))).geom point"             
                  + "          from "+tmpTable
                  + "         where id = ?"
                  + "       ) points;"
          ;
       }
       else {
          qPoints = "select id,"
                  + "       name,"
                  + "       localname,"
                  + "       admin_level,"
                  + "       path[1] polygon,"
                  + "       path[2] ring,"
                  + "       ST_x(point) x,"
                  + "       ST_y(point) y"
                  + "  from ("
                  + "        SELECT id,"
                  + "               name,"
                  + "               localname,"  
                  + "               admin_level," 
                  + "               (ST_DumpPoints("
                  + "                   ST_Multi("
                  + "                      ST_Transform("
                  + "                         ST_Buffer("
                  + "                            ST_Transform(way,3857),"
                  + "                            ?),"
                  + "                         4326)"
                  + "                      ))).path,"
                  + "               (ST_DumpPoints("
                  + "                   ST_Multi("
                  + "                      ST_Transform("
                  + "                         ST_Buffer("
                  + "                            ST_Transform(way,3857),"
                  + "                            ?),"
                  + "                         4326)"
                  + "                      ))).geom point"            
                  + "          from "+tmpTable
                  + "         where id = ?"
                  + "       ) points;"
          ;
       }

       SimpleLog.write(myLog, myHeader+myName+" qPoints="+qPoints);

       PreparedStatement pPoints=conn.prepareStatement(qPoints);
       
       SimpleLog.write(myLog, myHeader+myName+" after query");

       boolean first = true;
       String baseName = "dummy";

       while (BaseRS.next()) 
       {
          if (first) {
             baseName = BaseRS.getString("name");
             first = false;
          }               
          else {
//             if (debug > 1) SimpleLog.write(myLog, myHeader+"closing current Entry");  
//             zip.closeArchiveEntry();
          }             

          int admin_level = BaseRS.getInt("admin_level");
          String Name = BaseRS.getString("name");
          String AlFilename = baseName+"/"+"al"+admin_level+"/"+Name+".poly";
          if (debug > 1) SimpleLog.write(myLog, myHeader+"create new Zip Entry");  
          if (debug > 1) SimpleLog.write(myLog, myHeader+"adding "+AlFilename);

          zip.putArchiveEntry(new ZipArchiveEntry(AlFilename));
          zip.setComment("(C) OpenStreetMap contributors");
          zip.setLevel(9);

          Long id = BaseRS.getLong("id");
          if (debug > 2) SimpleLog.write(myLog, myHeader+"adding entry "+id);
          String name = BaseRS.getString("name"); 
          String localname = BaseRS.getString("localname");
          admin_level = BaseRS.getInt("admin_level");
          String mtags[] = (String[]) BaseRS.getArray("mtags").getArray();
          if (debug > 1) SimpleLog.write(myLog, myHeader+"id: "+id+" name: "+name);

          if (format.equals("poly")) { 
             if (debug > 2) SimpleLog.write(myLog,myHeader+"qPoints: "+qPoints+" ("+id+")"); 
             pPoints.setLong(1, id);
          }
          else {                             
             double simple = BufferParams(admin_level) % 1;
             if (debug > 2) SimpleLog.write(myLog,myHeader+"qPoints: "+qPoints+" ("+id+": "+bufferDist+","+simple+")");
             pPoints.setDouble(1, bufferDist);
             pPoints.setDouble(2, bufferDist);
             pPoints.setLong(  3, id);
          }

          ResultSet rs1 = pPoints.executeQuery();  // Details mit Path und Points

          int npols  = 0;                       
          int oldpol = 0;
          int oldring = 0; 

          DumpedPoint pt = getNextPoint(rs1);

          while (pt != null) { // loop 
             oldpol = pt.polygon;
             if (debug > 2 ) SimpleLog.write(myLog, myHeader+"oldpol: "+oldpol+" polygon: "+pt.polygon);
             int nrings = 1;
             while (pt != null && pt.polygon == oldpol ) {                                 
               npols = npols+1; 
                if (nrings==1)                            
                   zip.write((npols+" "+id+" "+name+"\n").getBytes());  
                else
                   zip.write(("!"+npols+"\n").getBytes());
                oldring = pt.ring;
                if (debug > 2) SimpleLog.write(myLog, myHeader+"inner: oldring="+oldring+" ring="+pt.ring);
                while (pt != null && pt.polygon==oldpol && pt.ring==oldring) {                                   
                   zip.write(("   "+pt.x+" "+pt.y+"\n").getBytes());
                   pt = getNextPoint(rs1);
                }
                if (debug > 2) SimpleLog.write(myLog, myHeader+"end of ring "+oldring);
                nrings = nrings+1;
                zip.write("END\n".getBytes());
             }                                           
          }
          if (debug > 1 ) SimpleLog.write(myLog, myHeader+"closing last "+format+" object");
          zip.write("END\n".getBytes());                   
          if (debug > 1) SimpleLog.write(myLog, myHeader+"closing Zip Entry");  
          zip.closeArchiveEntry();
       } 
       if (debug > 1) SimpleLog.write(myLog, myHeader+"closing Zip");  
       zip.close();
             if (debug > 0 ) SimpleLog.write(myLog, myHeader+exportFormat+" part1 done");

       String header = "attachment; filename=\"exported_boundaries_"+baseName+"."+format+".zip\"";

       // Init servlet response.
       response.reset();
//     response.setBufferSize(DEFAULT_BUFFER_SIZE);
       response.setContentType("application/zip");
       response.setCharacterEncoding("UTF-8");
              
//       File z = new File(ZipFile);
       long bts = ZipFile.length();
       response.setHeader("Content-Length",bts+"");

       if (debug > 0) SimpleLog.write(myLog, myHeader+"Content-Disposition="+ header);
       response.setHeader("Content-Disposition", header);

       BufferedInputStream input = new BufferedInputStream(new FileInputStream(ZipFile), DEFAULT_BUFFER_SIZE);
       BufferedOutputStream output = new BufferedOutputStream(response.getOutputStream(), DEFAULT_BUFFER_SIZE);

       // Write file contents to response.
       byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
       int length;
       while ((length = input.read(buffer)) > 0) {
          output.write(buffer, 0, length);
          total = total+length;
       }

       // close streams.
       if (debug > 1) SimpleLog.write(myLog, myHeader+"["+format+"] "+total+" bytes sent to "+remoteAddr);
       log2RRD("boundaries", format, total, debug);
       input.close();
       output.close();
       return(total);
    }

/* ******************************************** SVG **************************************** */

    private long exportSVG(HttpServletResponse response, String target, 
                           boolean subtree, Integer from_al, Integer to_al, String bCacheFilename) 
       throws IOException, SQLException, ServletException {

          SimpleLog.write(myLog, myHeader+"start SVG");
                    
          String baseName = "dummy";
          String svgFile = "dummy"; 
          long total = 0;
          indent = 1;
          
          if (bCacheFilename == null) { // create new one
             String newbCacheFilename = createbCacheFilename(exportFormat, exportLayout, exportAreas, timestamp);

             DOMImplementation impl = SVGDOMImplementation.getDOMImplementation();
             String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
             Document doc = impl.createDocument(svgNS, "svg", null);
             
             Comment comment = doc.createComment("svg created by "+myName);
             doc.appendChild(comment);
             
             String querybbb;  
             querybbb = "select ST_XMin(ST_Transform(st_setSrid(ST_Extent(bbox),4326),3857)),"
             + "       ST_XMax(ST_Transform(st_setSrid(ST_Extent(bbox),4326),3857)),"
             + "       ST_YMin(ST_Transform(st_setSrid(ST_Extent(bbox),4326),3857)),"
             + "       ST_YMax(ST_Transform(st_setSrid(ST_Extent(bbox),4326),3857))"
             + "  from "+tmpTable;
             
             if (debug > 2) SimpleLog.write(myLog, myHeader+"querybbb: "+querybbb);
             
             Statement statement2 = conn.createStatement();
             ResultSet bbbRs = statement2.executeQuery(querybbb);
             
             bbbRs.next();
             
             double xmin = bbbRs.getDouble("ST_Xmin");
             double xmax = bbbRs.getDouble("ST_Xmax");
             double ymin = bbbRs.getDouble("ST_Ymin");
             double ymax = bbbRs.getDouble("ST_Ymax");
             
             bbbRs.close();
             
             int xMsize = 512;
             int yMsize = 512;
             
             int xsize = xMsize-20;
             int ysize = yMsize-20;
             
             if (debug > 2) SimpleLog.write(myLog, myHeader+"bbb xmin: "+xmin+" ymin: "+ymin+", xmax: "+xmax+" ymax: "+ymax);
             
             double xScale = xsize / (xmax-xmin);
             double yScale = ysize / (ymax-ymin);
             
             double scale = 0.0;
             if (xScale < yScale) {
                scale = xScale;                         
                double y = (ymax-ymin) * scale +20.;
                yMsize = (int) y;
             }
             else {
                scale = yScale;                          
                double x = (xmax-xmin) * scale +20.;
                xMsize =  (int) x;
             }
             
             SimpleLog.write(myLog, myHeader+"scale: "+scale+" xMsize: "+xMsize+" yMsize: "+yMsize);
             
             String qPoints = "select ST_AsSVG(st_transscale(st_transscale(st_transform(way,3857),"
             +(-xmin)+","+(-ymax)+","+scale+","+scale+"),10,-10,1,1),0,6) path"
             + "  from "+tmpTable
             + " where id = ?;";
             
             //        if (debug > 2) SimpleLog.write(myLog, myHeader+"qPoints: "+qPoints+" ("+id+")");
             
             boolean first = true;
             
             PreparedStatement pPoints = conn.prepareStatement(qPoints);
             
             Element svg = doc.getDocumentElement();
             
             svg.setAttributeNS(null, "x", "0");
             svg.setAttributeNS(null, "y", "0");
             svg.setAttributeNS(null, "width",  xMsize+"");
             svg.setAttributeNS(null, "height", yMsize+"");
             svg.setAttributeNS(null, "id", "hier soll id hin");
             
             //        svg.setAttributeNS(null, "bbox", lx+" "+ly+" "+ux+" "+uy);
             
             // defs
             
             Element defs = doc.createElementNS(svgNS, "defs");
             
             Element rect = doc.createElementNS(svgNS, "rect");
             rect.setAttributeNS(null, "id", "MapFrame");
             rect.setAttributeNS(null, "x", "0");
             rect.setAttributeNS(null, "y", "0");
             rect.setAttributeNS(null, "width",  xMsize+"");
             rect.setAttributeNS(null, "height", yMsize+"");
             
             defs.appendChild(rect);
             
             Element clipPath = doc.createElementNS(svgNS, "clipPath");
             clipPath.setAttributeNS(null, "id", "MapClipRectangle");
             clipPath.setAttributeNS(null, "clipPathUnits", "userSpaceOnUse");
             
             Element use = doc.createElementNS(svgNS, "use"); 
             use.setAttributeNS(null, "xlink:href", "#MapFrame");
             
             clipPath.appendChild(use);
             
             defs.appendChild(clipPath);
             
             svg.appendChild(defs);
             
             Element clipPath2 = doc.createElementNS(svgNS, "clipPath");
             clipPath2.setAttributeNS(null, "clipPathUnits", "userSpaceOnUse");
             
             clipPath2.setAttributeNS(null, "id", "MapClipRectangle");
             Element use2 = doc.createElementNS(svgNS, "use");
             use2.setAttributeNS(null, "xlink:href","#MapFrame");
             clipPath2.appendChild(use2);
             
             svg.appendChild(clipPath2);
             
             // G=Map
             Element gmap = doc.createElementNS(svgNS, "g");
             gmap.setAttributeNS(null,"id", "Map");
             gmap.setAttributeNS(null, "clip-path", "url(#MapClipRectangle)");
             
             Element gmbg = doc.createElementNS(svgNS, "g");
             gmbg.setAttributeNS(null,"id", "Map_background");
             gmbg.setAttributeNS(null,"style", "fill:#e1ddd7;stroke:#000000");
             
             Element gmbg2 = doc.createElementNS(svgNS, "g");
             gmbg2.setAttributeNS(null,"id", "map_background");
             
             Element rect2 = doc.createElementNS(svgNS, "rect");
             rect2.setAttributeNS(null, "id", "Map_Background_Fill");
             rect2.setAttributeNS(null, "style", "stroke:none");
             rect2.setAttributeNS(null, "x", "0");
             rect2.setAttributeNS(null, "y", "0");
             rect2.setAttributeNS(null, "width",  xMsize+"");
             rect2.setAttributeNS(null, "height", yMsize+"");
             
             gmbg2.appendChild(rect2);
             
             gmbg.appendChild(gmbg2);
             
             gmap.appendChild(gmbg);
             
             Element line_aw = doc.createElementNS(svgNS, "g");
             line_aw.setAttributeNS(null, "id", "Line_artwork1");
             line_aw.setAttributeNS(null, "style", 
                "stroke-linejoin:round;stroke-linecap:round;stroke:#000000;stroke-width:2;fill:#F1fffe");
             
             Element g3 = doc.createElementNS(svgNS, "g");
             g3.setAttributeNS(null, "id", "hull");
             
             //                 Add Hull
             
             String queryHull;  
             queryHull = "select ST_AsSVG(st_transscale(st_transscale(st_transform(ST_Union(way),3857),"
             +         (-xmin)+","+(-ymax)+","+scale+","+scale+"),10,-10,1,1),0,6) hull"
             + "  from "+tmpTable+";"           
             ;
             if (debug > 2) SimpleLog.write(myLog, myHeader+"queryHull: "+queryHull);
             
             Statement statement3 = conn.createStatement();
             ResultSet hullRs = statement2.executeQuery(queryHull);
             
             hullRs.next();
             
             String svgPath = hullRs.getString("hull");
             //                 SimpleLog.write(myLog, myHeader+"path: "+svgPath);
             
             Element path = doc.createElementNS(svgNS, "path");
             path.setAttributeNS(null,"name","_HULL_");
             path.setAttributeNS(null,"d",svgPath);
             
             g3.appendChild(path);
             hullRs.close();
             
             line_aw.appendChild(g3);
             
             gmap.appendChild(line_aw);
             
             // Group 2
             Element line_aw2 = doc.createElementNS(svgNS, "g");
             line_aw2.setAttributeNS(null, "id", "Line_artwork2");
             line_aw2.setAttributeNS(null, "style", "stroke-linejoin:round;stroke-linecap:round;stroke:#000000;stroke-width:2;fill:#F1fffe");
             
             Element g4 = doc.createElementNS(svgNS, "g");
             g4.setAttributeNS(null, "id", "boundaries");
             
             //                 loop over boundaries
             
             while (BaseRS.next()) 
             {
                if (first) {
                   baseName = BaseRS.getString("name");
                   if (planet) baseName = "World";
                   first = false;
                }               
                
                Long id            = BaseRS.getLong("id");
                String name        = BaseRS.getString("name"); 
                String localname   = BaseRS.getString("localname");
                int admin_level    = BaseRS.getInt("admin_level");
                String mtags[]     = (String[]) BaseRS.getArray("mtags").getArray();
                if (debug > 2) SimpleLog.write(myLog, myHeader+"id: "+id+" name: "+name+" localname: "+localname);
                
                pPoints.setLong(1, id);
                if (debug > 2) SimpleLog.write(myLog, myHeader+"qPoints: "+qPoints+" ("+id+")");
                
                ResultSet rs1 = pPoints.executeQuery();
                rs1.next();
                
                svgPath = rs1.getString("path");
                //                    SimpleLog.write(myLog, myHeader+"path: "+svgPath);
                
                path = doc.createElementNS(svgNS, "path");
                path.setAttributeNS(null,"id",id+"");
                path.setAttributeNS(null,"name",name);
                path.setAttributeNS(null,"localname",localname);
                path.setAttributeNS(null,"admin_level",admin_level+"");
                path.setAttributeNS(null,"d",svgPath);
                
                g4.appendChild(path);
                
             }
             
             line_aw2.appendChild(g4);
             
             gmap.appendChild(line_aw2);
             
             Element frame = doc.createElementNS(svgNS, "g");
             frame.setAttributeNS(null,"id","Map_frame");
             frame.setAttributeNS(null,"style","stroke:black;stroke-width:2;fill:none");
             
             Element rect3 = doc.createElementNS(svgNS, "rect");
             rect3.setAttributeNS(null, "x", "0");
             rect3.setAttributeNS(null, "y", "0");
             rect3.setAttributeNS(null, "width",  xMsize+"");
             rect3.setAttributeNS(null, "height", yMsize+"");
             
             frame.appendChild(rect3);
             
             gmap.appendChild(frame);
             
             svg.appendChild(gmap);
             
             try {
                //           generate & display xml
                
                TransformerFactory tFactory = TransformerFactory.newInstance();
                
                Transformer transformer = tFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
                
                DOMSource source = new DOMSource(doc);
                
                StreamResult result = new StreamResult(new StringWriter());
                
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.transform(source, result);
                
                String xmlString = result.getWriter().toString();
                                
                svgFile = bCache + newbCacheFilename; 
                if (debug > 0) SimpleLog.write(myLog, myHeader+"creating svgFile: "+svgFile);
                PrintWriter svgOut = new PrintWriter(svgFile);
                
                svgOut.print(xmlString);
                svgOut.close();
             if (debug > 0 ) SimpleLog.write(myLog, myHeader+exportFormat+" part1 done");
                if (debug > 0 ) SimpleLog.write(myLog, myHeader+"bCacheFilename="+newbCacheFilename);
                
                insertBCache(user, timestamp, exportFormat, union, exportLayout, exportAreas, subtree, from_al, to_al, newbCacheFilename, selected, baseName);
             }
             catch (TransformerConfigurationException tce) {
            SimpleLog.write(myLog, myHeader+"exportBoundaries Tce "+tce);
             }
         catch (TransformerException te) {
             SimpleLog.write(myLog, myHeader+"exportBoundaries Te "+te);       
             }
             // finish creating svg
          }
          else {
             baseName = bCacheFilename.split("#")[0];
             svgFile = bCache+bCacheFilename.split("#")[1];
//           updateBcache(exportFormat, exportLayout, exportAreas, timestamp); -- used=used+1             
          }
//    Copy to response

       // Init servlet response.
       response.reset();      
       response.setCharacterEncoding("UTF-8");
       BufferedOutputStream output = new BufferedOutputStream(response.getOutputStream(), DEFAULT_BUFFER_SIZE);

       if (target.equals("file")) {
          response.setContentType("application/xml");
          String header = "attachment; filename=\"exported_boundaries_"+baseName+".svg\"";
          if (debug > 0) SimpleLog.write(myLog, myHeader+"Content-Disposition="+ header);
          response.setHeader("Content-Disposition", header);
          response.setHeader("Content-Transfer-Encoding", "binary");
       }
       else {
          SimpleLog.write(myLog, myHeader+"writing to html-stream");
          response.setContentType("text/plain");
       }
             
       if (debug > 0) SimpleLog.write(myLog, myHeader+"Reading svgFile "+svgFile);     
       
       File z = new File(svgFile);
       long bytes = z.length();
       response.setHeader("Content-Length",bytes+"");
       
       BufferedInputStream input = new BufferedInputStream(new FileInputStream(svgFile), DEFAULT_BUFFER_SIZE);

       // Write file contents to response.
       byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
       int length;

       while ((length = input.read(buffer)) > 0) {
          output.write(buffer, 0, length);
          total = total+length;
       }

// close streams.
       SimpleLog.write(myLog, myHeader+"[svg] "+total+" bytes sent to "+remoteAddr);
       log2RRD("boundaries", "svg", total, debug);
       input.close();
       output.close();    
      
       return(total);
    }

/* ***************************************************************************** */
/* ***************************** SHP ******************************************* */
/* ***************************************************************************** */

    private void add2Zip(String ShapeFile, String outName, ZipArchiveOutputStream zip) {

       debug = 0;
       if (debug > 0) SimpleLog.write(myLog, myHeader+"adding "+ShapeFile+" as "+outName);
       try {
          BufferedInputStream input = new BufferedInputStream(new FileInputStream(ShapeFile), DEFAULT_BUFFER_SIZE);

          zip.putArchiveEntry(new ZipArchiveEntry(outName));
          zip.setComment("(C) OpenStreetMap contributors");
          zip.setLevel(9);

          // Write file contents to response.
          byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
          int total=0;
          int length = 0;
          indent = 1;

          while ((length = input.read(buffer)) > 0) {
             zip.write(buffer, 0, length);
             total = total + length;
          }

          // close streams.
          if (debug > 0) SimpleLog.write(myLog, myHeader+"closing zip entry");
          input.close();
          zip.closeArchiveEntry();

          if (debug > 0) SimpleLog.write(myLog, myHeader+"deleting "+ShapeFile);
          File tmp = new File(ShapeFile);
          tmp.delete();
       }
        catch (FileNotFoundException err) {
          SimpleLog.write(myLog, myHeader+err);
          err.printStackTrace();
       }
       catch (IOException err) {
          SimpleLog.write(myLog, myHeader+err);
          err.printStackTrace();
       }
    } 

    private void addBoundary2SHP(String ids, String AlFilename, String exportLayout,
                                 int startLevel, int currentLevel, ZipArchiveOutputStream zip, int indent) 
       throws IOException, SQLException, ServletException {
          
          int debug=2;
          String subName = "addBoundary2SHP()";
          
          File shpFile = File.createTempFile("boundaries_", ".shp");
          SimpleLog.write(myLog, myHeader+"writing to "+shpFile);
          BufferedOutputStream shp = new BufferedOutputStream(new FileOutputStream(shpFile), DEFAULT_BUFFER_SIZE);
          
          if (debug > 0) {
             SimpleLog.write(myLog, myHeader+subName+" currentLevel="+currentLevel);
             SimpleLog.write(myLog, myHeader+subName+" union="+union);
          }

          String[] cmd;

          try {
             if (!union) {
                String[] xcmd = { "/usr/bin/pgsql2shp","-h","localhost","-u","osm","-f",shpFile.getAbsolutePath(),database,
                                "select id,"
//                            + "         convert_to(name,'UTF-8')::text \"name\","
                              + "         name,"
                              + "         localname,"
                              + "         tags->'boundary' boundary,"
                              + "         admin_level \"admin_lvl\","
                              + "         tags->'de:amtlicher_gemeindeschluessel' \"city_key\","
                              + "         tags->'de:regionalschluessel' \"region_key\","
                              + "         tags->'note' note,"
                              + "         tags->'flag' \"flag\","
                              + "         tags->'currency' \"currency\","
                              + "         tags->'ISO3166-1:alpha3' \"ISO1\","
                              + "         tags->'ISO3166-2' \"ISO2\","
                              + "         tags->'wikidata' \"wikidata\","
                              + "         tags->'wikipedia' \"wikipedia\","
                              + "         tags->'official_name' \"off_name\","
                              + "         tags tags,"
                              + "         way"
                              + "    from "+tmpTable
                };

                if (exportLayout.equals("split"))
                   xcmd[8] = xcmd[8] + "   where id in ("+ids+");";
                if (exportLayout.equals("levels")) {
                   SimpleLog.write(myLog, myHeader+"level="+currentLevel);
                   xcmd[8] = xcmd[8] + "   where admin_level ='"+currentLevel+"';";
                }

//              xcmd[8] = xcmd[8] + "   where id in ("+ids+");";
                
                cmd = xcmd;
             }
             else {
                String[] xcmd = { "/usr/bin/pgsql2shp","-h","localhost","-u","osm","-f",shpFile.getAbsolutePath(),database,
                                 "select 1 id,"
//                             + "         convert_to(name,'UTF-8')::text \"name\","
                               + "         'union_of_selected_boundaries'::text \"name\","
                               + "         'union_of_selected_boundaries'::text \"localname\","
                               + "         ST_Multi(ST_Union(way)) way"
                               + "    from "+tmpTable
                };

                if (exportLayout.equals("split"))
                   xcmd[8] = xcmd[8] + "   where id in ("+ids+");";
                if (exportLayout.equals("levels")) {
                   SimpleLog.write(myLog, myHeader+"level="+currentLevel);
                   xcmd[8] = xcmd[8] + "   where admin_level ='"+currentLevel+"';";
                }

//              xcmd[8] = xcmd[8] + "   where id in ("+ids+");";
             
                cmd = xcmd;
             }
                
             String fullCmd = StringUtils.join(cmd," ");

             if (debug > 1) SimpleLog.write(myLog, myHeader+subName+" running "+fullCmd);

             Runtime rt = Runtime.getRuntime();
 
             Process pr = rt.exec(cmd);
 
             String line;
             BufferedReader bri = new BufferedReader(new InputStreamReader(pr.getInputStream()));
             BufferedReader bre = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
             while ((line = bri.readLine()) != null) {
                if (debug > 1) SimpleLog.write(myLog, myHeader+"bri --> "+line);
             }

             bri.close();
             while ((line = bre.readLine()) != null) {
                if (debug > 1) SimpleLog.write(myLog, myHeader+"bre --> "+line);
             }
             bre.close();

             pr.waitFor();
             if (debug > 1) SimpleLog.write(myLog, myHeader+subName+" pgsql2shp done"); 
                          
             int slen = shpFile.getAbsolutePath().length();
             
             // create .cpg
             
             PrintWriter writer = new PrintWriter(shpFile.getAbsolutePath().substring(0,slen-4)+".cpg", "UTF-8");
             writer.println("UTF-8");
             writer.close();

             //  add Shapefile to zip.

             if (debug > 1) SimpleLog.write(myLog, myHeader+subName+" shpFile: "+shpFile.getAbsolutePath()); 
             add2Zip(shpFile.getAbsolutePath().substring(0,slen-4)+".dbf",AlFilename+".dbf",zip);
             add2Zip(shpFile.getAbsolutePath().substring(0,slen-4)+".prj",AlFilename+".prj",zip);
             add2Zip(shpFile.getAbsolutePath().substring(0,slen-4)+".shp",AlFilename+".shp",zip);
             add2Zip(shpFile.getAbsolutePath().substring(0,slen-4)+".shx",AlFilename+".shx",zip);
             add2Zip(shpFile.getAbsolutePath().substring(0,slen-4)+".cpg",AlFilename+".cpg",zip);
             if (debug > 1) SimpleLog.write(myLog, myHeader+subName+" done"); 

          }
          catch (Exception err) {
             SimpleLog.write(myLog, myHeader+subName+" error!");
             err.printStackTrace();
          }
    }
    
// ***************************************** exportSHP ************************************* //

    private long exportSHP(HttpServletResponse response, String target, boolean subtree, 
                           Integer from_al, Integer to_al, String bCacheFilename, int debug) 
       throws IOException, SQLException, ServletException {

       if (debug > 1) SimpleLog.write(myLog, myHeader+"start shape");
       long total = 0;
       int old_admin_level = 0;
       int startLevel = 0;
       int currentLevel = 0;
       int indent=0;
       boolean skip = false;
       String baseName = "dummy";
       String ZipFile = "dummy";
       
       if (bCacheFilename == null) { // create new one
          String newbCacheFilename = createbCacheFilename(exportFormat, exportLayout, exportAreas, timestamp);
          ZipFile = bCache + newbCacheFilename;
          if (debug > 0) SimpleLog.write(myLog, myHeader+"Creating ZipFile "+ZipFile);

          ZipArchiveOutputStream zip = new ZipArchiveOutputStream(new FileOutputStream(ZipFile));
          zip.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);

          if (debug > 0) SimpleLog.write(myLog, myHeader+"exportLayout="+exportLayout);

          String ids = "";
          String AlFilename = "Planet";
          int admin_level = 0;
          
          switch(exportLayout) {
          case "split":  // ############################### SPLIT ##########################
             while (BaseRS.next()) {
                ids = BaseRS.getInt("id")+"";  
                admin_level = BaseRS.getInt("admin_level");
                String rpath = BaseRS.getString("rpath");
                if (debug > 0 )
                   SimpleLog.write(myLog, myHeader+ "exportSHP/SPLIT: id="+ids+", admin_level="+admin_level+", rpath="+rpath);
                
                if (old_admin_level == 0) { // first boundary at all
//                 baseName = BaseRS.getString("name").replace(' ','_').replace('/','_');   // first name
//                 if (planet) baseName = "World";
                   baseName = getBaseName(BaseRS.getString("name"),selected, subtree, from_al, to_al, planet);
                   startLevel = StringUtils.countMatches(rpath, ",");   
                   if (debug > 3) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" startLevel="+startLevel);
                }
                old_admin_level = admin_level;
                
                currentLevel = StringUtils.countMatches(rpath, ",");    
                if (debug > 2) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" currentLevel="+currentLevel);

                String name = BaseRS.getString("name");
                // name mit id um gleiche Namen unterscheiden zu können! (z.B. in Albanien)
                addBoundary2SHP(ids, name+"_AL"+admin_level+"_"+BaseRS.getString("id"), exportLayout, startLevel, currentLevel, zip, indent);
             };  // end loop über die Boundaries  
             break;
             
          case "single": // ####################################### SINGLE ############################
             ids = "";
             while (BaseRS.next()) { 
                if (debug > 3) SimpleLog.write(myLog, myHeader+ "Fetching data from tmp-table");

                admin_level = BaseRS.getInt("admin_level");
                String rpath = BaseRS.getString("rpath");
                
                if (old_admin_level == 0) { // first boundary at all              
//                 baseName = BaseRS.getString("name").replace(' ','_').replace('/','_');   // first name
//                 if (planet) baseName = "World";
                   baseName = getBaseName(BaseRS.getString("name"),selected, subtree, from_al, to_al, planet);
                   startLevel = StringUtils.countMatches(rpath, ",");   
                   if (debug > 3) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" startLevel="+startLevel);
                }
                old_admin_level = admin_level;
                
                currentLevel = StringUtils.countMatches(rpath, ",");    
                if (debug > 3) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" currentLevel="+currentLevel);
                ids = ids+","+BaseRS.getInt("id");

             };  // end loop über die Boundaries 
             
             //           BaseRS.first(); // rewind
                             
             if (debug > 3) SimpleLog.write(myLog, myHeader+ "calling addBoundary2SHP(...) with ids="+ids.substring(1));

             addBoundary2SHP(ids.substring(1), baseName, exportLayout, startLevel, currentLevel, zip, indent); 
             
             break;
             
          case "levels": // ####################################### LEVELS ############################
             ids = "";
             if (debug > 1 ) SimpleLog.write(myLog, myHeader+"doing levels");
             while (BaseRS.next()) {  
                admin_level = BaseRS.getInt("admin_level");
                String rpath = BaseRS.getString("rpath");
                skip = false;
                
                if (admin_level > old_admin_level) {
                   if (old_admin_level == 0) { // first boundary at all
//                    baseName = BaseRS.getString("name").replace(' ','_').replace('/','_');    // first name
//                    if (planet) baseName = "World";
////                      baseName = BaseRS.getString("name").replace(' ','_').replace('/','_');    // first name
////                  if (debug > 1) SimpleLog.write(myLog, myHeader+ "first of all baseName="+baseName);
////                  startLevel = StringUtils.countMatches(rpath, ",");    
////                  if (debug > 1) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" startLevel="+startLevel);
                      baseName = getBaseName(BaseRS.getString("name"),selected, subtree, -1, -1, planet); // ohne from/to
                      startLevel = StringUtils.countMatches(rpath, ",");    
                      if (debug > 3) SimpleLog.write(myLog, myHeader+ "rpath="+rpath+" startLevel="+startLevel);
                   }
                   else {
                      // close old level
                      SimpleLog.write(myLog, myHeader+"new al="+admin_level+" finishing and closing current level");  
                      if (debug > 0) SimpleLog.write(myLog, myHeader+"close old ids: >"+ids+"<");
                      addBoundary2SHP(ids.substring(1), baseName+"_AL"+old_admin_level, exportLayout, startLevel, old_admin_level, zip, indent);   
                      ids = "";              
                   }       
                   old_admin_level = admin_level;
                }
                ids = ids+","+BaseRS.getInt("id");
             };  // end loop über die Boundaries 
             
             if (debug > 0) SimpleLog.write(myLog, myHeader+"finish ids: >"+ids+"<");
             addBoundary2SHP(ids.substring(1), baseName+"_AL"+admin_level, exportLayout, startLevel, admin_level, zip, indent);   
             break;
          } // switch
          
          //     statement.close();
          if (debug > 0) SimpleLog.write(myLog, myHeader+"closing zip");
          zip.close();
          if (debug > 0 ) SimpleLog.write(myLog, myHeader+exportFormat+" part1 done");
          if (debug > 0 ) SimpleLog.write(myLog, myHeader+"bCacheFilename="+bCacheFilename);

          insertBCache(user, timestamp, exportFormat, union, exportLayout, exportAreas, subtree, from_al, to_al, newbCacheFilename, selected, baseName);
       } // finish creating zip.
       else {
          baseName = bCacheFilename.split("#")[0];
          ZipFile = bCache+bCacheFilename.split("#")[1];
//        updateBcache(exportFormat, exportLayout, exportAreas, timestamp); -- used=used+1       
       }
          
       // Init servlet response.
       response.reset();      
       response.setCharacterEncoding("UTF-8");
       BufferedOutputStream output = new BufferedOutputStream(response.getOutputStream(), DEFAULT_BUFFER_SIZE);
       
       if (target.equals("file")) {
	      response.setContentType("application/zip");
	      String header = "attachment; filename=\"exported_boundaries_"+baseName+".shp.zip\"";
	      if (debug > 0) SimpleLog.write(myLog, myHeader+"Content-Disposition="+ header);
	      response.setHeader("Content-Disposition", header);
	      response.setHeader("Content-Transfer-Encoding", "binary");
       }
       else {
	      SimpleLog.write(myLog, myHeader+"writing to html-stream");
	      response.setContentType("text/plain");
       }    
       
       File z = new File(ZipFile);
       long bytes = z.length();
       response.setHeader("Content-Length",bytes+"");
       
       BufferedInputStream input = new BufferedInputStream(new FileInputStream(ZipFile), DEFAULT_BUFFER_SIZE);
       
       // Write file contents to response.
       byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
       int length;
       
       while ((length = input.read(buffer)) > 0) {
          output.write(buffer, 0, length);
          total = total+length;
       }
       
       ////   statement.close();
       input.close();
       output.close();  
   
       return(total);
    }

// ****************************************************************************
// ************************************** START MAIN **************************
// ****************************************************************************

   public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
      throws ServletException, IOException {
         
      int       debug = 2;
      int       rc  = 0;
      int       nextLevel;
      int       dpCnt = 0;
      String    target;
      long      total = 0; // total data sent
        
      String geoJsonString; 
      String contentType="application/text";
      api = false;

      String caller = request.getParameter("caller");

      String apiversion = coalesce(request.getParameter("apiversion"),request.getParameter("version"),"1.0");

      if (!apiversion.equals("none")) {
         caller = "api "+apiversion;
         api = true;
      }
//    debug = Integer.parseInt(request.getParameter("debug"));

      //is client behind something?
      remoteAddr = request.getHeader("X-FORWARDED-FOR");  
      if (remoteAddr == null) {  
         remoteAddr = request.getRemoteAddr();
      }

      String spaces = "                                ";
      myHeader = remoteAddr+spaces.substring(0,16-remoteAddr.length()) 
                      + caller + " " 
                      + myName+spaces.substring(0,30-myName.length());

      SimpleLog.write(myLog, myHeader+"remoteAddr="+remoteAddr);

/*    String notCached = request.getParameter("_");  // quick&dirty: verwerfe doppelten ajax-request
      if (notCached == null) { 
         SimpleLog.write(myLog, myHeader+"not cached, request purged");
         response.setStatus(405); 
         return;
      } */

      String referer = request.getHeader("referer");
      SimpleLog.write(myLog, myHeader+"referer="+referer);
      
      String queryString = request.getQueryString();
      SimpleLog.write(myLog, myHeader+"queryString="+queryString); 
      

/* geht nicht für api
      if (referer.indexOf("https://osm.wno-edv-service.de/boundaries") != 0) {
         SimpleLog.write(myLog, myHeader+"faked referer?");
         rc=401;                    
         response.setStatus(rc);  
         SimpleLog.write(myLog, myHeader+"done rc="+rc);
         return;
      }
*/

      response.reset(); // init
      
      Enumeration parameterNames = request.getParameterNames();
      String OAuth_session_key = "";
      database = "planet3";
      target = "html";
      selected = ""; 
      apiversion = "1.0";
      union = false;
      from_al = 2;
      to_al = 99;
      subtree= false;
      exportFormat = "shp";
      exportLayout = "levels";
      exportAreas = "water";
      
      while(parameterNames.hasMoreElements()) {
         Object objOri=parameterNames.nextElement();
         String param=(String)objOri;
         String value=request.getParameter(param);
         SimpleLog.write(myLog, myHeader+"Parameter name is '"+param+"' and parameter value is '"+value+"'");
         
         if (value.equals("")) {
            SimpleLog.write(myLog, myHeader+"value for "+param+" is empty");
            sendError(response,"any spaces in url not allowed",400);
            return;
         }
         
         switch (param.toLowerCase()) {
         case "database":
            database = request.getParameter(param);
            break;
         case "target":
            target = request.getParameter(param);
            break;
         case "apikey":
            OAuth_session_key = request.getParameter(param).toLowerCase();
            break;
         case "apiversion":
         case "version":
            apiversion = request.getParameter(param);
            if (!apiversion.equals("none"))
               subtree = true;
            SimpleLog.write(myLog, myHeader+"2 subtree = "+subtree);
            break;
         case "exportformat":
         case "format":
            exportFormat = request.getParameter(param).toLowerCase(); 
            switch(exportFormat) {
            case "osm":
            case "json":    
            case "poly":
            case "bpoly": 
            case "svg":        
            case "shp":
               break;
            default:  
               sendError(response,"invalid exportFormat="+exportFormat,400);
               return;
            }
            break;
         case "exportlayout":
         case "layout":
            exportLayout = request.getParameter(param).toLowerCase(); 
            switch(exportLayout) {
            case "single":
            case "levels":
            case "split":
               break;
            default:
               sendError(response,"invalid exportLayout="+exportLayout,400);
               return;
            }      
            break;
         case "exportareas":
         case "areas":
            exportAreas = request.getParameter(param).toLowerCase();
            switch (exportAreas) {
            case "land":
            case "water":
               break;
            default:
               sendError(response,"invalid exportAreas="+exportAreas,400);
               return;
            }             
            break;   
         case "selected":
         case "boundary":
         case "boundaries":
         case "rels":
            selected = request.getParameter(param);
            
            if (selected.matches("[0-9,]*")) {
                SimpleLog.write(myLog, myHeader+"numeric");
            }
            else {
               SimpleLog.write(myLog, myHeader+"not numeric");
               String xselected = selected.toUpperCase();
               if (!xselected.matches("[A-Z]{3}")) {
                  sendError(response,"Illegal parameter for selected: '"+selected+"'",400);
                  return;
               }
            }
            SimpleLog.write(myLog, myHeader+"selected scanned");
            break;                  
            
         case "buffer":
         case "bufferdist":
            String buffer = request.getParameter(param).toLowerCase();       
            bufferDist = Double.parseDouble(buffer);
            if (bufferDist<0) {
               sendError(response,"invalid buffer="+buffer,400);
               return;   
            }
            break;   
         case "union":
            String xunion = request.getParameter(param).toLowerCase();       
            union = Boolean.parseBoolean(xunion);
            break;  
         case "subtree":
            subtree = Boolean.parseBoolean(request.getParameter(param));
            SimpleLog.write(myLog, myHeader+"3 subtree = "+subtree);
            break;
         case "from_al":
            from_al = Integer.parseInt(request.getParameter(param));
            break;
         case "to_al":
            to_al = Integer.parseInt(request.getParameter(param));
            break;
         default:
            break;
         }      
      } 
      
      SimpleLog.write(myLog, myHeader+"cmdline scanned");
      SimpleLog.write(myLog, myHeader+"subtree = "+subtree);
      
      if (apiversion == null) {
         sendError(response,"parameter apiversion=N is missing",400);
         return;
      }
      
// check subtree und selected

      if (selected == null) {
         sendError(response,"parameter selected=N is missing",400);
         return;
      } 

// special: für seleced=0 from_al und to_al auf 2 setzen. 
      if (selected.equals("0")) {
         SimpleLog.write(myLog, myHeader+"parameter \"selected\" is 0, allow from_AL=2 and to_AL=2 only.");  
         from_al = 2;
         to_al = 2;
         subtree = true;
         planet = true;
      }
      

      if (subtree) { // ###########################################################################
         if (selected.indexOf(",") > 0) {
            SimpleLog.write(myLog, myHeader+"subtree erlaubt keine Liste");  
            sendError(response,"No list allowed when using API. Please specify only one top relation: "+selected,400);
            return;
         }
      } 
      
      if (from_al > to_al) {
         SimpleLog.write(myLog, myHeader+"from_al > to_al");
         sendError(response,"from_al="+from_al+" is greater than to_al="+to_al,400);
         return;
      }
         
/* -------------- OAuth start -------------------------------- */

      if (debug > 1) SimpleLog.write(myLog, myHeader+"OAuth_session_key="+OAuth_session_key);

      if (OAuth_session_key.equals("")) {           // check for cookie
         SimpleLog.write(myLog, myHeader+"OAuth_session_key not found.");
         rc=400;
         response.setStatus(rc);  
         SimpleLog.write(myLog, myHeader+"done rc="+rc);
         return;
      }      

      String uu = OsmOAuth.getUserFromOAuth(myLog, myHeader, OAuth_session_key, 1);
      
      if (uu.length() != 0) {
         user = uu.split(";")[0];
         userid = Integer.parseInt(uu.split(";")[1]);
      }
      else { 
         SimpleLog.write(myLog, myHeader+"User not found in oauth.");
         rc=400;
         sendError(response,"Unknown apikey '"+OAuth_session_key+"'",400);  
         SimpleLog.write(myLog, myHeader+"done rc="+rc);
         return;
      }
      
      if (user.equals("")) {                    // check for user
         SimpleLog.write(myLog, myHeader+"OAuth_session_key found but uuid not in database");
         rc=400;                    
         response.setStatus(rc);  
         SimpleLog.write(myLog, myHeader+"done rc="+rc);
         return;
      }
         
      if (debug > 1) SimpleLog.write(myLog, myHeader+"user="+user);
  
/* -------------- OAuth end ---------------------------------- */

      String uuid = UUID.randomUUID().toString();
      SimpleLog.write(myLog, myHeader+"uuid = " + uuid);
      tmpTable = "tmp_"+uuid.replace("-","");
      SimpleLog.write(myLog, myHeader+"tmpTable = " + tmpTable);

      try {
         Class.forName("org.postgresql.Driver");
      } 
      catch (ClassNotFoundException cnfe) {
         SimpleLog.write(myLog, myHeader+"Couldn't find the driver!");
         cnfe.printStackTrace();
         System.exit(1);
      }

      try { 
         conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/"+database,"osm", "");
         
         if (api) {
            boolean doit = checkBlocked(userid, user, 2);
            SimpleLog.write(myLog, myHeader+"doit="+doit); 
            if (!doit) {
               sendError(response,"Account blocked. Please read AND ANSWER your mail.", 400);
               return;
            }
            doit = checkQuotaUsageOfUser(userid, user, 2);
            SimpleLog.write(myLog, myHeader+"doit="+doit); 
            if (!doit) {
               sendError(response,"Dayly quota exceeded.", 400);
               return;
            }     
         }

         if (selected.length() == 3) {  // erst hier wegen sql-try
            String xselected = getCountryFromISO3(selected);
            if (xselected == null) {
               SimpleLog.write(myLog, myHeader+"selected kein Iso3");  
               rc=400;
               sendError(response,"Unknown Country Code: '"+selected+"'",400);  
               return;
            }  
            else {
               selected = xselected;
            }
         }
         
         String TempQuery;

         String TSquery = 
              "select timestamp from timestamps where tablename = 'collected_admin_boundaries';";
         
         if (debug > 1) SimpleLog.write(myLog,myHeader+TSquery);

         Statement statement = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_READ_ONLY);
         ResultSet TSrs = statement.executeQuery(TSquery);

         TSrs.next(); 

         timestamp = TSrs.getString("timestamp");
         if (debug > 0) SimpleLog.write(myLog,myHeader+"Timestamp="+timestamp);

         TSrs.close();

         String bCacheFilename = checkBCache(timestamp, exportFormat, union, exportLayout, exportAreas, subtree, from_al, to_al, selected);
         if (debug > 0) SimpleLog.write(myLog,myHeader+"bcacheFilename="+bCacheFilename);
         if (debug > 0) SimpleLog.write(myLog,myHeader+"subtree = "+subtree);

         if (bCacheFilename == null) {
            String WayOrLandarea = "way";
            if (exportAreas.equals("land")) WayOrLandarea = "coalesce(s1_landarea, way)";
            
            if (subtree) { 
               TempQuery = "create unlogged table "+tmpTable                    
               + " with (autovacuum_enabled=false, toast.autovacuum_enabled=false)"
               + " as "  
               + "select id,"
               + "       value \"name\","
               + "       localname,"
               + "       \"level\" admin_level,"
               + "       tags,"
               + "       hstore_to_array(tags) mtags,"
               + "       st_AsGeoJSON("+WayOrLandarea+",6) geoJson,"
               + "       "+WayOrLandarea+" way,"
               + "       bbox,"
               + "       rpath,"
               + "       '"+timestamp+"'::text \"timestamp\""
               + "  from boundaries "
               + " where path @> array["+selected+"::bigint]"
               ;
            
               if (from_al != 2 | to_al != 99) {
                  if (debug > 0) SimpleLog.write(myLog,myHeader+"adding al_from and al_to to query");
                  TempQuery = TempQuery 
                  + "   and wno_number(level) between "+from_al+" and "+to_al;
               }
            }
            else {     
               if (!union) {
                  TempQuery = "create Unlogged table "+tmpTable                    
                  + " with (autovacuum_enabled=false, toast.autovacuum_enabled=false)"
                     + " as "  
                  + "select id,"
                  + "       value \"name\","
                  + "       localname,"
                  + "       \"level\" admin_level,"
                  + "       tags,"
                  + "       hstore_to_array(tags) mtags,"
                  + "       st_AsGeoJSON("+WayOrLandarea+",6) geoJson,"
                  + "       "+WayOrLandarea+" way,"
                  + "       bbox,"
                  + "       rpath,"
                  + "       '"+timestamp+"'::text \"timestamp\""
                  + "  from boundaries"           
                  + " where id in ("+selected+");"
                  ;
               }
               else {
                  TempQuery = "create unlogged Table "+tmpTable
                  + " with (autovacuum_enabled=false, toast.autovacuum_enabled=false)"
                  + " as "  
                  + "select 1 id,"
                  + "       'union_of_selected_boundaries'::text \"name\","
                  + "       'union_of_selected_boundaries'::text localname,"
                  + "       max(wno_number(level)) admin_level,"
                  + "       null::hstore tags,"
                  + "       array[''] mtags,"
                  + "       ST_AsGeoJSON(ST_Multi(ST_Union("+WayOrLandarea+")),6) geoJson,"
                  + "       ST_Multi(ST_Union("+WayOrLandarea+")) way,"
                  + "       ST_Envelope(ST_Multi(ST_Union("+WayOrLandarea+"))) bbox,"
                  + "       array[0::bigint] rpath,"
                  + "       '"+timestamp+"'::text \"timestamp\""
                  + "  from boundaries "           
                  + " where id in ("+selected+");"
                  ;
               }
            }
            
            if (debug > 0) SimpleLog.write(myLog, myHeader+"TempQuery: "+TempQuery);
            
            Statement statementCR = conn.createStatement();
            statementCR.execute(TempQuery);
            statementCR.close();
            
            String BaseQuery = "select * from "+tmpTable
            + " order by wno_number(admin_level), name;";
            
            SimpleLog.write(myLog, myHeader+"BaseQuery: "+BaseQuery);
            
            Statement statementLOOP = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );

            BaseRS = statementLOOP.executeQuery(BaseQuery);
            
            SimpleLog.write(myLog, myHeader+"BaseQuery done.");

            boolean foundAny = BaseRS.first();
            SimpleLog.write(myLog, myHeader+"foundAny: "+foundAny);
            if (!foundAny) {
               SimpleLog.write(myLog, myHeader+"keine Daten gefunden");  
               response.setStatus(404);
               return;                 
            }
            BaseRS.beforeFirst();
         }
         else {
            SimpleLog.write(myLog,myHeader+"using cache");
            
            /*
            String username, String ts, String format, boolean union, String layout,
                             String areas, boolean subtree, Integer from_al, Integer to_al,
                             String filename, String selected,
                             String baseName) 
            
            */
            
            updateBCache(timestamp, exportFormat, union, exportLayout, exportAreas, subtree,
                         from_al, to_al, bCacheFilename.split("#")[1], selected,
                         bCacheFilename.split("#")[0], 2); // used=used+1
         }

//       SimpleLog.write(myLog, myHeader+"writing to html-stream");
//       OutputStream out = response.getOutputStream();

         if (debug > 1) SimpleLog.write(myLog, myHeader+"Format="+exportFormat);

         switch(exportFormat) {
            case "osm":
               break;

            case "json":  //         JSON
               total = exportJSON(response, target, subtree, from_al, to_al, bCacheFilename);
               break;   
            case "poly":
                 // vorlauf, kein break!
            case "bpoly": 
               total = exportPOLY(exportFormat, response, target);
               break;  // end POLY
            case "svg":  
               total = exportSVG(response, target, subtree, from_al, to_al, bCacheFilename);        
               break; // end SVG
            case "shp":
               total = exportSHP(response, target, subtree, from_al, to_al, bCacheFilename, 3);
               break;
            default:
               break;
         }

         if (bCacheFilename == null) {
            String qDropTemp = "Drop table "+tmpTable;
            if (debug < 3) {
               SimpleLog.write(myLog, myHeader+"qDropTemp: "+qDropTemp);
               int x = statement.executeUpdate(qDropTemp);
            }
         }
     
         logUsage(myBaseName, userid, user, "export", exportFormat, bufferDist, union, exportLayout,
                  exportAreas, total, remoteAddr, selected, subtree, apiversion, 0);
         
      } catch (SQLException se) {
         SimpleLog.write(myLog, myHeader+"Couldn't connect: print out a stack trace and exit.");
         SimpleLog.write(myLog, myHeader+getStackTrace(se));
      }
 
      finally {
         try {
            if (conn != null) {
               conn.close();
            }
         }
         catch (SQLException se) {
            SimpleLog.write(myLog, myHeader+se.toString());
            SimpleLog.write(myLog, myHeader+getStackTrace(se));
         }
      }       
           
      rc=200;
 
      SimpleLog.write(myLog, myHeader+"setting cookie fileDownload=true");
      Cookie cookie1 = new Cookie("fileDownload", "true");
      cookie1.setPath("/");
      cookie1.setMaxAge(10000000);
      response.addCookie(cookie1); 
      Cookie cookie2 = new Cookie("test", "true");
      cookie2.setMaxAge(60*60*24*365);
      response.addCookie(cookie2); 
     
      response.setStatus(rc);
      if (debug > 1) displayHttpResponse(response); 

      SimpleLog.write(myLog, myHeader+"done rc="+rc);
   } 

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
    throws IOException, ServletException
       {
           // Übergabe an doGet(), falls Anforderung mittels POST
           doGet(request, response);
       }
}
